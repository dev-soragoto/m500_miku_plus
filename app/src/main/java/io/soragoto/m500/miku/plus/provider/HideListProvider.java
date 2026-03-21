package io.soragoto.m500.miku.plus.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.SystemClock;
import io.soragoto.m500.miku.plus.Const;

import java.util.HashSet;
import java.util.Set;

import static io.soragoto.m500.miku.plus.Const.COL_PACKAGE;

public class HideListProvider extends ContentProvider {


    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Context ctx = getContext();
        if (ctx == null) return null;
        SharedPreferences prefs = ctx.getSharedPreferences(
                Const.PREFS_NAME, Context.MODE_PRIVATE);

        if ("/hooked".equals(uri.getPath())) {
            Set<String> hooked = prefs.getStringSet(
                    Const.KEY_HOOKED_PACKAGES, new HashSet<>());
            MatrixCursor cursor = new MatrixCursor(new String[]{COL_PACKAGE});
            for (String pkg : hooked) cursor.addRow(new Object[]{pkg});
            return cursor;
        }

        Set<String> hidden = prefs.getStringSet(
                Const.KEY_HIDDEN_PACKAGES, new HashSet<>());
        MatrixCursor cursor = new MatrixCursor(new String[]{COL_PACKAGE});
        for (String pkg : hidden) cursor.addRow(new Object[]{pkg});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if ("/hooked".equals(uri.getPath()) && values != null) {
            String pkg = values.getAsString(COL_PACKAGE);
            Context ctx = getContext();
            if (pkg != null && ctx != null) {
                SharedPreferences prefs = ctx.getSharedPreferences(
                        Const.PREFS_NAME, Context.MODE_PRIVATE);
                Set<String> hooked = new HashSet<>(
                        prefs.getStringSet(Const.KEY_HOOKED_PACKAGES, new HashSet<>()));
                hooked.add(pkg);
                prefs.edit()
                        .putStringSet(Const.KEY_HOOKED_PACKAGES, hooked)
                        .putLong(Const.KEY_HOOKED_BOOT_MILLIS,
                                System.currentTimeMillis() - SystemClock.elapsedRealtime())
                        .apply();
            }
        } else if ("/restart".equals(uri.getPath())) {
            Context ctx = getContext();
            if (ctx != null) {
                ctx.getContentResolver().notifyChange(uri, null);
            }
        }
        return null;
    }

    @Override
    public int delete(Uri uri, String sel, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues v, String sel, String[] args) {
        return 0;
    }
}
