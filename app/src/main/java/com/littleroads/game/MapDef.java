package com.littleroads.game;

/**
 * City definitions: name, colour palette, traffic side and a procedural terrain generator.
 * Terrain is deterministic per city so the same map is played every time.
 */
public final class MapDef {
    public static final int W = 40, H = 22;
    public static final byte LAND = 0, WATER = 1, MOUNTAIN = 2;

    public final int id;
    public final String name;
    public final String country;
    public final int land, water, mountain, outside;
    public final boolean leftHand;
    final long seed;

    private MapDef(int id, String name, String country, int land, int water, int mountain, int outside,
                   boolean leftHand, long seed) {
        this.id = id;
        this.name = name;
        this.country = country;
        this.land = land;
        this.water = water;
        this.mountain = mountain;
        this.outside = outside;
        this.leftHand = leftHand;
        this.seed = seed;
    }

    public static final MapDef[] ALL = {
            new MapDef(0, "Los Angeles", "USA", 0xFFF3E7D1, 0xFF9CCBE0, 0xFFE0CFAE, 0xFFE9DBC1, false, 1011),
            new MapDef(1, "Tokyo", "Japan", 0xFFEFEBE6, 0xFFA8C8DE, 0xFFDAD3C9, 0xFFE3DED7, true, 2027),
            new MapDef(2, "Munich", "Germany", 0xFFEAEEDF, 0xFFA5CEDD, 0xFFD4DCC4, 0xFFDEE3D1, false, 3049),
            new MapDef(3, "Istanbul", "Turkey", 0xFFF2E8DB, 0xFF8FBFD8, 0xFFDFD1BE, 0xFFE7DBCB, false, 4093),
            new MapDef(4, "Zurich", "Switzerland", 0xFFE9EDE7, 0xFF9EC7D6, 0xFFCBD4C8, 0xFFDCE1DA, false, 5101),
            new MapDef(5, "Rio de Janeiro", "Brazil", 0xFFEFF1DE, 0xFF8BC5D0, 0xFFC8D6B3, 0xFFE2E5CD, false, 6133),
    };

    private byte[] cache;

    public synchronized byte[] terrain() {
        if (cache == null) cache = generate();
        return cache;
    }

    public boolean hasWater() {
        byte[] t = terrain();
        for (byte b : t) if (b == WATER) return true;
        return false;
    }

    public boolean hasMountains() {
        byte[] t = terrain();
        for (byte b : t) if (b == MOUNTAIN) return true;
        return false;
    }

