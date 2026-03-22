package io.soragoto.m500.miku.plus;

public class Const {
    public static final String MODULE_PKG = "io.soragoto.m500.miku.plus";
    public static final String TAG = "MikuMikuHook";
    public static final String LAUNCHER_PKG = "com.android.launcher3";
    public static final String AUTHORITY_HIDE_PROVIDER = "io.soragoto.m500.miku.plus.provider";
    public static final String AUTHORITY_WALLPAPER_PROVIDER = "io.soragoto.m500.miku.plus.wallpaper.provider";
    public static final String PREF_FILE_LAUNCHER = "com.android.launcher3.prefs";
    public static final String PREF_FILE_MODULE = "io.soragoto.m500.miku.plus_preferences";

    public static final String URI_HIDDEN_PACKAGES =
            "content://" + AUTHORITY_HIDE_PROVIDER + "/hidden";
    public static final String URI_HOOKED_PACKAGES =
            "content://" + AUTHORITY_HIDE_PROVIDER + "/hooked";
    public static final String URI_RESTART_SIGNAL =
            "content://" + AUTHORITY_HIDE_PROVIDER + "/restart";

    public static final String URI_LANDSCAPE_BG =
            "content://" + AUTHORITY_WALLPAPER_PROVIDER + "/landscape_bg";

    public static final String PREF_HIDDEN_PACKAGES = "hidden_packages";
    public static final String PREF_HOOKED_PACKAGES = "hooked_packages";
    public static final String PREF_HOOKED_BOOT_MILLIS = "hooked_boot_millis";
    public static final String PREF_ALLOW_ROTATION = "pref_allowRotation";
    public static final String PREF_HIDE_ICONS = "pref_hideIcons";
    public static final String PREF_SHOW_MIKU_LANDSCAPE = "pref_showMikuLandscape";
    public static final String PREF_SHOW_MIKU_PORTRAIT = "pref_showMikuPortrait";
    public static final String PREF_LANDSCAPE_BG_ENABLED = "pref_landscape_bg_enabled";
    public static final String PREF_LANDSCAPE_BG_PICK = "pref_landscape_bg_pick";

    public static final String FIELD_MIKU_HIDDEN_BY_US = "miku_hidden_by_us";
    public static final String FIELD_MIKU_LISTENER = "miku_listener";
    public static final String FIELD_MIKU_LANDSCAPE_LISTENER = "miku_landscape_listener";

    public static final String COL_PACKAGE = "package";

    /**
     * Filename used to store the landscape background inside the module app's filesDir.
     */
    public static final String LANDSCAPE_BG_FILENAME = "landscape_bg.jpg";
    public static final String LANDSCAPE_BG_CROP_TMP_FILENAME = "landscape_bg_crop.jpg";
    public static final int LANDSCAPE_BG_MAX_LONG_EDGE = 2048;
}
