package com.gathin.tvhome.calendar;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Locale;

/**
 * Luxembourg's holiday periods for Home (config.xml season): a greeting, a focus-glow colour and a short effect
 * the first time Home shows each day. It changes by date on its own; a theme can force one ("season":
 * "christmas") or switch it off ("season": "off"). Pure date maths, so it is tested on the JVM.
 */
public enum Season {
    HALLOWEEN(0xFFFFA040, Effect.BATS),
    CHRISTMAS(0xFFFFD54F, Effect.SNOW),
    NEW_YEAR(0xFFFFD54F, Effect.SNOW),
    CARNIVAL(0xFFE040FB, Effect.CONFETTI),
    RAMADAN(0xFFE9C46A, Effect.LANTERNS),
    EID(0xFF81C784, Effect.LANTERNS);

    public enum Effect { SNOW, BATS, CONFETTI, LANTERNS }

    public final int accent;
    public final Effect effect;

    Season(int accent, Effect effect) {
        this.accent = accent;
        this.effect = effect;
    }

    /** The season for {@code date}, or null outside every holiday period. */
    static Season at(LocalDate date) {
        // Ramadan and the two Eids follow the Islamic calendar (Umm al-Qura, as java.time ships it; a locally
        // announced start can differ by a day). They come first: Ramadan can overlap carnival week.
        Season islamic = islamic(date);
        if (islamic != null) return islamic;
        MonthDay d = MonthDay.from(date);
        if (between(d, 10, 15, 11, 2)) return HALLOWEEN;   // the second half of October, through All Souls
        if (between(d, 12, 1, 12, 30)) return CHRISTMAS;
        if (d.equals(MonthDay.of(12, 31)) || between(d, 1, 1, 1, 6)) return NEW_YEAR;
        LocalDate easter = easter(date.getYear());
        LocalDate ashWednesday = easter.minusDays(46);
        // Fuesend: from Fetten Donneschdeg (the Thursday before Ash Wednesday) to Shrove Tuesday.
        if (!date.isBefore(ashWednesday.minusDays(6)) && date.isBefore(ashWednesday)) return CARNIVAL;
        return null;
    }

    /**
     * The season Home shows: {@code setting} "auto" (or empty) follows the date, "off" shows none, and a season's
     * name ("christmas", "new_year") forces it, which is also how to look at one outside its dates.
     */
    public static Season pick(String setting, LocalDate date) {
        String s = setting == null ? "" : setting.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("auto")) return at(date);
        if (s.equals("off")) return null;
        for (Season season : values()) if (season.name().toLowerCase(Locale.ROOT).equals(s)) return season;
        return at(date);
    }

    /** Ramadan (month 9), Eid al-Fitr (1 to 3 Shawwal) and Eid al-Adha (10 to 13 Dhu al-Hijjah), or null. */
    static Season islamic(LocalDate date) {
        try {
            java.time.chrono.HijrahDate h = java.time.chrono.HijrahDate.from(date);
            int month = h.get(java.time.temporal.ChronoField.MONTH_OF_YEAR);
            int day = h.get(java.time.temporal.ChronoField.DAY_OF_MONTH);
            if (month == 9) return RAMADAN;
            if (month == 10 && day <= 3) return EID;
            if (month == 12 && day >= 10 && day <= 13) return EID;
        } catch (java.time.DateTimeException e) {
            // Outside the calendar's range: no Islamic season.
        }
        return null;
    }

    private static boolean between(MonthDay d, int m1, int d1, int m2, int d2) {
        return !d.isBefore(MonthDay.of(m1, d1)) && !d.isAfter(MonthDay.of(m2, d2));
    }

    /** Easter Sunday (Gregorian), by the anonymous Meeus/Jones/Butcher algorithm. */
    static LocalDate easter(int year) {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }
}
