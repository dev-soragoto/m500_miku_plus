package io.soragoto.m500.miku.plus.hook;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.soragoto.m500.miku.plus.Const;
import io.soragoto.m500.miku.plus.R;
import io.soragoto.m500.miku.plus.util.XposedUtils;

import java.lang.ref.WeakReference;

/**
 * 在 Launcher 设置页新增两个开关，控制 Miku 在横/竖屏时的显示。
 * <p>
 * 添加两个 SwitchPreference，key 已存在则跳过
 *
 * <ul>
 *   <li>{@code pref_showMikuLandscape} — 横屏时显示初音（默认开）</li>
 *   <li>{@code pref_showMikuPortrait}  — 竖屏时显示初音（默认开）</li>
 * </ul>
 *
 * <p>Hook 2：FloatViewManager.showFloatView（beforeHookedMethod）
 * <pre>{@code
 *     Launcher onResume 调用路径：
 *     handler.postDelayed(showFloatViewTask, 200)
 *     -> showFloatViewTask (Launcher$4.smali)
 *     -> FloatViewManager.getInstance(ctx).showFloatView()
 *
 *     旋转时 Activity 重建（Launcher 无 onConfigurationChanged 覆写），
 *     onResume 必然重新触发，在此处拦截即可
 *     public void showFloatView() {
 *         if (!"miku".equals(this.theme)) return;       // 原始逻辑：非 miku 主题直接返回
 *         if (this.mFloatView != null) return;           // 已在显示则跳过
 *         // ... 下面才真正 addView
 *     }
 * }</pre>
 * 我们在 before 中检查朝向 + 偏好，匹配则 setResult(null) 取消整个方法。
 */
