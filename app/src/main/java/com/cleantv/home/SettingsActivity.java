package com.cleantv.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Settings in the preview's layout: categories on one side, the options of the chosen category on
 * the other. Moving over a category shows its options; the arrow toward the options enters them.
 */
public final class SettingsActivity extends Activity {
    private static final int THEMES = 0, ADDONS = 1, LAYOUT = 2, LIBRARY = 3, SAFETY = 4, POWER = 5;
    private static final int[] CAT_NAMES = {R.string.cat_themes, R.string.cat_addons, R.string.cat_layout,
            R.string.cat_library, R.string.cat_safety, R.string.cat_power};
    private static final String[] LANG_VALUES = {"system", "en", "ar"};
    private static final int[] REDUCE_VALUES = {-1, 1, 0};
    private static final int[] COLS_VALUES = {0, 4, 5, 6, 7};
    private static final int[] STANDBY_VALUES = {0, 1, 4, 6};
    private static final int[] METHOD_IDS = {5, 3, 4, 1, 2};
    private static final String[] METHOD_NAMES = {
            "Egyptian General Authority of Survey", "Muslim World League", "Umm al-Qura, Makkah",
            "University of Islamic Sciences, Karachi", "Islamic Society of North America"};
    private static final int[] CARD_DESC = {R.string.desc_weather, R.string.desc_prayer, R.string.desc_matches,
            R.string.desc_clock, R.string.desc_storage, R.string.desc_network, R.string.desc_standby, R.string.desc_remote};

    private static int lastCat;

