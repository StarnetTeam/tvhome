package com.cleantv.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
        int color = 0xFFFFFFFF;   // average colour of the picture, used for the focus glow
    }

    /** Streaming apps that come first in the default order when they are installed. */
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
                    it.color = averageColor(it.image);
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

    /** The user's order if they moved apps, otherwise streaming apps first and then A to Z. */
    static List<Item> ordered(Context c, List<Item> alphabetical) {
        List<Item> byDefault = new ArrayList<>();
        for (String pkg : PREFERRED) {
            Item it = find(alphabetical, pkg);
            if (it != null) byDefault.add(it);
        }
        for (Item it : alphabetical) if (!byDefault.contains(it)) byDefault.add(it);
        List<String> saved = Prefs.order(c);
        if (saved.isEmpty()) return byDefault;
        List<Item> out = new ArrayList<>();
        for (String pkg : saved) {
            Item it = find(byDefault, pkg);
            if (it != null && !out.contains(it)) out.add(it);
        }
        for (Item it : byDefault) if (!out.contains(it)) out.add(it);
        return out;
    }

    private static Item find(List<Item> list, String pkg) {
        for (Item it : list) if (it.pkg.equals(pkg)) return it;
        return null;
    }

    /** Average of the visible pixels of a small copy of the picture; falls back to white. */
    private static int averageColor(Drawable d) {
        try {
            Bitmap bm = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bm);
            d.setBounds(0, 0, 8, 8);
            d.draw(cv);
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int p = bm.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++;
                }
            }
            if (n == 0) return 0xFFFFFFFF;
            int cr = (int) (r / n), cg = (int) (g / n), cb = (int) (b / n);
            // keep it bright enough to read as light
            int max = Math.max(cr, Math.max(cg, cb));
            if (max < 110) { cr += 110 - max; cg += 110 - max; cb += 110 - max; }
            return Color.rgb(Math.min(255, cr), Math.min(255, cg), Math.min(255, cb));
        } catch (RuntimeException e) {
            return 0xFFFFFFFF;
        }
    }
}
