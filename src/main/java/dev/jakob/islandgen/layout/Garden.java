package dev.jakob.islandgen.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Plan eines japanischen Gartens auf einer Kirschblüten-Insel: ein Weg durch eine Reihe roter Torii,
 * Steinlaternen, ein Teich mit roter Bogenbrücke, ein Zen-Garten aus Kies mit Felsen und ein Bambushain.
 * Nur Daten; gebaut wird im GardenBuilder.
 */
public final class Garden {
	/** Torii: Mitte (x, z), Weg entlang X ({@code alongX}) oder Z, Fuss der Balken auf {@code y}. */
	public record Torii(int x, int z, boolean alongX, int y) {
	}

	/** Gerader Weg von {@code from} bis {@code to} (entlang X oder Z) auf der Linie {@code fixed}. */
	public record Path(boolean alongX, int fixed, int from, int to) {
	}

	public record Spot(int x, int z, int r, int y) {
	}

	public final List<Torii> torii = new ArrayList<>();
	public final List<int[]> lanterns = new ArrayList<>();
	public Path path;
	/** Teich: y = Wasseroberfläche. */
	public Spot pond;
	public boolean bridgeAlongX;
	/** Zen-Garten: y = Kiesfläche. */
	public Spot zen;
	public final List<int[]> rocks = new ArrayList<>();
	public Spot bamboo;
	public int minX, maxX, minZ, maxZ;

	private final Island island;
	private final Column col = new Column();

	Garden(Island is) {
		this.island = is;
		SplittableRandom r = new SplittableRandom(Noise.hash(is.seed(), 0x4A4150414EL));
		int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
		int di = r.nextInt(4);
		int dx = dirs[di][0], dz = dirs[di][1];
		int cx = (int) Math.floor(is.cx), cz = (int) Math.floor(is.cz);

		// Weg mit einer Reihe Torii (wie in Fushimi Inari), von aussen bis kurz vor die Mitte
		int outer = (int) (is.radius * 0.72), inner = 7;
		boolean alongX = dx != 0;
		int a0 = alongX ? cx + dx * inner : cz + dz * inner, a1 = alongX ? cx + dx * outer : cz + dz * outer;
		path = new Path(alongX, alongX ? cz : cx, Math.min(a0, a1), Math.max(a0, a1));
		int gates = 3 + r.nextInt(3);
		for (int k = 0; k < gates; k++) {
			int d = outer - 2 - k * 4;
			if (d < inner + 2) break;
			int tx = cx + dx * d, tz = cz + dz * d;
			int y = Math.max(top(tx + (alongX ? 0 : 2), tz + (alongX ? 2 : 0)), top(tx - (alongX ? 0 : 2), tz - (alongX ? 2 : 0)));
			if (y == Integer.MIN_VALUE) continue;
			torii.add(new Torii(tx, tz, alongX, y));
		}
		// Laternen am Wegende und an beiden Seiten des Eingangs
		lantern(cx + dx * (inner + 1) + (alongX ? 0 : 3), cz + dz * (inner + 1) + (alongX ? 3 : 0));
		lantern(cx + dx * (inner + 1) - (alongX ? 0 : 3), cz + dz * (inner + 1) - (alongX ? 3 : 0));
		lantern(cx + dx * outer + (alongX ? 0 : 3), cz + dz * outer + (alongX ? 3 : 0));
		lantern(cx + dx * outer - (alongX ? 0 : 3), cz + dz * outer - (alongX ? 3 : 0));

		double base = Math.atan2(dz, dx);
		// Teich mit Brücke gegenüber vom Weg
		{
			double a = base + Math.PI + (r.nextDouble() - 0.5) * 0.8;
			int pr = 3 + r.nextInt(2);
			int px = (int) Math.floor(is.cx + Math.cos(a) * Math.max(14, is.radius * 0.45));
			int pz = (int) Math.floor(is.cz + Math.sin(a) * Math.max(14, is.radius * 0.45));
			int min = Integer.MAX_VALUE;
			boolean ok = true;
			for (int x = px - pr - 2; x <= px + pr + 2 && ok; x++) for (int z = pz - pr - 2; z <= pz + pr + 2; z++) {
				if (Math.hypot(x - px, z - pz) > pr + 2) continue;
				int t = top(x, z);
				if (t == Integer.MIN_VALUE || col.bottom > t - 8) {
					ok = false;
					break;
				}
				min = Math.min(min, t);
			}
			if (ok) {
				pond = new Spot(px, pz, pr, min - 1);
				bridgeAlongX = r.nextBoolean();
				lantern(px + pr + 2, pz + pr + 1);
			}
		}
		// Zen-Garten seitlich
		{
			double a = base + Math.PI / 2 + (r.nextDouble() - 0.5) * 0.6;
			int zr = 4 + r.nextInt(2);
			int zx = (int) Math.floor(is.cx + Math.cos(a) * Math.max(15, is.radius * 0.5));
			int zz = (int) Math.floor(is.cz + Math.sin(a) * Math.max(15, is.radius * 0.5));
			int t = top(zx, zz);
			if (t != Integer.MIN_VALUE && insideAll(zx, zz, zr + 1)) {
				zen = new Spot(zx, zz, zr, t);
				int n = 2 + r.nextInt(2);
				for (int i = 0; i < n; i++) {
					double ra = r.nextDouble() * Math.PI * 2, rd = r.nextDouble() * (zr - 1.5);
					rocks.add(new int[]{(int) Math.floor(zx + Math.cos(ra) * rd), (int) Math.floor(zz + Math.sin(ra) * rd), 1 + r.nextInt(2)});
				}
				lantern(zx - zr - 1, zz - zr - 1);
			}
		}
		// Bambushain auf der anderen Seite
		{
			double a = base - Math.PI / 2 + (r.nextDouble() - 0.5) * 0.6;
			int br = 3 + r.nextInt(2);
			int bx = (int) Math.floor(is.cx + Math.cos(a) * is.radius * 0.6);
			int bz = (int) Math.floor(is.cz + Math.sin(a) * is.radius * 0.6);
			if (top(bx, bz) != Integer.MIN_VALUE) bamboo = new Spot(bx, bz, br, 0);
		}
		// ein paar einzelne Laternen
		int extra = 2 + r.nextInt(3);
		for (int i = 0; i < extra; i++) {
			double a = r.nextDouble() * Math.PI * 2, d = 10 + r.nextDouble() * is.radius * 0.5;
			lantern((int) Math.floor(is.cx + Math.cos(a) * d), (int) Math.floor(is.cz + Math.sin(a) * d));
		}

		minX = maxX = cx;
		minZ = maxZ = cz;
		box(path.alongX ? path.from : path.fixed - 3, path.alongX ? path.fixed - 4 : path.from, 0);
		box(path.alongX ? path.to : path.fixed + 3, path.alongX ? path.fixed + 4 : path.to, 0);
		for (int[] l : lanterns) box(l[0], l[1], 0);
		if (pond != null) box(pond.x, pond.z, pond.r + 3);
		if (zen != null) box(zen.x, zen.z, zen.r + 2);
		if (bamboo != null) box(bamboo.x, bamboo.z, bamboo.r + 1);
	}

