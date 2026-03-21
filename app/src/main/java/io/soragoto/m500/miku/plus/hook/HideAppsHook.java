package io.soragoto.m500.miku.plus.hook;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import io.soragoto.m500.miku.plus.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.soragoto.m500.miku.plus.Const.COL_PACKAGE;

public class HideAppsHook implements IXposedHookLoadPackage {

    private static final String TAG = "XposedHide";
    private static final String MODULE_PKG = "io.soragoto.m500.miku.plus";
    private static final Uri PROVIDER_URI = Uri.parse("content://" + MODULE_PKG + ".provider/hidden");
    private static final Uri HOOKED_URI = Uri.parse("content://" + MODULE_PKG + ".provider/hooked");
    private static final Uri RESTART_URI = Uri.parse("content://" + MODULE_PKG + ".provider/restart");

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
        Log.d(TAG, msg);
    }

    @Override
    public void handleLoadPackage(LoadPackageParam loadPackageParam) {
        log("handleLoadPackage: " + loadPackageParam.packageName);

        if ("com.android.launcher3".equals(loadPackageParam.packageName)) {
            hookLauncherSettings(loadPackageParam);
        }

        final String hookedPkg = loadPackageParam.packageName;
        final boolean isSelf = MODULE_PKG.equals(hookedPkg);
        final boolean[] registered = {false};
        final boolean[] observerRegistered = {false};

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
                            log("failed to get mContext: " + e);
                        }

                        // don't filter in the module's own process — it needs the full list
                        if (isSelf) {
                            log("skipping filter in module process");
                            return;
                        }

                        // register this package via ContentProvider on the first successful call
                        if (!registered[0] && ctx != null) {
                            try {
                                ctx.getContentResolver().insert(HOOKED_URI, packageNameValues(hookedPkg));
                                registered[0] = true;
                                log("registered hooked package: " + hookedPkg);
                            } catch (Throwable t) {
                                log("failed to register hooked package: " + t);
                            }
                        }                        // register ContentObserver for restart signal on the first successful ctx
                        if (!observerRegistered[0] && ctx != null) {
                            try {
                                final Context finalCtx = ctx;
                                ctx.getContentResolver().registerContentObserver(
                                        RESTART_URI,
                                        false,
                                        new ContentObserver(new Handler(Looper.getMainLooper())) {
                                            @Override
                                            public void onChange(boolean selfChange) {
                                                log("restart signal received, restarting: " + hookedPkg);
                                                try {
                                                    Intent intent = finalCtx.getPackageManager()
                                                            .getLaunchIntentForPackage(finalCtx.getPackageName());
                                                    if (intent != null) {
                                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                        finalCtx.startActivity(intent);
                                                    }
                                                } catch (Throwable t) {
                                                    log("failed to start activity before kill: " + t);
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
                                observerRegistered[0] = true;
                                log("registered restart observer for: " + hookedPkg);
                            } catch (Throwable t) {
                                log("failed to register restart observer: " + t);
                            }
                        }

                        Set<String> hidden = queryHiddenPackages(ctx);
                        log("getActivityList: apps=" + result.size() + " hidden=" + hidden);
                        if (hidden.isEmpty()) return;

                        List<LauncherActivityInfo> filtered = new ArrayList<>(result.size());
                        for (LauncherActivityInfo info : result) {
                            android.content.pm.ApplicationInfo ai = info.getApplicationInfo();
                            String pkg = (ai != null) ? ai.packageName : null;
                            if (hidden.contains(pkg)) {
                                log("hiding: " + pkg);
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
        log("hooked LauncherApps.getActivityList()");
    }

    private Set<String> queryHiddenPackages(Context ctx) {
        Set<String> result = new HashSet<>();
        if (ctx == null) {
            log("queryHiddenPackages: ctx is null");
            return result;
        }
        try {
            Cursor cursor = ctx.getContentResolver().query(PROVIDER_URI, null, null, null, null);
            if (cursor == null) {
                log("ContentProvider returned null cursor");
            } else {
                while (cursor.moveToNext()) {
                    result.add(cursor.getString(0));
                }
                cursor.close();
            }
        } catch (Exception e) {
            log("ContentProvider query failed: " + e);
        }
        return result;
    }

    private static ContentValues packageNameValues(String pkg) {
        ContentValues v = new ContentValues(1);
        v.put(COL_PACKAGE, pkg);
        return v;
    }

    /**
     * 在 Launcher 设置页注入「隐藏图标」入口条目，点击后跳转到 HideIconActivity。
     */
    private void hookLauncherSettings(LoadPackageParam lpp) {
        XposedHelpers.findAndHookMethod(
                "com.android.launcher3.settings.SettingsActivity$LauncherSettingsFragment",
                lpp.classLoader,
                "onCreatePreferences",
                Bundle.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object fragment = param.thisObject;
                        Object screen = XposedHelpers.callMethod(fragment, "getPreferenceScreen");

                        // 防止重复注入
                        if (XposedHelpers.callMethod(screen, "findPreference", "pref_hideIcons") != null) {
                            return;
                        }

                        Context context = (Context) XposedHelpers.callMethod(fragment, "requireContext");
                        Context moduleCtx = getModuleContext(context);

                        Class<?> prefClass = XposedHelpers.findClass(
                                "androidx.preference.Preference", lpp.classLoader);
                        Object pref = XposedHelpers.newInstance(prefClass, context);

                        XposedHelpers.callMethod(pref, "setKey", "pref_hideIcons");
                        XposedHelpers.callMethod(pref, "setTitle",
                                getModuleString(moduleCtx, R.string.pref_hide_icons_title, "Hide Icons"));
                        XposedHelpers.callMethod(pref, "setSummary",
                                getModuleString(moduleCtx, R.string.pref_hide_icons_summary,
                                        "Select apps to hide from the launcher drawer"));
                        XposedHelpers.callMethod(pref, "setPersistent", false);

                        Intent intent = new Intent()
                                .setComponent(new ComponentName(MODULE_PKG, MODULE_PKG + ".activity.HideIconActivity"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        XposedHelpers.callMethod(pref, "setIntent", intent);

                        XposedHelpers.callMethod(screen, "addPreference", pref);
                        log("injected pref_hideIcons into launcher settings");
                    }
                }
        );
    }

    private static Context getModuleContext(Context ctx) {
        try {
            return ctx.createPackageContext(MODULE_PKG, Context.CONTEXT_IGNORE_SECURITY);
        } catch (Throwable t) {
            log("getModuleContext failed: " + t);
            return null;
        }
    }

    private static String getModuleString(Context moduleCtx, int resId, String fallback) {
        if (moduleCtx == null) return fallback;
        try {
            return moduleCtx.getString(resId);
        } catch (Throwable t) {
            return fallback;
        }
    }
}