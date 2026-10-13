package com.littleroads.game;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Draws the world in world units (1 unit = 1 tile) after the camera transform is applied. */
final class Renderer {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flat = new Paint();          // no AA so neighbouring tiles join seamlessly
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path path = new Path();
    Palette pal;

    // Options
    boolean showGrid = true;
    boolean colorblind;

    Renderer() {
        fill.setStyle(Paint.Style.FILL);
        flat.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    // Visible tile window, set per frame.
    private int vx0, vy0, vx1, vy1;
    private float scale;

    void draw(Canvas c, World w, float scale, float ox, float oy, int screenW, int screenH, float now, GameView.Preview pv) {
        this.scale = scale;
        vx0 = Math.max(0, (int) Math.floor(-ox / scale) - 1);
        vy0 = Math.max(0, (int) Math.floor(-oy / scale) - 1);
        vx1 = Math.min(World.W - 1, (int) Math.ceil((screenW - ox) / scale) + 1);
        vy1 = Math.min(World.H - 1, (int) Math.ceil((screenH - oy) / scale) + 1);

        c.drawColor(pal.outside);
        c.save();
        c.translate(ox, oy);
        c.scale(scale, scale);

        drawTerrain(c, w);
        drawRegion(c, w);
        drawRoads(c, w, now);
        drawBuildingsGround(c, w);
        drawCars(c, w, false);
        drawBuildings(c, w, now);
        drawMotorways(c, w);
        drawCars(c, w, true);
        drawPins(c, w, now);
        if (pv != null) drawPreview(c, w, pv);
        c.restore();
    }

    // ------------------------------------------------------------------ terrain

    private boolean same(World w, int x, int y, byte type) {
        if (x < 0 || y < 0 || x >= World.W || y >= World.H) return true;
        return w.terrain[y * World.W + x] == type;
    }

    private boolean inner(World w, int x, int y) {
        for (int d = 0; d < 8; d++) if (!same(w, x + World.DX[d], y + World.DY[d], MapDef.MOUNTAIN)) return false;
        return same(w, x, y, MapDef.MOUNTAIN);
    }

    private void drawTerrain(Canvas c, World w) {
        c.drawColor(pal.land);
        if (showGrid) {
            stroke.setColor(pal.grid);
            stroke.setStrokeWidth(1f / scale);
            stroke.setStrokeCap(Paint.Cap.BUTT);
            for (int x = vx0; x <= vx1 + 1; x++) c.drawLine(x, vy0, x, vy1 + 1, stroke);
            for (int y = vy0; y <= vy1 + 1; y++) c.drawLine(vx0, y, vx1 + 1, y, stroke);
            stroke.setStrokeCap(Paint.Cap.ROUND);
        }
        final float r = 0.38f;
        for (int y = vy0; y <= vy1; y++) {
            for (int x = vx0; x <= vx1; x++) {
                byte t = w.terrain[y * World.W + x];
                if (t == MapDef.LAND) {
                    // Round the land corner where water/mountain wraps around it.
                    for (int k = 0; k < 4; k++) {
                        int sx = k == 0 || k == 3 ? -1 : 1, sy = k < 2 ? -1 : 1;
                        int ax = x + sx, ay = y + sy;
                        if (ax < 0 || ay < 0 || ax >= World.W || ay >= World.H) continue;
                        byte o = w.terrain[ay * World.W + ax];
                        if (o == MapDef.LAND) continue;
                        if (!same(w, x + sx, y, o) || !same(w, x, y + sy, o)) continue;
                        int col = o == MapDef.WATER ? pal.water : pal.mountain;
                        float cx = sx < 0 ? x : x + 1, cy = sy < 0 ? y : y + 1;
                        flat.setColor(col);
                        c.drawRect(Math.min(cx, cx - sx * r), Math.min(cy, cy - sy * r),
                                Math.max(cx, cx - sx * r), Math.max(cy, cy - sy * r), flat);
                        fill.setColor(pal.land);
                        c.drawCircle(cx - sx * r, cy - sy * r, r, fill);
                    }
                    continue;
                }
                int col = t == MapDef.WATER ? pal.water : pal.mountain;
                roundedTile(c, w, x, y, t, col, r, 0);
                if (t == MapDef.MOUNTAIN && inner(w, x, y)) {
                    // A raised inner contour for a terraced look.
                    fill.setColor(pal.mountainHi);
                    c.drawCircle(x + 0.5f, y + 0.5f, 0.42f, fill);
                    for (int d = 0; d < 8; d += 2) {
                        if (inner(w, x + World.DX[d], y + World.DY[d])) {
                            flat.setColor(pal.mountainHi);
                            float x0 = x + 0.5f + Math.min(0, World.DX[d]) * 0.5f, x1 = x + 0.5f + Math.max(0, World.DX[d]) * 0.5f;
                            float y0 = y + 0.5f + Math.min(0, World.DY[d]) * 0.5f, y1 = y + 0.5f + Math.max(0, World.DY[d]) * 0.5f;
                            if (World.DX[d] == 0) { x0 -= 0.42f; x1 += 0.42f; } else { y0 -= 0.42f; y1 += 0.42f; }
                            c.drawRect(x0, y0, x1, y1, flat);
                        }
                    }
                }
            }
        }
    }

    private void roundedTile(Canvas c, World w, int x, int y, byte t, int col, float r, float inset) {
        flat.setColor(col);
        fill.setColor(col);
        c.drawRect(x + r, y, x + 1 - r, y + 1, flat);
        c.drawRect(x, y + r, x + 1, y + 1 - r, flat);
        for (int k = 0; k < 4; k++) {
            int sx = k == 0 || k == 3 ? -1 : 1, sy = k < 2 ? -1 : 1;
            boolean round = !same(w, x + sx, y, t) && !same(w, x, y + sy, t);
            float cx = sx < 0 ? x + r : x + 1 - r, cy = sy < 0 ? y + r : y + 1 - r;
            if (round) {
                c.drawCircle(cx, cy, r, fill);
            } else {
                float ex = sx < 0 ? x : x + 1, ey = sy < 0 ? y : y + 1;
                c.drawRect(Math.min(cx, ex), Math.min(cy, ey), Math.max(cx, ex), Math.max(cy, ey), flat);
            }
        }
    }

    private void drawRegion(Canvas c, World w) {
        flat.setColor(Palette.alpha(pal.outside, 0xC8));
        float L = -50, T = -50, R = World.W + 50, B = World.H + 50;
        c.drawRect(L, T, R, w.ry0, flat);
        c.drawRect(L, w.ry1, R, B, flat);
        c.drawRect(L, w.ry0, w.rx0, w.ry1, flat);
        c.drawRect(w.rx1, w.ry0, R, w.ry1, flat);
        stroke.setColor(pal.panelLine);
        stroke.setStrokeWidth(1.5f / scale);
        rf.set(w.rx0, w.ry0, w.rx1, w.ry1);
        c.drawRect(rf, stroke);
    }

    // ------------------------------------------------------------------ roads

    static final float ROAD_W = 0.42f;

    private void drawRoads(Canvas c, World w, float now) {
        final int W = World.W;
        // Bridges first (rails and shadow underneath the road surface).
        for (Crossing cr : w.crossings) {
            if (cr.tunnel) continue;
            float ax = cr.a % W + 0.5f, ay = cr.a / W + 0.5f, bx = cr.b % W + 0.5f, by = cr.b / W + 0.5f;
            stroke.setColor(pal.shadow);
            stroke.setStrokeWidth(0.62f);
            stroke.setStrokeCap(Paint.Cap.BUTT);
            c.drawLine(ax + 0.06f, ay + 0.12f, bx + 0.06f, by + 0.12f, stroke);
            stroke.setColor(pal.bridgeRail);
            stroke.setStrokeWidth(0.62f);
            c.drawLine(ax, ay, bx, by, stroke);
            stroke.setStrokeCap(Paint.Cap.ROUND);
        }
        stroke.setColor(pal.road);
        stroke.setStrokeWidth(ROAD_W);
        fill.setColor(pal.road);
        for (int y = vy0; y <= vy1; y++) {
            for (int x = vx0; x <= vx1; x++) {
                int t = y * W + x;
                if (!w.road[t]) continue;
                Crossing cr = w.crossAt[t];
                if (cr != null && cr.tunnel) continue;
                int cb = w.conn[t] & 0xFF;
                for (int d = 0; d < 8; d++) {
                    if ((cb & (1 << d)) == 0) continue;
                    int n = t + World.DX[d] + World.DY[d] * W;
                    Crossing cn = w.crossAt[n];
                    if (cn != null && cn.tunnel) {
                        drawTunnelMouth(c, x, y, d);
                        continue;
                    }
                    // Draw each link once, except where the neighbour is off-screen.
                    if (d >= 4 && n % W >= vx0 && n % W <= vx1 && n / W >= vy0 && n / W <= vy1) continue;
                    c.drawLine(x + 0.5f, y + 0.5f, x + 0.5f + World.DX[d], y + 0.5f + World.DY[d], stroke);
                }
                c.drawCircle(x + 0.5f, y + 0.5f, ROAD_W / 2, fill);
            }
        }
        // Tunnel interiors: faint dashes.
        for (Crossing cr : w.crossings) {
            if (!cr.tunnel) continue;
            fill.setColor(Palette.alpha(pal.road, 0x55));
            for (int t : cr.interior) {
                float cx = t % W + 0.5f, cy = t / W + 0.5f;
                c.drawCircle(cx, cy, 0.08f, fill);
                c.drawCircle(cx + World.DX[cr.dir] * 0.33f, cy + World.DY[cr.dir] * 0.33f, 0.06f, fill);
                c.drawCircle(cx - World.DX[cr.dir] * 0.33f, cy - World.DY[cr.dir] * 0.33f, 0.06f, fill);
            }
            fill.setColor(pal.road);
        }
        // Roundabouts and traffic lights.
        for (int y = vy0; y <= vy1; y++) {
            for (int x = vx0; x <= vx1; x++) {
                int t = y * W + x;
                byte sp = w.special[t];
                if (sp == World.SP_ROUNDABOUT) {
                    fill.setColor(pal.road);
                    c.drawCircle(x + 0.5f, y + 0.5f, 0.46f, fill);
                    fill.setColor(pal.island);
                    c.drawCircle(x + 0.5f, y + 0.5f, 0.2f, fill);
                    stroke.setColor(pal.lotLine);
                    stroke.setStrokeWidth(0.025f);
                    c.drawCircle(x + 0.5f, y + 0.5f, 0.33f, stroke);
                } else if (sp == World.SP_LIGHT) {
                    boolean active = w.isIntersection(t);
                    fill.setColor(0xFF2B2D31);
                    c.drawCircle(x + 0.5f, y + 0.5f, 0.17f, fill);
                    int ph = w.lightPhase[t];
                    stroke.setStrokeWidth(0.07f);
                    stroke.setColor(active ? 0xFF4CD07D : 0xFF9AA0A6);
                    if (ph == 0) c.drawLine(x + 0.39f, y + 0.5f, x + 0.61f, y + 0.5f, stroke);
                    else c.drawLine(x + 0.5f, y + 0.39f, x + 0.5f, y + 0.61f, stroke);
                    fill.setColor(active ? 0xFFE5534B : 0xFF9AA0A6);
                    float rx = ph == 0 ? 0 : 0.11f, ry = ph == 0 ? 0.11f : 0;
                    c.drawCircle(x + 0.5f + rx, y + 0.5f + ry, 0.035f, fill);
                    c.drawCircle(x + 0.5f - rx, y + 0.5f - ry, 0.035f, fill);
                }
            }
        }
        stroke.setColor(pal.road);
    }

    private void drawTunnelMouth(Canvas c, int x, int y, int d) {
        float cx = x + 0.5f + World.DX[d] * 0.5f, cy = y + 0.5f + World.DY[d] * 0.5f;
        stroke.setColor(pal.road);
        stroke.setStrokeWidth(ROAD_W);
        c.drawLine(x + 0.5f, y + 0.5f, cx, cy, stroke);
        fill.setColor(0xFF2B2D31);
        c.drawCircle(cx, cy, 0.25f, fill);
        fill.setColor(pal.road);
    }

    private void drawMotorways(Canvas c, World w) {
        final int W = World.W;
        for (Motorway m : w.motorways) {
            float ax = m.a % W + 0.5f, ay = m.a / W + 0.5f, bx = m.b % W + 0.5f, by = m.b / W + 0.5f;
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setColor(pal.shadow);
            stroke.setStrokeWidth(0.56f);
            c.drawLine(ax + 0.10f, ay + 0.16f, bx + 0.10f, by + 0.16f, stroke);
            stroke.setColor(pal.motorwayEdge);
            stroke.setStrokeWidth(0.58f);
            c.drawLine(ax, ay, bx, by, stroke);
            stroke.setColor(pal.motorway);
            stroke.setStrokeWidth(0.44f);
            c.drawLine(ax, ay, bx, by, stroke);
            // dashed centre line
            float len = m.length;
            float ux = (bx - ax) / len, uy = (by - ay) / len;
            stroke.setColor(Palette.alpha(pal.motorwayEdge, 0xAA));
            stroke.setStrokeWidth(0.04f);
            stroke.setStrokeCap(Paint.Cap.BUTT);
            for (float s = 0.5f; s < len - 0.5f; s += 0.5f) {
                c.drawLine(ax + ux * s, ay + uy * s, ax + ux * (s + 0.22f), ay + uy * (s + 0.22f), stroke);
            }
            stroke.setStrokeCap(Paint.Cap.ROUND);
            fill.setColor(pal.motorwayEdge);
            c.drawCircle(ax, ay, 0.31f, fill);
            c.drawCircle(bx, by, 0.31f, fill);
            fill.setColor(pal.motorway);
            c.drawCircle(ax, ay, 0.24f, fill);
            c.drawCircle(bx, by, 0.24f, fill);
        }
    }

    // ------------------------------------------------------------------ buildings

    private boolean visible(float x, float y, float m) {
        return x >= vx0 - m && x <= vx1 + 1 + m && y >= vy0 - m && y <= vy1 + 1 + m;
    }

    static float popScale(float age) {
        if (age >= 0.5f) return 1;
        float t = Math.max(0, age / 0.5f);
        float s = 1.70158f;
        t -= 1;
        return t * t * ((s + 1) * t + s) + 1;
    }

    private void drawBuildingsGround(Canvas c, World w) {
        for (Dest d : w.dests) {
            if (!visible(d.cx, d.cy, 3)) continue;
            int col = pal.build[d.color];
            float sc = popScale(w.time - d.spawnTime);
            c.save();
            c.translate(d.cx, d.cy);
            c.scale(sc, sc);
            // Driveway to the entrance tile.
            stroke.setColor(pal.road);
            stroke.setStrokeWidth(0.36f);
            stroke.setStrokeCap(Paint.Cap.BUTT);
            c.drawLine(d.fx * 0.7f, d.fy * 0.7f, d.fx * 1.5f, d.fy * 1.5f, stroke);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            // Car park.
            fill.setColor(Palette.mix(col, pal.night ? 0xFF000000 : 0xFFFFFFFF, pal.night ? 0.45f : 0.62f));
            rf.set(-d.w / 2f + 0.05f, -d.h / 2f + 0.05f, d.w / 2f - 0.05f, d.h / 2f - 0.05f);
            c.drawRoundRect(rf, 0.16f, 0.16f, fill);
            // Parking bay lines.
            stroke.setColor(pal.lotLine);
            stroke.setStrokeWidth(0.03f);
            for (int i = 0; i < World.SLOTS; i++) {
                float lat = Dest.SLOT_LAT[i];
                for (int e = -1; e <= 1; e += 2) {
                    float l = lat + e * 0.2f;
                    float x0 = d.fx * 0.02f + d.rx * l, y0 = d.fy * 0.02f + d.ry * l;
                    float x1 = d.fx * 0.42f + d.rx * l, y1 = d.fy * 0.42f + d.ry * l;
                    c.drawLine(x0, y0, x1, y1, stroke);
                }
            }
            c.restore();
        }
        // House driveways.
        stroke.setColor(pal.road);
        stroke.setStrokeWidth(0.26f);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        for (House h : w.houses) {
            if (!visible(h.x, h.y, 1)) continue;
            float sc = popScale(w.time - h.spawnTime);
            float cx = h.x + 0.5f, cy = h.y + 0.5f;
            float fx = World.DX[h.dir], fy = World.DY[h.dir];
            c.drawLine(cx - fx * 0.1f, cy - fy * 0.1f, cx + fx * 0.55f * sc, cy + fy * 0.55f * sc, stroke);
        }
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    private void drawBuildings(Canvas c, World w, float now) {
        for (House h : w.houses) {
            if (!visible(h.x, h.y, 1)) continue;
            float sc = popScale(w.time - h.spawnTime);
            int col = pal.build[h.color];
            float cx = h.x + 0.5f, cy = h.y + 0.5f;
            float fx = World.DX[h.dir], fy = World.DY[h.dir];
            c.save();
            c.translate(cx - fx * 0.17f, cy - fy * 0.17f);
            c.scale(sc, sc);
            fill.setColor(pal.shadow);
            rf.set(-0.21f + 0.04f, -0.21f + 0.06f, 0.21f + 0.04f, 0.21f + 0.06f);
            c.drawRoundRect(rf, 0.07f, 0.07f, fill);
            fill.setColor(col);
            rf.set(-0.21f, -0.21f, 0.21f, 0.21f);
            c.drawRoundRect(rf, 0.07f, 0.07f, fill);
            // Pitched roof: the back half is a shade darker.
            fill.setColor(Palette.mix(col, 0xFF000000, 0.16f));
            path.reset();
            float bx = -fx, by = -fy, lx = -fy, ly = fx;
            path.moveTo(lx * 0.21f, ly * 0.21f);
            path.lineTo(lx * 0.21f + bx * 0.21f, ly * 0.21f + by * 0.21f);
            path.lineTo(-lx * 0.21f + bx * 0.21f, -ly * 0.21f + by * 0.21f);
            path.lineTo(-lx * 0.21f, -ly * 0.21f);
            path.close();
            c.save();
            c.clipRect(rf);
            c.drawPath(path, fill);
            c.restore();
            if (colorblind) drawSymbol(c, h.color, 0, 0, 0.11f, 0xEEFFFFFF);
            c.restore();
            // Cars waiting at home.
            for (int i = 0; i < 2; i++) {
                Car car = h.cars[i];
                if (car != null && car.state == Car.HOME) drawCar(c, car, col);
            }
        }
        for (Dest d : w.dests) {
            if (!visible(d.cx, d.cy, 3)) continue;
            int col = pal.build[d.color];
            float sc = popScale(w.time - d.spawnTime);
            c.save();
            c.translate(d.cx, d.cy);
            c.scale(sc, sc);
            // Building block at the back of the lot.
            float bcx = -d.fx * 0.5f, bcy = -d.fy * 0.5f;
            float hw = (Math.abs(d.rx) * 1.42f + Math.abs(d.fx) * 0.42f), hh = (Math.abs(d.ry) * 1.42f + Math.abs(d.fy) * 0.42f);
            fill.setColor(pal.shadow);
            rf.set(bcx - hw + 0.06f, bcy - hh + 0.09f, bcx + hw + 0.06f, bcy + hh + 0.09f);
            c.drawRoundRect(rf, 0.12f, 0.12f, fill);
            fill.setColor(col);
            rf.set(bcx - hw, bcy - hh, bcx + hw, bcy + hh);
            c.drawRoundRect(rf, 0.12f, 0.12f, fill);
            fill.setColor(Palette.mix(col, 0xFF000000, 0.14f));
            float iw = hw - 0.12f, ih = hh - 0.12f;
            rf.set(bcx - iw, bcy - ih, bcx + iw, bcy + ih);
            c.drawRoundRect(rf, 0.07f, 0.07f, fill);
            fill.setColor(Palette.mix(col, 0xFFFFFFFF, 0.18f));
            iw = hw - 0.22f; ih = hh - 0.22f;
            rf.set(bcx - iw, bcy - ih, bcx + iw, bcy + ih);
            c.drawRoundRect(rf, 0.05f, 0.05f, fill);
            if (colorblind) drawSymbol(c, d.color, bcx, bcy, 0.16f, 0xEEFFFFFF);
            c.restore();
        }
    }

    void drawSymbol(Canvas c, int color, float x, float y, float r, int argb) {
        fill.setColor(argb);
        switch (color) {
            case 0:
                c.drawCircle(x, y, r * 0.8f, fill);
                break;
            case 1:
                c.drawRect(x - r * 0.7f, y - r * 0.7f, x + r * 0.7f, y + r * 0.7f, fill);
                break;
            case 2:
                path.reset();
                path.moveTo(x, y - r);
                path.lineTo(x + r, y + r * 0.75f);
                path.lineTo(x - r, y + r * 0.75f);
                path.close();
                c.drawPath(path, fill);
                break;
            case 3:
                path.reset();
                path.moveTo(x, y - r);
                path.lineTo(x + r, y);
                path.lineTo(x, y + r);
                path.lineTo(x - r, y);
                path.close();
                c.drawPath(path, fill);
                break;
            case 4:
                c.drawRect(x - r, y - r * 0.3f, x + r, y + r * 0.3f, fill);
                c.drawRect(x - r * 0.3f, y - r, x + r * 0.3f, y + r, fill);
                break;
            default:
                path.reset();
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 5;
                    float rr = i % 2 == 0 ? r : r * 0.45f;
                    float px = x + (float) Math.cos(a) * rr, py = y + (float) Math.sin(a) * rr;
                    if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
                }
                path.close();
                c.drawPath(path, fill);
                break;
        }
    }

    // ------------------------------------------------------------------ cars

    private void drawCars(Canvas c, World w, boolean motorLayer) {
        for (int i = 0, n = w.cars.size(); i < n; i++) {
            Car car = w.cars.get(i);
            if (car.state == Car.HOME) continue;
            if (car.onMotor != motorLayer) continue;
            if (!visible(car.x, car.y, 1)) continue;
            drawCar(c, car, pal.build[car.color]);
        }
    }

    private void drawCar(Canvas c, Car car, int col) {
        c.save();
        c.translate(car.x, car.y);
        c.rotate((float) Math.toDegrees(car.ang));
        float L = 0.15f, Wd = 0.085f;
        if (car.onMotor) {
            fill.setColor(pal.shadow);
            rf.set(-L + 0.04f, -Wd + 0.06f, L + 0.04f, Wd + 0.06f);
            c.drawRoundRect(rf, 0.05f, 0.05f, fill);
        }
        fill.setColor(col);
        rf.set(-L, -Wd, L, Wd);
        c.drawRoundRect(rf, 0.05f, 0.05f, fill);
        fill.setColor(Palette.mix(col, 0xFFFFFFFF, 0.45f));
        rf.set(L * 0.15f, -Wd * 0.7f, L * 0.55f, Wd * 0.7f);
        c.drawRoundRect(rf, 0.02f, 0.02f, fill);
        c.restore();
    }

    // ------------------------------------------------------------------ demand pins

    private void drawPins(Canvas c, World w, float now) {
        for (Dest d : w.dests) {
            if (!visible(d.cx, d.cy, 3)) continue;
            int col = pal.build[d.color];
            float bcx = d.cx - d.fx * 0.5f, bcy = d.cy - d.fy * 0.5f;
            if (d.timer > 0) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(now * (4 + d.timer * 8));
                float R = 1.85f;
                stroke.setStrokeCap(Paint.Cap.BUTT);
                stroke.setColor(Palette.alpha(pal.warn, 0x30));
                stroke.setStrokeWidth(0.18f);
                c.drawCircle(d.cx, d.cy, R, stroke);
                stroke.setColor(Palette.alpha(pal.warn, (int) (170 + 85 * pulse)));
                rf.set(d.cx - R, d.cy - R, d.cx + R, d.cy + R);
                c.drawArc(rf, -90, 360 * d.timer, false, stroke);
                stroke.setStrokeCap(Paint.Cap.ROUND);
            }
            int n = d.pins;
            if (n <= 0) continue;
            // Pins sit in rows above the building, like markers on a map.
            int perRow = 6;
            float sp = 0.36f;
            for (int i = 0; i < n; i++) {
                int row = i / perRow, colIdx = i % perRow;
                int inRow = Math.min(perRow, n - row * perRow);
                float px = d.cx + (colIdx - (inRow - 1) / 2f) * sp;
                float py = d.y0 - 0.32f - row * 0.38f;
                fill.setColor(pal.shadow);
                c.drawCircle(px + 0.02f, py + 0.04f, 0.16f, fill);
                fill.setColor(0xFFFFFFFF);
                c.drawCircle(px, py, 0.16f, fill);
                fill.setColor(i >= World.OVER_PINS - 1 ? pal.warn : col);
                c.drawCircle(px, py, 0.115f, fill);
            }
        }
    }

