package engine.world;

import java.util.Random;

public class PerlinNoise {
    private final int[] p = new int[Chunk.HEIGHT];

    public PerlinNoise(long seed) {
        int[] perm = new int[256];
        for (int i = 0; i < 256; i++) perm[i] = i;
        Random r = new Random(seed);
        // Fisher–Yates
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = perm[i]; perm[i] = perm[j]; perm[j] = t;
        }
        // duplicate
        for (int i = 0; i < 512; i++) p[i] = perm[i & 255];
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static double lerp(double a, double b, double t) { return a + t * (b - a); }
    private static double grad(int hash, double x, double y) {
        // 8 gradient directions
        switch (hash & 7) {
            case 0: return  x + y;
            case 1: return  x - y;
            case 2: return -x + y;
            case 3: return -x - y;
            case 4: return  x;
            case 5: return -x;
            case 6: return  y;
            default: return -y;
        }
    }

    /** Returns noise in [-1, 1] */
    double noise(double x, double y) {
        int X = (int)Math.floor(x) & 255;
        int Y = (int)Math.floor(y) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);

        int aa = p[p[X] + Y];
        int ab = p[p[X] + Y + 1];
        int ba = p[p[X + 1] + Y];
        int bb = p[p[X + 1] + Y + 1];

        double u = fade(xf);
        double v = fade(yf);

        double x1 = lerp(grad(aa, xf,     yf),     grad(ba, xf - 1, yf),     u);
        double x2 = lerp(grad(ab, xf, yf - 1),    grad(bb, xf - 1, yf - 1), u);
        return lerp(x1, x2, v);
    }

    /** Fractal Brownian Motion (octaves), result in [-1, 1] (approx). */
    double fbm(double x, double y, int octaves, double lacunarity, double gain) {
        double amp = 1.0;
        double freq = 1.0;
        double sum = 0.0;
        double ampSum = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x * freq, y * freq);
            ampSum += amp;
            amp *= gain;         // persistence
            freq *= lacunarity;  // frequency growth
        }
        return sum / ampSum; // normalize to roughly [-1,1]
    }
}
