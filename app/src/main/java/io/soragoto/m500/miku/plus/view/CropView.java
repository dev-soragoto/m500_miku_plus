package io.soragoto.m500.miku.plus.view;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import org.jetbrains.annotations.NotNull;

/**
 * A view that lets the user pan and pinch-zoom an image, then crop it
 * to a fixed 16:9 aspect ratio.
 */
public class CropView extends View {

    private static final int CROP_ASPECT_W = 16;
    private static final int CROP_ASPECT_H = 9;
    private static final float CROP_FRAME_FRACTION = 0.90f; // crop rect occupies 90% of view width
    private static final int OVERLAY_ALPHA = 160;           // 0-255, semi-transparent dim

    private Bitmap bitmap;

    // Transform applied to the bitmap for display
    private final Matrix imageMatrix = new Matrix();
    private final Matrix inverseMatrix = new Matrix();

    // The crop rectangle in view coordinates (computed in onSizeChanged)
    private final RectF cropRect = new RectF();

    // Paints
    private final Paint overlayPaint = new Paint();
    private final Paint borderPaint = new Paint();

    // Gesture detectors
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;

    public CropView(Context context) {
        super(context);
        init(context);
    }

    public CropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        overlayPaint.setColor(0x00000000);
        overlayPaint.setAlpha(OVERLAY_ALPHA);
        overlayPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(0xFFFFFFFF);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f * context.getResources().getDisplayMetrics().density);
        borderPaint.setAntiAlias(true);

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, @NotNull MotionEvent e2, float distanceX, float distanceY) {
                imageMatrix.postTranslate(-distanceX, -distanceY);
                clampMatrix();
                invalidate();
                return true;
            }
        });

        scaleGestureDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(@NotNull ScaleGestureDetector detector) {
                        float factor = detector.getScaleFactor();
                        float focusX = detector.getFocusX();
                        float focusY = detector.getFocusY();
                        imageMatrix.postScale(factor, factor, focusX, focusY);
                        clampMatrix();
                        invalidate();
                        return true;
                    }
                });
    }

    public void setImageBitmap(Bitmap bmp) {
        this.bitmap = bmp;
        if (getWidth() > 0 && getHeight() > 0) {
            resetMatrix();
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        computeCropRect(w, h);
        if (bitmap != null) resetMatrix();
    }

    private void computeCropRect(int viewW, int viewH) {
        float cropW = viewW * CROP_FRAME_FRACTION;
        float cropH = cropW * CROP_ASPECT_H / CROP_ASPECT_W;
        if (cropH > viewH * CROP_FRAME_FRACTION) {
            cropH = viewH * CROP_FRAME_FRACTION;
            cropW = cropH * CROP_ASPECT_W / CROP_ASPECT_H;
        }
        float left = (viewW - cropW) / 2f;
        float top = (viewH - cropH) / 2f;
        cropRect.set(left, top, left + cropW, top + cropH);
    }

    /**
     * Scale image to fill crop rect, centered.
     */
    private void resetMatrix() {
        if (bitmap == null) return;
        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();

        float scaleX = cropRect.width() / bw;
        float scaleY = cropRect.height() / bh;
        float scale = Math.max(scaleX, scaleY); // fill (cover) crop rect

        float scaledW = bw * scale;
        float scaledH = bh * scale;
        float tx = cropRect.centerX() - scaledW / 2f;
        float ty = cropRect.centerY() - scaledH / 2f;

        imageMatrix.reset();
        imageMatrix.postScale(scale, scale);
        imageMatrix.postTranslate(tx, ty);
    }

    /**
     * After any gesture, constrain the matrix so the image still fully covers
     * the crop rect and doesn't become smaller than a min scale.
     */
    private void clampMatrix() {
        if (bitmap == null) return;
        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();

        // Min scale: image must cover crop rect
        float minScaleX = cropRect.width() / bw;
        float minScaleY = cropRect.height() / bh;
        float minScale = Math.max(minScaleX, minScaleY);

        // Extract current scale
        float[] values = new float[9];
        imageMatrix.getValues(values);
        float currentScale = values[Matrix.MSCALE_X];

        if (currentScale < minScale) {
            float cx = values[Matrix.MTRANS_X] + bw * currentScale / 2f;
            float cy = values[Matrix.MTRANS_Y] + bh * currentScale / 2f;
            imageMatrix.postScale(minScale / currentScale, minScale / currentScale, cx, cy);
            imageMatrix.getValues(values);
        }

        float scaledW = bw * values[Matrix.MSCALE_X];
        float scaledH = bh * values[Matrix.MSCALE_Y];
        float tx = values[Matrix.MTRANS_X];
        float ty = values[Matrix.MTRANS_Y];

        float maxTx = cropRect.left;
        float minTx = cropRect.right - scaledW;
        float maxTy = cropRect.top;
        float minTy = cropRect.bottom - scaledH;

        tx = Math.max(minTx, Math.min(maxTx, tx));
        ty = Math.max(minTy, Math.min(maxTy, ty));
        values[Matrix.MTRANS_X] = tx;
        values[Matrix.MTRANS_Y] = ty;
        imageMatrix.setValues(values);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = scaleGestureDetector.onTouchEvent(event);
        handled = gestureDetector.onTouchEvent(event) || handled;
        if (event.getAction() == MotionEvent.ACTION_UP) {
            performClick();
        }
        return handled || super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDraw(@NotNull Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) return;

        // Draw image
        canvas.drawBitmap(bitmap, imageMatrix, null);

        // Draw dim overlay (4 rectangles around crop rect)
        int w = getWidth();
        int h = getHeight();
        overlayPaint.setColor((OVERLAY_ALPHA << 24));
        canvas.drawRect(0, 0, w, cropRect.top, overlayPaint);
        canvas.drawRect(0, cropRect.bottom, w, h, overlayPaint);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint);
        canvas.drawRect(cropRect.right, cropRect.top, w, cropRect.bottom, overlayPaint);

        // Draw crop border
        canvas.drawRect(cropRect, borderPaint);
    }

    /**
     * Returns the cropped bitmap corresponding to the current crop rect.
     * The caller is responsible for recycling the returned bitmap.
     */
    public Bitmap getCroppedBitmap() {
        if (bitmap == null) return null;

        // Invert the display matrix to map crop rect → bitmap coordinates
        imageMatrix.invert(inverseMatrix);

        float[] pts = {
                cropRect.left, cropRect.top,
                cropRect.right, cropRect.bottom
        };
        inverseMatrix.mapPoints(pts);

        int srcX = Math.round(pts[0]);
        int srcY = Math.round(pts[1]);
        int srcW = Math.round(pts[2] - pts[0]);
        int srcH = Math.round(pts[3] - pts[1]);

        // Clamp to bitmap bounds
        srcX = Math.max(0, Math.min(srcX, bitmap.getWidth() - 1));
        srcY = Math.max(0, Math.min(srcY, bitmap.getHeight() - 1));
        srcW = Math.max(1, Math.min(srcW, bitmap.getWidth() - srcX));
        srcH = Math.max(1, Math.min(srcH, bitmap.getHeight() - srcY));

        return Bitmap.createBitmap(bitmap, srcX, srcY, srcW, srcH);
    }
}
