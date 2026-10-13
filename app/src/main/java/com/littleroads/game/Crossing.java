package com.littleroads.game;

/** A bridge (over water) or tunnel (through a mountain): a straight run between two land tiles. */
public final class Crossing {
    public final boolean tunnel;
    public final int a, b, dir;
    public final int[] interior;

    Crossing(boolean tunnel, int a, int b, int dir, int[] interior) {
        this.tunnel = tunnel;
        this.a = a;
        this.b = b;
        this.dir = dir;
        this.interior = interior;
    }
}
