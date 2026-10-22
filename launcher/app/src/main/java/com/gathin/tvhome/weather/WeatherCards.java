package com.gathin.tvhome.weather;

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
import com.gathin.tvhome.calendar.Holidays;
import com.gathin.tvhome.cards.Feed;
import com.gathin.tvhome.util.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The weather cards on Home (config.xml weather): today with the next hours, and the next days with the holidays. */
public final class WeatherCards {

    /** Hours shown on the today card; the forecast carries {@link Weather#HOURS}. */
    private static final int SHOWN_HOURS = 3;

    private final Activity activity;
    private final float density;
    private final Config.WeatherCard weatherCfg;
    private final Feed weatherFeed = new Feed("weather", 90L * 60 * 1000);
    private final Feed publicHolidayFeed = new Feed("public_holidays", 7L * 24 * 60 * 60 * 1000);
    private final Feed schoolHolidayFeed = new Feed("school_holidays", 7L * 24 * 60 * 60 * 1000);
    private String weatherJson;
    private List<Holidays.Holiday> holidays = new ArrayList<>();
    private final View weatherCard;
    private final TextView weatherPlace, weatherTemp, weatherSky, weatherHighLow, weatherFooter;
    private final LinearLayout weatherHours, forecastDays;
    private final View forecastCard;
    private final TextView forecastFooter;
    private Weather.Sky weatherBackdrop;
    private Boolean weatherBackdropDay;
    private boolean weatherRainAlert;

