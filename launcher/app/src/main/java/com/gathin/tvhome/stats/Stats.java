package com.gathin.tvhome.stats;

import java.util.List;
import java.util.Locale;

/**
 * The Stats panel's arithmetic (see StatsActivity): screen-on time from Android's screen on/off events, the
 * energy that time used at the TV's rated power, what it cost, and sizes in readable units. The TV has no power
 * meter, so energy is an estimate: screen-on time times {@code config.xml tv_watts}. No Android types here, so it
 * is tested on the JVM.
 */
public final class Stats {

    private Stats() {
    }

    /**
     * Milliseconds the screen was on between {@code start} and {@code end}. {@code events} are {time, on} pairs in
     * time order (on = 1 for "screen on", 0 for "off"); {@code onAtStart} says whether it was on before the first
     * event, and a session still running at {@code end} counts up to {@code end}.
     */
    public static long screenOn(List<long[]> events, long start, long end, boolean onAtStart) {
        long total = 0, since = onAtStart ? start : -1;
        for (long[] e : events) {
            long t = Math.max(start, Math.min(end, e[0]));
            if (e[1] == 1) {
                if (since < 0) since = t;
            } else if (since >= 0) {
                total += t - since;
                since = -1;
            }
        }
        if (since >= 0) total += end - since;
        return Math.max(0, total);
    }

    /** kWh used by a TV drawing {@code watts} for {@code ms} milliseconds. */
    public static double kwh(long ms, int watts) {
        return watts * (ms / 3_600_000.0) / 1000.0;
    }

    /** "4h 12m", "35m". */
    public static String duration(long ms) {
        long minutes = ms / 60_000;
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }

    /** "0.42 kWh", "12.6 kWh". */
    public static String energy(double kwh) {
        return String.format(Locale.ROOT, kwh < 10 ? "%.2f kWh" : "%.1f kWh", kwh);
    }

    /** "€0.09" with the configured currency before the amount. */
    public static String money(double amount, String currency) {
        return String.format(Locale.ROOT, "%s%.2f", currency, amount);
    }

    /** "3.4 GB", "820 MB", "12 KB". */
    public static String bytes(long b) {
        if (b >= 1L << 30) return String.format(Locale.ROOT, b >= 10L << 30 ? "%.0f GB" : "%.1f GB", b / (double) (1L << 30));
        if (b >= 1L << 20) return String.format(Locale.ROOT, "%.0f MB", b / (double) (1L << 20));
        return String.format(Locale.ROOT, "%.0f KB", b / 1024.0);
    }

    /** config.xml energy_price as a number, 0 when it is not one. */
    public static double price(String s) {
        try {
            double p = Double.parseDouble(s.trim().replace(',', '.'));
            return p >= 0 && p < 100 ? p : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
