package com.mayra.assistant;

/**
 * Detects a double clap in a stream of 16-bit PCM frames.
 *
 * A clap is a sudden, loud, very short sound: the level jumps far above the background noise
 * and falls back within ~120 ms. Speech and music stay loud for longer, so they are rejected.
 * Two claps 150–900 ms apart trigger the assistant.
 */
public final class ClapDetector {

    private final int sampleRate;
    private final int minPeak;          // absolute peak a clap must reach (0..32767)
    private final float jumpFactor;     // how many times louder than background

    private double noise = 300;         // running background RMS
    private double prevRms = 0;

    private long candidateAt = -1;      // sample index where a possible clap started
    private double candidateRms;
    private long lastClapAt = -1_000_000;
    private long cooldownUntil = -1;
    private long samples;

    /** @param sensitivity 0 = low, 1 = medium, 2 = high */
    public ClapDetector(int sampleRate, int sensitivity) {
        this.sampleRate = sampleRate;
        this.minPeak = sensitivity >= 2 ? 6000 : sensitivity == 1 ? 10000 : 15000;
        this.jumpFactor = sensitivity >= 2 ? 5f : sensitivity == 1 ? 7f : 9f;
    }

    /** Feed one frame (about 20–40 ms). Returns true when a double clap is detected. */
    public boolean process(short[] buf, int len) {
        long frameStart = samples;
        samples += len;
        int peak = 0;
        double sum = 0;
        for (int i = 0; i < len; i++) {
            int v = Math.abs((int) buf[i]);
            if (v > peak) peak = v;
            sum += (double) buf[i] * buf[i];
        }
        double rms = Math.sqrt(sum / Math.max(1, len));
        boolean triggered = false;

        if (candidateAt >= 0) {
            long age = frameStart - candidateAt;
            if (rms < candidateRms * 0.35) {
                // decayed quickly → a real clap
                candidateAt = -1;
                triggered = onClap(frameStart);
            } else if (age > ms(130)) {
                candidateAt = -1;          // stayed loud → speech / music / noise burst
                lastClapAt = -1_000_000;   // also break any pending double-clap
            }
        } else if (frameStart >= cooldownUntil
                && peak >= minPeak
                && rms > noise * jumpFactor
                && rms > prevRms * 2.5) {
            candidateAt = frameStart;
            candidateRms = rms;
        }

        if (candidateAt < 0 && rms < noise * 3) {
            noise = noise * 0.95 + rms * 0.05;   // adapt background only on quiet frames
            if (noise < 50) noise = 50;
        }
        prevRms = rms;
        return triggered;
    }

    private boolean onClap(long at) {
        long gap = at - lastClapAt;
        if (gap >= ms(150) && gap <= ms(900)) {
            lastClapAt = -1_000_000;
            cooldownUntil = at + ms(2500);
            return true;
        }
        lastClapAt = at;
        return false;
    }

    private long ms(int m) { return (long) sampleRate * m / 1000; }
}
