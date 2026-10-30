package com.cleantv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.time.LocalTime;
import java.util.Random;

/** Chooses and decodes the theme's wallpaper: fixed, rotating on a timer, or by time of day. */
final class Wallpapers {
    static final class Pick {
        int res;
        String caption;
    }

    private Wallpapers() {}

    /** @return null when the theme has no picture or wallpapers are switched off */
    static Pick current(Context c, Themes.T t) {
        if (!Prefs.wallpaper(c) || t.wp.length == 0) return null;
        int idx;
        if (t.moods && t.wp.length >= 3) {
            int h = LocalTime.now().getHour();
            idx = h >= 6 && h < 16 ? 0 : h >= 16 && h < 21 ? 1 : 2;   // morning, evening, night
        } else if (t.rotMin <= 0 || t.wp.length == 1) {
            idx = 0;
        } else {
            long slot = System.currentTimeMillis() / 60_000L / t.rotMin;
            if (t.shuffle) {
                int a = new Random(slot).nextInt(t.wp.length);
                int b = new Random(slot - 1).nextInt(t.wp.length);
                idx = a == b ? (a + 1) % t.wp.length : a;   // never the same picture twice in a row
            } else {
                idx = (int) (slot % t.wp.length);
            }
        }
        Pick p = new Pick();
        p.res = t.wp[idx];
        if (t.showCaption && t.captions != null && idx < t.captions.length && t.captions[idx] != null
                && !t.captions[idx].isEmpty()) {
            p.caption = t.captions[idx];
        }
        return p;
    }

    /** Call from a background thread. Uses 16-bit colour on weak devices to halve the memory. */
    static Bitmap decode(Context c, int res) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Prefs.lowEnd(c) ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeResource(c.getResources(), res, o);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }
}
