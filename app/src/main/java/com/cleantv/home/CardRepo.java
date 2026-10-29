package com.cleantv.home;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Builds cards on background threads and hands the result back on the main thread. */
final class CardRepo {
    interface Listener { void onCard(Card c); }

    static final List<Source> SOURCES = Arrays.<Source>asList(
            new Sources.Weather(), new Sources.Prayer(), new Sources.Matches(), new Sources.Clock(),
            new Sources.Storage(), new Sources.Network(), new Sources.Standby());

    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private CardRepo() {}

    static Source byId(String id) {
        for (Source s : SOURCES) if (s.id().equals(id)) return s;
        throw new IllegalArgumentException(id);
    }

    static void load(final Context c, final Source s, final boolean fetch, final Listener l) {
        IO.execute(() -> {
            Card k;
            try {
                k = s.build(c, fetch);
            } catch (Exception e) {
                k = new Card();
                k.id = s.id();
                k.title = c.getString(s.titleRes());
                k.accent = s.accent();
                k.big = "--";
                k.sub = c.getString(R.string.no_data);
            }
            final Card result = k;
            UI.post(() -> l.onCard(result));
        });
    }
}
