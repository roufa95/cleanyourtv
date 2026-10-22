package com.gathin.tvhome.waste;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.text.Spanned;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhome.R;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The bin calendar panel on Home (config.xml waste_commune): the next collection per bin, amber the evening before. */
public final class WastePanel {

    /** Rows on the bin card: one per bin type normally, fewer under the evening reminder. */
    private static final int WASTE_ROWS = 4, WASTE_ROWS_TONIGHT = 2;

    private final Activity activity;
    private final float density;
    private final String commune, locality, street;
    private List<WasteCalendar.Pickup> pickups = java.util.Collections.emptyList();
    private final View wastePanel;
    private final TextView wasteKicker, wasteAlert, wasteHeadline;
    private final LinearLayout wasteRows;
    private boolean wasteReminding;

    public WastePanel(Activity activity, String commune, String locality, String street) {
        this.activity = activity;
        density = activity.getResources().getDisplayMetrics().density;
        this.commune = commune;
        this.locality = locality;
        this.street = street;
        wastePanel = activity.findViewById(R.id.wastePanel);
        wasteKicker = activity.findViewById(R.id.wasteKicker);
        wasteAlert = activity.findViewById(R.id.wasteAlert);
        wasteHeadline = activity.findViewById(R.id.wasteHeadline);
        wasteRows = activity.findViewById(R.id.wasteRows);
        if (commune.isEmpty()) return;
        setUpRows();
        // A few dozen lines from the last weekly download; small enough to read here.
        pickups = WasteFetch.cached(activity, commune, locality, street);
    }

    /** Downloads the calendar again when the weekly refresh is due; {@code onChanged} runs when it arrives. */
    public void refreshIfDue(Runnable onChanged) {
        if (commune.isEmpty()) return;
        WasteFetch.refreshIfDue(activity, commune, locality, street, fresh -> {
            pickups = fresh;
            onChanged.run();
        });
    }

