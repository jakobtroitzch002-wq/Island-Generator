package dev.jakob.islandgen.world;

import dev.jakob.islandgen.layout.Column;
import dev.jakob.islandgen.layout.Group;
import dev.jakob.islandgen.layout.Island;
import dev.jakob.islandgen.layout.Layout;
import dev.jakob.islandgen.layout.Noise;
import dev.jakob.islandgen.layout.Theme;
import java.util.SplittableRandom;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Füllt einen Chunk mit Inselblöcken. Chunks ohne Insel werden sofort übersprungen (Leere kostet nichts).
 */
public final class TerrainBuilder {
	private TerrainBuilder() {
	}

	private static final ThreadLocal<Column> COLUMN = ThreadLocal.withInitial(Column::new);

	public static void fill(ChunkAccess chunk, Layout layout, StructureManager structures) {
		fillIslands(chunk, layout);
		StructurePadding.apply(chunk, layout, structures);
	}

	private static void fillIslands(ChunkAccess chunk, Layout layout) {
		ChunkPos pos = chunk.getPos();
		int x0 = pos.getMinBlockX(), z0 = pos.getMinBlockZ();
		Group g = layout.groupAt(x0, z0);
		if (g.isEmpty() || !g.intersects(x0, z0, x0 + 15, z0 + 15)) return;

		Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		Noise n = layout.noise;
		Column c = COLUMN.get();

		int[] colBottom = new int[256];
		int[] colTop = new int[256];
		Island[] colIsland = new Island[256];
		int occupied = 0;

		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int x = x0 + lx, z = z0 + lz;
				if (!g.sample(x, z, c)) continue;
				Island is = c.island;
				Palette.Style st = Palette.style(is.biome);
				int idx = lz * 16 + lx;
				colBottom[idx] = c.bottom;
				colTop[idx] = c.landTop;
				colIsland[idx] = is;
				occupied++;

				boolean caves = is.hasCaves() && c.t < 0.8 && c.landTop - c.bottom > 28;
				int caveTop = Math.min(c.landTop - 7, c.solidTop - 6);
				for (int y = c.bottom; y <= c.solidTop; y++) {
					if (caves && y > c.bottom + 10 && y < caveTop && isCave(n, is, x, y, z)) continue;
					set(chunk, oceanFloor, worldSurface, lx, y, lz, Palette.block(c, st, n, x, y, z));
				}
				if (c.fluid != Column.FLUID_NONE) {
					BlockState f = c.fluid == Column.FLUID_LAVA ? Palette.LAVA : Palette.WATER;
					for (int y = c.solidTop + 1; y <= c.fluidTop; y++) set(chunk, oceanFloor, worldSurface, lx, y, lz, f);
				}
			}
		}
		if (occupied == 0) return;

		SplittableRandom rnd = new SplittableRandom(Noise.hash(layout.seed ^ 0x0BE5L, x0 >> 4, z0 >> 4));
		if (layout.nether) {
			Ores.netherOres(chunk, rnd, occupied, colBottom, colTop, colIsland);
		} else {
			Ores.overworldOres(chunk, rnd, occupied, colBottom, colTop, colIsland);
		}

		// Startinsel: ein Baum und ein kleiner Wasserteich (die Mitte bleibt frei für das Camp).
		for (Island is : g.islands) {
			if (is.kind == Island.Kind.STARTER) decorateStarter(chunk, oceanFloor, worldSurface, layout, c, is, x0, z0);
		}
	}

	static boolean isCave(Noise n, Island is, int x, int y, int z) {
		double a = n.noise3(x / 24.0, y / 14.0, z / 24.0);
		double b = n.noise3(x / 24.0 + 71.3, y / 14.0 + 13.1, z / 24.0 + 29.7);
		if (a * a + b * b < 0.010) return true;
		if (is.underground != null) {
			return n.noise3(x / 34.0 + 200.5, y / 20.0, z / 34.0 - 80.1) > 0.40;
		}
		return false;
	}

	static void set(ChunkAccess chunk, Heightmap oceanFloor, Heightmap worldSurface, int lx, int y, int lz, BlockState state) {
		LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
		section.setBlockState(lx, y & 15, lz, state, false);
		oceanFloor.update(lx, y, lz, state);
		worldSurface.update(lx, y, lz, state);
	}

	static BlockState get(ChunkAccess chunk, int lx, int y, int lz) {
		LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
		return section.getBlockState(lx, y & 15, lz);
	}

	private static void decorateStarter(ChunkAccess chunk, Heightmap of, Heightmap ws, Layout layout, Column c, Island is, int x0, int z0) {
		BlockState log, leaves;
		if (is.biome.contains("jungle")) {
			log = Blocks.JUNGLE_LOG.defaultBlockState();
			leaves = Blocks.JUNGLE_LEAVES.defaultBlockState();
		} else if (is.biome.contains("cherry")) {
			log = Blocks.CHERRY_LOG.defaultBlockState();
			leaves = Blocks.CHERRY_LEAVES.defaultBlockState();
		} else {
			log = Blocks.OAK_LOG.defaultBlockState();
			leaves = Blocks.OAK_LEAVES.defaultBlockState();
		}
		leaves = leaves.setValue(LeavesBlock.PERSISTENT, true);
		// Baum bei (-8, -8)
		int tx = -8, tz = -8;
		if (layout.groupAt(tx, tz).sample(tx, tz, c)) {
			int y = c.solidTop + 1;
			for (int dy = -2; dy <= 1; dy++) {
				int r = dy >= 0 ? 1 : 2;
				for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) == r && Math.abs(dz) == r && (dy == 1 || r == 2 && dy == -1)) continue;
					put(chunk, of, ws, x0, z0, tx + dx, y + 4 + dy, tz + dz, leaves);
				}
			}
			for (int i = 0; i < 5; i++) put(chunk, of, ws, x0, z0, tx, y + i, tz, log);
			put(chunk, of, ws, x0, z0, tx, c.solidTop, tz, Blocks.DIRT.defaultBlockState());
		}
		// 2x2 Wasser bei (-10..-9, 7..8): unendliche Wasserquelle
		if (layout.groupAt(-10, 7).sample(-10, 7, c)) {
			int y = c.solidTop;
			for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) {
				put(chunk, of, ws, x0, z0, -10 + dx, y, 7 + dz, Palette.WATER);
				put(chunk, of, ws, x0, z0, -10 + dx, y - 1, 7 + dz, Blocks.CLAY.defaultBlockState());
			}
		}
	}

	/** Setzt einen Block in Weltkoordinaten, aber nur wenn er in diesem Chunk liegt. */
	private static void put(ChunkAccess chunk, Heightmap of, Heightmap ws, int x0, int z0, int x, int y, int z, BlockState state) {
		int lx = x - x0, lz = z - z0;
		if (lx < 0 || lx > 15 || lz < 0 || lz > 15) return;
		set(chunk, of, ws, lx, y, lz, state);
	}

	/** Hilfsfunktion für getBaseColumn / getBaseHeight. */
	public static BlockState stateAt(Layout layout, int x, int y, int z) {
		Column c = COLUMN.get();
		Group g = layout.groupAt(x, z);
		if (!g.sample(x, z, c)) return Palette.AIR;
		if (y < c.bottom) return Palette.AIR;
		if (y <= c.solidTop) {
			Island is = c.island;
			int caveTop = Math.min(c.landTop - 7, c.solidTop - 6);
			if (is.hasCaves() && c.t < 0.8 && c.landTop - c.bottom > 28 && y > c.bottom + 10 && y < caveTop && isCave(layout.noise, is, x, y, z)) return Palette.AIR;
			return Palette.block(c, Palette.style(is.biome), layout.noise, x, y, z);
		}
		if (c.fluid != Column.FLUID_NONE && y <= c.fluidTop) return c.fluid == Column.FLUID_LAVA ? Palette.LAVA : Palette.WATER;
		return Palette.AIR;
	}

	/** Ganze Säule auf einmal (für getBaseColumn). */
	public static BlockState[] columnStates(Layout layout, int x, int z, int minY, int depth) {
		BlockState[] out = new BlockState[depth];
		java.util.Arrays.fill(out, Palette.AIR);
		Column c = COLUMN.get();
		if (!layout.groupAt(x, z).sample(x, z, c)) return out;
		Island is = c.island;
		Palette.Style st = Palette.style(is.biome);
		boolean caves = is.hasCaves() && c.t < 0.8 && c.landTop - c.bottom > 28;
		int caveTop = Math.min(c.landTop - 7, c.solidTop - 6);
		for (int y = Math.max(c.bottom, minY); y <= c.solidTop && y < minY + depth; y++) {
			if (caves && y > c.bottom + 10 && y < caveTop && isCave(layout.noise, is, x, y, z)) continue;
			out[y - minY] = Palette.block(c, st, layout.noise, x, y, z);
		}
		if (c.fluid != Column.FLUID_NONE) {
			BlockState f = c.fluid == Column.FLUID_LAVA ? Palette.LAVA : Palette.WATER;
			for (int y = c.solidTop + 1; y <= c.fluidTop && y < minY + depth; y++) out[y - minY] = f;
		}
		return out;
	}

	/** Höhe direkt über dem obersten Block (fest oder flüssig bzw. nur fest), oder {@code minY} wenn leer. */
	public static int surfaceHeight(Layout layout, int x, int z, boolean solidOnly, int minY) {
		Column c = COLUMN.get();
		if (!layout.groupAt(x, z).sample(x, z, c)) return minY;
		return (solidOnly ? c.solidTop : c.highest()) + 1;
	}

	public static Column sampleColumn(Layout layout, int x, int z) {
		Column c = COLUMN.get();
		layout.groupAt(x, z).sample(x, z, c);
		return c;
	}

	static boolean isMountainTheme(Island is) {
		return is.theme == Theme.MOUNTAIN || is.kind == Island.Kind.STRONGHOLD;
	}
}
