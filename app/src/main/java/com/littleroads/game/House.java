package com.littleroads.game;

/** A one-tile home. Its driveway opens onto the "front" tile; it owns two cars. */
public final class House {
    public final int x, y, tile, dir, front, color;
    public final float spawnTime;
    public final Car[] cars = new Car[2];

    House(int x, int y, int dir, int color, float spawnTime) {
        this.x = x;
        this.y = y;
        this.tile = y * World.W + x;
        this.dir = dir;
        this.front = (y + World.DY[dir]) * World.W + (x + World.DX[dir]);
        this.color = color;
        this.spawnTime = spawnTime;
    }

    /** Where car {@code i} waits while parked at home. */
    public float parkX(int i) {
        float fx = World.DX[dir], fy = World.DY[dir];
        return x + 0.5f + fx * 0.25f + (-fy) * (i == 0 ? -0.11f : 0.11f);
    }

    public float parkY(int i) {
        float fx = World.DX[dir], fy = World.DY[dir];
        return y + 0.5f + fy * 0.25f + fx * (i == 0 ? -0.11f : 0.11f);
    }
}
