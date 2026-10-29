package com.cleantv.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The launchable apps on this device. Works on Android TV and on plain Android boxes. */
final class Apps {
    static final class Item {
        String label, pkg;
        Intent intent;
        Drawable image;
        boolean banner;
    }

    /** Streaming apps that go in the dock first when they are installed. */
    private static final List<String> PREFERRED = Arrays.asList(
            "com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus", "com.apple.atve.androidtv.appletv", "com.google.android.youtube",
            "com.plexapp.android", "org.xbmc.kodi", "com.spotify.tv.android", "com.android.vending");

    private Apps() {}

    /** Call from a background thread when withImages is true (loading banners is slow). */
    static List<Item> load(Context c, boolean withImages) {
        PackageManager pm = c.getPackageManager();
        Map<String, Item> found = new LinkedHashMap<>();
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : categories) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(c.getPackageName()) || found.containsKey(pkg)) continue;
                Item it = new Item();
                it.pkg = pkg;
                it.label = String.valueOf(ri.loadLabel(pm));
                it.intent = new Intent(Intent.ACTION_MAIN).addCategory(cat)
                        .setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                if (withImages) {
                    Drawable b = ri.activityInfo.loadBanner(pm);
                    if (b == null) b = ri.activityInfo.applicationInfo.loadBanner(pm);
                    it.banner = b != null;
                    it.image = b != null ? b : ri.loadIcon(pm);
                }
                found.put(pkg, it);
            }
        }
        List<Item> out = new ArrayList<>(found.values());
        Collections.sort(out, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) { return a.label.compareToIgnoreCase(b.label); }
        });
        return out;
    }

    /** The user's pinned apps first, then well-known streaming apps, then alphabetical. */
    static List<Item> pickDock(Context c, List<Item> visible, int size) {
        List<Item> dock = new ArrayList<>();
        for (String pkg : Prefs.dock(c)) {
            Item it = find(visible, pkg);
            if (it != null && !dock.contains(it) && dock.size() < size) dock.add(it);
        }
        if (dock.isEmpty()) {
            for (String pkg : PREFERRED) {
                Item it = find(visible, pkg);
                if (it != null && dock.size() < Math.min(4, size)) dock.add(it);
            }
            for (Item it : visible) {
                if (dock.size() >= Math.min(4, size)) break;
                if (!dock.contains(it)) dock.add(it);
            }
        }
        return dock;
    }

    private static Item find(List<Item> list, String pkg) {
        for (Item it : list) if (it.pkg.equals(pkg)) return it;
        return null;
    }
}
