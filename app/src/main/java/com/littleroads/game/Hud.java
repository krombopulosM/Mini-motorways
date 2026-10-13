package com.littleroads.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.ArrayList;

/** All screen-space UI: in-game HUD, overlays, main menu, settings and help. */
final class Hud {
    // Button ids
    static final int B_UNDO = 1, B_ROAD = 2, B_ERASE = 3, B_ROUNDABOUT = 4, B_LIGHT = 5, B_MOTORWAY = 6,
            B_BRIDGE = 7, B_TUNNEL = 8, B_PAUSE = 9, B_PLAY = 10, B_FAST = 11, B_MENU = 12,
            B_UP0 = 20, B_UP1 = 21, B_RESUME = 22, B_RESTART = 23, B_QUIT = 24, B_SETTINGS = 25,
            B_GO_RETRY = 26, B_GO_MENU = 27,
            B_MAP0 = 40, B_MODE0 = 50, B_OPEN_SETTINGS = 60, B_OPEN_HELP = 61,
            B_SET_SOUND = 70, B_SET_HAPTIC = 71, B_SET_GRID = 72, B_SET_COLORBLIND = 73, B_SET_NIGHT = 74,
            B_SET_RESET = 75, B_BACK = 76;

    // Icons
    static final int I_ROAD = 0, I_ERASE = 1, I_ROUNDABOUT = 2, I_LIGHT = 3, I_MOTORWAY = 4, I_BRIDGE = 5,
            I_TUNNEL = 6, I_UNDO = 7, I_PAUSE = 8, I_PLAY = 9, I_FAST = 10, I_MENU = 11, I_GEAR = 12,
            I_BACK = 13, I_CAR = 14, I_HELP = 15;

    static final String[] UP_NAMES = {"Roads", "Bridge", "Tunnel", "Motorway", "Roundabouts", "Traffic lights"};
    static final int[] UP_ICONS = {I_ROAD, I_BRIDGE, I_TUNNEL, I_MOTORWAY, I_ROUNDABOUT, I_LIGHT};
    static final String[] DAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    static final String[] MODES = {"Normal", "Expert", "Endless"};

    static final class Btn {
        int id;
        final RectF r = new RectF();
    }

    private final ArrayList<Btn> btns = new ArrayList<>();
    private int nb;
    final float dp;
    Palette pal;
    float insetL, insetT, insetR, insetB;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path path = new Path();
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Bitmap[] thumbs = new Bitmap[MapDef.ALL.length];

    Hud(float dp) {
        this.dp = dp;
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
    }

    // ------------------------------------------------------------------ buttons

    void beginFrame() { nb = 0; }

    private Btn add(int id, float l, float t, float r, float b) {
        Btn x;
        if (nb < btns.size()) x = btns.get(nb);
        else { x = new Btn(); btns.add(x); }
        nb++;
        x.id = id;
        x.r.set(l, t, r, b);
        return x;
    }

    int hit(float x, float y) {
        float slop = 6 * dp;
        for (int i = nb - 1; i >= 0; i--) {
            RectF r = btns.get(i).r;
            if (x >= r.left - slop && x <= r.right + slop && y >= r.top - slop && y <= r.bottom + slop) return btns.get(i).id;
        }
        return -1;
    }

    // ------------------------------------------------------------------ helpers

    private void txt(Canvas c, String s, float x, float y, float sizeSp, int color, Typeface tf, Paint.Align al) {
        text.setTextSize(sizeSp * dp);
        text.setColor(color);
        text.setTypeface(tf);
        text.setTextAlign(al);
        c.drawText(s, x, y, text);
    }

    private void panel(Canvas c, float l, float t, float r, float b, float rad, int color) {
        fill.setColor(pal.shadow);
        rf.set(l, t + 2 * dp, r, b + 3 * dp);
        c.drawRoundRect(rf, rad, rad, fill);
        fill.setColor(color);
        rf.set(l, t, r, b);
        c.drawRoundRect(rf, rad, rad, fill);
    }

