package com.cleantv.home;

import android.content.Context;

/**
 * The ten themes of the preview, with the same settings: wallpaper (rotating or by time of day),
 * dark overlay, dock look, icon-only buttons, glow and so on. Wallpapers are small JPEGs inside the APK.
 */
final class Themes {
    static final class T {
        final String name;
        final int bg, accent, cols, dock;
        int[] wp = new int[0];
        String[] captions;
        int rotMin, dim, dop = 100;
        boolean shuffle = true, moods, glass, band = true, title = true, cine, icons, appGlow, ambient = true,
                horizon, fade, showCaption;

        T(String name, int bg, int accent, int cols, int dock) {
            this.name = name; this.bg = bg; this.accent = accent; this.cols = cols; this.dock = dock;
        }
        T wp(int rotMin, boolean shuffle, int... res) { this.wp = res; this.rotMin = rotMin; this.shuffle = shuffle; return this; }
        T caps(String[] c) { this.captions = c; this.showCaption = true; return this; }
        T moods() { this.moods = true; return this; }
        T glass(int opacity) { this.glass = true; this.dop = opacity; return this; }
        T opacity(int o) { this.dop = o; return this; }
        T noBand() { this.band = false; return this; }
        T noTitle() { this.title = false; return this; }
        T cine() { this.cine = true; return this; }
        T icons() { this.icons = true; return this; }
        T appGlow() { this.appGlow = true; return this; }
        T noAmbient() { this.ambient = false; return this; }
        T horizon() { this.horizon = true; return this; }
        T fade() { this.fade = true; return this; }
        T dim(int d) { this.dim = d; return this; }
    }

    static final T[] ALL = {
            new T("default", 0xFF000000, 0xFFFFFFFF, 4, 4),
            new T("aurora", 0xFF070A14, 0xFFFFFFFF, 4, 4).wp(0, false, R.drawable.wp_aurora),
            new T("dussehra", 0xFF120809, 0xFFFFB23C, 4, 4).wp(0, false, R.drawable.wp_dussehra),
            new T("glass", 0xFF090C14, 0xFFFFFFFF, 4, 4).wp(0, false, R.drawable.wp_glass)
                    .glass(100).cine().icons().appGlow().horizon(),
            new T("nature-calm", 0xFF05070C, 0xFFFFFFFF, 6, 5).wp(5, true,
                    R.drawable.wp_nature_calm_01, R.drawable.wp_nature_calm_02, R.drawable.wp_nature_calm_03, R.drawable.wp_nature_calm_04, R.drawable.wp_nature_calm_05, R.drawable.wp_nature_calm_06, R.drawable.wp_nature_calm_07, R.drawable.wp_nature_calm_08, R.drawable.wp_nature_calm_09, R.drawable.wp_nature_calm_10, R.drawable.wp_nature_calm_11, R.drawable.wp_nature_calm_12)
                    .caps(new String[]{"Misty karst valley at sunrise, Laos - Basile Morin, CC BY-SA 4.0", "Autumn reflections, Loch Tummel, Scotland - Eric Kilby, CC BY-SA 4.0", "Morning mist at Tham Sakoen, Thailand - BerryJ, CC BY-SA 4.0", "Lake Mystery, New Zealand - Michal Klajban, CC BY-SA 4.0", "Spring bluebell beech wood, Pryor's Wood UK - Colin, CC BY-SA 4.0", "Autumn calm lake, Ossiacher See Austria - Johann Jaritz, CC BY-SA 4.0", "Winter frosted forest at sunrise, Karula Estonia - Amadvr, CC BY-SA 3.0", "Green forest with sunbeams, Roruper Holz Germany - Dietmar Rabich, CC BY-SA 4.0", "Mossy forest, Lierneux Belgium - Trougnouf, CC BY 4.0", "Misty rainforest lake, Khao Sok Thailand - Vyacheslav Argenberg, CC BY 4.0", "Misty river dawn, Cherkasy Ukraine - Oleksandr Malyon, CC BY-SA 4.0", "Kuang Si turquoise pool, Laos - Basile Morin, CC BY-SA 4.0"}).glass(40).icons().noAmbient().noTitle().fade().dim(14),
            new T("retro", 0xFF000000, 0xFFFFFFFF, 5, 3).wp(60, false, R.drawable.wp_retro_1, R.drawable.wp_retro_2, R.drawable.wp_retro_3)
                    .moods().noBand().icons().noAmbient().noTitle().dim(10),
            new T("ruska", 0xFF07080D, 0xFFFFFFFF, 4, 4).wp(0, false, R.drawable.wp_ruska)
                    .opacity(70).appGlow().noAmbient(),
            new T("scenes", 0xFF000000, 0xFFFFFFFF, 4, 4).wp(60, true,
                    R.drawable.wp_scenes_01, R.drawable.wp_scenes_02, R.drawable.wp_scenes_03, R.drawable.wp_scenes_04, R.drawable.wp_scenes_05, R.drawable.wp_scenes_06, R.drawable.wp_scenes_07, R.drawable.wp_scenes_08, R.drawable.wp_scenes_09, R.drawable.wp_scenes_10, R.drawable.wp_scenes_11, R.drawable.wp_scenes_12, R.drawable.wp_scenes_13, R.drawable.wp_scenes_14),
            new T("six-columns", 0xFF000000, 0xFFFFFFFF, 6, 6),
            new T("sweden", 0xFF05070C, 0xFFFFFFFF, 6, 6).wp(60, false,
                    R.drawable.wp_sweden_01, R.drawable.wp_sweden_02, R.drawable.wp_sweden_03)
                    .caps(new String[]{"Aurora over Abisko, Lapland · Pavel Shyshkouski, CC BY-SA 4.0", "Vasabron and City Hall, Stockholm · Julian Herzog, CC BY 4.0", "Skeppsbron, Stockholm · Arild Vågen, CC BY-SA 2.0"}).glass(50).icons().noAmbient().noTitle().fade(),
    };

    private Themes() {}

    static T current(Context c) { return ALL[Math.max(0, Math.min(Prefs.theme(c), ALL.length - 1))]; }

    /** Columns on Home: the user's choice, or the theme's own. */
    static int cols(Context c, T t) {
        int o = Prefs.cols(c);
        return o > 0 ? o : t.cols;
    }
}
