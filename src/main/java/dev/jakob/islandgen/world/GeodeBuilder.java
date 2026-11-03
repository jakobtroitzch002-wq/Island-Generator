package dev.jakob.islandgen.world;

import dev.jakob.islandgen.layout.Geode;
import dev.jakob.islandgen.layout.Group;
import dev.jakob.islandgen.layout.Island;
import dev.jakob.islandgen.layout.Noise;
import net.minecraft.core.Direction;
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
			TUFF = Blocks.TUFF.defaultBlockState(),
			CALCITE = Blocks.CALCITE.defaultBlockState(),
			AMETHYST = Blocks.AMETHYST_BLOCK.defaultBlockState(),
			BUDDING = Blocks.BUDDING_AMETHYST.defaultBlockState(),
			DRIPSTONE = Blocks.DRIPSTONE_BLOCK.defaultBlockState(),
			MOSS = Blocks.MOSS_BLOCK.defaultBlockState(),
			MOSS_CARPET = Blocks.MOSS_CARPET.defaultBlockState(),
			DIRT = Blocks.DIRT.defaultBlockState(),
			ROOTED_DIRT = Blocks.ROOTED_DIRT.defaultBlockState(),
			CLAY = Blocks.CLAY.defaultBlockState(),
			WATER = Blocks.WATER.defaultBlockState(),
			AZALEA = Blocks.AZALEA.defaultBlockState(),
			FLOWERING_AZALEA = Blocks.FLOWERING_AZALEA.defaultBlockState(),
			SHORT_GRASS = Blocks.SHORT_GRASS.defaultBlockState(),
			FERN = Blocks.FERN.defaultBlockState(),
			SPORE_BLOSSOM = Blocks.SPORE_BLOSSOM.defaultBlockState(),
			HANGING_ROOTS = Blocks.HANGING_ROOTS.defaultBlockState(),
			BIG_DRIPLEAF = Blocks.BIG_DRIPLEAF.defaultBlockState(),
			BIG_DRIPLEAF_STEM = Blocks.BIG_DRIPLEAF_STEM.defaultBlockState(),
			SMALL_DRIPLEAF_LOWER = Blocks.SMALL_DRIPLEAF.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
			SMALL_DRIPLEAF_UPPER = Blocks.SMALL_DRIPLEAF.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER),
			VINE_HEAD = Blocks.CAVE_VINES.defaultBlockState(),
			VINE_HEAD_BERRIES = Blocks.CAVE_VINES.defaultBlockState().setValue(BlockStateProperties.BERRIES, true),
			VINE_BODY = Blocks.CAVE_VINES_PLANT.defaultBlockState(),
			VINE_BODY_BERRIES = Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(BlockStateProperties.BERRIES, true),
			DRIP_TIP_DOWN = Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN),
			CLUSTER_DOWN = Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN),
			BUD_DOWN = Blocks.MEDIUM_AMETHYST_BUD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN);

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

	/** Material der Schale je nach Schicht, Stil der Geode und Flecken. */
	private static BlockState shell(Geode geo, int part, int x, int y, int z) {
		double p = geo.patch(x, y, z);
		double u = Noise.unit(Noise.hash(geo.seed, x, y, z));
		if (part == Geode.OUTER) return p < -0.45 ? TUFF : BASALT;
		if (part == Geode.MIDDLE) {
			if (geo.style == Geode.Style.DRIPSTONE && p > 0.2) return DRIPSTONE;
			return p < -0.5 ? TUFF : CALCITE;
		}
		// innere Schale: unten am Boden immer überwachsen
		boolean low = y < geo.floorLevel + 1;
		if (low && p > -0.6) return u < 0.3 ? ROOTED_DIRT : MOSS;
		return switch (geo.style) {
			case AMETHYST -> p > 0.42 ? CALCITE : (p < -0.5 ? MOSS : (u < 0.07 ? BUDDING : AMETHYST));
			case OVERGROWN -> p > 0.5 ? (u < 0.1 ? BUDDING : AMETHYST) : (p < -0.25 ? ROOTED_DIRT : (u < 0.15 ? CLAY : MOSS));
			case DRIPSTONE -> p > 0.45 ? (u < 0.08 ? BUDDING : AMETHYST) : (p < -0.35 ? MOSS : (u < 0.25 ? CALCITE : DRIPSTONE));
			case MIXED -> p < -0.2 ? (u < 0.07 ? BUDDING : AMETHYST) : (p < 0.25 ? MOSS : (u < 0.5 ? CALCITE : DRIPSTONE));
		};
	}

	private static void column(ChunkAccess chunk, Heightmap of, Heightmap ws, Geode geo, int lx, int lz, int x, int z) {
		int y0 = Math.max(geo.minY(), chunk.getMinY() + 1), y1 = geo.maxY();
		int lowY = Integer.MAX_VALUE, ceilY = Integer.MIN_VALUE;
		int[] parts = new int[y1 - y0 + 1];
		for (int y = y0; y <= y1; y++) {
			int p = geo.part(x, y, z);
			parts[y - y0] = p;
			if (p == Geode.INSIDE) {
				lowY = Math.min(lowY, y);
				ceilY = Math.max(ceilY, y);
			}
		}

		// Schale: nur dort, wo die Insel Blöcke hat. Ragt die Geode aus der Insel, bleibt sie offen (aufgerissen).
		for (int y = y0; y <= y1; y++) {
			int p = parts[y - y0];
			if (p == Geode.NONE || p == Geode.INSIDE) continue;
			if (TerrainBuilder.get(chunk, lx, y, lz).isAir()) continue;
			TerrainBuilder.set(chunk, of, ws, lx, y, lz, shell(geo, p, x, y, z));
		}
		if (lowY == Integer.MAX_VALUE) return;

		// Innenraum mit unebenem Moosboden
		int floor = geo.floorAt(x, z);
		boolean pond = geo.isPond(x, z);
		for (int y = lowY; y <= ceilY; y++) {
			if (parts[y - y0] != Geode.INSIDE) continue;
			boolean solidBefore = !TerrainBuilder.get(chunk, lx, y, lz).isAir();
			BlockState s;
			if (y < floor) {
				// Boden nur auffüllen, wo die Insel war (sonst bleibt das Loch eines Risses offen)
				if (!solidBefore) continue;
				if (pond) s = y >= floor - 2 ? WATER : CLAY;
				else s = y == floor - 1 ? MOSS : (Noise.unit(Noise.hash(geo.seed, x, y, z)) < 0.4 ? ROOTED_DIRT : DIRT);
			} else {
				s = AIR;
			}
			TerrainBuilder.set(chunk, of, ws, lx, y, lz, s);
		}

		long h = Noise.hash(geo.seed, x, z);
		double r1 = Noise.unit(h), r2 = Noise.unit(h >>> 13), r3 = Noise.unit(h >>> 29);

		// Pflanzen auf dem Moosboden (die Mitte bleibt frei)
		if (!pond && floor - 1 >= lowY && floor <= ceilY && !geo.isCenterSpot(x, z)
				&& TerrainBuilder.get(chunk, lx, floor - 1, lz) == MOSS && parts[floor - y0] == Geode.INSIDE) {
			boolean twoHigh = floor + 1 <= ceilY && parts[floor + 1 - y0] == Geode.INSIDE;
			if (r1 < 0.11) TerrainBuilder.set(chunk, of, ws, lx, floor, lz, AZALEA);
			else if (r1 < 0.17) TerrainBuilder.set(chunk, of, ws, lx, floor, lz, FLOWERING_AZALEA);
			else if (r1 < 0.24 && twoHigh) {
				TerrainBuilder.set(chunk, of, ws, lx, floor, lz, SMALL_DRIPLEAF_LOWER);
				TerrainBuilder.set(chunk, of, ws, lx, floor + 1, lz, SMALL_DRIPLEAF_UPPER);
			} else if (r1 < 0.30 && twoHigh) {
				TerrainBuilder.set(chunk, of, ws, lx, floor, lz, BIG_DRIPLEAF_STEM);
				TerrainBuilder.set(chunk, of, ws, lx, floor + 1, lz, BIG_DRIPLEAF);
			} else if (r1 < 0.40) TerrainBuilder.set(chunk, of, ws, lx, floor, lz, SHORT_GRASS);
			else if (r1 < 0.46) TerrainBuilder.set(chunk, of, ws, lx, floor, lz, FERN);
			else if (r1 < 0.74) TerrainBuilder.set(chunk, of, ws, lx, floor, lz, MOSS_CARPET);
		}

		// Decke: je nach Material Ranken, Wurzeln, Tropfsteine oder Amethyst-Kristalle
		if (ceilY > floor + 1) {
			BlockState above = TerrainBuilder.get(chunk, lx, ceilY + 1, lz);
			if (above.isAir()) return;
			if (Math.abs(x + 0.5 - geo.cx) < 0.6 && Math.abs(z + 0.5 - geo.cz) < 0.6) {
				TerrainBuilder.set(chunk, of, ws, lx, ceilY, lz, SPORE_BLOSSOM);
			} else if ((above == AMETHYST || above == BUDDING) && r3 < 0.18) {
				TerrainBuilder.set(chunk, of, ws, lx, ceilY, lz, r3 < 0.08 ? CLUSTER_DOWN : BUD_DOWN);
			} else if (above == DRIPSTONE && r3 < 0.35) {
				TerrainBuilder.set(chunk, of, ws, lx, ceilY, lz, DRIP_TIP_DOWN);
			} else if ((above == ROOTED_DIRT) && r3 < 0.5) {
				TerrainBuilder.set(chunk, of, ws, lx, ceilY, lz, HANGING_ROOTS);
			} else if (r2 < 0.30) {
				int len = 1 + (int) (Noise.unit(h >>> 27) * 4);
				int bottom = Math.max(ceilY - len + 1, floor + 2);
				for (int y = ceilY; y >= bottom; y--) {
					if (parts[y - y0] != Geode.INSIDE) {
						bottom = y + 1;
						break;
					}
				}
				for (int y = ceilY; y >= bottom; y--) {
					boolean berries = Noise.unit(Noise.hash(geo.seed ^ 3, x, y, z)) < 0.55;
					BlockState s = y == bottom ? (berries ? VINE_HEAD_BERRIES : VINE_HEAD) : (berries ? VINE_BODY_BERRIES : VINE_BODY);
					TerrainBuilder.set(chunk, of, ws, lx, y, lz, s);
				}
			}
		}
	}
}
