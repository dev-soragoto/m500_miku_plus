package io.soragoto.m500.miku.plus.hook;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.WindowManager;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.soragoto.m500.miku.plus.Const;
import io.soragoto.m500.miku.plus.R;
import io.soragoto.m500.miku.plus.util.BitmapUtils;
import io.soragoto.m500.miku.plus.util.XposedUtils;

import java.io.InputStream;
import java.lang.ref.WeakReference;

/**
 * 为 Launcher 横屏时提供独立的背景图。
 * <p>
 * 注册 DisplayListener 来监听旋转事件
 * <p>
 * 流程：
 * 1. hook Launcher.onCreate → 初始应用一次背景 + 注册 DisplayListener
 * 2. DisplayListener.onDisplayChanged (300ms debounce) → 根据当前方向动态切换背景
 * 3. 横屏 + 开关开启 → clearFlags(FLAG_SHOW_WALLPAPER) + 异步加载 bitmap 设为窗口背景
 * 4. 竖屏 / 开关关闭 → addFlags(FLAG_SHOW_WALLPAPER) + 清除自定义背景
 */
public class LandscapeWallpaperHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (!Const.LAUNCHER_PKG.equals(loadPackageParam.packageName)) return;
        XposedUtils.log("LandscapeWallpaperHook: launcher3 loaded");
        hookLauncherLifecycle(loadPackageParam);
        XposedUtils.hookLauncherSettings(loadPackageParam, "LandscapeWallpaperHook", this::injectPreferences);
    }

    // -------------------------------------------------------------------------
    // Hook 1 – Launcher Lifecycle (onCreate / onDestroy)
    // -------------------------------------------------------------------------

    private void hookLauncherLifecycle(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        // Hook onCreate
        XposedHelpers.findAndHookMethod(
                "com.android.launcher3.Launcher",
                loadPackageParam.classLoader,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Activity launcher = (Activity) param.thisObject;
                            if (!shouldApply(launcher)) return;
                            launcher.getWindow().clearFlags(
                                    WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
                            XposedUtils.log("onCreate before: FLAG_SHOW_WALLPAPER cleared");
                        } catch (Throwable t) {
                            XposedUtils.log("ERROR in onCreate before: " + t);
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Activity launcher = (Activity) param.thisObject;
                            if (shouldApply(launcher)) {
                                loadAndApplyAsync(launcher);
                            }
                            // Register listener for THIS instance
                            registerDisplayListener(launcher);
                        } catch (Throwable t) {
                            XposedUtils.log("ERROR in onCreate after: " + t);
                        }
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                "com.android.launcher3.Launcher",
                loadPackageParam.classLoader,
                "onDestroy",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Activity launcher = (Activity) param.thisObject;
                            unregisterDisplayListener(launcher);
                            XposedUtils.log("onDestroy: listener unregistered");
                        } catch (Throwable t) {
                            XposedUtils.log("ERROR in onDestroy: " + t);
                        }
                    }
                }
        );
    }

    // -------------------------------------------------------------------------
    // DisplayListener — 监听旋转，动态切换背景
    // -------------------------------------------------------------------------
    private static void registerDisplayListener(Activity launcher) {
        // Check if already registered (just in case)
        if (XposedHelpers.getAdditionalInstanceField(launcher, Const.FIELD_MIKU_LANDSCAPE_LISTENER) != null) return;

        Context appCtx = launcher.getApplicationContext();
        DisplayManager dm = (DisplayManager) appCtx.getSystemService(Context.DISPLAY_SERVICE);
        Handler handler = new Handler(Looper.getMainLooper());
        final Runnable[] pending = {null};

        // Capture launcher instance weakly to avoid leaks (though we unregister in onDestroy, safe to be sure)
        final WeakReference<Activity> launcherRef = new WeakReference<>(launcher);

        DisplayListener listener = new DisplayListener() {
            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId != Display.DEFAULT_DISPLAY) return;
                if (pending[0] != null) handler.removeCallbacks(pending[0]);
                pending[0] = () -> {
                    try {
                        Activity activity = launcherRef.get();
                        if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
                            return;
                        }
                        boolean landscape = XposedUtils.isLandscape(activity);
                        SharedPreferences prefs = activity.getSharedPreferences(
                                Const.PREF_FILE_LAUNCHER, Context.MODE_PRIVATE);
                        boolean enabled = prefs.getBoolean(
                                Const.PREF_LANDSCAPE_BG_ENABLED, false);

                        if (landscape && enabled) {
                            activity.getWindow().clearFlags(
                                    WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
                            loadAndApplyAsync(activity);
                        } else {
                            activity.getWindow().addFlags(
                                    WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
                            activity.getWindow().setBackgroundDrawable(
                                    new android.graphics.drawable.ColorDrawable(
                                            android.graphics.Color.TRANSPARENT));
                        }
                    } catch (Throwable t) {
                        XposedUtils.log("ERROR in DisplayListener: " + t);
                    }
                };
                handler.postDelayed(pending[0], 300);
            }

            @Override
            public void onDisplayAdded(int displayId) {
            }

            @Override
            public void onDisplayRemoved(int displayId) {
            }
        };

        dm.registerDisplayListener(listener, null);
        XposedHelpers.setAdditionalInstanceField(launcher, Const.FIELD_MIKU_LANDSCAPE_LISTENER, listener);
        XposedUtils.log("DisplayListener registered for instance: " + launcher.hashCode());
    }

    private static void unregisterDisplayListener(Activity launcher) {
        Object obj = XposedHelpers.getAdditionalInstanceField(launcher, Const.FIELD_MIKU_LANDSCAPE_LISTENER);
        if (obj instanceof DisplayListener) {
            Context appCtx = launcher.getApplicationContext();
            DisplayManager dm = (DisplayManager) appCtx.getSystemService(Context.DISPLAY_SERVICE);
            dm.unregisterDisplayListener((DisplayListener) obj);
            XposedHelpers.setAdditionalInstanceField(launcher, Const.FIELD_MIKU_LANDSCAPE_LISTENER, null);
        }
    }

    // -------------------------------------------------------------------------
    // 工具方法
    // -------------------------------------------------------------------------

    private static boolean shouldApply(Activity launcher) {
        if (!XposedUtils.isLandscape(launcher)) return false;
        SharedPreferences prefs = launcher.getSharedPreferences(
                Const.PREF_FILE_LAUNCHER, Context.MODE_PRIVATE);
        return prefs.getBoolean(Const.PREF_LANDSCAPE_BG_ENABLED, false);
    }

    /**
     * 在后台线程加载 bitmap，加载完成后在主线程设置窗口背景。
     * 主线程完全不阻塞。
     */
    private static void loadAndApplyAsync(Activity launcher) {
        Context appCtx = launcher.getApplicationContext();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            Bitmap bmp = loadBitmapFromProvider(appCtx);
            mainHandler.post(() -> {
                // Activity 可能已被销毁（切到其他 app 等情况）
                if (launcher.isDestroyed() || launcher.isFinishing()) return;
                if (bmp != null) {
                    BitmapDrawable drawable = new BitmapDrawable(appCtx.getResources(), bmp);
                    launcher.getWindow().setBackgroundDrawable(drawable);
                    XposedUtils.log("loadAndApplyAsync: background applied "
                            + bmp.getWidth() + "x" + bmp.getHeight());
                } else {
                    XposedUtils.log("loadAndApplyAsync: no bitmap, background unchanged");
                }
            });
        }, "LandscapeWallpaperLoader").start();
    }

    /**
     * 通过 ContentProvider 读取模块 App 私有目录中的横屏壁纸，按屏幕尺寸降采样。
     * 在后台线程调用。
     */
    private static Bitmap loadBitmapFromProvider(Context ctx) {
        Uri uri = Uri.parse(Const.URI_LANDSCAPE_BG);
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                XposedUtils.log("loadBitmapFromProvider: openInputStream returned null");
                return null;
            }
            byte[] bytes = BitmapUtils.readAllBytes(in);
            Bitmap bmp = BitmapUtils.decodeSampledFromBytes(bytes, Const.LANDSCAPE_BG_MAX_LONG_EDGE);
            XposedUtils.log("loadBitmapFromProvider: "
                    + (bmp != null ? bmp.getWidth() + "x" + bmp.getHeight() : "null"));
            return bmp;
        } catch (java.io.FileNotFoundException e) {
            XposedUtils.log("loadBitmapFromProvider: 未设置横屏壁纸 (FileNotFoundException)");
            return null;
        } catch (Exception e) {
            XposedUtils.log("loadBitmapFromProvider: ERROR " + e);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Hook 2 – 向 Launcher 设置页注入 Preference
    // -------------------------------------------------------------------------

    private void injectPreferences(
            Object screen,
            Context context,
            Context moduleCtx,
            ClassLoader classLoader) {
        // 防重复注入
        if (XposedUtils.prefExists(screen, Const.PREF_LANDSCAPE_BG_ENABLED)) return;

        // 开关：启用/禁用横屏背景
        XposedUtils.addSwitchPref(
                screen, classLoader, context,
                Const.PREF_LANDSCAPE_BG_ENABLED,
                XposedUtils.getString(moduleCtx, R.string.pref_landscape_bg_title, "横屏背景"),
                XposedUtils.getString(moduleCtx, R.string.pref_landscape_bg_summary, "横屏时使用独立壁纸"),
                false);
        Object bgSwitchPref = XposedHelpers.callMethod(screen, "findPreference", Const.PREF_LANDSCAPE_BG_ENABLED);

        // 入口：跳转到 LandscapeWallpaperActivity 选图
        Object pickPref = XposedUtils.addPref(
                screen, classLoader, context,
                Const.PREF_LANDSCAPE_BG_PICK,
                XposedUtils.getString(moduleCtx, R.string.pref_landscape_bg_pick_title, "设置横屏壁纸"),
                XposedUtils.getString(moduleCtx, R.string.pref_landscape_bg_pick_summary, "打开模块 App 选择图片"));
        SharedPreferences prefs = context.getSharedPreferences(Const.PREF_FILE_LAUNCHER, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(Const.PREF_LANDSCAPE_BG_ENABLED, false);
        boolean rotationEnabled = prefs.getBoolean(Const.PREF_ALLOW_ROTATION, false);
        XposedHelpers.callMethod(bgSwitchPref, "setVisible", rotationEnabled);
        XposedHelpers.callMethod(pickPref, "setVisible", rotationEnabled && enabled);

        Class<?> clickListenerClass = XposedHelpers.findClass(
                "androidx.preference.Preference$OnPreferenceClickListener",
                classLoader);
        Object clickListener = java.lang.reflect.Proxy.newProxyInstance(
                classLoader,
                new Class[]{clickListenerClass},
                (proxy, method, args) -> {
                    if ("onPreferenceClick".equals(method.getName())) {
                        try {
                            Intent intent = new Intent();
                            intent.setClassName(Const.MODULE_PKG,
                                    Const.MODULE_PKG + ".activity.LandscapeWallpaperActivity");
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                        } catch (Throwable t) {
                            XposedUtils.log("ERROR launching LandscapeWallpaperActivity: " + t);
                        }
                        return true;
                    }
                    return null;
                }
        );
        XposedHelpers.callMethod(pickPref, "setOnPreferenceClickListener", clickListener);
        if (bgSwitchPref != null) {
            Class<?> changeListenerClass = XposedHelpers.findClass(
                    "androidx.preference.Preference$OnPreferenceChangeListener",
                    classLoader);
            Object changeListener = java.lang.reflect.Proxy.newProxyInstance(
                    classLoader,
                    new Class[]{changeListenerClass},
                    (proxy, method, args) -> {
                        if ("onPreferenceChange".equals(method.getName())) {
                            boolean newValue = args != null
                                    && args.length > 1
                                    && args[1] instanceof Boolean
                                    && (Boolean) args[1];
                            boolean rotEnabled = context.getSharedPreferences(
                                            Const.PREF_FILE_LAUNCHER, Context.MODE_PRIVATE)
                                    .getBoolean(Const.PREF_ALLOW_ROTATION, false);
                            XposedHelpers.callMethod(pickPref, "setVisible", newValue && rotEnabled);
                            return true;
                        }
                        return null;
                    }
            );
            XposedHelpers.callMethod(bgSwitchPref, "setOnPreferenceChangeListener", changeListener);
        }

        XposedUtils.log("横屏背景 preferences 注入完成");
    }
}
