package io.soragoto.m500.miku.plus.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.window.OnBackInvokedDispatcher;
import io.soragoto.m500.miku.plus.Const;
import io.soragoto.m500.miku.plus.R;

import java.util.*;

public class HideIconActivity extends Activity {

    private SharedPreferences prefs;
    private AppPickerAdapter adapter;
    private TextView txtHiddenCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hide_icon);

        prefs = getSharedPreferences(Const.PREF_FILE_MODULE, MODE_PRIVATE);

        EditText editSearch = findViewById(R.id.editSearch);
        txtHiddenCount = findViewById(R.id.txtHiddenCount);
        ListView listApps = findViewById(R.id.listApps);

        adapter = new AppPickerAdapter(this);
        listApps.setAdapter(adapter);

        listApps.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo item = adapter.getItem(position);
            if (item != null) {
                item.checked = !item.checked;
                adapter.notifyDataSetChanged();
                saveHiddenPackages();
                updateHiddenCount();
            }
        });

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        txtHiddenCount.setText(R.string.loading);

        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBackPress);
        Log.d(Const.TAG, "OnBackInvokedCallback registered");

        loadAppsAsync();
    }

    private void loadAppsAsync() {
        Set<String> hidden = new HashSet<>(prefs.getStringSet(Const.PREF_HIDDEN_PACKAGES, new HashSet<>()));
        Handler handler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try {
                LauncherApps launcherApps = (LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
                List<LauncherActivityInfo> infos = launcherApps.getActivityList(null, Process.myUserHandle());

                Log.d(Const.TAG, "loadAppsAsync: getActivityList returned " + infos.size() + " entries");

                Map<String, AppInfo> seen = new LinkedHashMap<>();
                for (LauncherActivityInfo info : infos) {
                    String pkg = info.getApplicationInfo().packageName;
                    if (!seen.containsKey(pkg)) {
                        AppInfo appInfo = new AppInfo();
                        appInfo.label = info.getLabel().toString();
                        appInfo.packageName = pkg;
                        appInfo.icon = info.getIcon(0);
                        appInfo.checked = hidden.contains(pkg);
                        seen.put(pkg, appInfo);
                    }
                }

                Log.d(Const.TAG, "loadAppsAsync: unique packages=" + seen.size());

                List<AppInfo> list = new ArrayList<>(seen.values());
                list.sort((a, b) -> a.label.compareToIgnoreCase(b.label));

                handler.post(() -> {
                    adapter.setData(list);
                    updateHiddenCount();
                });
            } catch (Throwable t) {
                Log.e(Const.TAG, "loadAppsAsync: exception", t);
            }
        }).start();
    }

    private void saveHiddenPackages() {
        Set<String> hidden = new HashSet<>();
        for (AppInfo info : adapter.getAllItems()) {
            if (info.checked) hidden.add(info.packageName);
        }
        prefs.edit().putStringSet(Const.PREF_HIDDEN_PACKAGES, hidden).apply();
    }

    private void updateHiddenCount() {
        int count = 0;
        for (AppInfo info : adapter.getAllItems()) {
            if (info.checked) count++;
        }
        txtHiddenCount.setText(getString(R.string.hidden_count, count));
    }

    private void handleBackPress() {
        List<String> hookedPackages = queryHookedPackages();
        Log.d(Const.TAG, "handleBackPress: hookedPackages=" + hookedPackages);
        if (hookedPackages.isEmpty()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_restart_title)
                .setMessage(getString(R.string.dialog_restart_msg, String.join("\n", hookedPackages)))
                .setPositiveButton(R.string.btn_restart, (d, w) -> {
                    restartPackages(hookedPackages);
                    finish();
                })
                .setNegativeButton(R.string.btn_cancel, (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private List<String> queryHookedPackages() {
        List<String> result = new ArrayList<>();
        Uri uri = Uri.parse(Const.URI_HOOKED_PACKAGES);
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) result.add(cursor.getString(0));
            }
        } catch (Exception e) {
            Log.e(Const.TAG, "queryHookedPackages failed", e);
        }
        return result;
    }

    private void restartPackages(List<String> packages) {
        Uri restartUri = Uri.parse(Const.URI_RESTART_SIGNAL);
        for (String pkg : packages) {
            try {
                ContentValues cv = new ContentValues(1);
                cv.put(Const.COL_PACKAGE, pkg);
                getContentResolver().insert(restartUri, cv);
                Log.d(Const.TAG, "sent restart signal for: " + pkg);
            } catch (Exception e) {
                Log.e(Const.TAG, "failed to send restart signal for " + pkg, e);
            }
        }
    }

    // ── Data model ──────────────────────────────────────────────────────────

    static class AppInfo {
        String label;
        String packageName;
        Drawable icon;
        boolean checked;
    }

    // ── Adapter ─────────────────────────────────────────────────────────────

    static class AppPickerAdapter extends BaseAdapter implements Filterable {

        private final LayoutInflater inflater;
        private List<AppInfo> allItems = new ArrayList<>();
        private List<AppInfo> filteredItems = new ArrayList<>();

        AppPickerAdapter(Context ctx) {
            inflater = LayoutInflater.from(ctx);
        }

        void setData(List<AppInfo> items) {
            allItems = items;
            filteredItems = new ArrayList<>(items);
            notifyDataSetChanged();
        }

        List<AppInfo> getAllItems() {
            return allItems;
        }

        @Override
        public int getCount() {
            return filteredItems.size();
        }

        @Override
        public AppInfo getItem(int pos) {
            return filteredItems.get(pos);
        }

        @Override
        public long getItemId(int pos) {
            return pos;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.item_app, parent, false);
                holder = new ViewHolder();
                holder.icon = convertView.findViewById(R.id.imgIcon);
                holder.label = convertView.findViewById(R.id.txtLabel);
                holder.pkg = convertView.findViewById(R.id.txtPackage);
                holder.check = convertView.findViewById(R.id.checkBox);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            AppInfo item = filteredItems.get(position);
            holder.icon.setImageDrawable(item.icon);
            holder.label.setText(item.label);
            holder.pkg.setText(item.packageName);
            holder.check.setChecked(item.checked);

            return convertView;
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    if (constraint == null || constraint.length() == 0) {
                        results.values = new ArrayList<>(allItems);
                        results.count = allItems.size();
                    } else {
                        String query = constraint.toString().toLowerCase(Locale.ROOT);
                        List<AppInfo> filtered = new ArrayList<>();
                        for (AppInfo info : allItems) {
                            if (info.label.toLowerCase(Locale.ROOT).contains(query)
                                    || info.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                                filtered.add(info);
                            }
                        }
                        results.values = filtered;
                        results.count = filtered.size();
                    }
                    return results;
                }

                @SuppressWarnings("unchecked")
                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    filteredItems = (List<AppInfo>) results.values;
                    notifyDataSetChanged();
                }
            };
        }

        static class ViewHolder {
            ImageView icon;
            TextView label;
            TextView pkg;
            CheckBox check;
        }
    }
}
