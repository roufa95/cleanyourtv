package com.gathin.tvhome.calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;

import org.junit.Test;

public class SeasonTest {

    @Test
    public void easterDates() {
        assertEquals(LocalDate.of(2026, 4, 5), Season.easter(2026));
        assertEquals(LocalDate.of(2027, 3, 28), Season.easter(2027));
        assertEquals(LocalDate.of(2025, 4, 20), Season.easter(2025));
    }

    @Test
    public void fixedPeriods() {
        assertNull(Season.at(LocalDate.of(2026, 10, 14)));
        assertEquals(Season.HALLOWEEN, Season.at(LocalDate.of(2026, 10, 15)));
        assertEquals(Season.HALLOWEEN, Season.at(LocalDate.of(2026, 11, 2)));
        assertNull(Season.at(LocalDate.of(2026, 11, 3)));
        assertEquals(Season.CHRISTMAS, Season.at(LocalDate.of(2026, 12, 1)));
        assertEquals(Season.NEW_YEAR, Season.at(LocalDate.of(2026, 12, 31)));
        assertEquals(Season.NEW_YEAR, Season.at(LocalDate.of(2027, 1, 6)));
        assertNull(Season.at(LocalDate.of(2027, 1, 7)));
        assertNull(Season.at(LocalDate.of(2027, 6, 23)));   // no National Day theme
    }

    @Test
    public void movingPeriodsFollowEaster() {
        // 2026: Easter 5 April, Ash Wednesday 18 February, Fetten Donneschdeg 12 February.
        assertNull(Season.at(LocalDate.of(2026, 2, 11)));
        assertEquals(Season.CARNIVAL, Season.at(LocalDate.of(2026, 2, 12)));
        assertEquals(Season.CARNIVAL, Season.at(LocalDate.of(2026, 2, 17)));
        // No Easter theme: Easter only places Fuesend.
        assertNull(Season.at(LocalDate.of(2027, 3, 28)));
    }

    @Test
    public void ramadanAndEid() {
        // 1447 AH: Ramadan from 18 February 2026, Eid al-Fitr 20 March, Eid al-Adha 27 May (Umm al-Qura).
        assertNull(Season.at(LocalDate.of(2026, 2, 10)));
        assertEquals(Season.RAMADAN, Season.at(LocalDate.of(2026, 2, 25)));
        assertEquals(Season.RAMADAN, Season.at(LocalDate.of(2026, 3, 10)));
        assertEquals(Season.EID, Season.at(LocalDate.of(2026, 3, 21)));
        assertEquals(Season.EID, Season.at(LocalDate.of(2026, 5, 28)));
        assertNull(Season.at(LocalDate.of(2026, 6, 10)));
        // 2027: Ramadan starts during carnival week and wins.
        assertEquals(Season.RAMADAN, Season.at(LocalDate.of(2027, 2, 20)));
    }

    @Test
    public void settingForcesOrSwitchesOff() {
        LocalDate summer = LocalDate.of(2027, 7, 10);
        assertNull(Season.pick("auto", summer));
        assertEquals(Season.CHRISTMAS, Season.pick("Christmas", summer));
        assertEquals(Season.CARNIVAL, Season.pick("carnival", summer));
        assertNull(Season.pick("off", LocalDate.of(2026, 12, 24)));
        assertEquals(Season.CHRISTMAS, Season.pick("nonsense", LocalDate.of(2026, 12, 24)));
    }
}
