package com.gathin.tvhome.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gathin.tvhome.calendar.Season;
import com.gathin.tvhome.util.Config;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The daily background photo: once a day Home asks Unsplash for one dark landscape photo matching the season
 * (see Season; the rest of the year a moonlit castle, candles or a misty forest), saves it at screen size under
 * photos/<season> in the theme folder with the photographer's credit beside it, and keeps the newest {@link #KEEP}.
 * Config then rotates those photos, and falls back to the theme's drawn scenes while there are none (no key, or
 * offline). Needs the owner's own Unsplash Access Key (a theme's "unsplash_key").
 */
public final class PhotoFetch {

    private static final String TAG = "PhotoFetch";
    static final int KEEP = 6;
    private static final long RETRY_MS = 3L * 60 * 60 * 1000;
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static boolean running;

    private PhotoFetch() {
    }

    /** The folder name for a season: "halloween", "new_year"... and "normal" for the rest of the year. */
    public static String folder(Season season) {
        return season == null ? "normal" : season.name().toLowerCase(Locale.ROOT);
    }

    /** Unsplash searches per season, one a day in turn; all dark scenes so Home's text stays readable. */
    static String query(Season season, LocalDate day) {
        String[] q;
        if (season == null) {
            q = new String[]{"castle night", "candles dark", "misty forest night", "old library", "moon night sky",
                    "steam train night"};
        } else {
            switch (season) {
                case HALLOWEEN: q = new String[]{"halloween night", "haunted house night", "jack o lantern dark"}; break;
                case CHRISTMAS: q = new String[]{"christmas lights night", "snowy village night", "christmas tree dark"}; break;
                case NEW_YEAR: q = new String[]{"fireworks night city", "new year fireworks", "sparklers night"}; break;
                case CARNIVAL: q = new String[]{"carnival lights night", "confetti dark", "venetian mask"}; break;
                case RAMADAN: q = new String[]{"ramadan lantern", "crescent moon mosque night", "lanterns night"}; break;
                default: q = new String[]{"eid lanterns", "mosque night lights", "crescent moon night"};
            }
        }
        return q[(int) Math.floorMod(day.toEpochDay(), (long) q.length)];
    }

    /** The credit line saved beside a photo, or null for a drawn scene. */
    public static String credit(File image) {
        File f = new File(image.getPath() + ".credit");
        try {
            return f.isFile() ? new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Fetches today's photo for {@code season} in the background when it has not been fetched yet; then {@code done}. */
    public static void refreshIfDue(Context ctx, String key, Season season, Runnable done) {
        File dir = Config.dir(ctx);
        if (key.isEmpty() || dir == null) return;
        Context app = ctx.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences("photos", Context.MODE_PRIVATE);
        String folder = folder(season);
        LocalDate today = LocalDate.now();
        if (p.getLong("day_" + folder, -1) == today.toEpochDay()) return;
        if (System.currentTimeMillis() - p.getLong("tried_" + folder, 0) < RETRY_MS) return;
        synchronized (PhotoFetch.class) {
            if (running) return;
            running = true;
        }
        p.edit().putLong("tried_" + folder, System.currentTimeMillis()).apply();
        int width = app.getResources().getDisplayMetrics().widthPixels;
        int height = app.getResources().getDisplayMetrics().heightPixels;
        Handler main = new Handler(Looper.getMainLooper());
        worker.execute(() -> {
            try {
                File out = new File(new File(dir, "photos"), folder);
                if (!out.isDirectory() && !out.mkdirs()) throw new IOException("cannot create " + out);
                JSONObject photo = new JSONObject(new String(get("https://api.unsplash.com/photos/random?orientation=landscape"
                        + "&content_filter=high&query=" + URLEncoder.encode(query(season, today), "UTF-8"), key, 200_000),
                        StandardCharsets.UTF_8));
                String id = photo.getString("id").replaceAll("[^A-Za-z0-9_-]", "");
                String raw = photo.getJSONObject("urls").getString("raw");
                byte[] jpeg = get(raw + (raw.contains("?") ? "&" : "?") + "w=" + width + "&h=" + height
                        + "&fit=crop&crop=entropy&fm=jpg&q=75", null, 6_000_000);
                File image = new File(out, today.toString().replace("-", "") + "-" + id + ".jpg");
                File tmp = new File(out, image.getName() + ".tmp");
                try (OutputStream o = new FileOutputStream(tmp)) {
                    o.write(jpeg);
                }
                if (!tmp.renameTo(image)) throw new IOException("rename failed");
                JSONObject user = photo.optJSONObject("user");
                String name = user == null ? "" : user.optString("name", "").trim();
                try (OutputStream o = new FileOutputStream(image.getPath() + ".credit")) {
                    o.write(("Photo: " + (name.isEmpty() ? "Unsplash" : name + " / Unsplash")).getBytes(StandardCharsets.UTF_8));
                }
                // Unsplash's API guidelines: report each download through download_location.
                JSONObject links = photo.optJSONObject("links");
                if (links != null && links.has("download_location")) {
                    try {
                        get(links.getString("download_location"), key, 50_000);
                    } catch (IOException ignored) {
                        // The photo is saved either way.
                    }
                }
                prune(out);
                p.edit().putLong("day_" + folder, today.toEpochDay()).apply();
                Log.i(TAG, folder + ": " + image.getName() + " (" + jpeg.length / 1024 + " KB)");
                main.post(done);
            } catch (Exception e) {
                Log.w(TAG, folder + ": " + e);
            } finally {
                synchronized (PhotoFetch.class) {
                    running = false;
                }
            }
        });
    }

    /** Keeps the newest {@link #KEEP} photos (their names start with the date) and their credits. */
    private static void prune(File dir) {
        File[] photos = dir.listFiles((d, n) -> n.endsWith(".jpg"));
        if (photos == null || photos.length <= KEEP) return;
        Arrays.sort(photos, (a, b) -> b.getName().compareTo(a.getName()));
        for (int i = KEEP; i < photos.length; i++) {
            //noinspection ResultOfMethodCallIgnored
            photos[i].delete();
            //noinspection ResultOfMethodCallIgnored
            new File(photos[i].getPath() + ".credit").delete();
        }
    }

    private static byte[] get(String url, String key, int max) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        if (key != null) {
            c.setRequestProperty("Authorization", "Client-ID " + key);
            c.setRequestProperty("Accept-Version", "v1");
        }
        try {
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > max) throw new IOException("response too large");
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}