    private final List<Ui.Row> catViews = new ArrayList<>();
    private final List<Ui.Row> rows = new ArrayList<>();
    private LinearLayout rightBox;
    private TextView rightTitle;
    private ImageView wpView;
    private int cat, accent;
    private boolean reduce, rtl;
    private List<Apps.Item> libApps;

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
        rtl = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        if (reduce) getWindow().setWindowAnimations(0);
        cat = lastCat;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Themes.current(this).bg);
        wpView = new ImageView(this);
        wpView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wpView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View shade = new View(this);
        shade.setBackgroundColor(Color.argb(210, 8, 10, 16));
        root.addView(shade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setPadding(Ui.u(5f), Ui.u(3.6f), Ui.u(5f), Ui.u(3f));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(Ui.text(this, getString(R.string.set_title), 2.6f, Color.WHITE));
        for (int i = 0; i < CAT_NAMES.length; i++) {
            final int idx = i;
            Ui.Row r = new Ui.Row(this, getString(CAT_NAMES[i]), null, accent);
            r.setId(View.generateViewId());
            r.setOnFocusChangeListener((v, focused) -> {
                if (focused) select(idx);
            });
            r.setOnClickListener(v -> {
                if (!rows.isEmpty()) rows.get(0).requestFocus();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.u(0.4f);
            if (i == 0) lp.topMargin = Ui.u(1.6f);
            left.addView(r, lp);
            catViews.add(r);
        }
        catViews.get(0).setNextFocusUpId(catViews.get(0).getId());
        catViews.get(catViews.size() - 1).setNextFocusDownId(catViews.get(catViews.size() - 1).getId());
        content.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 24f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        rightTitle = Ui.text(this, "", 2.6f, Color.WHITE);
        right.addView(rightTitle);
        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        rightBox = new LinearLayout(this);
        rightBox.setOrientation(LinearLayout.VERTICAL);
        sc.addView(rightBox);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sl.topMargin = Ui.u(1.6f);
        right.addView(sc, sl);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 76f);
        rl.setMarginStart(Ui.u(3f));
        content.addView(right, rl);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        select(cat);
        catViews.get(cat).requestFocus();
        loadWallpaper();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderPane();   // also refreshes the accessibility status after coming back from system settings
    }

    private void loadWallpaper() {
        final Wallpapers.Pick p = Wallpapers.current(this, Themes.current(this));
        if (p == null) return;
        new Thread(() -> {
            final Bitmap bm = Wallpapers.decode(this, p.res);
            runOnUiThread(() -> {
                if (bm != null) wpView.setImageBitmap(bm);
            });
        }).start();
    }

    // ---- panes ------------------------------------------------------------------------------

    private void select(int i) {
        cat = i;
        lastCat = i;
        for (int k = 0; k < catViews.size(); k++) catViews.get(k).setSelectedLook(k == i);
        renderPane();
    }

    private void renderPane() {
        int focusIdx = -1;
        for (int k = 0; k < rows.size(); k++) if (rows.get(k).hasFocus()) focusIdx = k;
        rightTitle.setText(CAT_NAMES[cat]);
        rightBox.removeAllViews();
        rows.clear();
        switch (cat) {
            case THEMES: themesPane(); break;
            case ADDONS: addonsPane(); break;
            case LAYOUT: layoutPane(); break;
            case LIBRARY: libraryPane(); break;
            case SAFETY: safetyPane(); break;
            default: powerPane(); break;
        }
        wireFocus();
        if (focusIdx >= 0 && !rows.isEmpty()) rows.get(Math.min(focusIdx, rows.size() - 1)).requestFocus();
    }

    /** Arrow toward the options enters them, the opposite arrow comes back; no escaping up or down. */
    private void wireFocus() {
        Ui.Row catRow = catViews.get(cat);
        if (rows.isEmpty()) return;
        Ui.Row first = rows.get(0), last = rows.get(rows.size() - 1);
        if (rtl) catRow.setNextFocusLeftId(first.getId()); else catRow.setNextFocusRightId(first.getId());
        for (Ui.Row r : rows) {
            if (rtl) {
                r.setNextFocusRightId(catRow.getId());
                r.setNextFocusLeftId(r.getId());
            } else {
                r.setNextFocusLeftId(catRow.getId());
                r.setNextFocusRightId(r.getId());
            }
        }
        first.setNextFocusUpId(first.getId());
        last.setNextFocusDownId(last.getId());
    }

    private Ui.Row add(String title, String sub, String trail, boolean filled, final Runnable action) {
        Ui.Row r = new Ui.Row(this, title, sub, accent);
        r.setId(View.generateViewId());
        r.setTrail(trail, filled);
        r.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.u(0.4f);
        rightBox.addView(r, lp);
        rows.add(r);
        return r;
    }

    private Ui.Row toggle(int title, int sub, boolean on, final Runnable flip) {
        return add(getString(title), sub == 0 ? null : getString(sub), getString(on ? R.string.on : R.string.off), on, () -> {
            flip.run();
            renderPane();
        });
    }

    private String onOff(boolean on) { return getString(on ? R.string.on : R.string.off); }

    // ---- Themes -------------------------------------------------------------------------------

    private void themesPane() {
        for (int i = 0; i < Themes.ALL.length; i++) {
            final int idx = i;
            boolean active = i == Prefs.theme(this);
            add(Themes.ALL[i].name, null, active ? "\u2713" : "", true, () -> {
                Prefs.theme(this, idx);
                recreate();
            });
        }
    }

    // ---- Add-ons (the cards) --------------------------------------------------------------------

    private void addonsPane() {
        Set<String> on = Prefs.cards(this);
        for (int i = 0; i < Prefs.CARD_IDS.length; i++) {
            final String id = Prefs.CARD_IDS[i];
            final boolean enabled = on.contains(id);
            add(getString(CardRepo.byId(id).titleRes()), getString(CARD_DESC[i]), onOff(enabled), enabled, () -> {
                Set<String> v = Prefs.cards(this);
                if (!v.remove(id)) v.add(id);
                Prefs.cards(this, v);
                renderPane();
            });
        }
        add(getString(R.string.set_city), getString(R.string.set_city_sub), Prefs.city(this), false, this::editCity);
        add(getString(R.string.set_method), getString(R.string.set_method_sub), methodName(), false, this::chooseMethod);
    }

    private String methodName() {
        int id = Prefs.method(this);
        for (int i = 0; i < METHOD_IDS.length; i++) if (METHOD_IDS[i] == id) return METHOD_NAMES[i];
        return String.valueOf(id);
    }

    private void chooseMethod() {
        int cur = 0;
        for (int i = 0; i < METHOD_IDS.length; i++) if (METHOD_IDS[i] == Prefs.method(this)) cur = i;
        Dialogs.choose(this, getString(R.string.set_method), METHOD_NAMES, cur, i -> {
            Prefs.method(this, METHOD_IDS[i]);
            renderPane();
        });
    }

    private void editCity() {
        Dialogs.input(this, getString(R.string.set_city), getString(R.string.city_prompt), Prefs.city(this), this::searchCity);
    }

    private void searchCity(final String query) {
        if (query.isEmpty()) return;
        Toast.makeText(this, R.string.city_searching, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String lang = LocaleHelper.isArabic(this) ? "ar" : "en";
                String body = Http.get("https://geocoding-api.open-meteo.com/v1/search?name="
                        + URLEncoder.encode(query, "UTF-8") + "&count=6&language=" + lang + "&format=json");
                JSONArray results = new JSONObject(body).optJSONArray("results");
                final List<String[]> hits = new ArrayList<>();
                if (results != null) {
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject o = results.getJSONObject(i);
                        String label = o.optString("name");
                        String admin = o.optString("admin1", "");
                        String country = o.optString("country", "");
                        if (!admin.isEmpty()) label += ", " + admin;
                        if (!country.isEmpty()) label += ", " + country;
                        hits.add(new String[]{o.optString("name"), String.valueOf(o.getDouble("latitude")),
                                String.valueOf(o.getDouble("longitude")), label});
                    }
                }
                runOnUiThread(() -> showCityResults(hits));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.city_error, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showCityResults(final List<String[]> hits) {
        if (hits.isEmpty()) {
            Toast.makeText(this, R.string.city_none, Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[hits.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = hits.get(i)[3];
        Dialogs.menu(this, getString(R.string.set_city), labels, which -> {
            String[] h = hits.get(which);
            Prefs.place(this, h[0], h[1], h[2]);
            renderPane();
        });
    }

    // ---- Layout ---------------------------------------------------------------------------------

    private void layoutPane() {
        add(getString(R.string.set_language), null, langName(), false, this::chooseLanguage);
        toggle(R.string.lay_cards, 0, Prefs.showCards(this), () -> Prefs.showCards(this, !Prefs.showCards(this)));
        int cols = Prefs.cols(this);
        add(getString(R.string.set_cols), null, cols == 0 ? getString(R.string.cols_theme) : String.valueOf(cols), false, this::chooseCols);
        toggle(R.string.lay_wallpaper, 0, Prefs.wallpaper(this), () -> Prefs.wallpaper(this, !Prefs.wallpaper(this)));
        add(getString(R.string.set_reduce), getString(R.string.set_reduce_sub), reduceName(), false, this::chooseReduce);
        toggle(R.string.lay_tilemenu, 0, Prefs.tileMenu(this), () -> Prefs.tileMenu(this, !Prefs.tileMenu(this)));
    }

    private String langName() {
        String l = Prefs.lang(this);
        return l.equals("en") ? getString(R.string.lang_en) : l.equals("ar") ? getString(R.string.lang_ar)
                : getString(R.string.lang_system);
    }

    private String reduceName() {
        int m = Prefs.reduceMode(this);
        return m == 1 ? getString(R.string.on) : m == 0 ? getString(R.string.off) : getString(R.string.auto);
    }

    private int indexOf(int[] values, int v) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return 0;
    }

    private void chooseLanguage() {
        String[] items = {getString(R.string.lang_system), getString(R.string.lang_en), getString(R.string.lang_ar)};
        int cur = 0;
        for (int i = 0; i < LANG_VALUES.length; i++) if (LANG_VALUES[i].equals(Prefs.lang(this))) cur = i;
        Dialogs.choose(this, getString(R.string.set_language), items, cur, i -> {
            Prefs.lang(this, LANG_VALUES[i]);
            recreate();
        });
    }

    private void chooseReduce() {
        String[] items = {getString(R.string.auto), getString(R.string.on), getString(R.string.off)};
        Dialogs.choose(this, getString(R.string.set_reduce), items, indexOf(REDUCE_VALUES, Prefs.reduceMode(this)), i -> {
            Prefs.reduceMode(this, REDUCE_VALUES[i]);
            recreate();
        });
    }

    private void chooseCols() {
        String[] items = new String[COLS_VALUES.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = COLS_VALUES[i] == 0 ? getString(R.string.cols_theme) : String.valueOf(COLS_VALUES[i]);
        }
        Dialogs.choose(this, getString(R.string.set_cols), items, indexOf(COLS_VALUES, Prefs.cols(this)), i -> {
            Prefs.cols(this, COLS_VALUES[i]);
            renderPane();
        });
    }

    // ---- App library ------------------------------------------------------------------------------

    private void libraryPane() {
        add(getString(R.string.lib_reset), getString(R.string.lib_reset_sub), "", false, () -> {
            Prefs.order(this, new ArrayList<String>());
            Prefs.bumpApps(this);
            Toast.makeText(this, R.string.lib_reset_done, Toast.LENGTH_SHORT).show();
        });
        if (libApps == null) {
            TextView t = Ui.text(this, getString(R.string.loading), 1.5f, Color.argb(200, 255, 255, 255));
            t.setPadding(Ui.u(1.4f), Ui.u(1f), 0, 0);
            rightBox.addView(t);
            new Thread(() -> {
                final List<Apps.Item> list = Apps.load(this, false);
                runOnUiThread(() -> {
                    libApps = list;
                    if (cat == LIBRARY) renderPane();
                });
            }).start();
            return;
        }
        Set<String> hidden = Prefs.hidden(this);
        for (final Apps.Item it : libApps) {
            final boolean shown = !hidden.contains(it.pkg);
            add(it.label, getString(R.string.lib_show), onOff(shown), shown, () -> {
                Set<String> v = Prefs.hidden(this);
                if (!v.remove(it.pkg)) v.add(it.pkg);
                Prefs.hidden(this, v);
                Prefs.bumpApps(this);
                renderPane();
            });
        }
    }

    // ---- Safety -----------------------------------------------------------------------------------

    private void safetyPane() {
        boolean open = false;
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", 5555), 250);
            open = true;
        } catch (Exception ignored) {
            // closed
        }
        add(getString(R.string.safe_adb), getString(R.string.safe_adb_sub), onOff(open), open, () -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_LONG).show();
            }
        });
        add(getString(R.string.set_sources), null, "", false,
                () -> Dialogs.message(this, getString(R.string.set_sources), getString(R.string.credits_all)));
        add(getString(R.string.safe_reset), getString(R.string.safe_reset_sub), "", false, () -> {
            Dialogs.choose(this, getString(R.string.safe_reset), new String[]{getString(R.string.no), getString(R.string.yes)}, 0, i -> {
                if (i == 1) {
                    Prefs.sp(this).edit().clear().apply();
                    recreate();
                }
            });
        });
    }

    // ---- Power --------------------------------------------------------------------------------------

    private void powerPane() {
        add(getString(R.string.set_standby), getString(R.string.set_standby_sub), standbyName(), false, this::chooseStandby);
        toggle(R.string.set_keep_audio, R.string.set_keep_audio_sub, Prefs.keepAudio(this),
                () -> Prefs.keepAudio(this, !Prefs.keepAudio(this)));
        add(getString(R.string.set_access), getString(R.string.set_access_sub), onOff(accessibilityOn()), accessibilityOn(), this::openAccessibility);
    }

    private String standbyName() {
        int h = Prefs.standby(this);
        return h == 0 ? getString(R.string.off) : h == 1 ? getString(R.string.hours_1)
                : h == 4 ? getString(R.string.hours_4) : getString(R.string.hours_6);
    }

    private void chooseStandby() {
        String[] items = {getString(R.string.off), getString(R.string.hours_1), getString(R.string.hours_4), getString(R.string.hours_6)};
        Dialogs.choose(this, getString(R.string.set_standby), items, indexOf(STANDBY_VALUES, Prefs.standby(this)), i -> {
            Prefs.standby(this, STANDBY_VALUES[i]);
            if (STANDBY_VALUES[i] > 0 && !accessibilityOn()) {
                Toast.makeText(this, R.string.set_access_hint, Toast.LENGTH_LONG).show();
            }
            renderPane();
        });
    }

    private boolean accessibilityOn() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(this, IdleService.class);
        String s = enabled.toLowerCase();
        return s.contains(me.flattenToString().toLowerCase()) || s.contains(me.flattenToShortString().toLowerCase());
    }

    private void openAccessibility() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_LONG).show();
        }
    }
}