    private void pillButton(Canvas c, int id, float cx, float cy, float w, float h, String label, boolean primary) {
        add(id, cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        int bg = primary ? pal.text : Palette.alpha(pal.text, 0x14);
        fill.setColor(bg);
        rf.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        c.drawRoundRect(rf, h / 2, h / 2, fill);
        int fg = primary ? (pal.night ? 0xFF1E2228 : 0xFFFFFFFF) : pal.text;
        txt(c, label, cx, cy + 5.5f * dp, 15, fg, medium, Paint.Align.CENTER);
    }

    // ------------------------------------------------------------------ icons

    void icon(Canvas c, int which, float cx, float cy, float s, int col) {
        stroke.setColor(col);
        fill.setColor(col);
        float u = s / 2;
        switch (which) {
            case I_ROAD: {
                stroke.setStrokeWidth(s * 0.24f);
                path.reset();
                path.moveTo(cx - u * 0.8f, cy + u * 0.8f);
                path.quadTo(cx + u * 0.9f, cy + u * 0.8f, cx + u * 0.6f, cy - u * 0.8f);
                c.drawPath(path, stroke);
                break;
            }
            case I_ERASE: {
                c.save();
                c.rotate(-40, cx, cy);
                rf.set(cx - u * 0.9f, cy - u * 0.45f, cx + u * 0.9f, cy + u * 0.45f);
                stroke.setStrokeWidth(s * 0.09f);
                c.drawRoundRect(rf, u * 0.2f, u * 0.2f, stroke);
                rf.set(cx - u * 0.9f, cy - u * 0.45f, cx - u * 0.1f, cy + u * 0.45f);
                c.drawRoundRect(rf, u * 0.2f, u * 0.2f, fill);
                c.restore();
                break;
            }
            case I_ROUNDABOUT: {
                stroke.setStrokeWidth(s * 0.2f);
                c.drawCircle(cx, cy, u * 0.6f, stroke);
                stroke.setStrokeWidth(s * 0.14f);
                c.drawLine(cx, cy - u * 0.6f, cx, cy - u, stroke);
                c.drawLine(cx, cy + u * 0.6f, cx, cy + u, stroke);
                c.drawLine(cx - u * 0.6f, cy, cx - u, cy, stroke);
                c.drawLine(cx + u * 0.6f, cy, cx + u, cy, stroke);
                break;
            }
            case I_LIGHT: {
                rf.set(cx - u * 0.42f, cy - u * 0.95f, cx + u * 0.42f, cy + u * 0.95f);
                c.drawRoundRect(rf, u * 0.3f, u * 0.3f, fill);
                int bg = pal.night ? 0xFF303540 : 0xFFFFFFFF;
                fill.setColor(bg);
                c.drawCircle(cx, cy - u * 0.55f, u * 0.2f, fill);
                c.drawCircle(cx, cy, u * 0.2f, fill);
                c.drawCircle(cx, cy + u * 0.55f, u * 0.2f, fill);
                break;
            }
            case I_MOTORWAY: {
                stroke.setStrokeWidth(s * 0.3f);
                c.drawLine(cx - u * 0.7f, cy + u * 0.7f, cx + u * 0.7f, cy - u * 0.7f, stroke);
                c.drawCircle(cx - u * 0.7f, cy + u * 0.7f, u * 0.3f, fill);
                c.drawCircle(cx + u * 0.7f, cy - u * 0.7f, u * 0.3f, fill);
                stroke.setColor(pal.night ? 0xFF303540 : 0xFFFFFFFF);
                stroke.setStrokeWidth(s * 0.06f);
                c.drawLine(cx - u * 0.25f, cy + u * 0.25f, cx - u * 0.05f, cy + u * 0.05f, stroke);
                c.drawLine(cx + u * 0.12f, cy - u * 0.12f, cx + u * 0.32f, cy - u * 0.32f, stroke);
                break;
            }
            case I_BRIDGE: {
                stroke.setStrokeWidth(s * 0.13f);
                c.drawLine(cx - u, cy - u * 0.15f, cx + u, cy - u * 0.15f, stroke);
                rf.set(cx - u * 0.75f, cy - u * 0.15f, cx + u * 0.75f, cy + u * 1.1f);
                c.drawArc(rf, 180, 180, false, stroke);
                stroke.setStrokeWidth(s * 0.08f);
                c.drawLine(cx - u * 0.75f, cy - u * 0.15f, cx - u * 0.75f, cy + u * 0.5f, stroke);
                c.drawLine(cx + u * 0.75f, cy - u * 0.15f, cx + u * 0.75f, cy + u * 0.5f, stroke);
                break;
            }
            case I_TUNNEL: {
                path.reset();
                path.moveTo(cx - u, cy + u * 0.8f);
                path.lineTo(cx - u * 0.2f, cy - u * 0.8f);
                path.lineTo(cx + u * 0.3f, cy - u * 0.2f);
                path.lineTo(cx + u * 0.55f, cy - u * 0.5f);
                path.lineTo(cx + u, cy + u * 0.8f);
                path.close();
                c.drawPath(path, fill);
                fill.setColor(pal.night ? 0xFF303540 : 0xFFFFFFFF);
                rf.set(cx - u * 0.35f, cy + u * 0.1f, cx + u * 0.35f, cy + u * 1.5f);
                c.drawArc(rf, 180, 180, true, fill);
                break;
            }
            case I_UNDO: {
                stroke.setStrokeWidth(s * 0.13f);
                rf.set(cx - u * 0.7f, cy - u * 0.6f, cx + u * 0.75f, cy + u * 0.85f);
                c.drawArc(rf, 200, 230, false, stroke);
                path.reset();
                path.moveTo(cx - u * 1.0f, cy - u * 0.55f);
                path.lineTo(cx - u * 0.35f, cy - u * 0.75f);
                path.lineTo(cx - u * 0.6f, cy - u * 0.05f);
                path.close();
                c.drawPath(path, fill);
                break;
            }
            case I_PAUSE:
                rf.set(cx - u * 0.6f, cy - u * 0.7f, cx - u * 0.15f, cy + u * 0.7f);
                c.drawRoundRect(rf, u * 0.1f, u * 0.1f, fill);
                rf.set(cx + u * 0.15f, cy - u * 0.7f, cx + u * 0.6f, cy + u * 0.7f);
                c.drawRoundRect(rf, u * 0.1f, u * 0.1f, fill);
                break;
            case I_PLAY:
                path.reset();
                path.moveTo(cx - u * 0.45f, cy - u * 0.75f);
                path.lineTo(cx + u * 0.75f, cy);
                path.lineTo(cx - u * 0.45f, cy + u * 0.75f);
                path.close();
                c.drawPath(path, fill);
                break;
            case I_FAST:
                path.reset();
                path.moveTo(cx - u * 0.95f, cy - u * 0.65f);
                path.lineTo(cx, cy);
                path.lineTo(cx - u * 0.95f, cy + u * 0.65f);
                path.close();
                path.moveTo(cx - u * 0.05f, cy - u * 0.65f);
                path.lineTo(cx + u * 0.9f, cy);
                path.lineTo(cx - u * 0.05f, cy + u * 0.65f);
                path.close();
                c.drawPath(path, fill);
                break;
            case I_MENU:
                stroke.setStrokeWidth(s * 0.12f);
                for (int i = -1; i <= 1; i++) c.drawLine(cx - u * 0.7f, cy + i * u * 0.5f, cx + u * 0.7f, cy + i * u * 0.5f, stroke);
                break;
            case I_GEAR: {
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.rotate(i * 45, cx, cy);
                    rf.set(cx - u * 0.18f, cy - u, cx + u * 0.18f, cy - u * 0.5f);
                    c.drawRoundRect(rf, u * 0.06f, u * 0.06f, fill);
                    c.restore();
                }
                c.drawCircle(cx, cy, u * 0.68f, fill);
                fill.setColor(pal.night ? 0xFF303540 : 0xFFFFFFFF);
                c.drawCircle(cx, cy, u * 0.28f, fill);
                break;
            }
            case I_BACK:
                stroke.setStrokeWidth(s * 0.13f);
                c.drawLine(cx + u * 0.3f, cy - u * 0.7f, cx - u * 0.4f, cy, stroke);
                c.drawLine(cx - u * 0.4f, cy, cx + u * 0.3f, cy + u * 0.7f, stroke);
                break;
            case I_CAR:
                rf.set(cx - u, cy - u * 0.15f, cx + u, cy + u * 0.45f);
                c.drawRoundRect(rf, u * 0.2f, u * 0.2f, fill);
                rf.set(cx - u * 0.55f, cy - u * 0.6f, cx + u * 0.45f, cy);
                c.drawRoundRect(rf, u * 0.25f, u * 0.25f, fill);
                c.drawCircle(cx - u * 0.55f, cy + u * 0.5f, u * 0.25f, fill);
                c.drawCircle(cx + u * 0.55f, cy + u * 0.5f, u * 0.25f, fill);
                break;
            case I_HELP:
                stroke.setStrokeWidth(s * 0.1f);
                c.drawCircle(cx, cy, u * 0.9f, stroke);
                txt(c, "?", cx, cy + s * 0.2f, s / dp * 0.6f, col, medium, Paint.Align.CENTER);
                break;
        }
    }

