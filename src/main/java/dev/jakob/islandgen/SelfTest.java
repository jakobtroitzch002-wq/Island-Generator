package dev.jakob.islandgen;

import dev.jakob.islandgen.layout.Group;
import dev.jakob.islandgen.layout.Island;
import dev.jakob.islandgen.layout.Layout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Automatischer Test für GitHub Actions: generiert einige Inselgruppen, zeichnet eine Karte und
 * Seitenansichten, zählt Blöcke und Strukturen und beendet den Server danach.
 * Wird nur mit -Dislandgen.selftest=true aktiv.
 */
final class SelfTest {
	private SelfTest() {
	}

	private static final Block[] COUNTED = {
			Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE, Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE,
			Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_GOLD_ORE,
			Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.CALCITE, Blocks.AMETHYST_BLOCK,
			Blocks.BUDDING_AMETHYST, Blocks.DRIPSTONE_BLOCK, Blocks.MOSS_BLOCK, Blocks.SCULK, Blocks.CLAY, Blocks.MUD, Blocks.PACKED_ICE,
			Blocks.BLUE_ICE, Blocks.TERRACOTTA, Blocks.RED_SAND, Blocks.WATER, Blocks.LAVA, Blocks.CHEST, Blocks.SPAWNER, Blocks.BELL,
			Blocks.END_PORTAL_FRAME, Blocks.PRISMARINE, Blocks.SPONGE, Blocks.WET_SPONGE, Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.JUNGLE_LOG,
			Blocks.CHERRY_LOG, Blocks.DARK_OAK_LOG, Blocks.MANGROVE_LOG, Blocks.ACACIA_LOG, Blocks.BIRCH_LOG, Blocks.PALE_OAK_LOG,
			Blocks.BRAIN_CORAL_BLOCK, Blocks.TUBE_CORAL_BLOCK, Blocks.SUSPICIOUS_SAND, Blocks.SUSPICIOUS_GRAVEL, Blocks.TRIAL_SPAWNER,
			Blocks.NETHERRACK, Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_GOLD_ORE, Blocks.ANCIENT_DEBRIS, Blocks.GLOWSTONE, Blocks.NETHER_BRICKS,
			Blocks.GILDED_BLACKSTONE, Blocks.BLACKSTONE, Blocks.BASALT, Blocks.SOUL_SAND, Blocks.SHROOMLIGHT, Blocks.CRIMSON_STEM, Blocks.WARPED_STEM
	};

