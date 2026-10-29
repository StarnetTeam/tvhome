package com.cleantv.home;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Full-screen view of one card: the headline numbers plus every extra line the source made. */
public final class CardDetailActivity extends Activity {
    private LinearLayout box;
    private Source source;
    private boolean reduce;

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
        source = CardRepo.byId(getIntent().getStringExtra("id"));
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Themes.background(this));
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.u(6f), Ui.u(4f), Ui.u(6f), Ui.u(4f));
        scroll.addView(box);
        setContentView(scroll);
        box.addView(Ui.text(this, getString(R.string.loading), 2f, Color.WHITE));
        load(false);
    }

    private void load(boolean force) {
        if (force) Cache.expireAll(this);
        CardRepo.load(this, source, true, this::render);
    }

    private void render(Card k) {
        box.removeAllViews();
        box.addView(Ui.text(this, k.title, 2.6f, k.accent));
        TextView big = Ui.text(this, k.big, 7f, Color.WHITE);
        big.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        box.addView(big);
        box.addView(Ui.text(this, k.sub, 2.2f, Color.WHITE));
        if (!k.detail.isEmpty()) box.addView(Ui.text(this, k.detail, 1.7f, Color.argb(190, 255, 255, 255)));
        if (k.progress >= 0f) {
            Ui.Bar bar = new Ui.Bar(this);
            bar.set(k.progress, k.accent);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.u(40f), Ui.u(0.6f));
            lp.topMargin = Ui.u(1f);
            box.addView(bar, lp);
        }
        for (String line : k.lines) {
            TextView t = Ui.text(this, line, 1.9f, Color.WHITE);
            t.setPadding(0, Ui.u(1f), 0, 0);
            box.addView(t);
        }
        String when = Instant.ofEpochMilli(k.updated).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US));
        TextView foot = Ui.text(this, getString(R.string.updated, when)
                + (k.offline ? "  \u00B7  " + getString(R.string.offline_note) : ""), 1.3f, Color.argb(160, 255, 255, 255));
        foot.setPadding(0, Ui.u(2.5f), 0, 0);
        box.addView(foot);
        if (!k.credit.isEmpty()) box.addView(Ui.text(this, k.credit, 1.3f, Color.argb(160, 255, 255, 255)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView refresh = Ui.pill(this, getString(R.string.refresh), 1.8f, reduce, () -> load(true));
        TextView back = Ui.pill(this, getString(R.string.back), 1.8f, reduce, this::finish);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.setMarginEnd(Ui.u(1.2f));
        buttons.addView(refresh, gap);
        buttons.addView(back);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Ui.u(2.5f);
        box.addView(buttons, bl);
        refresh.requestFocus();
    }
}
