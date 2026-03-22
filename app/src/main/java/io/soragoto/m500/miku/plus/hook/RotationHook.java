package io.soragoto.m500.miku.plus.hook;

import android.content.Context;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.soragoto.m500.miku.plus.Const;
import io.soragoto.m500.miku.plus.R;
import io.soragoto.m500.miku.plus.util.XposedUtils;

public class RotationHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (!Const.LAUNCHER_PKG.equals(loadPackageParam.packageName)) return;

        XposedUtils.hookLauncherSettings(loadPackageParam, "RotationHook", this::injectPreferences);
    }

    private void injectPreferences(Object screen, Context context, Context moduleCtx, ClassLoader classLoader) {
        // 已存在则跳过（其他 launcher 已有该选项时不重复添加）
        if (XposedUtils.prefExists(screen, Const.PREF_ALLOW_ROTATION)) return;

        XposedUtils.addSwitchPref(
                screen, classLoader, context,
                Const.PREF_ALLOW_ROTATION,
                XposedUtils.getString(moduleCtx,
                        R.string.pref_allow_rotation_title, "Allow rotation"),
                XposedUtils.getString(moduleCtx,
                        R.string.pref_allow_rotation_summary,
                        "Allow the home screen to rotate with the device"),
                false);

        Object rotPref = XposedHelpers.callMethod(screen, "findPreference", Const.PREF_ALLOW_ROTATION);
        if (rotPref != null) {
            Class<?> changeListenerClass = XposedHelpers.findClass(
                    "androidx.preference.Preference$OnPreferenceChangeListener", classLoader);
            Object changeListener = java.lang.reflect.Proxy.newProxyInstance(
                    classLoader,
                    new Class[]{changeListenerClass},
                    (proxy, method, args) -> {
                        if ("onPreferenceChange".equals(method.getName())) {
                            boolean newValue = args != null
                                    && args.length > 1
                                    && args[1] instanceof Boolean
                                    && (Boolean) args[1];
                            // 横屏背景开关
                            Object bgPref = XposedHelpers.callMethod(
                                    screen, "findPreference", Const.PREF_LANDSCAPE_BG_ENABLED);
                            if (bgPref != null) {
                                XposedHelpers.callMethod(bgPref, "setVisible", newValue);
                            }
                            // 横屏壁纸选图入口（需同时判断横屏背景是否已启用）
                            Object bgPickPref = XposedHelpers.callMethod(
                                    screen, "findPreference", Const.PREF_LANDSCAPE_BG_PICK);
                            if (bgPickPref != null) {
                                boolean bgEnabled = context.getSharedPreferences(
                                        Const.PREF_FILE_LAUNCHER, Context.MODE_PRIVATE)
                                        .getBoolean(Const.PREF_LANDSCAPE_BG_ENABLED, false);
                                XposedHelpers.callMethod(bgPickPref, "setVisible", newValue && bgEnabled);
                            }
                            // 横屏显示初音（miku 主题未激活时可能不存在）
                            Object mikuLandPref = XposedHelpers.callMethod(
                                    screen, "findPreference", Const.PREF_SHOW_MIKU_LANDSCAPE);
                            if (mikuLandPref != null) {
                                XposedHelpers.callMethod(mikuLandPref, "setVisible", newValue);
                            }
                            return true;
                        }
                        return null;
                    }
            );
            XposedHelpers.callMethod(rotPref, "setOnPreferenceChangeListener", changeListener);
        }
    }
}
