package io.soragoto.m500.miku.plus.activity;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import io.soragoto.m500.miku.plus.Const;
import io.soragoto.m500.miku.plus.R;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {

    private static final long BOOT_TOLERANCE_MS = 60_000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_about);

        SharedPreferences prefs = getSharedPreferences(Const.PREF_FILE_MODULE, MODE_PRIVATE);

        long storedBootMillis = prefs.getLong(Const.PREF_HOOKED_BOOT_MILLIS, 0);
        long currentBootMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        boolean isFresh = storedBootMillis > 0
                && Math.abs(currentBootMillis - storedBootMillis) < BOOT_TOLERANCE_MS;

        Set<String> hooked = isFresh
                ? prefs.getStringSet(Const.PREF_HOOKED_PACKAGES, new HashSet<>())
                : new HashSet<>();

        boolean isActive = hooked.contains(Const.LAUNCHER_PKG);

        Log.d(Const.TAG, "isFresh=" + isFresh + " hooked=" + hooked + " isActive=" + isActive);

        TextView txtStatus = findViewById(R.id.txtModuleStatus);
        if (isActive) {
            txtStatus.setText(R.string.module_active);
            txtStatus.setBackgroundColor(0xFF2E7D32);
            txtStatus.setTextColor(Color.WHITE);
        } else {
            txtStatus.setText(R.string.module_inactive);
            txtStatus.setBackgroundColor(0xFFC62828);
            txtStatus.setTextColor(Color.WHITE);
        }

        // Static hint: always recommend scoping to launcher3 only.
        TextView txtHint = findViewById(R.id.txtConfigWarning);
        txtHint.setText(R.string.scope_hint);
        txtHint.setBackgroundColor(0xFF1565C0);
        txtHint.setVisibility(View.VISIBLE);
    }
}
