package com.gathin.tvhome.clock;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.gathin.tvhome.R;
import com.gathin.tvhome.cards.Feed;
import com.gathin.tvhome.util.Config;
import com.gathin.tvhome.wallpaper.PhotoFetch;
import com.gathin.tvhome.wallpaper.WallpaperPlayer;
import com.gathin.tvhome.waste.WasteCalendar;
import com.gathin.tvhome.waste.WasteFetch;
import com.gathin.tvhome.waste.WastePanel;
import com.gathin.tvhome.weather.Weather;
import com.gathin.tvhome.weather.WeatherCards;
import java.io.File;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The clock screensaver's scene (layout dream_clock): the thin clock and date on black over a slow ambient
 * glow. The clock sits in the bottom right corner and shifts a few pixels every minute, and the glow shifts
 * colour, so nothing burns in. When the
 * theme has a wallpaper collection, one of its pictures replaces the glow (a new one every ten minutes), and
 * under the date the scene shows the weather card's forecast and, the evening before a bin collection, the
 * bin card's reminder; both read what Home downloaded, nothing here goes online.
 * Shared by ClockDream (the system screensaver) and ClockActivity (Home left idle).
 */
public final class ClockScene {

    private static final long MOVE_MS = 60_000L;
    /** The glow's colour change: 16 steps over 8 s. Animated per frame it redrew the full-screen gradient ~480 times. */
    private static final int GLOW_STEPS = 16;
    private static final long GLOW_STEP_MS = 500L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content;
    private final GradientDrawable glow;
    /** Ticks (minutes) between wallpaper changes. */
    private static final int WALLPAPER_TICKS = 10;
    private final Context ctx;
    private final Config cfg;
    private final View ambient;
    private final ImageView wallpaper;
    private final TextView weather, bins;
    private TextView credit;
    private View shade;
    private final List<File> wallpapers;
    private int ticks;
    private File shown;
    private int colorIndex;
    private int glowColor;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            if (wallpapers.isEmpty()) shiftGlow();
            else if (++ticks % WALLPAPER_TICKS == 0) showWallpaper();
            updateInfo();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    /** root is the inflated dream_clock layout. */
    public ClockScene(View root) {
        content = root.findViewById(R.id.dreamContent);
        // Right-aligned text, so the clock, date and lines under it line up against the corner.
        ((android.widget.LinearLayout) content).setGravity(android.view.Gravity.END);
        ambient = root.findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);

