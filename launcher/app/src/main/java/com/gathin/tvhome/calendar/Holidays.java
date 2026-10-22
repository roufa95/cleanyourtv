package com.gathin.tvhome.calendar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Public holidays and school holidays for the weather card's footer, from the OpenHolidays API
 * (openholidaysapi.org: free, no key). Two lists, read into one: what is on now or comes next.
 */
public final class Holidays {

    /** A holiday further away than this is not worth a line on Home. */
    static final int HORIZON_DAYS = 60;

    public static final class Holiday {
        public final String name;
        public final LocalDate start, end;
        public final boolean school;

        Holiday(String name, LocalDate start, LocalDate end, boolean school) {
            this.name = name;
            this.start = start;
            this.end = end;
            this.school = school;
        }
    }

    /** What the footer says at one moment: a holiday under way (until {@link Holiday#end}) or the next one. */
    public static final class Next {
        public final Holiday holiday;
        /** True while it is on; then the footer counts down to the end instead of the start. */
        public final boolean now;
        /** Days until it starts (0 today, 1 tomorrow); 0 while it is on. */
        public final long inDays;

        Next(Holiday holiday, boolean now, long inDays) {
            this.holiday = holiday;
            this.now = now;
            this.inDays = inDays;
        }
    }

    private Holidays() {
    }

    public static String publicUrl(String country, LocalDate from) {
        return url("PublicHolidays", country, from);
    }

    public static String schoolUrl(String country, LocalDate from) {
        return url("SchoolHolidays", country, from);
    }

    /** One year from the first of {@code from}'s month, so the URL (and with it the cache) changes once a month. */
    private static String url(String kind, String country, LocalDate from) {
        LocalDate start = from.withDayOfMonth(1);
        return "https://openholidaysapi.org/" + kind + "?countryIsoCode=" + country + "&languageIsoCode=EN"
                + "&validFrom=" + start + "&validTo=" + start.plusYears(1);
    }

    /** OpenHolidays' list: [{"startDate": "2026-10-31", "endDate": "2026-11-08", "name": [{"text": ...}]}, ...]. */
    public static List<Holiday> parse(String json, boolean school) {
        List<Holiday> out = new ArrayList<>();
        if (json == null) return out;
        try {
            JSONArray list = new JSONArray(json);
            for (int i = 0; i < list.length(); i++) {
                JSONObject h = list.getJSONObject(i);
                JSONArray names = h.optJSONArray("name");
                String name = names != null && names.length() > 0 ? names.getJSONObject(0).optString("text", "") : "";
                if (name.isEmpty()) continue;
                LocalDate start = LocalDate.parse(h.getString("startDate"));
                LocalDate end = LocalDate.parse(h.optString("endDate", h.getString("startDate")));
                out.add(new Holiday(name.trim(), start, end, school));
            }
        } catch (JSONException | RuntimeException e) {
            return Collections.emptyList();
        }
        return out;
    }

    /**
     * The school holiday under way, or else the holiday that starts first within {@link #HORIZON_DAYS}. A public
     * holiday on its own day counts as under way. A public holiday inside a school holiday that is on gives way to it.
     */
    public static Next at(List<Holiday> holidays, LocalDate today) {
        Holiday onNow = null, next = null;
        for (Holiday h : holidays) {
            boolean on = !today.isBefore(h.start) && !today.isAfter(h.end);
            if (on) {
                if (onNow == null || (h.school && !onNow.school)) onNow = h;
            } else if (h.start.isAfter(today) && !h.start.isAfter(today.plusDays(HORIZON_DAYS))) {
                if (next == null || h.start.isBefore(next.start)) next = h;
            }
        }
        if (onNow != null) return new Next(onNow, true, 0);
        if (next != null) return new Next(next, false, java.time.temporal.ChronoUnit.DAYS.between(today, next.start));
        return null;
    }
}
