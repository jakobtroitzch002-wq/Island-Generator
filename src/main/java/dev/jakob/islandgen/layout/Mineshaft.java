package dev.jakob.islandgen.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Plan eines verlassenen Minenschachts in einer Tafelberg-Insel: ein tiefes Stollennetz unter der Geode,
 * ein Leiterschacht nach oben und Stollen auf Höhe der Tafelberge, die in den Felswänden enden.
 * Nur Daten; gebaut wird im MineshaftBuilder.
 */
public final class Mineshaft {
	/** Ein gerader Stollen entlang X ({@code alongX}) oder Z, Mittellinie bei {@code fixed}, Boden auf {@code y}. */
	public record Seg(boolean alongX, int fixed, int from, int to, int y) {
		public boolean contains(int x, int z, int widen) {
			int perp = alongX ? z - fixed : x - fixed;
			int along = alongX ? x : z;
			return Math.abs(perp) <= 1 && along >= from - widen && along <= to + widen;
		}
	}

	public final List<Seg> segs = new ArrayList<>();
	/** Truhen {x, y, z}. */
	public final List<int[]> chests = new ArrayList<>();
	/** Höhlenspinnen-Spawner {x, y, z} oder null. */
	public int[] spawner;
	public final int hubX, hubZ, deepY, upY;
	public int minX, maxX, minZ, maxZ;

	private final Island island;
	private final SplittableRandom r;

	Mineshaft(Island is) {
		this.island = is;
		this.r = new SplittableRandom(Noise.hash(is.seed(), 0x4D494E45L));
		this.deepY = is.topY - 24;
		this.upY = is.topY + 2;
		// Schacht: nahe der Mitte (dort ist die Insel dick genug), aber nicht durch die Geode
		double a0 = r.nextDouble() * Math.PI * 2;
		int hx = 0, hz = 0;
		for (int tryNo = 0; tryNo < 24; tryNo++) {
			double a = a0 + tryNo * 0.9;
			double dist = 12 + tryNo * 0.4;
			hx = (int) Math.floor(is.cx + Math.cos(a) * dist);
			hz = (int) Math.floor(is.cz + Math.sin(a) * dist);
			if (shaftClear(hx, hz)) break;
		}
		this.hubX = hx;
		this.hubZ = hz;

		// Tiefes Netz: vom Schacht aus in alle Richtungen, mit Abzweigungen
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] d : dirs) {
			if (r.nextDouble() < 0.85) grow(hubX, hubZ, d[0], d[1], deepY, 0);
		}
		// Stollen auf Höhe der Tafelberge (entstehen nur dort, wo Fels steht, und enden in den Wänden)
		int ups = 4 + r.nextInt(3);
		for (int i = 0; i < ups; i++) {
			double pa = r.nextDouble() * Math.PI * 2, pd = Math.sqrt(r.nextDouble()) * is.radius * 0.65;
			int px = (int) Math.floor(is.cx + Math.cos(pa) * pd), pz = (int) Math.floor(is.cz + Math.sin(pa) * pd);
			int half = (int) (is.radius * (0.5 + r.nextDouble() * 0.4));
			int y = upY + (r.nextInt(3) == 0 ? 7 : 0);
			if (r.nextBoolean()) segs.add(new Seg(true, pz, px - half, px + half, y));
			else segs.add(new Seg(false, px, pz - half, pz + half, y));
		}
		// Spawner mitten in einem tiefen Stollen
		List<Seg> deep = segs.stream().filter(s -> s.y == deepY && s.to - s.from >= 10).toList();
		if (!deep.isEmpty()) {
			Seg s = deep.get(r.nextInt(deep.size()));
			int along = (s.from + s.to) / 2;
			spawner = s.alongX ? new int[]{along, deepY, s.fixed} : new int[]{s.fixed, deepY, along};
		}

		minX = maxX = hubX;
		minZ = maxZ = hubZ;
		for (Seg s : segs) {
			int x0 = s.alongX ? s.from : s.fixed - 1, x1 = s.alongX ? s.to : s.fixed + 1;
			int z0 = s.alongX ? s.fixed - 1 : s.from, z1 = s.alongX ? s.fixed + 1 : s.to;
			minX = Math.min(minX, x0 - 2);
			maxX = Math.max(maxX, x1 + 2);
			minZ = Math.min(minZ, z0 - 2);
			maxZ = Math.max(maxZ, z1 + 2);
		}
	}

	private final Column col = new Column();

	/** Ist die Insel hier dick genug für einen Stollen (und weit genug weg von der Geode)? */
	private boolean inside(int x, int z) {
		if (!island.sample(x, z, col) || col.island != island) return false;
		if (col.bottom > deepY - 3 || col.landTop < deepY + 6) return false;
		return island.geode == null || island.geode.q(x, deepY + 2, z) > 3.4;
	}

	private boolean shaftClear(int hx, int hz) {
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
			if (!inside(hx + dx, hz + dz)) return false;
			if (island.geode != null) {
				for (int y = deepY; y <= island.topY + 2; y++) if (island.geode.q(hx + dx, y, hz + dz) < 3.6) return false;
			}
		}
		return true;
	}

	private void grow(int x, int z, int dx, int dz, int y, int depth) {
		int len = 12 + r.nextInt(20);
		int n = 0;
		while (n < len && inside(x + dx * (n + 1), z + dz * (n + 1))) n++;
		if (n < 5) return;
		int ex = x + dx * n, ez = z + dz * n;
		if (dx != 0) segs.add(new Seg(true, z, Math.min(x, ex), Math.max(x, ex), y));
		else segs.add(new Seg(false, x, Math.min(z, ez), Math.max(z, ez), y));
		if (r.nextDouble() < 0.5) chests.add(new int[]{ex, y, ez});
		if (depth < 2) {
			// Abzweigungen am Ende und manchmal unterwegs
			if (r.nextDouble() < 0.55) grow(ex, ez, dz, dx, y, depth + 1);
			if (r.nextDouble() < 0.55) grow(ex, ez, -dz, -dx, y, depth + 1);
			if (n > 12 && r.nextDouble() < 0.4) {
				int m = 4 + r.nextInt(n - 8);
				int mx = x + dx * m, mz = z + dz * m;
				if (r.nextBoolean()) grow(mx, mz, dz, dx, y, depth + 1);
				else grow(mx, mz, -dz, -dx, y, depth + 1);
			}
		}
	}

	public boolean inBox(int x, int z) {
		return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
	}

	public boolean isShaft(int x, int z) {
		return Math.abs(x - hubX) <= 1 && Math.abs(z - hubZ) <= 1;
	}
}
