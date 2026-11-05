package dev.jakob.islandgen.world;

import dev.jakob.islandgen.layout.Column;
import dev.jakob.islandgen.layout.Group;
import dev.jakob.islandgen.layout.Island;
import dev.jakob.islandgen.layout.Mineshaft;
import dev.jakob.islandgen.layout.Noise;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/** Baut die Minenschächte der Tafelberg-Inseln: Stollen mit Stützen, Schienen, Spinnweben und einen Leiterschacht. */
final class MineshaftBuilder {
	private MineshaftBuilder() {
	}

	private static final BlockState AIR = Blocks.AIR.defaultBlockState(),
			PLANKS = Blocks.OAK_PLANKS.defaultBlockState(),
			DARK_PLANKS = Blocks.DARK_OAK_PLANKS.defaultBlockState(),
			FENCE = Blocks.OAK_FENCE.defaultBlockState(),
			COBWEB = Blocks.COBWEB.defaultBlockState(),
			RAIL_NS = Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, RailShape.NORTH_SOUTH),
			RAIL_EW = Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, RailShape.EAST_WEST),
			LADDER = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH),
			LANTERN = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);

	static void apply(ChunkAccess chunk, Heightmap of, Heightmap ws, Group g, int x0, int z0) {
		Column c = new Column();
		for (Island is : g.islands) {
			Mineshaft m = is.mineshaft;
			if (m == null) continue;
			if (m.maxX < x0 || m.minX > x0 + 15 || m.maxZ < z0 || m.minZ > z0 + 15) continue;
			for (int lz = 0; lz < 16; lz++) {
				for (int lx = 0; lx < 16; lx++) {
					int x = x0 + lx, z = z0 + lz;
					if (!m.inBox(x, z)) continue;
					if (!is.sample(x, z, c)) continue;
					column(chunk, of, ws, is, m, c, lx, lz, x, z);
				}
			}
		}
	}

	private static boolean solid(BlockState s) {
		return !s.isAir() && s.getFluidState().isEmpty();
	}

	private static void column(ChunkAccess chunk, Heightmap of, Heightmap ws, Island is, Mineshaft m, Column c, int lx, int lz, int x, int z) {
		int hits = 0;
		for (Mineshaft.Seg s : m.segs) if (s.contains(x, z, 1)) hits++;

		for (Mineshaft.Seg s : m.segs) {
			if (!s.contains(x, z, 1)) continue;
			int y0 = s.y;
			boolean upper = y0 > m.deepY;
			if (c.landTop < y0 + (upper ? 4 : 5)) continue; // zu wenig Fels darüber
			if (c.bottom > y0 - 2) continue;                // kein Boden darunter
			if (nearGeode(is, x, y0, z)) continue;
			int perp = s.alongX() ? z - s.fixed() : x - s.fixed();
			int along = s.alongX() ? x : z;
			boolean end = along < s.from() || along > s.to();

			for (int y = y0; y <= y0 + 2; y++) TerrainBuilder.set(chunk, of, ws, lx, y, lz, AIR);
			if (!solid(TerrainBuilder.get(chunk, lx, y0 - 1, lz))) TerrainBuilder.set(chunk, of, ws, lx, y0 - 1, lz, PLANKS);

			long h = Noise.hash(is.seed() ^ 0x5348L, x, y0, z);
			double u = Noise.unit(h);
			boolean support = !end && Math.floorMod(along, 4) == 0;
			if (support) {
				if (perp != 0) {
					TerrainBuilder.set(chunk, of, ws, lx, y0, lz, FENCE);
					TerrainBuilder.set(chunk, of, ws, lx, y0 + 1, lz, FENCE);
				}
				TerrainBuilder.set(chunk, of, ws, lx, y0 + 2, lz, u < 0.15 ? DARK_PLANKS : PLANKS);
				// ab und zu eine Laterne unter dem Balken
				if (perp == 0 && u > 0.82 && hits == 1) TerrainBuilder.set(chunk, of, ws, lx, y0 + 1, lz, LANTERN);
			} else if (perp != 0 && u < 0.07) {
				TerrainBuilder.set(chunk, of, ws, lx, y0 + 2, lz, COBWEB);
			} else if (perp != 0 && u > 0.985) {
				TerrainBuilder.set(chunk, of, ws, lx, y0, lz, COBWEB);
			}
			// Schienen auf der Mittellinie (nicht an Kreuzungen, teils herausgebrochen)
			if (perp == 0 && hits == 1 && !end && !m.isShaft(x, z) && Noise.unit(h >>> 17) < 0.8
					&& TerrainBuilder.get(chunk, lx, y0 + 1, lz).isAir()) {
				TerrainBuilder.set(chunk, of, ws, lx, y0, lz, s.alongX() ? RAIL_EW : RAIL_NS);
			}
		}

		// Spinnweben rund um den Spawner
		if (m.spawner != null) {
			int dx = x - m.spawner[0], dz = z - m.spawner[2];
			if (Math.abs(dx) <= 3 && Math.abs(dz) <= 3) {
				for (int y = m.spawner[1]; y <= m.spawner[1] + 2; y++) {
					if ((dx != 0 || dz != 0) && TerrainBuilder.get(chunk, lx, y, lz).isAir()
							&& Noise.unit(Noise.hash(is.seed() ^ 0x5750L, x, y, z)) < 0.45) {
						TerrainBuilder.set(chunk, of, ws, lx, y, lz, COBWEB);
					}
				}
			}
		}

		// Leiterschacht von den tiefen Stollen bis an die Oberfläche
		int dx = x - m.hubX, dz = z - m.hubZ;
		if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2 && c.bottom < m.deepY - 1) {
			int top = c.landTop;
			boolean inner = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
			if (inner) {
				for (int y = m.deepY; y <= top; y++) TerrainBuilder.set(chunk, of, ws, lx, y, lz, AIR);
				if (!solid(TerrainBuilder.get(chunk, lx, m.deepY - 1, lz))) TerrainBuilder.set(chunk, of, ws, lx, m.deepY - 1, lz, PLANKS);
				if (dx == 0 && dz == -1) {
					for (int y = m.deepY; y <= top; y++) TerrainBuilder.set(chunk, of, ws, lx, y, lz, LADDER);
				}
			} else {
				// Wand um den Schacht (damit die Leiter hält und nichts hineinrieselt)
				for (int y = m.deepY + 3; y <= top; y++) {
					if (inSeg(m, x, y, z)) continue;
					BlockState s = TerrainBuilder.get(chunk, lx, y, lz);
					if (!solid(s) || s.is(Blocks.RED_SAND) || s.is(Blocks.SAND) || s.is(Blocks.GRAVEL)) {
						TerrainBuilder.set(chunk, of, ws, lx, y, lz, (y - m.deepY) % 5 == 0 ? PLANKS : Blocks.TERRACOTTA.defaultBlockState());
					}
				}
				// Zaun oben um die Öffnung, mit Lücke bei der Leiter
				if (!(dx == 0 && dz == -2)) TerrainBuilder.set(chunk, of, ws, lx, top + 1, lz, FENCE);
			}
		}
	}

	private static boolean inSeg(Mineshaft m, int x, int y, int z) {
		for (Mineshaft.Seg s : m.segs) if (y >= s.y() && y <= s.y() + 2 && s.contains(x, z, 1)) return true;
		return false;
	}

	private static boolean nearGeode(Island is, int x, int y0, int z) {
		if (is.geode == null) return false;
		for (int y = y0 - 1; y <= y0 + 3; y++) if (is.geode.q(x, y, z) < 3.0) return true;
		return false;
	}
}
