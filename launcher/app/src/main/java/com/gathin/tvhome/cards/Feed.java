package com.gathin.tvhome.cards;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A small JSON document from the web, kept in a file and downloaded again at most every {@code everyMs}: the
 * weather card's forecast and the footer's holidays. Home reads the file; only an overdue refresh goes online,
 * on one background thread shared by every feed, and a failed one waits {@link #RETRY_MS} before the next try.
 */
public final class Feed {

    private static final String TAG = "Feed";
    private static final long RETRY_MS = 30L * 60 * 1000;
    /** No feed here is more than a few dozen KB; anything far larger is not what we asked for. */
    private static final int MAX_BYTES = 1 << 20;
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();

    public interface Callback {
        void onBody(String body);
    }

    private final String name;
    private final long everyMs;
    private boolean running;

    public Feed(String name, long everyMs) {
        this.name = name;
        this.everyMs = everyMs;
    }

    private SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("feed_" + name, Context.MODE_PRIVATE);
    }

    private File file(Context ctx) {
        return new File(ctx.getFilesDir(), "feed_" + name + ".json");
    }

    /** The last body downloaded from {@code url}, or null (none yet, or the URL changed since). */
    public String cached(Context ctx, String url) {
        if (!url.equals(prefs(ctx).getString("url", ""))) return null;
        try {
            return new String(Files.readAllBytes(file(ctx).toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** When the last download from {@code url} finished, in epoch milliseconds; 0 if never. */
    long fetchedAt(Context ctx, String url) {
        SharedPreferences p = prefs(ctx);
        return url.equals(p.getString("url", "")) ? p.getLong("fetched", 0) : 0;
    }

    /** Downloads {@code url} in the background when due, then calls {@code done} on the main thread. */
    public void refreshIfDue(Context ctx, String url, Callback done) {
        Context app = ctx.getApplicationContext();
        SharedPreferences p = prefs(app);
        long now = System.currentTimeMillis();
        if (url.equals(p.getString("url", "")) && now - p.getLong("fetched", 0) < everyMs) return;
        if (url.equals(p.getString("tried_url", "")) && now - p.getLong("tried", 0) < RETRY_MS) return;
        if (running) return;   // main thread only, like every caller
        running = true;
        p.edit().putLong("tried", now).putString("tried_url", url).apply();
        Handler main = new Handler(Looper.getMainLooper());
        worker.execute(() -> {
            String body = null;
            try {
                body = download(url);
                File tmp = new File(app.getFilesDir(), "feed_" + name + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
                if (!tmp.renameTo(file(app))) throw new IOException("rename failed");
                p.edit().putString("url", url).putLong("fetched", System.currentTimeMillis()).apply();
                Log.i(TAG, name + ": " + body.length() + " bytes");
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, name + ": download failed: " + e);
                body = null;
            }
            String result = body;
            main.post(() -> {
                running = false;
                if (result != null) done.onBody(result);
            });
        });
    }

    private static String download(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "CleanYourTV launcher");
        try {
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) throw new IOException("response too large");
                }
                return out.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }
}
