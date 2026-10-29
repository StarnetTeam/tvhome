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
import android.widget.TextView;

/**
 * View helpers. Sizes are in "u" = 1% of the screen width, so the layout looks the same on any TV
 * resolution or density (the same idea as the interactive preview).
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

    private static GradientDrawable shape(int fill, float radiusU, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(u(radiusU));
        if (stroke != 0) g.setStroke(Math.max(2, u(0.3f)), stroke);
        return g;
    }

    /** Background that gets a white ring when focused. */
    static StateListDrawable focusBg(int base, float radiusU) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, shape(base, radiusU, Color.WHITE));
        s.addState(new int[]{}, shape(base, radiusU, 0));
        return s;
    }

    /** Transparent overlay that only draws the white ring when focused (for tiles with images). */
    static StateListDrawable focusRing(float radiusU) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, shape(Color.TRANSPARENT, radiusU, Color.WHITE));
        s.addState(new int[]{}, shape(Color.TRANSPARENT, radiusU, 0));
        return s;
    }

    static GradientDrawable solid(int color, float radiusU) { return shape(color, radiusU, 0); }

    /** A small grow animation on focus; skipped entirely in reduced-motion mode. */
    static void focusAnim(View v, boolean focused, boolean reduce) {
        if (reduce) {
            v.setScaleX(1f);
            v.setScaleY(1f);
            return;
        }
        float s = focused ? 1.06f : 1f;
        v.animate().scaleX(s).scaleY(s).setDuration(120).start();
    }

    /** A D-pad focusable text button: translucent normally, white with dark text when focused. */
    static TextView pill(Context c, CharSequence label, float sizeU, final boolean reduce, final Runnable action) {
        final TextView t = text(c, label, sizeU, Color.WHITE);
        t.setTag(Color.WHITE);
        t.setPadding(u(1.5f), u(0.8f), u(1.5f), u(0.8f));
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(focusBg(Color.argb(70, 255, 255, 255), 5f));
        t.setOnFocusChangeListener((v, focused) -> {
            t.setTextColor(focused ? Color.BLACK : (Integer) t.getTag());
            focusAnim(v, focused, reduce);
        });
        t.setOnClickListener(v -> action.run());
        return t;
    }

    /** Thin progress bar: a track and a filled part. */
    static final class Bar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress;

        Bar(Context c) {
            super(c);
            track.setColor(0x33FFFFFF);
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
