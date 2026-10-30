package com.cleantv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Look and feel shared by every screen, matching the interactive preview: glass pills, white
 * focus rows, accent chips, crests. Sizes are in "u" = 1% of the screen width, so the layout is
 * the same on any TV resolution or density.
 */
final class Ui {
    private static float unit = 10f;

    private Ui() {}

    static void init(Context c) { unit = c.getResources().getDisplayMetrics().widthPixels / 100f; }

    static int u(float v) { return Math.round(v * unit); }

    static TextView text(Context c, CharSequence s, float sizeU, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, u(sizeU));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static void oneLine(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
    }

    // ---- shapes -------------------------------------------------------------------------

    static GradientDrawable shape(int fill, float radiusU, int stroke, float strokeU) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(u(radiusU));
        if (stroke != 0) g.setStroke(Math.max(1, u(strokeU)), stroke);
        return g;
    }

    static GradientDrawable solid(int color, float radiusU) { return shape(color, radiusU, 0, 0f); }

    /** Fill with a white ring when focused (cards, tiles). */
    static StateListDrawable focusBg(int base, float radiusU) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, shape(base, radiusU, Color.WHITE, 0.3f));
        s.addState(new int[]{}, shape(base, radiusU, Color.argb(51, 255, 255, 255), 0.1f));
        return s;
    }

    /** Transparent overlay that only draws the white ring when focused (tiles with images). */
    static StateListDrawable focusRing(float radiusU) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, shape(Color.TRANSPARENT, radiusU, Color.WHITE, 0.3f));
        s.addState(new int[]{}, shape(Color.TRANSPARENT, radiusU, 0, 0f));
        return s;
    }

    /** The glass pill of the header buttons: brighter and ringed when focused. */
    static StateListDrawable pillBg(float radiusU) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, shape(Color.argb(77, 255, 255, 255), radiusU, Color.WHITE, 0.3f));
        s.addState(new int[]{}, shape(Color.argb(31, 255, 255, 255), radiusU, Color.argb(64, 255, 255, 255), 0.12f));
        return s;
    }

    // ---- focus effect -------------------------------------------------------------------

    /**
     * The grow effect when something gets focus. Reduced motion keeps the same enlargement but
     * applies it instantly, with no animation, so low-memory TVs still show where the focus is.
     */
    static void focusAnim(View v, boolean focused, boolean reduce, float scale) {
        float s = focused ? scale : 1f;
        v.setTranslationZ(focused ? u(1f) : 0f);
        if (reduce) {
            v.animate().cancel();
            v.setScaleX(s);
            v.setScaleY(s);
            return;
        }
        v.animate().scaleX(s).scaleY(s).setDuration(120).start();
    }

    static void focusAnim(View v, boolean focused, boolean reduce) { focusAnim(v, focused, reduce, 1.07f); }

    // ---- header pill with an icon ---------------------------------------------------------

    /** A glass pill with an icon and optional text; icon-only themes get a round button. */
    static LinearLayout iconPill(Context c, int iconRes, CharSequence label, boolean iconOnly,
                                 final boolean reduce, final Runnable action) {
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER);
        p.setFocusable(true);
        p.setClickable(true);
        p.setBackground(pillBg(5f));
        ImageView icon = new ImageView(c);
        icon.setImageResource(iconRes);
        int size = u(iconOnly ? 1.9f : 1.7f);
        p.addView(icon, new LinearLayout.LayoutParams(size, size));
        if (iconOnly) {
            p.setPadding(u(0.85f), u(0.85f), u(0.85f), u(0.85f));
        } else {
            p.setPadding(u(1.5f), u(0.7f), u(1.7f), u(0.7f));
            TextView t = text(c, label, 1.3f, Color.WHITE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(u(0.6f));
            p.addView(t, lp);
        }
        p.setOnFocusChangeListener((v, focused) -> v.setTranslationZ(focused ? u(1f) : 0f));
        p.setOnClickListener(v -> action.run());
        p.setContentDescription(label);
        return p;
    }

    // ---- accent chips (the "On / Off" and tab buttons) --------------------------------------

    static final class Chip extends TextView {
        private final int accent;
        private final boolean reduce;
        private boolean on;

        Chip(Context c, CharSequence label, boolean on, int accent, float sizeU, boolean reduce) {
            super(c);
            this.accent = accent;
            this.reduce = reduce;
            setText(label);
            setTextSize(TypedValue.COMPLEX_UNIT_PX, u(sizeU));
            setIncludeFontPadding(false);
            setSingleLine(true);
            setGravity(Gravity.CENTER);
            setPadding(u(1.3f), u(0.55f), u(1.3f), u(0.55f));
            setFocusable(true);
            setClickable(true);
            setOn(on);
            setOnFocusChangeListener((v, focused) -> {
                focusAnim(v, focused, this.reduce, 1.05f);
                paint(focused);
            });
        }

        void setOn(boolean v) {
            on = v;
            paint(isFocused());
        }

        private void paint(boolean focused) {
            int ring = focused ? Color.WHITE : 0;
            if (on) {
                setBackground(shape(accent, 4f, ring, 0.3f));
                setTextColor(Color.BLACK);
            } else {
                setBackground(shape(Color.TRANSPARENT, 4f, focused ? Color.WHITE : Color.argb(170, 255, 255, 255), focused ? 0.3f : 0.12f));
                setTextColor(Color.WHITE);
            }
        }
    }

    // ---- list rows (settings, dialogs, categories) --------------------------------------------

    /** A row that turns white with dark text when focused, like the preview's lists. */
    static final class Row extends LinearLayout {
        final TextView title, sub, trail;
        private final int accent;
        private boolean filled, selected;

        Row(Context c, CharSequence t, CharSequence s, int accent) {
            super(c);
            this.accent = accent;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(u(1.4f), u(0.9f), u(1.4f), u(0.9f));
            setFocusable(true);
            setClickable(true);
            LinearLayout left = new LinearLayout(c);
            left.setOrientation(VERTICAL);
            title = text(c, t, 1.65f, Color.WHITE);
            sub = text(c, s == null ? "" : s, 1.15f, Color.WHITE);
            sub.setVisibility(s == null || s.length() == 0 ? GONE : VISIBLE);
            left.addView(title);
            left.addView(sub);
            addView(left, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            trail = text(c, "", 1.3f, Color.WHITE);
            trail.setSingleLine(true);
            trail.setVisibility(GONE);
            trail.setPadding(u(1f), u(0.3f), u(1f), u(0.3f));
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(u(1f));
            addView(trail, lp);
            style(false);
            setOnFocusChangeListener((v, focused) -> style(focused));
        }

        /** @param filled true for an "On" pill (accent fill), false for an outlined value */
        void setTrail(String text, boolean filled) {
            trail.setText(text);
            trail.setVisibility(text == null || text.isEmpty() ? GONE : VISIBLE);
            this.filled = filled;
            style(isFocused());
        }

        void setSelectedLook(boolean on) {
            selected = on;
            style(isFocused());
        }

        private void style(boolean f) {
            setBackground(solid(f ? Color.WHITE : selected ? Color.argb(28, 255, 255, 255) : Color.TRANSPARENT, 1f));
            title.setTextColor(f ? Color.BLACK : Color.WHITE);
            sub.setTextColor(f ? 0xFF4A4F5C : Color.argb(155, 255, 255, 255));
            if (trail.getVisibility() == VISIBLE) {
                if (filled) {
                    trail.setBackground(solid(f ? Color.BLACK : accent, 4f));
                    trail.setTextColor(f ? Color.WHITE : Color.BLACK);
                } else {
                    int col = f ? Color.BLACK : Color.WHITE;
                    trail.setBackground(shape(Color.TRANSPARENT, 4f, col, 0.12f));
                    trail.setTextColor(col);
                }
            }
        }
    }

    // ---- team crest -----------------------------------------------------------------------

    /** A round badge with the team's initials, coloured from its name. */
    static TextView crest(Context c, String name, float sizeU) {
        int h = 0;
        for (int i = 0; i < name.length(); i++) h = (h * 31 + name.charAt(i)) % 360;
        StringBuilder ini = new StringBuilder();
        for (String w : name.split(" ")) if (!w.isEmpty() && ini.length() < 2) ini.append(w.charAt(0));
        TextView t = text(c, ini.toString(), sizeU * 0.42f, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.HSVToColor(new float[]{h, 0.45f, 0.5f}));
        t.setBackground(g);
        t.setLayoutParams(new LinearLayout.LayoutParams(u(sizeU), u(sizeU)));
        return t;
    }

    // ---- progress bar -----------------------------------------------------------------------

    static final class Bar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress;

        Bar(Context c) {
            super(c);
            track.setColor(0x2EFFFFFF);
        }

        void set(float p, int color) {
            progress = p;
            fill.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            cv.drawRoundRect(0, 0, w, h, r, r, track);
            if (progress > 0f) cv.drawRoundRect(0, 0, w * Math.min(1f, progress), h, r, r, fill);
        }
    }
}
