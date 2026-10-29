package com.cleantv.home;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Matches for one day, cached so the Home card and the Matches screen share the same requests. */
final class MatchesData {
    static final ZoneId ZONE = ZoneId.of("Africa/Cairo");

    static final class Day {
        List<BbcFootballProvider.League> leagues;
        boolean offline;
        long at;
    }

    private MatchesData() {}

    /** @return null when nothing was ever saved and the fetch failed */
    static Day day(Context c, final LocalDate d, boolean fetch) {
        Cache.Raw r = Cache.get(c, "matches:" + d, ttl(c, d), fetch,
                () -> serialize(new BbcFootballProvider(ZONE, Collections.<String>emptyList()).fetchDay(d)));
        if (r.body == null) return null;
        Day x = new Day();
        x.leagues = parse(r.body);
        x.offline = r.offline;
        x.at = r.at;
        return x;
    }

    /** Live today: 2 min. Kick-off within the hour: 10 min. Otherwise an hour. */
    static long ttl(Context c, LocalDate d) {
        if (!d.equals(LocalDate.now(ZONE))) return 60 * 60_000L;
        String saved = Cache.peek(c, "matches:" + d);
        if (saved == null) return 0L;
        boolean live = false, soon = false;
        java.time.LocalTime now = java.time.LocalTime.now(ZONE);
        for (BbcFootballProvider.League l : parse(saved)) {
            for (BbcFootballProvider.Match m : l.matches) {
                if (m.live) live = true;
                else if (m.upcoming && minutesUntil(m.kickoff, now) >= 0 && minutesUntil(m.kickoff, now) <= 60) soon = true;
            }
        }
        return BbcFootballProvider.nextRefreshMs(live, soon);
    }

    static long minutesUntil(String hhmm, java.time.LocalTime now) {
        try {
            return java.time.Duration.between(now, java.time.LocalTime.parse(hhmm)).toMinutes();
        } catch (Exception e) {
            return -1;
        }
    }

    private static String serialize(List<BbcFootballProvider.League> leagues) {
        StringBuilder sb = new StringBuilder();
        for (BbcFootballProvider.League l : leagues) {
            for (BbcFootballProvider.Match m : l.matches) {
                sb.append(l.name).append('\t').append(m.home).append('\t').append(m.away).append('\t')
                        .append(m.score).append('\t').append(m.display).append('\t').append(m.kickoff).append('\t')
                        .append(m.status).append('\t').append(m.finished ? '1' : '0').append('\n');
            }
        }
        return sb.toString();
    }

    static List<BbcFootballProvider.League> parse(String body) {
        Map<String, BbcFootballProvider.League> map = new LinkedHashMap<>();
        for (String line : body.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length < 8) continue;
            BbcFootballProvider.League l = map.get(f[0]);
            if (l == null) {
                l = new BbcFootballProvider.League();
                l.name = f[0];
                map.put(f[0], l);
            }
            BbcFootballProvider.Match m = new BbcFootballProvider.Match();
            m.league = f[0]; m.home = f[1]; m.away = f[2]; m.score = f[3]; m.display = f[4]; m.kickoff = f[5];
            m.status = f[6];
            m.live = "MidEvent".equals(f[6]);
            m.upcoming = "PreEvent".equals(f[6]);
            m.finished = "1".equals(f[7]);
            l.matches.add(m);
        }
        return new ArrayList<>(map.values());
    }

    /**
     * A league seen for the first time is shown unless it matches the default exclusion list;
     * after that the user's choice in the Leagues dialog is what counts.
     */
    static Set<String> visible(Context c, Collection<BbcFootballProvider.League> leagues) {
        SharedPreferences sp = Prefs.sp(c);
        Set<String> visible = new HashSet<>(sp.getStringSet("m_visible", new HashSet<String>()));
        Set<String> known = new HashSet<>(sp.getStringSet("m_known", new HashSet<String>()));
        boolean changed = false;
        for (BbcFootballProvider.League l : leagues) {
            if (known.add(l.name)) {
                if (!defaultExcluded(l.name)) visible.add(l.name);
                changed = true;
            }
        }
        if (changed) sp.edit().putStringSet("m_visible", visible).putStringSet("m_known", known).apply();
        return visible;
    }

    static void setVisible(Context c, Set<String> v) {
        Prefs.sp(c).edit().putStringSet("m_visible", v).apply();
    }

    private static boolean defaultExcluded(String league) {
        String l = league.toLowerCase();
        for (String x : BbcFootballProvider.DEFAULT_EXCLUDED) {
            if (l.contains(x.toLowerCase())) return true;
        }
        return false;
    }
}
