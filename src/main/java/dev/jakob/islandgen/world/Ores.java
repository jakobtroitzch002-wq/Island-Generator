package dev.jakob.islandgen.world;

import dev.jakob.islandgen.layout.Island;
import dev.jakob.islandgen.layout.Theme;
import java.util.SplittableRandom;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Verteilt Erzadern innerhalb eines Chunks. Die Tiefe wird relativ zur Insel gemessen
 * (0 = Oberfläche, 1 = Unterseite), damit jede Insel - egal auf welcher Höhe - sinnvolle Erze hat.
 */
final class Ores {
	private Ores() {
	}

	private record Ore(BlockState normal, BlockState deep, int attempts, int minSize, int maxSize, double fMin, double fMax, boolean mainOnly, double chance) {
	}

	private static BlockState s(Block b) {
		return b.defaultBlockState();
	}

	private static final Ore[] OVERWORLD = {
			new Ore(s(Blocks.COAL_ORE), s(Blocks.DEEPSLATE_COAL_ORE), 18, 7, 14, 0.05, 0.7, false, 1),
			new Ore(s(Blocks.COPPER_ORE), s(Blocks.DEEPSLATE_COPPER_ORE), 9, 5, 11, 0.15, 0.75, false, 1),
			new Ore(s(Blocks.IRON_ORE), s(Blocks.DEEPSLATE_IRON_ORE), 13, 4, 9, 0.15, 1.0, false, 1),
			new Ore(s(Blocks.GOLD_ORE), s(Blocks.DEEPSLATE_GOLD_ORE), 4, 4, 8, 0.5, 1.0, false, 1),
			new Ore(s(Blocks.REDSTONE_ORE), s(Blocks.DEEPSLATE_REDSTONE_ORE), 5, 4, 8, 0.6, 1.0, false, 1),
			new Ore(s(Blocks.LAPIS_ORE), s(Blocks.DEEPSLATE_LAPIS_ORE), 3, 3, 7, 0.5, 0.95, false, 1),
			// Diamanten: sehr selten, nur tief in Haupt- und Stronghold-Inseln
			new Ore(s(Blocks.DIAMOND_ORE), s(Blocks.DEEPSLATE_DIAMOND_ORE), 1, 2, 5, 0.8, 1.0, true, 0.22),
	};
	private static final Ore EMERALD = new Ore(s(Blocks.EMERALD_ORE), s(Blocks.DEEPSLATE_EMERALD_ORE), 6, 1, 3, 0.0, 0.6, false, 1);
	private static final Ore GOLD_BADLANDS = new Ore(s(Blocks.GOLD_ORE), s(Blocks.DEEPSLATE_GOLD_ORE), 10, 4, 9, 0.1, 1.0, false, 1);

	private static final BlockState QUARTZ = s(Blocks.NETHER_QUARTZ_ORE), NETHER_GOLD = s(Blocks.NETHER_GOLD_ORE),
			DEBRIS = s(Blocks.ANCIENT_DEBRIS), GLOWSTONE = s(Blocks.GLOWSTONE);

	static void overworldOres(ChunkAccess chunk, SplittableRandom r, int occupied, int[] bottom, int[] top, Island[] islands) {
		double scale = occupied / 256.0;
		for (Ore ore : OVERWORLD) place(chunk, r, ore, scale, bottom, top, islands, false);
		Island any = firstIsland(islands);
		if (any != null && any.theme == Theme.MOUNTAIN) place(chunk, r, EMERALD, scale, bottom, top, islands, false);
		if (any != null && any.kind == Island.Kind.STRONGHOLD) place(chunk, r, EMERALD, scale * 0.5, bottom, top, islands, false);
		if (any != null && any.theme == Theme.BADLANDS) place(chunk, r, GOLD_BADLANDS, scale, bottom, top, islands, false);
	}

	static void netherOres(ChunkAccess chunk, SplittableRandom r, int occupied, int[] bottom, int[] top, Island[] islands) {
		double scale = occupied / 256.0;
		blobs(chunk, r, QUARTZ, (int) Math.round(16 * scale), 5, 12, 0.05, 1.0, false, bottom, top, islands, false);
		blobs(chunk, r, NETHER_GOLD, (int) Math.round(9 * scale), 4, 9, 0.05, 1.0, false, bottom, top, islands, false);
		if (r.nextDouble() < 0.25 * scale) blobs(chunk, r, DEBRIS, 1, 1, 3, 0.75, 1.0, true, bottom, top, islands, true);
		// Glowstone-Trauben an der Unterseite
		if (r.nextDouble() < 0.6 * scale) glowstone(chunk, r, bottom, top, islands);
	}

