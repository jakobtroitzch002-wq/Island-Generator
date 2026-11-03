package dev.jakob.islandgen.layout;

/**
 * Eine Lush-Geode im Inneren einer Hauptinsel. Aussen wie eine Geode (Basalt, Calcit, innere Schale),
 * innen eine kleine, bewachsene Höhle mit Moosboden, Teich (mit Lehm für Axolotl), Azaleen,
 * Tropfblättern und Leuchtbeeren.
 * <p>
 * Die Form ist bewusst unregelmässig: zwei bis drei verschmolzene, schief gestellte Blasen mit stark verbeulter
 * Wand und unebenem Boden. Jede Geode hat einen eigenen Stil für die innere Schale (Amethyst, überwachsen,
 * Tropfstein oder gemischt), sodass nicht alle gleich aussehen.
 * <p>
 * Die Geode sitzt immer im Zentrum der Insel, eine feste Anzahl Blöcke unter dem Inselsockel. Reicht sie
 * über den Inselrand hinaus, bleibt die Schale dort offen, die Geode ist dann aufgerissen.
 */
public final class Geode {
	/** Abstand Geodenmitte unter dem Inselsockel bzw. unter dem Lagunenboden. */
	public static final int DEPTH = 13, DEPTH_LAGOON = 11;

	public static final int NONE = 0, OUTER = 1, MIDDLE = 2, INNER = 3, INSIDE = 4;

	/** Stil der inneren Schale. */
	public enum Style { AMETHYST, OVERGROWN, DRIPSTONE, MIXED }

	public final Island island;
	public final double cx, cy, cz;
	public final double rx, ry, rz;
	public final long seed;
	public final Style style;
	/** Grundhöhe des Moosbodens (erste Luftschicht über dem Boden). Der Boden steigt davon aus unregelmässig an. */
	public final int floorLevel;
	/** Mittelpunkt des Teichs. */
	public final double pondX, pondZ, pondR;
	private final Noise noise;
	private final double cos, sin;
	// Zusatzblasen (relativ zur Mitte, im gedrehten System)
	private final double[] lx, ly, lz, lr;
	private final double maxExtent;
	private volatile Boolean pondOk;

	Geode(Island island, Noise noise, long seed, double cx, double cy, double cz, double rx, double ry, double rz) {
		this.island = island;
		this.noise = noise;
		this.seed = seed;
		this.cx = cx;
		this.cy = cy;
		this.cz = cz;
		this.rx = rx;
		this.ry = ry;
		this.rz = rz;
		double angle = Noise.unit(Noise.hash(seed, 1)) * Math.PI * 2;
		this.cos = Math.cos(angle);
		this.sin = Math.sin(angle);
		this.style = Style.values()[(int) (Noise.unit(Noise.hash(seed, 2)) * Style.values().length)];

		// 1-2 Nebenblasen, seitlich versetzt und etwas höher/tiefer: die Geode wird schief und bucklig
		int lobes = 2 + (Noise.unit(Noise.hash(seed, 3)) < 0.4 ? 1 : 0);
		lx = new double[lobes];
		ly = new double[lobes];
		lz = new double[lobes];
		lr = new double[lobes];
		double ext = Math.max(rx, Math.max(ry, rz));
		for (int i = 0; i < lobes; i++) {
			double a = Noise.unit(Noise.hash(seed, 10 + i)) * Math.PI * 2;
			double dist = 0.8 + 0.35 * Noise.unit(Noise.hash(seed, 20 + i));
			lx[i] = Math.cos(a) * rx * dist;
			lz[i] = Math.sin(a) * rz * dist;
			ly[i] = (Noise.unit(Noise.hash(seed, 30 + i)) - 0.35) * ry * 0.45;
			lr[i] = 0.62 + 0.25 * Noise.unit(Noise.hash(seed, 40 + i));
			ext = Math.max(ext, Math.hypot(lx[i], lz[i]) + rx * lr[i]);
		}
		this.maxExtent = ext;

		this.floorLevel = (int) Math.floor(cy - ry * 0.35);
		double a = Noise.unit(Noise.hash(seed, 7)) * Math.PI * 2;
		this.pondR = 1.8 + 0.5 * Noise.unit(Noise.hash(seed, 8));
		this.pondX = cx + Math.cos(a) * rx * 0.62;
		this.pondZ = cz + Math.sin(a) * rz * 0.62;
	}

	/** Normierter Abstand zur Innenwand (1 = Wand), mit Nebenblasen und stark verbeulter Wand. */
	public double q(double x, double y, double z) {
		double px = x + 0.5 - cx, py = y + 0.5 - cy, pz = z + 0.5 - cz;
		// drehen, damit die Ellipse schief in der Insel liegt
		double ux = px * cos - pz * sin, uz = px * sin + pz * cos;
		double d = len(ux / rx, py / ry, uz / rz);
		for (int i = 0; i < lx.length; i++) {
			double di = len((ux - lx[i]) / (rx * lr[i]), (py - ly[i]) / (ry * lr[i]), (uz - lz[i]) / (rz * lr[i]));
			d = smin(d, di, 0.5);
		}
		// grobe Beulen + feine Unebenheit
		d += 0.32 * noise.noise3(x * 0.16 + 11.1, y * 0.19, z * 0.16 - 7.7)
				+ 0.08 * noise.noise3(x * 0.5 - 3.3, y * 0.5 + 9.9, z * 0.5);
		return d;
	}

