package com.gathin.tvhome.decor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

import com.gathin.tvhome.calendar.Season;
import java.util.Random;

/**
 * A transparent overlay that plays one short holiday effect (see Season) over Home, the first time it shows each
 * day: snow, bats, confetti or lanterns. Like PetalShowerView it takes no focus or clicks, uses a
 * hardware layer only while playing, works out every particle from the clock (nothing is allocated per frame),
 * and calls back once everything has faded.
 */
public final class SeasonShowerView extends View {

    private static final int N = 70;
    private static final int[] CONFETTI = {0xFFE040FB, 0xFFFFD54F, 0xFF4FC3F7, 0xFF81C784, 0xFFFF7043};

    private final float density;
    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bat = new Path();
    private final float[] x = new float[N], delay = new float[N], speed = new float[N], size = new float[N],
            phase = new float[N], spin = new float[N];
    private final int[] color = new int[N];

    private Season.Effect effect = Season.Effect.SNOW;
    private boolean playing;
    private long start, duration;
    private Runnable onDone;

    public SeasonShowerView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
        // A bat 1 unit wide, centred: two scalloped wings and a small body, flapped by scaling it vertically.
        bat.moveTo(0f, 0.05f);
        bat.cubicTo(-0.12f, -0.12f, -0.32f, -0.18f, -0.5f, -0.06f);
        bat.cubicTo(-0.42f, 0.0f, -0.38f, 0.06f, -0.36f, 0.12f);
        bat.cubicTo(-0.28f, 0.06f, -0.2f, 0.1f, -0.14f, 0.16f);
        bat.cubicTo(-0.08f, 0.1f, -0.04f, 0.14f, 0f, 0.2f);
        bat.cubicTo(0.04f, 0.14f, 0.08f, 0.1f, 0.14f, 0.16f);
        bat.cubicTo(0.2f, 0.1f, 0.28f, 0.06f, 0.36f, 0.12f);
        bat.cubicTo(0.38f, 0.06f, 0.42f, 0.0f, 0.5f, -0.06f);
        bat.cubicTo(0.32f, -0.18f, 0.12f, -0.12f, 0f, 0.05f);
        bat.close();
    }

    /** Plays the effect over {@code ms} milliseconds, then runs {@code done} on the main thread. */
    public void play(Season.Effect effect, long ms, Runnable done) {
        this.effect = effect;
        this.duration = ms;
        this.onDone = done;
        int count = effect == Season.Effect.BATS ? 9 : effect == Season.Effect.LANTERNS ? 34 : N;
        for (int i = 0; i < N; i++) {
            x[i] = rnd.nextFloat();
            delay[i] = rnd.nextFloat() * 0.45f;
            speed[i] = 0.6f + rnd.nextFloat() * 0.8f;
            size[i] = 0.5f + rnd.nextFloat();
            phase[i] = rnd.nextFloat() * 6.283f;
            spin[i] = (rnd.nextFloat() - 0.5f) * 720f;
            int[] palette = effect == Season.Effect.CONFETTI ? CONFETTI : null;
            color[i] = i >= count ? 0 : palette == null ? 0xFFFFFFFF : palette[rnd.nextInt(palette.length)];
        }
        setLayerType(LAYER_TYPE_HARDWARE, null);
        start = SystemClock.uptimeMillis();
        playing = true;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!playing) return;
        float t = (SystemClock.uptimeMillis() - start) / (float) duration;
        if (t >= 1f) {
            playing = false;
            setLayerType(LAYER_TYPE_NONE, null);
            if (onDone != null) post(onDone);
            return;
        }
        int w = getWidth(), h = getHeight();
        for (int i = 0; i < N; i++) {
            if (color[i] == 0) continue;
            // Each particle lives over (1 - delay) of the run and fades in its last quarter.
            float life = (t - delay[i]) / (1f - delay[i]);
            if (life <= 0f || life >= 1f) continue;
            int alpha = (int) (255 * Math.min(1f, Math.min(life * 6f, (1f - life) * 4f)));
            switch (effect) {
                case SNOW: {
                    float y = -20 * density + life * speed[i] * (h + 40 * density);
                    float px = x[i] * w + (float) Math.sin(phase[i] + life * 6f) * 24 * density;
                    paint.setColor(withAlpha(color[i], (int) (alpha * 0.85f)));
                    canvas.drawCircle(px, y, (1.5f + size[i] * 2.5f) * density, paint);
                    break;
                }
                case CONFETTI: {
                    float y = -20 * density + life * (0.7f + speed[i] * 0.5f) * (h + 40 * density);
                    float px = x[i] * w + (float) Math.sin(phase[i] + life * 8f) * 30 * density;
                    paint.setColor(withAlpha(color[i], alpha));
                    canvas.save();
                    canvas.translate(px, y);
                    canvas.rotate(spin[i] * life);
                    float s = size[i] * density;
                    canvas.drawRect(-4 * s, -2.5f * s, 4 * s, 2.5f * s, paint);
                    canvas.restore();
                    break;
                }
                case BATS: {
                    // Up and across from the lower edge, flapping.
                    boolean left = (i % 2) == 0;
                    float px = left ? -0.1f * w + life * 1.2f * w * speed[i] : 1.1f * w - life * 1.2f * w * speed[i];
                    float py = h * (0.8f - life * 0.7f) + (float) Math.sin(phase[i] + life * 9f) * 30 * density;
                    float span = (60 + size[i] * 50) * density;
                    float flap = 0.35f + 0.65f * Math.abs((float) Math.sin(life * 40f + phase[i]));
                    paint.setColor(withAlpha(0xFF2A1630, alpha));
                    canvas.save();
                    canvas.translate(px, py);
                    canvas.scale(left ? span : -span, span * flap);
                    canvas.drawPath(bat, paint);
                    canvas.restore();
                    break;
                }
                case LANTERNS: {
                    // Warm lanterns rising slowly from the bottom, swaying, each with a soft halo.
                    float y = h + 30 * density - life * (0.6f + speed[i] * 0.4f) * (h * 0.9f);
                    float px = x[i] * w + (float) Math.sin(phase[i] + life * 4f) * 18 * density;
                    float r = (5 + size[i] * 5) * density;
                    paint.setColor(withAlpha(0xFFFFB74D, alpha / 5));
                    canvas.drawCircle(px, y, r * 2.6f, paint);
                    paint.setColor(withAlpha(0xFFFFD580, alpha));
                    canvas.drawRoundRect(px - r * 0.7f, y - r, px + r * 0.7f, y + r, r * 0.4f, r * 0.4f, paint);
                    break;
                }
            }
        }
        postInvalidateOnAnimation();
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }
}