    // ------------------------------------------------------------------ in-game HUD

    void drawGame(Canvas c, GameView v, World w, int W, int H, float now) {
        float m = 12 * dp;
        float L = insetL + m, T = insetT + m, R = W - insetR - m, B = H - insetB - m;

        // Clock & week.
        float cr = 22 * dp, ccx = L + cr, ccy = T + cr;
        fill.setColor(pal.shadow);
        c.drawCircle(ccx, ccy + 2 * dp, cr, fill);
        fill.setColor(pal.panel);
        c.drawCircle(ccx, ccy, cr, fill);
        float day = w.day();
        int dayIdx = ((int) day) % 7;
        float weekFrac = (day % 7) / 7f;
        stroke.setStrokeCap(Paint.Cap.BUTT);
        stroke.setStrokeWidth(4 * dp);
        stroke.setColor(Palette.alpha(pal.text, 0x18));
        rf.set(ccx - cr + 5 * dp, ccy - cr + 5 * dp, ccx + cr - 5 * dp, ccy + cr - 5 * dp);
        c.drawArc(rf, 0, 360, false, stroke);
        stroke.setColor(pal.text);
        c.drawArc(rf, -90, 360 * weekFrac, false, stroke);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        // Day hand
        double a = (day % 1f) * 2 * Math.PI - Math.PI / 2;
        stroke.setStrokeWidth(2 * dp);
        c.drawLine(ccx, ccy, ccx + (float) Math.cos(a) * cr * 0.5f, ccy + (float) Math.sin(a) * cr * 0.5f, stroke);
        fill.setColor(pal.text);
        c.drawCircle(ccx, ccy, 2.5f * dp, fill);
        txt(c, "WEEK " + (w.week + 1), ccx + cr + 10 * dp, ccy - 1 * dp, 15, pal.text, medium, Paint.Align.LEFT);
        txt(c, DAYS[dayIdx], ccx + cr + 10 * dp, ccy + 15 * dp, 12, pal.textDim, regular, Paint.Align.LEFT);

        // Menu button.
        float mb = 20 * dp;
        float mcx = R - mb, mcy = T + cr;
        add(B_MENU, mcx - mb, mcy - mb, mcx + mb, mcy + mb);
        fill.setColor(pal.shadow);
        c.drawCircle(mcx, mcy + 2 * dp, mb, fill);
        fill.setColor(pal.panel);
        c.drawCircle(mcx, mcy, mb, fill);
        icon(c, I_MENU, mcx, mcy, 18 * dp, pal.text);

        // Speed controls.
        float bw = 38 * dp, bh = 40 * dp;
        float sr = mcx - mb - 10 * dp, sl = sr - bw * 3;
        panel(c, sl, mcy - bh / 2, sr, mcy + bh / 2, bh / 2, pal.panel);
        int[] ids = {B_PAUSE, B_PLAY, B_FAST};
        int[] ics = {I_PAUSE, I_PLAY, I_FAST};
        int sel = v.paused ? 0 : v.fast ? 2 : 1;
        for (int i = 0; i < 3; i++) {
            float bx = sl + bw * i + bw / 2;
            add(ids[i], sl + bw * i, mcy - bh / 2, sl + bw * (i + 1), mcy + bh / 2);
            if (i == sel) {
                fill.setColor(Palette.alpha(pal.text, 0x18));
                c.drawCircle(bx, mcy, 16 * dp, fill);
            }
            icon(c, ics[i], bx, mcy, 16 * dp, i == sel ? pal.text : pal.textDim);
        }

        // Score.
        String sc = String.valueOf(w.score);
        float scoreRight = sl - 14 * dp;
        text.setTextSize(24 * dp);
        text.setTypeface(medium);
        float tw = text.measureText(sc);
        txt(c, sc, scoreRight, mcy + 8 * dp, 24, pal.text, medium, Paint.Align.RIGHT);
        icon(c, I_CAR, scoreRight - tw - 16 * dp, mcy, 18 * dp, pal.textDim);

        // Toolbar.
        boolean bridges = w.hasWater, tunnels = w.hasMountains;
        int nTools = 6 + (bridges ? 1 : 0) + (tunnels ? 1 : 0);
        float tbw = 54 * dp, tbh = 62 * dp, gap = 10 * dp;
        float total = tbw * nTools + gap * (1 + ((bridges || tunnels) ? 1 : 0)) + 12 * dp;
        float tl = (W - total) / 2, tt = B - tbh;
        panel(c, tl, tt, tl + total, B, 20 * dp, pal.panel);
        float x = tl + 6 * dp;
        x = toolButton(c, B_UNDO, I_UNDO, x, tt, tbw, tbh, -1, false, w.canUndo());
        x += gap / 2;
        fill.setColor(pal.panelLine);
        c.drawRect(x - 0.5f * dp, tt + 14 * dp, x + 0.5f * dp, B - 14 * dp, fill);
        x += gap / 2;
        x = toolButton(c, B_ROAD, I_ROAD, x, tt, tbw, tbh, w.inv[World.UP_ROADS], v.tool == GameView.T_ROAD, true);
        x = toolButton(c, B_ERASE, I_ERASE, x, tt, tbw, tbh, -1, v.tool == GameView.T_ERASE, true);
        x = toolButton(c, B_ROUNDABOUT, I_ROUNDABOUT, x, tt, tbw, tbh, w.inv[World.UP_ROUNDABOUT], v.tool == GameView.T_ROUNDABOUT, true);
        x = toolButton(c, B_LIGHT, I_LIGHT, x, tt, tbw, tbh, w.inv[World.UP_LIGHTS], v.tool == GameView.T_LIGHT, true);
        x = toolButton(c, B_MOTORWAY, I_MOTORWAY, x, tt, tbw, tbh, w.inv[World.UP_MOTORWAY], v.tool == GameView.T_MOTORWAY, true);
        if (bridges || tunnels) {
            x += gap / 2;
            fill.setColor(pal.panelLine);
            c.drawRect(x - 0.5f * dp, tt + 14 * dp, x + 0.5f * dp, B - 14 * dp, fill);
            x += gap / 2;
        }
        if (bridges) x = toolButton(c, B_BRIDGE, I_BRIDGE, x, tt, tbw, tbh, w.inv[World.UP_BRIDGE], false, true);
        if (tunnels) toolButton(c, B_TUNNEL, I_TUNNEL, x, tt, tbw, tbh, w.inv[World.UP_TUNNEL], false, true);

        // Tutorial hint / toast.
        String hint = v.hint();
        if (hint != null) bubble(c, hint, W / 2f, tt - 22 * dp, 1f);
        if (v.toast != null && v.toastTime > 0 && !w.upgradePending && !w.gameOver && !v.menuOpen) {
            float al = Math.min(1, v.toastTime / 0.3f);
            bubble(c, v.toast, W / 2f, T + cr + 40 * dp, al);
        }
    }

