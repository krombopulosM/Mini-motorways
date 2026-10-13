package com.littleroads.game;

/**
 * A car belongs to a house. Its route is a list of points (tile centres plus a few
 * virtual points for driveways and parking spaces) turned into a smooth sequence of
 * straight "line" pieces and quadratic-Bezier "curve" pieces around each corner.
 */
public final class Car {
    public static final int HOME = 0, OUT = 1, PARKED = 2, BACK = 3;
    static final float LANE = 0.12f;

    public final House home;
    public final int homeSlot;
    public final int color;

    public int state = HOME;
    public Dest dest;
    public int slot = -1;

    // Route points. pt[i] is the tile index when the point is a road tile centre, else -1.
    float[] px = new float[64], py = new float[64];
    int[] pt = new int[64];
    int np;

    // Pieces.
    int nk;
    float[] kax = new float[128], kay = new float[128], kcx = new float[128], kcy = new float[128];
    float[] kbx = new float[128], kby = new float[128], klen = new float[128], kcum = new float[128];
    boolean[] kcurve = new boolean[128], kmotor = new boolean[128];
    int[] knode = new int[128];   // line: edge index i (point i -> i+1); curve: point index
    float total;
    boolean rampStart, rampEnd;

    int piece;
    float s;

    // Pose. (clx, cly) is the centre line, (x, y) includes the lane offset.
    public float x, y, ang;
    float clx, cly, hx = 1, hy;
    public float speed;
    public boolean onMotor;
    float laneSign = 1;

    // Intersection reservations.
    final int[] held = {-1, -1, -1};
    final boolean[] heldIn = new boolean[3];
    final float[] heldTime = new float[3];

    float stuck, ghost, wait;
    int retries;

    Car(House home, int homeSlot) {
        this.home = home;
        this.homeSlot = homeSlot;
        this.color = home.color;
        parkAtHome();
    }

    void parkAtHome() {
        x = clx = home.parkX(homeSlot);
        y = cly = home.parkY(homeSlot);
        ang = (float) Math.atan2(World.DY[home.dir], World.DX[home.dir]);
        speed = 0;
        onMotor = false;
    }

    // ------------------------------------------------------------------ path building

