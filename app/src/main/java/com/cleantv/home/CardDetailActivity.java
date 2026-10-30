package com.cleantv.home;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;
import android.view.Gravity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Full-screen view of a card, in the preview's look: heading, big number, boxes (forecast days,
 * prayer times, clocks) and extra lines. The standby card also lets you change the time here.
 */
public final class CardDetailActivity extends Activity {
    private LinearLayout box;
    private Source source;
    private boolean reduce;
    private int accent;

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
        source = CardRepo.byId(getIntent().getStringExtra("id"));
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 11, 17));
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.u(5f), Ui.u(3.6f), Ui.u(5f), Ui.u(3f));
        scroll.addView(box);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        box.addView(Ui.text(this, getString(R.string.loading), 2f, Color.WHITE));
        load(false);
    }

    private void load(boolean force) {
        if (force) Cache.expireAll(this);
        CardRepo.load(this, source, true, this::render);
    }

    private static LinearLayout.LayoutParams top(int px) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px;
        return lp;
    }

    private void render(final Card k) {
        box.removeAllViews();
        box.addView(Ui.text(this, k.title, 3.2f, Color.WHITE));
        String when = Instant.ofEpochMilli(k.updated).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US));
        String meta = getString(R.string.updated, when) + (k.offline ? "  \u00B7  " + getString(R.string.offline_note) : "")
                + (k.credit.isEmpty() ? "" : "  \u00B7  " + k.credit);
        box.addView(Ui.text(this, meta, 1.25f, Color.argb(150, 255, 255, 255)));

        TextView big = Ui.text(this, k.big, 6.5f, Color.WHITE);
        big.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        box.addView(big, top(Ui.u(1.2f)));

        LinearLayout subRow = new LinearLayout(this);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        subRow.setGravity(Gravity.CENTER_VERTICAL);
        subRow.addView(Ui.text(this, k.sub, 1.9f, Color.WHITE));
        if (!k.badge.isEmpty()) {
            TextView badge = Ui.text(this, k.badge, 1.3f, Color.BLACK);
            badge.setPadding(Ui.u(1f), Ui.u(0.3f), Ui.u(1f), Ui.u(0.3f));
            badge.setBackground(Ui.solid(k.accent, 4f));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.setMarginStart(Ui.u(1.2f));
            subRow.addView(badge, bl);
        }
        box.addView(subRow, top(Ui.u(0.4f)));
        if (!k.detail.isEmpty()) box.addView(Ui.text(this, k.detail, 1.5f, Color.argb(190, 255, 255, 255)), top(Ui.u(0.3f)));
        if (k.progress >= 0f) {
            Ui.Bar bar = new Ui.Bar(this);
            bar.set(k.progress, k.accent);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.u(40f), Math.max(2, Ui.u(0.6f)));
            lp.topMargin = Ui.u(1.2f);
            box.addView(bar, lp);
        }

        // boxes: forecast days, prayer times, world clocks
        for (int i = 0; i < k.items.size(); i += 3) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < Math.min(k.items.size(), i + 3); j++) {
                String[] it = k.items.get(j);
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setPadding(Ui.u(1.4f), Ui.u(1.1f), Ui.u(1.4f), Ui.u(1.1f));
                cell.setBackground(Ui.solid(j == k.highlight ? k.accent : Color.argb(32, 255, 255, 255), 1.4f));
                int fg = j == k.highlight ? Color.BLACK : Color.WHITE;
                cell.addView(Ui.text(this, it[0], 1.5f, j == k.highlight ? Color.BLACK : Color.argb(190, 255, 255, 255)));
                TextView v = Ui.text(this, it[1], 3.0f, fg);
                v.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                cell.addView(v);
                if (!it[2].isEmpty()) cell.addView(Ui.text(this, it[2], 1.2f, j == k.highlight ? Color.BLACK : Color.argb(170, 255, 255, 255)));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                cp.setMarginEnd(Ui.u(1.4f));
                row.addView(cell, cp);
            }
            box.addView(row, top(Ui.u(1.4f)));
        }
        for (String line : k.lines) box.addView(Ui.text(this, line, 1.7f, Color.WHITE), top(Ui.u(1f)));

        // the standby card doubles as its own setting
        if (k.id.equals("standby")) {
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            final int[] values = {0, 1, 4, 6};
            int[] names = {R.string.off, R.string.hours_1, R.string.hours_4, R.string.hours_6};
            for (int i = 0; i < values.length; i++) {
                final int v = values[i];
                Ui.Chip c = new Ui.Chip(this, getString(names[i]), Prefs.standby(this) == v, accent, 1.7f, reduce);
                c.setOnClickListener(x -> {
                    Prefs.standby(this, v);
                    load(false);
                });
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cp.setMarginEnd(Ui.u(1.2f));
                chips.addView(c, cp);
            }
            box.addView(chips, top(Ui.u(2f)));
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Ui.Chip refresh = new Ui.Chip(this, getString(R.string.refresh), false, accent, 1.7f, reduce);
        refresh.setOnClickListener(x -> load(true));
        Ui.Chip back = new Ui.Chip(this, getString(R.string.back), false, accent, 1.7f, reduce);
        back.setOnClickListener(x -> finish());
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.setMarginEnd(Ui.u(1.2f));
        buttons.addView(refresh, gap);
        buttons.addView(back);
        box.addView(buttons, top(Ui.u(2.4f)));
        refresh.requestFocus();
    }
}
