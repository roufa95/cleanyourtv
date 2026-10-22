package com.gathin.tvhome.waste;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bin collection days for one place in Luxembourg (config.xml waste_commune, waste_locality, waste_street).
 * The data is the national open-data calendar on data.public.lu (CC0, one CSV for every commune, the next
 * three months, refreshed daily); WasteFetch downloads it once a week and keeps only this place's rows.
 * Everything here is plain Java against that text, so it is tested on the JVM.
 */
public final class WasteCalendar {

    /** A collection that is due today stops showing once the morning round is over. */
    static final LocalTime TODAY_UNTIL = LocalTime.of(10, 0);
    /** The CSV's street value for a collection that covers the whole commune or locality. */
    private static final String ALL_STREETS = "toutes les rues";

    public enum Kind { HOUSEHOLD, BIO, VALORLUX, PAPER, GLASS, BULKY, ELECTRONICS, SDK, GREEN, TREES, WOOD, OTHER }

    public static final class Pickup {
        public final LocalDate date;
        /** The CSV's own name, e.g. "Déchets ménagers en mélange"; shown as is for {@link Kind#OTHER}. */
        public final String type;
        public final Kind kind;

        Pickup(LocalDate date, String type) {
            this.date = date;
            this.type = type.trim();
            this.kind = kind(this.type);
        }
    }

    private WasteCalendar() {
    }

    /** Lower case without accents or curly quotes, so "Déchets d’équipements" matches "dechets d'equipements". */
    static String fold(String s) {
        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replace('’', '\'').toLowerCase(Locale.ROOT);
    }

    static Kind kind(String type) {
        String t = fold(type);
        if (t.contains("superdreck")) return Kind.SDK;
        if (t.startsWith("biodechet")) return Kind.BIO;
        if (t.startsWith("valorlux") || t.contains("recyclable")) return Kind.VALORLUX;
        if (t.startsWith("papier")) return Kind.PAPER;
        if (t.startsWith("verre")) return Kind.GLASS;
        if (t.contains("encombrant")) return Kind.BULKY;
        if (t.contains("electrique") || t.contains("electronique")) return Kind.ELECTRONICS;
        if (t.contains("verdure")) return Kind.GREEN;
        if (t.contains("arbres de noel")) return Kind.TREES;
        if (t.contains("bois")) return Kind.WOOD;
        if (t.contains("menager")) return Kind.HOUSEHOLD;
        return Kind.OTHER;
    }

    /** One CSV line: fields separated by ';', each in double quotes ("" inside is a quote). */
    static List<String> fields(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ';') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    /**
     * Whether a row (commune, locality, street) is a collection at this place. A row with no locality covers the
     * whole commune; with no locality set, rows for one locality are left out, since they are someone else's
     * round. Street rows other than "Toutes les rues" count only for the street that is set.
     */
    static boolean matches(String commune, String locality, String street,
                           String wantCommune, String wantLocality, String wantStreet) {
        if (!fold(commune).equals(fold(wantCommune))) return false;
        String l = fold(locality);
        if (!l.isEmpty() && !l.equals(fold(wantLocality))) return false;
        String s = fold(street);
        return s.equals(ALL_STREETS) || (!fold(wantStreet).isEmpty() && s.equals(fold(wantStreet)));
    }

    /**
     * Reads the national CSV (Date;Type de collecte;Commune;Localité;Rue, dates as dd/MM/yyyy) line by line and
     * keeps this place's collections, one per day and type, in date order. Lines that do not parse are skipped.
     */
    static List<Pickup> parse(BufferedReader in, String commune, String locality, String street) throws IOException {
        Set<String> seen = new LinkedHashSet<>();
        List<Pickup> out = new ArrayList<>();
        String line;
        while ((line = in.readLine()) != null) {
            // Cheap test first: almost every line of the 7 MB file is another commune.
            if (!fold(line).contains(fold(commune))) continue;
            List<String> f = fields(line);
            if (f.size() < 5 || !matches(f.get(2), f.get(3), f.get(4), commune, locality, street)) continue;
            LocalDate date = date(f.get(0));
            if (date == null || f.get(1).trim().isEmpty()) continue;
            if (seen.add(date + "\t" + fold(f.get(1)))) out.add(new Pickup(date, f.get(1)));
        }
        sort(out);
        return out;
    }

