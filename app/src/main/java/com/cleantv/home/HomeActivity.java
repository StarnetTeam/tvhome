package com.cleantv.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The home screen: greeting and clock, a row of live info cards, then the dock and the grid of
 * all apps. Everything is built in code and works with the D-pad only.
 */
public final class HomeActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, CardTile> tiles = new HashMap<>();
    private final CardRepo.Listener cardListener = k -> {
        CardTile t = tiles.get(k.id);
        if (t != null) t.bind(k);
    };

    private LinearLayout cardsRow, dockRow, gridBox;
    private HorizontalScrollView cardsScroll;
    private TextView greetView, clockView, dateView, nameView;
    private String shownLang = "", shownCards = "";
    private int shownTheme, shownCols;
    private long shownAppsRev = -1L;
    private boolean reduce, resumed, loadingApps, packagesChanged;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateClock();
            refreshCards();
            handler.postDelayed(this, 30_000L);
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            packagesChanged = true;
            if (resumed) loadApps();
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        reduce = Prefs.reduce(this);
        shownLang = Prefs.lang(this);
        shownTheme = Prefs.theme(this);
        shownCols = Prefs.cols(this);
        if (reduce) getWindow().setWindowAnimations(0);
        buildUi();
        buildCards();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(packages);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (!shownLang.equals(Prefs.lang(this)) || shownTheme != Prefs.theme(this) || reduce != Prefs.reduce(this)) {
            recreate();
            return;
        }
        updateClock();
        if (!shownCards.equals(Prefs.cards(this).toString())) buildCards();
        if (packagesChanged || shownAppsRev != Prefs.appsRev(this) || shownCols != Prefs.cols(this)) {
            shownCols = Prefs.cols(this);
            loadApps();
        }
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already here: jump back to the first app of the dock
        if (dockRow != null && dockRow.getChildCount() > 0) dockRow.getChildAt(0).requestFocus();
    }

    @Override
    public void onBackPressed() {
        // a launcher has nowhere to go back to
    }

    // ---- building the screen ------------------------------------------------------------

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Themes.background(this));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setPadding(Ui.u(5f), Ui.u(3.4f), Ui.u(5f), Ui.u(4f));

        // header: greeting and clock on one side, quick buttons on the other
        FrameLayout header = new FrameLayout(this);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        greetView = Ui.text(this, "", 1.25f, Color.argb(210, 255, 255, 255));
        clockView = Ui.text(this, "", 4.6f, Color.WHITE);
        clockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        dateView = Ui.text(this, "", 1.3f, Color.argb(220, 255, 255, 255));
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeRow.addView(clockView);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.setMarginStart(Ui.u(1.6f));
        timeRow.addView(dateView, dateLp);
        left.addView(greetView);
        left.addView(timeRow);
        header.addView(left, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.TOP));

        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        addPill(pills, R.string.pill_network, () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        addPill(pills, R.string.pill_system, () -> open(new Intent(Settings.ACTION_SETTINGS)));
        addPill(pills, R.string.pill_settings, () -> open(new Intent(this, SettingsActivity.class)));
        header.addView(pills, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP));
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // live info cards
        cardsScroll = new HorizontalScrollView(this);
        cardsScroll.setHorizontalScrollBarEnabled(false);
        cardsScroll.setClipToPadding(false);
        cardsScroll.setClipChildren(false);
        cardsRow = new LinearLayout(this);
        cardsRow.setOrientation(LinearLayout.HORIZONTAL);
        cardsRow.setClipChildren(false);
        cardsRow.setPadding(Ui.u(0.8f), Ui.u(0.8f), Ui.u(0.8f), Ui.u(0.8f));
        cardsScroll.addView(cardsRow);
        LinearLayout.LayoutParams cardsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardsLp.topMargin = Ui.u(1.2f);
        content.addView(cardsScroll, cardsLp);

        // name of the focused app or card
        nameView = Ui.text(this, "", 2.5f, Color.WHITE);
        nameView.setMinHeight(Ui.u(3.6f));
        Ui.oneLine(nameView);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = Ui.u(0.6f);
        nameLp.bottomMargin = Ui.u(0.6f);
        content.addView(nameView, nameLp);

        // dock band and the rest of the apps
        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setClipChildren(false);
        dockRow.setPadding(Ui.u(0.9f), Ui.u(0.9f), Ui.u(0.9f), Ui.u(0.9f));
        dockRow.setBackground(Ui.solid(Color.argb(125, 14, 16, 24), 2f));
        LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dockLp.setMarginStart(-Ui.u(0.9f));
        dockLp.setMarginEnd(-Ui.u(0.9f));
        dockLp.bottomMargin = Ui.u(1.4f);
        content.addView(dockRow, dockLp);

        gridBox = new LinearLayout(this);
        gridBox.setOrientation(LinearLayout.VERTICAL);
        gridBox.setClipChildren(false);
        content.addView(gridBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll.addView(content);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void addPill(LinearLayout row, int label, Runnable action) {
        TextView p = Ui.pill(this, getString(label), 1.3f, reduce, action);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(Ui.u(0.9f));
        row.addView(p, lp);
    }

    private void updateClock() {
        boolean h24 = DateFormat.is24HourFormat(this);
        java.util.Locale loc = LocaleHelper.numLocale(this);
        LocalTime now = LocalTime.now();
        clockView.setText(now.format(DateTimeFormatter.ofPattern(h24 ? "HH:mm" : "h:mm a", loc)));
        dateView.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", loc)));
        int hour = now.getHour();
        greetView.setText(hour < 12 ? R.string.greet_morning : hour < 18 ? R.string.greet_afternoon : R.string.greet_evening);
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- cards --------------------------------------------------------------------------

    private void buildCards() {
        cardsRow.removeAllViews();
        tiles.clear();
        Set<String> on = Prefs.cards(this);
        for (final String id : Prefs.CARD_IDS) {
            if (!on.contains(id)) continue;
            final Source s = CardRepo.byId(id);
            CardTile t = new CardTile(this, s.accent());
            t.title.setText(s.titleRes());
            t.big.setText("\u2026");
            t.setOnFocusChangeListener((v, focused) -> {
                Ui.focusAnim(v, focused, reduce);
                if (focused) nameView.setText(s.titleRes());
            });
            t.setOnClickListener(v -> {
                Intent i = id.equals("matches") ? new Intent(this, MatchesActivity.class)
                        : new Intent(this, CardDetailActivity.class).putExtra("id", id);
                open(i);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.u(27.5f), Ui.u(10.6f));
            lp.setMarginEnd(Ui.u(1.5f));
            cardsRow.addView(t, lp);
            tiles.put(id, t);
        }
        cardsScroll.setVisibility(tiles.isEmpty() ? View.GONE : View.VISIBLE);
        shownCards = on.toString();
        refreshCards();
    }

    /** Cheap: cards read saved data, and only go to the network when their answer has expired. */
    private void refreshCards() {
        for (String id : tiles.keySet()) CardRepo.load(this, CardRepo.byId(id), true, cardListener);
    }

    /** One info card. All texts are single-line so no language can push content out of the frame. */
    static final class CardTile extends LinearLayout {
        final TextView title, big, sub, detail, badge;
        final Ui.Bar bar;
        private final int accent;

        CardTile(Context c, int accent) {
            super(c);
            this.accent = accent;
            setOrientation(HORIZONTAL);
            setFocusable(true);
            setClickable(true);
            setClipChildren(true);
            setBackground(Ui.focusBg(Color.argb(165, 12, 16, 26), 1.4f));

            View strip = new View(c);
            strip.setBackgroundColor(accent);
            addView(strip, new LayoutParams(Ui.u(0.35f), ViewGroup.LayoutParams.MATCH_PARENT));

            FrameLayout box = new FrameLayout(c);
            box.setPadding(Ui.u(1.3f), Ui.u(0.9f), Ui.u(1.3f), Ui.u(0.9f));
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(VERTICAL);
            title = Ui.text(c, "", 1.2f, Color.argb(200, 255, 255, 255));
            big = Ui.text(c, "", 3.0f, Color.WHITE);
            big.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            sub = Ui.text(c, "", 1.25f, Color.WHITE);
            detail = Ui.text(c, "", 1.05f, Color.argb(170, 255, 255, 255));
            Ui.oneLine(title);
            Ui.oneLine(big);
            Ui.oneLine(sub);
            Ui.oneLine(detail);
            title.setMaxWidth(Ui.u(17f));
            col.addView(title);
            col.addView(big);
            col.addView(sub);
            col.addView(detail);
            box.addView(col, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            badge = Ui.text(c, "", 1.0f, Color.BLACK);
            badge.setPadding(Ui.u(0.8f), Ui.u(0.2f), Ui.u(0.8f), Ui.u(0.2f));
            badge.setBackground(Ui.solid(accent, 3f));
            badge.setVisibility(GONE);
            box.addView(badge, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END));

            bar = new Ui.Bar(c);
            bar.setVisibility(GONE);
            box.addView(bar, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.u(0.3f), Gravity.BOTTOM));
            addView(box, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }

        void bind(Card k) {
            title.setText(k.offline ? k.title + " \u00B7 \u2022" : k.title);
            big.setText(k.big);
            sub.setText(k.sub);
            detail.setText(k.detail);
            badge.setText(k.badge);
            badge.setVisibility(k.badge.isEmpty() ? GONE : VISIBLE);
            if (k.progress >= 0f) {
                bar.setVisibility(VISIBLE);
                bar.set(k.progress, accent);
            } else {
                bar.setVisibility(GONE);
            }
        }
    }

    // ---- apps ---------------------------------------------------------------------------

    private void loadApps() {
        if (loadingApps) return;
        loadingApps = true;
        packagesChanged = false;
        final long rev = Prefs.appsRev(this);
        new Thread(() -> {
            final List<Apps.Item> items = Apps.load(HomeActivity.this, true);
            runOnUiThread(() -> {
                loadingApps = false;
                shownAppsRev = rev;
                populate(items);
            });
        }).start();
    }

    private void populate(List<Apps.Item> all) {
        int cols = Math.max(3, Math.min(8, Prefs.cols(this)));
        Set<String> hidden = Prefs.hidden(this);
        List<Apps.Item> visible = new ArrayList<>();
        for (Apps.Item it : all) if (!hidden.contains(it.pkg)) visible.add(it);
        List<Apps.Item> dock = Apps.pickDock(this, visible, cols);
        List<Apps.Item> rest = new ArrayList<>(visible);
        rest.removeAll(dock);

        float gap = 1.1f;
        float tileW = (90f - gap * (cols - 1)) / cols;
        int w = Ui.u(tileW), h = Math.round(w * 9f / 16f), g = Ui.u(gap);

        boolean hadFocus = dockRow.hasFocus() || gridBox.hasFocus();
        dockRow.removeAllViews();
        gridBox.removeAllViews();
        for (Apps.Item it : dock) addTile(dockRow, it, w, h, g);
        for (int i = 0; i < rest.size(); i += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            for (int j = i; j < Math.min(rest.size(), i + cols); j++) addTile(row, rest.get(j), w, h, g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = g;
            gridBox.addView(row, lp);
        }
        if (!hadFocus && dockRow.getChildCount() > 0 && getCurrentFocus() == null) {
            dockRow.getChildAt(0).requestFocus();
        }
    }

    private void addTile(LinearLayout row, Apps.Item it, int w, int h, int gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMarginEnd(gap);
        row.addView(appTile(it), lp);
    }

    private View appTile(final Apps.Item it) {
        FrameLayout f = new FrameLayout(this);
        f.setFocusable(true);
        f.setClickable(true);
        f.setBackground(Ui.solid(Color.rgb(38, 44, 58), 1.1f));
        f.setForeground(Ui.focusRing(1.1f));
        f.setClipToOutline(true);
        f.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.u(1.1f));
            }
        });
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(it.image);
        iv.setScaleType(it.banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        int pad = it.banner ? 0 : Ui.u(1.2f);
        iv.setPadding(pad, pad, pad, it.banner ? 0 : Ui.u(2.4f));
        f.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (!it.banner) {
            TextView label = Ui.text(this, it.label, 1.0f, Color.WHITE);
            Ui.oneLine(label);
            label.setGravity(Gravity.CENTER);
            label.setPadding(Ui.u(0.3f), 0, Ui.u(0.3f), Ui.u(0.5f));
            f.addView(label, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        }
        f.setOnFocusChangeListener((v, focused) -> {
            Ui.focusAnim(v, focused, reduce);
            if (focused) nameView.setText(it.label);
        });
        f.setOnClickListener(v -> {
            try {
                startActivity(it.intent);
            } catch (Exception e) {
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
            }
        });
        return f;
    }
}
