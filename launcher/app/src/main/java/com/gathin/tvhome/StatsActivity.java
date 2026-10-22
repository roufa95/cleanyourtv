package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.StatFs;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhome.stats.Stats;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The Stats pill's panel: memory and storage, the data the TV used (today, this month, the last seven days) and
 * an energy estimate (screen-on time today and this month at config.xml tv_watts, and what it cost at
 * energy_price). Data and screen time come from Android's own counters, which need usage access for the
 * launcher: `appops set com.gathin.tvhome GET_USAGE_STATS allow` (build-and-install does it). Read in the
 * background, shown in a glass panel under the top bar; Back or any other screen closes it.
 */
public final class StatsActivity extends Activity {

    private static final long DAY = 86_400_000L;
    private LinearLayout box;
    private float d;
    private Typeface medium, light;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_stats);
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        box = findViewById(R.id.stats);
        d = getResources().getDisplayMetrics().density;
        medium = getResources().getFont(R.font.outfit_medium);
        light = getResources().getFont(R.font.outfit_light);
        memory();
        TextView loading = text(getString(R.string.stats_loading), 12, 0x99FFFFFF, medium);
        box.addView(loading);
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Usage u = readUsage();
            main.post(() -> {
                if (isFinishing()) return;
                box.removeView(loading);
                show(u);
            });
        }, "stats").start();
        box.setFocusable(true);
        box.requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    // ---- memory and storage (read right away) --------------------------------------------------------------

    private void memory() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        getSystemService(ActivityManager.class).getMemoryInfo(mi);
        long used = mi.totalMem - mi.availMem;
        heading(R.string.stats_panel_memory, false);
        box.addView(bar(used / (float) mi.totalMem, 0xFF4FC3F7));
        box.addView(text(getString(R.string.stats_memory_used, Stats.bytes(used), Stats.bytes(mi.totalMem)), 14, 0xFFFFFFFF, medium));
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        box.addView(text(getString(R.string.stats_panel_storage, Stats.bytes(fs.getAvailableBytes()), Stats.bytes(fs.getTotalBytes())),
                12, 0xB3FFFFFF, medium));
    }

    // ---- data and screen time (read in the background) -----------------------------------------------------

    private static final class Usage {
        boolean access;
        long dataToday, dataMonth;
        long[] week = new long[7];
        long onToday, onMonth;
    }

    private Usage readUsage() {
        Usage u = new Usage();
        AppOpsManager ops = getSystemService(AppOpsManager.class);
        u.access = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName())
                == AppOpsManager.MODE_ALLOWED;
        if (!u.access) return u;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        long now = System.currentTimeMillis();
        long todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli();
        NetworkStatsManager nsm = getSystemService(NetworkStatsManager.class);
        u.dataToday = data(nsm, todayStart, now);
        u.dataMonth = data(nsm, monthStart, now);
        for (int i = 0; i < 7; i++) {
            long s = today.minusDays(6 - i).atStartOfDay(zone).toInstant().toEpochMilli();
            u.week[i] = data(nsm, s, Math.min(now, s + DAY));
        }

        // Screen on/off events since a little before the month began, to know whether it was on at its start.
        UsageStatsManager usm = getSystemService(UsageStatsManager.class);
        UsageEvents events = usm.queryEvents(monthStart - 2 * DAY, now);
        UsageEvents.Event e = new UsageEvents.Event();
        List<long[]> month = new ArrayList<>(), day = new ArrayList<>();
        boolean onAtMonth = false, onAtToday = false;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            int type = e.getEventType();
            if (type != UsageEvents.Event.SCREEN_INTERACTIVE && type != UsageEvents.Event.SCREEN_NON_INTERACTIVE) continue;
            boolean on = type == UsageEvents.Event.SCREEN_INTERACTIVE;
            long t = e.getTimeStamp();
            if (t < monthStart) onAtMonth = on;
            else month.add(new long[]{t, on ? 1 : 0});
            if (t < todayStart) onAtToday = on;
            else day.add(new long[]{t, on ? 1 : 0});
        }
        u.onMonth = Stats.screenOn(month, monthStart, now, onAtMonth);
        u.onToday = Stats.screenOn(day, todayStart, now, onAtToday);
        return u;
    }

    private static long data(NetworkStatsManager nsm, long start, long end) {
        long total = 0;
        for (int type : new int[]{ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_ETHERNET}) {
            try {
                NetworkStats.Bucket b = nsm.querySummaryForDevice(type, null, start, end);
                if (b != null) total += b.getRxBytes() + b.getTxBytes();
            } catch (Exception ignored) {
                // No such network on this TV, or no access: counts as nothing.
            }
        }
        return total;
    }

    private void show(Usage u) {
        if (!u.access) {
            heading(R.string.stats_data, true);
            box.addView(text(getString(R.string.stats_no_access), 12, 0xB3FFFFFF, medium));
            return;
        }
        heading(R.string.stats_data, true);
        box.addView(pair(Stats.bytes(u.dataToday), getString(R.string.stats_today),
                Stats.bytes(u.dataMonth), getString(R.string.stats_month)));
        box.addView(weekBars(u.week));

        int watts = getResources().getInteger(R.integer.tv_watts);
        double price = Stats.price(getString(R.string.energy_price));
        String currency = getString(R.string.energy_currency);
        double kToday = Stats.kwh(u.onToday, watts), kMonth = Stats.kwh(u.onMonth, watts);
        heading(R.string.stats_energy, true);
        box.addView(pair(Stats.duration(u.onToday), getString(R.string.stats_screen_on_today),
                Stats.duration(u.onMonth), getString(R.string.stats_screen_on_month)));
        box.addView(pair(Stats.energy(kToday), price > 0 ? "≈ " + Stats.money(kToday * price, currency) : "",
                Stats.energy(kMonth), price > 0 ? "≈ " + Stats.money(kMonth * price, currency) : ""));
        box.addView(text(getString(R.string.stats_energy_note, watts), 10, 0x80FFFFFF, medium));
    }

    // ---- small views -------------------------------------------------------------------------------------

    private void heading(int res, boolean gap) {
        TextView h = text(getString(res), 11, 0xB3FFFFFF, medium);
        h.setAllCaps(true);
        h.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) ((gap ? 16 : 0) * d);
        lp.bottomMargin = (int) (6 * d);
        box.addView(h, lp);
    }

    private TextView text(String s, float sp, int color, Typeface font) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(font);
        t.setIncludeFontPadding(false);
        t.setPadding(0, (int) (2 * d), 0, (int) (2 * d));
        return t;
    }

    /** Two big numbers side by side, each with a small label under it. */
    private View pair(String a, String aLabel, String b, String bLabel) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String[] cell : new String[][]{{a, aLabel}, {b, bLabel}}) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(text(cell[0], 22, 0xFFFFFFFF, light));
            if (!cell[1].isEmpty()) col.addView(text(cell[1], 11, 0xB3FFFFFF, medium));
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (6 * d);
        row.setLayoutParams(lp);
        return row;
    }

    /** A thin rounded bar filled to {@code f}. */
    private View bar(float f, int color) {
        LinearLayout track = new LinearLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x33FFFFFF);
        bg.setCornerRadius(4 * d);
        track.setBackground(bg);
        View fill = new View(this);
        GradientDrawable fg = new GradientDrawable();
        fg.setColor(color);
        fg.setCornerRadius(4 * d);
        fill.setBackground(fg);
        track.addView(fill, new LinearLayout.LayoutParams(0, (int) (8 * d), Math.max(0.01f, f)));
        track.addView(new View(this), new LinearLayout.LayoutParams(0, (int) (8 * d), Math.max(0.01f, 1 - f)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (8 * d));
        lp.bottomMargin = (int) (8 * d);
        track.setLayoutParams(lp);
        return track;
    }

    /** Seven bars for the last seven days' data, today last. */
    private View weekBars(long[] week) {
        long max = 1;
        for (long w : week) max = Math.max(max, w);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        int h = (int) (36 * d);
        for (int i = 0; i < week.length; i++) {
            View b = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setColor(i == week.length - 1 ? 0xFF4FC3F7 : 0x994FC3F7);
            g.setCornerRadius(2 * d);
            b.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.max((int) (2 * d), (int) (h * week[i] / (double) max)), 1f);
            lp.setMarginEnd((int) (4 * d));
            row.addView(b, lp);
        }
        TextView label = text(getString(R.string.stats_last_week), 10, 0x99FFFFFF, medium);
        label.setPadding((int) (8 * d), 0, 0, 0);
        row.addView(label);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        return row;
    }
}
