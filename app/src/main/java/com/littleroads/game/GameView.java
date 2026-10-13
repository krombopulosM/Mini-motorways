package com.littleroads.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

/** The single view hosting every screen. Runs the simulation from onDraw at display refresh rate. */
public final class GameView extends View {
    static final int SCR_MENU = 0, SCR_GAME = 1, SCR_SETTINGS = 2, SCR_HELP = 3;
    static final int T_ROAD = 0, T_ERASE = 1, T_ROUNDABOUT = 2, T_LIGHT = 3, T_MOTORWAY = 4;

    /** What the renderer should preview under the finger. */
    static final class Preview {
        static final int NONE = 0, MOTORWAY = 1, CROSS = 2, ERASE = 3, TILE = 4;
        int kind, a = -1, b = -1;
        float fx, fy;
        boolean valid;
    }

    private final SharedPreferences prefs;
    private final Sound sound;
    private final Hud hud;
    private final Renderer renderer = new Renderer();
    private final float dp;

    int screen = SCR_MENU;
    private int settingsReturn = SCR_MENU;
    World world;
    private MapDef map = MapDef.ALL[0];
    private int mode;

    int tool = T_ROAD;
    boolean paused, fast, menuOpen;
    String toast;
    float toastTime;
    private boolean bestSaved, newBest;
    private int bestAtStart;

    // Settings
    private boolean sfx = true, haptics = true, grid = true, colorblind, night;

    // Camera
    private float zoom = 1, panX, panY;
    private float camS = -1, camCx, camCy, camOx, camOy;

    // Input
    private int downBtn = -1;
    private boolean drawing, multi;
    private float downX, downY, lastWX, lastWY;
    private long strokeStart;
    private int strokeChange;
    private float pinchD0, pinchZoom0, pinchMx0, pinchMy0, pinchPanX0, pinchPanY0;
    private final Preview preview = new Preview();

    private long lastNanos;
    private float realTime;

    public GameView(Context ctx) {
        super(ctx);
        dp = ctx.getResources().getDisplayMetrics().density;
        prefs = ctx.getSharedPreferences("littleroads", Context.MODE_PRIVATE);
        sfx = prefs.getBoolean("sfx", true);
        haptics = prefs.getBoolean("haptics", true);
        grid = prefs.getBoolean("grid", true);
        colorblind = prefs.getBoolean("colorblind", false);
        night = prefs.getBoolean("night", false);
        mode = prefs.getInt("mode", 0);
        sound = new Sound(ctx);
        sound.enabled = sfx;
        hud = new Hud(dp);
        applyPalette();
        setFocusable(true);
        setHapticFeedbackEnabled(true);
    }

    private void applyPalette() {
        Palette p = Palette.of(world != null ? world.map : map, night);
        renderer.pal = p;
        hud.pal = p;
        renderer.showGrid = grid;
        renderer.colorblind = colorblind;
    }

    // ------------------------------------------------------------------ lifecycle

    void onPauseApp() {
        if (screen == SCR_GAME && world != null && !world.gameOver) menuOpen = true;
    }

    void onDestroyApp() {
        sound.release();
    }

