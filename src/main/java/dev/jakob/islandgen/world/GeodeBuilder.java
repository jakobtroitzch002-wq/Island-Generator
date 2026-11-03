package dev.jakob.islandgen.world;

import dev.jakob.islandgen.layout.Geode;
import dev.jakob.islandgen.layout.Group;
import dev.jakob.islandgen.layout.Island;
import dev.jakob.islandgen.layout.Noise;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Baut die Lush-Geoden in die Hauptinseln. Alles wird pro Säule aus Seed und Position berechnet,
 * daher passt eine Geode, die über mehrere Chunks reicht, nahtlos zusammen.
 */
final class GeodeBuilder {
	private GeodeBuilder() {
	}

	private static final BlockState AIR = Blocks.AIR.defaultBlockState(),
			BASALT = Blocks.SMOOTH_BASALT.defaultBlockState(),
			CALCITE = Blocks.CALCITE.defaultBlockState(),
			AMETHYST = Blocks.AMETHYST_BLOCK.defaultBlockState(),
			BUDDING = Blocks.BUDDING_AMETHYST.defaultBlockState(),
			MOSS = Blocks.MOSS_BLOCK.defaultBlockState(),
			MOSS_CARPET = Blocks.MOSS_CARPET.defaultBlockState(),
			DIRT = Blocks.DIRT.defaultBlockState(),
			ROOTED_DIRT = Blocks.ROOTED_DIRT.defaultBlockState(),
			CLAY = Blocks.CLAY.defaultBlockState(),
			WATER = Blocks.WATER.defaultBlockState(),
			AZALEA = Blocks.AZALEA.defaultBlockState(),
			FLOWERING_AZALEA = Blocks.FLOWERING_AZALEA.defaultBlockState(),
			SHORT_GRASS = Blocks.SHORT_GRASS.defaultBlockState(),
			SPORE_BLOSSOM = Blocks.SPORE_BLOSSOM.defaultBlockState(),
			BIG_DRIPLEAF = Blocks.BIG_DRIPLEAF.defaultBlockState(),
			BIG_DRIPLEAF_STEM = Blocks.BIG_DRIPLEAF_STEM.defaultBlockState(),
			SMALL_DRIPLEAF_LOWER = Blocks.SMALL_DRIPLEAF.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
			SMALL_DRIPLEAF_UPPER = Blocks.SMALL_DRIPLEAF.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER),
			VINE_HEAD = Blocks.CAVE_VINES.defaultBlockState(),
			VINE_HEAD_BERRIES = Blocks.CAVE_VINES.defaultBlockState().setValue(BlockStateProperties.BERRIES, true),
			VINE_BODY = Blocks.CAVE_VINES_PLANT.defaultBlockState(),
			VINE_BODY_BERRIES = Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(BlockStateProperties.BERRIES, true);

	static void apply(ChunkAccess chunk, Heightmap of, Heightmap ws, Group g, int x0, int z0) {
		for (Island is : g.islands) {
			Geode geo = is.geode;
			if (geo == null) continue;
			if (geo.maxX() < x0 || geo.minX() > x0 + 15 || geo.maxZ() < z0 || geo.minZ() > z0 + 15) continue;
			for (int lz = 0; lz < 16; lz++) {
				for (int lx = 0; lx < 16; lx++) {
					int x = x0 + lx, z = z0 + lz;
					if (geo.inBox(x, z)) column(chunk, of, ws, geo, lx, lz, x, z);
				}
			}
		}
	}

	private static void column(ChunkAccess chunk, Heightmap of, Heightmap ws, Geode geo, int lx, int lz, int x, int z) {
		int y0 = Math.max(geo.minY(), chunk.getMinY() + 1), y1 = geo.maxY();
		int floorY = Integer.MAX_VALUE, ceilY = Integer.MIN_VALUE;
		int[] parts = new int[y1 - y0 + 1];
		for (int y = y0; y <= y1; y++) {
			int p = geo.part(x, y, z);
			parts[y - y0] = p;
			if (p == Geode.INSIDE) {
				floorY = Math.min(floorY, y);
				ceilY = Math.max(ceilY, y);
			}
		}

		// Schale: nur dort, wo die Insel Blöcke hat. Ragt die Geode aus der Insel, bleibt sie offen (aufgerissen).
		for (int y = y0; y <= y1; y++) {
			int p = parts[y - y0];
			if (p == Geode.NONE || p == Geode.INSIDE) continue;
			if (TerrainBuilder.get(chunk, lx, y, lz).isAir()) continue;
			BlockState s = switch (p) {
				case Geode.BASALT -> BASALT;
				case Geode.CALCITE -> CALCITE;
				default -> Noise.unit(Noise.hash(geo.seed, x, y, z)) < 0.07 ? BUDDING : AMETHYST;
			};
			TerrainBuilder.set(chunk, of, ws, lx, y, lz, s);
		}
		if (floorY == Integer.MAX_VALUE) return;

		// Innenraum
		int floor = geo.floorLevel;
		boolean pond = geo.isPond(x, z);
		for (int y = floorY; y <= ceilY; y++) {
			if (parts[y - y0] != Geode.INSIDE) continue;
			boolean solidBefore = !TerrainBuilder.get(chunk, lx, y, lz).isAir();
			BlockState s;
			if (y < floor) {
				// Boden: nur auffüllen, wo die Insel war (sonst bleibt das Loch eines Risses offen)
				if (!solidBefore) continue;
				if (pond) s = y >= floor - 2 ? WATER : CLAY;
				else s = y == floor - 1 ? MOSS : (Noise.unit(Noise.hash(geo.seed, x, y, z)) < 0.4 ? ROOTED_DIRT : DIRT);
			} else {
				s = AIR;
			}
			TerrainBuilder.set(chunk, of, ws, lx, y, lz, s);
		}

		long h = Noise.hash(geo.seed, x, z);
		double r1 = Noise.unit(h), r2 = Noise.unit(h >>> 13);

		// Pflanzen auf dem Moosboden (die Mitte bleibt frei)
		if (!pond && floor - 1 >= floorY && floor <= ceilY && !geo.isCenterSpot(x, z)
				&& TerrainBuilder.get(chunk, lx, floor - 1, lz) == MOSS && parts[floor - y0] == Geode.INSIDE) {
			boolean twoHigh = floor + 1 <= ceilY && parts[floor + 1 - y0] == Geode.INSIDE;
			if (r1 < 0.10) TerrainBuilder.set(chunk, of, ws, lx, floor, lz, AZALEA);
			else if (r1 < 0.16) TerrainBuilder.set(chunk, of, ws, lx, floor, lz, FLOWERING_AZALEA);
			else if (r1 < 0.24 && twoHigh) {
				TerrainBuilder.set(chunk, of, ws, lx, floor, lz, SMALL_DRIPLEAF_LOWER);
				TerrainBuilder.set(chunk, of, ws, lx, floor + 1, lz, SMALL_DRIPLEAF_UPPER);
			} else if (r1 < 0.30 && twoHigh) {
				TerrainBuilder.set(chunk, of, ws, lx, floor, lz, BIG_DRIPLEAF_STEM);
				TerrainBuilder.set(chunk, of, ws, lx, floor + 1, lz, BIG_DRIPLEAF);
			} else if (r1 < 0.42) TerrainBuilder.set(chunk, of, ws, lx, floor, lz, SHORT_GRASS);
			else if (r1 < 0.72) TerrainBuilder.set(chunk, of, ws, lx, floor, lz, MOSS_CARPET);
		}

		// Decke: Leuchtbeeren-Ranken und eine Sporenblüte über der Mitte
		if (ceilY > floor + 1 && !TerrainBuilder.get(chunk, lx, ceilY + 1, lz).isAir()) {
			if (geo.isCenterSpot(x, z) && Math.abs(x + 0.5 - geo.cx) < 0.6 && Math.abs(z + 0.5 - geo.cz) < 0.6) {
				TerrainBuilder.set(chunk, of, ws, lx, ceilY, lz, SPORE_BLOSSOM);
			} else if (r2 < 0.30) {
				int len = 1 + (int) (Noise.unit(h >>> 27) * 3);
				int bottom = Math.max(ceilY - len + 1, floor + 2);
				for (int y = ceilY; y >= bottom; y--) {
					boolean berries = Noise.unit(Noise.hash(geo.seed ^ 3, x, y, z)) < 0.55;
					BlockState s = y == bottom ? (berries ? VINE_HEAD_BERRIES : VINE_HEAD) : (berries ? VINE_BODY_BERRIES : VINE_BODY);
					TerrainBuilder.set(chunk, of, ws, lx, y, lz, s);
				}
			}
		}
	}
}
