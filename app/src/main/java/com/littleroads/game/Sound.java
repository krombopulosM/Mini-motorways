package com.littleroads.game;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Tiny synthesised sound effects; written to WAV files once and played through SoundPool. */
final class Sound {
    static final int POP = 0, DEST = 1, DELIVER = 2, PLACE = 3, ERASE = 4, WEEK = 5, WARN = 6, OVER = 7, CLICK = 8, DENY = 9;
    private static final int RATE = 22050;
    private static final float[] PENTA = {1f, 1.125f, 1.25f, 1.5f, 1.6667f, 2f};

    private SoundPool pool;
    private final int[] ids = new int[10];
    boolean enabled = true;
    private int note;
    private long lastPlace;

    Sound(Context ctx) {
        try {
            pool = new SoundPool.Builder()
                    .setMaxStreams(8)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .build();
            File dir = new File(ctx.getCacheDir(), "sfx2");
            if (!dir.exists()) dir.mkdirs();
            ids[POP] = load(dir, "pop", tone(new float[]{880, 660}, 0.16f, 0.5f, 0));
            ids[DEST] = load(dir, "dest", chord(new float[]{523.25f, 659.25f, 783.99f}, 0.09f, 0.7f));
            ids[DELIVER] = load(dir, "deliver", tone(new float[]{523.25f}, 0.45f, 0.35f, 1));
            ids[PLACE] = load(dir, "place", tone(new float[]{1400, 1200}, 0.035f, 0.18f, 0));
            ids[ERASE] = load(dir, "erase", tone(new float[]{500, 300}, 0.06f, 0.25f, 0));
            ids[WEEK] = load(dir, "week", chord(new float[]{392f, 523.25f, 659.25f, 783.99f, 1046.5f}, 0.11f, 1.1f));
            ids[WARN] = load(dir, "warn", chord(new float[]{330f, 0f, 330f}, 0.13f, 0.5f));
            ids[OVER] = load(dir, "over", chord(new float[]{523.25f, 440f, 349.23f, 261.63f}, 0.22f, 1.4f));
            ids[CLICK] = load(dir, "click", tone(new float[]{900}, 0.03f, 0.25f, 0));
            ids[DENY] = load(dir, "deny", tone(new float[]{220, 180}, 0.12f, 0.3f, 0));
        } catch (Throwable t) {
            pool = null;
        }
    }

    private int load(File dir, String name, short[] pcm) throws IOException {
        File f = new File(dir, name + ".wav");
        if (!f.exists() || f.length() < 44) writeWav(f, pcm);
        return pool.load(f.getAbsolutePath(), 1);
    }

    /** A pitch-swept sine with an exponential decay; mode 1 adds a soft overtone like a marimba. */
    private static short[] tone(float[] freqs, float dur, float vol, int mode) {
        int n = (int) (RATE * dur) + 1;
        short[] out = new short[n];
        double phase = 0, phase2 = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            float f = freqs.length == 1 ? freqs[0] : freqs[0] + (freqs[1] - freqs[0]) * t;
            phase += 2 * Math.PI * f / RATE;
            phase2 += 2 * Math.PI * f * 4 / RATE;
            float env = (float) Math.exp(-t * (mode == 1 ? 5 : 4)) * Math.min(1, i / (RATE * 0.004f));
            double v = Math.sin(phase);
            if (mode == 1) v = v * 0.85 + Math.sin(phase2) * 0.15 * Math.exp(-t * 18);
            out[i] = (short) (v * env * vol * 32000);
        }
        return out;
    }

    private static short[] chord(float[] notes, float step, float dur) {
        int n = (int) (RATE * dur);
        float[] mix = new float[n];
        for (int k = 0; k < notes.length; k++) {
            if (notes[k] <= 0) continue;
            int start = (int) (k * step * RATE);
            double phase = 0;
            for (int i = start; i < n; i++) {
                float t = (i - start) / (float) RATE;
                phase += 2 * Math.PI * notes[k] / RATE;
                float env = (float) Math.exp(-t * 4.5f) * Math.min(1, (i - start) / (RATE * 0.005f));
                mix[i] += (float) (Math.sin(phase) * 0.6 + Math.sin(phase * 2) * 0.12) * env;
            }
        }
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            float fade = Math.min(1, (n - i) / (RATE * 0.05f));
            out[i] = (short) (Math.max(-1, Math.min(1, mix[i] * 0.35f * fade)) * 32000);
        }
        return out;
    }

    private static void writeWav(File f, short[] pcm) throws IOException {
        int dataLen = pcm.length * 2;
        byte[] b = new byte[44 + dataLen];
        putStr(b, 0, "RIFF");
        putInt(b, 4, 36 + dataLen);
        putStr(b, 8, "WAVEfmt ");
        putInt(b, 16, 16);
        putShort(b, 20, 1);
        putShort(b, 22, 1);
        putInt(b, 24, RATE);
        putInt(b, 28, RATE * 2);
        putShort(b, 32, 2);
        putShort(b, 34, 16);
        putStr(b, 36, "data");
        putInt(b, 40, dataLen);
        for (int i = 0; i < pcm.length; i++) putShort(b, 44 + i * 2, pcm[i]);
        FileOutputStream os = new FileOutputStream(f);
        try {
            os.write(b);
        } finally {
            os.close();
        }
    }

    private static void putStr(byte[] b, int o, String s) {
        for (int i = 0; i < s.length(); i++) b[o + i] = (byte) s.charAt(i);
    }

    private static void putInt(byte[] b, int o, int v) {
        b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); b[o + 2] = (byte) (v >> 16); b[o + 3] = (byte) (v >> 24);
    }

    private static void putShort(byte[] b, int o, int v) {
        b[o] = (byte) v; b[o + 1] = (byte) (v >> 8);
    }

    void play(int which) {
        play(which, 1f, 1f);
    }

    void play(int which, float rate, float vol) {
        if (!enabled || pool == null) return;
        if (which == PLACE || which == ERASE) {
            long now = System.currentTimeMillis();
            if (now - lastPlace < 45) return;
            lastPlace = now;
        }
        pool.play(ids[which], vol, vol, 1, 0, rate);
    }

    /** Deliveries walk up and down a pentatonic scale, a little like a music box. */
    void deliver(int color) {
        note = (note + 1 + (color % 3)) % PENTA.length;
        play(DELIVER, PENTA[note] * (color % 2 == 0 ? 1f : 0.75f), 0.55f);
    }

    void release() {
        if (pool != null) pool.release();
        pool = null;
    }
}
