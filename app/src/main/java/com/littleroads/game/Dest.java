package com.littleroads.game;

/**
 * A destination (office/shop) with a 3x2 footprint: a building at the back and a
 * four-space parking lot at the front. Cars enter through the entrance tile.
 */
public final class Dest {
    public final int x0, y0, w, h, dir, color, entrance;
    public final float cx, cy;   // footprint centre
    public final float fx, fy;   // unit vector toward the entrance
    public final float rx, ry;   // lateral unit vector
    public final float spawnTime;

    public int pins;             // outstanding demand
    public int enroute;          // cars on their way
    public float acc;            // fractional demand accumulator
    public float timer;          // overload timer 0..1
    public final Car[] slots = new Car[World.SLOTS];
    public boolean warned;

    // Cached shortest-path field from the entrance (rebuilt when the road network changes).
    float[] field;
    int[] parent;
    int fieldVersion = -1;

    static final float[] SLOT_LAT = {-1.20f, -0.72f, 0.72f, 1.20f};

    Dest(int x0, int y0, int dir, int color, float spawnTime) {
        this.x0 = x0;
        this.y0 = y0;
        this.dir = dir;
        boolean horiz = World.DX[dir] != 0;
        this.w = horiz ? 2 : 3;
        this.h = horiz ? 3 : 2;
        this.color = color;
        this.spawnTime = spawnTime;
        this.cx = x0 + w / 2f;
        this.cy = y0 + h / 2f;
        this.fx = World.DX[dir];
        this.fy = World.DY[dir];
        this.rx = -fy;
        this.ry = fx;
        int ex = (int) Math.floor(cx + fx * 1.5f), ey = (int) Math.floor(cy + fy * 1.5f);
        this.entrance = ey * World.W + ex;
    }

    public boolean covers(int x, int y) {
        return x >= x0 && y >= y0 && x < x0 + w && y < y0 + h;
    }

    int freeSlot() {
        for (int i = 0; i < slots.length; i++) if (slots[i] == null) return i;
        return -1;
    }

    /** Point in the aisle in front of slot i. */
    float aisleX(int i) { return cx + fx * 0.62f + rx * SLOT_LAT[i]; }
    float aisleY(int i) { return cy + fy * 0.62f + ry * SLOT_LAT[i]; }
    /** Parking space i. */
    public float slotX(int i) { return cx + fx * 0.20f + rx * SLOT_LAT[i]; }
    public float slotY(int i) { return cy + fy * 0.20f + ry * SLOT_LAT[i]; }
}
