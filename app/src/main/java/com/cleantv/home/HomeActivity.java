package com.cleantv.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The home screen, built to match the preview: wallpaper, greeting and clock, glass buttons with
 * icons, a row of live cards, then the dock and the app grid. D-pad only. Hold OK on an app for
 * its menu (move, clear data, uninstall, hide).
 */
public final class HomeActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, CardTile> tiles = new HashMap<>();
    private final Map<String, View> tileByPkg = new HashMap<>();
    private final CardRepo.Listener cardListener = k -> {
        CardTile t = tiles.get(k.id);
        if (t != null) t.bind(k);
    };

    private Themes.T theme;
    private ImageView wpView;
    private View dimView, shadeView, glowView;
    private GradientDrawable glow;
    private LinearLayout cardsRow, dockRow, gridBox, wifiPill;
    private HorizontalScrollView cardsScroll;
    private TextView greetView, clockView, ampmView, dateView, nameView, capView;
    private String signature = "";
    private long shownAppsRev = -1L;
    private int shownWp;
    private boolean reduce, resumed, loadingApps, packagesChanged;

    private List<Apps.Item> visibleOrdered = new ArrayList<>();
    private List<Apps.Item> allItems = new ArrayList<>();
    private String movingPkg;
    private int dockCount = 4, cols = 4;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateClock();
            applyWallpaper();
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
        theme = Themes.current(this);
        signature = signature();
        if (reduce) getWindow().setWindowAnimations(0);
        buildUi();
        buildCards();
    }

    /** Everything that changes the look of Home; when it changes the screen is rebuilt. */
    private String signature() {
        return Prefs.lang(this) + "|" + Prefs.theme(this) + "|" + Prefs.cols(this) + "|" + Prefs.wallpaper(this)
                + "|" + Prefs.showCards(this) + "|" + Prefs.cards(this) + "|" + Prefs.reduce(this);
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
        if (!signature.equals(signature())) {
            recreate();
            return;
        }
        updateClock();
        applyWallpaper();
        if (packagesChanged || shownAppsRev != Prefs.appsRev(this) || visibleOrdered.isEmpty()) loadApps();
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
        if (dockRow != null && dockRow.getChildCount() > 0) dockRow.getChildAt(0).requestFocus();
    }

    @Override
    public void onBackPressed() {
        // a launcher has nowhere to go back to
    }

    /** While an app is being moved, the arrows move it and OK or Back drops it. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (movingPkg != null) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                boolean rtl = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                switch (e.getKeyCode()) {
                    case KeyEvent.KEYCODE_DPAD_LEFT: moveApp(rtl ? 1 : -1, 0); break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT: moveApp(rtl ? -1 : 1, 0); break;
                    case KeyEvent.KEYCODE_DPAD_UP: moveApp(0, -1); break;
                    case KeyEvent.KEYCODE_DPAD_DOWN: moveApp(0, 1); break;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_BACK:
                        movingPkg = null;
                        Toast.makeText(this, R.string.move_done, Toast.LENGTH_SHORT).show();
                        break;
                    default: break;
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- building the screen --------------------------------------------------------------

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(theme.bg);

        wpView = new ImageView(this);
        wpView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wpView.setVisibility(View.GONE);
        root.addView(wpView, matchParent());

        dimView = new View(this);
        dimView.setBackgroundColor(Color.argb(theme.dim * 255 / 100, 0, 0, 0));
        root.addView(dimView, matchParent());

        shadeView = new View(this);
        shadeView.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Color.argb(140, 0, 0, 0), 0}));
        shadeView.setVisibility(View.GONE);
        root.addView(shadeView, matchParent());

        glowView = new View(this);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        int w = getResources().getDisplayMetrics().widthPixels;
        glow.setGradientRadius(w * (theme.horizon ? 0.62f : 0.55f));
        glow.setGradientCenter(theme.horizon ? 0.5f : 0.34f, theme.horizon ? 0f : 0.52f);
        glow.setColors(new int[]{theme.accent, 0});
        glowView.setBackground(glow);
        glowView.setVisibility(theme.ambient && !reduce ? View.VISIBLE : View.GONE);
        root.addView(glowView, matchParent());

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setPadding(Ui.u(5f), Ui.u(3.6f), Ui.u(5f), Ui.u(4f));

        // ---- header: greeting + clock, glass buttons, wallpaper caption
        FrameLayout header = new FrameLayout(this);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        boolean arabic = LocaleHelper.isArabic(this);
        greetView = Ui.text(this, "", 1.25f, Color.argb(205, 255, 255, 255));
        greetView.setAllCaps(true);
        if (!arabic) greetView.setLetterSpacing(0.2f);
        clockView = Ui.text(this, "", 4.6f, Color.WHITE);
        clockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        ampmView = Ui.text(this, "", 1.25f, Color.argb(205, 255, 255, 255));
        ampmView.setAllCaps(true);
        dateView = Ui.text(this, "", 1.25f, Color.argb(215, 255, 255, 255));
        dateView.setAllCaps(true);
        if (!arabic) dateView.setLetterSpacing(0.18f);
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeRow.addView(clockView);
        timeRow.addView(ampmView, marginStart(Ui.u(1.2f)));
        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(80, 255, 255, 255));
        LinearLayout.LayoutParams dv = new LinearLayout.LayoutParams(Math.max(1, Ui.u(0.1f)), Ui.u(3f));
        dv.setMarginStart(Ui.u(1.6f));
        timeRow.addView(divider, dv);
        timeRow.addView(dateView, marginStart(Ui.u(1.6f)));
        left.addView(greetView);
        left.addView(timeRow);
        header.addView(left, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.TOP));

        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout inputs = Ui.iconPill(this, R.drawable.ic_input, getString(R.string.pill_inputs), theme.icons, reduce, this::showInputs);
        wifiPill = Ui.iconPill(this, R.drawable.ic_wifi, getString(R.string.net_wifi), theme.icons, reduce,
                () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        LinearLayout settings = Ui.iconPill(this, R.drawable.ic_gear, getString(R.string.pill_settings), theme.icons, reduce,
                () -> open(new Intent(this, SettingsActivity.class)));
        pills.addView(inputs);
        pills.addView(wifiPill, marginStart(Ui.u(0.9f)));
        pills.addView(settings, marginStart(Ui.u(0.9f)));
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
        pl.topMargin = Ui.u(0.4f);
        header.addView(pills, pl);

        capView = Ui.text(this, "", 1.05f, Color.argb(220, 255, 255, 255));
        capView.setShadowLayer(3f, 0f, 1f, Color.BLACK);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
        cl.topMargin = Ui.u(5.2f);
        header.addView(capView, cl);
        content.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.u(11.4f)));

        // ---- live cards: always the same strip under the clock
        cardsScroll = new HorizontalScrollView(this);
        cardsScroll.setHorizontalScrollBarEnabled(false);
        cardsScroll.setClipToPadding(false);
        cardsScroll.setClipChildren(false);
        cardsRow = new LinearLayout(this);
        cardsRow.setOrientation(LinearLayout.HORIZONTAL);
        cardsRow.setClipChildren(false);
        cardsRow.setPadding(Ui.u(0.6f), Ui.u(0.6f), Ui.u(0.6f), Ui.u(0.6f));
        cardsScroll.addView(cardsRow);
        content.addView(cardsScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.u(11.8f)));

        // ---- name of the focused app or card
        nameView = Ui.text(this, "", theme.cine ? 3.1f : 2.5f, Color.WHITE);
        nameView.setShadowLayer(8f, 0f, 1f, Color.argb(130, 0, 0, 0));
        nameView.setGravity(Gravity.CENTER_VERTICAL);
        Ui.oneLine(nameView);
        nameView.setVisibility(theme.title ? View.VISIBLE : View.INVISIBLE);
        content.addView(nameView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.u(4.2f)));

        // ---- dock and grid
        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setClipChildren(false);
        dockRow.setPadding(Ui.u(0.9f), Ui.u(0.9f), Ui.u(0.9f), Ui.u(0.9f));
        if (theme.band) {
            int alpha = Math.round(theme.dop / 100f * 0.62f * 255f);
            dockRow.setBackground(theme.glass
                    ? Ui.shape(Color.argb(alpha, 14, 16, 24), 2f, Color.argb(56, 255, 255, 255), 0.1f)
                    : Ui.solid(Color.argb(alpha, 14, 16, 24), 2f));
        }
        LinearLayout.LayoutParams dockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dockLp.setMarginStart(-Ui.u(0.9f));
        dockLp.setMarginEnd(-Ui.u(0.9f));
        dockLp.bottomMargin = Ui.u(1.4f);
        content.addView(dockRow, dockLp);

        gridBox = new LinearLayout(this);
        gridBox.setOrientation(LinearLayout.VERTICAL);
        gridBox.setClipChildren(false);
        gridBox.setAlpha(theme.fade ? 0.8f : 1f);
        content.addView(gridBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll.addView(content);
        root.addView(scroll, matchParent());
        setContentView(root);
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout.LayoutParams marginStart(int px) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(px);
        return lp;
    }

    private void updateClock() {
        boolean h24 = DateFormat.is24HourFormat(this);
        Locale loc = LocaleHelper.numLocale(this);
        LocalTime now = LocalTime.now();
        clockView.setText(now.format(DateTimeFormatter.ofPattern(h24 ? "HH:mm" : "h:mm", loc)));
        ampmView.setText(h24 ? "" : now.format(DateTimeFormatter.ofPattern("a", loc)));
        dateView.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", loc)));
        int hour = now.getHour();
        greetView.setText(hour < 12 ? R.string.greet_morning : hour < 18 ? R.string.greet_afternoon : R.string.greet_evening);
        updateNetPill();
    }

    /** The Wi-Fi button shows the real connection: Wi-Fi, Ethernet or offline. */
    private void updateNetPill() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
        boolean on = ni != null && ni.isConnected();
        boolean eth = on && ni.getType() == ConnectivityManager.TYPE_ETHERNET;
        ((ImageView) wifiPill.getChildAt(0)).setImageResource(eth ? R.drawable.ic_lan : R.drawable.ic_wifi);
        wifiPill.getChildAt(0).setAlpha(on ? 1f : 0.45f);
        if (wifiPill.getChildCount() > 1) {
            ((TextView) wifiPill.getChildAt(1)).setText(!on ? R.string.net_off : eth ? R.string.net_eth : R.string.net_wifi);
        }
    }

    private void applyWallpaper() {
        final Wallpapers.Pick p = Wallpapers.current(this, theme);
        capView.setText(p != null && p.caption != null ? p.caption : "");
        final int res = p == null ? 0 : p.res;
        if (res == shownWp) return;
        shownWp = res;
        shadeView.setVisibility(p == null ? View.GONE : View.VISIBLE);
        if (p == null) {
            wpView.setImageDrawable(null);
            wpView.setVisibility(View.GONE);
            return;
        }
        new Thread(() -> {
            final Bitmap bm = Wallpapers.decode(HomeActivity.this, res);
            runOnUiThread(() -> {
                if (res == shownWp && bm != null) {
                    wpView.setImageBitmap(bm);
                    wpView.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private void setGlow(int color) {
        if (glow == null || glowView.getVisibility() != View.VISIBLE) return;
        glow.setColors(new int[]{color, 0});
        glowView.invalidate();
        glowView.setAlpha(wpView.getVisibility() == View.VISIBLE ? 0.4f : 0.55f);
    }

    private void open(Intent i) {
        try {
            startActivity(i);
            if (reduce) overridePendingTransition(0, 0);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- inputs (HDMI and other pass-through sources) ---------------------------------------

    private void showInputs() {
        try {
            TvInputManager m = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            final List<TvInputInfo> inputs = new ArrayList<>();
            if (m != null) {
                for (TvInputInfo i : m.getTvInputList()) if (i.isPassthroughInput()) inputs.add(i);
            }
            if (inputs.isEmpty()) {
                Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_LONG).show();
                return;
            }
            String[] labels = new String[inputs.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = String.valueOf(inputs.get(i).loadLabel(this));
            Dialogs.menu(this, getString(R.string.pill_inputs), labels, which ->
                    open(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputs.get(which).getId()))));
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_LONG).show();
        }
    }

    // ---- cards ------------------------------------------------------------------------------

    private void buildCards() {
        cardsRow.removeAllViews();
        tiles.clear();
        Set<String> on = Prefs.cards(this);
        if (Prefs.showCards(this)) {
            for (final String id : Prefs.CARD_IDS) {
                if (!on.contains(id)) continue;
                final Source s = CardRepo.byId(id);
                CardTile t = new CardTile(this, s.accent());
                t.title.setText(s.titleRes());
                t.big.setText("\u2026");
                t.setOnFocusChangeListener((v, focused) -> {
                    Ui.focusAnim(v, focused, reduce, 1.03f);
                    if (focused) {
                        nameView.setText(s.titleRes());
                        setGlow(s.accent());
                    }
                });
                t.setOnClickListener(v -> open(id.equals("matches") ? new Intent(this, MatchesActivity.class)
                        : new Intent(this, CardDetailActivity.class).putExtra("id", id)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.u(27.5f), Ui.u(10.6f));
                lp.setMarginEnd(Ui.u(1.5f));
                cardsRow.addView(t, lp);
                tiles.put(id, t);
            }
        }
        // keep the strip's space even with no cards, so the dock never jumps
        cardsScroll.setVisibility(tiles.isEmpty() ? View.INVISIBLE : View.VISIBLE);
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
            setBackground(Ui.focusBg(Color.argb(133, 12, 14, 22), 1.4f));

            View strip = new View(c);
            strip.setBackgroundColor(accent);
            addView(strip, new LayoutParams(Ui.u(0.35f), ViewGroup.LayoutParams.MATCH_PARENT));

            FrameLayout box = new FrameLayout(c);
            box.setPadding(Ui.u(1.3f), Ui.u(0.9f), Ui.u(1.3f), Ui.u(0.9f));
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(VERTICAL);
            title = Ui.text(c, "", 1.2f, Color.argb(190, 255, 255, 255));
            big = Ui.text(c, "", 3.0f, Color.WHITE);
            big.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            sub = Ui.text(c, "", 1.25f, Color.WHITE);
            detail = Ui.text(c, "", 1.05f, Color.argb(165, 255, 255, 255));
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
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(2, Ui.u(0.3f)), Gravity.BOTTOM);
            bl.bottomMargin = Ui.u(0.1f);
            box.addView(bar, bl);
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

    // ---- apps ---------------------------------------------------------------------------------

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
                allItems = items;
                populate();
            });
        }).start();
    }

    private void populate() {
        cols = Math.max(3, Math.min(8, Themes.cols(this, theme)));
        dockCount = Math.min(theme.dock, cols);
        Set<String> hidden = Prefs.hidden(this);
        List<Apps.Item> visible = new ArrayList<>();
        for (Apps.Item it : allItems) if (!hidden.contains(it.pkg)) visible.add(it);
        visibleOrdered = Apps.ordered(this, visible);

        float gap = 1.1f;
        float tileW = (90f - gap * (cols - 1)) / cols;
        int w = Ui.u(tileW), h = Math.round(w * 9f / 16f), g = Ui.u(gap);

        View focused = getCurrentFocus();
        boolean hadFocus = focused != null && (dockRow.hasFocus() || gridBox.hasFocus());
        dockRow.removeAllViews();
        gridBox.removeAllViews();
        tileByPkg.clear();
        int n = visibleOrdered.size();
        for (int i = 0; i < Math.min(dockCount, n); i++) addTile(dockRow, visibleOrdered.get(i), w, h, g);
        for (int i = dockCount; i < n; i += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            for (int j = i; j < Math.min(n, i + cols); j++) addTile(row, visibleOrdered.get(j), w, h, g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = g;
            gridBox.addView(row, lp);
        }
        if (movingPkg != null && tileByPkg.get(movingPkg) != null) {
            tileByPkg.get(movingPkg).requestFocus();
        } else if (!hadFocus && dockRow.getChildCount() > 0 && getCurrentFocus() == null) {
            dockRow.getChildAt(0).requestFocus();
        }
    }

    private void addTile(LinearLayout row, Apps.Item it, int w, int h, int gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMarginEnd(gap);
        View t = appTile(it);
        tileByPkg.put(it.pkg, t);
        row.addView(t, lp);
    }

    private View appTile(final Apps.Item it) {
        FrameLayout f = new FrameLayout(this);
        f.setFocusable(true);
        f.setClickable(true);
        f.setLongClickable(true);
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
            if (focused) {
                nameView.setText(it.label);
                setGlow(it.color);
            }
            if (Build.VERSION.SDK_INT >= 28 && theme.appGlow) {
                v.setOutlineSpotShadowColor(focused ? it.color : Color.BLACK);
            }
        });
        f.setOnClickListener(v -> {
            try {
                startActivity(it.intent);
            } catch (Exception e) {
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
            }
        });
        f.setOnLongClickListener(v -> {
            if (!Prefs.tileMenu(this)) return false;
            showTileMenu(it);
            return true;
        });
        return f;
    }

    // ---- hold OK on an app: the tile menu -----------------------------------------------------

    private void showTileMenu(final Apps.Item it) {
        String[] items = {getString(R.string.tile_move), getString(R.string.tile_clear),
                getString(R.string.tile_uninstall), getString(R.string.tile_hide)};
        Dialogs.menu(this, it.label, items, which -> {
            switch (which) {
                case 0:
                    movingPkg = it.pkg;
                    Toast.makeText(this, R.string.move_hint, Toast.LENGTH_LONG).show();
                    break;
                case 1:
                    Toast.makeText(this, R.string.clear_hint, Toast.LENGTH_LONG).show();
                    open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + it.pkg)));
                    break;
                case 2:
                    open(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + it.pkg)));
                    break;
                default:
                    Set<String> hidden = Prefs.hidden(this);
                    hidden.add(it.pkg);
                    Prefs.hidden(this, hidden);
                    populate();
                    break;
            }
        });
    }

    /** Moves the app being dragged one place (dx) or one row (dy) and keeps it focused. */
    private void moveApp(int dx, int dy) {
        int i = -1;
        for (int k = 0; k < visibleOrdered.size(); k++) if (visibleOrdered.get(k).pkg.equals(movingPkg)) i = k;
        if (i < 0) return;
        int n = visibleOrdered.size(), target = i + dx;
        if (dy > 0) target = i < dockCount ? dockCount + Math.min(i, cols - 1) : i + cols;
        if (dy < 0) {
            if (i >= dockCount) {
                int r = (i - dockCount) / cols, col = (i - dockCount) % cols;
                target = r == 0 ? Math.min(col, dockCount - 1) : i - cols;
            } else {
                target = i;
            }
        }
        target = Math.max(0, Math.min(n - 1, target));
        if (target == i) return;
        Apps.Item item = visibleOrdered.remove(i);
        visibleOrdered.add(target, item);
        List<String> order = new ArrayList<>();
        for (Apps.Item a : visibleOrdered) order.add(a.pkg);
        Prefs.order(this, order);
        populate();
    }
}
