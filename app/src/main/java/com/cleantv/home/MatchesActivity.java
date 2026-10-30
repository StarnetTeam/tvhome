package com.cleantv.home;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Matches in the preview's layout: day tabs, league chips, the list on one side (white row when
 * focused, team badges) and the details of the focused match on the other.
 */
public final class MatchesActivity extends Activity {
    private static final int LIVE_RED = Color.rgb(239, 68, 68);
    private static final long STALE_MS = 5 * 60_000L;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<MatchesData.Day> days = new ArrayList<>();
    private final Ui.Chip[] tabs = new Ui.Chip[3];
    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            if (alive) load(false);
        }
    };

    private LinearLayout list, detail, chipsRow;
    private TextView status;
    private int day, accent;
    private boolean alive, loading, reduce;
    private long loadedAt;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        reduce = Prefs.reduce(this);
        accent = Themes.current(this).accent;
        if (reduce) getWindow().setWindowAnimations(0);
        for (int i = 0; i < 3; i++) days.add(null);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 11, 17));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.u(5f), Ui.u(3.6f), Ui.u(5f), Ui.u(2.4f));

        col.addView(Ui.text(this, getString(R.string.card_matches), 2.6f, Color.WHITE));
        col.addView(Ui.text(this, getString(R.string.credit_bbc), 1.05f, Color.argb(150, 255, 255, 255)));

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            final int d = i;
            tabs[i] = new Ui.Chip(this, dayLabel(i), i == 0, accent, 1.3f, reduce);
            tabs[i].setOnClickListener(v -> {
                day = d;
                render();
            });
            tabRow.addView(tabs[i], gap());
        }
        Ui.Chip refresh = new Ui.Chip(this, getString(R.string.refresh), false, accent, 1.3f, reduce);
        refresh.setOnClickListener(v -> load(true));
        tabRow.addView(refresh, gap());
        col.addView(tabRow, top(Ui.u(1.2f)));

        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setPadding(Ui.u(0.4f), Ui.u(0.4f), Ui.u(0.4f), Ui.u(0.4f));
        chipScroll.addView(chipsRow);
        col.addView(chipScroll, top(Ui.u(0.6f)));

        status = Ui.text(this, "", 1.4f, Color.argb(210, 255, 255, 255));
        col.addView(status, top(Ui.u(0.6f)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        body.addView(scroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.35f));
        detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(Ui.u(1.6f), Ui.u(1.4f), Ui.u(1.6f), Ui.u(1.4f));
        detail.setBackground(Ui.solid(Color.argb(28, 255, 255, 255), 1.4f));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        dl.setMarginStart(Ui.u(2f));
        dl.gravity = Gravity.TOP;
        body.addView(detail, dl);
        col.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        alive = true;
        if (days.get(0) == null || System.currentTimeMillis() - loadedAt > STALE_MS) {
            load(false);
        } else {
            render();
            scheduleRefresh();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        alive = false;
        ui.removeCallbacks(refresher);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    /** Loads the three days; pages that are still fresh in the cache are not requested again. */
    private void load(final boolean force) {
        if (loading) return;
        loading = true;
        status.setText(R.string.loading);
        if (force) Cache.expirePrefix(this, "matches:");
        io.execute(() -> {
            LocalDate today = LocalDate.now(MatchesData.ZONE);
            for (int i = 0; i < 3 && alive; i++) {
                final int d = i;
                final MatchesData.Day result = MatchesData.day(this, today.plusDays(i), true);
                ui.post(() -> {
                    days.set(d, result);
                    render();
                });
                try {
                    Thread.sleep(500);   // one request at a time, politely spaced
                } catch (InterruptedException e) {
                    break;
                }
            }
            ui.post(() -> {
                loading = false;
                loadedAt = System.currentTimeMillis();
                scheduleRefresh();
            });
        });
    }

    private void scheduleRefresh() {
        ui.removeCallbacks(refresher);
        if (alive) ui.postDelayed(refresher, Math.max(30_000L, MatchesData.ttl(this, LocalDate.now(MatchesData.ZONE))));
    }

    private void render() {
        for (int i = 0; i < 3; i++) tabs[i].setOn(i == day);
        list.removeAllViews();
        detail.removeAllViews();
        chipsRow.removeAllViews();
        MatchesData.Day data = days.get(day);
        if (data == null) {
            status.setText(loading ? getString(R.string.loading) : getString(R.string.m_error));
            return;
        }
        // league chips: on = shown
        final Set<String> all = new TreeSet<>();
        for (MatchesData.Day d : days) {
            if (d != null) for (BbcFootballProvider.League l : d.leagues) all.add(l.name);
        }
        Set<String> visible = new HashSet<>();
        for (MatchesData.Day d : days) if (d != null) visible.addAll(MatchesData.visible(this, d.leagues));
        for (final String name : all) {
            final Ui.Chip c = new Ui.Chip(this, name, visible.contains(name), accent, 1.1f, reduce);
            c.setOnClickListener(v -> {
                Set<String> v2 = MatchesData.visible(this, new ArrayList<BbcFootballProvider.League>());
                Set<String> now = new HashSet<>(v2);
                if (!now.remove(name)) now.add(name);
                MatchesData.setVisible(this, now);
                render();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(Ui.u(0.6f));
            chipsRow.addView(c, lp);
        }

        int shown = 0;
        BbcFootballProvider.Match first = null;
        for (BbcFootballProvider.League lg : data.leagues) {
            if (!visible.contains(lg.name)) continue;
            TextView h = Ui.text(this, lg.name, 1.4f, accent == Color.WHITE ? Color.argb(180, 255, 255, 255) : accent);
            h.setPadding(Ui.u(0.4f), Ui.u(1.2f), 0, Ui.u(0.3f));
            list.addView(h);
            for (BbcFootballProvider.Match m : lg.matches) {
                list.addView(matchRow(m), top(Ui.u(0.3f)));
                if (first == null) first = m;
                shown++;
            }
        }
        status.setText(shown == 0 ? getString(R.string.m_none_filtered)
                : data.offline ? getString(R.string.offline_note) : "");
        if (first != null) showDetail(first);
    }

    /** One match: time, home team and badge, score, badge and away team. White when focused. */
    private LinearLayout matchRow(final BbcFootballProvider.Match m) {
        final LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.u(1.2f), Ui.u(0.6f), Ui.u(1.2f), Ui.u(0.6f));
        r.setFocusable(true);
        r.setClickable(true);
        final String when = m.live ? "\u25CF " + m.display : m.finished ? getString(R.string.full_time) : m.kickoff;
        final TextView time = Ui.text(this, when, 1.6f, m.live ? LIVE_RED : Color.WHITE);
        final TextView home = Ui.text(this, m.home, 1.6f, Color.WHITE);
        final TextView score = Ui.text(this, m.upcoming ? "v" : m.score, 1.6f, Color.WHITE);
        final TextView away = Ui.text(this, m.away, 1.6f, Color.WHITE);
        score.setTypeface(Typeface.DEFAULT_BOLD);
        score.setGravity(Gravity.CENTER);
        home.setGravity(Gravity.END);
        Ui.oneLine(home);
        Ui.oneLine(away);
        r.addView(time, new LinearLayout.LayoutParams(Ui.u(6f), ViewGroup.LayoutParams.WRAP_CONTENT));
        r.addView(home, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView c1 = Ui.crest(this, m.home, 2.2f);
        LinearLayout.LayoutParams c1p = new LinearLayout.LayoutParams(Ui.u(2.2f), Ui.u(2.2f));
        c1p.setMarginStart(Ui.u(0.6f));
        c1p.setMarginEnd(Ui.u(0.8f));
        r.addView(c1, c1p);
        r.addView(score, new LinearLayout.LayoutParams(Ui.u(6f), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView c2 = Ui.crest(this, m.away, 2.2f);
        LinearLayout.LayoutParams c2p = new LinearLayout.LayoutParams(Ui.u(2.2f), Ui.u(2.2f));
        c2p.setMarginStart(Ui.u(0.8f));
        c2p.setMarginEnd(Ui.u(0.6f));
        r.addView(c2, c2p);
        r.addView(away, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.setBackground(Ui.solid(Color.TRANSPARENT, 1f));
        r.setOnFocusChangeListener((v, focused) -> {
            r.setBackground(Ui.solid(focused ? Color.WHITE : Color.TRANSPARENT, 1f));
            int fg = focused ? Color.BLACK : Color.WHITE;
            time.setTextColor(m.live ? (focused ? LIVE_RED : LIVE_RED) : fg);
            home.setTextColor(fg);
            score.setTextColor(fg);
            away.setTextColor(fg);
            if (focused) showDetail(m);
        });
        return r;
    }

    /** The panel beside the list: league, teams with badges, status and kick-off. */
    private void showDetail(BbcFootballProvider.Match m) {
        detail.removeAllViews();
        detail.addView(Ui.text(this, m.league, 1.3f, Color.argb(180, 255, 255, 255)));
        LinearLayout teams = new LinearLayout(this);
        teams.setOrientation(LinearLayout.HORIZONTAL);
        teams.setGravity(Gravity.CENTER_VERTICAL);
        teams.addView(Ui.crest(this, m.home, 2.4f));
        TextView t = Ui.text(this, m.home + "  " + (m.upcoming ? "v" : m.score) + "  " + m.away, 1.9f, Color.WHITE);
        t.setPadding(Ui.u(0.8f), 0, Ui.u(0.8f), 0);
        teams.addView(t);
        teams.addView(Ui.crest(this, m.away, 2.4f));
        detail.addView(teams, top(Ui.u(0.6f)));
        String state = m.live ? "\u25CF " + getString(R.string.live_now) + " " + m.display
                : m.finished ? getString(R.string.full_time) : getString(R.string.kickoff_at, m.kickoff);
        detail.addView(Ui.text(this, state, 1.5f, m.live ? LIVE_RED : Color.WHITE), top(Ui.u(0.8f)));
        detail.addView(Ui.text(this, getString(R.string.m_cairo_time), 1.1f, Color.argb(150, 255, 255, 255)), top(Ui.u(0.6f)));
    }

    private String dayLabel(int offset) {
        int res = offset == 0 ? R.string.today : offset == 1 ? R.string.tomorrow : R.string.after_tomorrow;
        return getString(res) + " " + LocalDate.now(MatchesData.ZONE).plusDays(offset)
                .format(DateTimeFormatter.ofPattern("dd/MM"));
    }

    private static LinearLayout.LayoutParams top(int px) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px;
        return lp;
    }

    private LinearLayout.LayoutParams gap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Ui.u(0.8f));
        return lp;
    }
}
