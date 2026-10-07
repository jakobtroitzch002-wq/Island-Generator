package dev.jakob.islandgen.layout;

/**
 * Kleine, abhängigkeitsfreie Noise- und Hash-Helfer (seeded, deterministisch, threadsicher).
 */
public final class Noise {
	private final int[] perm = new int[512];

	public Noise(long seed) {
		int[] p = new int[256];
		for (int i = 0; i < 256; i++) p[i] = i;
		long s = seed;
		for (int i = 255; i > 0; i--) {
			s = mix(s + 0x9E3779B97F4A7C15L);
			int j = (int) Long.remainderUnsigned(s, i + 1);
			int t = p[i];
			p[i] = p[j];
			p[j] = t;
		}
		for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
	}

	// ---------------------------------------------------------------- Hashes

	public static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	public static long hash(long seed, long a) {
		return mix(seed ^ mix(a * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L));
	}

	public static long hash(long seed, long a, long b) {
		return hash(hash(seed, a), b);
	}

	public static long hash(long seed, long a, long b, long c) {
		return hash(hash(hash(seed, a), b), c);
	}

	/** Zufallswert in [0,1) aus einem Hash. */
	public static double unit(long h) {
		return (h >>> 11) * 0x1.0p-53;
	}

	// ---------------------------------------------------------------- Perlin

	private static double fade(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	private static double lerp(double t, double a, double b) {
		return a + t * (b - a);
	}

	private static double grad3(int hash, double x, double y, double z) {
		int h = hash & 15;
		double u = h < 8 ? x : y;
		double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
		return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
	}

	/** 3D Perlin Noise, ungefähr in [-1, 1]. */
	public double noise3(double x, double y, double z) {
		int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
		x -= xi;
		y -= yi;
		z -= zi;
		int X = xi & 255, Y = yi & 255, Z = zi & 255;
		double u = fade(x), v = fade(y), w = fade(z);
		int A = perm[X] + Y, AA = perm[A] + Z, AB = perm[A + 1] + Z;
		int B = perm[X + 1] + Y, BA = perm[B] + Z, BB = perm[B + 1] + Z;
		return lerp(w,
				lerp(v, lerp(u, grad3(perm[AA], x, y, z), grad3(perm[BA], x - 1, y, z)),
						lerp(u, grad3(perm[AB], x, y - 1, z), grad3(perm[BB], x - 1, y - 1, z))),
				lerp(v, lerp(u, grad3(perm[AA + 1], x, y, z - 1), grad3(perm[BA + 1], x - 1, y, z - 1)),
						lerp(u, grad3(perm[AB + 1], x, y - 1, z - 1), grad3(perm[BB + 1], x - 1, y - 1, z - 1))));
	}

	/** 2D Variante (Schnitt durch das 3D Noise). */
	public double noise2(double x, double z) {
		return noise3(x, 0.5, z);
	}

	/** Fraktales 2D Noise mit {@code octaves} Oktaven, ungefähr in [-1, 1]. */
	public double fbm2(double x, double z, int octaves) {
		double sum = 0, amp = 1, norm = 0;
		for (int i = 0; i < octaves; i++) {
			sum += noise2(x, z) * amp;
			norm += amp;
			amp *= 0.5;
			x *= 2.03;
			z *= 2.03;
		}
		return sum / norm;
	}
}