    private void setUpRows() {
        for (int i = 0; i < WASTE_ROWS; i++) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            android.widget.ImageView icon = new android.widget.ImageView(activity);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams((int) (18 * density), (int) (18 * density));
            ip.setMarginEnd((int) (10 * density));
            row.addView(icon, ip);
            row.addView(wasteText(14f, 0xFFFFFFFF), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView date = wasteText(13f, 0xE6FFFFFF);
            row.addView(date, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView rel = wasteText(12f, 0x99FFFFFF);
            rel.setGravity(Gravity.END);
            row.addView(rel, new LinearLayout.LayoutParams((int) (72 * density), ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (6 * density);
            wasteRows.addView(row, lp);
        }
    }

    private TextView wasteText(float sp, int color) {
        TextView t = new TextView(activity);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setTypeface(wasteKicker.getTypeface());
        return t;
    }

    /** Fills the panel for this minute; it stays hidden while the race panel is up or nothing is coming. */
    public void update(boolean racePanelShown) {
        if (commune.isEmpty()) return;
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<WasteCalendar.Pickup> upcoming = WasteCalendar.upcoming(pickups, now);
        if (upcoming.isEmpty() || racePanelShown) {
            wastePanel.setVisibility(View.GONE);
            return;
        }
        String place = locality.isEmpty() ? commune : locality;
        wasteKicker.setText(activity.getString(R.string.waste_kicker, place));

        // The evening before a collection the card turns amber and leads with the bins to put out.
        List<WasteCalendar.Pickup> tonight = WasteCalendar.tonight(pickups, now);
        boolean remind = !tonight.isEmpty();
        if (remind != wasteReminding) {
            wasteReminding = remind;
            wastePanel.setBackgroundResource(remind ? R.drawable.waste_panel_alert : R.drawable.waste_panel);
            wasteAlert.setVisibility(remind ? View.VISIBLE : View.GONE);
            wasteHeadline.setVisibility(remind ? View.VISIBLE : View.GONE);
        }
        List<WasteCalendar.Pickup> rows = WasteCalendar.nextPerKind(upcoming);
        if (remind) {
            wasteAlert.setText(activity.getString(R.string.waste_put_out_tonight));
            android.text.SpannableStringBuilder bins = new android.text.SpannableStringBuilder();
            for (WasteCalendar.Pickup p : tonight) {
                if (bins.length() > 0) bins.append("   ");
                int at = bins.length();
                bins.append('￼');
                bins.setSpan(iconSpan(p.kind, wasteHeadline.getTextSize()), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                bins.append(' ').append(name(activity, p));
            }
            wasteHeadline.setText(bins);
            // Under it, what comes after tomorrow.
            List<WasteCalendar.Pickup> later = new ArrayList<>();
            for (WasteCalendar.Pickup p : rows) if (!p.date.equals(tonight.get(0).date)) later.add(p);
            rows = later;
        }

        // Each row: [icon] Household   Fri 23   in 2 days.
        java.time.LocalDate today = now.toLocalDate();
        int shown = remind ? WASTE_ROWS_TONIGHT : WASTE_ROWS;
        for (int i = 0; i < wasteRows.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) wasteRows.getChildAt(i);
            if (i >= shown || i >= rows.size()) {
                row.setVisibility(View.GONE);
                continue;
            }
            WasteCalendar.Pickup p = rows.get(i);
            row.setVisibility(View.VISIBLE);
            android.graphics.drawable.Drawable icon = activity.getDrawable(iconRes(p.kind)).mutate();
            icon.setTint(color(p.kind));
            ((android.widget.ImageView) row.getChildAt(0)).setImageDrawable(icon);
            ((TextView) row.getChildAt(1)).setText(name(activity, p));
            ((TextView) row.getChildAt(2)).setText(java.time.format.DateTimeFormatter.ofPattern(
                    p.date.isBefore(today.plusDays(7)) ? "EEE d" : "EEE d MMM", Locale.getDefault()).format(p.date));
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, p.date);
            ((TextView) row.getChildAt(3)).setText(days == 0 ? activity.getString(R.string.waste_today)
                    : days == 1 ? activity.getString(R.string.waste_tomorrow) : activity.getString(R.string.waste_in_days, (int) days));
        }
        wastePanel.setVisibility(View.VISIBLE);
    }

    /** A bin's icon as a text span, {@code size} pixels square, tinted in the bin's colour. */
    private android.text.style.ImageSpan iconSpan(WasteCalendar.Kind kind, float size) {
        android.graphics.drawable.Drawable d = activity.getDrawable(iconRes(kind)).mutate();
        d.setTint(color(kind));
        d.setBounds(0, 0, Math.round(size), Math.round(size));
        return new android.text.style.ImageSpan(d, Build.VERSION.SDK_INT >= 29
                ? android.text.style.DynamicDrawableSpan.ALIGN_CENTER : android.text.style.DynamicDrawableSpan.ALIGN_BASELINE);
    }

    public static int iconRes(WasteCalendar.Kind kind) {
        switch (kind) {
            case HOUSEHOLD: return R.drawable.ic_bin_household;
            case BIO: return R.drawable.ic_bin_bio;
            case VALORLUX: return R.drawable.ic_bin_valorlux;
            case PAPER: return R.drawable.ic_bin_paper;
            case GLASS: return R.drawable.ic_bin_glass;
            case BULKY: return R.drawable.ic_bin_bulky;
            case ELECTRONICS: return R.drawable.ic_bin_electronics;
            case SDK: return R.drawable.ic_bin_sdk;
            case GREEN: return R.drawable.ic_bin_green;
            case TREES: return R.drawable.ic_bin_trees;
            case WOOD: return R.drawable.ic_bin_wood;
            default: return R.drawable.ic_bin_other;
        }
    }

    public static String name(Context c, WasteCalendar.Pickup p) {
        switch (p.kind) {
            case HOUSEHOLD: return c.getString(R.string.waste_household);
            case BIO: return c.getString(R.string.waste_bio);
            case VALORLUX: return c.getString(R.string.waste_valorlux);
            case PAPER: return c.getString(R.string.waste_paper);
            case GLASS: return c.getString(R.string.waste_glass);
            case BULKY: return c.getString(R.string.waste_bulky);
            case ELECTRONICS: return c.getString(R.string.waste_electronics);
            case SDK: return c.getString(R.string.waste_sdk);
            case GREEN: return c.getString(R.string.waste_green);
            case TREES: return c.getString(R.string.waste_trees);
            case WOOD: return c.getString(R.string.waste_wood);
            default: return p.type;
        }
    }

    /** One colour per bin, for the dots in the row: grey residual, green bio, blue Valorlux bags and so on. */
    public static int color(WasteCalendar.Kind kind) {
        switch (kind) {
            case HOUSEHOLD: return 0xFFB0BEC5;
            case BIO: return 0xFF8BC34A;
            case VALORLUX: return 0xFF42A5F5;
            case PAPER: return 0xFFFFD54F;
            case GLASS: return 0xFF26A69A;
            case BULKY: return 0xFFBCAAA4;
            case ELECTRONICS: return 0xFFAB47BC;
            case SDK: return 0xFFEF5350;
            case GREEN: return 0xFF66BB6A;
            case TREES: return 0xFF81C784;
            case WOOD: return 0xFFA1887F;
            default: return 0x99FFFFFF;
        }
    }
}
