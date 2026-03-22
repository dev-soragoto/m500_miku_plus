package io.soragoto.m500.miku.plus.hook;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.soragoto.m500.miku.plus.Const;
import io.soragoto.m500.miku.plus.util.XposedUtils;

/**
 * 修复 Launcher3 横屏时 FloatMikuView 拖动边界异常问题。
 * <p>
 * winW / winH 只在首次 showFloatView() 时设置一次
 * <pre>{@code
 *     public void showFloatView() {
 *         if ("miku".equals(this.theme) && this.mFloatView == null) {
 *             Rect r = this.mWindowManager.getCurrentWindowMetrics().getBounds();
 *             FloatMikuView floatMikuView = new FloatMikuView(this.mContext);
 *             this.mFloatView = floatMikuView;
 *             floatMikuView.setLayoutParams(this.mLayoutParams, r != null ? r.right : 0, r != null ? r.bottom : 0);
 *             try {
 *                 this.mWindowManager.addView(this.mFloatView, this.mLayoutParams);
 *             } catch (Exception e) {
 *                 e.printStackTrace();
 *             }
 *         }
 *     }
 * }</pre>
 * <p>
 * handleDrag 用过期的 winW/winH 做边界夹取，横屏后出错
 * <pre>{@code
 *     protected void handleDrag(MotionEvent event) {
 *         super.handleDrag(event);
 *         int x = (int) event.getRawX();
 *         int y = (int) event.getRawY();
 *         switch (event.getAction()) {
 *             case 1:
 *                 if (this.isDragging) {
 *                     this.mLastX = 0;
 *                     this.mLastY = 0;
 *                 }
 *                 break;
 *             case 2:
 *                 if (this.mLastX == 0) { this.mLastX = x; }
 *                 if (this.mLastY == 0) { this.mLastY = y; }
 *                 int dx = x - this.mLastX;
 *                 int dy = y - this.mLastY;
 *                 this.mLayoutParams.x += dx;
 *                 this.mLayoutParams.y += dy;
 *                 // X 夹取：winW 竖屏时 ≈1080，横屏实际 ≈2340 → maxX 偏小，右半屏不可达
 *                 if (this.mLayoutParams.x < 0) {
 *                     this.mLayoutParams.x = 0;
 *                 } else if (this.mLayoutParams.x > this.winW - this.mLayoutParams.width) {
 *                     this.mLayoutParams.x = this.winW - this.mLayoutParams.width;
 *                 }
 *                 // Y 夹取：winH 竖屏时 ≈2340，横屏实际 ≈1080 → maxY 偏大，可拖出屏幕底部
 *                 if (this.mLayoutParams.y < 0) {
 *                     this.mLayoutParams.y = 0;
 *                 } else if (this.mLayoutParams.y > this.winH - this.mLayoutParams.height) {
 *                     this.mLayoutParams.y = this.winH - this.mLayoutParams.height;
 *                 }
 *                 this.mWindowManager.updateViewLayout(this, this.mLayoutParams);
 *                 this.mLastX = x;
 *                 this.mLastY = y;
 *                 this.isDragging = true;
 *                 break;
 *         }
 *     }
 * }</pre>
 * ─────────────────────────────────────────────────────────────────────────
 * 在 handleDrag 每次执行前覆写 winW/winH 为当前实际屏幕尺寸。
 */
public class MikuFixHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (!Const.LAUNCHER_PKG.equals(loadPackageParam.packageName)) {
            return;
        }

        XposedHelpers.findAndHookMethod(
                "com.android.launcher3.floatmiku.FloatMikuView",
                loadPackageParam.classLoader,
                "handleDrag",
                MotionEvent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Object thisObj = param.thisObject;
                            Context ctx = (Context) XposedHelpers.getObjectField(thisObj, "mContext");
                            Rect bounds = XposedUtils.getWindowBounds(ctx);
                            XposedHelpers.setIntField(thisObj, "winW", bounds.right);
                            XposedHelpers.setIntField(thisObj, "winH", bounds.bottom);
                        } catch (Throwable ignored) {
                        }
                    }
                }
        );
    }
}
