package com.gathin.tvhome.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;

import org.junit.Test;

public class WeatherTest {

    // Open-Meteo's shape, cut down to eight hours of one evening and one day.
    private static final String JSON = "{\"hourly\":{"
            + "\"time\":[\"2026-10-21T19:00\",\"2026-10-21T20:00\",\"2026-10-21T21:00\",\"2026-10-21T22:00\","
            + "\"2026-10-21T23:00\",\"2026-10-22T00:00\",\"2026-10-22T01:00\",\"2026-10-22T02:00\"],"
            + "\"temperature_2m\":[10.4,9.6,8.5,7.9,7.2,6.6,6.1,5.5],"
            + "\"weather_code\":[2,1,0,0,3,45,61,95],"
            + "\"precipitation_probability\":[0,0,5,10,20,30,65,80],"
            + "\"is_day\":[1,0,0,0,0,0,0,0]},"
            + "\"daily\":{\"time\":[\"2026-10-21\",\"2026-10-22\",\"2026-10-23\",\"2026-10-24\",\"2026-10-25\",\"2026-10-26\"],"
            + "\"weather_code\":[1,61,3,0,71,2],\"precipitation_probability_max\":[10,80,20,0,60,5],"
            + "\"temperature_2m_max\":[14.6,12.2,11.4,13.0,2.6,9.9],\"temperature_2m_min\":[5.8,4.1,3.5,2.2,-1.4,1.0]}}";

    @Test
    public void readsTheHourWeAreIn() {
        Weather.Now w = Weather.at(JSON, LocalDateTime.of(2026, 10, 21, 20, 47));
        assertEquals(10, w.current.temp);
        assertEquals(Weather.Sky.MAINLY_CLEAR, w.current.sky);
        assertFalse(w.current.day);
        assertEquals(15, w.high);
        assertEquals(6, w.low);
    }

    @Test
    public void nextHoursFollowInOrderAndStopAtTheEnd() {
        Weather.Now w = Weather.at(JSON, LocalDateTime.of(2026, 10, 21, 20, 0));
        assertEquals(Weather.HOURS, w.next.size());
        assertEquals(21, w.next.get(0).time.getHour());
        assertEquals(Weather.Sky.CLEAR, w.next.get(0).sky);
        assertEquals(Weather.Sky.FOG, w.next.get(3).sky);
        assertEquals(65, w.next.get(4).precip);

        Weather.Now late = Weather.at(JSON, LocalDateTime.of(2026, 10, 21, 23, 30));
        assertEquals(3, late.next.size());
        assertEquals(Weather.Sky.THUNDER, late.next.get(2).sky);
    }

    @Test
    public void theNextFourDaysAfterToday() {
        Weather.Now w = Weather.at(JSON, LocalDateTime.of(2026, 10, 21, 20, 0));
        assertEquals(Weather.DAYS, w.days.size());
        assertEquals(22, w.days.get(0).date.getDayOfMonth());
        assertEquals(Weather.Sky.RAIN, w.days.get(0).sky);
        assertEquals(80, w.days.get(0).precip);
        assertEquals(Weather.Sky.SNOW, w.days.get(3).sky);
        assertEquals(3, w.days.get(3).high);
        assertEquals(-1, w.days.get(3).low);
    }

    @Test
    public void rainAlertOnlyWhileDryAndLikely() {
        // Dry at 20:00; 01:00 is rain at 65% and the first wet hour likely enough.
        Weather.Now w = Weather.at(JSON, LocalDateTime.of(2026, 10, 21, 20, 0));
        assertEquals(1, w.rain.time.getHour());
        assertEquals(Weather.Sky.RAIN, w.rain.sky);
        // Already raining at 01:00: no alert.
        assertNull(Weather.at(JSON, LocalDateTime.of(2026, 10, 22, 1, 0)).rain);
        // From 19:00, 01:00 is six hours away: the last hour the alert still looks at.
        assertEquals(1, Weather.at(JSON, LocalDateTime.of(2026, 10, 21, 19, 0)).rain.time.getHour());
    }

    @Test
    public void nothingOutsideTheForecast() {
        assertNull(Weather.at(JSON, LocalDateTime.of(2026, 10, 23, 12, 0)));
        assertNull(Weather.at(JSON, LocalDateTime.of(2026, 10, 21, 18, 59)));
        assertNull(Weather.at("not json", LocalDateTime.of(2026, 10, 21, 20, 0)));
    }

    @Test
    public void wmoCodes() {
        assertEquals(Weather.Sky.DRIZZLE, Weather.sky(53));
        assertEquals(Weather.Sky.RAIN, Weather.sky(66));
        assertEquals(Weather.Sky.SHOWERS, Weather.sky(81));
        assertEquals(Weather.Sky.SNOW, Weather.sky(73));
        assertEquals(Weather.Sky.SNOW, Weather.sky(86));
        assertEquals(Weather.Sky.CLOUDY, Weather.sky(12));
    }

    @Test
    public void urlCarriesTheCoordinates() {
        String url = Weather.url("49.505", "5.937");
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=49.505&longitude=5.937&"));
        assertEquals("-3°", Weather.degrees(-3));
    }
}
