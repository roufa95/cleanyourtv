package com.gathin.tvhome.waste;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The bin calendar's one network call: at most once a week (and again when the place in the config changes), the
 * national collection calendar is downloaded from data.public.lu and this place's rows are kept in a small file.
 * The rest of the time the panel reads that file. Nothing else in the launcher goes online.
 */
public final class WasteFetch {

    private static final String TAG = "WasteFetch";
    /** data.public.lu's permanent link to the dataset's CSV; it redirects to the latest daily export. */
    static final String CSV_URL = "https://data.public.lu/fr/datasets/r/c3805ec5-7836-49a4-9983-effaf81910d0";
    private static final long WEEK_MS = 7L * 24 * 60 * 60 * 1000;
    /** After a failed download (TV offline, server down), wait this long before the next try. */
    private static final long RETRY_MS = 6L * 60 * 60 * 1000;
    private static final String PREFS = "waste";
    private static final String CACHE = "waste.tsv";

    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static boolean running;

    private WasteFetch() {
    }

    private static String place(String commune, String locality, String street) {
        return commune + "|" + locality + "|" + street;
    }

    /** The last download for this place; empty before the first one, or after the place changed. */
    public static List<WasteCalendar.Pickup> cached(Context ctx, String commune, String locality, String street) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!place(commune, locality, street).equals(p.getString("place", ""))) return Collections.emptyList();
        File f = new File(ctx.getFilesDir(), CACHE);
        try {
            return WasteCalendar.deserialize(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Starts a download in the background when one is due, and calls {@code done} on the main thread with the new
     * collections once it succeeded. A cheap preferences check otherwise, so it can run on every resume.
     */
    public static void refreshIfDue(Context ctx, String commune, String locality, String street,
                             Callback done) {
        Context app = ctx.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String place = place(commune, locality, street);
        long now = System.currentTimeMillis();
        boolean samePlace = place.equals(p.getString("place", ""));
        if (samePlace && now - p.getLong("fetched", 0) < WEEK_MS) return;
        // The retry wait counts for the place it was tried for, so a first download that fails (TV offline) is not
        // retried on every resume either.
        if (place.equals(p.getString("tried_place", "")) && now - p.getLong("tried", 0) < RETRY_MS) return;
        synchronized (WasteFetch.class) {
            if (running) return;
            running = true;
        }
        p.edit().putLong("tried", now).putString("tried_place", place).apply();
        Handler main = new Handler(Looper.getMainLooper());
        worker.execute(() -> {
            try {
                List<WasteCalendar.Pickup> pickups = download(commune, locality, street);
                File tmp = new File(app.getFilesDir(), CACHE + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(WasteCalendar.serialize(pickups).getBytes(StandardCharsets.UTF_8));
                }
                if (!tmp.renameTo(new File(app.getFilesDir(), CACHE))) throw new IOException("rename failed");
                p.edit().putString("place", place).putLong("fetched", System.currentTimeMillis()).apply();
                // An empty result is kept too, so a misspelt commune does not download 7 MB on every resume.
                if (pickups.isEmpty()) Log.w(TAG, "no collections found for " + place);
                else Log.i(TAG, pickups.size() + " collections for " + place);
                main.post(() -> done.onPickups(pickups));
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "download failed: " + e);
            } finally {
                synchronized (WasteFetch.class) {
                    running = false;
                }
            }
        });
    }

    private static List<WasteCalendar.Pickup> download(String commune, String locality, String street) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(CSV_URL).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(60_000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "CleanYourTV launcher (bin calendar, weekly)");
        try {
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + c.getResponseCode());
            // Read and filter line by line: the whole file never sits in memory.
            try (BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                return WasteCalendar.parse(in, commune, locality, street);
            }
        } finally {
            c.disconnect();
        }
    }

    public interface Callback {
        void onPickups(List<WasteCalendar.Pickup> pickups);
    }
}
