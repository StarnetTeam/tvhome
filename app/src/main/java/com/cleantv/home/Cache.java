package com.cleantv.home;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps the last good answer of every data source, so cards show something instantly, keep working
 * offline, and the network is only touched when an answer is older than its time-to-live.
 */
final class Cache {
    interface Fetcher { String fetch() throws Exception; }

    static final class Raw {
        String body;        // null when nothing was ever saved
        long at;            // when the body was fetched
        boolean offline;    // true when the body is older than its TTL and could not be refreshed
    }

    private static final Map<String, Long> LAST_TRY = new HashMap<>();
    private static final long RETRY_MS = 60_000L;
    private static final long KEEP_MS = 3L * 24 * 3600 * 1000;

    private Cache() {}

    static Raw get(Context c, String key, long ttlMs, boolean fetch, Fetcher f) {
        SharedPreferences sp = Prefs.sp(c);
        Raw r = new Raw();
        r.body = sp.getString("raw:" + key, null);
        r.at = sp.getLong("at:" + key, 0L);
        long now = System.currentTimeMillis();
        boolean fresh = r.body != null && now - r.at < ttlMs;
        if (!fresh && fetch && mayTry(key, now)) {
            try {
                String b = f.fetch();
                sp.edit().putString("raw:" + key, b).putLong("at:" + key, now).apply();
                r.body = b;
                r.at = now;
                fresh = true;
                prune(sp, now);
            } catch (Exception e) {
                // keep the saved copy
            }
        }
        r.offline = !fresh && r.body != null;
        return r;
    }

    static String peek(Context c, String key) { return Prefs.sp(c).getString("raw:" + key, null); }

    /** Marks everything stale so the next load fetches again. */
    static void expireAll(Context c) {
        SharedPreferences sp = Prefs.sp(c);
        SharedPreferences.Editor e = sp.edit();
        for (String k : sp.getAll().keySet()) if (k.startsWith("at:")) e.putLong(k, 0L);
        e.apply();
        synchronized (LAST_TRY) { LAST_TRY.clear(); }
    }

    /** Marks only the entries whose key starts with the prefix as stale. */
    static void expirePrefix(Context c, String prefix) {
        SharedPreferences sp = Prefs.sp(c);
        SharedPreferences.Editor e = sp.edit();
        for (String k : sp.getAll().keySet()) if (k.startsWith("at:" + prefix)) e.putLong(k, 0L);
        e.apply();
        synchronized (LAST_TRY) { LAST_TRY.clear(); }
    }

    private static boolean mayTry(String key, long now) {
        synchronized (LAST_TRY) {
            Long last = LAST_TRY.get(key);
            if (last != null && now - last < RETRY_MS) return false;
            LAST_TRY.put(key, now);
            return true;
        }
    }

    private static void prune(SharedPreferences sp, long now) {
        SharedPreferences.Editor e = sp.edit();
        boolean any = false;
        for (Map.Entry<String, ?> en : sp.getAll().entrySet()) {
            String k = en.getKey();
            if (k.startsWith("at:") && en.getValue() instanceof Long && now - (Long) en.getValue() > KEEP_MS) {
                e.remove(k).remove("raw:" + k.substring(3));
                any = true;
            }
        }
        if (any) e.apply();
    }
}
