package io.soragoto.m500.miku.plus.hook;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import io.soragoto.m500.miku.plus.Const;
import io.soragoto.m500.miku.plus.R;
import io.soragoto.m500.miku.plus.util.XposedUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.soragoto.m500.miku.plus.Const.COL_PACKAGE;

public class HideAppsHook implements IXposedHookLoadPackage {

    private static final Uri PROVIDER_URI = Uri.parse(Const.URI_HIDDEN_PACKAGES);
    private static final Uri HOOKED_URI = Uri.parse(Const.URI_HOOKED_PACKAGES);
    private static final Uri RESTART_URI = Uri.parse(Const.URI_RESTART_SIGNAL);

    @Override
    public void handleLoadPackage(LoadPackageParam loadPackageParam) {
        XposedUtils.log("handleLoadPackage: " + loadPackageParam.packageName);

        if (Const.LAUNCHER_PKG.equals(loadPackageParam.packageName)) {
            XposedUtils.hookLauncherSettings(loadPackageParam, "HideAppsHook", this::injectPreference);
        }

        final String hookedPkg = loadPackageParam.packageName;
        final boolean isSelf = Const.MODULE_PKG.equals(hookedPkg);
        final AtomicBoolean registered = new AtomicBoolean(false);
        final AtomicBoolean observerRegistered = new AtomicBoolean(false);

        XposedHelpers.findAndHookMethod(
                android.content.pm.LauncherApps.class,
                "getActivityList",
                String.class,
                UserHandle.class,
                new XC_MethodHook() {
                    @Override
                    @SuppressWarnings("unchecked")
                    protected void afterHookedMethod(MethodHookParam param) {
                        List<LauncherActivityInfo> result =
                                (List<LauncherActivityInfo>) param.getResult();
                        if (result == null || result.isEmpty()) return;

                        Context ctx = null;
                        try {
                            ctx = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                        } catch (Throwable e) {
                            XposedUtils.log("failed to get mContext: " + e);
                        }

                        // don't filter in the module's own process — it needs the full list
                        if (isSelf) {
                            XposedUtils.log("skipping filter in module process");
                            return;
                        }

                        // register this package via ContentProvider on the first successful call
                        if (!registered.get() && ctx != null) {
                            try {
                                ctx.getContentResolver().insert(HOOKED_URI, packageNameValues(hookedPkg));
                                registered.set(true);
                                XposedUtils.log("registered hooked package: " + hookedPkg);
                            } catch (Throwable t) {
                                XposedUtils.log("failed to register hooked package: " + t);
                            }
                        }

                        // register ContentObserver for restart signal on the first successful ctx
                        if (!observerRegistered.get() && ctx != null) {
                            try {
                                final Context finalCtx = ctx;
                                ctx.getContentResolver().registerContentObserver(
                                        RESTART_URI,
                                        false,
                                        new ContentObserver(new Handler(Looper.getMainLooper())) {
                                            @Override
                                            public void onChange(boolean selfChange) {
                                                XposedUtils.log("restart signal received, restarting: " + hookedPkg);
                                                try {
                                                    Intent intent = finalCtx.getPackageManager()
                                                            .getLaunchIntentForPackage(finalCtx.getPackageName());
                                                    if (intent != null) {
                                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                        finalCtx.startActivity(intent);
                                                    }
                                                } catch (Throwable t) {
                                                    XposedUtils.log("failed to start activity before kill: " + t);
                                                }
                                                // delay slightly to let startActivity's Binder call
                                                // be dispatched before the process is killed
                                                new Handler(Looper.getMainLooper()).postDelayed(
                                                        () -> android.os.Process.killProcess(
                                                                android.os.Process.myPid()),
                                                        300
                                                );
                                            }
                                        }
                                );
                                observerRegistered.set(true);
                                XposedUtils.log("registered restart observer for: " + hookedPkg);
                            } catch (Throwable t) {
                                XposedUtils.log("failed to register restart observer: " + t);
                            }
                        }

                        Set<String> hidden = queryHiddenPackages(ctx);
                        XposedUtils.log("getActivityList: apps=" + result.size() + " hidden=" + hidden);
                        if (hidden.isEmpty()) return;

                        List<LauncherActivityInfo> filtered = new ArrayList<>(result.size());
                        for (LauncherActivityInfo info : result) {
                            android.content.pm.ApplicationInfo ai = info.getApplicationInfo();
                            String pkg = (ai != null) ? ai.packageName : null;
                            if (hidden.contains(pkg)) {
                                XposedUtils.log("hiding: " + pkg);
                            } else {
                                filtered.add(info);
                            }
                        }

                        if (filtered.size() != result.size()) {
                            param.setResult(filtered);
                        }
                    }
                }
        );
        XposedUtils.log("hooked LauncherApps.getActivityList()");
    }

    private Set<String> queryHiddenPackages(Context ctx) {
        Set<String> result = new HashSet<>();
        if (ctx == null) {
            XposedUtils.log("queryHiddenPackages: ctx is null");
            return result;
        }
        try {
            Cursor cursor = ctx.getContentResolver().query(PROVIDER_URI, null, null, null, null);
            if (cursor == null) {
                XposedUtils.log("ContentProvider returned null cursor");
            } else {
                while (cursor.moveToNext()) {
                    result.add(cursor.getString(0));
                }
                cursor.close();
            }
        } catch (Exception e) {
            XposedUtils.log("ContentProvider query failed: " + e);
        }
        return result;
    }

    private static ContentValues packageNameValues(String pkg) {
        ContentValues v = new ContentValues(1);
        v.put(COL_PACKAGE, pkg);
        return v;
    }

    private void injectPreference(Object screen, Context context, Context moduleCtx, ClassLoader classLoader) {
        // 防止重复注入
        if (XposedUtils.prefExists(screen, Const.PREF_HIDE_ICONS)) return;

        Object pref = XposedUtils.addPref(
                screen, classLoader, context,
                Const.PREF_HIDE_ICONS,
                XposedUtils.getString(moduleCtx,
                        R.string.pref_hide_icons_title, "Hide Icons"),
                XposedUtils.getString(moduleCtx,
                        R.string.pref_hide_icons_summary,
                        "Select apps to hide from the launcher drawer"));

        Intent intent = new Intent()
                .setComponent(new ComponentName(Const.MODULE_PKG,
                        Const.MODULE_PKG + ".activity.HideIconActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        XposedHelpers.callMethod(pref, "setIntent", intent);

        XposedUtils.log("injected pref_hideIcons into launcher settings");
    }
}