    /** "23/10/2026" or "2026-10-23"; null for anything else. */
    static LocalDate date(String s) {
        String t = s.trim();
        try {
            if (t.matches("\\d{2}/\\d{2}/\\d{4}")) {
                return LocalDate.of(Integer.parseInt(t.substring(6)), Integer.parseInt(t.substring(3, 5)),
                        Integer.parseInt(t.substring(0, 2)));
            }
            return LocalDate.parse(t);
        } catch (DateTimeException | NumberFormatException e) {   // DateTimeParseException included
            return null;
        }
    }

    /** The cache file WasteFetch keeps: one "2026-10-23<TAB>type" line per collection. */
    static String serialize(List<Pickup> pickups) {
        StringBuilder b = new StringBuilder();
        for (Pickup p : pickups) b.append(p.date).append('\t').append(p.type.replace('\t', ' ')).append('\n');
        return b.toString();
    }

    static List<Pickup> deserialize(String text) {
        List<Pickup> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            LocalDate d = date(line.substring(0, tab));
            String type = line.substring(tab + 1).trim();
            if (d != null && !type.isEmpty()) out.add(new Pickup(d, type));
        }
        sort(out);
        return out;
    }

    private static void sort(List<Pickup> pickups) {
        Collections.sort(pickups, (a, b) -> {
            int c = a.date.compareTo(b.date);
            return c != 0 ? c : a.kind.compareTo(b.kind);
        });
    }

    /** Collections still to come at {@code now}: today's until {@link #TODAY_UNTIL}, then the following days. */
    public static List<Pickup> upcoming(List<Pickup> all, LocalDateTime now) {
        LocalDate first = now.toLocalTime().isBefore(TODAY_UNTIL) ? now.toLocalDate() : now.toLocalDate().plusDays(1);
        List<Pickup> out = new ArrayList<>();
        for (Pickup p : all) if (!p.date.isBefore(first)) out.add(p);
        return out;
    }

    /** The evening reminder starts at this time the day before a collection. */
    static final LocalTime REMIND_FROM = LocalTime.of(17, 0);

    /** The next collection of each bin type, soonest first: "when is my paper bin?". */
    public static List<Pickup> nextPerKind(List<Pickup> upcoming) {
        List<Pickup> out = new ArrayList<>();
        Set<Kind> seen = new java.util.HashSet<>();
        for (Pickup p : upcoming) {
            // OTHER covers several CSV types, so it is told apart by its own name.
            if (p.kind == Kind.OTHER ? out.stream().noneMatch(o -> o.type.equals(p.type)) : seen.add(p.kind)) out.add(p);
        }
        return out;
    }

    /** The bins to put out tonight: tomorrow's collections, from {@link #REMIND_FROM} on; empty otherwise. */
    public static List<Pickup> tonight(List<Pickup> all, LocalDateTime now) {
        List<Pickup> out = new ArrayList<>();
        if (now.toLocalTime().isBefore(REMIND_FROM)) return out;
        LocalDate tomorrow = now.toLocalDate().plusDays(1);
        for (Pickup p : all) if (p.date.equals(tomorrow)) out.add(p);
        return out;
    }

    /** The upcoming collections grouped by day, at most {@code days} days. */
    static List<List<Pickup>> byDay(List<Pickup> upcoming, int days) {
        List<List<Pickup>> out = new ArrayList<>();
        for (Pickup p : upcoming) {
            if (out.isEmpty() || !out.get(out.size() - 1).get(0).date.equals(p.date)) {
                if (out.size() == days) break;
                out.add(new ArrayList<>());
            }
            out.get(out.size() - 1).add(p);
        }
        return out;
    }
}
