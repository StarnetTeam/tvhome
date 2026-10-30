package com.cleantv.home;

import android.app.ActivityManager;
import android.content.Context;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The cards. Each one reads real data: the network (weather, prayer times, matches) or the device
 * itself (clock, memory and storage, connection, standby timer). build(c, false) only uses saved
 * data, so it can run every few seconds; build(c, true) may refresh from the network when the
 * saved answer is older than its time-to-live.
 */
final class Sources {
    private Sources() {}

    // ---- helpers ------------------------------------------------------------------------

    static Card base(Context c, Source s) {
        Card k = new Card();
        k.id = s.id();
        k.title = c.getString(s.titleRes());
        k.accent = s.accent();
        k.updated = System.currentTimeMillis();
        return k;
    }

    /** Numbers go into strings as text so digits are always Latin, in every language. */
    static String n(long v) { return String.valueOf(v); }

    static String duration(Context c, long ms) {
        long m = Math.max(0L, ms / 60_000L);
        if (m >= 60) return c.getString(R.string.dur_hm, n(m / 60), n(m % 60));
        return c.getString(R.string.dur_m, n(m));
    }

    static String hhmm(LocalTime t) {
        return String.format(Locale.US, "%02d:%02d", t.getHour(), t.getMinute());
    }

    static String gb(double bytes) {
        return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private static String condition(Context c, int code) {
        int r;
        if (code == 0) r = R.string.wx_clear;
        else if (code <= 2) r = R.string.wx_partly;
        else if (code == 3) r = R.string.wx_cloudy;
        else if (code == 45 || code == 48) r = R.string.wx_fog;
        else if (code >= 51 && code <= 57) r = R.string.wx_drizzle;
        else if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) r = R.string.wx_rain;
        else if ((code >= 71 && code <= 77) || code == 85 || code == 86) r = R.string.wx_snow;
        else if (code >= 95) r = R.string.wx_thunder;
        else r = R.string.wx_cloudy;
        return c.getString(r);
    }

    private static String aqiText(Context c, int aqi) {
        int r = aqi <= 50 ? R.string.aqi_good : aqi <= 100 ? R.string.aqi_moderate
                : aqi <= 150 ? R.string.aqi_sensitive : aqi <= 200 ? R.string.aqi_unhealthy
                : aqi <= 300 ? R.string.aqi_very : R.string.aqi_hazardous;
        return c.getString(r);
    }

    // ---- weather: Open-Meteo (free, no key, non-commercial) ------------------------------

    static final class Weather implements Source {
        @Override public String id() { return "weather"; }
        @Override public int titleRes() { return R.string.card_weather; }
        @Override public int accent() { return 0xFF38BDF8; }

        @Override
        public Card build(Context c, boolean fetch) throws Exception {
            final String lat = Prefs.lat(c), lon = Prefs.lon(c);
            Cache.Raw r = Cache.get(c, "wx:" + lat + "," + lon, 30 * 60_000L, fetch, () -> Http.get(
                    "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                            + "&current=temperature_2m,apparent_temperature,weather_code"
                            + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                            + "&timezone=auto&forecast_days=4"));
            Card k = base(c, this);
            k.credit = c.getString(R.string.credit_openmeteo);
            if (r.body == null) {
                k.big = "--";
                k.sub = c.getString(R.string.no_data);
                return k;
            }
            JSONObject root = new JSONObject(r.body);
            JSONObject cur = root.getJSONObject("current");
            JSONObject daily = root.getJSONObject("daily");
            JSONArray hi = daily.getJSONArray("temperature_2m_max");
            JSONArray lo = daily.getJSONArray("temperature_2m_min");
            JSONArray codes = daily.getJSONArray("weather_code");
            JSONArray days = daily.getJSONArray("time");
            long temp = Math.round(cur.getDouble("temperature_2m"));
            long feels = Math.round(cur.getDouble("apparent_temperature"));
            k.big = temp + "\u00B0";
            k.sub = condition(c, cur.getInt("weather_code")) + " \u00B7 " + Prefs.city(c);
            k.detail = c.getString(R.string.wx_feels, n(feels), n(Math.round(hi.getDouble(0))), n(Math.round(lo.getDouble(0))));
            try {
                final String aqKey = "aq:" + lat + "," + lon;
                Cache.Raw a = Cache.get(c, aqKey, 60 * 60_000L, fetch, () -> Http.get(
                        "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + lat
                                + "&longitude=" + lon + "&current=us_aqi"));
                if (a.body != null) {
                    int aqi = (int) Math.round(new JSONObject(a.body).getJSONObject("current").getDouble("us_aqi"));
                    k.badge = c.getString(R.string.aqi_badge, n(aqi));
                    k.lines.add(c.getString(R.string.aqi_line, n(aqi), aqiText(c, aqi)));
                }
            } catch (Exception ignored) {
                // air quality is optional
            }
            Locale loc = LocaleHelper.numLocale(c);
            for (int i = 1; i < days.length(); i++) {
                String day = LocalDate.parse(days.getString(i)).getDayOfWeek().getDisplayName(TextStyle.FULL, loc);
                k.items.add(new String[]{day, Math.round(hi.getDouble(i)) + "\u00B0",
                        "/ " + Math.round(lo.getDouble(i)) + "\u00B0  " + condition(c, codes.getInt(i))});
            }
            k.updated = r.at;
            k.offline = r.offline;
            return k;
        }
    }

