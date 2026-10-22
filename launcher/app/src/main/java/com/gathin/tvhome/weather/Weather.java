package com.gathin.tvhome.weather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The weather card's forecast: Open-Meteo's hourly and daily JSON for one place, read into what the card shows
 * at a given moment. The current conditions come from the hour we are in rather than Open-Meteo's "current"
 * block, so a forecast downloaded a few hours ago still shows the right hour. No Android types beyond org.json.
 */
public final class Weather {

    /** The card shows this many hours after the current one... */
    static final int HOURS = 5;
    /** ...and this many days after today. */
    public static final int DAYS = 4;
    /** The rain alert looks this many hours ahead, for an hour at least this likely to be wet. */
    static final int ALERT_HOURS = 6, ALERT_PERCENT = 60;

    public enum Sky { CLEAR, MAINLY_CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SHOWERS, SNOW, THUNDER }

    public static final class Hour {
        public final LocalDateTime time;
        public final int temp;
        public final Sky sky;
        public final boolean day;
        /** Chance of rain or snow in percent; -1 when the forecast has none. */
        public final int precip;

        Hour(LocalDateTime time, int temp, Sky sky, boolean day, int precip) {
            this.time = time;
            this.temp = temp;
            this.sky = sky;
            this.day = day;
            this.precip = precip;
        }
    }

    public static final class Day {
        public final LocalDate date;
        public final Sky sky;
        public final int high, low;
        /** Highest chance of rain or snow that day in percent; -1 when the forecast has none. */
        public final int precip;

        Day(LocalDate date, Sky sky, int high, int low, int precip) {
            this.date = date;
            this.sky = sky;
            this.high = high;
            this.low = low;
            this.precip = precip;
        }
    }

    public static final class Now {
        public final Hour current;
        public final int high, low;
        /** The next {@link #HOURS} hours, in order. */
        public final List<Hour> next;
        /** Up to {@link #DAYS} days after today, in order. */
        public final List<Day> days;
        /** The first wet hour coming up while it is dry now (see {@link #ALERT_HOURS}), or null. */
        public final Hour rain;

        Now(Hour current, int high, int low, List<Hour> next, List<Day> days, Hour rain) {
            this.current = current;
            this.high = high;
            this.low = low;
            this.next = Collections.unmodifiableList(next);
            this.days = Collections.unmodifiableList(days);
            this.rain = rain;
        }
    }

    private Weather() {
    }

    public static String url(String latitude, String longitude) {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + latitude + "&longitude=" + longitude
                + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&timezone=auto&forecast_days=" + (DAYS + 1);
    }

    /** WMO weather interpretation codes, as Open-Meteo documents them. */
    static Sky sky(int code) {
        if (code == 0) return Sky.CLEAR;
        if (code == 1) return Sky.MAINLY_CLEAR;
        if (code == 2) return Sky.PARTLY_CLOUDY;
        if (code == 3) return Sky.CLOUDY;
        if (code == 45 || code == 48) return Sky.FOG;
        if (code >= 51 && code <= 57) return Sky.DRIZZLE;
        if ((code >= 61 && code <= 67)) return Sky.RAIN;
        if (code >= 80 && code <= 82) return Sky.SHOWERS;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Sky.SNOW;
        if (code >= 95) return Sky.THUNDER;
        return Sky.CLOUDY;
    }

    /** What the card shows at {@code now} (local time of the forecast), or null when the forecast does not reach it. */
    public static Now at(String json, LocalDateTime now) {
        try {
            JSONObject o = new JSONObject(json);
            JSONObject h = o.getJSONObject("hourly");
            JSONArray times = h.getJSONArray("time");
            JSONArray temps = h.getJSONArray("temperature_2m");
            JSONArray codes = h.getJSONArray("weather_code");
            JSONArray precip = h.optJSONArray("precipitation_probability");
            JSONArray isDay = h.optJSONArray("is_day");
            LocalDateTime hour = now.withMinute(0).withSecond(0).withNano(0);
            int at = -1;
            for (int i = 0; i < times.length(); i++) {
                if (LocalDateTime.parse(times.getString(i)).equals(hour)) {
                    at = i;
                    break;
                }
            }
            if (at < 0 || temps.isNull(at)) return null;
            List<Hour> next = new ArrayList<>();
            for (int i = at + 1; i < times.length() && next.size() < HOURS; i++) {
                if (temps.isNull(i)) break;
                next.add(hour(times, temps, codes, precip, isDay, i));
            }

            JSONObject d = o.getJSONObject("daily");
            JSONArray days = d.getJSONArray("time");
            JSONArray max = d.getJSONArray("temperature_2m_max");
            JSONArray min = d.getJSONArray("temperature_2m_min");
            JSONArray dayCodes = d.optJSONArray("weather_code");
            JSONArray dayPrecip = d.optJSONArray("precipitation_probability_max");
            int high = Integer.MIN_VALUE, low = Integer.MIN_VALUE;
            List<Day> later = new ArrayList<>();
            for (int i = 0; i < days.length(); i++) {
                LocalDate date = LocalDate.parse(days.getString(i));
                if (max.isNull(i) || min.isNull(i)) continue;
                if (date.equals(now.toLocalDate())) {
                    high = (int) Math.round(max.getDouble(i));
                    low = (int) Math.round(min.getDouble(i));
                } else if (date.isAfter(now.toLocalDate()) && later.size() < DAYS) {
                    later.add(new Day(date, sky(dayCodes == null ? 3 : dayCodes.optInt(i, 3)),
                            (int) Math.round(max.getDouble(i)), (int) Math.round(min.getDouble(i)),
                            dayPrecip == null || dayPrecip.isNull(i) ? -1 : dayPrecip.getInt(i)));
                }
            }
            if (high == Integer.MIN_VALUE) return null;
            Hour current = hour(times, temps, codes, precip, isDay, at);
            Hour rain = null;
            if (!wet(current.sky)) {
                for (int i = at + 1; i <= at + ALERT_HOURS && i < times.length() && !temps.isNull(i); i++) {
                    Hour ahead = hour(times, temps, codes, precip, isDay, i);
                    if (wet(ahead.sky) && ahead.precip >= ALERT_PERCENT) {
                        rain = ahead;
                        break;
                    }
                }
            }
            return new Now(current, high, low, next, later, rain);
        } catch (JSONException | RuntimeException e) {
            return null;
        }
    }

    private static Hour hour(JSONArray times, JSONArray temps, JSONArray codes, JSONArray precip, JSONArray isDay, int i)
            throws JSONException {
        LocalDateTime t = LocalDateTime.parse(times.getString(i));
        boolean day = isDay == null || isDay.isNull(i) ? t.getHour() >= 7 && t.getHour() < 19 : isDay.getInt(i) == 1;
        int p = precip == null || precip.isNull(i) ? -1 : precip.getInt(i);
        return new Hour(t, (int) Math.round(temps.getDouble(i)), sky(codes.optInt(i, 3)), day, p);
    }

    /** Rain, drizzle, showers, snow or thunder. */
    static boolean wet(Sky sky) {
        return sky == Sky.DRIZZLE || sky == Sky.RAIN || sky == Sky.SHOWERS || sky == Sky.SNOW || sky == Sky.THUNDER;
    }

    /** "8°", "-3°". */
    public static String degrees(int t) {
        return String.format(Locale.ROOT, "%d°", t);
    }
}
