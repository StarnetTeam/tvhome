package com.cleantv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
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
 * Today's matches: three day tabs (today, tomorrow, after tomorrow), a leagues filter and the
 * match list, all reachable with the remote. Data comes from MatchesData (the BBC Sport page).
 */
public final class MatchesActivity extends Activity {
    private static final int LIVE_RED = Color.rgb(248, 113, 113);
    private static final long STALE_MS = 5 * 60_000L;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<MatchesData.Day> days = new ArrayList<>();
    private final TextView[] tabs = new TextView[3];
    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            if (alive) load(false);
        }
    };

    private LinearLayout list;
    private TextView status;
    private int day;
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
        if (reduce) getWindow().setWindowAnimations(0);
        for (int i = 0; i < 3; i++) days.add(null);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Themes.background(this));
        root.setPadding(Ui.u(5f), Ui.u(3f), Ui.u(5f), Ui.u(2f));

        root.addView(Ui.text(this, getString(R.string.card_matches), 3f, Color.WHITE));
        root.addView(Ui.text(this, getString(R.string.credit_bbc), 1.3f, Color.argb(170, 255, 255, 255)));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.topMargin = Ui.u(1.4f);
        for (int i = 0; i < 3; i++) {
            final int d = i;
            tabs[i] = Ui.pill(this, dayLabel(i), 1.6f, reduce, () -> {
                day = d;
                render();
            });
            bar.addView(tabs[i], gap());
        }
        bar.addView(Ui.pill(this, getString(R.string.leagues), 1.6f, reduce, this::chooseLeagues), gap());
        bar.addView(Ui.pill(this, getString(R.string.refresh), 1.6f, reduce, () -> load(true)), gap());
        root.addView(bar, barLp);

        status = Ui.text(this, "", 1.5f, Color.argb(210, 255, 255, 255));
        status.setPadding(0, Ui.u(1f), 0, Ui.u(0.4f));
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
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
        for (int i = 0; i < 3; i++) {
            tabs[i].setTag(i == day ? Themes.current(this).accent : Color.WHITE);
            if (!tabs[i].hasFocus()) tabs[i].setTextColor((Integer) tabs[i].getTag());
        }
        list.removeAllViews();
        MatchesData.Day data = days.get(day);
        if (data == null) {
            status.setText(loading ? getString(R.string.loading) : getString(R.string.m_error));
            return;
        }
        Set<String> visible = MatchesData.visible(this, data.leagues);
        int shown = 0;
        for (BbcFootballProvider.League lg : data.leagues) {
            if (!visible.contains(lg.name)) continue;
            TextView h = Ui.text(this, lg.name, 1.6f, Themes.current(this).accent);
            h.setPadding(Ui.u(0.4f), Ui.u(1.4f), 0, Ui.u(0.4f));
            list.addView(h);
            for (BbcFootballProvider.Match m : lg.matches) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.u(0.4f);
                list.addView(row(m), lp);
                shown++;
            }
        }
        status.setText(shown == 0 ? getString(R.string.m_none_filtered)
                : data.offline ? getString(R.string.offline_note) : "");
    }

    private TextView row(final BbcFootballProvider.Match m) {
        String when = m.live ? "\u25CF " + m.display : m.finished ? getString(R.string.full_time) : m.kickoff;
        TextView t = Ui.pill(this, when + "    " + m.home + "   " + m.score + "   " + m.away, 2f, reduce, () -> showDetails(m));
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        int color = m.live ? LIVE_RED : Color.WHITE;
        t.setTag(color);
        t.setTextColor(color);
        return t;
    }

    private void showDetails(BbcFootballProvider.Match m) {
        String state = m.live ? getString(R.string.live_now) + " " + m.display
                : m.finished ? getString(R.string.full_time) : getString(R.string.kickoff_at, m.kickoff);
        new AlertDialog.Builder(this)
                .setTitle(m.home + "  " + m.score + "  " + m.away)
                .setMessage(m.league + "\n" + state)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void chooseLeagues() {
        Set<String> all = new TreeSet<>();
        for (MatchesData.Day d : days) {
            if (d != null) for (BbcFootballProvider.League l : d.leagues) all.add(l.name);
        }
        if (all.isEmpty()) return;
        final String[] names = all.toArray(new String[0]);
        final boolean[] on = new boolean[names.length];
        Set<String> visible = new HashSet<>();
        for (MatchesData.Day d : days) if (d != null) visible.addAll(MatchesData.visible(this, d.leagues));
        for (int i = 0; i < names.length; i++) on[i] = visible.contains(names[i]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.leagues)
                .setMultiChoiceItems(names, on, (dialog, which, checked) -> on[which] = checked)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Set<String> v = new HashSet<>();
                    for (int i = 0; i < names.length; i++) if (on[i]) v.add(names[i]);
                    MatchesData.setVisible(this, v);
                    render();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String dayLabel(int offset) {
        int res = offset == 0 ? R.string.today : offset == 1 ? R.string.tomorrow : R.string.after_tomorrow;
        return getString(res) + " " + LocalDate.now(MatchesData.ZONE).plusDays(offset)
                .format(DateTimeFormatter.ofPattern("dd/MM"));
    }

    private LinearLayout.LayoutParams gap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Ui.u(1f));
        return lp;
    }
}
