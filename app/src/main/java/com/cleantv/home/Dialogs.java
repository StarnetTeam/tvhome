package com.cleantv.home;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Pop-up panels in the same style as the rest of the app: dark glass, white focus rows. */
final class Dialogs {
    interface Pick { void on(int index); }
    interface Text { void on(String value); }

    private Dialogs() {}

    /** A scroll view that never grows taller than a fixed height. */
    private static final class MaxScroll extends ScrollView {
        private final int max;

        MaxScroll(Context c, int max) {
            super(c);
            this.max = max;
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST));
        }
    }

    private static final class Panel {
        final Dialog dialog;
        final LinearLayout list;
        final LinearLayout root;

        Panel(Activity a, String title) {
            dialog = new Dialog(a, android.R.style.Theme_Material_Dialog_NoActionBar);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            if (Prefs.reduce(a)) dialog.getWindow().setWindowAnimations(0);
            root = new LinearLayout(a);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackground(Ui.solid(Color.argb(246, 16, 20, 30), 1.6f));
            root.setPadding(Ui.u(2f), Ui.u(1.6f), Ui.u(2f), Ui.u(1.6f));
            root.addView(Ui.text(a, title, 2.1f, Color.WHITE));
            list = new LinearLayout(a);
            list.setOrientation(LinearLayout.VERTICAL);
            MaxScroll sc = new MaxScroll(a, Ui.u(28f));
            sc.addView(list);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.u(1f);
            root.addView(sc, lp);
            dialog.setContentView(root, new ViewGroup.LayoutParams(Ui.u(40f), ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        Ui.Row add(Activity a, String text) {
            Ui.Row r = new Ui.Row(a, text, null, Themes.current(a).accent);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.u(0.3f);
            list.addView(r, lp);
            return r;
        }

        void buttons(Activity a, String ok, Runnable onOk, String cancel) {
            LinearLayout bar = new LinearLayout(a);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.END);
            boolean reduce = Prefs.reduce(a);
            int accent = Themes.current(a).accent;
            Ui.Chip c = new Ui.Chip(a, cancel, false, accent, 1.5f, reduce);
            c.setOnClickListener(v -> dialog.dismiss());
            Ui.Chip o = new Ui.Chip(a, ok, true, accent, 1.5f, reduce);
            o.setOnClickListener(v -> {
                dialog.dismiss();
                if (onOk != null) onOk.run();
            });
            LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gap.setMarginStart(Ui.u(1f));
            bar.addView(c);
            bar.addView(o, gap);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.u(1.2f);
            root.addView(bar, lp);
        }
    }

    /** One choice from a list; the current one is marked. */
    static void choose(Activity a, String title, String[] items, int checked, final Pick pick) {
        final Panel p = new Panel(a, title);
        Ui.Row focus = null;
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            Ui.Row r = p.add(a, items[i]);
            if (i == checked) {
                r.setTrail("\u2713", true);
                focus = r;
            }
            r.setOnClickListener(v -> {
                p.dialog.dismiss();
                pick.on(idx);
            });
        }
        p.dialog.show();
        if (focus != null) focus.requestFocus();
        else if (p.list.getChildCount() > 0) p.list.getChildAt(0).requestFocus();
    }

    /** A plain menu: choose one action. */
    static void menu(Activity a, String title, String[] items, final Pick pick) {
        choose(a, title, items, -1, pick);
    }

    /** Several on/off choices with OK and Cancel; on[] is updated in place. */
    static void multi(final Activity a, String title, String[] items, final boolean[] on, final Runnable onOk) {
        final Panel p = new Panel(a, title);
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            final Ui.Row r = p.add(a, items[i]);
            r.setTrail(a.getString(on[i] ? R.string.on : R.string.off), on[i]);
            r.setOnClickListener(v -> {
                on[idx] = !on[idx];
                r.setTrail(a.getString(on[idx] ? R.string.on : R.string.off), on[idx]);
            });
        }
        p.buttons(a, a.getString(R.string.ok), onOk, a.getString(R.string.cancel));
        p.dialog.show();
        if (p.list.getChildCount() > 0) p.list.getChildAt(0).requestFocus();
    }

    static void message(Activity a, String title, String message) {
        Panel p = new Panel(a, title);
        p.list.addView(Ui.text(a, message, 1.5f, Color.argb(220, 255, 255, 255)));
        p.buttons(a, a.getString(R.string.ok), null, a.getString(R.string.back));
        p.dialog.show();
    }

    static void input(final Activity a, String title, String hint, String initial, final Text ok) {
        final Panel p = new Panel(a, title);
        p.list.addView(Ui.text(a, hint, 1.3f, Color.argb(190, 255, 255, 255)));
        final EditText et = new EditText(a);
        et.setSingleLine(true);
        et.setText(initial);
        et.setTextColor(Color.WHITE);
        et.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.u(1.8f));
        p.list.addView(et);
        p.buttons(a, a.getString(R.string.ok), () -> ok.on(et.getText().toString().trim()), a.getString(R.string.cancel));
        p.dialog.show();
        et.requestFocus();
    }
}
