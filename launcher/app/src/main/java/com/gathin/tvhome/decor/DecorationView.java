package com.gathin.tvhome.decor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.gathin.tvhome.R;
import com.gathin.tvhome.calendar.Season;
import java.util.Random;

/**
 * The holiday decorations on Home (see Season): spiders, a bat and jack-o'-lanterns at Halloween, lights, snow
 * and icicles at Christmas, streamers and balloons at New Year, bunting for Fuesend, lanterns for Ramadan and Eid. Everything hangs from or sits on the edges of the three
 * Home cards and the dock, never over their text, and follows the cards when they scroll away.
 *
 * Each piece is drawn once, shaded with a light from the top left, into a small bitmap when Home is built; a frame
 * only places those bitmaps (a spider bobbing, a pumpkin flickering, a lantern swinging), at {@link #FPS} frames a
 * second and only while Home is in front, so a 1 GB TV pays almost nothing for it.
 */
public final class DecorationView extends View {

    private static final int FPS = 20;
    private static final int[] BULBS = {0xFFEF5350, 0xFFFFD54F, 0xFF66BB6A, 0xFF4FC3F7, 0xFFE040FB};
    private static final int[] CARNIVAL = {0xFFE040FB, 0xFFFFD54F, 0xFF4FC3F7, 0xFF81C784, 0xFFFF7043};

    private final Season season;
    private final View[] cards;
    /** The top bar's pills (Inputs, network, Settings): Halloween decorates their outer edges too. */
    private final View[] pills;
    private final int[] here = new int[2], there = new int[2];
    private final RecyclerView grid;
    private final int dockCount;
    private final float d;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final Path path = new Path();
    private final RectF rect = new RectF(), dock = new RectF(), tile = new RectF();
    private final long start = SystemClock.uptimeMillis();
    private boolean running;
    private float fade = 1f;

    // Sprites, built once for the season.
    private Bitmap glowWarm, glowCool, spider, pumpkin, pumpkinSmall, pumpkinTiny, batPerched, icicle, gift, mask,
            lantern, crescent;
    private Bitmap[] bulbs, flags, balloons;

    public DecorationView(Context context, Season season, View[] cards, View[] pills, RecyclerView grid, int dockCount) {
        super(context);
        this.season = season;
        this.cards = cards;
        this.pills = pills;
        this.grid = grid;
        this.dockCount = Math.max(1, dockCount);
        d = context.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.setStyle(Paint.Style.STROKE);
        buildSprites();
    }

    public void start() {
        running = true;
        invalidate();
    }

    public void stop() {
        running = false;
    }

