package io.soragoto.m500.miku.plus.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import io.soragoto.m500.miku.plus.Const;
import io.soragoto.m500.miku.plus.R;
import io.soragoto.m500.miku.plus.util.BitmapUtils;
import io.soragoto.m500.miku.plus.view.CropView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class CropActivity extends Activity {

    public static final String EXTRA_SOURCE_URI = "extra_source_uri";
    public static final int REQUEST_CROP = 2001;

    private static final String OUTPUT_FILENAME = "crop_output.jpg";

    private CropView cropView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        cropView = findViewById(R.id.cropView);

        Button btnCancel = findViewById(R.id.btnCropCancel);
        Button btnConfirm = findViewById(R.id.btnCropConfirm);

        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        btnConfirm.setOnClickListener(v -> onConfirm());

        Uri sourceUri = getIntent().getParcelableExtra(EXTRA_SOURCE_URI, Uri.class);
        if (sourceUri == null) {
            Log.e(Const.TAG, "CropActivity: no source URI");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        loadImage(sourceUri);
    }

    private void loadImage(Uri uri) {
        new Thread(() -> {
            try {
                Bitmap bmp = BitmapUtils.decodeSampledFromUri(
                        getContentResolver(), uri, Const.LANDSCAPE_BG_MAX_LONG_EDGE);
                if (bmp == null) throw new IOException("Failed to decode bitmap");
                runOnUiThread(() -> cropView.setImageBitmap(bmp));
            } catch (IOException e) {
                Log.e(Const.TAG, "CropActivity loadImage failed", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.landscape_bg_crop_failed, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_CANCELED);
                    finish();
                });
            }
        }).start();
    }

    private void onConfirm() {
        Bitmap cropped = cropView.getCroppedBitmap();
        if (cropped == null) return;

        new Thread(() -> {
            File out = new File(getCacheDir(), OUTPUT_FILENAME);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                if (!cropped.compress(Bitmap.CompressFormat.JPEG, 95, fos)) {
                    throw new IOException("Compress failed");
                }
                Uri resultUri = Uri.fromFile(out);
                Intent result = new Intent();
                result.setData(resultUri);
                runOnUiThread(() -> {
                    setResult(RESULT_OK, result);
                    finish();
                });
            } catch (IOException e) {
                Log.e(Const.TAG, "CropActivity onConfirm failed", e);
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.landscape_bg_crop_failed, Toast.LENGTH_SHORT).show()
                );
            } finally {
                cropped.recycle();
            }
        }).start();
    }
}