    private void bubble(Canvas c, String s, float cx, float cy, float alpha) {
        text.setTextSize(14 * dp);
        text.setTypeface(medium);
        float tw = text.measureText(s);
        float h = 34 * dp;
        int a = (int) (alpha * 255);
        fill.setColor(Palette.alpha(pal.text, (int) (a * 0.92f)));
        rf.set(cx - tw / 2 - 16 * dp, cy - h / 2, cx + tw / 2 + 16 * dp, cy + h / 2);
        c.drawRoundRect(rf, h / 2, h / 2, fill);
        int fg = pal.night ? 0xFF1E2228 : 0xFFFFFFFF;
        txt(c, s, cx, cy + 5 * dp, 14, Palette.alpha(fg, a), medium, Paint.Align.CENTER);
    }

    private float toolButton(Canvas c, int id, int ic, float x, float t, float w, float h, int count, boolean selected, boolean enabled) {
        add(id, x, t, x + w, t + h);
        float cx = x + w / 2;
        if (selected) {
            fill.setColor(Palette.alpha(pal.text, 0x16));
            rf.set(x + 3 * dp, t + 6 * dp, x + w - 3 * dp, t + h - 6 * dp);
            c.drawRoundRect(rf, 14 * dp, 14 * dp, fill);
        }
        boolean empty = count == 0;
        int col = !enabled || empty ? Palette.alpha(pal.text, 0x55) : pal.text;
        icon(c, ic, cx, t + (count >= 0 ? 24 : 31) * dp, 24 * dp, col);
        if (count >= 0) txt(c, String.valueOf(count), cx, t + 52 * dp, 13, empty ? pal.warn : pal.textDim, medium, Paint.Align.CENTER);
        return x + w;
    }

