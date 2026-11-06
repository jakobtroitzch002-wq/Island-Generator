package dev.jakob.islandgen.world;

import dev.jakob.islandgen.layout.Column;
import dev.jakob.islandgen.layout.Garden;
import dev.jakob.islandgen.layout.Group;
import dev.jakob.islandgen.layout.Island;
import dev.jakob.islandgen.layout.Noise;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/** Baut die japanischen Gärten der Kirschblüten-Inseln. */
final class GardenBuilder {
	private GardenBuilder() {
	}

	private static final BlockState AIR = Blocks.AIR.defaultBlockState(),
			RED = Palette.byId("red_concrete"),
			BLACK = Palette.byId("black_concrete"),
			GRAVEL = Blocks.GRAVEL.defaultBlockState(),
			STEP = Blocks.STONE_BRICKS.defaultBlockState(),
			STEP2 = Blocks.POLISHED_ANDESITE.defaultBlockState(),
			WALL = Blocks.STONE_BRICK_WALL.defaultBlockState(),
			BODY = Blocks.POLISHED_ANDESITE.defaultBlockState(),
			LANTERN = Blocks.LANTERN.defaultBlockState(),
			CAP = Blocks.STONE_BRICK_SLAB.defaultBlockState(),
			WATER = Blocks.WATER.defaultBlockState(),
			CLAY = Blocks.CLAY.defaultBlockState(),
			DIRT = Blocks.DIRT.defaultBlockState(),
			GRASS = Blocks.GRASS_BLOCK.defaultBlockState(),
			MOSS = Blocks.MOSS_BLOCK.defaultBlockState(),
			MOSSY = Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
			LILY = Blocks.LILY_PAD.defaultBlockState(),
			DECK = Blocks.MANGROVE_PLANKS.defaultBlockState(),
			RAIL = Blocks.MANGROVE_FENCE.defaultBlockState(),
			BAMBOO = Blocks.BAMBOO.defaultBlockState(),
			BAMBOO_TOP = Blocks.BAMBOO.defaultBlockState().setValue(BlockStateProperties.BAMBOO_LEAVES, BambooLeaves.LARGE),
			BAMBOO_MID = Blocks.BAMBOO.defaultBlockState().setValue(BlockStateProperties.BAMBOO_LEAVES, BambooLeaves.SMALL);

