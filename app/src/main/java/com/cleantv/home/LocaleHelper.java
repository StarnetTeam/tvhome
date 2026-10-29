package com.cleantv.home;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** In-app language: system, English or Arabic. Used from attachBaseContext of every Activity. */
final class LocaleHelper {
    private LocaleHelper() {}

    static Context wrap(Context base) {
        String lang = Prefs.lang(base);
        if ("system".equals(lang)) return base;
        Locale l = new Locale(lang);
        Locale.setDefault(l);
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(l);
        cfg.setLayoutDirection(l);
        return base.createConfigurationContext(cfg);
    }

    /** The user's locale but with Latin digits, so clocks, scores and numbers always look alike. */
    static Locale numLocale(Context c) {
        String lang = Prefs.lang(c);
        Locale base = "system".equals(lang) ? Locale.getDefault() : new Locale(lang);
        try {
            return new Locale.Builder().setLocale(base).setUnicodeLocaleKeyword("nu", "latn").build();
        } catch (RuntimeException e) {
            return Locale.ENGLISH;
        }
    }

    static boolean isArabic(Context c) {
        String lang = Prefs.lang(c);
        return "ar".equals(lang) || ("system".equals(lang) && "ar".equals(Locale.getDefault().getLanguage()));
    }
}
