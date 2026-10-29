package com.cleantv.home;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

/** Built-in looks. Gradients only, so the APK stays tiny and old GPUs stay happy. */
final class Themes {
    static final class T {
        final int name, top, bottom, accent;
        T(int name, int top, int bottom, int accent) {
            this.name = name; this.top = top; this.bottom = bottom; this.accent = accent;
        }
    }

    static final T[] ALL = {
            new T(R.string.theme_midnight, 0xFF0B1630, 0xFF000000, 0xFF7AA8FF),
            new T(R.string.theme_aurora, 0xFF0B3A4A, 0xFF1A0B3A, 0xFF4CC9F0),
            new T(R.string.theme_sunset, 0xFF5A2208, 0xFF120809, 0xFFFFB23C),
            new T(R.string.theme_forest, 0xFF0E3B2A, 0xFF05070C, 0xFF6FCF97),
            new T(R.string.theme_ocean, 0xFF0B3A5A, 0xFF0A1220, 0xFF38BDF8),
            new T(R.string.theme_mono, 0xFF262626, 0xFF000000, 0xFFFFFFFF),
    };

    private Themes() {}

    static T current(Context c) { return ALL[Math.max(0, Math.min(Prefs.theme(c), ALL.length - 1))]; }

    static GradientDrawable background(Context c) {
        T t = current(c);
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t.top, t.bottom});
    }
}
