package io.soragoto.m500.miku.plus.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.annotation.NonNull;
import io.soragoto.m500.miku.plus.Const;

import java.io.File;
import java.io.FileNotFoundException;

public class LandscapeWallpaperProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        Log.d(Const.TAG, "LandscapeWallpaperProvider onCreate");
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Log.w(Const.TAG, "LandscapeWallpaperProvider query not supported, uri=" + uri);
        return null;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        String path = uri.getPath();
        if ("/landscape_bg".equals(path) || "/landscape_bg_crop_out".equals(path)) {
            return "image/jpeg";
        }
        Log.w(Const.TAG, "LandscapeWallpaperProvider getType unknown path: " + uri);
        return null;
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        Log.w(Const.TAG, "LandscapeWallpaperProvider insert not supported, uri=" + uri);
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        Log.w(Const.TAG, "LandscapeWallpaperProvider delete not supported, uri=" + uri);
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        Log.w(Const.TAG, "LandscapeWallpaperProvider update not supported, uri=" + uri);
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        Context ctx = getContext();
        if (ctx == null) {
            Log.e(Const.TAG, "LandscapeWallpaperProvider openFile failed: context is null");
            throw new FileNotFoundException("Provider context is null");
        }

        String path = uri.getPath();
        if ("/landscape_bg".equals(path)) {
            File file = new File(ctx.getFilesDir(), Const.LANDSCAPE_BG_FILENAME);
            if (!file.exists()) {
                Log.w(Const.TAG, "LandscapeWallpaperProvider openFile landscape_bg not set");
                throw new FileNotFoundException("Landscape background not set");
            }
            Log.d(Const.TAG, "LandscapeWallpaperProvider openFile read landscape_bg");
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        }

        if ("/landscape_bg_crop_out".equals(path)) {
            File file = new File(ctx.getCacheDir(), Const.LANDSCAPE_BG_CROP_TMP_FILENAME);
            switch (mode) {
                case "r" -> {
                    if (!file.exists()) {
                        Log.w(Const.TAG, "LandscapeWallpaperProvider openFile crop_out not found for read");
                        throw new FileNotFoundException("Crop output not found");
                    }
                    Log.d(Const.TAG, "LandscapeWallpaperProvider openFile read crop_out");
                    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                }
                case "w", "wt" -> {
                    int flags = ParcelFileDescriptor.MODE_CREATE
                            | ParcelFileDescriptor.MODE_TRUNCATE
                            | ParcelFileDescriptor.MODE_WRITE_ONLY;
                    Log.d(Const.TAG, "LandscapeWallpaperProvider openFile write crop_out mode=" + mode);
                    return ParcelFileDescriptor.open(file, flags);
                }
                case "rw", "rwt" -> {
                    int flags = ParcelFileDescriptor.MODE_CREATE
                            | ParcelFileDescriptor.MODE_TRUNCATE
                            | ParcelFileDescriptor.MODE_READ_WRITE;
                    Log.d(Const.TAG, "LandscapeWallpaperProvider openFile read-write crop_out mode=" + mode);
                    return ParcelFileDescriptor.open(file, flags);
                }
            }
            Log.w(Const.TAG, "LandscapeWallpaperProvider openFile unsupported mode=" + mode);
            throw new FileNotFoundException("Unsupported mode for crop output: " + mode);
        }

        Log.w(Const.TAG, "LandscapeWallpaperProvider openFile unknown uri=" + uri + ", mode=" + mode);
        throw new FileNotFoundException("Unknown URI: " + uri);
    }
}