    // ------------------------------------------------------------------ overlays

    private void dim(Canvas c, int W, int H) {
        c.drawColor(pal.night ? 0x99000000 : 0x77303438);
    }

    void drawUpgrade(Canvas c, World w, int W, int H) {
        dim(c, W, H);
        float cy = H * 0.5f;
        txt(c, "Week " + w.week + " complete", W / 2f, cy - 110 * dp, 26, 0xFFFFFFFF, medium, Paint.Align.CENTER);
        txt(c, "Your city is growing. +" + World.WEEKLY_ROADS + " roads.  Choose an upgrade:", W / 2f, cy - 82 * dp, 14, 0xDDFFFFFF, regular, Paint.Align.CENTER);
        float cw = 150 * dp, ch = 160 * dp, gap = 28 * dp;
        for (int i = 0; i < 2; i++) {
            int u = w.offers[i];
            float l = W / 2f + (i == 0 ? -gap / 2 - cw : gap / 2), t = cy - 60 * dp;
            add(B_UP0 + i, l, t, l + cw, t + ch);
            panel(c, l, t, l + cw, t + ch, 18 * dp, pal.night ? 0xFF303540 : 0xFFFFFFFF);
            icon(c, UP_ICONS[u], l + cw / 2, t + 52 * dp, 50 * dp, pal.text);
            txt(c, UP_NAMES[u], l + cw / 2, t + 112 * dp, 15, pal.text, medium, Paint.Align.CENTER);
            txt(c, "+" + World.UP_AMOUNT[u], l + cw / 2, t + 140 * dp, 18, pal.textDim, medium, Paint.Align.CENTER);
        }
    }