    public WeatherCards(Activity activity, Config.WeatherCard weatherCfg) {
        this.activity = activity;
        this.weatherCfg = weatherCfg;
        density = activity.getResources().getDisplayMetrics().density;
        weatherCard = activity.findViewById(R.id.weatherCard);
        weatherPlace = activity.findViewById(R.id.weatherPlace);
        weatherTemp = activity.findViewById(R.id.weatherTemp);
        weatherSky = activity.findViewById(R.id.weatherSky);
        weatherHighLow = activity.findViewById(R.id.weatherHighLow);
        weatherFooter = activity.findViewById(R.id.weatherFooter);
        weatherHours = activity.findViewById(R.id.weatherHours);
        forecastCard = activity.findViewById(R.id.forecastCard);
        forecastDays = activity.findViewById(R.id.forecastDays);
        forecastFooter = activity.findViewById(R.id.forecastFooter);
        ((TextView) activity.findViewById(R.id.forecastKicker)).setText(activity.getString(R.string.forecast_title, Weather.DAYS));
        weatherPlace.setText(weatherCfg.place);
        // One column per hour: time, icon, temperature, and the chance of rain when it is worth showing.
        for (int i = 0; i < SHOWN_HOURS; i++) {
            LinearLayout cell = new LinearLayout(activity);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setLayoutParams(new LinearLayout.LayoutParams((int) (31 * density), ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.addView(weatherText(11f, 0xB3FFFFFF));
            cell.addView(new android.widget.ImageView(activity), weatherIconParams(20, 4));
            cell.addView(weatherText(13f, 0xFFFFFFFF));
            cell.addView(weatherText(10f, 0xFF7CC4FF));
            weatherHours.addView(cell);
        }
        // One row per day: day, icon, chance of rain, then the high and the low on the far side.
        for (int i = 0; i < Weather.DAYS; i++) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView day = weatherText(13f, 0xD9FFFFFF);
            row.addView(day, new LinearLayout.LayoutParams((int) (44 * density), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(new android.widget.ImageView(activity), weatherIconParams(18, 0));
            TextView rain = weatherText(10f, 0xFF7CC4FF);
            rain.setPadding((int) (6 * density), 0, 0, 0);
            row.addView(rain, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView high = weatherText(13f, 0xFFFFFFFF);
            high.setGravity(Gravity.END);
            row.addView(high, new LinearLayout.LayoutParams((int) (34 * density), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView low = weatherText(13f, 0x8CFFFFFF);
            low.setGravity(Gravity.END);
            row.addView(low, new LinearLayout.LayoutParams((int) (34 * density), ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (3 * density);
            forecastDays.addView(row, lp);
        }
        weatherJson = weatherFeed.cached(activity, Weather.url(weatherCfg.latitude, weatherCfg.longitude));
        loadHolidays();
    }

    private LinearLayout.LayoutParams weatherIconParams(int dp, int marginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (dp * density), (int) (dp * density));
        lp.topMargin = (int) (marginDp * density);
        lp.bottomMargin = (int) (marginDp * density);
        return lp;
    }

    private TextView weatherText(float sp, int color) {
        TextView t = new TextView(activity);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        t.setTypeface(weatherPlace.getTypeface());
        return t;
    }

    private void loadHolidays() {
        if (weatherCfg.holidaysCountry.isEmpty()) return;
        java.time.LocalDate today = java.time.LocalDate.now();
        List<Holidays.Holiday> all = new ArrayList<>(Holidays.parse(publicHolidayFeed.cached(activity,
                Holidays.publicUrl(weatherCfg.holidaysCountry, today)), false));
        all.addAll(Holidays.parse(schoolHolidayFeed.cached(activity,
                Holidays.schoolUrl(weatherCfg.holidaysCountry, today)), true));
        holidays = all;
    }

    /** Overdue downloads only; a few preference reads otherwise, so it can run every minute. */
    public void refreshFeeds() {
        weatherFeed.refreshIfDue(activity, Weather.url(weatherCfg.latitude, weatherCfg.longitude), body -> {
            weatherJson = body;
            update();
        });
        if (weatherCfg.holidaysCountry.isEmpty()) return;
        java.time.LocalDate today = java.time.LocalDate.now();
        publicHolidayFeed.refreshIfDue(activity, Holidays.publicUrl(weatherCfg.holidaysCountry, today), body -> {
            loadHolidays();
            update();
        });
        schoolHolidayFeed.refreshIfDue(activity, Holidays.schoolUrl(weatherCfg.holidaysCountry, today), body -> {
            loadHolidays();
            update();
        });
    }

    /** Fills both weather cards for this minute, or hides them while there is no forecast for the current hour. */
    public void update() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        Weather.Now w = weatherJson == null ? null : Weather.at(weatherJson, now);
        if (w == null) {
            weatherCard.setVisibility(View.GONE);
            forecastCard.setVisibility(View.GONE);
            return;
        }
        weatherTemp.setText(Weather.degrees(w.current.temp));
        android.text.SpannableStringBuilder sky = new android.text.SpannableStringBuilder();
        appendIcon(sky, skyIcon(w.current.sky, w.current.day), weatherSky.getTextSize() * 1.5f);
        sky.append(' ').append(skyName(activity, w.current.sky));
        weatherSky.setText(sky);
        weatherHighLow.setText(activity.getString(R.string.weather_high_low, Weather.degrees(w.high), Weather.degrees(w.low)));

        java.time.format.DateTimeFormatter hourFormat = java.time.format.DateTimeFormatter.ofPattern(
                android.text.format.DateFormat.is24HourFormat(activity) ? "HH" : "ha", Locale.getDefault());
        for (int i = 0; i < weatherHours.getChildCount(); i++) {
            LinearLayout cell = (LinearLayout) weatherHours.getChildAt(i);
            if (i >= w.next.size()) {
                cell.setVisibility(View.INVISIBLE);
                continue;
            }
            Weather.Hour h = w.next.get(i);
            cell.setVisibility(View.VISIBLE);
            ((TextView) cell.getChildAt(0)).setText(hourFormat.format(h.time).toLowerCase(Locale.getDefault()));
            ((android.widget.ImageView) cell.getChildAt(1)).setImageResource(skyIcon(h.sky, h.day));
            ((TextView) cell.getChildAt(2)).setText(Weather.degrees(h.temp));
            // Like the iPad widget: the chance of rain only when it is likely enough to matter.
            ((TextView) cell.getChildAt(3)).setText(h.precip >= 30 ? h.precip + "%" : "");
        }
        // A rain alert takes the footer's place (and turns the outline blue) while it is dry and rain is coming.
        if (w.rain != null) {
            android.text.SpannableStringBuilder alert = new android.text.SpannableStringBuilder();
            appendIcon(alert, skyIcon(w.rain.sky, w.rain.day), weatherFooter.getTextSize() * 1.6f);
            String at = java.time.format.DateTimeFormatter.ofPattern(
                    android.text.format.DateFormat.is24HourFormat(activity) ? "HH:mm" : "h a", Locale.getDefault()).format(w.rain.time);
            alert.append(' ').append(activity.getString(w.rain.sky == Weather.Sky.SNOW ? R.string.weather_snow_from
                    : R.string.weather_rain_from, at));
            weatherFooter.setText(alert);
            weatherFooter.setTextColor(0xFFBFE1FF);
        } else {
            weatherFooter.setText(sunText(now.toLocalDate()));
            weatherFooter.setTextColor(0xD9FFFFFF);
        }

        java.time.format.DateTimeFormatter dayFormat = java.time.format.DateTimeFormatter.ofPattern("EEE", Locale.getDefault());
        for (int i = 0; i < forecastDays.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) forecastDays.getChildAt(i);
            if (i >= w.days.size()) {
                row.setVisibility(View.INVISIBLE);
                continue;
            }
            Weather.Day d = w.days.get(i);
            row.setVisibility(View.VISIBLE);
            ((TextView) row.getChildAt(0)).setText(dayFormat.format(d.date));
            ((android.widget.ImageView) row.getChildAt(1)).setImageResource(skyIcon(d.sky, true));
            ((TextView) row.getChildAt(2)).setText(d.precip >= 30 ? d.precip + "%" : "");
            ((TextView) row.getChildAt(3)).setText(Weather.degrees(d.high));
            ((TextView) row.getChildAt(4)).setText(Weather.degrees(d.low));
        }
        CharSequence holiday = holidayText(now.toLocalDate());
        forecastFooter.setText(holiday);
        forecastFooter.setVisibility(holiday.length() == 0 ? View.INVISIBLE : View.VISIBLE);

        setWeatherBackdrop(w.current.sky, w.current.day, w.rain != null);
        weatherCard.setVisibility(View.VISIBLE);
        forecastCard.setVisibility(View.VISIBLE);
    }

    /** Sunrise and sunset, computed here (no network). */
    private CharSequence sunText(java.time.LocalDate today) {
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder();
        int[] sun = Sun.riseAndSet(today, Double.parseDouble(weatherCfg.latitude),
                Double.parseDouble(weatherCfg.longitude), java.time.ZoneId.systemDefault());
        if (sun == null) return b;
        float icon = weatherFooter.getTextSize() * 1.6f;
        java.time.format.DateTimeFormatter t = java.time.format.DateTimeFormatter.ofPattern(
                android.text.format.DateFormat.is24HourFormat(activity) ? "HH:mm" : "h:mm a", Locale.getDefault());
        appendIcon(b, R.drawable.ic_wx_sunrise, icon);
        b.append(' ').append(t.format(java.time.LocalTime.MIN.plusMinutes(Math.floorMod(sun[0], 1440)))).append("     ");
        appendIcon(b, R.drawable.ic_wx_sunset, icon);
        b.append(' ').append(t.format(java.time.LocalTime.MIN.plusMinutes(Math.floorMod(sun[1], 1440))));
        return b;
    }

    /** The holiday under way or coming up, or nothing. */
    private CharSequence holidayText(java.time.LocalDate today) {
        Holidays.Next next = Holidays.at(holidays, today);
        if (next == null) return "";
        String name = next.holiday.name;
        if (next.now) {
            return !next.holiday.school || next.holiday.end.equals(today) ? name
                    : activity.getString(R.string.holiday_until, name, java.time.format.DateTimeFormatter
                            .ofPattern("EEE d MMM", Locale.getDefault()).format(next.holiday.end));
        }
        if (next.inDays == 1) return activity.getString(R.string.holiday_tomorrow, name);
        return activity.getString(R.string.holiday_in, name, (int) next.inDays);
    }

    private void appendIcon(android.text.SpannableStringBuilder b, int res, float size) {
        b.append('￼');
        b.setSpan(weatherIconSpan(res, size), b.length() - 1, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Both cards' gradient follows the sky, in deep tones that sit on a dark Home: navy at night, blue by day. */
    private void setWeatherBackdrop(Weather.Sky sky, boolean day, boolean rainAlert) {
        if (sky == weatherBackdrop && Boolean.valueOf(day).equals(weatherBackdropDay) && rainAlert == weatherRainAlert) return;
        weatherBackdrop = sky;
        weatherBackdropDay = day;
        weatherRainAlert = rainAlert;
        int top, bottom;
        switch (sky) {
            case CLEAR:
            case MAINLY_CLEAR:
                top = day ? 0xF22F6FB8 : 0xF2142046;
                bottom = day ? 0xF2183A6E : 0xF20A0F24;
                break;
            case PARTLY_CLOUDY:
                top = day ? 0xF2356089 : 0xF21B2547;
                bottom = day ? 0xF21C3550 : 0xF20D1226;
                break;
            case RAIN:
            case DRIZZLE:
            case SHOWERS:
                top = 0xF2324456;
                bottom = 0xF2192430;
                break;
            case THUNDER:
                top = 0xF2332B4A;
                bottom = 0xF2161325;
                break;
            case SNOW:
                top = 0xF2465468;
                bottom = 0xF2222A36;
                break;
            default:   // cloudy, fog
                top = day ? 0xF2465565 : 0xF2252C38;
                bottom = day ? 0xF2252D38 : 0xF2121519;
        }
        for (View card : new View[]{weatherCard, forecastCard}) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
            g.setCornerRadius(20 * density);
            // The today card's outline turns blue under a rain alert; the next-days card keeps the quiet one.
            boolean alert = rainAlert && card == weatherCard;
            g.setStroke(Math.max(1, (int) (alert ? 1.5f * density : density)), alert ? 0xFF42A5F5 : 0x38FFFFFF);
            card.setBackground(g);
        }
    }

    public static int skyIcon(Weather.Sky sky, boolean day) {
        switch (sky) {
            case CLEAR: return day ? R.drawable.ic_wx_sun : R.drawable.ic_wx_moon;
            case MAINLY_CLEAR:
            case PARTLY_CLOUDY: return day ? R.drawable.ic_wx_sun_cloud : R.drawable.ic_wx_moon_cloud;
            case FOG: return R.drawable.ic_wx_fog;
            case DRIZZLE: return R.drawable.ic_wx_drizzle;
            case RAIN: return R.drawable.ic_wx_rain;
            case SHOWERS: return day ? R.drawable.ic_wx_showers : R.drawable.ic_wx_rain;
            case SNOW: return R.drawable.ic_wx_snow;
            case THUNDER: return R.drawable.ic_wx_thunder;
            default: return R.drawable.ic_wx_cloud;
        }
    }

    public static String skyName(Context c, Weather.Sky sky) {
        switch (sky) {
            case CLEAR: return c.getString(R.string.weather_clear);
            case MAINLY_CLEAR: return c.getString(R.string.weather_mainly_clear);
            case PARTLY_CLOUDY: return c.getString(R.string.weather_partly_cloudy);
            case FOG: return c.getString(R.string.weather_fog);
            case DRIZZLE: return c.getString(R.string.weather_drizzle);
            case RAIN: return c.getString(R.string.weather_rain);
            case SHOWERS: return c.getString(R.string.weather_showers);
            case SNOW: return c.getString(R.string.weather_snow);
            case THUNDER: return c.getString(R.string.weather_thunder);
            default: return c.getString(R.string.weather_cloudy);
        }
    }

    /** A coloured weather icon as a text span, {@code size} pixels square. */
    private android.text.style.ImageSpan weatherIconSpan(int res, float size) {
        android.graphics.drawable.Drawable d = activity.getDrawable(res).mutate();
        d.setBounds(0, 0, Math.round(size), Math.round(size));
        return new android.text.style.ImageSpan(d, Build.VERSION.SDK_INT >= 29
                ? android.text.style.DynamicDrawableSpan.ALIGN_CENTER : android.text.style.DynamicDrawableSpan.ALIGN_BASELINE);
    }
}
