package dev.jakob.islandgen.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verteilt Inselgruppen unregelmässig: Die Welt ist in Reihen (768 Blöcke hoch) geteilt, jede Reihe
 * ist um einen zufälligen Betrag seitlich verschoben, und jede Gruppe sitzt zufällig versetzt in ihrer Zelle.
 * So entsteht kein sichtbares Gitter. Mit max. 150 Blöcken Gruppenradius und max. 108 Blöcken Versatz
 * liegen zwischen zwei Gruppen mindestens ~250 und meist höchstens ~800 Blöcke Leere.
 */
public final class Layout {
	public static final int CELL = 768;
	public static final int HALF = CELL / 2;
	public static final int JITTER = 108;
	public static final double MAX_GROUP_REACH = 150;
	/** Wasserspiegel der Ozean-Lagunen (= Meeresspiegel des Generators - 1). */
	public static final int OCEAN_WATER = 62;
	/** Alle Inseln sind 20 % kleiner als ursprünglich geplant (ausser Start- und Stronghold-Insel). */
	public static final double SIZE = 0.8;

	private static volatile Layout overworldCache, netherCache;

	public final long seed;
	public final boolean nether;
	public final Noise noise;
	private final ConcurrentHashMap<Long, Group> groups = new ConcurrentHashMap<>();
	private final int strongholdCellX, strongholdCellZ;