    void drawPauseMenu(Canvas c, int W, int H) {
        dim(c, W, H);
        float pw = 280 * dp, ph = 290 * dp;
        float l = (W - pw) / 2, t = (H - ph) / 2;
        panel(c, l, t, l + pw, t + ph, 22 * dp, pal.night ? 0xFF303540 : 0xFFFFFFFF);
        txt(c, "Paused", W / 2f, t + 44 * dp, 22, pal.text, medium, Paint.Align.CENTER);
        float bw = 220 * dp, bh = 44 * dp, y = t + 90 * dp;
        pillButton(c, B_RESUME, W / 2f, y, bw, bh, "Resume", true);
        pillButton(c, B_RESTART, W / 2f, y + 54 * dp, bw, bh, "Restart", false);
        pillButton(c, B_SETTINGS, W / 2f, y + 108 * dp, bw, bh, "Settings", false);
        pillButton(c, B_QUIT, W / 2f, y + 162 * dp, bw, bh, "Main menu", false);
    }

    void drawGameOver(Canvas c, World w, int best, boolean newBest, int W, int H) {
        dim(c, W, H);
        float pw = 340 * dp, ph = 280 * dp;
        float l = (W - pw) / 2, t = (H - ph) / 2;
        panel(c, l, t, l + pw, t + ph, 22 * dp, pal.night ? 0xFF303540 : 0xFFFFFFFF);
        txt(c, "Gridlock!", W / 2f, t + 44 * dp, 24, pal.text, medium, Paint.Align.CENTER);
        txt(c, "A destination in " + w.map.name + " waited too long.", W / 2f, t + 70 * dp, 13, pal.textDim, regular, Paint.Align.CENTER);
        txt(c, String.valueOf(w.score), W / 2f, t + 134 * dp, 46, pal.text, light, Paint.Align.CENTER);
        txt(c, "trips  ·  " + (w.week + 1) + (w.week == 0 ? " week" : " weeks"), W / 2f, t + 160 * dp, 13, pal.textDim, regular, Paint.Align.CENTER);
        txt(c, newBest ? "New best score!" : "Best: " + best, W / 2f, t + 186 * dp, 14, newBest ? pal.good : pal.textDim, medium, Paint.Align.CENTER);
        float bw = 130 * dp, bh = 44 * dp, y = t + ph - 40 * dp;
        pillButton(c, B_GO_RETRY, W / 2f - bw / 2 - 8 * dp, y, bw, bh, "Try again", true);
        pillButton(c, B_GO_MENU, W / 2f + bw / 2 + 8 * dp, y, bw, bh, "Cities", false);
    }

    // ------------------------------------------------------------------ main menu