    // ---- prayer times: Aladhan (free, no key) --------------------------------------------

    static final class Prayer implements Source {
        private static final int[] NAMES = {R.string.pr_fajr, R.string.pr_sunrise, R.string.pr_dhuhr,
                R.string.pr_asr, R.string.pr_maghrib, R.string.pr_isha};
        private static final String[] KEYS = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
        private static final int[] PRAYERS = {0, 2, 3, 4, 5};   // sunrise is listed but is not a prayer

        @Override public String id() { return "prayer"; }
        @Override public int titleRes() { return R.string.card_prayer; }
        @Override public int accent() { return 0xFF22A06B; }

        @Override
        public Card build(Context c, boolean fetch) throws Exception {
            final String lat = Prefs.lat(c), lon = Prefs.lon(c);
            final int method = Prefs.method(c);
            final String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US));
            Cache.Raw r = Cache.get(c, "pr:" + date + ":" + lat + "," + lon + ":" + method, 12 * 3600_000L, fetch,
                    () -> Http.get("https://api.aladhan.com/v1/timings/" + date + "?latitude=" + lat
                            + "&longitude=" + lon + "&method=" + method));
            Card k = base(c, this);
            k.credit = c.getString(R.string.credit_aladhan);
            if (r.body == null) {
                k.big = "--";
                k.sub = c.getString(R.string.no_data);
                return k;
            }
            JSONObject data = new JSONObject(r.body).getJSONObject("data");
            JSONObject timings = data.getJSONObject("timings");
            ZoneId zone;
            try {
                zone = ZoneId.of(data.getJSONObject("meta").getString("timezone"));
            } catch (Exception e) {
                zone = ZoneId.systemDefault();
            }
            int[] mins = new int[6];
            LocalTime[] times = new LocalTime[6];
            for (int i = 0; i < 6; i++) {
                times[i] = LocalTime.parse(timings.getString(KEYS[i]).substring(0, 5));
                mins[i] = times[i].getHour() * 60 + times[i].getMinute();
            }
            LocalTime now = ZonedDateTime.now(zone).toLocalTime();
            int nowM = now.getHour() * 60 + now.getMinute();

            // next prayer: the first one still ahead today, otherwise tomorrow's Fajr (today's time is
            // used for it; Fajr moves by about a minute a day)
            int pos = -1;
            for (int i = 0; i < PRAYERS.length; i++) {
                if (mins[PRAYERS[i]] > nowM) { pos = i; break; }
            }
            if (pos < 0) pos = 0;
            int next = PRAYERS[pos];
            int prev = PRAYERS[(pos + PRAYERS.length - 1) % PRAYERS.length];
            int after = PRAYERS[(pos + 1) % PRAYERS.length];
            int untilM = (mins[next] - nowM + 1440) % 1440;
            int span = Math.max(1, (mins[next] - mins[prev] + 1440) % 1440);
            int elapsed = (nowM - mins[prev] + 1440) % 1440;