    /** @return true if the back press was consumed. */
    boolean onBack() {
        switch (screen) {
            case SCR_GAME:
                if (world != null && world.upgradePending) return true;
                if (world != null && world.gameOver) { quitToMenu(); return true; }
                menuOpen = !menuOpen;
                return true;
            case SCR_SETTINGS:
            case SCR_HELP:
                screen = screen == SCR_SETTINGS ? settingsReturn : SCR_MENU;
                return true;
            default:
                return false;
        }
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets in) {
        float l = 0, t = 0, r = 0, b = 0;
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                Object dc = in.getClass().getMethod("getDisplayCutout").invoke(in);
                if (dc != null) {
                    l = (Integer) dc.getClass().getMethod("getSafeInsetLeft").invoke(dc);
                    t = (Integer) dc.getClass().getMethod("getSafeInsetTop").invoke(dc);
                    r = (Integer) dc.getClass().getMethod("getSafeInsetRight").invoke(dc);
                    b = (Integer) dc.getClass().getMethod("getSafeInsetBottom").invoke(dc);
                }
            } catch (Throwable ignored) {
            }
        }
        hud.insetL = l;
        hud.insetT = t;
        hud.insetR = r;
        hud.insetB = b;
        return super.onApplyWindowInsets(in);
    }

    // ------------------------------------------------------------------ game control

    private void startGame(MapDef m) {
        map = m;
        world = new World(m, mode, System.nanoTime());
        world.takeEvents();
        applyPalette();
        screen = SCR_GAME;
        tool = T_ROAD;
        paused = false;
        fast = false;
        menuOpen = false;
        bestSaved = false;
        newBest = false;
        bestAtStart = best(m.id, mode);
        zoom = 1;
        panX = panY = 0;
        camS = -1;
        toast = null;
    }

    private int best(int mapId, int mode) {
        return prefs.getInt("best_" + mapId + "_" + mode, 0);
    }

    private void saveBest() {
        if (world == null || bestSaved) return;
        bestSaved = true;
        int b = best(world.map.id, world.mode);
        if (world.score > b) {
            prefs.edit().putInt("best_" + world.map.id + "_" + world.mode, world.score).apply();
            newBest = world.score > 0;
        }
    }

    private void quitToMenu() {
        saveBest();
        world = null;
        screen = SCR_MENU;
        menuOpen = false;
        applyPalette();
    }

    void showToast(String s) {
        toast = s;
        toastTime = 2.2f;
    }

    String hint() {
        if (world == null || world.gameOver || world.upgradePending) return null;
        if (world.score == 0 && world.time < 70) {
            boolean anyRoad = false;
            for (int t = 0; t < World.N && !anyRoad; t++) anyRoad = world.road[t];
            if (!anyRoad) return "Drag from a house to the destination of the same colour";
            return world.time > 8 ? "Connect every house to its destination" : null;
        }
        if (tool == T_ROUNDABOUT) return "Tap a road to place or remove a roundabout";
        if (tool == T_LIGHT) return "Tap a junction to place or remove traffic lights";
        if (tool == T_MOTORWAY) return "Drag between two points to build a motorway";
        if (tool == T_ERASE) return "Drag over roads to remove them";
        return null;
    }

    private void buzz() {
        if (haptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // ------------------------------------------------------------------ frame

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : (now - lastNanos) / 1e9f;
        lastNanos = now;
        dt = Math.min(dt, 0.05f);
        realTime += dt;
        int W = getWidth(), H = getHeight();
        hud.beginFrame();

        switch (screen) {
            case SCR_MENU: {
                int[] bests = new int[MapDef.ALL.length];
                for (int i = 0; i < bests.length; i++) bests[i] = best(i, mode);
                hud.drawMenu(c, W, H, mode, bests, realTime);
                break;
            }
            case SCR_SETTINGS:
                hud.drawSettings(c, W, H, new boolean[]{sfx, haptics, grid, colorblind, night});
                break;
            case SCR_HELP:
                hud.drawHelp(c, W, H);
                break;
            default:
                stepGame(dt);
                drawGameScene(c, W, H, dt);
                if (world.gameOver) {
                    hud.drawGameOver(c, world, Math.max(bestAtStart, world.score), newBest, W, H);
                } else if (world.upgradePending) {
                    hud.drawUpgrade(c, world, W, H);
                } else if (menuOpen) {
                    hud.drawPauseMenu(c, W, H);
                }
                break;
        }
        postInvalidateOnAnimation();
    }

    private void stepGame(float dt) {
        if (toastTime > 0) toastTime -= dt;
        if (!paused && !menuOpen && !world.gameOver && !world.upgradePending) {
            float sim = dt * (fast ? 2.5f : 1f);
            int n = Math.max(1, (int) Math.ceil(sim / (1 / 60f)));
            for (int i = 0; i < n; i++) world.update(sim / n);
        }
        handleEvents(world.takeEvents());
    }

    private void handleEvents(int ev) {
        if (ev == 0) return;
        if ((ev & World.EV_GAMEOVER) != 0) {
            sound.play(Sound.OVER);
            saveBest();
            drawing = false;
            Dest f = world.failed;
            if (f != null) {
                panX = f.cx - (world.rx0 + world.rx1) / 2;
                panY = f.cy - (world.ry0 + world.ry1) / 2;
                zoom = 1.5f;
            }
        }
        if ((ev & World.EV_WEEK) != 0) { sound.play(Sound.WEEK); drawing = false; }
        if ((ev & World.EV_DEST) != 0 && world.time > 1) { sound.play(Sound.DEST); showToast("A new destination appeared"); }
        if ((ev & World.EV_HOUSE) != 0) sound.play(Sound.POP, 1f, 0.6f);
        if ((ev & World.EV_DELIVER) != 0) sound.deliver(world.lastDeliverColor);
        if ((ev & World.EV_WARN) != 0) { sound.play(Sound.WARN); if (haptics) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); }
        if ((ev & World.EV_NO_ROADS) != 0) { showToast("Out of road tiles - erase some or wait for next week"); sound.play(Sound.DENY); }
        if ((ev & World.EV_NO_ITEM) != 0) { showToast("None left - more arrive with weekly upgrades"); sound.play(Sound.DENY); }
        if ((ev & World.EV_PLACE) != 0) { sound.play(Sound.PLACE); buzz(); }
        if ((ev & World.EV_ERASE) != 0) { sound.play(Sound.ERASE); buzz(); }
    }

    private void drawGameScene(Canvas c, int W, int H, float dt) {
        updateCamera(W, H, dt);
        renderer.draw(c, world, camS, camOx, camOy, W, H, realTime, drawing || preview.kind == Preview.TILE ? preview : null);
        if (screen == SCR_GAME) hud.drawGame(c, this, world, W, H, realTime);
    }

    private void updateCamera(int W, int H, float dt) {
        float top = hud.insetT + 66 * dp, bot = hud.insetB + 84 * dp, side = 16 * dp;
        float availW = W - hud.insetL - hud.insetR - 2 * side, availH = H - top - bot;
        float rw = world.rx1 - world.rx0, rh = world.ry1 - world.ry0;
        float fit = Math.min(availW / (rw + 0.6f), availH / (rh + 0.6f));
        float maxZoom = Math.max(1f, 64 * dp / fit);
        zoom = Math.max(1f, Math.min(zoom, maxZoom));
        float limX = rw / 2 * (1 - 1 / zoom) + 1, limY = rh / 2 * (1 - 1 / zoom) + 1;
        panX = Math.max(-limX, Math.min(limX, panX));
        panY = Math.max(-limY, Math.min(limY, panY));
        float ts = fit * zoom;
        float tcx = (world.rx0 + world.rx1) / 2 + panX, tcy = (world.ry0 + world.ry1) / 2 + panY;
        if (camS < 0 || multi) {
            camS = ts;
            camCx = tcx;
            camCy = tcy;
        } else {
            float k = 1 - (float) Math.exp(-dt * 6);
            camS += (ts - camS) * k;
            camCx += (tcx - camCx) * k;
            camCy += (tcy - camCy) * k;
        }
        float scx = hud.insetL + side + availW / 2, scy = top + availH / 2;
        camOx = scx - camCx * camS;
        camOy = scy - camCy * camS;
    }

    private float toWX(float sx) { return (sx - camOx) / camS; }
    private float toWY(float sy) { return (sy - camOy) / camS; }

    private int tileAt(float wx, float wy) {
        int x = (int) Math.floor(wx), y = (int) Math.floor(wy);
        if (x < 0 || y < 0 || x >= World.W || y >= World.H) return -1;
        return World.idx(x, y);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (camS <= 0 && screen == SCR_GAME) return true;
        int action = e.getActionMasked();
        float x = e.getX(), y = e.getY();
        if (screen != SCR_GAME || world.gameOver || world.upgradePending || menuOpen) {
            buttonsOnly(action, x, y);
            return true;
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                multi = false;
                downX = x;
                downY = y;
                downBtn = hud.hit(x, y);
                if (downBtn >= 0) return true;
                beginTool(toWX(x), toWY(y));
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (downBtn >= 0) return true;
                if (drawing) cancelStroke();
                if (e.getPointerCount() >= 2) {
                    multi = true;
                    pinchD0 = spacing(e);
                    pinchZoom0 = zoom;
                    pinchMx0 = (e.getX(0) + e.getX(1)) / 2;
                    pinchMy0 = (e.getY(0) + e.getY(1)) / 2;
                    pinchPanX0 = panX;
                    pinchPanY0 = panY;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (downBtn >= 0) return true;
                if (multi) {
                    if (e.getPointerCount() >= 2) {
                        float d = spacing(e);
                        if (pinchD0 > 10) zoom = pinchZoom0 * d / pinchD0;
                        float mx = (e.getX(0) + e.getX(1)) / 2, my = (e.getY(0) + e.getY(1)) / 2;
                        panX = pinchPanX0 - (mx - pinchMx0) / camS;
                        panY = pinchPanY0 - (my - pinchMy0) / camS;
                    }
                    return true;
                }
                if (drawing) {
                    for (int h = 0; h < e.getHistorySize(); h++) moveTool(toWX(e.getHistoricalX(h)), toWY(e.getHistoricalY(h)));
                    moveTool(toWX(x), toWY(y));
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (downBtn >= 0) {
                    int b = hud.hit(x, y);
                    if (b == downBtn) onButton(b);
                    downBtn = -1;
                    return true;
                }
                if (!multi) endTool(toWX(x), toWY(y), Math.hypot(x - downX, y - downY) < 12 * dp);
                drawing = false;
                multi = false;
                preview.kind = Preview.NONE;
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                if (drawing) cancelStroke();
                drawing = false;
                multi = false;
                downBtn = -1;
                preview.kind = Preview.NONE;
                return true;
            }
            default:
                return true;
        }
    }

    private static float spacing(MotionEvent e) {
        float dx = e.getX(0) - e.getX(1), dy = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void buttonsOnly(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) {
            downBtn = hud.hit(x, y);
        } else if (action == MotionEvent.ACTION_UP) {
            int b = hud.hit(x, y);
            if (b >= 0 && b == downBtn) onButton(b);
            downBtn = -1;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            downBtn = -1;
        }
    }

    private void beginTool(float wx, float wy) {
        lastWX = wx;
        lastWY = wy;
        strokeStart = System.currentTimeMillis();
        drawing = true;
        switch (tool) {
            case T_ROAD:
                world.beginAction();
                strokeChange = world.changeCounter;
                world.strokeBegin(wx, wy);
                updateRoadPreview(wx, wy);
                break;
            case T_ERASE:
                world.beginAction();
                strokeChange = world.changeCounter;
                world.eraseLine(wx, wy, wx, wy);
                preview.kind = Preview.ERASE;
                preview.fx = wx;
                preview.fy = wy;
                break;
            case T_MOTORWAY:
                preview.kind = Preview.MOTORWAY;
                preview.a = tileAt(wx, wy);
                preview.b = preview.a;
                preview.valid = false;
                break;
            default:
                preview.kind = Preview.TILE;
                preview.a = tileAt(wx, wy);
                break;
        }
    }

    private void moveTool(float wx, float wy) {
        switch (tool) {
            case T_ROAD:
                world.strokeMove(wx, wy);
                updateRoadPreview(wx, wy);
                break;
            case T_ERASE:
                world.eraseLine(lastWX, lastWY, wx, wy);
                preview.fx = wx;
                preview.fy = wy;
                break;
            case T_MOTORWAY:
                preview.b = tileAt(wx, wy);
                preview.valid = world.motorwayValid(preview.a, preview.b);
                break;
            default:
                preview.a = tileAt(wx, wy);
                break;
        }
        lastWX = wx;
        lastWY = wy;
    }

    private void updateRoadPreview(float wx, float wy) {
        int anchor = world.strokeAnchor();
        int t = tileAt(wx, wy);
        if (anchor >= 0 && t >= 0 && t != anchor && !Car.adjacent(anchor, t) && world.terrain[t] != MapDef.LAND) {
            preview.kind = Preview.CROSS;
            preview.a = anchor;
            preview.fx = wx;
            preview.fy = wy;
            preview.valid = false;
        } else if (anchor >= 0 && t >= 0 && !Car.adjacent(anchor, t) && world.crossingLength(anchor, t) > 0) {
            preview.kind = Preview.CROSS;
            preview.a = anchor;
            preview.fx = t % World.W + 0.5f;
            preview.fy = t / World.W + 0.5f;
            preview.valid = true;
        } else {
            preview.kind = Preview.NONE;
        }
        // Bridge hint while hovering water next to the shore.
        if (anchor >= 0 && t >= 0 && world.terrain[t] != MapDef.LAND && world.crossAt[t] == null) {
            preview.kind = Preview.CROSS;
            preview.a = anchor;
            preview.fx = wx;
            preview.fy = wy;
            preview.valid = false;
        }
    }

    private void endTool(float wx, float wy, boolean tap) {
        switch (tool) {
            case T_ROAD:
                world.strokeEnd();
                world.endAction();
                break;
            case T_ERASE:
                world.endAction();
                break;
            case T_MOTORWAY: {
                int b = tileAt(wx, wy);
                if (preview.a >= 0 && b >= 0 && preview.a != b) {
                    world.beginAction();
                    world.buildMotorway(preview.a, b);
                    world.endAction();
                }
                break;
            }
            default: {
                if (!tap) break;
                int t = tileAt(wx, wy);
                if (t < 0) break;
                world.beginAction();
                world.toggleSpecial(t, tool == T_ROUNDABOUT ? World.SP_ROUNDABOUT : World.SP_LIGHT);
                world.endAction();
                break;
            }
        }
    }

    /** A second finger landed: treat the first touch as the start of a pinch, not a stroke. */
    private void cancelStroke() {
        if (tool == T_ROAD || tool == T_ERASE) {
            world.strokeEnd();
            boolean changed = world.changeCounter != strokeChange;
            world.endAction();
            if (changed && System.currentTimeMillis() - strokeStart < 400) world.undo();
        }
        drawing = false;
        preview.kind = Preview.NONE;
    }

    private void selectTool(int t, int inventoryItem) {
        if (inventoryItem >= 0 && world.inv[inventoryItem] <= 0 && tool != t) {
            showToast("None left - more arrive with weekly upgrades");
            sound.play(Sound.DENY);
            return;
        }
        tool = (tool == t && t != T_ROAD) ? T_ROAD : t;
        sound.play(Sound.CLICK);
    }

    private void onButton(int b) {
        switch (b) {
            case Hud.B_UNDO:
                if (world.canUndo()) { world.undo(); sound.play(Sound.ERASE); } else sound.play(Sound.DENY);
                break;
            case Hud.B_ROAD: selectTool(T_ROAD, -1); break;
            case Hud.B_ERASE: selectTool(T_ERASE, -1); break;
            case Hud.B_ROUNDABOUT: selectTool(T_ROUNDABOUT, World.UP_ROUNDABOUT); break;
            case Hud.B_LIGHT: selectTool(T_LIGHT, World.UP_LIGHTS); break;
            case Hud.B_MOTORWAY: selectTool(T_MOTORWAY, World.UP_MOTORWAY); break;
            case Hud.B_BRIDGE:
                showToast("Drag a road straight across water to build a bridge");
                break;
            case Hud.B_TUNNEL:
                showToast("Drag a road straight through a mountain to dig a tunnel");
                break;
            case Hud.B_PAUSE: paused = true; sound.play(Sound.CLICK); break;
            case Hud.B_PLAY: paused = false; fast = false; sound.play(Sound.CLICK); break;
            case Hud.B_FAST: paused = false; fast = true; sound.play(Sound.CLICK); break;
            case Hud.B_MENU: menuOpen = true; sound.play(Sound.CLICK); break;
            case Hud.B_RESUME: menuOpen = false; sound.play(Sound.CLICK); break;
            case Hud.B_RESTART: saveBest(); startGame(world.map); sound.play(Sound.CLICK); break;
            case Hud.B_QUIT: quitToMenu(); sound.play(Sound.CLICK); break;
            case Hud.B_SETTINGS: settingsReturn = SCR_GAME; screen = SCR_SETTINGS; sound.play(Sound.CLICK); break;
            case Hud.B_UP0:
            case Hud.B_UP1: {
                world.chooseUpgrade(b - Hud.B_UP0);
                sound.play(Sound.DEST);
                break;
            }
            case Hud.B_GO_RETRY: startGame(world.map); sound.play(Sound.CLICK); break;
            case Hud.B_GO_MENU: quitToMenu(); sound.play(Sound.CLICK); break;
            case Hud.B_OPEN_SETTINGS: settingsReturn = SCR_MENU; screen = SCR_SETTINGS; sound.play(Sound.CLICK); break;
            case Hud.B_OPEN_HELP: screen = SCR_HELP; sound.play(Sound.CLICK); break;
            case Hud.B_BACK: onBack(); sound.play(Sound.CLICK); break;
            case Hud.B_SET_SOUND: sfx = !sfx; sound.enabled = sfx; savePrefs(); sound.play(Sound.CLICK); break;
            case Hud.B_SET_HAPTIC: haptics = !haptics; savePrefs(); buzz(); break;
            case Hud.B_SET_GRID: grid = !grid; savePrefs(); break;
            case Hud.B_SET_COLORBLIND: colorblind = !colorblind; savePrefs(); break;
            case Hud.B_SET_NIGHT: night = !night; savePrefs(); break;
            case Hud.B_SET_RESET: {
                SharedPreferences.Editor ed = prefs.edit();
                for (int i = 0; i < MapDef.ALL.length; i++) for (int m = 0; m < 3; m++) ed.remove("best_" + i + "_" + m);
                ed.apply();
                showToast("Best scores cleared");
                sound.play(Sound.ERASE);
                break;
            }
            default:
                if (b >= Hud.B_MAP0 && b < Hud.B_MAP0 + MapDef.ALL.length) {
                    sound.play(Sound.CLICK);
                    startGame(MapDef.ALL[b - Hud.B_MAP0]);
                } else if (b >= Hud.B_MODE0 && b < Hud.B_MODE0 + 3) {
                    mode = b - Hud.B_MODE0;
                    prefs.edit().putInt("mode", mode).apply();
                    sound.play(Sound.CLICK);
                }
                break;
        }
    }

    private void savePrefs() {
        prefs.edit().putBoolean("sfx", sfx).putBoolean("haptics", haptics).putBoolean("grid", grid)
                .putBoolean("colorblind", colorblind).putBoolean("night", night).apply();
        applyPalette();
    }
}
