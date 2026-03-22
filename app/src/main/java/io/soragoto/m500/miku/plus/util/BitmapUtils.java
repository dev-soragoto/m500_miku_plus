package io.soragoto.m500.miku.plus.util;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class BitmapUtils {

    private BitmapUtils() {
    }

    public static int calculateInSampleSize(int outWidth, int outHeight, int maxLongEdge) {
        int longest = Math.max(outWidth, outHeight);
        int sample = 1;
        while (longest / (sample * 2) > maxLongEdge) sample *= 2;
        return Math.max(sample, 1);
    }

    public static Bitmap decodeSampledFromBytes(byte[] bytes, int maxLongEdge) {
        BitmapFactory.Options boundsOpts = new BitmapFactory.Options();
        boundsOpts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, boundsOpts);

        int sample = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxLongEdge);
        BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
        decodeOpts.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decodeOpts);
    }

    public static Bitmap decodeSampledFromUri(ContentResolver resolver, Uri uri, int maxLongEdge) throws IOException {
        BitmapFactory.Options boundsOpts = new BitmapFactory.Options();
        boundsOpts.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open: " + uri);
            BitmapFactory.decodeStream(in, null, boundsOpts);
        }

        int sample = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxLongEdge);
        BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
        decodeOpts.inSampleSize = sample;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot re-open: " + uri);
            return BitmapFactory.decodeStream(in, null, decodeOpts);
        }
    }

    public static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
        return buf.toByteArray();
    }

}
