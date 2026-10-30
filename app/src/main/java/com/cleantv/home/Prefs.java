package com.cleantv.home;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Every setting lives here. Plain SharedPreferences, no libraries. */
final class Prefs {
    static final String[] CARD_IDS = {"weather", "prayer", "matches", "clock", "storage", "network", "standby", "remote"};

    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("tvhome", Context.MODE_PRIVATE);
    }

    /** "system", "en" or "ar". */
    static String lang(Context c) { return sp(c).getString("lang", "system"); }
    static void lang(Context c, String v) { sp(c).edit().putString("lang", v).apply(); }

    static int theme(Context c) { return sp(c).getInt("theme", 4); }
    static void theme(Context c, int v) { sp(c).edit().putInt("theme", v).apply(); }

    /** -1 = automatic (on for weak devices), 0 = off, 1 = on. */
    static int reduceMode(Context c) { return sp(c).getInt("reduce", -1); }
    static void reduceMode(Context c, int v) { sp(c).edit().putInt("reduce", v).apply(); }

    static boolean reduce(Context c) {
        int m = reduceMode(c);
        return m >= 0 ? m == 1 : lowEnd(c);
    }

    /** True for devices with about 2 GB of RAM or less. */
    static boolean lowEnd(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        if (am.isLowRamDevice()) return true;
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.totalMem <= 2_600_000_000L;
    }

    static Set<String> cards(Context c) {
        return new HashSet<>(sp(c).getStringSet("cards", new HashSet<>(Arrays.asList(CARD_IDS))));
    }
    static void cards(Context c, Set<String> v) { sp(c).edit().putStringSet("cards", v).apply(); }

    static String city(Context c) { return sp(c).getString("city", "Cairo"); }
    static String lat(Context c) { return sp(c).getString("lat", "30.0444"); }
    static String lon(Context c) { return sp(c).getString("lon", "31.2357"); }
    static void place(Context c, String name, String lat, String lon) {
        sp(c).edit().putString("city", name).putString("lat", lat).putString("lon", lon).apply();
    }

    /** Aladhan calculation method id (5 = Egyptian General Authority of Survey). */
    static int method(Context c) { return sp(c).getInt("method", 5); }
    static void method(Context c, int v) { sp(c).edit().putInt("method", v).apply(); }

    /** Hours without any remote key before standby; 0 = off. */
    static int standby(Context c) { return sp(c).getInt("standby", 4); }
    static void standby(Context c, int v) { sp(c).edit().putInt("standby", v).apply(); }

    static boolean keepAudio(Context c) { return sp(c).getBoolean("keepAudio", true); }
    static void keepAudio(Context c, boolean v) { sp(c).edit().putBoolean("keepAudio", v).apply(); }

    static Set<String> hidden(Context c) { return new HashSet<>(sp(c).getStringSet("hidden", new HashSet<String>())); }
    static void hidden(Context c, Set<String> v) { sp(c).edit().putStringSet("hidden", v).apply(); }

    /** The user's order of apps (package names). Empty = the default order. */
    static List<String> order(Context c) {
        String s = sp(c).getString("order", "");
        return s.isEmpty() ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }
    static void order(Context c, List<String> v) { sp(c).edit().putString("order", TextUtils.join(",", v)).apply(); }

    /** Apps per row: 0 = the theme's own number, otherwise 4 to 7. */
    static int cols(Context c) { return sp(c).getInt("cols", 0); }
    static void cols(Context c, int v) { sp(c).edit().putInt("cols", v).apply(); }

    static boolean wallpaper(Context c) { return sp(c).getBoolean("wallpaper", true); }
    static void wallpaper(Context c, boolean v) { sp(c).edit().putBoolean("wallpaper", v).apply(); }

    static boolean tileMenu(Context c) { return sp(c).getBoolean("tileMenu", true); }
    static void tileMenu(Context c, boolean v) { sp(c).edit().putBoolean("tileMenu", v).apply(); }

    /** True when cards sit above the dock. */
    static boolean showCards(Context c) { return sp(c).getBoolean("showCards", true); }
    static void showCards(Context c, boolean v) { sp(c).edit().putBoolean("showCards", v).apply(); }

    /** Bumped by Settings whenever the app list or dock changes, so Home reloads it. */
    static long appsRev(Context c) { return sp(c).getLong("appsRev", 0L); }
    static void bumpApps(Context c) { sp(c).edit().putLong("appsRev", System.currentTimeMillis()).apply(); }
}
