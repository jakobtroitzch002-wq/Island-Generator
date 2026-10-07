package dev.jakob.islandgen.world;

import dev.jakob.islandgen.layout.Column;
import dev.jakob.islandgen.layout.Layout;
import dev.jakob.islandgen.layout.Noise;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;

/**
 * Strukturen, die normalerweise im Boden stecken (Trial Chambers, Ancient City, Stronghold, Trail Ruins),
 * würden über den Inselrand hinaus in die Leere ragen. Hier werden sie in Gestein eingepackt,
 * und Gebäude mit Fundament (Dörfer usw.) bekommen unter sich ein paar Blöcke, statt in der Luft zu hängen.
 */
final class StructurePadding {
	private StructurePadding() {
	}

	private static final BlockState STONE = Blocks.STONE.defaultBlockState(), DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState(),
			TUFF = Blocks.TUFF.defaultBlockState(), COBBLE = Blocks.COBBLESTONE.defaultBlockState(),
			NETHERRACK = Blocks.NETHERRACK.defaultBlockState(), BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();

	static void apply(ChunkAccess chunk, Layout layout, StructureManager structures) {
		if (structures == null) return;
		ChunkPos pos = chunk.getPos();
		var starts = structures.startsForStructure(pos.x(), pos.z(), s -> s.terrainAdaptation() != TerrainAdjustment.NONE);
		if (starts.isEmpty()) return;
		int x0 = pos.getMinBlockX(), z0 = pos.getMinBlockZ();
		Heightmap of = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap ws = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		int[] landTop = new int[256];
		for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
			Column c = TerrainBuilder.sampleColumn(layout, x0 + lx, z0 + lz);
			landTop[lz * 16 + lx] = c.island == null ? Integer.MIN_VALUE : c.landTop;
		}
		for (StructureStart start : starts) {
			TerrainAdjustment adj = start.getStructure().terrainAdaptation();
			for (StructurePiece piece : start.getPieces()) {
				if (!piece.isCloseToChunk(pos, 4)) continue;
				BoundingBox b = piece.getBoundingBox();
				if (adj == TerrainAdjustment.BEARD_THIN) {
					foundation(chunk, layout, of, ws, b, x0, z0);
				} else {
					// Stronghold (BURY) und Trial Chambers (ENCAPSULATE) werden überall eingepackt: der Fels wächst mit, die Festung verschwindet in der Insel.
					wrap(chunk, layout, of, ws, b, x0, z0, landTop, adj == TerrainAdjustment.BURY || adj == TerrainAdjustment.ENCAPSULATE);
				}
			}
		}
	}

	/** Packt eine Struktur rundum in Gestein (nur dort, wo Luft ist). */
	private static void wrap(ChunkAccess chunk, Layout layout, Heightmap of, Heightmap ws, BoundingBox b, int x0, int z0, int[] landTop, boolean everywhere) {
		int m = everywhere ? 4 : 2;
		int xa = Math.max(b.minX() - m, x0), xb = Math.min(b.maxX() + m, x0 + 15);
		int za = Math.max(b.minZ() - m, z0), zb = Math.min(b.maxZ() + m, z0 + 15);
		if (xa > xb || za > zb) return;
		for (int x = xa; x <= xb; x++) {
			for (int z = za; z <= zb; z++) {
				int lx = x - x0, lz = z - z0;
				int jag = everywhere
						? (int) (4 + 6 * (layout.noise.noise2(x * 0.15, z * 0.15) + 1))
						: (int) (Noise.unit(Noise.hash(layout.seed, x, z, 77)) * 4);
				int yBottom = Math.max(b.minY() - m - jag, chunk.getMinY() + 1);
				int top = landTop[lz * 16 + lx];
				if (top == Integer.MIN_VALUE && !everywhere) continue; // keine schwebenden Steinblöcke ausserhalb der Inseln
				int yTop = top == Integer.MIN_VALUE ? b.maxY() + 2 : Math.min(b.maxY() + 1, top);
				for (int y = yBottom; y <= yTop; y++) {
					if (!TerrainBuilder.get(chunk, lx, y, lz).isAir()) continue;
					TerrainBuilder.set(chunk, of, ws, lx, y, lz, filler(layout, x, y, z));
				}
			}
		}
	}

	/** Ein paar Blöcke Fundament unter Gebäuden, damit nichts in der Luft schwebt. */
	private static void foundation(ChunkAccess chunk, Layout layout, Heightmap of, Heightmap ws, BoundingBox b, int x0, int z0) {
		int xa = Math.max(b.minX(), x0), xb = Math.min(b.maxX(), x0 + 15);
		int za = Math.max(b.minZ(), z0), zb = Math.min(b.maxZ(), z0 + 15);
		if (xa > xb || za > zb) return;
		for (int x = xa; x <= xb; x++) {
			for (int z = za; z <= zb; z++) {
				int lx = x - x0, lz = z - z0;
				// Nur Lücken bis zum Inselboden schliessen (max. 6 Blöcke), nie freischwebend auffüllen.
				int ground = Integer.MIN_VALUE;
				for (int y = b.minY() - 1; y >= b.minY() - 7 && y > chunk.getMinY(); y--) {
					if (!TerrainBuilder.get(chunk, lx, y, lz).isAir()) { ground = y; break; }
				}
				if (ground == Integer.MIN_VALUE) continue;
				for (int y = b.minY() - 1; y > ground; y--) {
					TerrainBuilder.set(chunk, of, ws, lx, y, lz, layout.nether ? NETHERRACK : (y == ground + 1 ? COBBLE : STONE));
				}
			}
		}
	}

	private static BlockState filler(Layout layout, int x, int y, int z) {
		double u = Noise.unit(Noise.hash(layout.seed, x, y, z));
		if (layout.nether) return u < 0.25 ? BLACKSTONE : NETHERRACK;
		if (u < 0.2) return TUFF;
		return y < 90 ? DEEPSLATE : STONE;
	}
}
