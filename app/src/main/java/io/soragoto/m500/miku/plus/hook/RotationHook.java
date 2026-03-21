package io.soragoto.m500.miku.plus.hook;

import android.content.Context;
import android.os.Bundle;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class RotationHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (!loadPackageParam.packageName.equals("com.android.launcher3")) return;

        XposedHelpers.findAndHookMethod(
                "com.android.launcher3.settings.SettingsActivity$LauncherSettingsFragment",
                loadPackageParam.classLoader,
                "onCreatePreferences",
                Bundle.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object fragment = param.thisObject;
                        Object screen = XposedHelpers.callMethod(fragment, "getPreferenceScreen");

                        // 已存在则跳过（其他 launcher 已有该选项时不重复添加）
                        if (XposedHelpers.callMethod(screen, "findPreference", "pref_allowRotation") != null) {
                            return;
                        }

                        Context context = (Context) XposedHelpers.callMethod(fragment, "requireContext");

                        String lang = context.getResources().getConfiguration().getLocales().get(0).getLanguage();
                        boolean isChinese = "zh".equals(lang);
                        String title = isChinese ? "允许旋转" : "Allow rotation";
                        String summary = isChinese ? "允许主屏幕跟随设备传感器旋转" : "Allow the home screen to rotate with the device";

                        Class<?> switchPrefClass = XposedHelpers.findClass(
                                "androidx.preference.SwitchPreference", loadPackageParam.classLoader);
                        Object pref = XposedHelpers.newInstance(switchPrefClass, context);

                        XposedHelpers.callMethod(pref, "setKey", "pref_allowRotation");
                        XposedHelpers.callMethod(pref, "setTitle", title);
                        XposedHelpers.callMethod(pref, "setSummary", summary);
                        XposedHelpers.callMethod(pref, "setPersistent", true);
                        XposedHelpers.callMethod(pref, "setDefaultValue", false);
                        XposedHelpers.callMethod(screen, "addPreference", pref);
                    }
                }
        );
    }
}
