package io.soragoto.m500.miku.plus.hook;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.soragoto.m500.miku.plus.R;

import java.util.concurrent.atomic.AtomicBoolean;

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

    private static final String TAG = "XposedHide";
    private static final String MODULE_PKG = "io.soragoto.m500.miku.plus";
    private static final String KEY_SHOW_LANDSCAPE = "pref_showMikuLandscape";
    private static final String KEY_SHOW_PORTRAIT = "pref_showMikuPortrait";

    /**
     * 标记 miku 是否被本 hook 主动隐藏（用于旋转回来时决定是否恢复）
     */
    private static final AtomicBoolean sHiddenByUs = new AtomicBoolean(false);
    /**
     * DisplayListener 只注册一次
     */
    private static final AtomicBoolean sListenerRegistered = new AtomicBoolean(false);


    /**
     * 通过 createPackageContext 加载模块自身的资源。
     * <p>
     * 原理：R.string.xxx 是编译期写入模块 APK 的整型 ID，
     * createPackageContext 返回的 Context 资源池来自同一个 APK，
     * 两边 ID 完全对应，Android 自动处理 locale/语言。
     * 不依赖任何 Xposed 专用 API。
     */
    private static Context getModuleContext(Context ctx) {
        try {
            Context moduleCtx = ctx.createPackageContext(
                    MODULE_PKG, Context.CONTEXT_IGNORE_SECURITY);
            log("getModuleContext: OK, locale="
                    + moduleCtx.getResources().getConfiguration().getLocales().get(0));
            return moduleCtx;
        } catch (Throwable t) {
            log("getModuleContext: FAILED err=" + t);
            return null;
        }
    }

    /**
     * 读取模块字符串资源，失败时返回 fallback。
     */
    private static String getString(Context moduleCtx, int resId, String fallback) {
        if (moduleCtx == null) {
            log("getString: moduleCtx=null, using fallback=\"" + fallback + "\"");
            return fallback;
        }
        try {
            String s = moduleCtx.getString(resId);
            log("getString: resId=0x" + Integer.toHexString(resId) + " -> \"" + s + "\"");
            return s;
        } catch (Throwable t) {
            log("getString: FAILED resId=0x" + Integer.toHexString(resId)
                    + " fallback=\"" + fallback + "\" err=" + t);
            return fallback;
        }
    }


    private static void log(String msg) {
        Log.d(TAG, msg);
        XposedBridge.log(TAG + ": " + msg);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (!"com.android.launcher3".equals(loadPackageParam.packageName)) return;
        log("handleLoadPackage: launcher3 loaded");
        hookSettings(loadPackageParam);
        hookShowFloatView(loadPackageParam);
    }

    /**
     * 在 Launcher 设置页添加两个 SwitchPreference
     */
    private void hookSettings(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        XposedHelpers.findAndHookMethod(
                "com.android.launcher3.settings.SettingsActivity$LauncherSettingsFragment",
                loadPackageParam.classLoader,
                "onCreatePreferences",
                Bundle.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object fragment = param.thisObject;
                        Object screen = XposedHelpers.callMethod(fragment, "getPreferenceScreen");

                        // 已存在则跳过，防止重复添加
                        if (XposedHelpers.callMethod(screen, "findPreference", KEY_SHOW_LANDSCAPE) != null) {
                            return;
                        }

                        Context context = (Context) XposedHelpers.callMethod(fragment, "requireContext");

                        // 仅在初音主题激活时才显示这两个开关
                        if (!isMikuThemeActive(context)) {
                            log("Miku theme not active, skipping preference injection");
                            return;
                        }

                        // 加载模块资源（支持 values-zh 等多语言），失败时回退到硬编码字符串
                        Context moduleCtx = getModuleContext(context);

                        Class<?> switchPrefClass = XposedHelpers.findClass(
                                "androidx.preference.SwitchPreference", loadPackageParam.classLoader);

                        // 开关 1：横屏时显示初音
                        Object prefLandscape = XposedHelpers.newInstance(switchPrefClass, context);
                        XposedHelpers.callMethod(prefLandscape, "setKey", KEY_SHOW_LANDSCAPE);
                        XposedHelpers.callMethod(prefLandscape, "setTitle",
                                getString(moduleCtx, R.string.pref_show_miku_landscape_title,
                                        "Show Miku in landscape"));
                        XposedHelpers.callMethod(prefLandscape, "setSummary",
                                getString(moduleCtx, R.string.pref_show_miku_landscape_summary,
                                        "Show the Miku pet when in landscape orientation"));
                        XposedHelpers.callMethod(prefLandscape, "setPersistent", true);
                        XposedHelpers.callMethod(prefLandscape, "setDefaultValue", true);
                        XposedHelpers.callMethod(screen, "addPreference", prefLandscape);

                        // 开关 2：竖屏时显示初音
                        Object prefPortrait = XposedHelpers.newInstance(switchPrefClass, context);
                        XposedHelpers.callMethod(prefPortrait, "setKey", KEY_SHOW_PORTRAIT);
                        XposedHelpers.callMethod(prefPortrait, "setTitle",
                                getString(moduleCtx, R.string.pref_show_miku_portrait_title,
                                        "Show Miku in portrait"));
                        XposedHelpers.callMethod(prefPortrait, "setSummary",
                                getString(moduleCtx, R.string.pref_show_miku_portrait_summary,
                                        "Show the Miku pet when in portrait orientation"));
                        XposedHelpers.callMethod(prefPortrait, "setPersistent", true);
                        XposedHelpers.callMethod(prefPortrait, "setDefaultValue", true);
                        XposedHelpers.callMethod(screen, "addPreference", prefPortrait);
                    }
                }
        );
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
                            boolean isLandscape = getCurrentIsLandscape(ctx);
                            SharedPreferences prefs = ctx.getSharedPreferences(
                                    "com.android.launcher3.prefs", Context.MODE_PRIVATE);
                            boolean showLandscape = prefs.getBoolean(KEY_SHOW_LANDSCAPE, true);
                            boolean showPortrait = prefs.getBoolean(KEY_SHOW_PORTRAIT, true);

                            log("showFloatView called | isLandscape=" + isLandscape
                                    + " | showLandscape=" + showLandscape
                                    + " | showPortrait=" + showPortrait);

                            if ((isLandscape && !showLandscape) || (!isLandscape && !showPortrait)) {
                                log("suppressing showFloatView");
                                param.setResult(null);
                                sHiddenByUs.set(true);
                            } else {
                                log("allowing showFloatView to proceed");
                            }
                        } catch (Throwable t) {
                            log("ERROR in beforeHookedMethod: " + t);
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            // miku 成功显示，不再处于我们主动隐藏的状态
                            Object floatView = XposedHelpers.getObjectField(param.thisObject, "mFloatView");
                            if (floatView != null) {
                                sHiddenByUs.set(false);
                            }
                            // 只注册一次 DisplayListener，监听旋转时主动 hide/show
                            if (sListenerRegistered.compareAndSet(false, true)) {
                                registerDisplayListener(param.thisObject);
                                log("DisplayListener registered");
                            }
                        } catch (Throwable t) {
                            log("ERROR in afterHookedMethod: " + t);
                        }
                    }
                }
        );
    }

    /**
     * 注册 DisplayListener，旋转时主动同步 miku 可见性
     */
    private static void registerDisplayListener(Object fvm) {
        Context ctx = (Context) XposedHelpers.getObjectField(fvm, "mContext");
        DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);

        Handler handler = new Handler(Looper.getMainLooper());
        final Runnable[] pendingCheck = {null};

        dm.registerDisplayListener(new DisplayListener() {
            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId != Display.DEFAULT_DISPLAY) return;
                if (pendingCheck[0] != null) handler.removeCallbacks(pendingCheck[0]);
                pendingCheck[0] = () -> {
                    try {
                        Context c = (Context) XposedHelpers.getObjectField(fvm, "mContext");
                        if (!isMikuThemeActive(c)) {
                            log("onDisplayChanged: Miku theme not active, skipping");
                            return;
                        }
                        boolean isLandscape = getCurrentIsLandscape(c);
                        SharedPreferences prefs = c.getSharedPreferences(
                                "com.android.launcher3.prefs", Context.MODE_PRIVATE);
                        boolean shouldHide = (isLandscape && !prefs.getBoolean(KEY_SHOW_LANDSCAPE, true))
                                || (!isLandscape && !prefs.getBoolean(KEY_SHOW_PORTRAIT, true));

                        Object floatView = XposedHelpers.getObjectField(fvm, "mFloatView");
                        log("onDisplayChanged | isLandscape=" + isLandscape
                                + " | shouldHide=" + shouldHide
                                + " | floatView=" + (floatView != null ? "visible" : "null")
                                + " | sHiddenByUs=" + sHiddenByUs.get());

                        if (shouldHide && floatView != null) {
                            // miku 在屏幕上，需要隐藏
                            XposedHelpers.callMethod(fvm, "hideFloatView");
                            sHiddenByUs.set(true);
                            log("DisplayListener: hidden miku");
                        } else if (!shouldHide && sHiddenByUs.get()) {
                            // 是我们主动隐藏的，旋转回来后恢复（beforeHookedMethod 会再次校验）
                            sHiddenByUs.set(false);
                            XposedHelpers.callMethod(fvm, "showFloatView");
                            log("DisplayListener: restored miku");
                            // 恢复后重新定位到右下角
                            Object restoredView = XposedHelpers.getObjectField(fvm, "mFloatView");
                            if (restoredView != null) {
                                repositionToBottomRight(fvm, restoredView, c);
                            }
                        } else if (!shouldHide && floatView != null) {
                            // miku 持续可见（横竖屏均开启），旋转后修正位置到右下角
                            repositionToBottomRight(fvm, floatView, c);
                            log("DisplayListener: repositioned miku to bottom-right");
                        }
                    } catch (Throwable t) {
                        log("ERROR in onDisplayChanged: " + t);
                    }
                };
                // 延迟 300ms，等旋转动画完成、API 值稳定后再读取
                handler.postDelayed(pendingCheck[0], 300);
            }

            @Override
            public void onDisplayAdded(int displayId) {
            }

            @Override
            public void onDisplayRemoved(int displayId) {
            }
        }, null);
    }


    private static boolean getCurrentIsLandscape(Context ctx) {
        int orientation = ctx.getResources().getConfiguration().orientation;
        log("orientation=" + orientation);
        return orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private static boolean isMikuThemeActive(Context ctx) {
        String theme = Settings.Global.getString(ctx.getContentResolver(), "device_theme");
        return "miku".equals(theme);
    }

    /**
     * 将 FloatMikuView 定位到屏幕右下角，距右边和底部各保留 15% 余量。
     */
    private static void repositionToBottomRight(Object fvm, Object floatView, Context ctx) {
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            Rect bounds = wm.getCurrentWindowMetrics().getBounds();
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
            log("repositionToBottomRight | x=" + lp.x + " y=" + lp.y
                    + " bounds=" + bounds.right + "x" + bounds.bottom);
        } catch (Throwable t) {
            log("ERROR in repositionToBottomRight: " + t);
        }
    }
}