    private byte[] generate() {
        Noise n = new Noise(seed);
        byte[] t = new byte[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float u = (x + 0.5f) / W, v = (y + 0.5f) / H;
                t[y * W + x] = classify(u, v, x + 0.5f, y + 0.5f, n);
            }
        }
        // Smooth out single-tile specks so shorelines look tidy.
        for (int pass = 0; pass < 2; pass++) {
            byte[] c = t.clone();
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    byte me = c[y * W + x];
                    int same = 0;
                    for (int d = 0; d < 8; d += 2) {
                        int nx = x + World.DX[d], ny = y + World.DY[d];
                        if (nx < 0 || ny < 0 || nx >= W || ny >= H) { same++; continue; }
                        if (c[ny * W + nx] == me) same++;
                    }
                    if (me != LAND && same <= 1) t[y * W + x] = LAND;
                    if (me == LAND && same == 0) {
                        int nx = Math.min(W - 1, x + 1);
                        t[y * W + x] = c[y * W + nx];
                    }
                }
            }
        }
        return t;
    }

    private byte classify(float u, float v, float fx, float fy, Noise n) {
        float wob = n.fbm(fx * 0.18f, fy * 0.18f) - 0.5f;
        switch (id) {
            case 0: { // Los Angeles: Pacific coast on the left, hills to the north, a thin river
                float coast = 0.12f + 0.05f * (float) Math.sin(v * 5.0) + wob * 0.10f + (v > 0.7f ? (v - 0.7f) * 0.25f : 0);
                if (u < coast) return WATER;
                float river = 0.62f - v * 0.22f + 0.03f * (float) Math.sin(v * 9);
                if (Math.abs(u - river) * W < 0.55f && v > 0.25f) return WATER;
                float hill = n.fbm(fx * 0.22f + 7, fy * 0.22f + 3);
                if (v < 0.30f && u > 0.40f && hill > 0.56f) return MOUNTAIN;
                if (u > 0.86f && v > 0.55f && hill > 0.60f) return MOUNTAIN;
                return LAND;
            }
            case 1: { // Tokyo: bay in the south east, Sumida river, canal to the west
                float bx = (u - 1.02f) / 0.42f, by = (v - 1.08f) / 0.42f;
                if (bx * bx + by * by < 1 + wob * 0.6f) return WATER;
                float river = 0.60f + 0.05f * (float) Math.sin(v * 7) + wob * 0.04f;
                if (Math.abs(u - river) * W < 0.85f) return WATER;
                float canal = 0.36f + 0.06f * (float) Math.sin(u * 8);
                if (u < river && u > 0.05f && Math.abs(v - canal) * H < 0.55f) return WATER;
                return LAND;
            }
            case 2: { // Munich: the Isar crossing diagonally, a lake and a few parks on hills
                float river = 0.20f + v * 0.55f + 0.04f * (float) Math.sin(v * 9) + wob * 0.03f;
                if (Math.abs(u - river) * W < 1.0f) return WATER;
                float lx = (u - 0.86f) / 0.09f, ly = (v - 0.22f) / 0.17f;
                if (lx * lx + ly * ly < 1 + wob) return WATER;
                float hill = n.fbm(fx * 0.25f + 11, fy * 0.25f + 5);
                if (u < 0.18f && v > 0.65f && hill > 0.55f) return MOUNTAIN;
                return LAND;
            }
            case 3: { // Istanbul: the Bosphorus splits the city, Golden Horn and Sea of Marmara
                float strait = 0.52f + 0.07f * (float) Math.sin(v * 6) + wob * 0.03f;
                if (Math.abs(u - strait) * W < 1.6f) return WATER;
                float horn = 0.42f + (strait - u) * 0.45f;
                if (u < strait && u > 0.18f && Math.abs(v - horn) * H < 0.7f) return WATER;
                if (v > 0.86f + wob * 0.12f + 0.04f * (float) Math.sin(u * 7)) return WATER;
                return LAND;
            }
            case 4: { // Zurich: long lake to the south east, the Limmat, mountains around the edge
                float px = u - 0.42f, py = v - 0.55f;
                float dxl = 0.45f, dyl = 0.75f; // lake axis
                float tProj = Math.max(0, Math.min(1, (px * dxl + py * dyl) / (dxl * dxl + dyl * dyl)));
                float qx = (px - dxl * tProj) * W, qy = (py - dyl * tProj) * H;
                if (Math.sqrt(qx * qx + qy * qy) < 2.0f + wob * 2) return WATER;
                float river = 0.42f - (0.55f - v) * 0.5f;
                if (v < 0.56f && Math.abs(u - river) * W < 0.55f) return WATER;
                float edge = Math.max(Math.abs(u - 0.5f) * 2, Math.abs(v - 0.5f) * 2);
                float hill = n.fbm(fx * 0.2f + 3, fy * 0.2f + 9);
                if (hill + edge * 0.35f > 0.88f) return MOUNTAIN;
                return LAND;
            }
            default: { // Rio de Janeiro: Atlantic to the south, Guanabara bay, many granite peaks
                if (v > 0.84f + 0.05f * (float) Math.sin(u * 9) + wob * 0.10f) return WATER;
                float bx = (u - 0.98f) / 0.17f, by = (v - 0.42f) / 0.36f;
                if (bx * bx + by * by < 1 + wob) return WATER;
                float hill = n.fbm(fx * 0.24f + 1, fy * 0.24f + 13);
                float c = Math.abs(u - 0.45f) + Math.abs(v - 0.45f);
                if (c > 0.16f && hill > 0.58f) return MOUNTAIN;
                return LAND;
            }
        }
    }

    /** Small seeded value-noise generator. */
    static final class Noise {
        private final int[] perm = new int[512];

        Noise(long seed) {
            java.util.Random r = new java.util.Random(seed);
            int[] p = new int[256];
            for (int i = 0; i < 256; i++) p[i] = i;
            for (int i = 255; i > 0; i--) {
                int j = r.nextInt(i + 1);
                int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
            }
            for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
        }

        private float hash(int x, int y) {
            return perm[(perm[x & 255] + y) & 511] / 255f;
        }

        float value(float x, float y) {
            int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
            float xf = x - xi, yf = y - yi;
            float u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
            float a = hash(xi, yi), b = hash(xi + 1, yi), c = hash(xi, yi + 1), d = hash(xi + 1, yi + 1);
            return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
        }

        float fbm(float x, float y) {
            return value(x, y) * 0.65f + value(x * 2.1f + 17, y * 2.1f + 31) * 0.35f;
        }
    }
}