        ctx = root.getContext();
        cfg = Config.load(ctx);
        wallpaper = root.findViewById(R.id.dreamWallpaper);
        weather = root.findViewById(R.id.dreamWeather);
        bins = root.findViewById(R.id.dreamBins);
        File dir = Config.dir(ctx);
        // The same collection Home rotates: the season's photos or drawn scenes, else the normal set.
        wallpapers = dir == null || !cfg.wallpaperEnabled ? Collections.emptyList() : cfg.wallpapers;
        credit = root.findViewById(R.id.dreamCredit);
        shade = root.findViewById(R.id.dreamShade);
        if (!wallpapers.isEmpty()) {
            ambient.setVisibility(View.GONE);
            wallpaper.setVisibility(View.VISIBLE);
            root.findViewById(R.id.dreamShade).setVisibility(View.VISIBLE);
            showWallpaper();
        }
        updateInfo();
    }

    /** Another picture from the collection, decoded at screen size in 16-bit colour (about 4 MB at 1080p). */
    private void showWallpaper() {
        File next = wallpapers.get(random.nextInt(wallpapers.size()));
        if (wallpapers.size() > 1 && next.equals(shown)) {
            next = wallpapers.get((wallpapers.indexOf(next) + 1) % wallpapers.size());
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(next.getPath(), o);
        int screen = ctx.getResources().getDisplayMetrics().widthPixels;
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= screen) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeFile(next.getPath(), o);
        if (b == null) return;
        shown = next;
        String line = PhotoFetch.credit(next);
        credit.setText(line == null ? "" : line);
        credit.setVisibility(line == null ? View.GONE : View.VISIBLE);
        // Downloaded photos get Home's darker photo shade; the theme's drawn scenes keep the lighter one.
        if (line != null) shade.setBackground(WallpaperPlayer.photoShade());
        else shade.setBackgroundColor(0x66000000);
        wallpaper.animate().alpha(0f).setDuration(600).withEndAction(() -> {
            wallpaper.setImageBitmap(b);
            wallpaper.animate().alpha(1f).setDuration(1400).start();
        }).start();
    }

    /** The forecast for this hour, and tonight's bins, from the files Home keeps. */
    private void updateInfo() {
        LocalDateTime now = LocalDateTime.now();
        Weather.Now w = null;
        if (cfg.weather) {
            String json = new Feed("weather", 0).cached(ctx, Weather.url(cfg.latitude, cfg.longitude));
            if (json != null) w = Weather.at(json, now);
        }
        if (w != null) {
            SpannableStringBuilder t = new SpannableStringBuilder();
            icon(t, ctx.getDrawable(WeatherCards.skyIcon(w.current.sky, w.current.day)).mutate(),
                    weather.getTextSize() * 1.3f);
            t.append("  ").append(Weather.degrees(w.current.temp)).append("  ·  ")
                    .append(WeatherCards.skyName(ctx, w.current.sky));
            weather.setText(t);
            weather.setVisibility(View.VISIBLE);
        } else {
            weather.setVisibility(View.GONE);
        }

        List<WasteCalendar.Pickup> tonight = cfg.wasteCommune.isEmpty() ? Collections.emptyList()
                : WasteCalendar.tonight(WasteFetch.cached(ctx, cfg.wasteCommune, cfg.wasteLocality, cfg.wasteStreet), now);
        if (tonight.isEmpty()) {
            bins.setVisibility(View.GONE);
            return;
        }
        SpannableStringBuilder t = new SpannableStringBuilder(ctx.getString(R.string.waste_put_out_tonight));
        for (int i = 0; i < tonight.size(); i++) {
            WasteCalendar.Pickup p = tonight.get(i);
            t.append(i == 0 ? "   " : "   +   ");
            Drawable d = ctx.getDrawable(WastePanel.iconRes(p.kind)).mutate();
            d.setTint(WastePanel.color(p.kind));
            icon(t, d, bins.getTextSize() * 1.3f);
            t.append(' ').append(WastePanel.name(ctx, p));
        }
        bins.setText(t);
        bins.setVisibility(View.VISIBLE);
    }

    private static void icon(SpannableStringBuilder b, Drawable d, float size) {
        d.setBounds(0, 0, Math.round(size), Math.round(size));
        b.append('￼');
        b.setSpan(new ImageSpan(d, Build.VERSION.SDK_INT >= 29 ? DynamicDrawableSpan.ALIGN_CENTER
                : DynamicDrawableSpan.ALIGN_BASELINE), b.length() - 1, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    public void start() {
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, MOVE_MS);
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);   // the tick and any glow steps still queued
        content.animate().cancel();
    }

    /** Glides the clock to its next spot in the corner over a few seconds, instead of fading it out and in. */
    private void moveClock() {
        float x = content.getX(), y = content.getY();
        placeClock();
        float nx = content.getX(), ny = content.getY();
        content.setX(x);
        content.setY(y);
        content.animate().x(nx).y(ny).setDuration(4000).start();
    }

    /**
     * The clock's corner: bottom right (bottom left in right-to-left languages), 48dp in from the edges. Each
     * minute it shifts by up to 10dp within that corner, too little to notice and enough that nothing burns in.
     */
    private void placeClock() {
        View parent = (View) content.getParent();
        float density = content.getResources().getDisplayMetrics().density;
        float margin = 48 * density, jitter = 10 * density;
        boolean rtl = content.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float x = rtl ? margin + random.nextFloat() * jitter
                : parent.getWidth() - content.getWidth() - margin - random.nextFloat() * jitter;
        float y = parent.getHeight() - content.getHeight() - margin - random.nextFloat() * jitter;
        content.setX(Math.max(0f, x));
        content.setY(Math.max(0f, y));
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void shiftGlow() {
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        int to = PALETTE[colorIndex];
        ArgbEvaluator mix = new ArgbEvaluator();
        for (int i = 1; i <= GLOW_STEPS; i++) {
            float f = (float) i / GLOW_STEPS;
            handler.postDelayed(() -> setGlow((int) mix.evaluate(f, from, to)), i * GLOW_STEP_MS);
        }
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