    Bitmap thumb(MapDef m) {
        if (thumbs[m.id] != null) return thumbs[m.id];
        int k = 6;
        Bitmap b = Bitmap.createBitmap(MapDef.W * k, MapDef.H * k, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        byte[] t = m.terrain();
        Paint p = new Paint();
        c.drawColor(m.land);
        for (int y = 0; y < MapDef.H; y++) {
            for (int x = 0; x < MapDef.W; x++) {
                byte v = t[y * MapDef.W + x];
                if (v == MapDef.LAND) continue;
                p.setColor(v == MapDef.WATER ? m.water : m.mountain);
                c.drawRect(x * k, y * k, (x + 1) * k, (y + 1) * k, p);
            }
        }
        // Small blocks hinting at buildings.
        java.util.Random r = new java.util.Random(m.seed);
        for (int i = 0; i < 14; i++) {
            int x = 6 + r.nextInt(MapDef.W - 12), y = 4 + r.nextInt(MapDef.H - 8);
            if (t[y * MapDef.W + x] != MapDef.LAND) continue;
            p.setColor(Palette.BUILD[r.nextInt(6)]);
            c.drawRect(x * k + 1, y * k + 1, (x + 1) * k - 1, (y + 1) * k - 1, p);
        }
        thumbs[m.id] = b;
        return b;
    }

    void drawMenu(Canvas c, int W, int H, int mode, int[] bests, float now) {
        MapDef bgMap = MapDef.ALL[0];
        c.drawColor(pal.night ? pal.land : bgMap.land);
        float m = 22 * dp;
        float L = insetL + m, T = insetT + m, R = W - insetR - m, B = H - insetB - m;
        // Decorative road ribbon behind the title.
        stroke.setColor(Palette.alpha(pal.road, 0x30));
        stroke.setStrokeWidth(12 * dp);
        path.reset();
        path.moveTo(0, T + 64 * dp);
        path.cubicTo(W * 0.3f, T + 20 * dp, W * 0.55f, T + 110 * dp, W, T + 50 * dp);
        c.drawPath(path, stroke);
        float carT = (now * 0.06f) % 1f;
        fill.setColor(Palette.BUILD[1]);
        c.drawCircle(W * carT, T + 64 * dp + (float) Math.sin(carT * 6) * 10 * dp - carT * 14 * dp, 5 * dp, fill);

        txt(c, "Little Roads", L, T + 34 * dp, 34, pal.text, light, Paint.Align.LEFT);
        txt(c, "Draw roads. Keep the city moving.", L + 2 * dp, T + 56 * dp, 13, pal.textDim, regular, Paint.Align.LEFT);

        // Settings and help buttons.
        float gb = 20 * dp;
        float gx = R - gb, gy = T + 20 * dp;
        add(B_OPEN_SETTINGS, gx - gb, gy - gb, gx + gb, gy + gb);
        fill.setColor(pal.panel);
        c.drawCircle(gx, gy, gb, fill);
        icon(c, I_GEAR, gx, gy, 20 * dp, pal.text);
        float hx = gx - 2 * gb - 10 * dp;
        add(B_OPEN_HELP, hx - gb, gy - gb, hx + gb, gy + gb);
        fill.setColor(pal.panel);
        c.drawCircle(hx, gy, gb, fill);
        icon(c, I_HELP, hx, gy, 20 * dp, pal.text);

        // Mode selector.
        float sw = 84 * dp, sh = 34 * dp;
        float sr = hx - gb - 16 * dp, sl = sr - sw * 3;
        fill.setColor(Palette.alpha(pal.text, 0x10));
        rf.set(sl, gy - sh / 2, sr, gy + sh / 2);
        c.drawRoundRect(rf, sh / 2, sh / 2, fill);
        for (int i = 0; i < 3; i++) {
            float l = sl + sw * i;
            add(B_MODE0 + i, l, gy - sh / 2, l + sw, gy + sh / 2);
            if (i == mode) {
                fill.setColor(pal.text);
                rf.set(l + 2 * dp, gy - sh / 2 + 2 * dp, l + sw - 2 * dp, gy + sh / 2 - 2 * dp);
                c.drawRoundRect(rf, sh / 2, sh / 2, fill);
            }
            txt(c, MODES[i], l + sw / 2, gy + 5 * dp, 13, i == mode ? (pal.night ? 0xFF1E2228 : 0xFFFFFFFF) : pal.text, medium, Paint.Align.CENTER);
        }

        // City cards (3 x 2).
        float top = T + 80 * dp;
        float gapX = 14 * dp, gapY = 14 * dp;
        float cw = (R - L - gapX * 2) / 3f;
        float ch = (B - top - gapY) / 2f;
        float thumbH = ch - 46 * dp;
        float thumbW = thumbH * MapDef.W / MapDef.H;
        if (thumbW > cw - 16 * dp) { thumbW = cw - 16 * dp; thumbH = thumbW * MapDef.H / MapDef.W; }
        for (int i = 0; i < MapDef.ALL.length; i++) {
            MapDef md = MapDef.ALL[i];
            int col = i % 3, row = i / 3;
            float l = L + col * (cw + gapX), t = top + row * (ch + gapY);
            add(B_MAP0 + i, l, t, l + cw, t + ch);
            panel(c, l, t, l + cw, t + ch, 16 * dp, pal.night ? 0xFF303540 : 0xFFFFFFFF);
            float tx = l + (cw - thumbW) / 2, ty = t + 8 * dp;
            c.save();
            rf.set(tx, ty, tx + thumbW, ty + thumbH);
            path.reset();
            path.addRoundRect(rf, 10 * dp, 10 * dp, Path.Direction.CW);
            c.clipPath(path);
            c.drawBitmap(thumb(md), null, rf, bmp);
            c.restore();
            txt(c, md.name, l + 14 * dp, t + ch - 16 * dp, 15, pal.text, medium, Paint.Align.LEFT);
            String b = bests[i] > 0 ? "Best " + bests[i] : md.country;
            txt(c, b, l + cw - 14 * dp, t + ch - 16 * dp, 12, pal.textDim, regular, Paint.Align.RIGHT);
        }
    }

    // ------------------------------------------------------------------ settings & help

    private void header(Canvas c, String title, int W, float T, float L) {
        float gb = 20 * dp;
        add(B_BACK, L, T, L + 2 * gb, T + 2 * gb);
        fill.setColor(pal.panel);
        c.drawCircle(L + gb, T + gb, gb, fill);
        icon(c, I_BACK, L + gb, T + gb, 18 * dp, pal.text);
        txt(c, title, W / 2f, T + gb + 9 * dp, 24, pal.text, medium, Paint.Align.CENTER);
    }

    void drawSettings(Canvas c, int W, int H, boolean[] values) {
        c.drawColor(pal.land);
        float m = 22 * dp;
        float L = insetL + m, T = insetT + m;
        header(c, "Settings", W, T, L);
        String[] names = {"Sound effects", "Haptic feedback", "Show grid", "Colourblind symbols", "Night mode"};
        int[] ids = {B_SET_SOUND, B_SET_HAPTIC, B_SET_GRID, B_SET_COLORBLIND, B_SET_NIGHT};
        float pw = Math.min(460 * dp, W - 2 * m), rowH = 48 * dp;
        float l = (W - pw) / 2, t = T + 60 * dp;
        float ph = rowH * names.length;
        panel(c, l, t, l + pw, t + ph, 16 * dp, pal.night ? 0xFF303540 : 0xFFFFFFFF);
        for (int i = 0; i < names.length; i++) {
            float y = t + rowH * i;
            add(ids[i], l, y, l + pw, y + rowH);
            if (i > 0) {
                fill.setColor(pal.panelLine);
                c.drawRect(l + 16 * dp, y, l + pw - 16 * dp, y + 1, fill);
            }
            txt(c, names[i], l + 18 * dp, y + rowH / 2 + 5 * dp, 15, pal.text, regular, Paint.Align.LEFT);
            // Switch
            float sw = 44 * dp, sh = 24 * dp, sx = l + pw - 18 * dp - sw, sy = y + (rowH - sh) / 2;
            fill.setColor(values[i] ? pal.good : Palette.alpha(pal.text, 0x30));
            rf.set(sx, sy, sx + sw, sy + sh);
            c.drawRoundRect(rf, sh / 2, sh / 2, fill);
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(values[i] ? sx + sw - sh / 2 : sx + sh / 2, sy + sh / 2, sh / 2 - 3 * dp, fill);
        }
        float y = t + ph + 36 * dp;
        if (y + 22 * dp < H - insetB) pillButton(c, B_SET_RESET, W / 2f, y, 220 * dp, 40 * dp, "Reset best scores", false);
    }

    private ArrayList<String> wrap(String s, float maxW) {
        ArrayList<String> out = new ArrayList<>();
        String cur = "";
        for (String word : s.split(" ")) {
            String next = cur.isEmpty() ? word : cur + " " + word;
            if (text.measureText(next) > maxW && !cur.isEmpty()) {
                out.add(cur);
                cur = word;
            } else {
                cur = next;
            }
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }

    @SuppressWarnings("unchecked")
    void drawHelp(Canvas c, int W, int H) {
        c.drawColor(pal.land);
        float m = 22 * dp;
        float L = insetL + m, T = insetT + m;
        header(c, "How to play", W, T, L);
        String[][] lines = {
                {"Connect", "Drag your finger from a house's driveway to a destination of the same colour to build a road."},
                {"Deliver", "Destinations show pins when they need visitors. Cars leave matching houses, park, then drive home."},
                {"Don't jam", "If a destination collects too many pins, a ring fills up. When it closes, the game is over."},
                {"Erase & undo", "Pick the eraser and drag over roads to remove them (tiles are refunded). Undo reverts your last edit."},
                {"Upgrades", "Each week brings more roads plus a choice: bridges, tunnels, motorways, roundabouts or traffic lights."},
                {"Bridges", "Drag a road straight across water (or into a mountain for a tunnel) to use a bridge or tunnel."},
                {"Camera", "Pinch with two fingers to zoom, drag with two fingers to pan. You can still build while paused."},
        };
        float pw = Math.min(680 * dp, W - insetL - insetR - 2 * m);
        float l = (W - pw) / 2, t = T + 58 * dp;
        float textW = pw - 112 * dp;
        text.setTextSize(13 * dp);
        text.setTypeface(regular);
        ArrayList<String>[] wrapped = new ArrayList[lines.length];
        int totalLines = 0;
        for (int i = 0; i < lines.length; i++) {
            wrapped[i] = wrap(lines[i][1], textW);
            totalLines += wrapped[i].size();
        }
        float avail = H - insetB - m - t;
        float lineH = Math.min(18 * dp, avail / (totalLines + lines.length * 0.9f));
        float gapH = lineH * 0.9f;
        float y = t + lineH;
        for (int i = 0; i < lines.length; i++) {
            txt(c, lines[i][0], l, y, 14, pal.text, medium, Paint.Align.LEFT);
            for (String ln : wrapped[i]) {
                txt(c, ln, l + 112 * dp, y, 13, pal.textDim, regular, Paint.Align.LEFT);
                y += lineH;
            }
            y += gapH;
        }
    }
}
