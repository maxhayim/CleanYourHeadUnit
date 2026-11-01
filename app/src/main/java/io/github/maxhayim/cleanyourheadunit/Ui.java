package io.github.maxhayim.cleanyourheadunit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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
 * The app's small UI kit, built in code: cards, rows, pills, buttons and a responsive grid.
 * Touch targets are at least 56 dp tall; nothing animates.
 */
public final class Ui {
    private static Typeface font, fontBold;

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static Typeface font(Context c, boolean bold) {
        if (font == null) {
            try {
                font = Typeface.createFromAsset(c.getAssets(), "fonts/outfit.ttf");
            } catch (RuntimeException e) {
                font = Typeface.SANS_SERIF;
            }
            fontBold = Typeface.create(font, Typeface.BOLD);
        }
        return bold ? fontBold : font;
    }

    public static Theme t(Context c) {
        return Theme.get(c);
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setTypeface(font(c, bold));
        v.setLineSpacing(0, 1.12f);
        return v;
    }

    public static TextView body(Context c, CharSequence s) {
        return text(c, s, 15, t(c).text, false);
    }

    public static TextView dim(Context c, CharSequence s) {
        return text(c, s, 13, t(c).dim, false);
    }

    public static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    public static int mix(int a, int b, float f) {
        return Color.rgb(Math.round(Color.red(a) * (1 - f) + Color.red(b) * f),
                Math.round(Color.green(a) * (1 - f) + Color.green(b) * f),
                Math.round(Color.blue(a) * (1 - f) + Color.blue(b) * f));
    }

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        l.setBackground(round(t(c).surface, dp(c, 14)));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static View gap(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, dp), dp(c, dp)));
        return v;
    }

    public static void add(LinearLayout parent, View child, int topGapDp) {
        LinearLayout.LayoutParams p = child.getLayoutParams() instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) child.getLayoutParams()
                : parent.getOrientation() == LinearLayout.VERTICAL ? match() : wrap();
        if (parent.getOrientation() == LinearLayout.VERTICAL) p.topMargin = dp(parent.getContext(), topGapDp);
        else p.leftMargin = dp(parent.getContext(), topGapDp);
        parent.addView(child, p);
    }

    /** Buttons into a wrapping row; the row handles spacing. */
    public static void add(Flow parent, View child, int ignoredGapDp) {
        parent.addView(child);
    }

    /** Section title with an optional one-line explanation. */
    public static View header(Context c, String title, String sub) {
        LinearLayout l = vbox(c);
        l.addView(text(c, title, 26, t(c).text, true));
        if (sub != null) add(l, text(c, sub, 14, t(c).dim, false), 4);
        return l;
    }

    /** A coloured pill: status, category, finding. */
    public static TextView pill(Context c, String s, int color) {
        TextView v = text(c, s, 12, color, true);
        v.setBackground(round(mix(t(c).surface, color, 0.18f), dp(c, 20)));
        v.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        v.setSingleLine(true);
        return v;
    }

    public static int categoryColor(Context c, String cat) {
        Theme th = t(c);
        switch (cat) {
            case Rules.RECOMMENDED: return th.ok;
            case Rules.OPTIONAL: return th.accent;
            case Rules.ADVANCED: return th.warn;
            case Rules.PROTECTED: return mix(th.dim, th.text, 0.4f);
            case Rules.USER: return th.dim;
            default: return mix(th.warn, th.dim, 0.5f);
        }
    }

    public static String categoryLabel(String cat) {
        switch (cat) {
            case Rules.RECOMMENDED: return "Recommended";
            case Rules.OPTIONAL: return "Optional";
            case Rules.ADVANCED: return "Advanced";
            case Rules.PROTECTED: return "Protected";
            case Rules.USER: return "Your app";
            default: return "Unknown";
        }
    }

    public static TextView button(Context c, String label, boolean primary, View.OnClickListener l) {
        Theme th = t(c);
        TextView b = text(c, label, 15, primary ? (th.dark ? Color.BLACK : Color.WHITE) : th.text, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 52));
        b.setPadding(dp(c, 18), 0, dp(c, 18), 0);
        int base = primary ? th.accent : mix(th.surface, th.text, 0.10f);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, round(mix(base, th.text, 0.25f), dp(c, 12)));
        s.addState(new int[]{android.R.attr.state_focused}, round(mix(base, th.text, 0.18f), dp(c, 12)));
        s.addState(new int[]{}, round(base, dp(c, 12)));
        b.setBackground(s);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }

    /** "Key  value" line; a missing value reads "Not reported", never a guess. */
    public static View kv(Context c, String key, String value) {
        LinearLayout r = hbox(c);
        r.setMinimumHeight(dp(c, 34));
        TextView k = text(c, key, 14, t(c).dim, false);
        r.addView(k, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f));
        boolean missing = value == null || value.trim().isEmpty();
        TextView v = text(c, missing ? "Not reported" : value, 15, missing ? t(c).dim : t(c).text, !missing);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setMaxLines(8);
        r.setBaselineAligned(false);
        r.setPadding(0, dp(c, 4), 0, dp(c, 4));
        r.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f));
        return r;
    }

    public static ImageView icon(Context c, int res, int color, int sizeDp) {
        ImageView iv = new ImageView(c);
        Drawable d = c.getResources().getDrawable(res).mutate();
        d.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        iv.setImageDrawable(d);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return iv;
    }

    /** A big number with a label under it, for the dashboard. */
    public static View stat(Context c, String value, String label, int color) {
        LinearLayout l = card(c);
        l.addView(text(c, value, 24, color, true));
        add(l, dim(c, label), 2);
        return l;
    }

    /** Lays children out in as many equal columns as fit cells of at least minCellDp. */
    public static final class Grid extends ViewGroup {
        final int minCell, gap;
        int forcedCols;

        public Grid(Context c, int minCellDp) {
            super(c);
            minCell = dp(c, minCellDp);
            gap = dp(c, 12);
        }

        int cols(int width) {
            if (forcedCols > 0) return forcedCols;
            // Few items stretch to fill the row instead of leaving a hole on the right.
            return Math.max(1, Math.min(Math.max(1, getChildCount()), (width + gap) / (minCell + gap)));
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws);
            int cols = cols(w);
            int cw = (w - gap * (cols - 1)) / cols;
            int h = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                v.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                rowH = Math.max(rowH, v.getMeasuredHeight());
                if (i % cols == cols - 1 || i == getChildCount() - 1) {
                    h += rowH + (i == getChildCount() - 1 ? 0 : gap);
                    rowH = 0;
                }
            }
            // Equal heights in a row look tidier: measure again at the row's height.
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int w = r - l;
            int cols = cols(w);
            int cw = (w - gap * (cols - 1)) / cols;
            int y = 0;
            for (int start = 0; start < getChildCount(); start += cols) {
                int rowH = 0;
                for (int i = start; i < Math.min(start + cols, getChildCount()); i++) rowH = Math.max(rowH, getChildAt(i).getMeasuredHeight());
                for (int i = start; i < Math.min(start + cols, getChildCount()); i++) {
                    View v = getChildAt(i);
                    int x = (i - start) * (cw + gap);
                    if (v.getMeasuredHeight() != rowH) {
                        v.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(rowH, MeasureSpec.EXACTLY));
                    }
                    v.layout(x, y, x + cw, y + rowH);
                }
                y += rowH + gap;
            }
        }
    }

    /** A row of buttons that wraps onto the next line on narrow screens (800x480) instead of clipping. */
    public static final class Flow extends ViewGroup {
        final int gap;

        public Flow(Context c) {
            super(c);
            gap = dp(c, 10);
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws);
            int x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v.getVisibility() == GONE) continue;
                v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                if (x > 0 && x + v.getMeasuredWidth() > w) {
                    x = 0;
                    y += rowH + gap;
                    rowH = 0;
                }
                x += v.getMeasuredWidth() + gap;
                rowH = Math.max(rowH, v.getMeasuredHeight());
            }
            setMeasuredDimension(w, y + rowH);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int w = r - l, x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v.getVisibility() == GONE) continue;
                if (x > 0 && x + v.getMeasuredWidth() > w) {
                    x = 0;
                    y += rowH + gap;
                    rowH = 0;
                }
                v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
                x += v.getMeasuredWidth() + gap;
                rowH = Math.max(rowH, v.getMeasuredHeight());
            }
        }
    }
}