    void setPath(float[] xs, float[] ys, int[] ts, int n, boolean rs, boolean re) {
        if (px.length < n) {
            int cap = n + 32;
            px = new float[cap];
            py = new float[cap];
            pt = new int[cap];
        }
        System.arraycopy(xs, 0, px, 0, n);
        System.arraycopy(ys, 0, py, 0, n);
        System.arraycopy(ts, 0, pt, 0, n);
        np = n;
        rampStart = rs;
        rampEnd = re;
        nk = 0;
        total = 0;
        for (int i = 0; i < n - 1; i++) {
            float dx = px[i + 1] - px[i], dy = py[i + 1] - py[i];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-5f) continue;
            float ux = dx / len, uy = dy / len;
            float trim = Math.min(0.5f, len / 2);
            float ts0 = i == 0 ? 0 : trim, te = i == n - 2 ? 0 : trim;
            float ax = px[i] + ux * ts0, ay = py[i] + uy * ts0;
            float bx = px[i + 1] - ux * te, by = py[i + 1] - uy * te;
            boolean motor = pt[i] >= 0 && pt[i + 1] >= 0 && !adjacent(pt[i], pt[i + 1]);
            float ll = (float) Math.hypot(bx - ax, by - ay);
            if (ll > 1e-4f) addPiece(false, ax, ay, 0, 0, bx, by, ll, i, motor);
            if (i < n - 2) {
                // Curve around point i+1 toward the start of the next edge.
                int j = i + 1;
                float dx2 = px[j + 1] - px[j], dy2 = py[j + 1] - py[j];
                float len2 = (float) Math.sqrt(dx2 * dx2 + dy2 * dy2);
                if (len2 < 1e-5f) continue;
                float tr2 = Math.min(0.5f, len2 / 2);
                float nx = px[j] + dx2 / len2 * tr2, ny = py[j] + dy2 / len2 * tr2;
                float cl = curveLen(bx, by, px[j], py[j], nx, ny);
                if (cl > 1e-4f) addPiece(true, bx, by, px[j], py[j], nx, ny, cl, j, false);
            }
        }
        if (nk == 0) addPiece(false, px[0], py[0], 0, 0, px[n - 1], py[n - 1], 1e-3f, 0, false);
        piece = 0;
        s = 0;
        computePose();
    }

    static boolean adjacent(int a, int b) {
        int dx = Math.abs(a % World.W - b % World.W), dy = Math.abs(a / World.W - b / World.W);
        return dx <= 1 && dy <= 1;
    }

    private void addPiece(boolean curve, float ax, float ay, float cx, float cy, float bx, float by,
                          float len, int node, boolean motor) {
        if (nk == kax.length) grow();
        kcurve[nk] = curve;
        kax[nk] = ax; kay[nk] = ay;
        kcx[nk] = cx; kcy[nk] = cy;
        kbx[nk] = bx; kby[nk] = by;
        klen[nk] = len;
        kcum[nk] = total;
        knode[nk] = node;
        kmotor[nk] = motor;
        total += len;
        nk++;
    }

    private void grow() {
        int c = kax.length * 2;
        kax = java.util.Arrays.copyOf(kax, c); kay = java.util.Arrays.copyOf(kay, c);
        kcx = java.util.Arrays.copyOf(kcx, c); kcy = java.util.Arrays.copyOf(kcy, c);
        kbx = java.util.Arrays.copyOf(kbx, c); kby = java.util.Arrays.copyOf(kby, c);
        klen = java.util.Arrays.copyOf(klen, c); kcum = java.util.Arrays.copyOf(kcum, c);
        kcurve = java.util.Arrays.copyOf(kcurve, c); kmotor = java.util.Arrays.copyOf(kmotor, c);
        knode = java.util.Arrays.copyOf(knode, c);
    }

    private static float curveLen(float ax, float ay, float cx, float cy, float bx, float by) {
        float len = 0, lx = ax, ly = ay;
        for (int i = 1; i <= 8; i++) {
            float u = i / 8f, a = (1 - u) * (1 - u), b = 2 * u * (1 - u), c = u * u;
            float x = a * ax + b * cx + c * bx, y = a * ay + b * cy + c * by;
            len += (float) Math.hypot(x - lx, y - ly);
            lx = x;
            ly = y;
        }
        return len;
    }

    /** Tile index of the corner a curve piece turns around, or -1. */
    int curveTile(int k) {
        return kcurve[k] ? pt[knode[k]] : -1;
    }

    /** Index of the first route point the car has not yet passed. */
    int nextPoint() {
        return knode[piece] + 1;
    }

    float distAlong() {
        return kcum[piece] + s;
    }

    void computePose() {
        int k = piece;
        float len = klen[k];
        float t = len <= 0 ? 1 : Math.max(0, Math.min(1, s / len));
        float tx, ty;
        if (!kcurve[k]) {
            clx = kax[k] + (kbx[k] - kax[k]) * t;
            cly = kay[k] + (kby[k] - kay[k]) * t;
            tx = kbx[k] - kax[k];
            ty = kby[k] - kay[k];
        } else {
            float a = (1 - t) * (1 - t), b = 2 * t * (1 - t), c = t * t;
            clx = a * kax[k] + b * kcx[k] + c * kbx[k];
            cly = a * kay[k] + b * kcy[k] + c * kby[k];
            tx = 2 * (1 - t) * (kcx[k] - kax[k]) + 2 * t * (kbx[k] - kcx[k]);
            ty = 2 * (1 - t) * (kcy[k] - kay[k]) + 2 * t * (kby[k] - kcy[k]);
            if (Math.abs(tx) + Math.abs(ty) < 1e-6f) {
                tx = kbx[k] - kax[k];
                ty = kby[k] - kay[k];
            }
        }
        float tl = (float) Math.sqrt(tx * tx + ty * ty);
        if (tl > 1e-6f) {
            hx = tx / tl;
            hy = ty / tl;
        }
        float d = kcum[k] + s, f = 1;
        if (rampStart) f = Math.min(f, d / 0.6f);
        if (rampEnd) f = Math.min(f, (total - d) / 0.6f);
        f = Math.max(0, Math.min(1, f));
        float off = LANE * f * laneSign;
        x = clx - hy * off;
        y = cly + hx * off;
        onMotor = kmotor[k];
    }

    void turnTowardHeading(float dt) {
        float target = (float) Math.atan2(hy, hx);
        float d = target - ang;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        ang += d * Math.min(1, dt * 14);
    }

    boolean holds(int t) {
        return held[0] == t || held[1] == t || held[2] == t;
    }

    boolean isDriving() {
        return state == OUT || state == BACK;
    }
}
