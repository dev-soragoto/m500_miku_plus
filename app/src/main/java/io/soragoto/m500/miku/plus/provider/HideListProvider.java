package io.soragoto.m500.miku.plus.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import io.soragoto.m500.miku.plus.Const;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Set;

import static io.soragoto.m500.miku.plus.Const.COL_PACKAGE;

public class HideListProvider extends ContentProvider {


    @Override
    public boolean onCreate() {
        Log.d(Const.TAG, "HideListProvider onCreate");
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Context ctx = getContext();
        if (ctx == null) {
            Log.w(Const.TAG, "HideListProvider query: context is null, uri=" + uri);
            return null;
        }
        SharedPreferences prefs = ctx.getSharedPreferences(
                Const.PREF_FILE_MODULE, Context.MODE_PRIVATE);

        if ("/hooked".equals(uri.getPath())) {
            Set<String> hooked = prefs.getStringSet(
                    Const.PREF_HOOKED_PACKAGES, new HashSet<>());
            Log.d(Const.TAG, "HideListProvider query hooked: size=" + hooked.size());
            MatrixCursor cursor = new MatrixCursor(new String[]{COL_PACKAGE});
            for (String pkg : hooked) cursor.addRow(new Object[]{pkg});
            return cursor;
        }

        Set<String> hidden = prefs.getStringSet(
                Const.PREF_HIDDEN_PACKAGES, new HashSet<>());
        Log.d(Const.TAG, "HideListProvider query hidden: size=" + hidden.size());
        MatrixCursor cursor = new MatrixCursor(new String[]{COL_PACKAGE});
        for (String pkg : hidden) cursor.addRow(new Object[]{pkg});
        return cursor;
    }

    @Override
    public String getType(@NotNull Uri uri) {
        Log.w(Const.TAG, "HideListProvider getType unexpected path: " + uri);
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if ("/hooked".equals(uri.getPath()) && values != null) {
            String pkg = values.getAsString(COL_PACKAGE);
            Context ctx = getContext();
            if (pkg != null && ctx != null) {
                SharedPreferences prefs = ctx.getSharedPreferences(
                        Const.PREF_FILE_MODULE, Context.MODE_PRIVATE);
                Set<String> hooked = new HashSet<>(
                        prefs.getStringSet(Const.PREF_HOOKED_PACKAGES, new HashSet<>()));
                hooked.add(pkg);
                prefs.edit()
                        .putStringSet(Const.PREF_HOOKED_PACKAGES, hooked)
                        .putLong(Const.PREF_HOOKED_BOOT_MILLIS,
                                System.currentTimeMillis() - SystemClock.elapsedRealtime())
                        .apply();
                Log.d(Const.TAG, "HideListProvider insert hooked: " + pkg);
            }
        } else if ("/restart".equals(uri.getPath())) {
            Context ctx = getContext();
            if (ctx != null) {
                ctx.getContentResolver().notifyChange(uri, null);
                Log.d(Const.TAG, "HideListProvider insert restart signal");
            }
        } else {
            Log.w(Const.TAG, "HideListProvider insert unexpected path: " + uri);
        }
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, String sel, String[] args) {
        Log.w(Const.TAG, "HideListProvider delete not supported, uri=" + uri);
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues v, String sel, String[] args) {
        Log.w(Const.TAG, "HideListProvider update not supported, uri=" + uri);
        return 0;
    }


    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        Log.w(Const.TAG, "HideListProvider openFile unexpected call, uri=" + uri + ", mode=" + mode);
        throw new FileNotFoundException("Unknown URI: " + uri);
    }
}