	static void run(MinecraftServer server) {
		StringBuilder report = new StringBuilder();
		Path dir = Path.of("selftest");
		try {
			Files.createDirectories(dir);
			long seed = IslandGen.seed();
			report.append("Seed: ").append(seed).append('\n');

			ServerLevel ow = server.overworld();
			Layout layout = Layout.get(seed, false);
			report.append("Stronghold-Zelle: ").append(layout.strongholdCellX()).append(',').append(layout.strongholdCellZ()).append('\n');

			List<Group> groups = new ArrayList<>();
			for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) groups.add(layout.group(i, j));
			Group sh = layout.group(layout.strongholdCellX(), layout.strongholdCellZ());
			if (!groups.contains(sh)) groups.add(sh);
			for (Group g : groups) {
				report.append("\nGruppe ").append(g.cellX).append(',').append(g.cellZ).append(" Thema=").append(g.theme)
						.append(" Mitte=").append((int) g.centerX).append(',').append((int) g.centerZ).append('\n');
				for (Island is : g.islands) report.append("   ").append(is).append(is.underground != null ? " Höhle=" + is.underground : "").append('\n');
			}

			Set<Long> loaded = new HashSet<>();
			Map<String, Integer> structures = new TreeMap<>();
			Map<String, Long> blocks = new TreeMap<>();
			long t0 = System.nanoTime();
			int chunkCount = 0;
			for (Group g : groups) chunkCount += loadGroup(ow, g, loaded, structures, blocks);
			long ms = (System.nanoTime() - t0) / 1_000_000;
			report.append("\nOverworld: ").append(chunkCount).append(" Chunks generiert in ").append(ms).append(" ms (")
					.append(chunkCount == 0 ? 0 : ms / chunkCount).append(" ms/Chunk inkl. Analyse)\n");
			report.append("\nStrukturen (Overworld):\n");
			structures.forEach((k, v) -> report.append("   ").append(k).append(": ").append(v).append('\n'));
			report.append("\nBlöcke (Overworld):\n");
			blocks.forEach((k, v) -> report.append("   ").append(k).append(": ").append(v).append('\n'));

			drawMap(ow, groups, loaded, dir.resolve("map_overworld.png"));
			for (Group g : groups) {
				if (g.isEmpty()) continue;
				Island is = g.islands.get(0);
				drawSide(ow, (int) is.cx - 160, (int) is.cx + 160, (int) is.cz, 0, 220, dir.resolve("side_" + g.cellX + "_" + g.cellZ + ".png"));
			}

			// Nether
			ServerLevel nether = server.getLevel(Level.NETHER);
			if (nether != null) {
				Layout nl = Layout.get(seed, true);
				List<Group> ng = new ArrayList<>();
				for (int i = -1; i <= 0; i++) for (int j = -1; j <= 0; j++) ng.add(nl.group(i, j));
				Set<Long> nLoaded = new HashSet<>();
				Map<String, Integer> nStructures = new TreeMap<>();
				Map<String, Long> nBlocks = new TreeMap<>();
				t0 = System.nanoTime();
				int nCount = 0;
				for (Group g : ng) nCount += loadGroup(nether, g, nLoaded, nStructures, nBlocks);
				report.append("\nNether: ").append(nCount).append(" Chunks in ").append((System.nanoTime() - t0) / 1_000_000).append(" ms\n");
				for (Group g : ng) report.append("   Gruppe ").append(g.cellX).append(',').append(g.cellZ).append(' ').append(g.theme).append(" Inseln=").append(g.islands.size()).append('\n');
				report.append("Strukturen (Nether):\n");
				nStructures.forEach((k, v) -> report.append("   ").append(k).append(": ").append(v).append('\n'));
				report.append("Blöcke (Nether):\n");
				nBlocks.forEach((k, v) -> report.append("   ").append(k).append(": ").append(v).append('\n'));
				drawMap(nether, ng, nLoaded, dir.resolve("map_nether.png"));
				Island is = ng.get(ng.size() - 1).islands.get(0);
				drawSide(nether, (int) is.cx - 160, (int) is.cx + 160, (int) is.cz, 0, 160, dir.resolve("side_nether.png"));
			}

			// /locate testen (inkl. Enderaugen-Ziel)
			String[] cmds = {
					"locate structure minecraft:stronghold",
					"locate structure #minecraft:eye_of_ender_located",
					"locate structure #minecraft:village",
					"locate structure minecraft:monument",
					"locate structure minecraft:trial_chambers",
					"locate structure minecraft:mansion",
					"execute in minecraft:the_nether run locate structure minecraft:fortress",
					"execute in minecraft:the_nether run locate structure minecraft:bastion_remnant"
			};
			for (String cmd : cmds) {
				IslandGen.LOGGER.info("[selftest] /{}", cmd);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
			}
			BlockPos spawn = ow.getRespawnData().pos();
			report.append("\nWelt-Spawn: ").append(spawn).append('\n');
			report.append("\nERGEBNIS: OK\n");
		} catch (Throwable t) {
			IslandGen.LOGGER.error("[selftest] Fehler", t);
			report.append("\nERGEBNIS: FEHLER ").append(t).append('\n');
		} finally {
			try {
				Files.writeString(dir.resolve("report.txt"), report.toString());
			} catch (IOException e) {
				IslandGen.LOGGER.error("[selftest] Report nicht schreibbar", e);
			}
			IslandGen.LOGGER.info("[selftest] fertig, Server wird beendet\n{}", report);
			server.halt(false);
		}
	}

	private static int loadGroup(ServerLevel level, Group g, Set<Long> loaded, Map<String, Integer> structures, Map<String, Long> blocks) {
		if (g.isEmpty()) return 0;
		int r = (int) Math.ceil(g.reach) + 16;
		int cx0 = ((int) g.centerX - r) >> 4, cx1 = ((int) g.centerX + r) >> 4;
		int cz0 = ((int) g.centerZ - r) >> 4, cz1 = ((int) g.centerZ + r) >> 4;
		Set<Block> counted = Set.of(COUNTED);
		var structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		int n = 0;
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				if (!g.intersects(cx * 16, cz * 16, cx * 16 + 15, cz * 16 + 15)) continue;
				ChunkAccess chunk = level.getChunk(cx, cz);
				if (!loaded.add(((long) cx << 32) ^ (cz & 0xFFFFFFFFL))) continue;
				n++;
				for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
					if (!e.getValue().isValid()) continue;
					var key = structureRegistry.getKey(e.getKey());
					structures.merge(String.valueOf(key), 1, Integer::sum);
				}
				BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
				for (int y = 0; y < 256; y++) {
					for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
						BlockState s = chunk.getBlockState(p.set(cx * 16 + lx, y, cz * 16 + lz));
						if (s.isAir()) continue;
						Block b = s.getBlock();
						if (counted.contains(b)) blocks.merge(BuiltInRegistries.BLOCK.getKey(b).toString(), 1L, Long::sum);
					}
				}
			}
		}
		return n;
	}

	private static void drawMap(ServerLevel level, List<Group> groups, Set<Long> loaded, Path file) throws IOException {
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (Group g : groups) {
			minX = Math.min(minX, (int) (g.centerX - 170));
			maxX = Math.max(maxX, (int) (g.centerX + 170));
			minZ = Math.min(minZ, (int) (g.centerZ - 170));
			maxZ = Math.max(maxZ, (int) (g.centerZ + 170));
		}
		int scale = 2;
		int w = (maxX - minX) / scale, h = (maxZ - minZ) / scale;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				int x = minX + px * scale, z = minZ + py * scale;
				int color = 0x0F1626;
				if (loaded.contains(((long) (x >> 4) << 32) ^ ((z >> 4) & 0xFFFFFFFFL))) {
					ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
					int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
					if (y > level.getMinY()) {
						BlockState s = chunk.getBlockState(p.set(x, y, z));
						int c = s.getMapColor(level, p).col;
						double f = 0.55 + Math.max(0, Math.min(200, y)) / 330.0;
						color = shade(c, f);
					}
				}
				img.setRGB(px, py, color);
			}
		}
		ImageIO.write(img, "png", file.toFile());
	}

	private static void drawSide(ServerLevel level, int x0, int x1, int z, int y0, int y1, Path file) throws IOException {
		int scale = 3;
		BufferedImage img = new BufferedImage((x1 - x0) * scale, (y1 - y0) * scale, BufferedImage.TYPE_INT_RGB);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = x0; x < x1; x++) {
			ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
			for (int y = y0; y < y1; y++) {
				BlockState s = chunk.getBlockState(p.set(x, y, z));
				int c = s.isAir() ? 0x9CC7F0 : s.getMapColor(level, p).col;
				for (int dx = 0; dx < scale; dx++) for (int dy = 0; dy < scale; dy++) {
					img.setRGB((x - x0) * scale + dx, (y1 - 1 - y) * scale + dy, c);
				}
			}
		}
		ImageIO.write(img, "png", file.toFile());
	}

	private static int shade(int c, double f) {
		int r = (int) Math.min(255, ((c >> 16) & 255) * f), g = (int) Math.min(255, ((c >> 8) & 255) * f), b = (int) Math.min(255, (c & 255) * f);
		return (r << 16) | (g << 8) | b;
	}
}