    // ------------------------------------------------------------------ previews

    private void drawPreview(Canvas c, World w, GameView.Preview pv) {
        if (pv.kind == GameView.Preview.MOTORWAY && pv.a >= 0 && pv.b >= 0) {
            float ax = pv.a % World.W + 0.5f, ay = pv.a / World.W + 0.5f;
            float bx = pv.b % World.W + 0.5f, by = pv.b / World.W + 0.5f;
            stroke.setColor(pv.valid ? Palette.alpha(pal.motorway, 0xA0) : Palette.alpha(pal.warn, 0x90));
            stroke.setStrokeWidth(0.44f);
            c.drawLine(ax, ay, bx, by, stroke);
            fill.setColor(stroke.getColor());
            c.drawCircle(ax, ay, 0.3f, fill);
            c.drawCircle(bx, by, 0.3f, fill);
        } else if (pv.kind == GameView.Preview.CROSS && pv.a >= 0) {
            float ax = pv.a % World.W + 0.5f, ay = pv.a / World.W + 0.5f;
            stroke.setColor(pv.valid ? Palette.alpha(pal.road, 0x90) : Palette.alpha(pal.road, 0x40));
            stroke.setStrokeWidth(pv.valid ? ROAD_W : 0.12f);
            c.drawLine(ax, ay, pv.fx, pv.fy, stroke);
        } else if (pv.kind == GameView.Preview.ERASE) {
            fill.setColor(Palette.alpha(pal.warn, 0x55));
            c.drawCircle(pv.fx, pv.fy, 0.45f, fill);
        } else if (pv.kind == GameView.Preview.TILE && pv.a >= 0) {
            stroke.setColor(Palette.alpha(pal.text, 0x60));
            stroke.setStrokeWidth(0.05f);
            rf.set(pv.a % World.W + 0.06f, pv.a / World.W + 0.06f, pv.a % World.W + 0.94f, pv.a / World.W + 0.94f);
            c.drawRoundRect(rf, 0.18f, 0.18f, stroke);
        }
    }
}
