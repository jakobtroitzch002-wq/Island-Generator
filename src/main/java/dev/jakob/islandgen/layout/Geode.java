package dev.jakob.islandgen.layout;

/**
 * Eine Lush-Geode im Inneren einer Hauptinsel: aussen Basalt, Calcit und Amethyst wie eine normale Geode,
 * innen eine kleine, bewachsene Höhle mit Moosboden, Teich (mit Lehm für Axolotl), Azaleen,
 * Tropfblättern und Leuchtbeeren.
 * <p>
 * Die Geode sitzt immer im Zentrum der Insel, eine feste Anzahl Blöcke unter dem Inselsockel. Reicht sie
 * über den Inselrand hinaus, bleibt die Schale dort offen, die Geode ist dann aufgerissen.
 */
public final class Geode {
	/** Abstand Geodenmitte unter dem Inselsockel bzw. unter dem Lagunenboden. */
	public static final int DEPTH = 13, DEPTH_LAGOON = 11;

	public static final int NONE = 0, BASALT = 1, CALCITE = 2, AMETHYST = 3, INSIDE = 4;

	public final Island island;
	public final double cx, cy, cz;
	public final double rx, ry, rz;
	public final long seed;
	/** Oberkante des Moosbodens (erste Luftschicht über dem Boden). */
	public final int floorLevel;
	/** Mittelpunkt des Teichs. */
	public final double pondX, pondZ, pondR;
	private final Noise noise;
	private final double rMin;
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
		this.rMin = Math.min(rx, Math.min(ry, rz));
		this.floorLevel = (int) Math.floor(cy - ry * 0.35);
		double a = Noise.unit(Noise.hash(seed, 7)) * Math.PI * 2;
		this.pondR = 1.9;
		this.pondX = cx + Math.cos(a) * rx * 0.58;
		this.pondZ = cz + Math.sin(a) * rz * 0.58;
	}

	/** Normierter Abstand zur Mitte (1 = Innenwand), leicht verbeult. */
	public double q(double x, double y, double z) {
		double dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry, dz = (z + 0.5 - cz) / rz;
		double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
		return d + 0.07 * noise.noise3(x * 0.3 + 11.1, y * 0.3, z * 0.3 - 7.7);
	}

	/** Welcher Teil der Geode liegt an (x, y, z)? */
	public int part(int x, int y, int z) {
		double q = q(x, y, z);
		if (q < 1) return INSIDE;
		double out = (q - 1) * rMin; // ungefähre Entfernung zur Innenwand in Blöcken
		if (out < 1.0) return AMETHYST;
		if (out < 2.0) return CALCITE;
		if (out < 3.1) return BASALT;
		return NONE;
	}

	/** Grenzen (inkl. Schale) für schnelles Verwerfen. */
	public int minX() { return (int) Math.floor(cx - rx - 5); }
	public int maxX() { return (int) Math.ceil(cx + rx + 5); }
	public int minY() { return (int) Math.floor(cy - ry - 5); }
	public int maxY() { return (int) Math.ceil(cy + ry + 5); }
	public int minZ() { return (int) Math.floor(cz - rz - 5); }
	public int maxZ() { return (int) Math.ceil(cz + rz + 5); }

	public boolean inBox(int x, int z) {
		return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
	}

	/** Liegt (x, y, z) im Innenraum (mit kleinem Rand)? Für Mob-Spawns und Biom. */
	public boolean isInside(double x, double y, double z, double margin) {
		return q(x, y, z) < 1 + margin;
	}

	public boolean isPond(int x, int z) {
		double dx = x + 0.5 - pondX, dz = z + 0.5 - pondZ;
		return dx * dx + dz * dz < pondR * pondR && pondOk();
	}

	/** Der Teich wird nur angelegt, wenn der Boden darunter überall fest ist (sonst läuft Wasser in die Leere). */
	public boolean pondOk() {
		Boolean ok = pondOk;
		if (ok == null) {
			ok = true;
			Column c = new Column();
			for (int x = (int) Math.floor(pondX - pondR - 2); x <= pondX + pondR + 2 && ok; x++) {
				for (int z = (int) Math.floor(pondZ - pondR - 2); z <= pondZ + pondR + 2 && ok; z++) {
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
