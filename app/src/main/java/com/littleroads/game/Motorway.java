package com.littleroads.game;

/** An elevated express link between two road tiles. */
public final class Motorway {
    public final int a, b;
    public final float length;

    Motorway(int a, int b) {
        this.a = a;
        this.b = b;
        float dx = a % World.W - b % World.W, dy = a / World.W - b / World.W;
        this.length = (float) Math.sqrt(dx * dx + dy * dy);
    }
}
