package io.soragoto.m500.miku.plus.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.soragoto.m500.miku.plus.Const;

public final class XposedUtils {

    private XposedUtils() {
    }

    @FunctionalInterface
    public interface SettingsInjector {
        void inject(Object screen, Context context, Context moduleCtx, ClassLoader classLoader) throws Throwable;
    }

    public static void log(String msg) {
        Log.d(Const.TAG, msg);
        XposedBridge.log(Const.TAG + ": " + msg);
    }

    public static Context getModuleContext(Context ctx) {
        try {
            return ctx.createPackageContext(Const.MODULE_PKG, Context.CONTEXT_IGNORE_SECURITY);
        } catch (Throwable t) {
            log("getModuleContext failed: " + t);
            return null;
        }
    }

    public static String getString(Context moduleCtx, int resId, String fallback) {
        if (moduleCtx == null) return fallback;
        try {
            return moduleCtx.getString(resId);
        } catch (Throwable t) {
            return fallback;
        }
    }

    /**
     * Returns true if a preference with the given key already exists in the preference screen.
     * Used to guard against duplicate preference injection.
     */
    public static boolean prefExists(Object screen, String key) {
        return XposedHelpers.callMethod(screen, "findPreference", key) != null;
    }

    /**
     * Creates a SwitchPreference, configures it, adds it to the screen, and returns it.
     */
    public static void addSwitchPref(Object screen, ClassLoader cl, Context ctx,
                                     String key, String title, String summary, boolean defaultValue) {
        Class<?> switchPrefClass = XposedHelpers.findClass(
                "androidx.preference.SwitchPreference", cl);
        Object pref = XposedHelpers.newInstance(switchPrefClass, ctx);
        XposedHelpers.callMethod(pref, "setKey", key);
        XposedHelpers.callMethod(pref, "setTitle", title);
        XposedHelpers.callMethod(pref, "setSummary", summary);
        XposedHelpers.callMethod(pref, "setPersistent", true);
        XposedHelpers.callMethod(pref, "setDefaultValue", defaultValue);
        XposedHelpers.callMethod(screen, "addPreference", pref);
    }


    public static Object addPref(Object screen, ClassLoader cl, Context ctx,
                                 String key, String title, String summary) {
        Class<?> prefClass = XposedHelpers.findClass(
                "androidx.preference.Preference", cl);
        Object pref = XposedHelpers.newInstance(prefClass, ctx);
        XposedHelpers.callMethod(pref, "setKey", key);
        XposedHelpers.callMethod(pref, "setTitle", title);
        XposedHelpers.callMethod(pref, "setSummary", summary);
        XposedHelpers.callMethod(pref, "setPersistent", false);
        XposedHelpers.callMethod(screen, "addPreference", pref);
        return pref;
    }

    public static void hookLauncherSettings(
            XC_LoadPackage.LoadPackageParam lpp,
            String owner,
            SettingsInjector injector) {
        XposedHelpers.findAndHookMethod(
                "com.android.launcher3.settings.SettingsActivity$LauncherSettingsFragment",
                lpp.classLoader,
                "onCreatePreferences",
                Bundle.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object fragment = param.thisObject;
                            Object screen = XposedHelpers.callMethod(fragment, "getPreferenceScreen");
                            Context context = (Context) XposedHelpers.callMethod(fragment, "requireContext");
                            Context moduleCtx = getModuleContext(context);
                            injector.inject(screen, context, moduleCtx, lpp.classLoader);
                        } catch (Throwable t) {
                            log(owner + ": settings injection failed: " + t);
                        }
                    }
                }
        );
    }

    public static boolean isLandscape(Context ctx) {
        return ctx.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static boolean isMikuThemeActive(Context ctx) {
        String theme = Settings.Global.getString(ctx.getContentResolver(), "device_theme");
        return "miku".equals(theme);
    }

    public static Rect getWindowBounds(Context ctx) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return new Rect();
        return wm.getCurrentWindowMetrics().getBounds();
    }
}
