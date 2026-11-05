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
			BAMBOO = Blocks.BAMBOO.defaultBlockState(),
			LOG = Blocks.OAK_LOG.defaultBlockState(),
			AZALEA_LEAVES = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true),
			FLOWERING_LEAVES = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);

	/** Kleiner Azaleenbaum: 3-4 Blöcke Stamm, runde Krone aus (blühendem) Azaleenlaub bis unter die Decke. */
	private static void tree(ChunkAccess chunk, Heightmap of, Heightmap ws, Geode geo, int lx, int lz, int x, int z,
			int[] parts, int y0, boolean trunk) {
		if (!geo.nearTree(x, z, 3)) return;
		int base = geo.floorAt(geo.treeX, geo.treeZ);
		int ceil = base;
		while (ceil + 1 < y0 + parts.length && geo.part(geo.treeX, ceil + 1, geo.treeZ) == Geode.INSIDE) ceil++;
		if (ceil - base < 4) return; // zu niedrig für einen Baum
		int top = Math.min(base + 3, ceil - 1);
		if (trunk) {
			TerrainBuilder.set(chunk, of, ws, lx, base - 1, lz, ROOTED_DIRT);
			for (int y = base; y <= top; y++) TerrainBuilder.set(chunk, of, ws, lx, y, lz, LOG);
		}
		double ccy = top + 0.5;
		for (int y = top - 1; y <= ceil; y++) {
			if (y - y0 < 0 || y - y0 >= parts.length || parts[y - y0] != Geode.INSIDE) continue;
			double dx = x - geo.treeX, dy = (y - ccy) * 1.3, dz = z - geo.treeZ;
			if (dx * dx + dy * dy + dz * dz > 2.6 * 2.6) continue;
			if (!TerrainBuilder.get(chunk, lx, y, lz).isAir()) continue;
			boolean flowering = Noise.unit(Noise.hash(geo.seed ^ 5, x, y, z)) < 0.3;
			TerrainBuilder.set(chunk, of, ws, lx, y, lz, flowering ? FLOWERING_LEAVES : AZALEA_LEAVES);
		}
	}

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

	/**
	 * Wasser nur dort, wo es rundherum eingeschlossen ist (Teich, aufgefüllter Boden oder Schale).
	 * So kann nie Wasser in eine Höhle oder in die Leere auslaufen.
	 */
	private static boolean waterSafe(Geode geo, int x, int y, int z) {
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] d : dirs) {
			int nx = x + d[0], nz = z + d[1];
			if (geo.isPond(nx, nz)) continue;
			int p = geo.part(nx, y, nz);
			if (p == Geode.INSIDE) {
				if (geo.isPillar(nx, nz)) continue;
				if (y >= geo.floorAt(nx, nz)) return false;
			} else if (p == Geode.NONE) {
				return false;
			}
		}
		return true;
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
		// innere Schale: unten am Boden immer überwachsen, die Decke grösstenteils auch (Lush-Höhle)
		boolean low = y < geo.floorLevel + 1;
		if (low && p > -0.6) return u < 0.3 ? ROOTED_DIRT : MOSS;
		boolean ceiling = y > geo.cy + geo.ry * 0.25;
		if (ceiling && geo.ceilingMossy(x, z)) return u < 0.35 ? ROOTED_DIRT : MOSS;
		BlockState am = AMETHYST; // kein knospender Amethyst: sonst wachsen mit der Zeit überall Kristalle
		return switch (geo.style) {
			case AMETHYST -> p > 0.25 ? CALCITE : (p < -0.35 ? MOSS : am);
			case OVERGROWN -> p > 0.55 ? am : (p < -0.25 ? ROOTED_DIRT : (u < 0.15 ? CLAY : MOSS));
			case DRIPSTONE -> p > 0.5 ? am : (p < -0.35 ? MOSS : (u < 0.25 ? CALCITE : DRIPSTONE));
			case MIXED -> p < -0.35 ? am : (p < 0.2 ? MOSS : (u < 0.5 ? CALCITE : DRIPSTONE));
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
		boolean pillar = geo.isPillar(x, z);
		for (int y = lowY; y <= ceilY; y++) {
			if (parts[y - y0] != Geode.INSIDE) continue;
			boolean solidBefore = !TerrainBuilder.get(chunk, lx, y, lz).isAir();
			BlockState s;
			if (pillar) {
				// Säule aus dem Material der inneren Schale, unten bemoost
				if (!solidBefore) continue;
				s = y < floor + 1 ? MOSS : shell(geo, Geode.INNER, x, y + 100, z);
			} else if (y < floor) {
				// Boden nur auffüllen, wo die Insel war (sonst bleibt das Loch eines Risses offen)
				if (!solidBefore) continue;
				if (pond) s = y >= floor - 2 ? (waterSafe(geo, x, y, z) ? WATER : CLAY) : CLAY;
				else s = y == floor - 1 ? MOSS : (Noise.unit(Noise.hash(geo.seed, x, y, z)) < 0.4 ? ROOTED_DIRT : DIRT);
			} else {
				s = AIR;
			}
			TerrainBuilder.set(chunk, of, ws, lx, y, lz, s);
		}

		long h = Noise.hash(geo.seed, x, z);
		double r1 = Noise.unit(h), r2 = Noise.unit(h >>> 13), r3 = Noise.unit(h >>> 29);

		if (pillar) return;

		boolean trunk = x == geo.treeX && z == geo.treeZ;
		boolean byTree = geo.nearTree(x, z, 1);

		// Bambus-Fleck
		if (!pond && !byTree && floor - 1 >= lowY && floor <= ceilY && geo.isBambooPatch(x, z) && r1 < 0.55 && parts[floor - y0] == Geode.INSIDE
				&& TerrainBuilder.get(chunk, lx, floor - 1, lz) == MOSS) {
			int top = Math.min(floor + 1 + (int) (r2 * 3), ceilY - 1);
			for (int y = floor; y <= top; y++) TerrainBuilder.set(chunk, of, ws, lx, y, lz, BAMBOO);
		} else
		// Pflanzen auf dem Moosboden (die Mitte bleibt frei)
		if (!pond && !byTree && floor - 1 >= lowY && floor <= ceilY && !geo.isCenterSpot(x, z)
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

		// Azaleenbaum: Stamm und Krone
		tree(chunk, of, ws, geo, lx, lz, x, z, parts, y0, trunk);

		// Decke: je nach Material Ranken, Laub, Wurzeln, Tropfsteine oder ein paar Amethyst-Kristalle
		if (ceilY > floor + 1) {
			BlockState above = TerrainBuilder.get(chunk, lx, ceilY + 1, lz);
			if (above.isAir() || !TerrainBuilder.get(chunk, lx, ceilY, lz).isAir()) return;
			if (Math.abs(x + 0.5 - geo.cx) < 0.6 && Math.abs(z + 0.5 - geo.cz) < 0.6) {
				TerrainBuilder.set(chunk, of, ws, lx, ceilY, lz, SPORE_BLOSSOM);
			} else if (above == DRIPSTONE && r3 < 0.3) {
				TerrainBuilder.set(chunk, of, ws, lx, ceilY, lz, DRIP_TIP_DOWN);
			} else if ((above == MOSS || above == ROOTED_DIRT) && r3 < 0.16) {
				// hängende Laubbüschel
				TerrainBuilder.set(chunk, of, ws, lx, ceilY, lz, r3 < 0.06 ? FLOWERING_LEAVES : AZALEA_LEAVES);
				if (r3 < 0.1 && ceilY - 1 > floor + 1 && parts[ceilY - 1 - y0] == Geode.INSIDE)
					TerrainBuilder.set(chunk, of, ws, lx, ceilY - 1, lz, AZALEA_LEAVES);
			} else if (above == ROOTED_DIRT && r3 < 0.55) {
				TerrainBuilder.set(chunk, of, ws, lx, ceilY, lz, HANGING_ROOTS);
			} else if (r2 < (above == MOSS ? 0.5 : 0.2)) {
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
