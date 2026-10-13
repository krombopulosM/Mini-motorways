package com.littleroads.game;

/** Colours for a map in day or night mode. */
final class Palette {
    static final int[] BUILD = {0xFFE5534B, 0xFF3F8EDB, 0xFFF2B33D, 0xFF3FA66B, 0xFF9A6AD6, 0xFFF07B3A};
    static final int[] BUILD_NIGHT = {0xFFFF6B63, 0xFF5AA6F5, 0xFFFFC857, 0xFF56C485, 0xFFB388F0, 0xFFFF9150};

    int land, water, waterDeep, mountain, mountainHi, outside, road, roadHi, bridgeRail, motorway, motorwayEdge,
            grid, text, textDim, panel, panelLine, shadow, island, lotLine, warn, good;
    int[] build;
    boolean night;

    static Palette of(MapDef m, boolean night) {
        Palette p = new Palette();
        p.night = night;
        if (!night) {
            p.land = m.land;
            p.water = m.water;
            p.waterDeep = mix(m.water, 0xFF000000, 0.10f);
            p.mountain = m.mountain;
            p.mountainHi = mix(m.mountain, 0xFF000000, 0.07f);
            p.outside = m.outside;
            p.road = 0xFF55585C;
            p.roadHi = 0xFF6A6E73;
            p.bridgeRail = 0xFFFAFAF7;
            p.motorway = 0xFF707479;
            p.motorwayEdge = 0xFFFFFFFF;
            p.grid = 0x14000000;
            p.text = 0xFF34363A;
            p.textDim = 0xFF8A8C90;
            p.panel = 0xF2FFFFFF;
            p.panelLine = 0x1F000000;
            p.shadow = 0x30000000;
            p.island = 0xFF9CC78A;
            p.lotLine = 0xCCFFFFFF;
            p.build = BUILD;
        } else {
            p.land = 0xFF262A31;
            p.water = 0xFF1C2D3D;
            p.waterDeep = 0xFF16242F;
            p.mountain = 0xFF323842;
            p.mountainHi = 0xFF3A414C;
            p.outside = 0xFF1D2026;
            p.road = 0xFF8E959E;
            p.roadHi = 0xFFA4ABB3;
            p.bridgeRail = 0xFF5D6670;
            p.motorway = 0xFFB4BAC2;
            p.motorwayEdge = 0xFF3B4250;
            p.grid = 0x14FFFFFF;
            p.text = 0xFFE8E9EB;
            p.textDim = 0xFF9097A0;
            p.panel = 0xF2303540;
            p.panelLine = 0x22FFFFFF;
            p.shadow = 0x50000000;
            p.island = 0xFF3E5A3E;
            p.lotLine = 0x99FFFFFF;
            p.build = BUILD_NIGHT;
        }
        p.warn = 0xFFE5534B;
        p.good = 0xFF3FA66B;
        return p;
    }

    static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int r = (int) (ar + (br - ar) * t), g = (int) (ag + (bg - ag) * t), bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static int alpha(int c, int a) {
        return (c & 0x00FFFFFF) | (a << 24);
    }
}
