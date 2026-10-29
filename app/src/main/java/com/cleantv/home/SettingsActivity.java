package com.cleantv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
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

/** All options, one scrolling list. OK on a row changes it or opens a choice. */
public final class SettingsActivity extends Activity {

    private interface Pick { void on(int index); }

    private static final String[] LANG_VALUES = {"system", "en", "ar"};
    private static final int[] REDUCE_VALUES = {-1, 1, 0};
    private static final int[] COLS_VALUES = {4, 5, 6, 7};
    private static final int[] STANDBY_VALUES = {0, 1, 4, 6};
    private static final int[] METHOD_IDS = {5, 3, 4, 1, 2};
    private static final String[] METHOD_NAMES = {
            "Egyptian General Authority of Survey", "Muslim World League", "Umm al-Qura, Makkah",
            "University of Islamic Sciences, Karachi", "Islamic Society of North America"};

    private LinearLayout box;
    private final List<android.view.View> rows = new ArrayList<>();
    private int focusAt;
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
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Themes.background(this));
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.u(8f), Ui.u(3.5f), Ui.u(8f), Ui.u(4f));
        scroll.addView(box);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();   // also refreshes the accessibility status after coming back from system settings
    }

    // ---- the list -----------------------------------------------------------------------

    private void build() {
        box.removeAllViews();
        rows.clear();
        box.addView(Ui.text(this, getString(R.string.set_title), 3f, Color.WHITE));

        header(R.string.sec_general);
        row(R.string.set_language, null, langName(), this::chooseLanguage);
        row(R.string.set_theme, null, getString(Themes.current(this).name), this::chooseTheme);
        row(R.string.set_reduce, getString(R.string.set_reduce_sub), reduceName(), this::chooseReduce);
        row(R.string.set_cols, null, String.valueOf(Prefs.cols(this)), this::chooseCols);

        header(R.string.sec_cards);
        row(R.string.set_cards, null, Prefs.cards(this).size() + " / " + Prefs.CARD_IDS.length, this::chooseCards);
        row(R.string.set_city, getString(R.string.set_city_sub), Prefs.city(this), this::editCity);
        row(R.string.set_method, null, methodName(), this::chooseMethod);

        header(R.string.sec_power);
        row(R.string.set_standby, getString(R.string.set_standby_sub), standbyName(), this::chooseStandby);
        row(R.string.set_keep_audio, getString(R.string.set_keep_audio_sub), onOff(Prefs.keepAudio(this)), () -> {
            Prefs.keepAudio(this, !Prefs.keepAudio(this));
            build();
        });
        row(R.string.set_access, getString(R.string.set_access_sub), onOff(accessibilityOn()), this::openAccessibility);

        header(R.string.sec_apps);
        row(R.string.set_dock, getString(R.string.set_dock_sub), "", this::chooseDock);
        row(R.string.set_hidden, getString(R.string.set_hidden_sub), "", this::chooseHidden);

        header(R.string.sec_about);
        row(R.string.set_sources, null, "", () -> new AlertDialog.Builder(this)
                .setTitle(R.string.set_sources).setMessage(R.string.credits_all)
                .setPositiveButton(android.R.string.ok, null).show());

        if (!rows.isEmpty()) rows.get(Math.min(focusAt, rows.size() - 1)).requestFocus();
    }

    private void header(int title) {
        TextView t = Ui.text(this, getString(title), 1.4f, Themes.current(this).accent);
        t.setPadding(0, Ui.u(2f), 0, Ui.u(0.4f));
        box.addView(t);
    }

    private void row(int title, String sub, String value, final Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.u(1.6f), Ui.u(1f), Ui.u(1.6f), Ui.u(1f));
        r.setFocusable(true);
        r.setClickable(true);
        r.setBackground(Ui.focusBg(Color.argb(45, 255, 255, 255), 1f));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(Ui.text(this, getString(title), 1.8f, Color.WHITE));
        if (sub != null && !sub.isEmpty()) left.addView(Ui.text(this, sub, 1.2f, Color.argb(175, 255, 255, 255)));
        r.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(Ui.text(this, value, 1.6f, Themes.current(this).accent));
        r.setOnFocusChangeListener((v, focused) -> Ui.focusAnim(v, focused, reduce));
        r.setOnClickListener(v -> {
            focusAt = rows.indexOf(v);
            action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.u(0.5f);
        box.addView(r, lp);
        rows.add(r);
    }

    // ---- values -------------------------------------------------------------------------

    private String onOff(boolean on) { return getString(on ? R.string.on : R.string.off); }

    private String langName() {
        String l = Prefs.lang(this);
        return l.equals("en") ? getString(R.string.lang_en) : l.equals("ar") ? getString(R.string.lang_ar)
                : getString(R.string.lang_system);
    }

    private String reduceName() {
        int m = Prefs.reduceMode(this);
        return m == 1 ? getString(R.string.on) : m == 0 ? getString(R.string.off) : getString(R.string.auto);
    }

    private String methodName() {
        int id = Prefs.method(this);
        for (int i = 0; i < METHOD_IDS.length; i++) if (METHOD_IDS[i] == id) return METHOD_NAMES[i];
        return String.valueOf(id);
    }

    private String standbyName() {
        int h = Prefs.standby(this);
        return h == 0 ? getString(R.string.off) : h == 1 ? getString(R.string.hours_1)
                : h == 4 ? getString(R.string.hours_4) : getString(R.string.hours_6);
    }

    private int indexOf(int[] values, int v) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return 0;
    }

    // ---- choices ------------------------------------------------------------------------

    private void choose(int title, String[] items, int checked, final Pick pick) {
        new AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    d.dismiss();
                    pick.on(which);
                }).show();
    }

    private void chooseLanguage() {
        String[] items = {getString(R.string.lang_system), getString(R.string.lang_en), getString(R.string.lang_ar)};
        int cur = 0;
        for (int i = 0; i < LANG_VALUES.length; i++) if (LANG_VALUES[i].equals(Prefs.lang(this))) cur = i;
        choose(R.string.set_language, items, cur, i -> {
            Prefs.lang(this, LANG_VALUES[i]);
            recreate();
        });
    }

    private void chooseTheme() {
        String[] items = new String[Themes.ALL.length];
        for (int i = 0; i < items.length; i++) items[i] = getString(Themes.ALL[i].name);
        choose(R.string.set_theme, items, Prefs.theme(this), i -> {
            Prefs.theme(this, i);
            recreate();
        });
    }

    private void chooseReduce() {
        String[] items = {getString(R.string.auto), getString(R.string.on), getString(R.string.off)};
        choose(R.string.set_reduce, items, indexOf(REDUCE_VALUES, Prefs.reduceMode(this)), i -> {
            Prefs.reduceMode(this, REDUCE_VALUES[i]);
            recreate();
        });
    }

    private void chooseCols() {
        String[] items = new String[COLS_VALUES.length];
        for (int i = 0; i < items.length; i++) items[i] = String.valueOf(COLS_VALUES[i]);
        choose(R.string.set_cols, items, indexOf(COLS_VALUES, Prefs.cols(this)), i -> {
            Prefs.cols(this, COLS_VALUES[i]);
            build();
        });
    }

    private void chooseCards() {
        final String[] ids = Prefs.CARD_IDS;
        String[] items = new String[ids.length];
        final boolean[] on = new boolean[ids.length];
        Set<String> enabled = Prefs.cards(this);
        for (int i = 0; i < ids.length; i++) {
            items[i] = getString(CardRepo.byId(ids[i]).titleRes());
            on[i] = enabled.contains(ids[i]);
        }
        new AlertDialog.Builder(this).setTitle(R.string.set_cards)
                .setMultiChoiceItems(items, on, (d, which, checked) -> on[which] = checked)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    Set<String> v = new HashSet<>();
                    for (int i = 0; i < ids.length; i++) if (on[i]) v.add(ids[i]);
                    Prefs.cards(this, v);
                    build();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void chooseMethod() {
        choose(R.string.set_method, METHOD_NAMES, indexOf(METHOD_IDS, Prefs.method(this)), i -> {
            Prefs.method(this, METHOD_IDS[i]);
            build();
        });
    }

    private void chooseStandby() {
        String[] items = {getString(R.string.off), getString(R.string.hours_1), getString(R.string.hours_4), getString(R.string.hours_6)};
        choose(R.string.set_standby, items, indexOf(STANDBY_VALUES, Prefs.standby(this)), i -> {
            Prefs.standby(this, STANDBY_VALUES[i]);
            if (STANDBY_VALUES[i] > 0 && !accessibilityOn()) {
                Toast.makeText(this, R.string.set_access_hint, Toast.LENGTH_LONG).show();
            }
            build();
        });
    }

    // ---- accessibility service (needed for standby) -------------------------------------

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

    // ---- city search (Open-Meteo geocoding) ---------------------------------------------

    private void editCity() {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setText(Prefs.city(this));
        new AlertDialog.Builder(this).setTitle(R.string.set_city).setMessage(R.string.city_prompt).setView(et)
                .setPositiveButton(android.R.string.ok, (d, w) -> searchCity(et.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null).show();
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
        new AlertDialog.Builder(this).setTitle(R.string.set_city)
                .setItems(labels, (d, which) -> {
                    String[] h = hits.get(which);
                    Prefs.place(this, h[0], h[1], h[2]);
                    build();
                }).show();
    }

    // ---- apps ---------------------------------------------------------------------------

    private void chooseDock() {
        new Thread(() -> {
            final List<Apps.Item> all = Apps.load(this, false);
            runOnUiThread(() -> {
                Set<String> hidden = Prefs.hidden(this);
                final List<Apps.Item> list = new ArrayList<>();
                for (Apps.Item it : all) if (!hidden.contains(it.pkg)) list.add(it);
                String[] items = new String[list.size()];
                final boolean[] on = new boolean[items.length];
                List<String> dock = Prefs.dock(this);
                for (int i = 0; i < items.length; i++) {
                    items[i] = list.get(i).label;
                    on[i] = dock.contains(list.get(i).pkg);
                }
                new AlertDialog.Builder(this).setTitle(R.string.set_dock)
                        .setMultiChoiceItems(items, on, (d, which, checked) -> on[which] = checked)
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            List<String> v = new ArrayList<>();
                            for (int i = 0; i < on.length; i++) if (on[i]) v.add(list.get(i).pkg);
                            Prefs.dock(this, v);
                            Prefs.bumpApps(this);
                        })
                        .setNegativeButton(android.R.string.cancel, null).show();
            });
        }).start();
    }

    private void chooseHidden() {
        new Thread(() -> {
            final List<Apps.Item> all = Apps.load(this, false);
            runOnUiThread(() -> {
                String[] items = new String[all.size()];
                final boolean[] on = new boolean[items.length];
                Set<String> hidden = Prefs.hidden(this);
                for (int i = 0; i < items.length; i++) {
                    items[i] = all.get(i).label;
                    on[i] = hidden.contains(all.get(i).pkg);
                }
                new AlertDialog.Builder(this).setTitle(R.string.set_hidden)
                        .setMultiChoiceItems(items, on, (d, which, checked) -> on[which] = checked)
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            Set<String> v = new HashSet<>();
                            for (int i = 0; i < on.length; i++) if (on[i]) v.add(all.get(i).pkg);
                            Prefs.hidden(this, v);
                            Prefs.bumpApps(this);
                        })
                        .setNegativeButton(android.R.string.cancel, null).show();
            });
        }).start();
    }
}