	private void box(int x, int z, int pad) {
		minX = Math.min(minX, x - pad - 4);
		maxX = Math.max(maxX, x + pad + 4);
		minZ = Math.min(minZ, z - pad - 4);
		maxZ = Math.max(maxZ, z + pad + 4);
	}

	/** Oberkante der Insel an (x, z), oder MIN_VALUE wenn dort nichts (oder Rand) ist. */
	private int top(int x, int z) {
		if (!island.sample(x, z, col) || col.island != island) return Integer.MIN_VALUE;
		if (Math.hypot(x + 0.5 - island.cx, z + 0.5 - island.cz) > island.radius * 0.8) return Integer.MIN_VALUE;
		return col.landTop;
	}

	private boolean insideAll(int x0, int z0, int r) {
		for (int x = x0 - r; x <= x0 + r; x++) for (int z = z0 - r; z <= z0 + r; z++) {
			if (Math.hypot(x - x0, z - z0) <= r && top(x, z) == Integer.MIN_VALUE) return false;
		}
		return true;
	}

	private void lantern(int x, int z) {
		int t = top(x, z);
		if (t == Integer.MIN_VALUE) return;
		if (pond != null && Math.hypot(x - pond.x, z - pond.z) < pond.r + 1.5) return;
		if (path != null && onPath(x, z, 2)) return;
		lanterns.add(new int[]{x, z});
	}

	public boolean onPath(int x, int z, int halfWidth) {
		int perp = path.alongX ? z - path.fixed : x - path.fixed;
		int along = path.alongX ? x : z;
		return Math.abs(perp) <= halfWidth && along >= path.from && along <= path.to;
	}

	public boolean inBox(int x, int z) {
		return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
	}
}
