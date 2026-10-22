package com.gathin.tvhome.calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class HolidaysTest {

    // OpenHolidays' answers for Luxembourg, 2026/27, trimmed.
    private static final String SCHOOL = "[{\"startDate\":\"2026-10-31\",\"endDate\":\"2026-11-08\","
            + "\"name\":[{\"language\":\"EN\",\"text\":\"All Saints Day Holidays\"}]},"
            + "{\"startDate\":\"2026-12-19\",\"endDate\":\"2027-01-03\",\"name\":[{\"language\":\"EN\",\"text\":\"Christmas Holidays\"}]}]";
    private static final String PUBLIC = "[{\"startDate\":\"2026-11-01\",\"endDate\":\"2026-11-01\","
            + "\"name\":[{\"language\":\"EN\",\"text\":\"All Saints\"}]},"
            + "{\"startDate\":\"2026-12-25\",\"endDate\":\"2026-12-25\",\"name\":[{\"language\":\"EN\",\"text\":\"Christmas Day\"}]}]";

    private static List<Holidays.Holiday> all() {
        List<Holidays.Holiday> list = new ArrayList<>(Holidays.parse(PUBLIC, false));
        list.addAll(Holidays.parse(SCHOOL, true));
        return list;
    }

    @Test
    public void countsDownToTheNextOne() {
        Holidays.Next n = Holidays.at(all(), LocalDate.of(2026, 10, 21));
        assertEquals("All Saints Day Holidays", n.holiday.name);
        assertFalse(n.now);
        assertEquals(10, n.inDays);
    }

    @Test
    public void aSchoolHolidayUnderWayWinsOverAPublicHolidayInsideIt() {
        Holidays.Next n = Holidays.at(all(), LocalDate.of(2026, 11, 1));
        assertTrue(n.now);
        assertTrue(n.holiday.school);
        assertEquals(LocalDate.of(2026, 11, 8), n.holiday.end);
    }

    @Test
    public void aPublicHolidayOnItsOwnDay() {
        Holidays.Next n = Holidays.at(Holidays.parse(PUBLIC, false), LocalDate.of(2026, 12, 25));
        assertTrue(n.now);
        assertEquals("Christmas Day", n.holiday.name);
    }

    @Test
    public void nothingBeyondTheHorizonOrWithoutData() {
        assertNull(Holidays.at(all(), LocalDate.of(2027, 1, 4)));
        assertTrue(Holidays.parse("oops", true).isEmpty());
        assertTrue(Holidays.parse(null, true).isEmpty());
    }

    @Test
    public void urlsChangeOncePerMonth() {
        assertEquals(Holidays.schoolUrl("LU", LocalDate.of(2026, 10, 2)), Holidays.schoolUrl("LU", LocalDate.of(2026, 10, 30)));
        assertTrue(Holidays.publicUrl("LU", LocalDate.of(2026, 10, 21))
                .endsWith("validFrom=2026-10-01&validTo=2027-10-01"));
    }
}
