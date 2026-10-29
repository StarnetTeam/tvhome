package com.cleantv.home;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java port of the user's "BBC Football Scores Scraper 4.1" (Python).
 *
 * UNOFFICIAL SOURCE. It reads the public BBC Sport scores page, which BBC's terms do not permit
 * without permission. Keep it for personal use, off by default, behind a visible "unofficial"
 * label, and never redistribute it. The page layout can change at any time; when it does,
 * fetchDay() returns an empty list and the card shows "no data" instead of crashing.
 *
 * Call from a background thread only (it does network I/O).
 */
public final class BbcFootballProvider {

    public static final String BASE = "https://www.bbc.com/sport/football/scores-fixtures";

    /** Same exclusion list as the original script; the Settings screen lets the user override it. */
    public static final List<String> DEFAULT_EXCLUDED = java.util.Arrays.asList(
            "Scottish", "Women's", "Irish", "Austrian", "Brazilian", "Croatia", "Danish", "Chinese",
            "Dutch", "Finnish", "Indian", "Nigerian", "Norwegian", "Polish", "Serbian", "Swedish",
            "Ukraine", "Belgian", "South Africa", "Australian", "Czech", "Greek", "Swiss");

    private static final Pattern DATA =
            Pattern.compile("window\\.__INITIAL_DATA__\\s*=\\s*\"(.*?)\";", Pattern.DOTALL);
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    public static final class Match {
        public String league, home, away, homeUrn, awayUrn;
        public String homeScore, awayScore;   // null before kick-off
        public String score;                  // "2 - 1" or "vs"
        public String display;                // minute text, "FT", or the listed kick-off time
        public String kickoff;                // HH:mm in the chosen zone, "N/A" if unknown
        public String status;                 // BBC status: PreEvent, MidEvent, PostEvent
        public boolean live, finished, upcoming;
    }

    public static final class League {
        public String name;
        public final List<Match> matches = new ArrayList<>();
    }

    private final ZoneId zone;
    private final List<String> excluded;

    /** @param zone     the zone shown to the user (the original used Africa/Cairo) */
    public BbcFootballProvider(ZoneId zone, List<String> excluded) {
        this.zone = zone;
        this.excluded = excluded;
    }

    /** One request per day. Returns an empty list when the page has no scores or changed shape. */
    public List<League> fetchDay(LocalDate day) throws IOException, JSONException {
        String html = Http.get(BASE + "/" + day);
        Matcher m = DATA.matcher(html);
        if (!m.find()) return Collections.emptyList();

        // The page embeds the data as a JSON-escaped string; decode it, then parse it as JSON.
        Object decoded = new JSONTokener("\"" + m.group(1) + "\"").nextValue();
        JSONObject root = new JSONObject(String.valueOf(decoded));
        JSONObject data = root.optJSONObject("data");
        if (data == null) return Collections.emptyList();

        List<League> out = new ArrayList<>();
        for (Iterator<String> it = data.keys(); it.hasNext(); ) {
            String key = it.next();
            if (!key.contains("sport-data-scores-fixtures")) continue;
            JSONObject v = data.optJSONObject(key);
            JSONObject d = v == null ? null : v.optJSONObject("data");
            JSONArray groups = d == null ? null : d.optJSONArray("eventGroups");
            if (groups == null) continue;

            for (int i = 0; i < groups.length(); i++) {
                JSONObject g = groups.getJSONObject(i);
                String name = g.optString("displayLabel", "Unknown League");
                if (isExcluded(name)) continue;
                League lg = new League();
                lg.name = name;
                JSONArray sec = g.optJSONArray("secondaryGroups");
                if (sec != null) {
                    for (int j = 0; j < sec.length(); j++) {
                        JSONArray events = sec.getJSONObject(j).optJSONArray("events");
                        if (events == null) continue;
                        for (int k = 0; k < events.length(); k++) {
                            lg.matches.add(parse(events.getJSONObject(k), name));
                        }
                    }
                }
                if (!lg.matches.isEmpty()) out.add(lg);
            }
        }
        return out;
    }

    private Match parse(JSONObject e, String league) {
        JSONObject h = e.optJSONObject("home") != null ? e.optJSONObject("home") : new JSONObject();
        JSONObject a = e.optJSONObject("away") != null ? e.optJSONObject("away") : new JSONObject();
        Match x = new Match();
        x.league = league;
        x.home = h.optString("fullName", "TBD");
        x.away = a.optString("fullName", "TBD");
        x.homeUrn = h.optString("urn", "");
        x.awayUrn = a.optString("urn", "");
        x.homeScore = h.isNull("score") ? null : h.optString("score");
        x.awayScore = a.isNull("score") ? null : a.optString("score");
        x.score = x.homeScore != null ? x.homeScore + " - " + x.awayScore : "vs";

        String comment = value(e, "statusComment");
        String period = value(e, "periodLabel");
        x.status = e.optString("status", "");
        x.live = "MidEvent".equals(x.status);
        x.upcoming = "PreEvent".equals(x.status);
        x.finished = "FT".equals(comment) || "FT".equals(period);
        JSONObject date = e.optJSONObject("date");
        x.display = !comment.isEmpty() ? comment : (date != null ? date.optString("time", "") : "");
        x.kickoff = toLocal(e.optString("startDateTime", ""));
        return x;
    }

    private static String value(JSONObject o, String key) {
        JSONObject s = o.optJSONObject(key);
        return s == null ? "" : s.optString("value", "");
    }

    private String toLocal(String iso) {
        try {
            return OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(HHMM);
        } catch (Exception ex) {
            return "N/A";
        }
    }

    private boolean isExcluded(String league) {
        String l = league.toLowerCase();
        for (String x : excluded) if (l.contains(x.toLowerCase())) return true;
        return false;
    }

    /** How long to wait before the next refresh of today's page; other days only need hourly. */
    public static long nextRefreshMs(boolean anyLive, boolean kickoffWithinHour) {
        return anyLive ? 2 * 60_000L : kickoffWithinHour ? 10 * 60_000L : 60 * 60_000L;
    }
}