	private static double len(double a, double b, double c) {
		return Math.sqrt(a * a + b * b + c * c);
	}

	/** Weiches Minimum: Blasen verschmelzen ohne harte Kante. */
	private static double smin(double a, double b, double k) {
		double h = Math.max(k - Math.abs(a - b), 0) / k;
		return Math.min(a, b) - h * h * k * 0.25;
	}

	/** Welcher Teil der Geode liegt an (x, y, z)? Die Schalendicke schwankt. */
	public int part(int x, int y, int z) {
		double q = q(x, y, z);
		if (q < 1) return INSIDE;
		double out = (q - 1) * Math.min(rx, Math.min(ry, rz));
		// jede Schicht ist unterschiedlich dick, stellenweise fehlt eine fast ganz: keine gleichmässigen Ringe
		double t1 = Math.max(0.35, 1.0 + 1.0 * noise.noise3(x * 0.26 + 50, y * 0.26, z * 0.26 + 50));
		double t2 = t1 + Math.max(0.4, 1.0 + 0.9 * noise.noise3(x * 0.26 - 80, y * 0.26 + 30, z * 0.26));
		double t3 = t2 + Math.max(0.6, 1.1 + 0.7 * noise.noise3(x * 0.22, y * 0.22 - 60, z * 0.22 + 90));
		if (out < t1) return INNER;
		if (out < t2) return MIDDLE;
		if (out < t3) return OUTER;
		return NONE;
	}

	/** Fleckenrauschen für die Materialwahl der Schale (-1..1). */
	public double patch(int x, int y, int z) {
		return noise.noise3(x * 0.28 + 123.4, y * 0.28 - 45.6, z * 0.28 + 7.8);
	}

	/** Moosboden-Höhe dieser Säule: nie tiefer als floorLevel (hält den Teich dicht), stellenweise höher. */
	public int floorAt(int x, int z) {
		if (isPondArea(x, z) || isCenterSpot(x, z)) return floorLevel;
		double n = noise.noise2(x * 0.3 + 300, z * 0.3 - 300) + 0.4 * noise.noise2(x * 0.7 - 50, z * 0.7);
		return floorLevel + Math.max(0, (int) Math.round(n * 3.0));
	}

	/** Felssäulen und Brocken im Innenraum (nie in der Mitte oder im Teich). */
	public boolean isPillar(int x, int z) {
		if (isCenterSpot(x, z) || isPondArea(x, z)) return false;
		double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
		if (dx * dx + dz * dz < 2.6 * 2.6) return false;
		return noise.noise2(x * 0.36 + 900, z * 0.36 + 900) > 0.45;
	}

	/** Grenzen (inkl. Schale) für schnelles Verwerfen. */
	public int minX() { return (int) Math.floor(cx - maxExtent - 6); }
	public int maxX() { return (int) Math.ceil(cx + maxExtent + 6); }
	public int minY() { return (int) Math.floor(cy - ry * 1.4 - 6); }
	public int maxY() { return (int) Math.ceil(cy + ry * 1.4 + 6); }
	public int minZ() { return (int) Math.floor(cz - maxExtent - 6); }
	public int maxZ() { return (int) Math.ceil(cz + maxExtent + 6); }

	public boolean inBox(int x, int z) {
		return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
	}

	/** Liegt (x, y, z) im Innenraum (mit kleinem Rand)? Für Mob-Spawns und Biom. */
	public boolean isInside(double x, double y, double z, double margin) {
		return q(x, y, z) < 1 + margin;
	}

	private boolean isPondArea(int x, int z) {
		if (isCenterSpot(x, z)) return false;
		double dx = x + 0.5 - pondX, dz = z + 0.5 - pondZ;
		double r = pondR * (1 + 0.3 * noise.noise2(x * 0.5 + 77, z * 0.5 - 77));
		return dx * dx + dz * dz < r * r;
	}

	public boolean isPond(int x, int z) {
		return isPondArea(x, z) && pondOk();
	}

	/** Der Teich wird nur angelegt, wenn der Boden darunter überall fest ist (sonst läuft Wasser in die Leere). */
	public boolean pondOk() {
		Boolean ok = pondOk;
		if (ok == null) {
			ok = true;
			Column c = new Column();
			for (int x = (int) Math.floor(pondX - pondR * 1.3 - 2); x <= pondX + pondR * 1.3 + 2 && ok; x++) {
				for (int z = (int) Math.floor(pondZ - pondR * 1.3 - 2); z <= pondZ + pondR * 1.3 + 2 && ok; z++) {
					if (!island.sample(x, z, c) || c.bottom > floorLevel - 5) ok = false;
				}
			}
			pondOk = ok;
		}
		return ok;
	}

	/** Freifläche in der Mitte (hier kommt später der Wegstein hin). */
	public boolean isCenterSpot(int x, int z) {
		double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
		return dx * dx + dz * dz < 1.6 * 1.6;
	}
}