	static void apply(ChunkAccess chunk, Heightmap of, Heightmap ws, Group g, int x0, int z0) {
		Column c = new Column();
		for (Island is : g.islands) {
			Garden gd = is.garden;
			if (gd == null) continue;
			if (gd.maxX < x0 || gd.minX > x0 + 15 || gd.maxZ < z0 || gd.minZ > z0 + 15) continue;
			for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
				int x = x0 + lx, z = z0 + lz;
				if (!gd.inBox(x, z) || !is.sample(x, z, c) || c.island != is) continue;
				column(chunk, of, ws, is, gd, c, lx, lz, x, z);
			}
		}
	}

	private static void set(ChunkAccess ch, Heightmap of, Heightmap ws, int lx, int y, int lz, BlockState s) {
		TerrainBuilder.set(ch, of, ws, lx, y, lz, s);
	}

	private static void column(ChunkAccess ch, Heightmap of, Heightmap ws, Island is, Garden gd, Column c, int lx, int lz, int x, int z) {
		int top = c.landTop;
		double u = Noise.unit(Noise.hash(is.seed() ^ 0x4741L, x, z));

		// Zen-Garten: eben, Kies mit Rechenspuren aus Steinziegeln, Moosrand, ein paar Felsen
		if (gd.zen != null) {
			double d = Math.hypot(x - gd.zen.x(), z - gd.zen.z());
			int zy = gd.zen.y();
			if (d < gd.zen.r() + 1.2) {
				for (int y = zy + 1; y <= Math.max(top, zy) + 1; y++) set(ch, of, ws, lx, y, lz, AIR);
				for (int y = top + 1; y < zy; y++) set(ch, of, ws, lx, y, lz, DIRT);
				top = zy;
				if (d < gd.zen.r()) {
					// Rechenlinien: Ringe um den grössten Felsen
					set(ch, of, ws, lx, zy, lz, GRAVEL);
					set(ch, of, ws, lx, zy - 1, lz, DIRT);
					for (int[] rk : gd.rocks) {
						if (rk[0] == x && rk[1] == z) {
							for (int k = 1; k <= rk[2]; k++) set(ch, of, ws, lx, zy + k, lz, k == rk[2] ? MOSSY : Blocks.COBBLESTONE.defaultBlockState());
						} else if (Math.abs(rk[0] - x) + Math.abs(rk[1] - z) == 1 && u < 0.5) {
							set(ch, of, ws, lx, zy + 1, lz, MOSSY);
						}
					}
				} else {
					set(ch, of, ws, lx, zy, lz, u < 0.5 ? MOSS : Blocks.STONE_BRICK_SLAB.defaultBlockState());
					if (u >= 0.5) set(ch, of, ws, lx, zy - 1, lz, DIRT);
				}
			}
		}

		// Teich mit Seerosen und roter Bogenbrücke
		if (gd.pond != null) {
			double d = Math.hypot(x - gd.pond.x(), z - gd.pond.z());
			int wy = gd.pond.y();
			int r = gd.pond.r();
			int along = gd.bridgeAlongX ? x - gd.pond.x() : z - gd.pond.z();
			int perp = gd.bridgeAlongX ? z - gd.pond.z() : x - gd.pond.x();
			if (d < r) {
				for (int y = wy + 1; y <= top + 1; y++) set(ch, of, ws, lx, y, lz, AIR);
				set(ch, of, ws, lx, wy, lz, WATER);
				set(ch, of, ws, lx, wy - 1, lz, d < r - 1.5 ? WATER : CLAY);
				set(ch, of, ws, lx, wy - 2, lz, CLAY);
				if (d < r - 1.5) set(ch, of, ws, lx, wy - 3, lz, CLAY);
				if (u < 0.14 && perp != 0 && Math.abs(perp) != 1) set(ch, of, ws, lx, wy + 1, lz, LILY);
				top = wy;
			} else if (d < r + 1.6 && u < 0.3 && Math.abs(perp) > 1) {
				set(ch, of, ws, lx, top + 1, lz, MOSSY); // Ufersteine
			}
			if (Math.abs(along) <= r + 1 && Math.abs(perp) <= 1) {
				int deck = wy + 1 + (Math.abs(along) <= r - 2 ? 1 : 0);
				for (int y = deck + 1; y <= Math.max(top, deck) + 2; y++) set(ch, of, ws, lx, y, lz, AIR);
				if (perp == 0) {
					set(ch, of, ws, lx, deck, lz, DECK);
				} else {
					set(ch, of, ws, lx, deck, lz, DECK);
					if (Math.abs(along) <= r) set(ch, of, ws, lx, deck + 1, lz, RAIL);
				}
			}
		}

		// Weg: Trittsteine in der Mitte, Kies an den Seiten
		if (gd.onPath(x, z, 1)) {
			int along = gd.path.alongX() ? x : z;
			int perp = gd.path.alongX() ? z - gd.path.fixed() : x - gd.path.fixed();
			set(ch, of, ws, lx, top + 1, lz, AIR);
			set(ch, of, ws, lx, top, lz, perp == 0 ? (Math.floorMod(along, 3) == 0 ? STEP2 : STEP) : GRAVEL);
			set(ch, of, ws, lx, top - 1, lz, DIRT);
		}

		// Torii
		for (Garden.Torii t : gd.torii) {
			int along = t.alongX() ? x - t.x() : z - t.z();
			int p = t.alongX() ? z - t.z() : x - t.x();
			if (along != 0 || Math.abs(p) > 4) continue;
			int y = t.y();
			if (Math.abs(p) == 2) for (int k = top + 1; k <= y + 5; k++) set(ch, of, ws, lx, k, lz, RED);       // Pfeiler
			if (Math.abs(p) <= 3) set(ch, of, ws, lx, y + 4, lz, RED);                                          // Nuki
			if (p == 0) set(ch, of, ws, lx, y + 5, lz, RED);                                                    // Tafel
			set(ch, of, ws, lx, y + 6, lz, BLACK);                                                             // Kasagi
			if (Math.abs(p) == 4) set(ch, of, ws, lx, y + 7, lz, BLACK);                                       // geschwungene Enden
		}

		// Steinlaternen
		for (int[] l : gd.lanterns) {
			if (l[0] != x || l[1] != z) continue;
			set(ch, of, ws, lx, top + 1, lz, WALL);
			set(ch, of, ws, lx, top + 2, lz, BODY);
			set(ch, of, ws, lx, top + 3, lz, LANTERN);
			set(ch, of, ws, lx, top + 4, lz, CAP);
		}

		// Bambushain
		if (gd.bamboo != null && Math.hypot(x - gd.bamboo.x(), z - gd.bamboo.z()) < gd.bamboo.r() + 0.5 && u < 0.5) {
			int h = 5 + (int) (Noise.unit(Noise.hash(is.seed() ^ 0x42414DL, x, z)) * 6);
			BlockState below = TerrainBuilder.get(ch, lx, top, lz);
			if (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT) || below.is(Blocks.MOSS_BLOCK)) {
				for (int k = 1; k <= h; k++) set(ch, of, ws, lx, top + k, lz, k == h ? BAMBOO_TOP : (k >= h - 2 ? BAMBOO_MID : BAMBOO));
			}
		}
	}
}
