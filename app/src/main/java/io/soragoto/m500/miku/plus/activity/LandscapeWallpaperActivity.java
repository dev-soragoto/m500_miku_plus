package io.soragoto.m500.miku.plus.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import io.soragoto.m500.miku.plus.Const;
import io.soragoto.m500.miku.plus.R;
import io.soragoto.m500.miku.plus.util.BitmapUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class LandscapeWallpaperActivity extends Activity {

    private static final int REQUEST_PICK_IMAGE = 1001;

    private ImageView imgPreview;
    private TextView txtNoImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landscape_wallpaper);

        imgPreview = findViewById(R.id.imgLandscapeBgPreview);
        txtNoImage = findViewById(R.id.txtLandscapeBgNoImage);

        Button btnPick = findViewById(R.id.btnPickLandscapeBg);
        btnPick.setOnClickListener(v -> launchImagePicker());

        Button btnClear = findViewById(R.id.btnClearLandscapeBg);
        btnClear.setOnClickListener(v -> clearLandscapeBg());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPreview();
    }

    private void launchImagePicker() {
        Log.d(Const.TAG, "LandscapeWallpaperActivity launchImagePicker");
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE) {
            Log.d(Const.TAG, "onActivityResult pick: resultCode=" + resultCode);
            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    Log.d(Const.TAG, "pick image uri=" + uri);
                    launchCrop(uri);
                }
            }
            return;
        }
        if (requestCode == CropActivity.REQUEST_CROP) {
            Log.d(Const.TAG, "onActivityResult crop: resultCode=" + resultCode);
            if (resultCode == RESULT_OK && data != null) {
                Uri resultUri = data.getData();
                if (resultUri != null) {
                    saveLandscapeBg(resultUri);
                }
            } else if (resultCode != RESULT_CANCELED) {
                Toast.makeText(this, R.string.landscape_bg_crop_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void launchCrop(Uri sourceUri) {
        Log.d(Const.TAG, "launchCrop source=" + sourceUri);
        Intent intent = new Intent(this, CropActivity.class);
        intent.putExtra(CropActivity.EXTRA_SOURCE_URI, sourceUri);
        startActivityForResult(intent, CropActivity.REQUEST_CROP);
    }

    private void saveLandscapeBg(Uri uri) {
        Log.d(Const.TAG, "saveLandscapeBg uri=" + uri);
        Bitmap decoded = null;
        try {
            decoded = BitmapUtils.decodeSampledFromUri(
                    getContentResolver(), uri, Const.LANDSCAPE_BG_MAX_LONG_EDGE);
            if (decoded == null) throw new IOException("Failed to decode bitmap");

            File dest = new File(getFilesDir(), Const.LANDSCAPE_BG_FILENAME);
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                if (!decoded.compress(Bitmap.CompressFormat.JPEG, 90, fos)) {
                    throw new IOException("Failed to compress bitmap");
                }
            }
            Log.d(Const.TAG, "saveLandscapeBg ok: " + dest.getAbsolutePath());
            Toast.makeText(this, R.string.landscape_bg_set_ok, Toast.LENGTH_SHORT).show();
            refreshPreview();
        } catch (IOException | IllegalArgumentException e) {
            Log.e(Const.TAG, "saveLandscapeBg failed", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (decoded != null && !decoded.isRecycled()) decoded.recycle();
            // Clean up crop temp output file
            if ("file".equals(uri.getScheme())) {
                String path = uri.getPath();
                if (path != null) {
                    File tmp = new File(path);
                    if (tmp.exists() && !tmp.delete()) {
                        Log.w(Const.TAG, "Failed to delete temp file: " + tmp.getAbsolutePath());
                    }
                }
            }
        }
    }

    private void clearLandscapeBg() {
        File file = new File(getFilesDir(), Const.LANDSCAPE_BG_FILENAME);
        if (file.exists() && file.delete()) {
            Log.d(Const.TAG, "clear Landscape Bg ok: " + file.getAbsolutePath());
        }
        Toast.makeText(this, R.string.landscape_bg_cleared, Toast.LENGTH_SHORT).show();
        refreshPreview();
    }

    private void refreshPreview() {
        File file = new File(getFilesDir(), Const.LANDSCAPE_BG_FILENAME);
        if (file.exists()) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);

            int targetW = imgPreview.getWidth() > 0 ? imgPreview.getWidth() : 1024;
            int targetH = imgPreview.getHeight() > 0 ? imgPreview.getHeight() : 256;
            int maxEdge = Math.max(targetW, targetH);
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = BitmapUtils.calculateInSampleSize(opts.outWidth, opts.outHeight, maxEdge);

            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            imgPreview.setImageBitmap(bmp);
            imgPreview.setVisibility(View.VISIBLE);
            txtNoImage.setVisibility(View.GONE);
        } else {
            imgPreview.setImageBitmap(null);
            imgPreview.setVisibility(View.GONE);
            txtNoImage.setVisibility(View.VISIBLE);
        }
    }
}

