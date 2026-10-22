package com.gathin.tvhome.waste;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class WasteCalendarTest {

    // Lines as data.public.lu writes them (with the BOM-less header), Sanem rows from October 2026.
    private static final String CSV = String.join("\n",
            "\"Date\";\"Type de collecte\";\"Commune\";\"Localité\";\"Rue\"",
            "\"22/10/2026\";\"Biodéchets\";\"Esch-sur-Alzette\";\"Esch-sur-Alzette\";\"Rue de Belvaux\"",
            "\"30/10/2026\";\"Déchets ménagers en mélange\";\"Sanem\";\"\";\"Toutes les rues\"",
            "\"23/10/2026\";\"Déchets ménagers en mélange\";\"Sanem\";\"\";\"Toutes les rues\"",
            "\"23/10/2026\";\"Déchets ménagers en mélange\";\"Sanem\";\"\";\"Toutes les rues\"",
            "\"26/10/2026\";\"Valorlux\";\"Sanem\";\"\";\"Toutes les rues\"",
            "\"27/10/2026\";\"Biodéchets\";\"Sanem\";\"\";\"Toutes les rues\"",
            "\"27/10/2026\";\"Valorlux\";\"Sanem\";\"\";\"Toutes les rues\"",
            "\"28/10/2026\";\"SuperDrecksKëscht\";\"Sanem\";\"Belvaux\";\"Rue de la Poste\"",
            "\"29/10/2026\";\"Papier/Carton\";\"Sanem\";\"Soleuvre\";\"Toutes les rues\"",
            "not a csv line",
            "\"31/02/2026\";\"Verre\";\"Sanem\";\"\";\"Toutes les rues\"");

    private static List<WasteCalendar.Pickup> parse(String locality, String street) throws IOException {
        return WasteCalendar.parse(new BufferedReader(new StringReader(CSV)), "Sanem", locality, street);
    }

    @Test
    public void keepsTheCommuneWideCollectionsInDateOrderWithoutDuplicates() throws IOException {
        List<WasteCalendar.Pickup> p = parse("", "");
        assertEquals(5, p.size());
        assertEquals(LocalDate.of(2026, 10, 23), p.get(0).date);
        assertEquals(WasteCalendar.Kind.HOUSEHOLD, p.get(0).kind);
        assertEquals(WasteCalendar.Kind.VALORLUX, p.get(1).kind);
        // Same day: sorted by kind, so the headline always lists them the same way.
        assertEquals(WasteCalendar.Kind.BIO, p.get(2).kind);
        assertEquals(WasteCalendar.Kind.VALORLUX, p.get(3).kind);
        assertEquals(LocalDate.of(2026, 10, 30), p.get(4).date);
    }

    @Test
    public void localityAndStreetAddTheirOwnRounds() throws IOException {
        List<WasteCalendar.Pickup> belvaux = parse("Belvaux", "rue de la poste");
        assertEquals(6, belvaux.size());
        assertTrue(belvaux.stream().anyMatch(x -> x.kind == WasteCalendar.Kind.SDK));
        assertFalse(belvaux.stream().anyMatch(x -> x.kind == WasteCalendar.Kind.PAPER));

        List<WasteCalendar.Pickup> soleuvre = parse("Soleuvre", "");
        assertEquals(6, soleuvre.size());
        assertTrue(soleuvre.stream().anyMatch(x -> x.kind == WasteCalendar.Kind.PAPER));
    }

    @Test
    public void namesTheDatasetsTypes() {
        assertEquals(WasteCalendar.Kind.HOUSEHOLD, WasteCalendar.kind("Conteneur pour déchets ménagers"));
        assertEquals(WasteCalendar.Kind.PAPER, WasteCalendar.kind("Papier/Carton (commerces)"));
        assertEquals(WasteCalendar.Kind.GLASS, WasteCalendar.kind("Verre – 40L"));
        assertEquals(WasteCalendar.Kind.ELECTRONICS,
                WasteCalendar.kind("Déchets d’équipements électriques et électroniques"));
        assertEquals(WasteCalendar.Kind.BULKY, WasteCalendar.kind("Déchets encombrants en vrac"));
        assertEquals(WasteCalendar.Kind.TREES, WasteCalendar.kind("Arbres de Noël"));
        assertEquals(WasteCalendar.Kind.WOOD, WasteCalendar.kind("Vieux bois"));
        assertEquals(WasteCalendar.Kind.OTHER, WasteCalendar.kind("Vieux vêtements"));
    }

    @Test
    public void todaysCollectionCountsUntilTheMorningRoundIsOver() throws IOException {
        List<WasteCalendar.Pickup> p = parse("", "");
        assertEquals(LocalDate.of(2026, 10, 23),
                WasteCalendar.upcoming(p, LocalDateTime.of(2026, 10, 23, 9, 59)).get(0).date);
        assertEquals(LocalDate.of(2026, 10, 26),
                WasteCalendar.upcoming(p, LocalDateTime.of(2026, 10, 23, 10, 0)).get(0).date);
        assertTrue(WasteCalendar.upcoming(p, LocalDateTime.of(2026, 10, 31, 8, 0)).isEmpty());
    }

    @Test
    public void groupsByDay() throws IOException {
        List<List<WasteCalendar.Pickup>> days = WasteCalendar.byDay(parse("", ""), 3);
        assertEquals(3, days.size());
        assertEquals(1, days.get(0).size());
        assertEquals(2, days.get(2).size());
    }

    @Test
    public void oneRowPerBinType() throws IOException {
        List<WasteCalendar.Pickup> rows = WasteCalendar.nextPerKind(
                WasteCalendar.upcoming(parse("", ""), LocalDateTime.of(2026, 10, 23, 12, 0)));
        assertEquals(3, rows.size());
        assertEquals(WasteCalendar.Kind.VALORLUX, rows.get(0).kind);
        assertEquals(LocalDate.of(2026, 10, 26), rows.get(0).date);
        assertEquals(WasteCalendar.Kind.BIO, rows.get(1).kind);
        assertEquals(WasteCalendar.Kind.HOUSEHOLD, rows.get(2).kind);
        assertEquals(LocalDate.of(2026, 10, 30), rows.get(2).date);
    }

    @Test
    public void tonightFromFiveTheEveningBefore() throws IOException {
        List<WasteCalendar.Pickup> p = parse("", "");
        assertTrue(WasteCalendar.tonight(p, LocalDateTime.of(2026, 10, 26, 16, 59)).isEmpty());
        List<WasteCalendar.Pickup> tue = WasteCalendar.tonight(p, LocalDateTime.of(2026, 10, 26, 17, 0));
        assertEquals(2, tue.size());
        assertEquals(WasteCalendar.Kind.BIO, tue.get(0).kind);
        assertTrue(WasteCalendar.tonight(p, LocalDateTime.of(2026, 10, 24, 20, 0)).isEmpty());
    }

    @Test
    public void cacheRoundTrips() throws IOException {
        List<WasteCalendar.Pickup> p = parse("Belvaux", "Rue de la Poste");
        List<WasteCalendar.Pickup> back = WasteCalendar.deserialize(WasteCalendar.serialize(p));
        assertEquals(p.size(), back.size());
        for (int i = 0; i < p.size(); i++) {
            assertEquals(p.get(i).date, back.get(i).date);
            assertEquals(p.get(i).type, back.get(i).type);
        }
    }

    @Test
    public void quotedFields() {
        assertEquals(Arrays.asList("a;b", "say \"hi\"", ""), WasteCalendar.fields("\"a;b\";\"say \"\"hi\"\"\";\"\""));
    }

    /** Against the real export when WASTE_CSV points at one (not part of the normal run). */
    @Test
    public void realExport() throws IOException {
        String path = System.getenv("WASTE_CSV");
        if (path == null) return;
        try (BufferedReader in = java.nio.file.Files.newBufferedReader(java.nio.file.Paths.get(path))) {
            List<WasteCalendar.Pickup> p = WasteCalendar.parse(in, "Sanem", "", "");
            System.out.println("Sanem: " + p.size() + " collections");
            for (WasteCalendar.Pickup x : p) System.out.println("  " + x.date + "  " + x.kind + "  " + x.type);
            assertFalse(p.isEmpty());
        }
    }
}
