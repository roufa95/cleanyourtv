package com.gathin.tvhome.stats;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class StatsTest {

    private static final long H = 3_600_000L, M = 60_000L;

    @Test
    public void screenOnAddsUpSessionsAndTheOneRunningNow() {
        List<long[]> events = Arrays.asList(new long[]{2 * H, 1}, new long[]{4 * H, 0}, new long[]{5 * H, 1});
        assertEquals(2 * H + 30 * M, Stats.screenOn(events, 0, 5 * H + 30 * M, false));
    }

    @Test
    public void onBeforeTheWindowCountsFromItsStart() {
        List<long[]> events = Collections.singletonList(new long[]{3 * H, 0});
        assertEquals(2 * H, Stats.screenOn(events, H, 6 * H, true));
        assertEquals(5 * H, Stats.screenOn(Collections.emptyList(), H, 6 * H, true));
        assertEquals(0, Stats.screenOn(Collections.emptyList(), H, 6 * H, false));
    }

    @Test
    public void repeatedOnEventsDoNotDoubleCount() {
        List<long[]> events = Arrays.asList(new long[]{H, 1}, new long[]{2 * H, 1}, new long[]{3 * H, 0});
        assertEquals(2 * H, Stats.screenOn(events, 0, 10 * H, false));
    }

    @Test
    public void energyAndCost() {
        double kwh = Stats.kwh(4 * H + 12 * M, 100);
        assertEquals(0.42, kwh, 1e-9);
        assertEquals("0.42 kWh", Stats.energy(kwh));
        assertEquals("12.6 kWh", Stats.energy(12.6));
        assertEquals("€0.09", Stats.money(kwh * Stats.price("0,22"), "€"));
        assertEquals(0, Stats.price("cheap"), 0);
    }

    @Test
    public void readableSizesAndDurations() {
        assertEquals("3.4 GB", Stats.bytes((long) (3.4 * (1L << 30))));
        assertEquals("58 GB", Stats.bytes(58L << 30));
        assertEquals("820 MB", Stats.bytes(820L << 20));
        assertEquals("4h 12m", Stats.duration(4 * H + 12 * M));
        assertEquals("35m", Stats.duration(35 * M));
    }
}