            k.big = hhmm(times[next]);
            k.sub = c.getString(R.string.pr_in, c.getString(NAMES[next]), duration(c, untilM * 60_000L));
            k.detail = c.getString(R.string.pr_then, c.getString(NAMES[after]), hhmm(times[after]));
            k.progress = Math.min(1f, (float) elapsed / span);
            try {
                JSONObject h = data.getJSONObject("date").getJSONObject("hijri");
                String month = h.getJSONObject("month").getString(LocaleHelper.isArabic(c) ? "ar" : "en");
                k.lines.add(h.getString("day") + " " + month + " " + h.getString("year"));
            } catch (Exception ignored) {
                // the Hijri date is optional
            }
            for (int i = 0; i < 6; i++) k.items.add(new String[]{c.getString(NAMES[i]), hhmm(times[i]), ""});
            k.highlight = next;
            k.updated = r.at;
            k.offline = r.offline;
            return k;
        }
    }

    // ---- matches: BBC Sport page through BbcFootballProvider (unofficial) ----------------

    static final class Matches implements Source {
        @Override public String id() { return "matches"; }
        @Override public int titleRes() { return R.string.card_matches; }
        @Override public int accent() { return 0xFF22C55E; }

        @Override
        public Card build(Context c, boolean fetch) throws Exception {
            Card k = base(c, this);
            k.credit = c.getString(R.string.credit_bbc);
            MatchesData.Day d = MatchesData.day(c, LocalDate.now(MatchesData.ZONE), fetch);
            if (d == null) {
                k.big = "--";
                k.sub = c.getString(R.string.no_data);
                return k;
            }
            Set<String> visible = MatchesData.visible(c, d.leagues);
            LocalTime now = LocalTime.now(MatchesData.ZONE);
            String nowS = hhmm(now);
            int live = 0, total = 0;
            BbcFootballProvider.Match firstLive = null, next = null;
            for (BbcFootballProvider.League l : d.leagues) {
                if (!visible.contains(l.name)) continue;
                for (BbcFootballProvider.Match m : l.matches) {
                    total++;
                    if (m.live) {
                        live++;
                        if (firstLive == null) firstLive = m;
                    } else if (m.upcoming && m.kickoff.compareTo(nowS) >= 0
                            && (next == null || m.kickoff.compareTo(next.kickoff) < 0)) {
                        next = m;
                    }
                }
            }
            k.big = live > 0 ? c.getString(R.string.m_live, n(live))
                    : total > 0 ? c.getString(R.string.m_today, n(total)) : c.getString(R.string.m_none);
            k.sub = firstLive != null
                    ? firstLive.home + " " + firstLive.score + " " + firstLive.away + " " + firstLive.display
                    : c.getString(R.string.m_nolive);
            k.detail = next != null
                    ? c.getString(R.string.m_next, next.home, next.away, next.kickoff)
                    : c.getString(R.string.m_nonext);
            if (live > 0) k.badge = c.getString(R.string.m_badge_live);
            k.updated = d.at;
            k.offline = d.offline;
            return k;
        }
    }

    // ---- world clock: the device clock and time zones, no network -------------------------

    static final class Clock implements Source {
        private static final String[] ZONES = {"Africa/Cairo", "Europe/London", "America/New_York", "Asia/Dubai"};
        private static final int[] NAMES = {R.string.city_cairo, R.string.city_london, R.string.city_newyork, R.string.city_dubai};

        @Override public String id() { return "clock"; }
        @Override public int titleRes() { return R.string.card_clock; }
        @Override public int accent() { return 0xFFA78BFA; }

        @Override
        public Card build(Context c, boolean fetch) {
            Card k = base(c, this);
            k.big = hhmm(ZonedDateTime.now().toLocalTime());
            StringBuilder first = new StringBuilder(), second = new StringBuilder();
            for (int i = 0; i < ZONES.length; i++) {
                String part = c.getString(NAMES[i]) + " " + hhmm(ZonedDateTime.now(ZoneId.of(ZONES[i])).toLocalTime());
                StringBuilder target = i < 2 ? first : second;
                if (target.length() > 0) target.append(" \u00B7 ");
                target.append(part);
            }
            k.items.add(new String[]{c.getString(R.string.clock_local), hhmm(ZonedDateTime.now().toLocalTime()), ""});
            for (int i = 0; i < ZONES.length; i++) {
                k.items.add(new String[]{c.getString(NAMES[i]), hhmm(ZonedDateTime.now(ZoneId.of(ZONES[i])).toLocalTime()), ""});
            }
            k.sub = first.toString();
            k.detail = second.toString();
            return k;
        }
    }

    // ---- memory and storage: from the device ---------------------------------------------

    static final class Storage implements Source {
        @Override public String id() { return "storage"; }
        @Override public int titleRes() { return R.string.card_storage; }
        @Override public int accent() { return 0xFF94A3B8; }

        @Override
        public Card build(Context c, boolean fetch) {
            Card k = base(c, this);
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            if (am != null) am.getMemoryInfo(mi);
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            double total = fs.getTotalBytes(), free = fs.getAvailableBytes();
            k.big = gb(mi.availMem);
            k.sub = c.getString(R.string.st_ram, gb(mi.totalMem));
            k.detail = c.getString(R.string.st_storage, gb(free), gb(total));
            k.progress = total > 0 ? (float) ((total - free) / total) : -1f;
            k.lines.add(c.getString(R.string.st_ram_line, gb(mi.availMem), gb(mi.totalMem)));
            k.lines.add(c.getString(R.string.st_storage, gb(free), gb(total)));
            return k;
        }
    }

    // ---- network: connection type, signal and address ------------------------------------

    static final class Network implements Source {
        @Override public String id() { return "network"; }
        @Override public int titleRes() { return R.string.card_network; }
        @Override public int accent() { return 0xFF34D399; }

        @Override
        public Card build(Context c, boolean fetch) {
            Card k = base(c, this);
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) {
                k.big = c.getString(R.string.net_off);
                k.sub = c.getString(R.string.net_none);
                return k;
            }
            String ip = localIp();
            k.detail = ip.isEmpty() ? "" : c.getString(R.string.net_ip, ip);
            if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
                k.big = c.getString(R.string.net_wifi);
                k.sub = c.getString(R.string.net_connected);
                try {
                    WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    int pct = WifiManager.calculateSignalLevel(wm.getConnectionInfo().getRssi(), 101);
                    k.sub = c.getString(R.string.net_signal, n(pct));
                    k.progress = pct / 100f;
                } catch (Exception ignored) {
                    // signal strength is optional
                }
            } else if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) {
                k.big = c.getString(R.string.net_eth);
                k.sub = c.getString(R.string.net_connected);
            } else {
                k.big = c.getString(R.string.net_other);
                k.sub = c.getString(R.string.net_connected);
            }
            k.lines.add(k.big + " \u00B7 " + k.sub);
            if (!ip.isEmpty()) k.lines.add(k.detail);
            return k;
        }

        static String localIp() {
            try {
                Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
                if (ifs == null) return "";
                for (NetworkInterface nif : Collections.list(ifs)) {
                    if (!nif.isUp() || nif.isLoopback()) continue;
                    for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                        if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
                    }
                }
            } catch (Exception ignored) {
                // no address to show
            }
            return "";
        }
    }

    // ---- standby timer: the real countdown kept by IdleService ---------------------------

    static final class Standby implements Source {
        @Override public String id() { return "standby"; }
        @Override public int titleRes() { return R.string.card_standby; }
        @Override public int accent() { return 0xFFF59E0B; }

        @Override
        public Card build(Context c, boolean fetch) {
            Card k = base(c, this);
            int hours = Prefs.standby(c);
            if (hours == 0) {
                k.big = c.getString(R.string.off);
                k.sub = c.getString(R.string.sb_auto_off);
                k.detail = c.getString(R.string.sb_hint);
                return k;
            }
            if (Build.VERSION.SDK_INT < 28) {
                k.big = "9+";
                k.sub = c.getString(R.string.sb_needs9);
                return k;
            }
            if (!IdleService.running) {
                k.big = c.getString(R.string.sb_setup);
                k.sub = c.getString(R.string.sb_enable);
                k.detail = c.getString(R.string.sb_enable_hint);
                k.badge = c.getString(R.string.sb_badge_off);
                return k;
            }
            long total = hours * 3_600_000L;
            long left = Math.max(0L, total - (System.currentTimeMillis() - IdleService.lastInput));
            k.big = duration(c, left);
            k.sub = c.getString(R.string.sb_until);
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            boolean audio = Prefs.keepAudio(c) && am != null && am.isMusicActive();
            k.detail = c.getString(audio ? R.string.sb_audio : R.string.sb_reset);
            k.progress = 1f - (float) left / total;
            k.lines.add(c.getString(R.string.sb_line, n(hours)));
            return k;
        }
    }

    // ---- remote access: is network debugging (ADB) open on this device? --------------------

    static final class Remote implements Source {
        @Override public String id() { return "remote"; }
        @Override public int titleRes() { return R.string.card_remote; }
        @Override public int accent() { return 0xFFF97316; }

        @Override
        public Card build(Context c, boolean fetch) {
            Card k = base(c, this);
            boolean adb = android.provider.Settings.Global.getInt(c.getContentResolver(),
                    android.provider.Settings.Global.ADB_ENABLED, 0) == 1;
            boolean open = portOpen(5555);
            String ip = Network.localIp();
            k.big = c.getString(open ? R.string.on : R.string.off);
            k.sub = c.getString(open ? R.string.rm_open : adb ? R.string.rm_usb : R.string.rm_off);
            k.detail = open && !ip.isEmpty() ? ip + ":5555" : c.getString(R.string.rm_hint);
            if (open) k.badge = c.getString(R.string.rm_badge);
            k.lines.add(c.getString(R.string.rm_explain));
            return k;
        }

        private static boolean portOpen(int port) {
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 300);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