	private static Island firstIsland(Island[] islands) {
		for (Island i : islands) if (i != null) return i;
		return null;
	}

	private static void place(ChunkAccess chunk, SplittableRandom r, Ore ore, double scale, int[] bottom, int[] top, Island[] islands, boolean hard) {
		double expected = ore.attempts * scale * ore.chance;
		int attempts = (int) expected + (r.nextDouble() < expected - (int) expected ? 1 : 0);
		for (int a = 0; a < attempts; a++) {
			int lx = r.nextInt(16), lz = r.nextInt(16);
			int idx = lz * 16 + lx;
			Island is = islands[idx];
			if (is == null) continue;
			if (ore.mainOnly && is.kind != Island.Kind.MAIN && is.kind != Island.Kind.STRONGHOLD) continue;
			int t = top[idx], b = bottom[idx];
			if (t - b < 4) continue;
			double f = ore.fMin + r.nextDouble() * (ore.fMax - ore.fMin);
			int y = t - (int) Math.round(f * (t - b));
			int size = ore.minSize + r.nextInt(ore.maxSize - ore.minSize + 1);
			walk(chunk, r, lx, y, lz, size, ore.normal, ore.deep, false);
		}
	}

	private static void blobs(ChunkAccess chunk, SplittableRandom r, BlockState ore, int attempts, int minSize, int maxSize,
			double fMin, double fMax, boolean mainOnly, int[] bottom, int[] top, Island[] islands, boolean hard) {
		for (int a = 0; a < attempts; a++) {
			int lx = r.nextInt(16), lz = r.nextInt(16);
			int idx = lz * 16 + lx;
			Island is = islands[idx];
			if (is == null) continue;
			if (mainOnly && is.kind != Island.Kind.MAIN) continue;
			int t = top[idx], b = bottom[idx];
			if (t - b < 4) continue;
			double f = fMin + r.nextDouble() * (fMax - fMin);
			int y = t - (int) Math.round(f * (t - b));
			walk(chunk, r, lx, y, lz, minSize + r.nextInt(maxSize - minSize + 1), ore, null, hard);
		}
	}

	/** Zufallswanderung: ersetzt passende Gesteinsblöcke durch das Erz. */
	private static void walk(ChunkAccess chunk, SplittableRandom r, int x, int y, int z, int size, BlockState normal, BlockState deep, boolean netherHard) {
		for (int i = 0; i < size; i++) {
			if (x >= 0 && x < 16 && z >= 0 && z < 16 && y > chunk.getMinY() && y < chunk.getMinY() + chunk.getHeight()) {
				BlockState cur = TerrainBuilder.get(chunk, x, y, z);
				BlockState rep = null;
				if (deep != null) {
					if (Palette.isStoneLike(cur)) rep = normal;
					else if (Palette.isDeepslate(cur)) rep = deep;
				} else if (netherHard ? Palette.isNetherHard(cur) : Palette.isNetherBase(cur)) {
					rep = normal;
				}
				if (rep != null) chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x, y & 15, z, rep, false);
			}
			switch (r.nextInt(6)) {
				case 0 -> x++;
				case 1 -> x--;
				case 2 -> y++;
				case 3 -> y--;
				case 4 -> z++;
				default -> z--;
			}
		}
	}

	private static void glowstone(ChunkAccess chunk, SplittableRandom r, int[] bottom, int[] top, Island[] islands) {
		int lx = 2 + r.nextInt(12), lz = 2 + r.nextInt(12);
		int idx = lz * 16 + lx;
		if (islands[idx] == null) return;
		int y = bottom[idx] - 1;
		for (int i = 0; i < 18; i++) {
			int x = lx + r.nextInt(5) - 2, z = lz + r.nextInt(5) - 2, yy = y - r.nextInt(4);
			if (x < 0 || x > 15 || z < 0 || z > 15 || yy <= chunk.getMinY()) continue;
			int j = z * 16 + x;
			if (islands[j] == null || bottom[j] < yy) continue; // nur unterhalb der Insel anhängen
			if (bottom[j] - yy > 4) continue;
			for (int k = yy; k < bottom[j]; k++) {
				chunk.getSection(chunk.getSectionIndex(k)).setBlockState(x, k & 15, z, GLOWSTONE, false);
			}
		}
	}
}
