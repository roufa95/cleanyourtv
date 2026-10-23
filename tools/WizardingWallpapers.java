import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Random;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Draws the wallpapers of themes/wizarding-night: six dark, original night scenes in a storybook-castle mood
 * (a castle over a lake, floating candles, a misty forest, a candlelit library, a steam train on a viaduct, an
 * owl across the moon). Everything is drawn from code with Java 2D: no photos, no characters, no logos.
 * Every scene keeps its lower third dark, because the dock, the grid and the text sit there, and puts its moon
 * at the top centre, the one patch of sky that the clock, the pills and the Home cards leave open.
 *
 *     java tools/WizardingWallpapers.java themes/wizarding-night/wallpapers
 *
 * It also draws two scenes per holiday season into wallpapers/<season>/ (halloween, christmas, new_year, carnival,
 * ramadan; Eid uses Ramadan's). The launcher shows those in their period when it has no
 * downloaded photo for it (see PhotoFetch in the launcher).
 *
 * The random seeds are fixed, so running it again gives the same pictures.
 */
public class WizardingWallpapers {

    static final int W = 1920, H = 1080;

    public static void main(String[] args) throws IOException {
        File out = new File(args.length > 0 ? args[0] : "themes/wizarding-night/wallpapers");
        out.mkdirs();
        save(castleLake(), new File(out, "01-castle-lake.jpg"));
        save(candleHall(), new File(out, "02-candle-hall.jpg"));
        save(mistyForest(), new File(out, "03-misty-forest.jpg"));
        save(library(), new File(out, "04-library.jpg"));
        save(viaduct(), new File(out, "05-night-train.jpg"));
        save(owlMoon(), new File(out, "06-owl-moon.jpg"));
        seasons(out);
    }

    // ---- scenes ---------------------------------------------------------------------------------------------

    static BufferedImage castleLake() {
        Random r = new Random(11);
        BufferedImage img = canvas();
        Graphics2D g = g(img);
        sky(g, new Color(0x0B1230), new Color(0x1C2350), new Color(0x2A2346), 0.62);
        stars(g, r, 420, 0, (int) (H * 0.55));
        moon(g, 985, 112, 58, new Color(0xE9E4D0), false);
        Color hill = new Color(0x0A0D1C);
        ridge(g, r, 0.58, 0.05, 6, new Color(0x141A33));
        // The cliff and the castle on it, right of centre.
        Path2D cliff = new Path2D.Double();
        cliff.moveTo(760, H * 0.66);
        cliff.lineTo(860, 560);
        cliff.lineTo(1010, 520);
        cliff.lineTo(1290, 515);
        cliff.lineTo(1400, 560);
        cliff.lineTo(1480, H * 0.66);
        cliff.closePath();
        g.setColor(hill);
        g.fill(cliff);
        castle(g, r, 900, 520, 1.0, hill, true);
        lake(img, g, (int) (H * 0.66));
        mist(g, r, 14, (int) (H * 0.6), (int) (H * 0.7), new Color(150, 160, 200), 0.05f);
        finish(img, g, r);
        return img;
    }

    static BufferedImage candleHall() {
        Random r = new Random(22);
        BufferedImage img = canvas();
        Graphics2D g = g(img);
        // A dark vaulted hall: the "ceiling" a starry dusk, stone arches at the sides.
        sky(g, new Color(0x070914), new Color(0x161A33), new Color(0x1E1A2A), 0.7);
        stars(g, r, 260, 0, (int) (H * 0.5));
        Color stone = new Color(0x0C0D16);
        for (int i = 0; i < 2; i++) {
            int x = i == 0 ? 0 : W - 330;
            g.setColor(stone);
            g.fillRect(x, 0, 330, H);
            for (int k = 0; k < 3; k++) {   // tall arched windows with faint moonlight
                int wx = x + 95, wy = 120 + k * 290;
                Path2D arch = new Path2D.Double();
                arch.moveTo(wx, wy + 230);
                arch.lineTo(wx, wy + 70);
                arch.quadTo(wx + 70, wy - 40, wx + 140, wy + 70);
                arch.lineTo(wx + 140, wy + 230);
                arch.closePath();
                g.setPaint(new GradientPaint(wx, wy, new Color(0x2A3566), wx, wy + 230, new Color(0x121731)));
                g.fill(arch);
                g.setColor(stone);
                g.setStroke(new BasicStroke(6));
                g.drawLine(wx + 70, wy + 10, wx + 70, wy + 230);
                g.drawLine(wx, wy + 140, wx + 140, wy + 140);
            }
        }
        // Long tables far below, only as darker bands.
        g.setPaint(new GradientPaint(0, H * 0.7f, new Color(0, 0, 0, 0), 0, H, new Color(0x030308)));
        g.fillRect(0, (int) (H * 0.6), W, (int) (H * 0.4));
        // Floating candles: smaller and dimmer further away.
        for (int i = 0; i < 70; i++) {
            double depth = r.nextDouble();
            double s = 0.35 + depth * 0.9;
            int x = 360 + r.nextInt(W - 720);
            int y = (int) (90 + (1 - depth) * 260 + r.nextGaussian() * 60 + depth * 230);
            if (y > H * 0.62) continue;
            candle(g, x, y, s);
        }
        finish(img, g, r);
        return img;
    }

    static BufferedImage mistyForest() {
        Random r = new Random(33);
        BufferedImage img = canvas();
        Graphics2D g = g(img);
        sky(g, new Color(0x06120F), new Color(0x0F2A26), new Color(0x1F3A3A), 0.55);
        stars(g, r, 120, 0, (int) (H * 0.35));
        moon(g, 960, 110, 46, new Color(0xDDE8E2), false);
        // Layers of pines, each darker and larger than the one behind it, with mist between them.
        Color[] layers = {new Color(0x183632), new Color(0x10261F), new Color(0x0A1914), new Color(0x050D0A)};
        for (int l = 0; l < layers.length; l++) {
            double scale = 0.55 + l * 0.35;
            int base = (int) (H * (0.56 + l * 0.08));
            g.setColor(layers[l]);
            g.fillRect(0, base, W, H - base);
            for (int x = -40; x < W + 40; x += (int) (34 * scale + r.nextInt((int) (40 * scale) + 1))) {
                pine(g, x, base + 4, (int) ((150 + r.nextInt(120)) * scale), layers[l]);
            }
            mist(g, r, 10, base - 120, base + 40, new Color(170, 200, 190), 0.045f);
        }
        // A path of lantern lights winding into the trees.
        for (int i = 0; i < 9; i++) {
            double t = i / 8.0;
            int x = (int) (900 + Math.sin(t * 5) * 140 * (1 - t) + t * 140);
            int y = (int) (H * 0.86 - t * 230);
            glow(g, x, y, (int) (60 - t * 40), new Color(255, 190, 110), 0.55f);
            g.setColor(new Color(255, 225, 170));
            int d = (int) Math.max(3, 9 - t * 6);
            g.fill(new Ellipse2D.Double(x - d / 2.0, y - d / 2.0, d, d));
        }
        // Fireflies.
        for (int i = 0; i < 40; i++) {
            int x = r.nextInt(W), y = (int) (H * 0.55 + r.nextInt((int) (H * 0.35)));
            glow(g, x, y, 10, new Color(220, 255, 170), 0.5f);
        }
        finish(img, g, r);
        return img;
    }

    static BufferedImage library() {
        Random r = new Random(44);
        BufferedImage img = canvas();
        Graphics2D g = g(img);
        g.setPaint(new GradientPaint(0, 0, new Color(0x120C0A), 0, H, new Color(0x050303)));
        g.fillRect(0, 0, W, H);
        // An arched window with the moon in it, centre.
        int wx = 760, wy = 110, ww = 400, wh = 520;
        Path2D arch = new Path2D.Double();
        arch.moveTo(wx, wy + wh);
        arch.lineTo(wx, wy + 200);
        arch.quadTo(wx + ww / 2.0, wy - 90, wx + ww, wy + 200);
        arch.lineTo(wx + ww, wy + wh);
        arch.closePath();
        g.setPaint(new GradientPaint(wx, wy, new Color(0x1D2A55), wx, wy + wh, new Color(0x0D1430)));
        g.fill(arch);
        g.setClip(arch);
        stars(g, r, 90, wy, wy + wh);
        moon(g, wx + 270, wy + 190, 48, new Color(0xE8E6DA), false);
        g.setClip(null);
        g.setColor(new Color(0x0A0605));
        g.setStroke(new BasicStroke(10));
        g.drawLine(wx + ww / 2, wy + 40, wx + ww / 2, wy + wh);
        for (int k = 1; k < 4; k++) g.drawLine(wx, wy + 150 + k * 95, wx + ww, wy + 150 + k * 95);
        g.setStroke(new BasicStroke(14));
        g.draw(arch);
        // Moonlight falling into the room.
        Path2D beam = new Path2D.Double();
        beam.moveTo(wx + 40, wy + wh);
        beam.lineTo(wx + ww - 40, wy + wh);
        beam.lineTo(wx + ww + 260, H);
        beam.lineTo(wx - 120, H);
        beam.closePath();
        g.setPaint(new GradientPaint(0, wy + wh, new Color(120, 140, 210, 34), 0, H, new Color(120, 140, 210, 0)));
        g.fill(beam);
        // Shelves of books left and right.
        for (int side = 0; side < 2; side++) {
            int x0 = side == 0 ? 40 : W - 640, x1 = x0 + 600;
            g.setColor(new Color(0x1A100B));
            g.fillRect(x0 - 20, 40, 640, H);
            for (int shelf = 0; shelf < 7; shelf++) {
                int top = 70 + shelf * 140;
                g.setColor(new Color(0x0C0705));
                g.fillRect(x0, top + 118, 600, 16);
                int x = x0 + 6;
                while (x < x1 - 10) {
                    int bw = 14 + r.nextInt(22), bh = 80 + r.nextInt(36);
                    if (x + bw > x1 - 6) break;
                    float hue = new float[]{0.0f, 0.02f, 0.08f, 0.33f, 0.6f, 0.95f}[r.nextInt(6)];
                    Color c = Color.getHSBColor(hue, 0.55f, 0.16f + r.nextFloat() * 0.12f);
                    g.setColor(c);
                    if (r.nextInt(14) == 0) {   // a leaning book
                        Graphics2D t = (Graphics2D) g.create();
                        t.rotate(Math.toRadians(-12), x, top + 118);
                        t.fillRect(x, top + 118 - bh, bw, bh);
                        t.dispose();
                        x += bw + 18;
                    } else {
                        g.fillRect(x, top + 118 - bh, bw, bh);
                        g.setColor(c.brighter());
                        g.fillRect(x + 2, top + 118 - bh + 12, bw - 4, 3);
                        x += bw + 2;
                    }
                }
            }
        }
        // Candles on a reading desk, lighting the books warm.
        glow(g, 520, 560, 520, new Color(255, 170, 80), 0.22f);
        glow(g, W - 520, 600, 440, new Color(255, 170, 80), 0.16f);
        candle(g, 520, 600, 1.3);
        candle(g, 470, 640, 1.0);
        candle(g, W - 520, 640, 1.1);
        // Dust in the moonbeam.
        for (int i = 0; i < 140; i++) {
            int x = wx - 60 + r.nextInt(ww + 260), y = wy + wh + r.nextInt(H - wy - wh);
            g.setColor(new Color(200, 210, 255, 20 + r.nextInt(50)));
            g.fillRect(x, y, 2, 2);
        }
        finish(img, g, r);
        return img;
    }

    static BufferedImage viaduct() {
        Random r = new Random(55);
        BufferedImage img = canvas();
        Graphics2D g = g(img);
        sky(g, new Color(0x0A1024), new Color(0x1E2645), new Color(0x3A2D45), 0.6);
        stars(g, r, 360, 0, (int) (H * 0.5));
        moon(g, 970, 115, 60, new Color(0xEDE6CF), false);
        ridge(g, r, 0.55, 0.06, 5, new Color(0x161B33));
        ridge(g, r, 0.62, 0.05, 7, new Color(0x0E1226));
        // The stone viaduct: a deck with tall arches, curving gently across.
        Color stone = new Color(0x07080F);
        int deck = (int) (H * 0.5);
        g.setColor(stone);
        g.fillRect(0, deck, W, 34);
        for (int i = 0; i < 12; i++) {
            int x = i * 172 - 40;
            g.fillRect(x, deck, 34, H - deck);
            Path2D a = new Path2D.Double();
            a.moveTo(x + 34, deck + 34);
            a.lineTo(x + 172, deck + 34);
            a.lineTo(x + 172, deck + 80);
            a.quadTo(x + 103, deck + 34, x + 34, deck + 80);
            a.closePath();
            g.fill(a);
        }
        // The train: engine, tender and carriages with warm windows; steam rising behind the funnel.
        int tx = 980, ty = deck - 62;
        g.setColor(stone);
        g.fill(new RoundRectangle2D.Double(tx, ty + 10, 210, 52, 14, 14));
        g.fillRect(tx + 150, ty - 20, 60, 50);
        g.fillRect(tx + 30, ty - 22, 22, 34);
        g.fillRect(tx + 215, ty + 14, 90, 48);
        for (int c = 0; c < 4; c++) {
            int cx = tx + 315 + c * 160;
            g.setColor(stone);
            g.fill(new RoundRectangle2D.Double(cx, ty + 4, 150, 58, 10, 10));
            for (int k = 0; k < 5; k++) {
                int x = cx + 12 + k * 27;
                glow(g, x + 8, ty + 24, 26, new Color(255, 180, 90), 0.35f);
                g.setColor(new Color(255, 200, 120));
                g.fillRect(x, ty + 16, 16, 16);
            }
        }
        for (int i = 0; i < 26; i++) {
            double t = i / 25.0;
            int sx = (int) (tx + 40 + t * 520 + r.nextGaussian() * 12);
            int sy = (int) (ty - 40 - Math.sqrt(t) * 170 + r.nextGaussian() * 10);
            int rad = (int) (30 + t * 90);
            glow(g, sx, sy, rad, new Color(190, 195, 215), (float) (0.32 * (1 - t) + 0.06));
        }
        mist(g, r, 16, (int) (H * 0.6), (int) (H * 0.8), new Color(140, 150, 190), 0.05f);
        finish(img, g, r);
        return img;
    }

    static BufferedImage owlMoon() {
        Random r = new Random(66);
        BufferedImage img = canvas();
        Graphics2D g = g(img);
        sky(g, new Color(0x080B1E), new Color(0x1A1F45), new Color(0x2B2A52), 0.65);
        stars(g, r, 520, 0, (int) (H * 0.6));
        moon(g, 965, 118, 88, new Color(0xF1EBD6), false);
        owl(g, 965, 118, 0.52);
        Color dark = new Color(0x090B18);
        ridge(g, r, 0.66, 0.04, 6, new Color(0x121633));
        // Three towers, the middle one tallest, with conical roofs and a few lit windows.
        int[][] towers = {{360, 520, 120}, {560, 430, 150}, {1480, 470, 130}};
        for (int[] t : towers) tower(g, r, t[0], t[1], t[2], dark, true);
        g.setColor(dark);
        g.fillRect(300, (int) (H * 0.68), 1400, H);
        g.fillRect(0, (int) (H * 0.74), W, H);
        finish(img, g, r);
        return img;
    }

    // ---- seasonal sets (wallpapers/<season>, chosen by the launcher in that holiday period) -------------------

    static void seasons(File out) throws IOException {
        String[][] names = {{"halloween", "01-witch-moon", "02-graveyard"}, {"christmas", "01-snowy-village", "02-forest-cabin"},
                {"new_year", "01-city-fireworks", "02-castle-fireworks"}, {"carnival", "01-lantern-street", "02-confetti-roofs"},
                {"ramadan", "01-crescent-domes", "02-lantern-alley"}};
        for (String[] s : names) new File(out, s[0]).mkdirs();
        for (File old : new File(out, "halloween").listFiles()) old.delete();
        save(witchMoon(), new File(out, "halloween/" + names[0][1] + ".jpg"));
        save(graveyard(), new File(out, "halloween/" + names[0][2] + ".jpg"));
        save(snowyVillage(), new File(out, "christmas/" + names[1][1] + ".jpg"));
        save(forestCabin(), new File(out, "christmas/" + names[1][2] + ".jpg"));
        save(cityFireworks(new Color[]{new Color(0xFFD54F), new Color(0xE0E0E0), new Color(0xFFB300)}, 71, false),
                new File(out, "new_year/" + names[2][1] + ".jpg"));
        save(castleFireworks(), new File(out, "new_year/" + names[2][2] + ".jpg"));
        save(lanternStreet(), new File(out, "carnival/" + names[3][1] + ".jpg"));
        save(confettiRoofs(), new File(out, "carnival/" + names[3][2] + ".jpg"));
        save(crescentDomes(), new File(out, "ramadan/" + names[4][1] + ".jpg"));
        save(lanternAlley(), new File(out, "ramadan/" + names[4][2] + ".jpg"));
    }

    // Halloween: Home's cards cover the middle of the picture, so the Halloween things sit where they still show:
    // the moon and the sky at the top centre, the corners, the left and right edges, and the strip between the
    // cards and the dock (about 56% to 67% of the height).

    static BufferedImage witchMoon() {
        Random r = new Random(201);
        BufferedImage img = canvas();
        Graphics2D g = g(img);
        sky(g, new Color(0x14081E), new Color(0x3A1640), new Color(0x1E0C14), 0.58);
        stars(g, r, 220, 0, (int) (H * 0.5));
        glow(g, 965, 128, 360, new Color(255, 120, 30), 0.25f);
        moonColored(g, 965, 128, 104, new Color(0xFF9A3C));
        Color dark = new Color(0x0C0510);
        witch(g, 965, 122, 1.0, dark);
        for (int i = 0; i < 9; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 150 + r.nextDouble() * 260;
            bat(g, (int) (965 + Math.cos(a) * d * 1.6), (int) (110 + Math.sin(a) * d * 0.35), 0.35 + r.nextDouble() * 0.4, dark);
        }
        cobweb(g, 0, 0, 260, false);
        cobweb(g, W, 0, 260, true);
        edgeTrees(g, r, dark);
        int ground = (int) (H * 0.64);
        g.setColor(new Color(0x10070F));
        g.fillRect(0, ground, W, H - ground);
        for (int i = 0; i < 6; i++) gravestone(g, 260 + i * 290 + r.nextInt(80), ground + 6, 0.8 + r.nextDouble() * 0.4, new Color(0x1A1220));
        for (int i = 0; i < 7; i++) pumpkin(g, 180 + i * 260 + r.nextInt(70), ground - 4 + r.nextInt(20), 0.75 + r.nextDouble() * 0.45);
        mist(g, r, 16, ground - 60, ground + 40, new Color(170, 230, 160), 0.05f);
        finish(img, g, r);
        return img;
    }

    static BufferedImage graveyard() {
        Random r = new Random(202);
        BufferedImage img = canvas();
        Graphics2D g = g(img);
        sky(g, new Color(0x0A0C1C), new Color(0x1E2236), new Color(0x142018), 0.6);
        stars(g, r, 200, 0, (int) (H * 0.5));
        glow(g, 965, 120, 300, new Color(200, 255, 190), 0.12f);
        moonColored(g, 965, 120, 88, new Color(0xE8F0D0));
        Color dark = new Color(0x070A0C);
        for (int i = 0; i < 14; i++) bat(g, 700 + r.nextInt(540), 40 + r.nextInt(170), 0.3 + r.nextDouble() * 0.35, dark);
        cobweb(g, 0, 0, 220, false);
        cobweb(g, W, 0, 220, true);
        edgeTrees(g, r, dark);
        // A haunted house at the far right, half behind the forecast card, its windows still showing at the edge.
        house(g, r, W - 230, (int) (H * 0.64), 200, 260, dark, true);
        int ground = (int) (H * 0.64);
        g.setColor(new Color(0x0A100C));
        g.fillRect(0, ground, W, H - ground);
        g.setColor(new Color(0x1A221E));
        for (int x = 120; x < W - 260; x += 46) g.fillRect(x, ground - 46, 6, 50);
        g.fillRect(120, ground - 40, W - 380, 5);
        for (int i = 0; i < 7; i++) gravestone(g, 200 + i * 240 + r.nextInt(60), ground + 6, 0.8 + r.nextDouble() * 0.5, new Color(0x262E2A));
        for (int i = 0; i < 4; i++) ghost(g, 380 + i * 380 + r.nextInt(80), ground - 70 - r.nextInt(30), 0.8 + r.nextDouble() * 0.4);
        for (int i = 0; i < 5; i++) pumpkin(g, 300 + i * 330 + r.nextInt(80), ground + 2, 0.7 + r.nextDouble() * 0.35);
        mist(g, r, 18, ground - 70, ground + 40, new Color(170, 230, 190), 0.06f);
        finish(img, g, r);
        return img;
    }

    /** A witch on her broomstick, flying left across the moon. */
    static void witch(Graphics2D g, int cx, int cy, double s, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke((float) (6 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(cx - 120 * s, cy + 30 * s, cx + 90 * s, cy - 6 * s));
        Path2D bristles = new Path2D.Double();
        bristles.moveTo(cx + 80 * s, cy - 4 * s);
        bristles.lineTo(cx + 140 * s, cy - 30 * s);
        bristles.lineTo(cx + 150 * s, cy - 4 * s);
        bristles.lineTo(cx + 138 * s, cy + 22 * s);
        bristles.closePath();
        g.fill(bristles);
        Path2D body = new Path2D.Double();
        body.moveTo(cx - 40 * s, cy + 20 * s);
        body.curveTo(cx - 34 * s, cy - 30 * s, cx + 10 * s, cy - 40 * s, cx + 40 * s, cy + 6 * s);
        body.curveTo(cx + 60 * s, cy + 30 * s, cx + 30 * s, cy + 40 * s, cx - 40 * s, cy + 20 * s);
        body.closePath();
        g.fill(body);
        Path2D cape = new Path2D.Double();
        cape.moveTo(cx + 10 * s, cy - 24 * s);
        cape.curveTo(cx + 60 * s, cy - 50 * s, cx + 90 * s, cy - 30 * s, cx + 110 * s, cy - 50 * s);
        cape.curveTo(cx + 90 * s, cy - 10 * s, cx + 50 * s, cy - 6 * s, cx + 30 * s, cy - 2 * s);
        cape.closePath();
        g.fill(cape);
        g.fill(new Ellipse2D.Double(cx - 30 * s, cy - 58 * s, 26 * s, 26 * s));
        Path2D hat = new Path2D.Double();
        hat.moveTo(cx - 48 * s, cy - 50 * s);
        hat.lineTo(cx + 6 * s, cy - 50 * s);
        hat.lineTo(cx - 34 * s, cy - 110 * s);
        hat.closePath();
        g.fill(hat);
        g.fill(new Ellipse2D.Double(cx - 56 * s, cy - 54 * s, 70 * s, 10 * s));
        Path2D nose = new Path2D.Double();
        nose.moveTo(cx - 28 * s, cy - 46 * s);
        nose.lineTo(cx - 44 * s, cy - 40 * s);
        nose.lineTo(cx - 28 * s, cy - 38 * s);
        g.fill(nose);
    }

    /** A cobweb in a top corner: threads from the corner, and sagging rings between them. */
    static void cobweb(Graphics2D g, int x, int y, int size, boolean right) {
        g.setColor(new Color(220, 225, 240, 70));
        g.setStroke(new BasicStroke(1.4f));
        int spokes = 6;
        double[] ex = new double[spokes], ey = new double[spokes];
        for (int i = 0; i < spokes; i++) {
            double a = Math.toRadians(4 + i * 82.0 / (spokes - 1));
            ex[i] = x + (right ? -1 : 1) * Math.cos(a) * size;
            ey[i] = y + Math.sin(a) * size;
            g.draw(new java.awt.geom.Line2D.Double(x, y, ex[i], ey[i]));
        }
        for (int ring = 1; ring <= 5; ring++) {
            double f = ring / 5.5;
            Path2D p = new Path2D.Double();
            for (int i = 0; i < spokes; i++) {
                double px = x + (ex[i] - x) * f, py = y + (ey[i] - y) * f;
                if (i == 0) p.moveTo(px, py);
                else {
                    double mx = x + ((ex[i] + ex[i - 1]) / 2 - x) * f * 0.86, my = y + ((ey[i] + ey[i - 1]) / 2 - y) * f * 0.86;
                    p.quadTo(mx, my, px, py);
                }
            }
            g.draw(p);
        }
    }

    /** Crooked bare trees up the left and right edges, the parts of the picture Home never covers. */
    static void edgeTrees(Graphics2D g, Random r, Color c) {
        g.setColor(c);
        branch(g, r, 40, H, -Math.PI / 2 + 0.08, 330, 34, 7);
        branch(g, r, W - 40, H, -Math.PI / 2 - 0.08, 330, 34, 7);
    }

    static void gravestone(Graphics2D g, int x, int base, double s, Color c) {
        Graphics2D t = (Graphics2D) g.create();
        t.rotate(Math.toRadians((x % 7) - 3), x, base);
        Path2D p = new Path2D.Double();
        p.moveTo(x - 28 * s, base);
        p.lineTo(x - 28 * s, base - 50 * s);
        p.quadTo(x - 28 * s, base - 82 * s, x, base - 82 * s);
        p.quadTo(x + 28 * s, base - 82 * s, x + 28 * s, base - 50 * s);
        p.lineTo(x + 28 * s, base);
        p.closePath();
        t.setPaint(new GradientPaint((float) (x - 28 * s), 0, c.brighter(), (float) (x + 28 * s), 0, c.darker()));
        t.fill(p);
        t.setColor(new Color(0, 0, 0, 120));
        t.setFont(new java.awt.Font("Serif", java.awt.Font.BOLD, (int) (18 * s)));
        t.drawString("RIP", (float) (x - 16 * s), (float) (base - 44 * s));
        t.dispose();
    }

    static void ghost(Graphics2D g, int x, int y, double s) {
        glow(g, x, y, 90 * s, new Color(200, 255, 220), 0.12f);
        Path2D p = new Path2D.Double();
        p.moveTo(x - 30 * s, y + 40 * s);
        p.lineTo(x - 30 * s, y - 10 * s);
        p.quadTo(x - 30 * s, y - 50 * s, x, y - 50 * s);
        p.quadTo(x + 30 * s, y - 50 * s, x + 30 * s, y - 10 * s);
        p.lineTo(x + 30 * s, y + 40 * s);
        for (int i = 0; i < 4; i++) p.quadTo(x + (22 - i * 15) * s, y + 26 * s, x + (15 - i * 15) * s, y + 40 * s);
        p.closePath();
        g.setColor(new Color(225, 245, 235, 120));
        g.fill(p);
        g.setColor(new Color(10, 20, 15, 170));
        g.fill(new Ellipse2D.Double(x - 14 * s, y - 24 * s, 9 * s, 13 * s));
        g.fill(new Ellipse2D.Double(x + 5 * s, y - 24 * s, 9 * s, 13 * s));
        g.fill(new Ellipse2D.Double(x - 6 * s, y - 2 * s, 12 * s, 14 * s));
    }

    static BufferedImage snowyVillage() {
        Random r = new Random(103);
        BufferedImage img = canvas();
        Graphics2D g = g(img);
        sky(g, new Color(0x0A1530), new Color(0x1E2E52), new Color(0x2A3550), 0.6);
        stars(g, r, 200, 0, (int) (H * 0.4));
        moon(g, 965, 112, 52, new Color(0xEEF2FA), false);
        Color dark = new Color(0x0B1226);
        ridge(g, r, 0.55, 0.05, 5, new Color(0x16213D));
        snowRidge(g, r, 0.62, new Color(0x4A5670));
        int[][] houses = {{180, 140, 120}, {360, 170, 150}, {600, 130, 120}, {1180, 160, 140}, {1400, 150, 130}, {1640, 170, 150}};
        for (int[] h : houses) house(g, r, h[0], (int) (H * 0.66), h[1], h[2], dark, true);
        tree(g, 930, (int) (H * 0.68), 240);
        g.setColor(new Color(0x3A4660));
        g.fillRect(0, (int) (H * 0.68), W, H);
        snowfall(g, r, 600);
        finish(img, g, r);
        return img;
    }

    static BufferedImage forestCabin() {
        Random r = new Random(104);
        BufferedImage img = canvas();
        Graphics2D g = g(img);
        sky(g, new Color(0x081226), new Color(0x182848), new Color(0x22304A), 0.58);
        stars(g, r, 260, 0, (int) (H * 0.45));
        moon(g, 965, 112, 48, new Color(0xEEF2FA), false);
        Color[] layers = {new Color(0x1A2A44), new Color(0x121E34), new Color(0x0A1424)};
        for (int l = 0; l < layers.length; l++) {
            double scale = 0.6 + l * 0.4;
            int base = (int) (H * (0.58 + l * 0.08));
            for (int x = -40; x < W + 40; x += (int) (40 * scale + r.nextInt((int) (40 * scale) + 1))) {
                pine(g, x, base + 4, (int) ((150 + r.nextInt(120)) * scale), layers[l]);
            }
        }
        house(g, r, 1240, (int) (H * 0.74), 220, 150, new Color(0x0A0F1C), true);
        g.setColor(new Color(0x36425A));
        g.fillRect(0, (int) (H * 0.74), W, H);
        snowfall(g, r, 500);
        finish(img, g, r);
        return img;
    }

    static BufferedImage cityFireworks(Color[] palette, long seed, boolean river) {
        Random r = new Random(seed);
        BufferedImage img = canvas();
        Graphics2D g = g(img);
        sky(g, new Color(0x05070F), new Color(0x10142A), new Color(0x161A2E), 0.6);
        stars(g, r, 120, 0, (int) (H * 0.4));
        openSkyFireworks(g, r, palette, 6);
        Color dark = new Color(0x05060C);
        int base = (int) (H * (river ? 0.6 : 0.66));
        int x = 0;
        while (x < W) {
            int w = 60 + r.nextInt(120), h = 60 + r.nextInt(230);
            g.setColor(dark);
            g.fillRect(x, base - h, w, H);
            for (int k = 0; k < w * h / 900; k++) {
                if (r.nextInt(3) == 0) continue;
                g.setColor(new Color(255, 210, 140, 120 + r.nextInt(100)));
                g.fillRect(x + 6 + r.nextInt(Math.max(1, w - 12)), base - h + 10 + r.nextInt(Math.max(1, h - 20)), 4, 5);
            }
            x += w + r.nextInt(12);
        }
        if (river) lake(img, g, base);
        finish(img, g, r);
        return img;
    }

    static BufferedImage castleFireworks() {
        Random r = new Random(105);
        BufferedImage img = canvas();
        Graphics2D g = g(img);
        sky(g, new Color(0x070A18), new Color(0x161C38), new Color(0x1E1A30), 0.62);
        stars(g, r, 200, 0, (int) (H * 0.45));
        Color[] gold = {new Color(0xFFD54F), new Color(0xE0E0E0), new Color(0xFFB300)};
        openSkyFireworks(g, r, gold, 5);
        Color hill = new Color(0x090B18);
        ridge(g, r, 0.62, 0.04, 5, new Color(0x121633));
        castle(g, r, 720, 600, 1.0, hill, true);
        g.setColor(hill);
        g.fillRect(0, (int) (H * 0.7), W, H);
        finish(img, g, r);
        return img;
    }

    static BufferedImage lanternStreet() {
        Random r = new Random(106);
        BufferedImage img = canvas();
        Graphics2D g = g(img);
        sky(g, new Color(0x120820), new Color(0x2A1238), new Color(0x1A0C20), 0.55);
        stars(g, r, 120, 0, (int) (H * 0.3));
        Color dark = new Color(0x0C0612);
        // Two rows of tall houses leaning towards a street that runs into the distance.
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 5; i++) {
                int w = 150 + r.nextInt(60), h = 380 + r.nextInt(200);
                int x = side == 0 ? i * 150 - 40 : W - (i + 1) * 150 + 40;
                g.setColor(dark);
                g.fillRect(x, H - h, w, h);
                Path2D roof = new Path2D.Double();
                roof.moveTo(x - 6, H - h);
                roof.lineTo(x + w / 2.0, H - h - 70);
                roof.lineTo(x + w + 6, H - h);
                g.fill(roof);
                for (int k = 0; k < 4; k++) window(g, x + 30 + (k % 2) * 70, H - h + 60 + (k / 2) * 110, 1.4);
            }
        }
        Color[] c = {new Color(0xE040FB), new Color(0xFFD54F), new Color(0x4FC3F7), new Color(0x81C784), new Color(0xFF7043)};
        for (int line = 0; line < 4; line++) stringLights(g, r, 480, 180 + line * 70, 1440, 230 + line * 70, 60 - line * 8, c);
        confetti(g, r, 260, c);
        g.setColor(dark);
        g.fillRect(0, (int) (H * 0.8), W, H);
        mask(g, 965, 130, 1.3);
        finish(img, g, r);
        return img;
    }

    static BufferedImage confettiRoofs() {
        Random r = new Random(107);
        BufferedImage img = canvas();
        Graphics2D g = g(img);
        sky(g, new Color(0x0E0820), new Color(0x221440), new Color(0x1C1030), 0.6);
        Color[] c = {new Color(0xE040FB), new Color(0xFFD54F), new Color(0x4FC3F7), new Color(0x81C784), new Color(0xFF7043)};
        confetti(g, r, 500, c);
        bunting(g, r, 0, 90, W, 150, c);
        Color dark = new Color(0x0A0614);
        int x = 0;
        while (x < W) {
            int w = 120 + r.nextInt(120), h = 120 + r.nextInt(160);
            house(g, r, x, (int) (H * 0.72), w, h, dark, true);
            x += w + 10;
        }
        g.setColor(dark);
        g.fillRect(0, (int) (H * 0.72), W, H);
        finish(img, g, r);
        return img;
    }

    static BufferedImage crescentDomes() {
        Random r = new Random(111);
        BufferedImage img = canvas();
        Graphics2D g = g(img);
        sky(g, new Color(0x070C22), new Color(0x162048), new Color(0x1C1A34), 0.62);
        stars(g, r, 420, 0, (int) (H * 0.55));
        crescent(g, 965, 115, 62, new Color(0xF2D27A));
        Color dark = new Color(0x080A18);
        ridge(g, r, 0.64, 0.03, 5, new Color(0x10143A));
        dome(g, 960, (int) (H * 0.62), 170, dark);
        dome(g, 700, (int) (H * 0.66), 100, dark);
        dome(g, 1220, (int) (H * 0.66), 100, dark);
        minaret(g, 520, (int) (H * 0.72), 330, dark);
        minaret(g, 1400, (int) (H * 0.72), 330, dark);
        g.setColor(dark);
        g.fillRect(560, (int) (H * 0.62), 800, H);
        for (int i = 0; i < 7; i++) window(g, 640 + i * 100, (int) (H * 0.66), 1.4);
        g.setColor(dark);
        g.fillRect(0, (int) (H * 0.72), W, H);
        hangingLantern(g, 860, 0, 60, 0.9);
        hangingLantern(g, 1070, 0, 90, 0.9);
        finish(img, g, r);
        return img;
    }

    static BufferedImage lanternAlley() {
        Random r = new Random(112);
        BufferedImage img = canvas();
        Graphics2D g = g(img);
        sky(g, new Color(0x080C20), new Color(0x1A1C3A), new Color(0x15121E), 0.5);
        stars(g, r, 180, 0, (int) (H * 0.3));
        crescent(g, 965, 112, 50, new Color(0xF2D27A));
        Color wall = new Color(0x0E0A12);
        g.setColor(wall);
        Path2D left = new Path2D.Double();
        left.moveTo(0, 0);
        left.lineTo(640, 260);
        left.lineTo(640, H);
        left.lineTo(0, H);
        g.fill(left);
        Path2D right = new Path2D.Double();
        right.moveTo(W, 0);
        right.lineTo(1280, 260);
        right.lineTo(1280, H);
        right.lineTo(W, H);
        g.fill(right);
        // A string of lanterns across the alley low down, in the strip Home leaves open above the dock, and two
        // more at the top centre between the clock and the pills.
        g.setColor(new Color(0x6A5420));
        g.setStroke(new BasicStroke(2f));
        g.draw(new java.awt.geom.QuadCurve2D.Double(0, H * 0.55, W / 2.0, H * 0.6, W, H * 0.55));
        for (int i = 0; i < 9; i++) {
            double x = 110 + i * 212.5, f = x / W, y = H * 0.55 + 4 * f * (1 - f) * H * 0.05;
            hangingLantern(g, (int) x, (int) y, 18, 1.0);
        }
        hangingLantern(g, 880, 0, 50, 0.8);
        hangingLantern(g, 1050, 0, 80, 0.8);
        glow(g, 960, H * 0.7, 420, new Color(255, 180, 90), 0.12f);
        g.setColor(new Color(0x0A080E));
        g.fillRect(0, (int) (H * 0.8), W, H);
        finish(img, g, r);
        return img;
    }

    // ---- seasonal pieces ------------------------------------------------------------------------------------

    static void moonColored(Graphics2D g, int x, int y, int rad, Color c) {
        glow(g, x, y, rad * 3.5, c, 0.22f);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x - rad * 0.3, y - rad * 0.3), rad * 1.3f,
                new float[]{0f, 1f}, new Color[]{c.brighter(), c.darker()}));
        g.fill(new Ellipse2D.Double(x - rad, y - rad, rad * 2, rad * 2));
    }

    static void crescent(Graphics2D g, int x, int y, int rad, Color c) {
        glow(g, x, y, rad * 3, c, 0.18f);
        java.awt.geom.Area a = new java.awt.geom.Area(new Ellipse2D.Double(x - rad, y - rad, rad * 2, rad * 2));
        a.subtract(new java.awt.geom.Area(new Ellipse2D.Double(x - rad * 0.55, y - rad * 1.15, rad * 2, rad * 2)));
        g.setColor(c);
        g.fill(a);
        star5(g, x + rad * 1.2, y - rad * 0.2, rad * 0.28, c);
    }

    static void star5(Graphics2D g, double cx, double cy, double rad, Color c) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 == 0 ? rad : rad * 0.42;
            if (i == 0) p.moveTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
            else p.lineTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
        }
        p.closePath();
        g.setColor(c);
        g.fill(p);
    }

    static void deadTree(Graphics2D g, Random r, int x, int y, int h, Color c) {
        g.setColor(c);
        branch(g, r, x, y + h, -Math.PI / 2, h * 0.45, 22, 6);
    }

    static void branch(Graphics2D g, Random r, double x, double y, double a, double len, double w, int depth) {
        if (depth == 0 || len < 6) return;
        double x2 = x + Math.cos(a) * len, y2 = y + Math.sin(a) * len;
        g.setStroke(new BasicStroke((float) w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(x, y, x2, y2));
        int n = 2 + (r.nextInt(3) == 0 ? 1 : 0);
        for (int i = 0; i < n; i++) {
            branch(g, r, x2, y2, a + (r.nextDouble() - 0.5) * 1.3, len * (0.6 + r.nextDouble() * 0.15), w * 0.62, depth - 1);
        }
    }

    static void bat(Graphics2D g, int cx, int cy, double s, Color c) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy + 6 * s);
        p.curveTo(cx - 20 * s, cy - 18 * s, cx - 52 * s, cy - 20 * s, cx - 80 * s, cy - 4 * s);
        p.curveTo(cx - 66 * s, cy, cx - 60 * s, cy + 10 * s, cx - 58 * s, cy + 18 * s);
        p.curveTo(cx - 44 * s, cy + 8 * s, cx - 30 * s, cy + 14 * s, cx - 22 * s, cy + 22 * s);
        p.curveTo(cx - 12 * s, cy + 14 * s, cx - 6 * s, cy + 18 * s, cx, cy + 26 * s);
        p.curveTo(cx + 6 * s, cy + 18 * s, cx + 12 * s, cy + 14 * s, cx + 22 * s, cy + 22 * s);
        p.curveTo(cx + 30 * s, cy + 14 * s, cx + 44 * s, cy + 8 * s, cx + 58 * s, cy + 18 * s);
        p.curveTo(cx + 60 * s, cy + 10 * s, cx + 66 * s, cy, cx + 80 * s, cy - 4 * s);
        p.curveTo(cx + 52 * s, cy - 20 * s, cx + 20 * s, cy - 18 * s, cx, cy + 6 * s);
        p.closePath();
        g.setColor(c);
        g.fill(p);
        g.fill(new Ellipse2D.Double(cx - 7 * s, cy - 6 * s, 14 * s, 26 * s));
    }

    static void pumpkin(Graphics2D g, int cx, int cy, double s) {
        glow(g, cx, cy, 90 * s, new Color(255, 140, 40), 0.1f);
        Color[] ribs = {new Color(0xB84E0C), new Color(0xD9650F), new Color(0xEE7A1E)};
        double[][] lobes = {{-34, 26}, {34, 26}, {-18, 30}, {18, 30}, {0, 32}};
        for (int i = 0; i < lobes.length; i++) {
            g.setColor(ribs[Math.min(2, i / 2)]);
            g.fill(new Ellipse2D.Double(cx + lobes[i][0] * s - lobes[i][1] * s, cy - 34 * s, lobes[i][1] * 2 * s, 66 * s));
        }
        g.setColor(new Color(255, 190, 110, 120));
        g.fill(new Ellipse2D.Double(cx - 22 * s, cy - 26 * s, 10 * s, 28 * s));
        g.setColor(new Color(0x4A6B2A));
        g.fill(new RoundRectangle2D.Double(cx - 4 * s, cy - 48 * s, 8 * s, 16 * s, 3 * s, 3 * s));
        g.setColor(new Color(0xC8862E));
        Path2D face = new Path2D.Double();
        face.moveTo(cx - 22 * s, cy - 6 * s);
        face.lineTo(cx - 10 * s, cy - 20 * s);
        face.lineTo(cx - 4 * s, cy - 4 * s);
        face.closePath();
        face.moveTo(cx + 22 * s, cy - 6 * s);
        face.lineTo(cx + 10 * s, cy - 20 * s);
        face.lineTo(cx + 4 * s, cy - 4 * s);
        face.closePath();
        face.moveTo(cx - 24 * s, cy + 10 * s);
        face.quadTo(cx, cy + 32 * s, cx + 24 * s, cy + 10 * s);
        face.lineTo(cx + 14 * s, cy + 14 * s);
        face.lineTo(cx + 8 * s, cy + 8 * s);
        face.lineTo(cx + 2 * s, cy + 16 * s);
        face.lineTo(cx - 4 * s, cy + 8 * s);
        face.lineTo(cx - 10 * s, cy + 16 * s);
        face.lineTo(cx - 16 * s, cy + 10 * s);
        face.closePath();
        g.fill(face);
    }

    static void house(Graphics2D g, Random r, int x, int base, int w, int h, Color c, boolean lit) {
        g.setColor(c);
        g.fillRect(x, base - h, w, h);
        Path2D roof = new Path2D.Double();
        roof.moveTo(x - 8, base - h);
        roof.lineTo(x + w / 2.0, base - h - w * 0.45);
        roof.lineTo(x + w + 8, base - h);
        roof.closePath();
        g.fill(roof);
        if (!lit) return;
        int n = 1 + r.nextInt(3);
        for (int i = 0; i < n; i++) window(g, x + 14 + r.nextInt(Math.max(1, w - 34)), base - h + 16 + r.nextInt(Math.max(1, h - 40)), 1.3);
    }

    static void snowRidge(Graphics2D g, Random r, double level, Color c) {
        ridge(g, r, level, 0.03, 7, c);
    }

    static void snowfall(Graphics2D g, Random r, int n) {
        for (int i = 0; i < n; i++) {
            double s = 1.2 + r.nextDouble() * 3.2;
            g.setColor(new Color(255, 255, 255, 90 + r.nextInt(140)));
            g.fill(new Ellipse2D.Double(r.nextInt(W), r.nextInt(H), s, s));
        }
    }

    static void tree(Graphics2D g, int x, int base, int h) {
        g.setColor(new Color(0x14361F));
        for (int i = 0; i < 4; i++) {
            int w = (int) (h * (0.38 - i * 0.07)), top = (int) (base - h + i * h * 0.2);
            Path2D p = new Path2D.Double();
            p.moveTo(x, top);
            p.lineTo(x + w, top + h * 0.34);
            p.lineTo(x - w, top + h * 0.34);
            p.closePath();
            g.fill(p);
        }
        Color[] bulbs = {new Color(0xEF5350), new Color(0xFFD54F), new Color(0x4FC3F7), new Color(0x81C784)};
        Random r = new Random(9);
        for (int i = 0; i < 26; i++) {
            double t = r.nextDouble();
            int y = (int) (base - h * 0.9 + t * h * 0.9), half = (int) (t * h * 0.36);
            int bx = x - half + r.nextInt(Math.max(1, half * 2));
            glow(g, bx, y, 14, bulbs[i % 4], 0.6f);
            g.setColor(bulbs[i % 4]);
            g.fill(new Ellipse2D.Double(bx - 3, y - 3, 6, 6));
        }
        star5(g, x, base - h - 6, 16, new Color(0xFFD54F));
    }

    /** Fireworks where Home leaves the sky open: two at the top centre, between the clock and the pills, the rest
     *  low over the skyline in the strip between the cards and the dock. */
    static void openSkyFireworks(Graphics2D g, Random r, Color[] palette, int low) {
        for (int i = 0; i < 2; i++) firework(g, r, 905 + i * 120, 70 + r.nextInt(40), 55 + r.nextInt(20), palette[i % palette.length]);
        for (int i = 0; i < low; i++) {
            firework(g, r, 160 + i * (1600 / Math.max(1, low - 1)) + r.nextInt(60) - 30, (int) (H * 0.58) + r.nextInt(30),
                    45 + r.nextInt(25), palette[(i + 2) % palette.length]);
        }
    }

    static void firework(Graphics2D g, Random r, int x, int y, int rad, Color c) {
        glow(g, x, y, rad * 1.3, c, 0.12f);
        int n = 40 + r.nextInt(20);
        for (int i = 0; i < n; i++) {
            double a = i * 2 * Math.PI / n + r.nextDouble() * 0.05, d = rad * (0.7 + r.nextDouble() * 0.3);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 140));
            g.setStroke(new BasicStroke(1.6f));
            g.draw(new java.awt.geom.Line2D.Double(x + Math.cos(a) * d * 0.35, y + Math.sin(a) * d * 0.35,
                    x + Math.cos(a) * d, y + Math.sin(a) * d + d * 0.08));
            g.setColor(c.brighter());
            g.fill(new Ellipse2D.Double(x + Math.cos(a) * d - 2.5, y + Math.sin(a) * d + d * 0.08 - 2.5, 5, 5));
        }
    }

    static void stringLights(Graphics2D g, Random r, int x0, int y0, int x1, int y1, int sag, Color[] c) {
        g.setColor(new Color(0x2A2234));
        g.setStroke(new BasicStroke(2f));
        Path2D wire = new Path2D.Double();
        wire.moveTo(x0, y0);
        wire.quadTo((x0 + x1) / 2.0, Math.max(y0, y1) + sag, x1, y1);
        g.draw(wire);
        for (int i = 1; i < 24; i++) {
            double t = i / 24.0, mx = (x0 + x1) / 2.0, my = Math.max(y0, y1) + sag;
            double x = (1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * mx + t * t * x1;
            double y = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * my + t * t * y1;
            Color col = c[i % c.length];
            glow(g, x, y + 8, 22, col, 0.5f);
            g.setColor(col);
            g.fill(new Ellipse2D.Double(x - 4, y + 3, 8, 11));
        }
    }

    static void confetti(Graphics2D g, Random r, int n, Color[] c) {
        for (int i = 0; i < n; i++) {
            Graphics2D t = (Graphics2D) g.create();
            t.translate(r.nextInt(W), r.nextInt((int) (H * 0.7)));
            t.rotate(r.nextDouble() * Math.PI);
            Color col = c[r.nextInt(c.length)];
            t.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 150 + r.nextInt(100)));
            t.fillRect(-5, -3, 10, 6);
            t.dispose();
        }
    }

    static void bunting(Graphics2D g, Random r, int x0, int y0, int x1, int sag, Color[] c) {
        g.setColor(new Color(220, 220, 230, 160));
        g.setStroke(new BasicStroke(1.5f));
        Path2D wire = new Path2D.Double();
        wire.moveTo(x0, y0);
        wire.quadTo((x0 + x1) / 2.0, y0 + sag, x1, y0);
        g.draw(wire);
        for (int i = 1; i < 30; i++) {
            double t = i / 30.0, mx = (x0 + x1) / 2.0, my = y0 + sag;
            double x = (1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * mx + t * t * x1;
            double y = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * my + t * t * y0;
            Path2D f = new Path2D.Double();
            f.moveTo(x - 18, y);
            f.lineTo(x + 18, y);
            f.lineTo(x, y + 40);
            f.closePath();
            g.setColor(c[i % c.length]);
            g.fill(f);
        }
    }

    static void mask(Graphics2D g, int cx, int cy, double s) {
        Path2D m = new Path2D.Double();
        m.moveTo(cx - 70 * s, cy);
        m.quadTo(cx - 35 * s, cy - 40 * s, cx, cy - 6 * s);
        m.quadTo(cx + 35 * s, cy - 40 * s, cx + 70 * s, cy);
        m.quadTo(cx + 60 * s, cy + 36 * s, cx + 24 * s, cy + 28 * s);
        m.quadTo(cx, cy + 14 * s, cx - 24 * s, cy + 28 * s);
        m.quadTo(cx - 60 * s, cy + 36 * s, cx - 70 * s, cy);
        m.closePath();
        glow(g, cx, cy, 160 * s, new Color(224, 64, 251), 0.2f);
        g.setPaint(new GradientPaint(cx, (float) (cy - 30 * s), new Color(0xF2C94C), cx, (float) (cy + 30 * s), new Color(0xA67C00)));
        g.fill(m);
        g.setColor(new Color(0x120820));
        g.fill(new Ellipse2D.Double(cx - 44 * s, cy - 6 * s, 28 * s, 16 * s));
        g.fill(new Ellipse2D.Double(cx + 16 * s, cy - 6 * s, 28 * s, 16 * s));
    }

    static void dome(Graphics2D g, int cx, int base, int rad, Color c) {
        g.setColor(c);
        g.fill(new java.awt.geom.Arc2D.Double(cx - rad, base - rad, rad * 2, rad * 2, 0, 180, java.awt.geom.Arc2D.PIE));
        g.fillRect(cx - 3, base - rad - 40, 6, 40);
        crescentSmall(g, cx, base - rad - 50, 9, c);
    }

    static void crescentSmall(Graphics2D g, int x, int y, int rad, Color c) {
        java.awt.geom.Area a = new java.awt.geom.Area(new Ellipse2D.Double(x - rad, y - rad, rad * 2, rad * 2));
        a.subtract(new java.awt.geom.Area(new Ellipse2D.Double(x - rad * 0.5, y - rad * 1.1, rad * 2, rad * 2)));
        g.setColor(c);
        g.fill(a);
    }

    static void minaret(Graphics2D g, int cx, int base, int h, Color c) {
        g.setColor(c);
        g.fillRect(cx - 16, base - h, 32, h);
        g.fillRect(cx - 26, base - h * 2 / 3, 52, 12);
        g.fillRect(cx - 26, base - h + 40, 52, 10);
        Path2D cap = new Path2D.Double();
        cap.moveTo(cx - 18, base - h);
        cap.lineTo(cx, base - h - 60);
        cap.lineTo(cx + 18, base - h);
        cap.closePath();
        g.fill(cap);
        window(g, cx - 5, base - h + 70, 1.0);
    }

    static void hangingLantern(Graphics2D g, int x, int top, int chain, double s) {
        g.setColor(new Color(0xB08D3A));
        g.setStroke(new BasicStroke((float) (1.5 * s)));
        g.drawLine(x, top, x, (int) (top + chain * s));
        int y = (int) (top + chain * s);
        glow(g, x, y + 30 * s, 90 * s, new Color(255, 180, 80), 0.35f);
        Path2D body = new Path2D.Double();
        body.moveTo(x - 10 * s, y);
        body.lineTo(x + 10 * s, y);
        body.lineTo(x + 18 * s, y + 14 * s);
        body.lineTo(x + 18 * s, y + 46 * s);
        body.lineTo(x + 10 * s, y + 58 * s);
        body.lineTo(x - 10 * s, y + 58 * s);
        body.lineTo(x - 18 * s, y + 46 * s);
        body.lineTo(x - 18 * s, y + 14 * s);
        body.closePath();
        g.setColor(new Color(0x7A5C1C));
        g.fill(body);
        g.setPaint(new GradientPaint((float) (x - 12 * s), 0, new Color(0xFFE8A8), (float) (x + 12 * s), 0, new Color(0xFFB74D)));
        g.fill(new Rectangle2D.Double(x - 12 * s, y + 16 * s, 24 * s, 28 * s));
        g.setColor(new Color(0x7A5C1C));
        g.fill(new Rectangle2D.Double(x - 1.5 * s, y + 16 * s, 3 * s, 28 * s));
    }

    // ---- pieces ---------------------------------------------------------------------------------------------

    static BufferedImage canvas() {
        return new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    }

    static Graphics2D g(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /** Top colour, horizon colour (at {@code horizon} of the height), and a darker colour below it. */
    static void sky(Graphics2D g, Color top, Color mid, Color low, double horizon) {
        g.setPaint(new java.awt.LinearGradientPaint(0, 0, 0, H, new float[]{0f, (float) horizon, 1f},
                new Color[]{top, mid, low}));
        g.fillRect(0, 0, W, H);
    }

    static void stars(Graphics2D g, Random r, int n, int y0, int y1) {
        for (int i = 0; i < n; i++) {
            int x = r.nextInt(W), y = y0 + r.nextInt(Math.max(1, y1 - y0));
            double fade = 1 - (y - y0) / (double) Math.max(1, y1 - y0) * 0.7;
            int a = (int) ((60 + r.nextInt(170)) * fade);
            double s = r.nextDouble() < 0.06 ? 2.6 : 1.0 + r.nextDouble() * 1.1;
            if (s > 2) glow(g, x, y, 9, new Color(200, 210, 255), 0.35f);
            g.setColor(new Color(235, 238, 255, Math.min(255, a)));
            g.fill(new Ellipse2D.Double(x - s / 2, y - s / 2, s, s));
        }
    }

    static void moon(Graphics2D g, int x, int y, int rad, Color c, boolean crescent) {
        glow(g, x, y, rad * 5, new Color(c.getRed(), c.getGreen(), c.getBlue()), 0.18f);
        glow(g, x, y, rad * 2, new Color(c.getRed(), c.getGreen(), c.getBlue()), 0.25f);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x - rad * 0.3, y - rad * 0.3), rad * 1.3f,
                new float[]{0f, 1f}, new Color[]{c, c.darker()}));
        g.fill(new Ellipse2D.Double(x - rad, y - rad, rad * 2, rad * 2));
        Random r = new Random(rad);
        for (int i = 0; i < 7; i++) {   // faint maria
            double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * rad * 0.6, s = rad * (0.15 + r.nextDouble() * 0.25);
            g.setColor(new Color(0, 0, 0, 22));
            g.fill(new Ellipse2D.Double(x + Math.cos(a) * d - s, y + Math.sin(a) * d - s, s * 2, s * 1.6));
        }
        if (crescent) {   // the night side, in the sky's colour
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f));
            g.setColor(new Color(0x0D1330));
            g.fill(new Ellipse2D.Double(x - rad * 0.45, y - rad * 1.08, rad * 2.05, rad * 2.05));
            g.setComposite(AlphaComposite.SrcOver);
        }
    }

    static void glow(Graphics2D g, double x, double y, double rad, Color c, float alpha) {
        if (rad < 1) return;
        Color in = new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(255 * alpha));
        Color out = new Color(c.getRed(), c.getGreen(), c.getBlue(), 0);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), (float) rad, new float[]{0f, 1f},
                new Color[]{in, out}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(new Ellipse2D.Double(x - rad, y - rad, rad * 2, rad * 2));
    }

    /** A hill line across the screen at {@code level} of the height, wobbling by {@code amp}. */
    static void ridge(Graphics2D g, Random r, double level, double amp, int bumps, Color c) {
        Path2D p = new Path2D.Double();
        p.moveTo(0, H);
        double[] k = new double[bumps + 2];
        for (int i = 0; i < k.length; i++) k[i] = (r.nextDouble() - 0.5) * 2;
        for (int x = 0; x <= W; x += 8) {
            double t = x / (double) W * (bumps + 1);
            int i = Math.min((int) t, bumps);
            double f = t - i, s = (1 - Math.cos(f * Math.PI)) / 2;
            double y = H * (level + amp * (k[i] * (1 - s) + k[i + 1] * s));
            p.lineTo(x, y);
        }
        p.lineTo(W, H);
        p.closePath();
        g.setColor(c);
        g.fill(p);
    }

    static void mist(Graphics2D g, Random r, int n, int y0, int y1, Color c, float alpha) {
        for (int i = 0; i < n; i++) {
            int x = r.nextInt(W), y = y0 + r.nextInt(Math.max(1, y1 - y0));
            int rx = 300 + r.nextInt(500), ry = 40 + r.nextInt(70);
            Graphics2D t = (Graphics2D) g.create();
            t.translate(x, y);
            t.scale(1, ry / (double) rx);
            glow(t, 0, 0, rx, c, alpha);
            t.dispose();
        }
    }

    static void castle(Graphics2D g, Random r, int x, int base, double s, Color c, boolean lit) {
        int[][] towers = {{0, 210, 60}, {90, 300, 74}, {200, 240, 56}, {300, 360, 86}, {410, 230, 60}};
        g.setColor(c);
        g.fillRect(x - 10, (int) (base - 140 * s), (int) (500 * s), (int) (160 * s));
        for (int[] t : towers) tower(g, r, (int) (x + t[0] * s), (int) (base - t[1] * s), (int) (t[2] * s), c, lit);
        if (!lit) return;
        for (int i = 0; i < 26; i++) {   // windows along the walls
            int wx = (int) (x + r.nextInt((int) (480 * s))), wy = (int) (base - 30 - r.nextInt((int) (100 * s)));
            window(g, wx, wy, 1.0);
        }
    }

    static void tower(Graphics2D g, Random r, int x, int top, int w, Color c, boolean lit) {
        g.setColor(c);
        g.fillRect(x, top, w, H - top);
        Path2D roof = new Path2D.Double();
        roof.moveTo(x - w * 0.12, top + 2);
        roof.lineTo(x + w / 2.0, top - w * 1.5);
        roof.lineTo(x + w * 1.12, top + 2);
        roof.closePath();
        g.fill(roof);
        g.setStroke(new BasicStroke(2.5f));
        g.drawLine(x + w / 2, (int) (top - w * 1.5), x + w / 2, (int) (top - w * 1.5 - 26));
        if (!lit) return;
        int n = 1 + r.nextInt(3);
        for (int i = 0; i < n; i++) window(g, x + w / 2 - 5, top + 40 + i * 70 + r.nextInt(20), 1.2);
    }

    static void window(Graphics2D g, int x, int y, double s) {
        glow(g, x + 5 * s, y + 8 * s, 34 * s, new Color(255, 170, 70), 0.28f);
        Path2D p = new Path2D.Double();
        p.moveTo(x, y + 18 * s);
        p.lineTo(x, y + 6 * s);
        p.quadTo(x + 5 * s, y - 2 * s, x + 10 * s, y + 6 * s);
        p.lineTo(x + 10 * s, y + 18 * s);
        p.closePath();
        g.setColor(new Color(255, 200, 110));
        g.fill(p);
    }

    /** The lake: a reflection of everything above the water line, darkened and rippled. */
    static void lake(BufferedImage img, Graphics2D g, int water) {
        BufferedImage copy = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        copy.getGraphics().drawImage(img, 0, 0, null);
        for (int y = water; y < H; y++) {
            int src = water - (y - water) - 1;
            if (src < 0) break;
            int shift = (int) (Math.sin(y * 0.35) * 3 + Math.sin(y * 0.11) * 4);
            for (int x = 0; x < W; x++) {
                int sx = Math.min(W - 1, Math.max(0, x + shift));
                int p = copy.getRGB(sx, src);
                int rr = (p >> 16) & 255, gg = (p >> 8) & 255, bb = p & 255;
                double k = 0.42 * (1 - (y - water) / (double) (H - water)) + 0.06;
                img.setRGB(x, y, new Color((int) (rr * k), (int) (gg * k), (int) Math.min(255, bb * k + 6)).getRGB());
            }
        }
        g.setColor(new Color(160, 170, 210, 40));
        g.setStroke(new BasicStroke(1.5f));
        Random r = new Random(7);
        for (int i = 0; i < 60; i++) {
            int y = water + 6 + r.nextInt(H - water - 6), x = r.nextInt(W), len = 30 + r.nextInt(160);
            g.drawLine(x, y, x + len, y);
        }
    }

    static void candle(Graphics2D g, int x, int y, double s) {
        glow(g, x, y - 14 * s, 70 * s, new Color(255, 170, 80), 0.32f);
        g.setPaint(new GradientPaint(x, y, new Color(236, 226, 200), x, (float) (y + 48 * s), new Color(150, 135, 110)));
        g.fill(new RoundRectangle2D.Double(x - 5 * s, y, 10 * s, 46 * s, 3 * s, 3 * s));
        GeneralPath flame = new GeneralPath();
        flame.moveTo(x, y - 24 * s);
        flame.quadTo(x + 7 * s, y - 8 * s, x, y - 1 * s);
        flame.quadTo(x - 7 * s, y - 8 * s, x, y - 24 * s);
        g.setPaint(new GradientPaint(x, (float) (y - 24 * s), new Color(255, 245, 210), x, y, new Color(255, 150, 50)));
        g.fill(flame);
    }

    static void pine(Graphics2D g, int x, int base, int h, Color c) {
        g.setColor(c);
        g.fillRect(x - Math.max(2, h / 40), base - h / 8, Math.max(4, h / 20), h / 8 + 6);
        for (int i = 0; i < 4; i++) {
            double t = i / 4.0;
            int w = (int) (h * (0.34 - t * 0.07));
            int top = (int) (base - h + t * h * 0.22);
            int bottom = (int) (base - h * 0.08 - (1 - t) * 0 - (3 - i) * h * 0.18 * 0.0) ;
            Path2D p = new Path2D.Double();
            p.moveTo(x, top);
            p.lineTo(x + w, bottom - i * h * 0.17);
            p.lineTo(x - w, bottom - i * h * 0.17);
            p.closePath();
            g.fill(p);
        }
    }

    /** An owl in flight seen against the moon: body, head with ear tufts, and spread wings. */
    static void owl(Graphics2D g, int cx, int cy, double s) {
        Color c = new Color(0x0A0B16);
        g.setColor(c);
        Path2D wings = new Path2D.Double();
        wings.moveTo(cx, cy + 6 * s);
        wings.curveTo(cx - 40 * s, cy - 30 * s, cx - 110 * s, cy - 40 * s, cx - 170 * s, cy - 18 * s);
        wings.curveTo(cx - 140 * s, cy - 6 * s, cx - 128 * s, cy + 4 * s, cx - 118 * s, cy + 14 * s);
        wings.curveTo(cx - 90 * s, cy + 6 * s, cx - 70 * s, cy + 14 * s, cx - 54 * s, cy + 24 * s);
        wings.curveTo(cx - 34 * s, cy + 22 * s, cx - 18 * s, cy + 28 * s, cx, cy + 36 * s);
        wings.curveTo(cx + 18 * s, cy + 28 * s, cx + 34 * s, cy + 22 * s, cx + 54 * s, cy + 24 * s);
        wings.curveTo(cx + 70 * s, cy + 14 * s, cx + 90 * s, cy + 6 * s, cx + 118 * s, cy + 14 * s);
        wings.curveTo(cx + 128 * s, cy + 4 * s, cx + 140 * s, cy - 6 * s, cx + 170 * s, cy - 18 * s);
        wings.curveTo(cx + 110 * s, cy - 40 * s, cx + 40 * s, cy - 30 * s, cx, cy + 6 * s);
        wings.closePath();
        g.fill(wings);
        g.fill(new Ellipse2D.Double(cx - 20 * s, cy - 10 * s, 40 * s, 62 * s));
        g.fill(new Ellipse2D.Double(cx - 19 * s, cy - 34 * s, 38 * s, 34 * s));
        Path2D ears = new Path2D.Double();
        ears.moveTo(cx - 17 * s, cy - 24 * s);
        ears.lineTo(cx - 14 * s, cy - 44 * s);
        ears.lineTo(cx - 5 * s, cy - 30 * s);
        ears.moveTo(cx + 17 * s, cy - 24 * s);
        ears.lineTo(cx + 14 * s, cy - 44 * s);
        ears.lineTo(cx + 5 * s, cy - 30 * s);
        g.fill(ears);
        Path2D tail = new Path2D.Double();
        tail.moveTo(cx - 12 * s, cy + 46 * s);
        tail.lineTo(cx, cy + 66 * s);
        tail.lineTo(cx + 12 * s, cy + 46 * s);
        tail.closePath();
        g.fill(tail);
    }

    /** Vignette, a dark lower third for the dock and grid, and fine grain so dark gradients do not band. */
    static void finish(BufferedImage img, Graphics2D g, Random r) {
        g.setPaint(new java.awt.LinearGradientPaint(0, H * 0.5f, 0, H, new float[]{0f, 0.45f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(3, 3, 8, 150), new Color(2, 2, 6, 235)}));
        g.fill(new Rectangle2D.Double(0, H * 0.5, W, H * 0.5));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(W / 2.0, H * 0.42), W * 0.75f, new float[]{0.55f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 170)}));
        g.fillRect(0, 0, W, H);
        g.dispose();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int p = img.getRGB(x, y), n = r.nextInt(5) - 2;
                int rr = clamp(((p >> 16) & 255) + n), gg = clamp(((p >> 8) & 255) + n), bb = clamp((p & 255) + n);
                img.setRGB(x, y, (rr << 16) | (gg << 8) | bb);
            }
        }
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static void save(BufferedImage img, File f) throws IOException {
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter w = it.next();
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(0.9f);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(f)) {
            w.setOutput(out);
            w.write(null, new IIOImage(img, null, null), p);
        } finally {
            w.dispose();
        }
        System.out.println(f + "  " + f.length() / 1024 + " KB");
    }
}