public class MikuVisibilityHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (!Const.LAUNCHER_PKG.equals(loadPackageParam.packageName)) return;
        XposedUtils.log("handleLoadPackage: launcher3 loaded");
        XposedUtils.hookLauncherSettings(loadPackageParam, "MikuVisibilityHook", this::injectPreferences);
        hookShowFloatView(loadPackageParam);
        hookLauncherDestroy(loadPackageParam);
    }

    private void injectPreferences(Object screen, Context context, Context moduleCtx, ClassLoader classLoader) {
        if (XposedUtils.prefExists(screen, Const.PREF_SHOW_MIKU_LANDSCAPE)) return;

        if (!isMikuThemeActive(context)) {
            XposedUtils.log("Miku theme not active, skipping preference injection");
            return;
        }

        XposedUtils.addSwitchPref(
                screen, classLoader, context,
                Const.PREF_SHOW_MIKU_LANDSCAPE,
                XposedUtils.getString(moduleCtx,
                        R.string.pref_show_miku_landscape_title, "Show Miku in landscape"),
                XposedUtils.getString(moduleCtx,
                        R.string.pref_show_miku_landscape_summary,
                        "Show the Miku pet when in landscape orientation"),
                true);

        SharedPreferences mikuPrefs = context.getSharedPreferences(
                Const.PREF_FILE_LAUNCHER, Context.MODE_PRIVATE);
        boolean rotationEnabled = mikuPrefs.getBoolean(Const.PREF_ALLOW_ROTATION, false);
        Object landscapePref = XposedHelpers.callMethod(screen, "findPreference", Const.PREF_SHOW_MIKU_LANDSCAPE);
        if (landscapePref != null) {
            XposedHelpers.callMethod(landscapePref, "setVisible", rotationEnabled);
        }

        XposedUtils.addSwitchPref(
                screen, classLoader, context,
                Const.PREF_SHOW_MIKU_PORTRAIT,
                XposedUtils.getString(moduleCtx,
                        R.string.pref_show_miku_portrait_title, "Show Miku in portrait"),
                XposedUtils.getString(moduleCtx,
                        R.string.pref_show_miku_portrait_summary,
                        "Show the Miku pet when in portrait orientation"),
                true);
    }

    /**
     * 拦截 showFloatView，根据当前朝向和偏好决定是否取消显示；并注册旋转监听。
     */
    private void hookShowFloatView(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        XposedHelpers.findAndHookMethod(
                "com.android.launcher3.floatmiku.FloatViewManager",
                loadPackageParam.classLoader,
                "showFloatView",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Context ctx = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                            boolean isLandscape = XposedUtils.isLandscape(ctx);
                            SharedPreferences prefs = ctx.getSharedPreferences(
                                    Const.PREF_FILE_LAUNCHER, Context.MODE_PRIVATE);
                            boolean showLandscape = prefs.getBoolean(Const.PREF_SHOW_MIKU_LANDSCAPE, true);
                            boolean showPortrait = prefs.getBoolean(Const.PREF_SHOW_MIKU_PORTRAIT, true);

                            XposedUtils.log("showFloatView called | isLandscape=" + isLandscape
                                    + " | showLandscape=" + showLandscape
                                    + " | showPortrait=" + showPortrait);

                            if ((isLandscape && !showLandscape) || (!isLandscape && !showPortrait)) {
                                XposedUtils.log("suppressing showFloatView");
                                param.setResult(null);
                                // Mark as hidden by us on THIS instance
                                XposedHelpers.setAdditionalInstanceField(param.thisObject, Const.FIELD_MIKU_HIDDEN_BY_US, true);
                            } else {
                                XposedUtils.log("allowing showFloatView to proceed");
                            }
                        } catch (Throwable t) {
                            XposedUtils.log("ERROR in beforeHookedMethod: " + t);
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object fvm = param.thisObject;
                            // miku 成功显示，重置隐藏标记
                            Object floatView = XposedHelpers.getObjectField(fvm, "mFloatView");
                            if (floatView != null) {
                                XposedHelpers.setAdditionalInstanceField(fvm, Const.FIELD_MIKU_HIDDEN_BY_US, false);
                            }

                            Object existingListener = XposedHelpers.getAdditionalInstanceField(fvm, Const.FIELD_MIKU_LISTENER);
                            if (existingListener == null) {
                                registerDisplayListener(fvm);
                                XposedUtils.log("DisplayListener registered for FVM instance: " + fvm.hashCode());
                            }
                        } catch (Throwable t) {
                            XposedUtils.log("ERROR in afterHookedMethod: " + t);
                        }
                    }
                }
        );
    }

    /**
     * Hook Launcher onDestroy to clean up listeners
     */
    private void hookLauncherDestroy(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        XposedHelpers.findAndHookMethod(
                "com.android.launcher3.Launcher",
                loadPackageParam.classLoader,
                "onDestroy",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Activity launcher = (Activity) param.thisObject;
                            Class<?> fvmClass = XposedHelpers.findClass("com.android.launcher3.floatmiku.FloatViewManager", loadPackageParam.classLoader);
                            Object fvm = XposedHelpers.callStaticMethod(fvmClass, "getInstance", launcher);

                            if (fvm != null) {
                                unregisterDisplayListener(fvm);
                                XposedUtils.log("Launcher onDestroy: unregistered listener for FVM");
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
        );
    }

    /**
     * 注册 DisplayListener
     */
    private static void registerDisplayListener(Object fvm) {
        Context ctx = (Context) XposedHelpers.getObjectField(fvm, "mContext");
        DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);

        Handler handler = new Handler(Looper.getMainLooper());
        final Runnable[] pendingCheck = {null};

        // WeakReference to FVM to avoid leaks inside the listener
        final WeakReference<Object> fvmRef = new WeakReference<>(fvm);

        DisplayListener listener = new DisplayListener() {
            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId != Display.DEFAULT_DISPLAY) return;
                if (pendingCheck[0] != null) handler.removeCallbacks(pendingCheck[0]);
                pendingCheck[0] = () -> {
                    try {
                        Object fvmInstance = fvmRef.get();
                        if (fvmInstance == null) return;

                        Context c = (Context) XposedHelpers.getObjectField(fvmInstance, "mContext");
                        if (!isMikuThemeActive(c)) return;

                        boolean isLandscape = XposedUtils.isLandscape(c);
                        SharedPreferences prefs = c.getSharedPreferences(
                                Const.PREF_FILE_LAUNCHER, Context.MODE_PRIVATE);
                        boolean shouldHide = (isLandscape && !prefs.getBoolean(Const.PREF_SHOW_MIKU_LANDSCAPE, true))
                                || (!isLandscape && !prefs.getBoolean(Const.PREF_SHOW_MIKU_PORTRAIT, true));

                        Object floatView = XposedHelpers.getObjectField(fvmInstance, "mFloatView");
                        Object hiddenByUsObj = XposedHelpers.getAdditionalInstanceField(fvmInstance, Const.FIELD_MIKU_HIDDEN_BY_US);
                        boolean hiddenByUs = hiddenByUsObj != null && (boolean) hiddenByUsObj;

                        if (shouldHide && floatView != null) {
                            XposedHelpers.callMethod(fvmInstance, "hideFloatView");
                            XposedHelpers.setAdditionalInstanceField(fvmInstance, Const.FIELD_MIKU_HIDDEN_BY_US, true);
                        } else if (!shouldHide && hiddenByUs) {
                            XposedHelpers.setAdditionalInstanceField(fvmInstance, Const.FIELD_MIKU_HIDDEN_BY_US, false);
                            XposedHelpers.callMethod(fvmInstance, "showFloatView");
                            // Restore position
                            Object restoredView = XposedHelpers.getObjectField(fvmInstance, "mFloatView");
                            if (restoredView != null) {
                                repositionToBottomRight(fvmInstance, restoredView, c);
                            }
                        } else if (!shouldHide && floatView != null) {
                            repositionToBottomRight(fvmInstance, floatView, c);
                        }
                    } catch (Throwable t) {
                        XposedUtils.log("ERROR in onDisplayChanged: " + t);
                    }
                };
                handler.postDelayed(pendingCheck[0], 300);
            }

            @Override
            public void onDisplayAdded(int displayId) {
            }

            @Override
            public void onDisplayRemoved(int displayId) {
            }
        };

        dm.registerDisplayListener(listener, null);
        XposedHelpers.setAdditionalInstanceField(fvm, Const.FIELD_MIKU_LISTENER, listener);
    }

    private static void unregisterDisplayListener(Object fvm) {
        Object listener = XposedHelpers.getAdditionalInstanceField(fvm, Const.FIELD_MIKU_LISTENER);
        if (listener instanceof DisplayListener) {
            Context ctx = (Context) XposedHelpers.getObjectField(fvm, "mContext");
            DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
            dm.unregisterDisplayListener((DisplayListener) listener);
            XposedHelpers.setAdditionalInstanceField(fvm, Const.FIELD_MIKU_LISTENER, null);
        }
    }

    private static boolean isMikuThemeActive(Context ctx) {
        return XposedUtils.isMikuThemeActive(ctx);
    }

    private static void repositionToBottomRight(Object fvm, Object floatView, Context ctx) {
        try {
            Rect bounds = XposedUtils.getWindowBounds(ctx);
            int marginRight = (int) (bounds.right * 0.05f);
            int marginBottom = (int) (bounds.bottom * 0.20f);
            WindowManager.LayoutParams lp =
                    (WindowManager.LayoutParams)
                            XposedHelpers.getObjectField(floatView, "mLayoutParams");
            lp.x = bounds.right - lp.width - marginRight;
            lp.y = bounds.bottom - lp.height - marginBottom;
            WindowManager fvmWm = (WindowManager)
                    XposedHelpers.getObjectField(fvm, "mWindowManager");
            fvmWm.updateViewLayout((View) floatView, lp);
            XposedUtils.log("repositionToBottomRight | x=" + lp.x + " y=" + lp.y
                    + " bounds=" + bounds.right + "x" + bounds.bottom);
        } catch (Throwable t) {
            XposedUtils.log("ERROR in repositionToBottomRight: " + t);
        }
    }
}