	private Layout(long seed, boolean nether) {
		this.seed = seed;
		this.nether = nether;
		this.noise = new Noise(Noise.hash(seed, nether ? 0x4E45L : 0x4F57L));
		int pick = (int) Long.remainderUnsigned(Noise.hash(seed, 0x5354524FL), 16);
		int[][] ring = new int[16][];
		int n = 0;
		for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j++) {
			if (Math.max(Math.abs(i), Math.abs(j)) == 2) ring[n++] = new int[]{i, j};
		}
		strongholdCellX = nether ? Integer.MIN_VALUE : ring[pick][0];
		strongholdCellZ = nether ? Integer.MIN_VALUE : ring[pick][1];
	}

	public static Layout get(long seed, boolean nether) {
		Layout l = nether ? netherCache : overworldCache;
		if (l == null || l.seed != seed) {
			l = new Layout(seed, nether);
			if (nether) netherCache = l; else overworldCache = l;
		}
		return l;
	}

	/** Seitliche Verschiebung einer Reihe (Vielfaches von 16, damit Chunks nie zwei Zellen schneiden). */
	public int rowShift(int cz) {
		if (cz == 0) return 0; // Reihe mit der Startinsel bleibt am Ursprung
		return (int) Long.remainderUnsigned(Noise.hash(seed, 0x524F57L, cz), CELL / 16) * 16;
	}

	public int cellZ(int z) {
		return Math.floorDiv(z + HALF, CELL);
	}

	public int cellX(int x, int cz) {
		return Math.floorDiv(x - rowShift(cz) + HALF, CELL);
	}

	public Group groupAt(int x, int z) {
		int cz = cellZ(z);
		return group(cellX(x, cz), cz);
	}

	public Group group(int cx, int cz) {
		long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
		Group g = groups.get(key);
		if (g == null) {
			if (groups.size() > 4096) groups.clear();
			g = generate(cx, cz);
			Group prev = groups.putIfAbsent(key, g);
			if (prev != null) g = prev;
		}
		return g;
	}

	/** Grösse einer Region (in Zellen), in der genau eine Trial-Chamber-Insel liegt: 6 x 768 = ~4600 Blöcke. */
	public static final int TRIAL_REGION = 6;

	/** Zelle mit der Trial-Chamber-Insel in der Region (rx, rz), als {cx, cz}. */
	public int[] trialCell(int rx, int rz) {
		for (int attempt = 0; ; attempt++) {
			long h = Noise.hash(seed, 0x545249414CL + attempt, rx, rz);
			int cx = rx * TRIAL_REGION + (int) Long.remainderUnsigned(h, TRIAL_REGION);
			int cz = rz * TRIAL_REGION + (int) Long.remainderUnsigned(h >>> 20, TRIAL_REGION);
			if ((cx == 0 && cz == 0) || isStrongholdCell(cx, cz)) continue;
			return new int[]{cx, cz};
		}
	}

	public boolean isTrialCell(int cx, int cz) {
		if (nether) return false;
		int[] t = trialCell(Math.floorDiv(cx, TRIAL_REGION), Math.floorDiv(cz, TRIAL_REGION));
		return t[0] == cx && t[1] == cz;
	}

	public boolean isStrongholdCell(int cx, int cz) {
		return cx == strongholdCellX && cz == strongholdCellZ;
	}

	public int strongholdCellX() { return strongholdCellX; }
	public int strongholdCellZ() { return strongholdCellZ; }

	// ------------------------------------------------------------------ Generierung

	private Group generate(int cx, int cz) {
		SplittableRandom r = new SplittableRandom(Noise.hash(seed ^ (nether ? 0x4E45L : 0), cx, cz));
		double gx = cx * (double) CELL + rowShift(cz) + (r.nextDouble() * 2 - 1) * JITTER;
		double gz = cz * (double) CELL + (r.nextDouble() * 2 - 1) * JITTER;
		List<Island> list = new ArrayList<>();

		if (!nether && cx == 0 && cz == 0) {
			// Kleine Startinsel genau beim Spawn, alleine.
			list.add(new Island.Builder(Island.Kind.STARTER, 0, noise, Noise.hash(seed, 1, cx, cz))
					.at(0.5, 0.5, 20).height(120, 22).biome("minecraft:plains", null).hills(1.0).noPools().build());
			return new Group(cx, cz, null, 0.5, 0.5, list);
		}

		if (isStrongholdCell(cx, cz)) {
			// Eigene Stronghold-Insel: ein riesiger, tiefer Felsbrocken mit der Festung im Inneren.
			list.add(new Island.Builder(Island.Kind.STRONGHOLD, 0, noise, Noise.hash(seed, 2, cx, cz))
					.at(gx, gz, 74).height(118, 116).biome("minecraft:stony_peaks", null).hills(4).noPools().build());
			addSatellites(r, list, gx, gz, null, new String[]{"minecraft:stony_peaks"}, 118, 3 + r.nextInt(3));
			return new Group(cx, cz, null, gx, gz, list);
		}

		if (isTrialCell(cx, cz)) {
			// Seltene Trial-Chamber-Insel: ein grosser, tiefer Felsbrocken, in dem die Kammern stecken.
			list.add(new Island.Builder(Island.Kind.TRIAL, 0, noise, Noise.hash(seed, 3, cx, cz))
					.at(gx, gz, 66).height(124, 96).biome("minecraft:windswept_gravelly_hills", null).hills(5).noPools().build());
			addSatellites(r, list, gx, gz, null, new String[]{"minecraft:windswept_gravelly_hills"}, 124, 3 + r.nextInt(3));
			return new Group(cx, cz, null, gx, gz, list);
		}

		Theme[] pool = nether ? Theme.NETHER : Theme.OVERWORLD;
		Theme theme = pool[r.nextInt(pool.length)];
		boolean ocean = theme == Theme.OCEAN;

		int baseTop;
		if (nether) baseTop = 76 + r.nextInt(14);
		else if (ocean) baseTop = 65;
		else if (theme == Theme.MOUNTAIN) baseTop = 106 + r.nextInt(12);
		else baseTop = 112 + r.nextInt(22);
		int minBottom = nether ? 18 : 2;

		// --- Hauptinsel(n)
		int mains = 1 + (r.nextBoolean() ? 1 : 0);
		double r0 = theme.mainRadiusMin + r.nextDouble() * (theme.mainRadiusMax - theme.mainRadiusMin);
		// Ozean (Monument in der Lagune) und Dunkelwald (Waldanwesen) brauchen ihre Grösse, der Rest schrumpft.
		if (ocean) mains = 1;
		else r0 *= theme == Theme.DARK_FOREST ? 0.9 : SIZE;
		if (mains == 2 && theme != Theme.DARK_FOREST) r0 *= 0.9;
		String zone = null;
		double thick0;
		if (nether) {
			thick0 = 30 + r.nextInt(12);
		} else if (ocean) {
			thick0 = 56;
		} else {
			thick0 = 42 + r.nextInt(16);
			int roll = r.nextInt(100);
			if (roll < 14) zone = "minecraft:lush_caves";
			else if (roll < 28) zone = "minecraft:dripstone_caves";
			else if (roll < 38) { zone = "minecraft:deep_dark"; thick0 += 14; }
			else if (roll < 43) zone = "minecraft:sulfur_caves";
		}
		double m0x = gx + (r.nextDouble() * 2 - 1) * 10, m0z = gz + (r.nextDouble() * 2 - 1) * 10;
		// Bei zwei Hauptinseln wird das Paar um die Gruppenmitte zentriert.
		double r1 = (26 + r.nextDouble() * 11);
		double pairAngle = r.nextDouble() * Math.PI * 2;
		double pairDist = r0 * 1.12 + r1 * 1.12 + 4 + r.nextInt(8);
		double m1x = 0, m1z = 0;
		if (mains == 2) {
			double w0 = r1 / (r0 + r1), w1 = r0 / (r0 + r1);
			double cos = Math.cos(pairAngle), sin = Math.sin(pairAngle);
			m0x = gx - cos * pairDist * w0;
			m0z = gz - sin * pairDist * w0;
			m1x = gx + cos * pairDist * w1;
			m1z = gz + sin * pairDist * w1;
			// Passt das Paar nicht in den Gruppenradius, wird die zweite Insel weggelassen.
			if (Math.hypot(m0x - gx, m0z - gz) + r0 * 1.32 > MAX_GROUP_REACH
					|| Math.hypot(m1x - gx, m1z - gz) + r1 * 1.32 > MAX_GROUP_REACH) {
				mains = 1;
				m0x = gx;
				m0z = gz;
			}
		}
		Island.Builder b0 = new Island.Builder(Island.Kind.MAIN, 0, noise, r.nextLong())
				.at(m0x, m0z, r0).height(baseTop, thick0).biome(theme.mainBiome, theme)
				.underground(zone).nether(nether).hills(theme.hillAmp).minBottom(minBottom);
		if (ocean) b0.lagoon(OCEAN_WATER, 33, 0.68);
		list.add(b0.build());

		if (mains == 2) {
			{
				int top1 = clampTop(baseTop + r.nextInt(17) - 8, theme);
				Island.Builder b1 = new Island.Builder(Island.Kind.MAIN, 1, noise, r.nextLong())
						.at(m1x, m1z, r1).height(ocean ? 65 : top1, nether ? 30 + r.nextInt(10) : (ocean ? 46 : 40 + r.nextInt(14)))
						.biome(theme.mainBiome, theme).nether(nether).hills(theme.hillAmp).minBottom(minBottom);
				if (ocean) b1.lagoon(OCEAN_WATER, 48, 0.55);
				list.add(b1.build());
			}
		}

		// --- Nebeninseln: im Ring um die Hauptinsel(n), 5-30 Blöcke Abstand
		double mainExtent = 0;
		for (Island m : list) mainExtent = Math.max(mainExtent, Math.hypot(m.cx - gx, m.cz - gz) + m.radius * 1.12);
		int sides = 1 + r.nextInt(7);
		int sideIndex = 0;
		for (int k = 0; k < sides; k++) {
			double rs = Math.round((10 + r.nextInt(15)) * SIZE);
			String biome = theme.sideBiomes[r.nextInt(theme.sideBiomes.length)];
			for (int attempt = 0; attempt < 40; attempt++) {
				double a = r.nextDouble() * Math.PI * 2;
				double dist = mainExtent + rs * 1.12 + 3 + r.nextInt(10);
				double x = gx + Math.cos(a) * dist, z = gz + Math.sin(a) * dist;
				Island parent = nearest(list, x, z);
				if (Math.hypot(x - gx, z - gz) + rs * 1.32 > MAX_GROUP_REACH) continue;
				if (overlaps(list, x, z, rs, 4)) continue;
				int top = clampTop(parent.topY + r.nextInt(21) - 10, theme);
				double thick = rs * (1.1 + r.nextDouble() * 0.7) + 6;
				Island.Builder bs = new Island.Builder(Island.Kind.SIDE, sideIndex++, noise, r.nextLong())
						.at(x, z, rs).height(top, thick).biome(biome, theme).nether(nether)
						.hills(biome.equals("minecraft:stony_peaks") ? 24 : Math.min(theme.hillAmp, 6) * 0.6).minBottom(minBottom);
				if (ocean) {
					if (biome.equals("minecraft:warm_ocean")) bs.height(64, thick).lagoon(OCEAN_WATER, 55, 0.6);
					else bs.height(64 + r.nextInt(3), thick);
				}
				list.add(bs.build());
				break;
			}
		}

		// --- Satelliten
		addSatellites(r, list, gx, gz, theme, theme.sideBiomes, baseTop, 2 + r.nextInt(4));
		return new Group(cx, cz, theme, gx, gz, list);
	}

	private void addSatellites(SplittableRandom r, List<Island> list, double gx, double gz, Theme theme, String[] biomes, int baseTop, int count) {
		double extent = 0;
		for (Island i : list) extent = Math.max(extent, Math.hypot(i.cx - gx, i.cz - gz) + i.radius);
		int idx = 0;
		for (int k = 0; k < count; k++) {
			double rs = 3 + r.nextInt(4);
			for (int attempt = 0; attempt < 30; attempt++) {
				double a = r.nextDouble() * Math.PI * 2;
				double dist = Math.min(extent + 3 + r.nextInt(12), MAX_GROUP_REACH - rs * 1.32 - 1);
				double x = gx + Math.cos(a) * dist, z = gz + Math.sin(a) * dist;
				if (overlaps(list, x, z, rs, 3)) continue;
				int top;
				if (nether) top = Math.max(40, Math.min(98, baseTop + r.nextInt(41) - 20));
				else if (theme == Theme.OCEAN) top = 60 + r.nextInt(25);
				else top = Math.max(100, Math.min(150, baseTop + r.nextInt(51) - 25));
				String biome = biomes[r.nextInt(biomes.length)];
				if (biome.equals("minecraft:warm_ocean")) biome = "minecraft:beach";
				list.add(new Island.Builder(Island.Kind.SATELLITE, idx++, noise, r.nextLong())
						.at(x, z, rs).height(top, rs * 1.5 + 2 + r.nextInt(4)).biome(biome, theme)
						.nether(nether).hills(0.8).minBottom(nether ? 18 : 2).build());
				break;
			}
		}
	}

	private static Island nearest(List<Island> list, double x, double z) {
		Island best = list.get(0);
		for (Island i : list) if (i.kind == Island.Kind.MAIN && Math.hypot(i.cx - x, i.cz - z) < Math.hypot(best.cx - x, best.cz - z)) best = i;
		return best;
	}

	private static boolean overlaps(List<Island> list, double x, double z, double r, double gap) {
		for (Island i : list) {
			if (Math.hypot(i.cx - x, i.cz - z) < i.radius * 1.15 + r * 1.15 + gap) return true;
		}
		return false;
	}

	private int clampTop(int top, Theme theme) {
		if (nether) return Math.max(60, Math.min(95, top));
		if (theme == Theme.OCEAN) return 65;
		return Math.max(100, Math.min(145, top));
	}
}
