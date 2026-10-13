package com.littleroads.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * The whole game simulation. Pure Java (no Android dependencies) so it can be
 * exercised headlessly.
 *
 * Grid: W x H tiles. Roads live on tiles and connect to any of the 8 neighbours.
 * Houses spawn near destinations of the same colour; destinations produce demand
 * ("pins") that cars from matching houses satisfy by driving there and back.
 * If a destination keeps too many pins for too long, the game is over.
 */
public final class World {
    public static final int W = MapDef.W, H = MapDef.H, N = W * H;
    public static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1};
    public static final int[] DY = {0, 1, 1, 1, 0, -1, -1, -1};

    public static final int MODE_NORMAL = 0, MODE_EXPERT = 1, MODE_ENDLESS = 2;
    public static final int SP_NONE = 0, SP_ROUNDABOUT = 1, SP_LIGHT = 2;

    public static final int UP_ROADS = 0, UP_BRIDGE = 1, UP_TUNNEL = 2, UP_MOTORWAY = 3, UP_ROUNDABOUT = 4, UP_LIGHTS = 5;
    public static final int[] UP_AMOUNT = {20, 1, 1, 1, 2, 2};
    public static final int WEEKLY_ROADS = 20;

    public static final float DAY = 20f;
    public static final int SLOTS = 4;
    public static final int MAX_COLORS = 6;
    public static final int OVER_PINS = 6;
    public static final int MAX_PINS = 12;
    static final float OVER_TIME = 55f;
    static final float CAR_SPEED = 1.6f, MOTOR_SPEED = 4.2f;
    static final float PARK_TIME = 1.0f;
    static final int MAX_CROSS = 8;
    static final float BASE_DEMAND = 0.09f;    // pins per second per destination in week 0

    public static final int EV_HOUSE = 1, EV_DEST = 2, EV_DELIVER = 4, EV_WARN = 8, EV_GAMEOVER = 16,
            EV_WEEK = 32, EV_NO_ROADS = 64, EV_PLACE = 128, EV_ERASE = 256, EV_NO_ITEM = 512;

    public final MapDef map;
    public final int mode;
    public final byte[] terrain;
    public final boolean hasWater, hasMountains;

    public final boolean[] road = new boolean[N];
    public final byte[] conn = new byte[N];
    public final byte[] special = new byte[N];
    public Crossing[] crossAt = new Crossing[N];
    public int[] motorEnds = new int[N];
    public final House[] houseAt = new House[N];
    public final Dest[] destAt = new Dest[N];
    public final int[] attach = new int[N];

    public final ArrayList<House> houses = new ArrayList<>();
    public final ArrayList<Dest> dests = new ArrayList<>();
    public final ArrayList<Car> cars = new ArrayList<>();
    public ArrayList<Crossing> crossings = new ArrayList<>();
    public ArrayList<Motorway> motorways = new ArrayList<>();

    public final int[] inv = new int[6];
    public float time;
    public int score;
    public int week;
    public boolean gameOver;
    public Dest failed;
    public boolean upgradePending;
    public final int[] offers = new int[2];
    public float rx0, ry0, rx1, ry1;
    public int roadVersion;
    public int events;
    public int colorsUsed;
    public int lastDeliverColor;

    // Traffic control.
    final int[] occ = new int[N];
    final byte[] occEntry = new byte[N];
    public final byte[] lightPhase = new byte[N];
    final float[] lightTimer = new float[N];
    final byte[] lightDemand = new byte[N];
    final byte[] lightLast = new byte[N];

    final Random rng;
    float houseTimer, nextDestTime, repathTimer = -1, dispatchTimer;
    int changeCounter;

    public World(MapDef map, int mode, long seed) {
        this.map = map;
        this.mode = mode;
        this.rng = new Random(seed);
        this.terrain = map.terrain();
        this.hasWater = map.hasWater();
        this.hasMountains = map.hasMountains();
        float rw = 14, rh = rw * H / W;
        rx0 = W / 2f - rw / 2; rx1 = W / 2f + rw / 2;
        ry0 = H / 2f - rh / 2; ry1 = H / 2f + rh / 2;
        inv[UP_ROADS] = 30;
        inv[UP_BRIDGE] = hasWater ? (map.id == 3 ? 3 : 2) : 0;
        inv[UP_TUNNEL] = hasMountains ? 2 : 0;
        spawnDest(true);
        houseTimer = 0.6f;
        nextDestTime = DAY * 2.2f;
    }

    // ================================================================= helpers

    public static int idx(int x, int y) { return y * W + x; }

    public static int dirBetween(int a, int b) {
        int dx = b % W - a % W, dy = b / W - a / W;
        for (int d = 0; d < 8; d++) if (DX[d] == dx && DY[d] == dy) return d;
        return -1;
    }

    public boolean inMap(int x, int y) { return x >= 0 && y >= 0 && x < W && y < H; }

    public boolean inRegion(int x, int y) {
        if (!inMap(x, y)) return false;
        float cx = x + 0.5f, cy = y + 0.5f;
        return cx > rx0 && cx < rx1 && cy > ry0 && cy < ry1;
    }

    public boolean inRegion(int t) { return inRegion(t % W, t / W); }

    boolean isBuilding(int t) { return houseAt[t] != null || destAt[t] != null; }

    /** Can a new road tile be placed here? */
    public boolean canBuildRoad(int t) {
        return !road[t] && inRegion(t) && terrain[t] == MapDef.LAND && !isBuilding(t) && crossAt[t] == null;
    }

    /** A road tile that ordinary roads may connect to (bridge/tunnel interiors excluded). */
    boolean isOpenRoad(int t) { return road[t] && crossAt[t] == null; }

    public int degree(int t) {
        return Integer.bitCount(conn[t] & 0xFF) + motorEnds[t] + attach[t];
    }

    public boolean isIntersection(int t) {
        return special[t] == SP_ROUNDABOUT || degree(t) >= 3;
    }

    public boolean infinite() { return false; }

    void roadsChanged() {
        roadVersion++;
        changeCounter++;
        if (repathTimer < 0) repathTimer = 0.25f;
    }

    // ================================================================= road editing

    private void placeRoad(int t) {
        road[t] = true;
        inv[UP_ROADS]--;
        events |= EV_PLACE;
        roadsChanged();
    }

    /** Place a road tile (if needed) to start a stroke. */
    public boolean ensureRoad(int t) {
        if (isOpenRoad(t)) return true;
        if (!canBuildRoad(t)) return false;
        if (inv[UP_ROADS] <= 0) { events |= EV_NO_ROADS; return false; }
        placeRoad(t);
        return true;
    }

    private boolean crossesDiagonal(int a, int dir) {
        if ((dir & 1) == 0) return false;
        int ax = a % W, ay = a / W;
        int ox = ax + DX[dir], oy = ay;           // the tile beside us horizontally
        int qx = ax, qy = ay + DY[dir];           // the tile beside us vertically
        if (!inMap(ox, oy) || !inMap(qx, qy)) return false;
        int o = idx(ox, oy), q = idx(qx, qy);
        int d = dirBetween(o, q);
        return d >= 0 && (conn[o] & (1 << d)) != 0;
    }

    /** Connect a (existing road) to its neighbour b, placing a road on b if needed. */
    public boolean connect(int a, int b) {
        int dir = dirBetween(a, b);
        if (dir < 0 || !isOpenRoad(a)) return false;
        if (crossAt[b] != null) return false;
        if ((conn[a] & (1 << dir)) != 0) return true;
        if (crossesDiagonal(a, dir)) return false;
        if (!road[b]) {
            if (!canBuildRoad(b)) return false;
            if (inv[UP_ROADS] <= 0) { events |= EV_NO_ROADS; return false; }
            placeRoad(b);
        }
        conn[a] |= 1 << dir;
        conn[b] |= 1 << ((dir + 4) & 7);
        roadsChanged();
        return true;
    }

    /** Can a straight bridge/tunnel go from a to b? Returns the number of interior tiles, or -1. */
    public int crossingLength(int a, int b) {
        if (!isOpenRoad(a)) return -1;
        int ax = a % W, ay = a / W, bx = b % W, by = b / W;
        int dx = bx - ax, dy = by - ay;
        if (!(dx == 0 || dy == 0 || Math.abs(dx) == Math.abs(dy))) return -1;
        int k = Math.max(Math.abs(dx), Math.abs(dy));
        if (k < 2 || k - 1 > MAX_CROSS) return -1;
        int sx = Integer.signum(dx), sy = Integer.signum(dy);
        byte type = terrain[idx(ax + sx, ay + sy)];
        if (type == MapDef.LAND) return -1;
        for (int i = 1; i < k; i++) {
            int t = idx(ax + sx * i, ay + sy * i);
            if (terrain[t] != type || crossAt[t] != null || road[t] || !inRegion(t)) return -1;
            if (crossesDiagonal(idx(ax + sx * (i - 1), ay + sy * (i - 1)), dirBetween(idx(ax + sx * (i - 1), ay + sy * (i - 1)), t)))
                return -1;
        }
        if (!(isOpenRoad(b) || canBuildRoad(b))) return -1;
        return k - 1;
    }

    public boolean crossingIsTunnel(int a, int b) {
        int ax = a % W, ay = a / W;
        int sx = Integer.signum(b % W - ax), sy = Integer.signum(b / W - ay);
        return terrain[idx(ax + sx, ay + sy)] == MapDef.MOUNTAIN;
    }

    public boolean tryCross(int a, int b) {
        int len = crossingLength(a, b);
        if (len < 0) return false;
        boolean tunnel = crossingIsTunnel(a, b);
        int item = tunnel ? UP_TUNNEL : UP_BRIDGE;
        if (inv[item] <= 0) { events |= EV_NO_ITEM; return false; }
        if (!road[b] && inv[UP_ROADS] <= 0) { events |= EV_NO_ROADS; return false; }
        if (!road[b]) placeRoad(b);
        int ax = a % W, ay = a / W;
        int sx = Integer.signum(b % W - ax), sy = Integer.signum(b / W - ay);
        int dir = dirBetween(a, idx(ax + sx, ay + sy));
        int[] tiles = new int[len];
        Crossing c = new Crossing(tunnel, a, b, dir, tiles);
        int prev = a;
        for (int i = 1; i <= len + 1; i++) {
            int t = idx(ax + sx * i, ay + sy * i);
            if (i <= len) {
                tiles[i - 1] = t;
                road[t] = true;
                crossAt[t] = c;
            }
            conn[prev] |= 1 << dir;
            conn[t] |= 1 << ((dir + 4) & 7);
            prev = t;
        }
        crossings.add(c);
        inv[item]--;
        events |= EV_PLACE;
        roadsChanged();
        return true;
    }

    private void removeCrossing(Crossing c) {
        int opp = (c.dir + 4) & 7;
        for (int t : c.interior) {
            road[t] = false;
            conn[t] = 0;
            crossAt[t] = null;
            special[t] = SP_NONE;
        }
        conn[c.a] &= ~(1 << c.dir);
        conn[c.b] &= ~(1 << opp);
        crossings.remove(c);
        inv[c.tunnel ? UP_TUNNEL : UP_BRIDGE]++;
        roadsChanged();
    }

    private void removeMotorway(Motorway m) {
        motorways.remove(m);
        motorEnds[m.a]--;
        motorEnds[m.b]--;
        inv[UP_MOTORWAY]++;
        roadsChanged();
    }

    /** Erase whatever road infrastructure is on tile t. */
    public boolean erase(int t) {
        if (crossAt[t] != null) {
            removeCrossing(crossAt[t]);
            events |= EV_ERASE;
            return true;
        }
        if (!road[t]) return false;
        for (int i = crossings.size() - 1; i >= 0; i--) {
            Crossing c = crossings.get(i);
            if (c.a == t || c.b == t) removeCrossing(c);
        }
        for (int i = motorways.size() - 1; i >= 0; i--) {
            Motorway m = motorways.get(i);
            if (m.a == t || m.b == t) removeMotorway(m);
        }
        if (special[t] == SP_ROUNDABOUT) inv[UP_ROUNDABOUT]++;
        if (special[t] == SP_LIGHT) inv[UP_LIGHTS]++;
        special[t] = SP_NONE;
        for (int d = 0; d < 8; d++) {
            if ((conn[t] & (1 << d)) == 0) continue;
            int n = t + DX[d] + DY[d] * W;
            conn[n] &= ~(1 << ((d + 4) & 7));
        }
        conn[t] = 0;
        road[t] = false;
        inv[UP_ROADS]++;
        events |= EV_ERASE;
        roadsChanged();
        return true;
    }

    /** Toggle a roundabout or traffic light on a tile. */
    public boolean toggleSpecial(int t, int kind) {
        int item = kind == SP_ROUNDABOUT ? UP_ROUNDABOUT : UP_LIGHTS;
        if (special[t] == kind) {
            special[t] = SP_NONE;
            inv[item]++;
            events |= EV_ERASE;
            roadsChanged();
            return true;
        }
        if (crossAt[t] != null) return false;
        if (inv[item] <= 0) { events |= EV_NO_ITEM; return false; }
        if (!road[t]) {
            if (kind == SP_LIGHT || !ensureRoad(t)) return false;
        }
        if (special[t] != SP_NONE) inv[special[t] == SP_ROUNDABOUT ? UP_ROUNDABOUT : UP_LIGHTS]++;
        special[t] = (byte) kind;
        inv[item]--;
        lightTimer[t] = 0;
        events |= EV_PLACE;
        roadsChanged();
        return true;
    }

    public boolean motorwayValid(int a, int b) {
        if (a == b || a < 0 || b < 0) return false;
        int dx = Math.abs(a % W - b % W), dy = Math.abs(a / W - b / W);
        if (Math.max(dx, dy) < 2 || dx * dx + dy * dy > 26 * 26) return false;
        if (!(isOpenRoad(a) || canBuildRoad(a)) || !(isOpenRoad(b) || canBuildRoad(b))) return false;
        for (Motorway m : motorways) if ((m.a == a && m.b == b) || (m.a == b && m.b == a)) return false;
        int need = (road[a] ? 0 : 1) + (road[b] ? 0 : 1);
        return inv[UP_MOTORWAY] > 0 && inv[UP_ROADS] >= need;
    }

    public boolean buildMotorway(int a, int b) {
        if (!motorwayValid(a, b)) {
            if (inv[UP_MOTORWAY] <= 0) events |= EV_NO_ITEM;
            return false;
        }
        if (!road[a]) placeRoad(a);
        if (!road[b]) placeRoad(b);
        motorways.add(new Motorway(a, b));
        motorEnds[a]++;
        motorEnds[b]++;
        inv[UP_MOTORWAY]--;
        events |= EV_PLACE;
        roadsChanged();
        return true;
    }

    // ----------------------------------------------------------------- road strokes

    int strokeLast = -1;

    public void strokeBegin(float wx, float wy) {
        strokeLast = -1;
        strokeMove(wx, wy);
    }

    public void strokeMove(float wx, float wy) {
        int tx = (int) Math.floor(wx), ty = (int) Math.floor(wy);
        if (!inMap(tx, ty)) return;
        int target = idx(tx, ty);
        if (strokeLast < 0) {
            if (ensureRoad(target)) strokeLast = target;
            return;
        }
        if (target == strokeLast) return;
        // Ignore the corner zones of a tile so diagonal strokes register as diagonals.
        float ox = wx - (tx + 0.5f), oy = wy - (ty + 0.5f);
        if (Math.abs(ox) > 0.36f && Math.abs(oy) > 0.36f && Car.adjacent(target, strokeLast)) return;
        int cur = strokeLast;
        for (int steps = 0; steps < 40 && cur != target; steps++) {
            int cx = cur % W, cy = cur / W;
            int dx = tx - cx, dy = ty - cy;
            int sx = Integer.signum(dx), sy = Integer.signum(dy);
            if (Math.abs(dx) > 2 * Math.abs(dy)) sy = 0;
            else if (Math.abs(dy) > 2 * Math.abs(dx)) sx = 0;
            int next = idx(cx + sx, cy + sy);
            Crossing cr = crossAt[next];
            if (cr != null) {
                // Follow an existing bridge/tunnel to its far end.
                if ((cr.a == cur && cr.interior[0] == next) || (cr.b == cur && cr.interior[cr.interior.length - 1] == next)) {
                    cur = cr.a == cur ? cr.b : cr.a;
                    strokeLast = cur;
                    continue;
                }
                return;
            }
            if (terrain[next] != MapDef.LAND) {
                if (terrain[target] == MapDef.LAND && tryCross(cur, target)) strokeLast = target;
                return;
            }
            if (!connect(cur, next)) return;
            cur = next;
            strokeLast = cur;
        }
    }

    public void strokeEnd() { strokeLast = -1; }

    /** For the renderer: the tile a pending bridge/tunnel would start from. */
    public int strokeAnchor() { return strokeLast; }

    public void eraseLine(float x0, float y0, float x1, float y1) {
        float dist = (float) Math.hypot(x1 - x0, y1 - y0);
        int n = Math.max(1, (int) (dist / 0.2f));
        int lastT = -1;
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            int tx = (int) Math.floor(x0 + (x1 - x0) * u), ty = (int) Math.floor(y0 + (y1 - y0) * u);
            if (!inMap(tx, ty)) continue;
            int t = idx(tx, ty);
            if (t != lastT) erase(t);
            lastT = t;
        }
    }

    // ----------------------------------------------------------------- undo

    static final class Snap {
        boolean[] road; byte[] conn, special; Crossing[] crossAt; int[] motorEnds;
        ArrayList<Crossing> crossings; ArrayList<Motorway> motorways; int[] inv; int counter;
    }

    final ArrayList<Snap> undoStack = new ArrayList<>();

    public void beginAction() {
        Snap s = new Snap();
        s.road = road.clone(); s.conn = conn.clone(); s.special = special.clone();
        s.crossAt = crossAt.clone(); s.motorEnds = motorEnds.clone();
        s.crossings = new ArrayList<>(crossings); s.motorways = new ArrayList<>(motorways);
        s.inv = inv.clone(); s.counter = changeCounter;
        undoStack.add(s);
        if (undoStack.size() > 25) undoStack.remove(0);
    }

    public void endAction() {
        if (undoStack.isEmpty()) return;
        Snap s = undoStack.get(undoStack.size() - 1);
        if (s.counter == changeCounter) undoStack.remove(undoStack.size() - 1);
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }

    public void undo() {
        if (undoStack.isEmpty()) return;
        Snap s = undoStack.remove(undoStack.size() - 1);
        System.arraycopy(s.road, 0, road, 0, N);
        System.arraycopy(s.conn, 0, conn, 0, N);
        System.arraycopy(s.special, 0, special, 0, N);
        crossAt = s.crossAt;
        motorEnds = s.motorEnds;
        crossings = s.crossings;
        motorways = s.motorways;
        System.arraycopy(s.inv, 0, inv, 0, inv.length);
        // A building may have appeared on a tile whose road was erased after the snapshot.
        for (int t = 0; t < N; t++) if (road[t] && isBuilding(t)) erase(t);
        roadsChanged();
    }

    // ================================================================= simulation

    public void update(float dt) {
        if (gameOver || upgradePending) return;
        time += dt;
        int w = (int) (time / (DAY * 7));
        if (w > week) {
            week = w;
            inv[UP_ROADS] += WEEKLY_ROADS;
            makeOffers();
            upgradePending = true;
            undoStack.clear();
            events |= EV_WEEK;
        }
        growRegion();
        spawnLogic(dt);
        demand(dt);
        if (repathTimer >= 0) {
            repathTimer -= dt;
            if (repathTimer < 0) repathAll();
        }
        dispatchTimer -= dt;
        if (dispatchTimer <= 0) {
            dispatchTimer = 0.15f;
            dispatch();
        }
        updateLights(dt);
        for (int i = 0, n = cars.size(); i < n; i++) updateCar(cars.get(i), dt);
    }

    public float day() { return time / DAY; }

    private void growRegion() {
        float rw = Math.min(W, 14 + 0.75f * day());
        float rh = rw * H / W;
        rx0 = W / 2f - rw / 2; rx1 = W / 2f + rw / 2;
        ry0 = H / 2f - rh / 2; ry1 = H / 2f + rh / 2;
    }

    // ----------------------------------------------------------------- upgrades

    private void makeOffers() {
        int[] pool = new int[12];
        int n = 0;
        pool[n++] = UP_ROADS;
        pool[n++] = UP_MOTORWAY;
        pool[n++] = UP_ROUNDABOUT;
        pool[n++] = UP_LIGHTS;
        if (hasWater) { pool[n++] = UP_BRIDGE; pool[n++] = UP_BRIDGE; }
        if (hasMountains) { pool[n++] = UP_TUNNEL; pool[n++] = UP_TUNNEL; }
        pool[n++] = UP_ROADS;
        int a = pool[rng.nextInt(n)], b;
        do { b = pool[rng.nextInt(n)]; } while (b == a);
        offers[0] = a;
        offers[1] = b;
    }

    public void chooseUpgrade(int i) {
        if (!upgradePending) return;
        int u = offers[i];
        inv[u] += UP_AMOUNT[u];
        upgradePending = false;
    }

    // ----------------------------------------------------------------- spawning

    private float difficulty() {
        return mode == MODE_EXPERT ? 1.3f : 1f;
    }

    private void spawnLogic(float dt) {
        houseTimer -= dt;
        if (houseTimer <= 0) {
            spawnHouse();
            int nh = houses.size();
            float target = dests.size() * (5f + 0.4f * Math.min(week, 10));
            float base = nh < 3 ? 2.5f : nh < target ? Math.max(3.5f, 6.5f - 0.3f * week) : 13f;
            houseTimer = base / difficulty() * (0.8f + rng.nextFloat() * 0.4f);
        }
        if (time >= nextDestTime) {
            spawnDest(false);
            float days = Math.max(2.4f, 4.5f - 0.25f * week) / difficulty();
            nextDestTime = time + days * DAY * (0.85f + rng.nextFloat() * 0.3f);
        }
    }

    boolean canPlaceBuilding(int t) {
        return inRegion(t) && terrain[t] == MapDef.LAND && !road[t] && !isBuilding(t)
                && attach[t] == 0 && motorEnds[t] == 0 && crossAt[t] == null;
    }

    private boolean nearRegionEdge(int x, int y) {
        return x + 0.5f < rx0 + 1 || x + 0.5f > rx1 - 1 || y + 0.5f < ry0 + 1 || y + 0.5f > ry1 - 1;
    }

    void spawnHouse() {
        if (dests.isEmpty()) return;
        int[] hc = new int[MAX_COLORS], dc = new int[MAX_COLORS];
        for (House h : houses) hc[h.color]++;
        for (Dest d : dests) dc[d.color]++;
        float totalW = 0;
        float[] wt = new float[MAX_COLORS];
        for (int c = 0; c < MAX_COLORS; c++) {
            if (dc[c] == 0) continue;
            wt[c] = Math.max(0.6f, dc[c] * 6f - hc[c]);
            totalW += wt[c];
        }
        float r = rng.nextFloat() * totalW;
        int color = 0;
        for (int c = 0; c < MAX_COLORS; c++) {
            if (wt[c] == 0) continue;
            color = c;
            r -= wt[c];
            if (r <= 0) break;
        }
        // Pick one of the destinations of that colour, favouring those with fewer houses around.
        Dest d = null;
        float best = -1e9f;
        for (Dest e : dests) {
            if (e.color != color) continue;
            int around = 0;
            for (House h : houses) if (h.color == color && dist2(h.x + 0.5f, h.y + 0.5f, e.cx, e.cy) < 36) around++;
            float sc = -around + rng.nextFloat() * 2;
            if (sc > best) { best = sc; d = e; }
        }
        if (d == null) return;
        int radius = 3 + Math.min(4, week);
        int bestT = -1;
        best = -1e9f;
        for (int tries = 0; tries < 80; tries++) {
            int tx = (int) Math.floor(d.cx) + rng.nextInt(radius * 2 + 1) - radius;
            int ty = (int) Math.floor(d.cy) + rng.nextInt(radius * 2 + 1) - radius;
            if (!inMap(tx, ty)) continue;
            int t = idx(tx, ty);
            if (!canPlaceBuilding(t) || pickHouseDir(tx, ty, d, false) < 0) continue;
            float dist = (float) Math.sqrt(dist2(tx + 0.5f, ty + 0.5f, d.cx, d.cy));
            if (dist < 2.2f) continue;
            float sc = rng.nextFloat() * 1.5f - dist * 0.12f;
            for (int k = 0; k < 8; k++) {
                int nx = tx + DX[k], ny = ty + DY[k];
                if (!inMap(nx, ny)) continue;
                House h = houseAt[idx(nx, ny)];
                if (h != null) sc += h.color == color ? 0.5f : -0.4f;
                if (destAt[idx(nx, ny)] != null) sc -= 0.3f;
            }
            if (nearRegionEdge(tx, ty)) sc -= 0.5f;
            if (sc > best) { best = sc; bestT = t; }
        }
        if (bestT < 0) {
            // Nothing near: try anywhere in the region.
            for (int tries = 0; tries < 200 && bestT < 0; tries++) {
                int tx = (int) (rx0 + rng.nextFloat() * (rx1 - rx0));
                int ty = (int) (ry0 + rng.nextFloat() * (ry1 - ry0));
                if (inMap(tx, ty) && canPlaceBuilding(idx(tx, ty)) && pickHouseDir(tx, ty, d, false) >= 0) bestT = idx(tx, ty);
            }
            if (bestT < 0) return;
        }
        int tx = bestT % W, ty = bestT / W;
        int dir = pickHouseDir(tx, ty, d, true);
        House h = new House(tx, ty, dir, color, time);
        houses.add(h);
        houseAt[bestT] = h;
        attach[h.front]++;
        for (int i = 0; i < 2; i++) {
            Car c = new Car(h, i);
            c.laneSign = map.leftHand ? -1 : 1;
            h.cars[i] = c;
            cars.add(c);
        }
        events |= EV_HOUSE;
        roadsChanged();
    }

    private int pickHouseDir(int tx, int ty, Dest d, boolean random) {
        int bestD = -1;
        float best = -1e9f;
        for (int k = 0; k < 8; k += 2) {
            int fx = tx + DX[k], fy = ty + DY[k];
            if (!inRegion(fx, fy)) continue;
            int f = idx(fx, fy);
            if (terrain[f] != MapDef.LAND || isBuilding(f) || crossAt[f] != null) continue;
            float sc = road[f] ? 2f : 0f;
            float vx = d.cx - (tx + 0.5f), vy = d.cy - (ty + 0.5f);
            float vl = (float) Math.sqrt(vx * vx + vy * vy) + 1e-3f;
            sc += (DX[k] * vx + DY[k] * vy) / vl * 0.6f;
            if (random) sc += rng.nextFloat() * 0.8f;
            if (sc > best) { best = sc; bestD = k; }
        }
        return bestD;
    }

    static float dist2(float ax, float ay, float bx, float by) {
        return (ax - bx) * (ax - bx) + (ay - by) * (ay - by);
    }

    void spawnDest(boolean first) {
        int color;
        int[] dc = new int[MAX_COLORS];
        for (Dest d : dests) dc[d.color]++;
        if (first) {
            color = 0;
        } else {
            boolean allTwo = true;
            for (int c = 0; c < colorsUsed; c++) if (dc[c] < 2) allTwo = false;
            if (colorsUsed < MAX_COLORS && (colorsUsed < 2 || rng.nextFloat() < 0.55f || allTwo)) {
                color = colorsUsed;
            } else {
                color = 0;
                for (int c = 1; c < colorsUsed; c++) if (dc[c] < dc[color]) color = c;
            }
        }
        Dest best = null;
        float bestScore = -1e9f;
        float mx = W / 2f, my = H / 2f;
        for (int tries = 0; tries < 400; tries++) {
            int dir = rng.nextInt(4) * 2;
            int x0, y0;
            if (first && tries < 200) {
                x0 = (int) (mx - 1 + rng.nextInt(9) - 4);
                y0 = (int) (my - 1 + rng.nextInt(5) - 2);
            } else {
                x0 = (int) (rx0 + rng.nextFloat() * (rx1 - rx0));
                y0 = (int) (ry0 + rng.nextFloat() * (ry1 - ry0));
            }
            Dest cand = new Dest(x0, y0, dir, color, time);
            if (!destFits(cand)) continue;
            float sc = rng.nextFloat() * 2;
            if (first) sc -= (float) Math.sqrt(dist2(cand.cx, cand.cy, mx, my)) * 0.4f + (tries < 200 ? 0 : 20);
            float nearest = 1e9f;
            for (Dest d : dests) {
                float dd = dist2(d.cx, d.cy, cand.cx, cand.cy);
                nearest = Math.min(nearest, dd);
                if (d.color == color && dd < 100) sc -= 2;
            }
            if (nearest < 36) continue;
            if (nearest < 1e8f) sc -= Math.abs((float) Math.sqrt(nearest) - 9) * 0.15f;
            // Prefer open space with few buildings around.
            for (House h : houses) if (dist2(h.x + 0.5f, h.y + 0.5f, cand.cx, cand.cy) < 9) sc -= 0.5f;
            if (sc > bestScore) { bestScore = sc; best = cand; }
            if (first && tries > 120 && best != null) break;
        }
        if (best == null) return;
        if (color == colorsUsed) colorsUsed++;
        dests.add(best);
        for (int y = best.y0; y < best.y0 + best.h; y++)
            for (int x = best.x0; x < best.x0 + best.w; x++) destAt[idx(x, y)] = best;
        attach[best.entrance]++;
        events |= EV_DEST;
        roadsChanged();
    }

    private boolean destFits(Dest d) {
        for (int y = d.y0 - 1; y <= d.y0 + d.h; y++) {
            for (int x = d.x0 - 1; x <= d.x0 + d.w; x++) {
                boolean inside = d.covers(x, y);
                if (inside) {
                    if (!inMap(x, y) || !canPlaceBuilding(idx(x, y)) || nearRegionEdge(x, y)) return false;
                } else if (inMap(x, y) && destAt[idx(x, y)] != null) {
                    return false;
                }
            }
        }
        int e = d.entrance, ex = e % W, ey = e / W;
        if (!inRegion(ex, ey) || terrain[e] != MapDef.LAND || isBuilding(e) || crossAt[e] != null) return false;
        return true;
    }

    // ----------------------------------------------------------------- demand & failure

    private void demand(float dt) {
        int[] hc = new int[MAX_COLORS], dc = new int[MAX_COLORS];
        for (House h : houses) hc[h.color]++;
        for (Dest d : dests) dc[d.color]++;
        float mult = (1f + 0.08f * Math.min(week, 16)) * (mode == MODE_EXPERT ? 1.3f : 1f);
        for (Dest d : dests) {
            if (hc[d.color] == 0) continue;
            float age = time - d.spawnTime;
            float ramp = Math.min(1f, 0.3f + age / (DAY * 2.5f));
            // A destination served by only one or two houses asks for less.
            float supply = Math.min(1f, 0.4f + 0.2f * hc[d.color] / (float) dc[d.color]);
            float rate = BASE_DEMAND * mult * ramp * supply;
            d.acc += rate * dt;
            if (d.acc >= 1 && d.pins < MAX_PINS) {
                d.acc -= 1 + (rng.nextFloat() - 0.5f) * 0.6f;
                d.pins++;
            }
            if (d.pins >= OVER_PINS) {
                if (!d.warned) { d.warned = true; events |= EV_WARN; }
                d.timer += dt / OVER_TIME;
                if (d.timer >= 1) {
                    if (mode == MODE_ENDLESS) {
                        d.timer = 1;
                    } else if (!gameOver) {
                        gameOver = true;
                        failed = d;
                        events |= EV_GAMEOVER;
                    }
                }
            } else {
                d.timer = Math.max(0, d.timer - dt / 25f);
                if (d.timer == 0) d.warned = false;
            }
        }
    }

    // ----------------------------------------------------------------- path finding

    private final float[] dist = new float[N];
    private final int[] par = new int[N];
    private final int[] heap = new int[N * 8 + 16];
    private final float[] heapKey = new float[N * 8 + 16];
    private int heapSize;
    private final float[] nodePenalty = new float[N];
    private int penaltyVersion = -1;

    private void ensurePenalties() {
        if (penaltyVersion == roadVersion) return;
        penaltyVersion = roadVersion;
        for (int t = 0; t < N; t++) {
            if (!road[t]) { nodePenalty[t] = 0; continue; }
            if (special[t] == SP_ROUNDABOUT) nodePenalty[t] = 0.12f;
            else if (degree(t) >= 3) nodePenalty[t] = special[t] == SP_LIGHT ? 0.3f : 0.45f;
            else nodePenalty[t] = 0;
        }
    }

    private void heapPush(int v, float k) {
        int i = heapSize++;
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (heapKey[p] <= k) break;
            heap[i] = heap[p];
            heapKey[i] = heapKey[p];
            i = p;
        }
        heap[i] = v;
        heapKey[i] = k;
    }

    private int heapPop() {
        int top = heap[0];
        int v = heap[--heapSize];
        float k = heapKey[heapSize];
        int i = 0;
        while (true) {
            int c = 2 * i + 1;
            if (c >= heapSize) break;
            if (c + 1 < heapSize && heapKey[c + 1] < heapKey[c]) c++;
            if (heapKey[c] >= k) break;
            heap[i] = heap[c];
            heapKey[i] = heapKey[c];
            i = c;
        }
        heap[i] = v;
        heapKey[i] = k;
        return top;
    }

    /** Dijkstra from src over the road graph. If target >= 0 stops when it is settled. */
    private void dijkstra(int src, int target, float[] outDist, int[] outPar) {
        ensurePenalties();
        Arrays.fill(outDist, Float.MAX_VALUE);
        Arrays.fill(outPar, -1);
        heapSize = 0;
        if (!road[src]) return;
        outDist[src] = 0;
        heapPush(src, 0);
        while (heapSize > 0) {
            float k = heapKey[0];
            int u = heapPop();
            if (k > outDist[u]) continue;
            if (u == target) return;
            int c = conn[u] & 0xFF;
            for (int d = 0; d < 8; d++) {
                if ((c & (1 << d)) == 0) continue;
                int v = u + DX[d] + DY[d] * W;
                float nd = k + ((d & 1) == 0 ? 1f : 1.4142f) / CAR_SPEED + nodePenalty[v];
                if (nd < outDist[v] && heapSize < heap.length - 1) {
                    outDist[v] = nd;
                    outPar[v] = u;
                    heapPush(v, nd);
                }
            }
            if (motorEnds[u] > 0) {
                for (int i = 0, n = motorways.size(); i < n; i++) {
                    Motorway m = motorways.get(i);
                    int v = m.a == u ? m.b : m.b == u ? m.a : -1;
                    if (v < 0) continue;
                    float nd = k + m.length / MOTOR_SPEED + 0.3f + nodePenalty[v];
                    if (nd < outDist[v] && heapSize < heap.length - 1) {
                        outDist[v] = nd;
                        outPar[v] = u;
                        heapPush(v, nd);
                    }
                }
            }
        }
    }

    /** Shortest road path from src to dst (inclusive), or null. */
    int[] findPath(int src, int dst) {
        if (!road[src] || !road[dst]) return null;
        if (src == dst) return new int[]{src};
        // Search from dst so the parent chain walks from src toward dst.
        dijkstra(dst, src, dist, par);
        if (dist[src] == Float.MAX_VALUE) return null;
        int n = 1;
        for (int t = src; t != dst; t = par[t]) n++;
        int[] p = new int[n];
        int i = 0;
        for (int t = src; ; t = par[t]) {
            p[i++] = t;
            if (t == dst) break;
        }
        return p;
    }

    private void ensureField(Dest d) {
        if (d.fieldVersion == roadVersion && d.field != null) return;
        if (d.field == null) {
            d.field = new float[N];
            d.parent = new int[N];
        }
        dijkstra(d.entrance, -1, d.field, d.parent);
        d.fieldVersion = roadVersion;
    }

    boolean edgeValid(int a, int b) {
        if (a < 0 || b < 0) return true;
        if (!road[a] || !road[b]) return false;
        int d = dirBetween(a, b);
        if (d >= 0) return (conn[a] & (1 << d)) != 0;
        for (Motorway m : motorways) if ((m.a == a && m.b == b) || (m.a == b && m.b == a)) return true;
        return false;
    }

    // ----------------------------------------------------------------- dispatch

    private void dispatch() {
        for (int di = 0, dn = dests.size(); di < dn; di++) {
            Dest d = dests.get(di);
            if (d.pins - d.enroute <= 0) continue;
            int slot = d.freeSlot();
            if (slot < 0 || !road[d.entrance]) continue;
            ensureField(d);
            Car best = null;
            float bestD = Float.MAX_VALUE;
            for (int hi = 0, hn = houses.size(); hi < hn; hi++) {
                House h = houses.get(hi);
                if (h.color != d.color || !road[h.front]) continue;
                float f = d.field[h.front];
                if (f >= bestD) continue;
                Car c = h.cars[0].state == Car.HOME ? h.cars[0] : h.cars[1].state == Car.HOME ? h.cars[1] : null;
                if (c == null) continue;
                best = c;
                bestD = f;
            }
            if (best != null) startTrip(best, d, slot);
        }
    }

    private float[] bx = new float[256], by = new float[256];
    private int[] bt = new int[256];
    private int bn;

    private void bAdd(float x, float y, int t) {
        if (bn == bx.length) {
            bx = Arrays.copyOf(bx, bn * 2);
            by = Arrays.copyOf(by, bn * 2);
            bt = Arrays.copyOf(bt, bn * 2);
        }
        if (bn > 0 && Math.abs(bx[bn - 1] - x) < 1e-4f && Math.abs(by[bn - 1] - y) < 1e-4f) return;
        bx[bn] = x;
        by[bn] = y;
        bt[bn] = t;
        bn++;
    }

    private void bAddTile(int t) { bAdd(t % W + 0.5f, t / W + 0.5f, t); }

    private void startTrip(Car c, Dest d, int slot) {
        House h = c.home;
        bn = 0;
        bAdd(h.parkX(c.homeSlot), h.parkY(c.homeSlot), -1);
        for (int t = h.front; ; t = d.parent[t]) {
            bAddTile(t);
            if (t == d.entrance || d.parent[t] < 0) break;
        }
        addLotIn(d, slot);
        c.setPath(bx, by, bt, bn, true, true);
        c.state = Car.OUT;
        c.dest = d;
        c.slot = slot;
        c.speed = 0;
        c.stuck = 0;
        d.slots[slot] = c;
        d.enroute++;
    }

    private void addLotIn(Dest d, int slot) {
        bAdd(d.aisleX(slot), d.aisleY(slot), -1);
        bAdd(d.slotX(slot), d.slotY(slot), -1);
    }

    private void addHomeIn(Car c) {
        bAdd(c.home.parkX(c.homeSlot), c.home.parkY(c.homeSlot), -1);
    }

    private void sendHome(Car c) {
        Dest d = c.dest;
        int[] p = findPath(d.entrance, c.home.front);
        if (p == null) {
            c.wait = 1f;
            if (++c.retries > 8) vanish(c);
            return;
        }
        bn = 0;
        bAdd(d.slotX(c.slot), d.slotY(c.slot), -1);
        bAdd(d.aisleX(c.slot), d.aisleY(c.slot), -1);
        for (int t : p) bAddTile(t);
        addHomeIn(c);
        c.setPath(bx, by, bt, bn, true, true);
        d.slots[c.slot] = null;
        c.slot = -1;
        c.state = Car.BACK;
        c.retries = 0;
        c.speed = 0;
        c.stuck = 0;
    }

    /** Car gives up and reappears at home (its road was removed). */
    void vanish(Car c) {
        releaseAll(c);
        if (c.state == Car.OUT && c.dest != null) {
            c.dest.enroute--;
            if (c.slot >= 0) c.dest.slots[c.slot] = null;
        } else if (c.state == Car.PARKED && c.dest != null && c.slot >= 0) {
            c.dest.slots[c.slot] = null;
        }
        c.state = Car.HOME;
        c.dest = null;
        c.slot = -1;
        c.retries = 0;
        c.parkAtHome();
    }

    private void releaseAll(Car c) {
        for (int i = 0; i < 3; i++) release(c, i);
    }

    private void release(Car c, int i) {
        int t = c.held[i];
        if (t < 0) return;
        if (occ[t] > 0) occ[t]--;
        c.held[i] = -1;
        c.heldIn[i] = false;
        c.heldTime[i] = 0;
    }

    private void addHold(Car c, int t, int entry) {
        for (int i = 0; i < 3; i++) {
            if (c.held[i] < 0) {
                c.held[i] = t;
                c.heldIn[i] = false;
                c.heldTime[i] = 0;
                if (occ[t] == 0) occEntry[t] = (byte) entry;
                occ[t]++;
                return;
            }
        }
    }

    // ----------------------------------------------------------------- re-routing

    private void repathAll() {
        for (int i = 0, n = cars.size(); i < n; i++) {
            Car c = cars.get(i);
            if (c.isDriving()) repath(c);
        }
    }

    private void repath(Car c) {
        int k = c.nextPoint();
        if (k >= c.np) return;
        int cur = k - 1;
        int r = k;
        while (r < c.np && c.pt[r] < 0) r++;
        if (r >= c.np) {
            // Only driveway/parking points left.
            if (c.pt[cur] >= 0 && !road[c.pt[cur]]) vanish(c);
            return;
        }
        // The edge we are on must still exist.
        if (c.pt[cur] >= 0) {
            if (!edgeValid(c.pt[cur], c.pt[k]) || !road[c.pt[cur]]) { vanish(c); return; }
        } else if (!road[c.pt[r]]) {
            vanish(c);
            return;
        }
        int from = c.pt[r];
        boolean out = c.state == Car.OUT;
        int target = out ? c.dest.entrance : c.home.front;
        int[] p = findPath(from, target);
        if (p == null && out) {
            // Can't reach the destination any more: head home instead.
            int[] home = findPath(from, c.home.front);
            if (home == null) { vanish(c); return; }
            c.dest.enroute--;
            c.dest.slots[c.slot] = null;
            c.slot = -1;
            c.state = Car.BACK;
            out = false;
            p = home;
        } else if (p == null) {
            vanish(c);
            return;
        } else {
            // Unchanged route? Keep the current pieces to avoid jitter.
            int remaining = 0;
            for (int i = r; i < c.np && c.pt[i] >= 0; i++) remaining++;
            if (remaining == p.length) {
                boolean same = true;
                for (int i = 0; i < p.length; i++) if (c.pt[r + i] != p[i]) { same = false; break; }
                if (same) return;
            }
        }
        bn = 0;
        bAdd(c.clx, c.cly, -1);
        for (int i = k; i < r; i++) bAdd(c.px[i], c.py[i], c.pt[i]);
        for (int t : p) bAddTile(t);
        if (out) addLotIn(c.dest, c.slot);
        else addHomeIn(c);
        int ct = (int) Math.floor(c.clx) + (int) Math.floor(c.cly) * W;
        for (int i = 0; i < 3; i++) if (c.held[i] >= 0 && c.held[i] != ct) release(c, i);
        c.setPath(bx, by, bt, bn, false, true);
    }

    // ----------------------------------------------------------------- traffic lights

    private void updateLights(float dt) {
        for (int t = 0; t < N; t++) {
            if (special[t] != SP_LIGHT) continue;
            lightTimer[t] += dt;
            int phase = lightPhase[t];
            int want = lightDemand[t];
            boolean mine = (want & (1 << phase)) != 0, other = (want & (1 << (1 - phase))) != 0;
            boolean switchNow = (lightTimer[t] > 4.0f && other) || (lightTimer[t] > 1.2f && !mine && other);
            if (switchNow && occ[t] == 0) {
                lightPhase[t] = (byte) (1 - phase);
                lightTimer[t] = 0;
            }
            lightLast[t] = lightDemand[t];
            lightDemand[t] = 0;
        }
    }

    static int dirIndex(float ux, float uy) {
        int d = Math.round((float) (Math.atan2(uy, ux) / (Math.PI / 4)));
        return d & 7;
    }

    static int lightGroup(int dir) {
        int m = dir & 3;
        return (m == 0 || m == 1) ? 0 : 1;
    }

    private boolean canEnter(Car c, int k, int t) {
        int j = c.knode[k];
        float ex = c.px[j] - c.px[j - 1], ey = c.py[j] - c.py[j - 1];
        int entry = dirIndex(ex, ey);
        // Don't block the box: the space just beyond the junction must be free.
        if (j + 1 < c.np) {
            float ox = c.px[j + 1] - c.px[j], oy = c.py[j + 1] - c.py[j];
            float ol = (float) Math.sqrt(ox * ox + oy * oy);
            if (ol > 1e-4f) {
                ox /= ol;
                oy /= ol;
                float off = Car.LANE * c.laneSign;
                float qx = c.px[j] + ox * 0.8f - oy * off, qy = c.py[j] + oy * 0.8f + ox * off;
                for (int i = 0, n = cars.size(); i < n; i++) {
                    Car o = cars.get(i);
                    if (o == c || !o.isDriving() || o.onMotor) continue;
                    if (o.speed < 0.3f && dist2(o.x, o.y, qx, qy) < 0.09f && (o.hx * ox + o.hy * oy) > 0.3f) return false;
                }
            }
        }
        if (special[t] == SP_ROUNDABOUT) return occ[t] < 3;
        if (special[t] == SP_LIGHT) {
            int g = lightGroup(entry);
            lightDemand[t] |= (byte) (1 << g);
            if (lightPhase[t] != g) return false;
            // Amber: stop feeding the junction so the other direction gets its turn.
            if (lightTimer[t] > 4.0f && (lightLast[t] & (1 << (1 - g))) != 0) return false;
            return occ[t] == 0 || (lightGroup(occEntry[t]) == g && occ[t] < 3);
        }
        return occ[t] == 0 || (occEntry[t] == entry && occ[t] < 2);
    }

    // ----------------------------------------------------------------- car movement

    private void updateCar(Car c, float dt) {
        switch (c.state) {
            case Car.HOME:
                return;
            case Car.PARKED:
                c.wait -= dt;
                if (c.wait <= 0) sendHome(c);
                return;
            default:
                break;
        }
        if (c.ghost > 0) c.ghost -= dt;
        updateHolds(c, dt);

        int k0 = c.piece;
        float vmax = c.kmotor[k0] ? MOTOR_SPEED : CAR_SPEED;
        int ctile = c.curveTile(k0);
        if (ctile >= 0 && isIntersection(ctile)) vmax *= special[ctile] == SP_ROUNDABOUT ? 0.85f : 0.7f;
        if (c.pt[c.knode[k0]] < 0 && !c.kmotor[k0]) vmax *= 0.75f; // driveways & car parks

        float limit = Float.MAX_VALUE;
        float cur = c.distAlong();
        boolean waitingJunction = false;
        for (int k = k0; k < c.nk; k++) {
            float d = c.kcum[k] - cur;
            if (d > 0.35f) break;
            int t = c.curveTile(k);
            if (t < 0 || c.holds(t) || !isIntersection(t)) continue;
            int j = c.knode[k];
            float ex = c.px[j] - c.px[j - 1], ey = c.py[j] - c.py[j - 1];
            int entry = dirIndex(ex, ey);
            if (d <= 0 || c.ghost > 0 || canEnter(c, k, t)) {
                addHold(c, t, entry);
            } else {
                limit = Math.max(0, d - 0.06f);
                waitingJunction = true;
                break;
            }
        }

        boolean blocked = c.ghost <= 0 && carAhead(c);
        if (blocked) {
            c.speed = 0;
        } else {
            c.speed += (vmax - c.speed) * Math.min(1, dt * 4);
            if (c.speed < 0.2f) c.speed = 0.2f;
        }
        float move = blocked ? 0 : c.speed * dt;
        if (move > limit) {
            move = limit;
            if (limit < c.speed * dt * 0.5f) c.speed = 0.2f;
        }
        if (move < 1e-4f) {
            c.stuck += dt;
            if (c.stuck > (waitingJunction ? 12f : 9f)) {
                c.ghost = 1.5f;
                c.stuck = 0;
            }
        } else {
            c.stuck = 0;
        }
        c.s += move;
        while (c.piece < c.nk && c.s >= c.klen[c.piece]) {
            c.s -= c.klen[c.piece];
            c.piece++;
        }
        if (c.piece >= c.nk) {
            c.piece = c.nk - 1;
            c.s = c.klen[c.piece];
            c.computePose();
            arrive(c);
            return;
        }
        c.computePose();
        c.turnTowardHeading(dt);
    }

    private void updateHolds(Car c, float dt) {
        int ct = (int) Math.floor(c.clx) + (int) Math.floor(c.cly) * W;
        for (int i = 0; i < 3; i++) {
            int t = c.held[i];
            if (t < 0) continue;
            c.heldTime[i] += dt;
            if (t == ct) c.heldIn[i] = true;
            else if (c.heldIn[i] || c.heldTime[i] > 6f) release(c, i);
        }
    }

    private boolean carAhead(Car c) {
        float hx = c.hx, hy = c.hy;
        for (int i = 0, n = cars.size(); i < n; i++) {
            Car o = cars.get(i);
            if (o == c || !o.isDriving() || o.onMotor != c.onMotor) continue;
            float dx = o.x - c.x, dy = o.y - c.y;
            float along = dx * hx + dy * hy;
            if (along <= 0.02f || along > 0.42f) continue;
            float lat = Math.abs(-dx * hy + dy * hx);
            if (lat > 0.16f) continue;
            if (o.hx * hx + o.hy * hy < -0.2f) continue;
            return true;
        }
        return false;
    }

    private void arrive(Car c) {
        releaseAll(c);
        if (c.state == Car.OUT) {
            Dest d = c.dest;
            c.state = Car.PARKED;
            c.wait = PARK_TIME;
            c.speed = 0;
            d.enroute--;
            if (d.pins > 0) d.pins--;
            score++;
            lastDeliverColor = d.color;
            events |= EV_DELIVER;
        } else {
            c.state = Car.HOME;
            c.dest = null;
            c.parkAtHome();
        }
    }

    // ================================================================= queries for UI

    public int carsOut() {
        int n = 0;
        for (Car c : cars) if (c.state != Car.HOME) n++;
        return n;
    }

    public Dest mostUrgent() {
        Dest best = null;
        for (Dest d : dests) if (d.timer > 0 && (best == null || d.timer > best.timer)) best = d;
        return best;
    }

    public int takeEvents() {
        int e = events;
        events = 0;
        return e;
    }
}