    // ---- per frame -----------------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        findDock();
        switch (season) {
            case HALLOWEEN: halloween(c, t); break;
            case CHRISTMAS: christmas(c, t); break;
            case NEW_YEAR: newYear(c, t); break;
            case CARNIVAL: bunting(c, t, flags); carnivalMask(c); break;
            default: lanterns(c, t);
        }
        if (running) postInvalidateDelayed(1000 / FPS);
    }

    /** The card's bounds on screen (it scrolls and fades with the grid), or false while it is not showing. */
    private boolean card(int i) {
        View v = cards[i];
        if (v == null || v.getVisibility() != VISIBLE || v.getAlpha() < 0.05f || v.getWidth() == 0) return false;
        onScreen(v, rect);
        setFade(v.getAlpha());
        return true;
    }

    /**
     * A view's bounds in this overlay's coordinates. The pills and the grid sit in containers of their own, so
     * their getX/getY are not screen positions; window positions (which include scrolling) are.
     */
    private void onScreen(View v, RectF out) {
        getLocationInWindow(here);
        v.getLocationInWindow(there);
        float x = there[0] - here[0], y = there[1] - here[1];
        out.set(x, y, x + v.getWidth(), y + v.getHeight());
    }

    /** Sprites and lines drawn next are faded by f (the card's own fade as it scrolls away). */
    private void setFade(float f) {
        fade = f;
        paint.setAlpha((int) (255 * f));
    }

    /** A line colour, faded with the card it belongs to. */
    private void lineColor(int argb) {
        line.setColor(argb);
        line.setAlpha((int) (Color.alpha(argb) * fade));
    }

    /**
     * The dock band's bounds: the union of the dock tiles' cards, as DockDecoration draws the band (the grid cells
     * are wider and taller than the cards in them), plus its 14dp padding; empty while the dock is scrolled away.
     */
    private void findDock() {
        dock.setEmpty();
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            int pos = grid.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockCount) continue;
            View card = child.findViewById(R.id.card);
            if (card == null) continue;
            onScreen(card, tile);
            if (dock.isEmpty()) dock.set(tile);
            else dock.union(tile);
        }
        if (dock.isEmpty()) return;
        dock.inset(-14 * d, -14 * d);
        if (dock.top < 0 || dock.top > getHeight()) dock.setEmpty();
    }

    /** Draws {@code b} centred at x, with its bottom (anchor 1) or top (anchor 0) at y, turned by deg. */
    private void sprite(Canvas c, Bitmap b, float x, float y, float anchor, float deg, float scaleX) {
        matrix.reset();
        matrix.postTranslate(-b.getWidth() / 2f, -b.getHeight() * anchor);
        matrix.postScale(scaleX, 1f);
        matrix.postRotate(deg);
        matrix.postTranslate(x, y);
        c.drawBitmap(b, matrix, paint);
    }

    private void glow(Canvas c, Bitmap g, float x, float y, float alpha) {
        int keep = paint.getAlpha();
        paint.setAlpha((int) (keep * Math.max(0f, Math.min(1f, alpha))));
        sprite(c, g, x, y + g.getHeight() / 2f, 1f, 0f, 1f);
        paint.setAlpha(keep);
    }

    private void halloween(Canvas c, float t) {
        if (card(0)) {
            hangingSpider(c, rect.left + rect.width() * 0.66f, rect.bottom, t, 0f, 1f);
            glow(c, glowWarm, rect.right - 34 * d, rect.top - 12 * d, flicker(t, 0f));
            sprite(c, pumpkinSmall, rect.right - 34 * d, rect.top + 3 * d, 1f, 0f, 1f);
        }
        if (card(1)) {
            glow(c, glowWarm, rect.right - 40 * d, rect.top - 16 * d, flicker(t, 2f));
            sprite(c, pumpkin, rect.right - 40 * d, rect.top + 4 * d, 1f, 0f, 1f);
        }
        if (card(2)) {
            hangingSpider(c, rect.left + rect.width() * 0.2f, rect.bottom, t, 1.7f, 0.85f);
        }
        pillsHalloween(c, t);
        if (!dock.isEmpty()) {
            setFade(1f);
            glow(c, glowWarm, dock.left + 26 * d, dock.top - 10 * d, flicker(t, 4f));
            sprite(c, pumpkinSmall, dock.left + 26 * d, dock.top + 6 * d, 1f, 0f, 1f);
            glow(c, glowWarm, dock.right - 30 * d, dock.top - 14 * d, flicker(t, 5f));
            sprite(c, pumpkin, dock.right - 30 * d, dock.top + 8 * d, 1f, 0f, 1f);
        }
    }

    /** A pumpkin on Inputs, a bat perched on the network pill, a spider under Settings; never over their labels. */
    private void pillsHalloween(Canvas c, float t) {
        setFade(1f);
        if (pill(0)) {
            glow(c, glowWarm, rect.left + 20 * d, rect.top - 10 * d, flicker(t, 6f) * 0.8f);
            sprite(c, pumpkinTiny, rect.left + 20 * d, rect.top + 5 * d, 1f, -8f, 1f);
        }
        if (pill(1)) {
            float bob = (float) Math.sin(t * 2.2f) * 1.2f * d;
            sprite(c, batPerched, rect.right - 26 * d, rect.top + 4 * d + bob, 1f, 0f, 1f);
        }
        if (pill(2)) hangingSpider(c, rect.right - 30 * d, rect.bottom - 2 * d, t, 3f, 0.7f);
    }

    private boolean pill(int i) {
        View v = pills == null || i >= pills.length ? null : pills[i];
        if (v == null || v.getVisibility() != VISIBLE || v.getWidth() == 0) return false;
        onScreen(v, rect);
        return true;
    }

    /** A lantern's flicker, kept low so a pumpkin glows softly rather than lighting up the screen. */
    private static float flicker(float t, float phase) {
        return 0.35f + 0.1f * (float) Math.sin(t * 7.3f + phase) + 0.06f * (float) Math.sin(t * 13.1f + phase * 2);
    }

    /** A spider on its thread under a card's bottom edge, bobbing slowly. */
    private void hangingSpider(Canvas c, float x, float top, float t, float phase, float scale) {
        float len = (26 + 12 * (float) Math.sin(t * 1.1f + phase)) * d;
        lineColor(0xCCE8EEF8);
        line.setStrokeWidth(1f * d / 2);
        c.drawLine(x, top, x, top + len, line);
        sprite(c, spider, x, top + len - 6 * d * scale, 0f, (float) Math.sin(t * 0.9f + phase) * 4f, scale);
    }

    private void christmas(Canvas c, float t) {
        for (int i = 0; i < 3; i++) {
            if (!card(i)) continue;
            snowCap(c);
            lightString(c, t, i);
            Random r = new Random(31 + i);
            for (int k = 0; k < 4; k++) {
                float x = rect.left + rect.width() * (0.12f + 0.22f * k + r.nextFloat() * 0.08f);
                sprite(c, icicle, x, rect.bottom - 2 * d, 0f, 0f, 0.7f + r.nextFloat() * 0.5f);
            }
        }
        if (!dock.isEmpty()) {
            setFade(1f);
            sprite(c, gift, dock.right - 40 * d, dock.top + 6 * d, 1f, 0f, 1f);
        }
    }

    /** Snow lying on a card's top edge, a soft bumpy white band with a blue shadow underneath. */
    private void snowCap(Canvas c) {
        path.reset();
        path.moveTo(rect.left + 10 * d, rect.top + 2 * d);
        Random r = new Random((long) rect.width());
        float x = rect.left + 10 * d;
        while (x < rect.right - 10 * d) {
            float w = (18 + r.nextInt(20)) * d;
            float nx = Math.min(rect.right - 10 * d, x + w);
            path.quadTo((x + nx) / 2, rect.top - (5 + r.nextInt(6)) * d, nx, rect.top + 1 * d);
            x = nx;
        }
        path.lineTo(rect.right - 10 * d, rect.top + 4 * d);
        path.lineTo(rect.left + 10 * d, rect.top + 4 * d);
        path.close();
        Paint snow = line;
        snow.setStyle(Paint.Style.FILL);
        snow.setShader(new LinearGradient(0, rect.top - 10 * d, 0, rect.top + 4 * d, 0xFFFFFFFF, 0xFFC8D8F0, Shader.TileMode.CLAMP));
        c.drawPath(path, snow);
        snow.setShader(null);
        snow.setStyle(Paint.Style.STROKE);
    }

    /** A sagging string of coloured bulbs just above a card, twinkling in turn. */
    private void lightString(Canvas c, float t, int card) {
        float x0 = rect.left + 14 * d, x1 = rect.right - 14 * d, y = rect.top - 12 * d, sag = 10 * d;
        lineColor(0xFF2A3044);
        line.setStrokeWidth(1.5f * d);
        path.reset();
        path.moveTo(x0, y);
        path.quadTo((x0 + x1) / 2, y + sag * 2, x1, y);
        c.drawPath(path, line);
        int n = Math.max(3, (int) ((x1 - x0) / (26 * d)));
        for (int i = 0; i <= n; i++) {
            float f = i / (float) n, x = x0 + (x1 - x0) * f, by = y + sag * 2 * 2 * f * (1 - f);
            Bitmap b = bulbs[(i + card) % bulbs.length];
            float tw = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(t * 2.4f + i * 1.7f + card));
            glow(c, glowCool, x, by + 8 * d, tw * 0.8f);
            sprite(c, b, x, by, 0f, (float) Math.sin(i * 1.3f) * 8f, 1f);
        }
    }

    private void newYear(Canvas c, float t) {
        if (card(0)) streamers(c, rect.left - 4 * d, rect.top + 14 * d, t, -1);
        if (card(2)) streamers(c, rect.right + 4 * d, rect.top + 14 * d, t, 1);
        if (card(1)) {
            for (int i = 0; i < 2; i++) {
                float bob = (float) Math.sin(t * 1.3f + i * 1.9f) * 4 * d;
                // On the left, away from the top bar's pills.
                float x = rect.left + (34 + i * 24) * d, y = rect.top - (30 + i * 8) * d + bob;
                lineColor(0xB3FFFFFF);
                line.setStrokeWidth(0.8f * d);
                c.drawLine(x, y + balloons[i].getHeight() / 2f, rect.left + 44 * d, rect.top + 2 * d, line);
                sprite(c, balloons[i], x, y, 0.5f, (float) Math.sin(t + i) * 5f, 1f);
            }
        }
    }

    /** Gold and silver ribbons curling down a card's outer edge (side -1 left, 1 right), swaying. */
    private void streamers(Canvas c, float x, float top, float t, int side) {
        int[] colors = {0xFFFFD54F, 0xFFE0E0E0, 0xFFFFB300};
        for (int k = 0; k < 3; k++) {
            lineColor(colors[k]);
            line.setStrokeWidth(3 * d);
            line.setStrokeCap(Paint.Cap.ROUND);
            path.reset();
            float sx = x + side * k * 6 * d;
            path.moveTo(sx, top);
            for (int s = 1; s <= 10; s++) {
                float yy = top + s * 7 * d;
                float xx = sx + side * (2 + (float) Math.sin(s * 0.9f + t * 1.6f + k)) * 3 * d;
                path.lineTo(xx, yy);
            }
            c.drawPath(path, line);
        }
    }

    /** Bunting across the top of each card: a sagging cord with folded paper flags that sway a little. */
    private void bunting(Canvas c, float t, Bitmap[] set) {
        for (int i = 0; i < 3; i++) {
            if (!card(i)) continue;
            // High enough that the flags end above the card's title.
            float x0 = rect.left + 6 * d, x1 = rect.right - 6 * d, y = rect.top - 30 * d, sag = 5 * d;
            lineColor(0xCCDDDDE6);
            line.setStrokeWidth(1.2f * d);
            path.reset();
            path.moveTo(x0, y);
            path.quadTo((x0 + x1) / 2, y + sag * 2, x1, y);
            c.drawPath(path, line);
            int n = Math.max(4, (int) ((x1 - x0) / (22 * d)));
            for (int k = 1; k < n; k++) {
                float f = k / (float) n, x = x0 + (x1 - x0) * f, fy = y + sag * 2 * 2 * f * (1 - f);
                sprite(c, set[(k + i) % set.length], x, fy, 0f, (float) Math.sin(t * 1.8f + k) * 6f, 1f);
            }
        }
    }

    private void carnivalMask(Canvas c) {
        if (card(0)) sprite(c, mask, rect.right - 36 * d, rect.top + 10 * d, 1f, -12f, 1f);
    }

    /** Lanterns on chains under each card, swinging gently, and a crescent with a star on the today card. */
    private void lanterns(Canvas c, float t) {
        for (int i = 0; i < 3; i++) {
            if (!card(i)) continue;
            for (int k = 0; k < 2; k++) {
                float x = rect.left + rect.width() * (k == 0 ? 0.22f : 0.78f), top = rect.bottom - 2 * d;
                float chain = (14 + ((i + k) % 2) * 12) * d;
                float swing = (float) Math.sin(t * 1.4f + i + k * 1.3f) * 5f;
                c.save();
                c.rotate(swing, x, top);
                lineColor(0xFFB08D3A);
                line.setStrokeWidth(1.2f * d);
                c.drawLine(x, top, x, top + chain, line);
                glow(c, glowWarm, x, top + chain + 2 * d, 0.55f + 0.15f * (float) Math.sin(t * 2f + k));
                sprite(c, lantern, x, top + chain, 0f, 0f, 1f);
                c.restore();
            }
        }
        if (card(1)) sprite(c, crescent, rect.left + 30 * d, rect.top + 6 * d, 1f, -10f, 1f);
    }

    // ---- sprites, drawn once -------------------------------------------------------------------------------

    private void buildSprites() {
        glowWarm = glowBitmap(0xFFFFA040, 70);
        glowCool = glowBitmap(0xFFFFF2C0, 22);
        switch (season) {
            case HALLOWEEN:
                spider = spiderBitmap(30);
                pumpkin = pumpkinBitmap(46);
                pumpkinSmall = pumpkinBitmap(32);
                pumpkinTiny = pumpkinBitmap(22);
                batPerched = perchedBatBitmap(26);
                break;
            case CHRISTMAS:
                bulbs = new Bitmap[BULBS.length];
                for (int i = 0; i < BULBS.length; i++) bulbs[i] = bulbBitmap(BULBS[i], 12);
                icicle = icicleBitmap(10, 20);
                gift = giftBitmap(34);
                break;
            case NEW_YEAR:
                balloons = new Bitmap[]{balloonBitmap(0xFFFFC83D, 30), balloonBitmap(0xFFD7DCE2, 26)};
                break;
            case CARNIVAL:
                flags = flagsFor(CARNIVAL);
                mask = maskBitmap(48);
                break;
            default:
                lantern = lanternBitmap(24);
                crescent = crescentBitmap(36);
        }
    }

    private Bitmap blank(float wDp, float hDp) {
        return Bitmap.createBitmap(Math.max(1, Math.round(wDp * d)), Math.max(1, Math.round(hDp * d)), Bitmap.Config.ARGB_8888);
    }

    private Bitmap glowBitmap(int color, float rDp) {
        Bitmap b = blank(rDp * 2, rDp * 2);
        float r = b.getWidth() / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(r, r, r, (color & 0x00FFFFFF) | 0x80000000, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        new Canvas(b).drawCircle(r, r, r, p);
        return b;
    }

    /** A shaded ball: lit from the top left, darker round the bottom right. */
    private static Shader ball(float cx, float cy, float r, int color) {
        return new RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.5f,
                new int[]{lighten(color, 0.45f), color, darken(color, 0.45f)}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
    }

    private Bitmap pumpkinBitmap(float sizeDp) {
        Bitmap b = blank(sizeDp * 1.2f, sizeDp);
        Canvas c = new Canvas(b);
        float w = b.getWidth(), h = b.getHeight(), cy = h * 0.6f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float[][] lobes = {{0.2f, 0.2f}, {0.8f, 0.2f}, {0.33f, 0.24f}, {0.67f, 0.24f}, {0.5f, 0.26f}};
        for (float[] l : lobes) {
            float lx = w * l[0], rx = w * l[1];
            p.setShader(ball(lx, cy, h * 0.4f, 0xFFE8741C));
            c.drawOval(lx - rx, cy - h * 0.38f, lx + rx, cy + h * 0.38f, p);
        }
        p.setShader(null);
        p.setColor(0xFF4A6B2A);
        c.drawRoundRect(w * 0.46f, h * 0.06f, w * 0.54f, h * 0.26f, 2 * d, 2 * d, p);
        p.setShader(new LinearGradient(0, cy - h * 0.2f, 0, cy + h * 0.25f, 0xFFE0A050, 0xFFB85A14, Shader.TileMode.CLAMP));
        Path face = new Path();
        face.moveTo(w * 0.3f, cy - h * 0.05f);
        face.lineTo(w * 0.4f, cy - h * 0.2f);
        face.lineTo(w * 0.45f, cy - h * 0.03f);
        face.close();
        face.moveTo(w * 0.7f, cy - h * 0.05f);
        face.lineTo(w * 0.6f, cy - h * 0.2f);
        face.lineTo(w * 0.55f, cy - h * 0.03f);
        face.close();
        face.moveTo(w * 0.28f, cy + h * 0.06f);
        face.quadTo(w * 0.5f, cy + h * 0.32f, w * 0.72f, cy + h * 0.06f);
        face.lineTo(w * 0.62f, cy + h * 0.12f);
        face.lineTo(w * 0.56f, cy + h * 0.06f);
        face.lineTo(w * 0.5f, cy + h * 0.14f);
        face.lineTo(w * 0.44f, cy + h * 0.06f);
        face.lineTo(w * 0.38f, cy + h * 0.12f);
        face.close();
        c.drawPath(face, p);
        return b;
    }

    private Bitmap spiderBitmap(float sizeDp) {
        Bitmap b = blank(sizeDp * 1.6f, sizeDp * 1.4f);
        Canvas c = new Canvas(b);
        float w = b.getWidth(), h = b.getHeight(), cx = w / 2;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(1.6f * d);
        p.setColor(0xFF120B14);
        float by = h * 0.55f;
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < 4; k++) {
                float ky = by - h * 0.12f + k * h * 0.1f;
                Path leg = new Path();
                leg.moveTo(cx, ky);
                leg.lineTo(cx + s * w * 0.24f, ky - h * 0.16f + k * h * 0.04f);
                leg.lineTo(cx + s * w * 0.42f, ky + h * 0.08f + k * h * 0.05f);
                c.drawPath(leg, p);
            }
        }
        p.setStyle(Paint.Style.FILL);
        p.setShader(ball(cx, by, h * 0.22f, 0xFF2A1830));
        c.drawOval(cx - w * 0.16f, by - h * 0.18f, cx + w * 0.16f, by + h * 0.24f, p);
        p.setShader(ball(cx, h * 0.28f, h * 0.12f, 0xFF2A1830));
        c.drawCircle(cx, h * 0.3f, h * 0.12f, p);
        p.setShader(null);
        p.setColor(0xFFFF4A2A);
        c.drawCircle(cx - h * 0.05f, h * 0.31f, 1.4f * d, p);
        c.drawCircle(cx + h * 0.05f, h * 0.31f, 1.4f * d, p);
        return b;
    }

    /** A bat sitting upright with its wings folded, ears up and orange eyes, lit from the top left. */
    private Bitmap perchedBatBitmap(float sizeDp) {
        Bitmap b = blank(sizeDp * 1.3f, sizeDp);
        Canvas c = new Canvas(b);
        float w = b.getWidth(), h = b.getHeight(), cx = w / 2;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path wings = new Path();
        wings.moveTo(cx, h * 0.3f);
        wings.cubicTo(cx - w * 0.5f, h * 0.1f, cx - w * 0.55f, h * 0.7f, cx - w * 0.2f, h);
        wings.lineTo(cx + w * 0.2f, h);
        wings.cubicTo(cx + w * 0.55f, h * 0.7f, cx + w * 0.5f, h * 0.1f, cx, h * 0.3f);
        wings.close();
        p.setShader(new LinearGradient(cx - w * 0.4f, 0, cx + w * 0.4f, h, 0xFF4A3458, 0xFF120A18, Shader.TileMode.CLAMP));
        c.drawPath(wings, p);
        p.setShader(ball(cx, h * 0.55f, w * 0.2f, 0xFF2A1830));
        c.drawOval(cx - w * 0.17f, h * 0.3f, cx + w * 0.17f, h * 0.95f, p);
        p.setShader(ball(cx, h * 0.24f, w * 0.14f, 0xFF2A1830));
        c.drawCircle(cx, h * 0.26f, w * 0.13f, p);
        p.setShader(null);
        p.setColor(0xFF1C1024);
        Path ears = new Path();
        ears.moveTo(cx - w * 0.12f, h * 0.2f);
        ears.lineTo(cx - w * 0.1f, 0);
        ears.lineTo(cx - w * 0.02f, h * 0.16f);
        ears.close();
        ears.moveTo(cx + w * 0.12f, h * 0.2f);
        ears.lineTo(cx + w * 0.1f, 0);
        ears.lineTo(cx + w * 0.02f, h * 0.16f);
        ears.close();
        c.drawPath(ears, p);
        p.setColor(0xFFFFA040);
        c.drawCircle(cx - w * 0.05f, h * 0.26f, 1.3f * d, p);
        c.drawCircle(cx + w * 0.05f, h * 0.26f, 1.3f * d, p);
        return b;
    }

    private Bitmap bulbBitmap(int color, float sizeDp) {
        Bitmap b = blank(sizeDp, sizeDp * 1.6f);
        Canvas c = new Canvas(b);
        float w = b.getWidth(), h = b.getHeight();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, 0, 0xFFE6D48A, 0xFF8A7A3A, Shader.TileMode.CLAMP));
        c.drawRoundRect(w * 0.3f, 0, w * 0.7f, h * 0.24f, 1.5f * d, 1.5f * d, p);
        p.setShader(ball(w / 2, h * 0.6f, w * 0.45f, color));
        c.drawOval(w * 0.08f, h * 0.2f, w * 0.92f, h, p);
        p.setShader(null);
        p.setColor(0xB3FFFFFF);
        c.drawOval(w * 0.25f, h * 0.32f, w * 0.45f, h * 0.55f, p);
        return b;
    }

    private Bitmap icicleBitmap(float wDp, float hDp) {
        Bitmap b = blank(wDp, hDp);
        Canvas c = new Canvas(b);
        float w = b.getWidth(), h = b.getHeight();
        Path p = new Path();
        p.moveTo(0, 0);
        p.lineTo(w, 0);
        p.lineTo(w * 0.5f, h);
        p.close();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, w, 0, new int[]{0xE6FFFFFF, 0xCCBFE3FF, 0x8099C8F0}, null, Shader.TileMode.CLAMP));
        c.drawPath(p, paint);
        return b;
    }

    private Bitmap giftBitmap(float sizeDp) {
        Bitmap b = blank(sizeDp, sizeDp);
        Canvas c = new Canvas(b);
        float s = b.getWidth();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, s, s, 0xFFE53935, 0xFF8E1B1B, Shader.TileMode.CLAMP));
        c.drawRoundRect(s * 0.08f, s * 0.38f, s * 0.92f, s, 2 * d, 2 * d, p);
        p.setShader(new LinearGradient(0, 0, s, 0, 0xFFEF5350, 0xFFB71C1C, Shader.TileMode.CLAMP));
        c.drawRoundRect(0, s * 0.28f, s, s * 0.44f, 2 * d, 2 * d, p);
        p.setShader(new LinearGradient(0, 0, s, 0, 0xFFFFE082, 0xFFC9A227, Shader.TileMode.CLAMP));
        c.drawRect(s * 0.44f, s * 0.28f, s * 0.56f, s, p);
        c.drawOval(s * 0.18f, s * 0.08f, s * 0.5f, s * 0.32f, p);
        c.drawOval(s * 0.5f, s * 0.08f, s * 0.82f, s * 0.32f, p);
        return b;
    }

    private Bitmap balloonBitmap(int color, float sizeDp) {
        Bitmap b = blank(sizeDp, sizeDp * 1.25f);
        Canvas c = new Canvas(b);
        float w = b.getWidth(), h = b.getHeight();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(ball(w / 2, h * 0.42f, w * 0.5f, color));
        c.drawOval(0, 0, w, h * 0.88f, p);
        p.setShader(null);
        p.setColor(darken(color, 0.3f));
        Path knot = new Path();
        knot.moveTo(w * 0.44f, h * 0.88f);
        knot.lineTo(w * 0.56f, h * 0.88f);
        knot.lineTo(w * 0.5f, h);
        knot.close();
        c.drawPath(knot, p);
        p.setColor(0xCCFFFFFF);
        c.drawOval(w * 0.2f, h * 0.12f, w * 0.38f, h * 0.36f, p);
        return b;
    }

    /** One paper flag per colour, folded down the middle so one half catches the light. */
    private Bitmap[] flagsFor(int[] colors) {
        Bitmap[] out = new Bitmap[colors.length];
        for (int i = 0; i < colors.length; i++) {
            Bitmap b = blank(16, 20);
            Canvas c = new Canvas(b);
            float w = b.getWidth(), h = b.getHeight();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Path left = new Path();
            left.moveTo(0, 0);
            left.lineTo(w / 2, 0);
            left.lineTo(w / 2, h);
            left.close();
            Path right = new Path();
            right.moveTo(w / 2, 0);
            right.lineTo(w, 0);
            right.lineTo(w / 2, h);
            right.close();
            p.setColor(lighten(colors[i], 0.2f));
            c.drawPath(left, p);
            p.setColor(darken(colors[i], 0.2f));
            c.drawPath(right, p);
            out[i] = b;
        }
        return out;
    }

    private Bitmap maskBitmap(float sizeDp) {
        Bitmap b = blank(sizeDp, sizeDp * 0.6f);
        Canvas c = new Canvas(b);
        float w = b.getWidth(), h = b.getHeight();
        Path m = new Path();
        m.moveTo(0, h * 0.35f);
        m.quadTo(w * 0.25f, -h * 0.1f, w * 0.5f, h * 0.3f);
        m.quadTo(w * 0.75f, -h * 0.1f, w, h * 0.35f);
        m.quadTo(w * 0.9f, h * 1.05f, w * 0.62f, h * 0.85f);
        m.quadTo(w * 0.5f, h * 0.6f, w * 0.38f, h * 0.85f);
        m.quadTo(w * 0.1f, h * 1.05f, 0, h * 0.35f);
        m.close();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, 0xFFFFE08A, 0xFF9C7A10, Shader.TileMode.CLAMP));
        c.drawPath(m, p);
        p.setShader(null);
        p.setColor(0xFF120820);
        c.drawOval(w * 0.16f, h * 0.38f, w * 0.4f, h * 0.62f, p);
        c.drawOval(w * 0.6f, h * 0.38f, w * 0.84f, h * 0.62f, p);
        p.setColor(0xFFE040FB);
        c.drawCircle(w * 0.5f, h * 0.24f, 2.5f * d, p);
        return b;
    }

    private Bitmap lanternBitmap(float sizeDp) {
        Bitmap b = blank(sizeDp, sizeDp * 1.7f);
        Canvas c = new Canvas(b);
        float w = b.getWidth(), h = b.getHeight();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, 0, 0xFFE0C068, 0xFF7A5C1C, Shader.TileMode.CLAMP));
        Path cap = new Path();
        cap.moveTo(w * 0.3f, 0);
        cap.lineTo(w * 0.7f, 0);
        cap.lineTo(w * 0.85f, h * 0.16f);
        cap.lineTo(w * 0.15f, h * 0.16f);
        cap.close();
        c.drawPath(cap, p);
        Path body = new Path();
        body.moveTo(w * 0.1f, h * 0.16f);
        body.lineTo(w * 0.9f, h * 0.16f);
        body.lineTo(w, h * 0.3f);
        body.lineTo(w, h * 0.75f);
        body.lineTo(w * 0.8f, h * 0.88f);
        body.lineTo(w * 0.2f, h * 0.88f);
        body.lineTo(0, h * 0.75f);
        body.lineTo(0, h * 0.3f);
        body.close();
        c.drawPath(body, p);
        p.setShader(new RadialGradient(w * 0.45f, h * 0.5f, w * 0.6f, 0xFFFFF4C8, 0xFFFFA726, Shader.TileMode.CLAMP));
        c.drawRect(w * 0.16f, h * 0.24f, w * 0.84f, h * 0.78f, p);
        p.setShader(null);
        p.setColor(0xFF7A5C1C);
        c.drawRect(w * 0.47f, h * 0.24f, w * 0.53f, h * 0.78f, p);
        c.drawRect(w * 0.16f, h * 0.49f, w * 0.84f, h * 0.53f, p);
        Path tip = new Path();
        tip.moveTo(w * 0.35f, h * 0.88f);
        tip.lineTo(w * 0.65f, h * 0.88f);
        tip.lineTo(w * 0.5f, h);
        tip.close();
        c.drawPath(tip, p);
        return b;
    }

    private Bitmap crescentBitmap(float sizeDp) {
        Bitmap b = blank(sizeDp * 1.5f, sizeDp);
        Canvas c = new Canvas(b);
        float h = b.getHeight(), r = h / 2;
        Path moon = new Path();
        moon.addCircle(r, r, r, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle(r * 1.5f, r * 0.8f, r * 0.9f, Path.Direction.CW);
        moon.op(cut, Path.Op.DIFFERENCE);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, r * 2, r * 2, 0xFFFFE9A8, 0xFFC9A227, Shader.TileMode.CLAMP));
        c.drawPath(moon, p);
        Path star = new Path();
        float sx = r * 2.3f, sy = r * 0.7f, sr = r * 0.32f;
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = i % 2 == 0 ? sr : sr * 0.42f;
            if (i == 0) star.moveTo(sx + (float) Math.cos(a) * rr, sy + (float) Math.sin(a) * rr);
            else star.lineTo(sx + (float) Math.cos(a) * rr, sy + (float) Math.sin(a) * rr);
        }
        star.close();
        c.drawPath(star, p);
        return b;
    }

    private static int lighten(int c, float f) {
        return Color.argb(Color.alpha(c), (int) (Color.red(c) + (255 - Color.red(c)) * f),
                (int) (Color.green(c) + (255 - Color.green(c)) * f), (int) (Color.blue(c) + (255 - Color.blue(c)) * f));
    }

    private static int darken(int c, float f) {
        return Color.argb(Color.alpha(c), (int) (Color.red(c) * (1 - f)), (int) (Color.green(c) * (1 - f)),
                (int) (Color.blue(c) * (1 - f)));
    }
}
