package dev.jakob.islandgen.world;

import dev.jakob.islandgen.layout.Column;
import dev.jakob.islandgen.layout.Island;
import dev.jakob.islandgen.layout.Noise;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Entscheidet, welcher Block an welcher Stelle einer Insel liegt (ohne Erze, die kommen danach).
 */
final class Palette {
	private Palette() {
	}

	static final BlockState AIR = Blocks.AIR.defaultBlockState();
	static final BlockState WATER = Blocks.WATER.defaultBlockState();
	static final BlockState LAVA = Blocks.LAVA.defaultBlockState();

	private static final BlockState STONE = s(Blocks.STONE), DEEPSLATE = s(Blocks.DEEPSLATE), DIRT = s(Blocks.DIRT),
			GRASS = s(Blocks.GRASS_BLOCK), SAND = s(Blocks.SAND), SANDSTONE = s(Blocks.SANDSTONE), RED_SAND = s(Blocks.RED_SAND),
			GRAVEL = s(Blocks.GRAVEL), CLAY = s(Blocks.CLAY), GRANITE = s(Blocks.GRANITE), DIORITE = s(Blocks.DIORITE),
			ANDESITE = s(Blocks.ANDESITE), TUFF = s(Blocks.TUFF), CALCITE = s(Blocks.CALCITE), COBBLE = s(Blocks.COBBLESTONE),
			MOSSY_COBBLE = s(Blocks.MOSSY_COBBLESTONE), MOSS = s(Blocks.MOSS_BLOCK), PODZOL = s(Blocks.PODZOL),
			COARSE_DIRT = s(Blocks.COARSE_DIRT), MYCELIUM = s(Blocks.MYCELIUM), MUD = s(Blocks.MUD), SNOW_BLOCK = s(Blocks.SNOW_BLOCK),
			POWDER_SNOW = s(Blocks.POWDER_SNOW), PACKED_ICE = s(Blocks.PACKED_ICE), BLUE_ICE = s(Blocks.BLUE_ICE),
			DRIPSTONE = s(Blocks.DRIPSTONE_BLOCK), ROOTED_DIRT = s(Blocks.ROOTED_DIRT), SMOOTH_BASALT = s(Blocks.SMOOTH_BASALT),
			OBSIDIAN = s(Blocks.OBSIDIAN), MAGMA = s(Blocks.MAGMA_BLOCK),
			NETHERRACK = s(Blocks.NETHERRACK), SOUL_SAND = s(Blocks.SOUL_SAND), SOUL_SOIL = s(Blocks.SOUL_SOIL),
			CRIMSON_NYLIUM = s(Blocks.CRIMSON_NYLIUM), WARPED_NYLIUM = s(Blocks.WARPED_NYLIUM), BASALT = s(Blocks.BASALT),
			BLACKSTONE = s(Blocks.BLACKSTONE), NETHER_WART_BLOCK = s(Blocks.NETHER_WART_BLOCK), WARPED_WART_BLOCK = s(Blocks.WARPED_WART_BLOCK);

	private static final BlockState[] BANDS = {
			byId("terracotta"), byId("orange_terracotta"), byId("orange_terracotta"), byId("yellow_terracotta"),
			byId("terracotta"), byId("brown_terracotta"), byId("red_terracotta"), byId("terracotta"),
			byId("white_terracotta"), byId("light_gray_terracotta"), byId("orange_terracotta"), byId("terracotta"),
			byId("red_terracotta"), byId("yellow_terracotta"), byId("terracotta"), byId("brown_terracotta")
	};

	/** Block über seine ID holen (robust gegen Umbenennungen von Java-Konstanten). */
	static BlockState byId(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
	}

	private static BlockState s(Block b) {
		return b.defaultBlockState();
	}

	enum Style {
		GRASSY, FOREST_FLOOR, SAVANNA, DESERT, BADLANDS, SNOWY, ICE, MOUNTAIN, STONY, MUSHROOM, MUD, SWAMP, BEACH,
		NETHER_WASTES, SOUL_SAND_VALLEY, CRIMSON, WARPED, BASALT_DELTAS
	}

	static Style style(String biome) {
		return switch (biome) {
			case "minecraft:desert" -> Style.DESERT;
			case "minecraft:badlands", "minecraft:wooded_badlands", "minecraft:eroded_badlands" -> Style.BADLANDS;
			case "minecraft:snowy_plains", "minecraft:snowy_taiga" -> Style.SNOWY;
			case "minecraft:ice_spikes" -> Style.ICE;
			case "minecraft:jagged_peaks" -> Style.MOUNTAIN;
			case "minecraft:stony_peaks" -> Style.STONY;
			case "minecraft:mushroom_fields" -> Style.MUSHROOM;
			case "minecraft:mangrove_swamp" -> Style.MUD;
			case "minecraft:swamp" -> Style.SWAMP;
			case "minecraft:beach", "minecraft:warm_ocean", "minecraft:deep_lukewarm_ocean" -> Style.BEACH;
			case "minecraft:savanna", "minecraft:savanna_plateau" -> Style.SAVANNA;
			case "minecraft:taiga", "minecraft:old_growth_spruce_taiga", "minecraft:old_growth_pine_taiga",
					"minecraft:dark_forest", "minecraft:pale_garden", "minecraft:jungle", "minecraft:bamboo_jungle",
					"minecraft:sparse_jungle" -> Style.FOREST_FLOOR;
			case "minecraft:nether_wastes" -> Style.NETHER_WASTES;
			case "minecraft:soul_sand_valley" -> Style.SOUL_SAND_VALLEY;
			case "minecraft:crimson_forest" -> Style.CRIMSON;
			case "minecraft:warped_forest" -> Style.WARPED;
			case "minecraft:basalt_deltas" -> Style.BASALT_DELTAS;
			default -> Style.GRASSY;
		};
	}

	/**
	 * Block für (x, y, z) in einer Säule, die zur Insel gehört. {@code y} liegt zwischen
	 * {@code c.bottom} und {@code c.solidTop}.
	 */
	static BlockState block(Column c, Style st, Noise n, int x, int y, int z) {
		Island is = c.island;
		boolean nether = is.nether;
		int d = c.landTop - y;            // Tiefe unter der eigentlichen Geländeoberkante
		boolean surface = y == c.solidTop;
		boolean underFluid = c.fluid != Column.FLUID_NONE;
		double thick = c.landTop - c.bottom;

		// --- Unterseite: Haut aus Bruchstein/Moos (Overworld) bzw. Netherrack/Schwarzstein
		// gilt auch am dünnen Inselrand, damit man von unten nie Erde sieht
		if (y <= c.bottom + 1 && y < c.solidTop) {
			long h = Noise.hash(is.seed(), x, y, z);
			double u = Noise.unit(h);
			if (nether) return u < 0.3 ? BLACKSTONE : (u < 0.38 ? MAGMA : NETHERRACK);
			if (st == Style.DESERT) return u < 0.5 ? SANDSTONE : STONE;
			if (st == Style.BADLANDS) return BANDS[Math.floorMod(y, BANDS.length)];
			if (u < 0.35) return COBBLE;
			if (u < 0.5 && st != Style.ICE && st != Style.SNOWY && st != Style.MOUNTAIN) return MOSSY_COBBLE;
			return STONE;
		}

		// --- Oberfläche
		if (surface || d < 0) {
			if (underFluid) return fluidFloor(c, st, n, x, z);
			return top(st, n, x, y, z, is);
		}

		// --- Untergrund (unter Teichen/Lagunen zählt die Tiefe ab dem Boden)
		int sub = underFluid ? c.solidTop - y : d;
		int underDepth = 3 + (int) (1.5 * (n.noise2(x * 0.09, z * 0.09) + 1));
		// Erde/Sand nur so tief, dass darunter immer noch Gestein liegt (keine Erd-Rippen am Rand)
		underDepth = Math.min(underDepth, (int) thick - 3);
		if (sub <= underDepth) {
			BlockState u = under(st, n, x, y, z, sub, c);
			if (u != null) return u;
		}
		if (st == Style.DESERT && sub <= underDepth + 5) return SANDSTONE;
		if (st == Style.BEACH && sub <= underDepth + 3) return SANDSTONE;
		if (st == Style.BADLANDS && d <= 26) {
			int band = Math.floorMod(y + (int) (n.noise2(x * 0.02, z * 0.02) * 4), BANDS.length);
			return BANDS[band];
		}

		// --- Gesteinskörper
		if (nether) return netherBody(c, st, n, x, y, z);

		double p1 = n.noise3(x / 14.0, y / 10.0, z / 14.0);
		double p2 = n.noise3(x / 14.0 + 91.7, y / 10.0 + 17.3, z / 14.0 + 43.1);
		double p3 = n.noise3(x / 9.0 + 211.3, y / 9.0 + 7.7, z / 9.0 - 51.9);

		boolean deep = (is.kind == Island.Kind.MAIN || is.kind == Island.Kind.STRONGHOLD)
				&& y < c.bottom + thick * 0.38 + p2 * 4;
		if (deep) {
			if (p1 > 0.5) return TUFF;
			if (p3 > 0.72) return CALCITE;
			if (p3 < -0.68) return GRAVEL;
			return DEEPSLATE;
		}
		if (p3 > 0.62) return GRAVEL;
		if (p1 > 0.52) return GRANITE;
		if (p1 < -0.52) return DIORITE;
		if (p2 > 0.52) return ANDESITE;
		if (p2 < -0.6) return TUFF;
		if (p3 < -0.62 && d < 14) return (st == Style.SWAMP || st == Style.MUD || st == Style.BEACH) ? CLAY : DIRT;
		if (p3 < -0.7) return CALCITE;
		if ((st == Style.MOUNTAIN || st == Style.STONY) && p1 > 0.35 && p1 < 0.4) return CALCITE;
		if (st == Style.ICE && d < 12 && p2 > 0.4) return p2 > 0.62 ? BLUE_ICE : PACKED_ICE;
		if ("minecraft:dripstone_caves".equals(is.underground) && p2 < -0.35 && d > 14) return DRIPSTONE;
		return STONE;
	}

	private static BlockState top(Style st, Noise n, int x, int y, int z, Island is) {
		double v = n.noise2(x * 0.08 + 13, z * 0.08 - 7);
		return switch (st) {
			case GRASSY -> GRASS;
			case FOREST_FLOOR -> {
				if (is.biome.contains("taiga")) yield v > 0.35 ? PODZOL : (v < -0.45 ? COARSE_DIRT : GRASS);
				if (is.biome.contains("jungle")) yield v > 0.5 ? PODZOL : GRASS;
				yield GRASS;
			}
			case SAVANNA -> v > 0.4 ? COARSE_DIRT : GRASS;
			case DESERT, BEACH -> SAND;
			case BADLANDS -> v > 0.45 ? COARSE_DIRT : RED_SAND;
			case SNOWY -> GRASS;
			case ICE -> SNOW_BLOCK;
			case MOUNTAIN -> {
				if (y >= 138) yield v > 0.45 ? POWDER_SNOW : SNOW_BLOCK;
				yield v > 0.3 ? SNOW_BLOCK : STONE;
			}
			case STONY -> v > 0.5 ? CALCITE : (v < -0.5 ? GRAVEL : STONE);
			case MUSHROOM -> MYCELIUM;
			case MUD -> MUD;
			case SWAMP -> GRASS;
			case NETHER_WASTES -> v > 0.55 ? GRAVEL : NETHERRACK;
			case SOUL_SAND_VALLEY -> v > 0 ? SOUL_SAND : SOUL_SOIL;
			case CRIMSON -> CRIMSON_NYLIUM;
			case WARPED -> WARPED_NYLIUM;
			case BASALT_DELTAS -> v > 0.1 ? BASALT : BLACKSTONE;
		};
	}

	private static BlockState under(Style st, Noise n, int x, int y, int z, int sub, Column c) {
		return switch (st) {
			case GRASSY, FOREST_FLOOR, SAVANNA, SNOWY, SWAMP, MUSHROOM -> DIRT;
			case DESERT, BEACH -> SAND;
			case BADLANDS -> sub <= 1 ? RED_SAND : null;
			case ICE -> sub <= 1 ? SNOW_BLOCK : (Noise.unit(Noise.hash(c.island.seed(), x, y, z)) < 0.5 ? PACKED_ICE : DIRT);
			case MOUNTAIN, STONY -> null;
			case MUD -> sub <= 3 ? MUD : (sub <= 4 ? CLAY : null);
			case NETHER_WASTES, CRIMSON, WARPED -> NETHERRACK;
			case SOUL_SAND_VALLEY -> SOUL_SOIL;
			case BASALT_DELTAS -> Noise.unit(Noise.hash(c.island.seed(), x, y, z)) < 0.5 ? BASALT : BLACKSTONE;
		};
	}

	private static BlockState fluidFloor(Column c, Style st, Noise n, int x, int z) {
		if (c.fluid == Column.FLUID_LAVA) return c.island.nether ? MAGMA : (st == Style.DESERT ? SANDSTONE : STONE);
		double v = n.noise2(x * 0.15, z * 0.15);
		return switch (st) {
			case BEACH -> v > 0.45 ? GRAVEL : (v < -0.5 ? CLAY : SAND);
			case MUD, SWAMP -> v > 0.2 ? CLAY : MUD;
			case DESERT, BADLANDS -> SAND;
			default -> v > 0.3 ? CLAY : (v < -0.3 ? SAND : DIRT);
		};
	}

	private static BlockState netherBody(Column c, Style st, Noise n, int x, int y, int z) {
		double p1 = n.noise3(x / 13.0, y / 10.0, z / 13.0);
		double p2 = n.noise3(x / 13.0 + 51.7, y / 10.0 - 27.3, z / 13.0 + 3.1);
		double thick = c.landTop - c.bottom;
		boolean deep = c.island.kind == Island.Kind.MAIN && y < c.bottom + thick * 0.3;
		if (st == Style.BASALT_DELTAS) {
			if (p1 > 0.3) return BASALT;
			if (p1 < -0.3) return BLACKSTONE;
			if (p2 > 0.6) return MAGMA;
			return NETHERRACK;
		}
		if (deep && p1 > 0.35) return BLACKSTONE;
		if (p2 > 0.62) return st == Style.SOUL_SAND_VALLEY ? SOUL_SOIL : GRAVEL;
		if (p2 < -0.62) return st == Style.SOUL_SAND_VALLEY ? BASALT : SOUL_SAND;
		if (p1 < -0.6) return MAGMA;
		if (st == Style.CRIMSON && p1 > 0.65 && c.landTop - y < 10) return NETHER_WART_BLOCK;
		if (st == Style.WARPED && p1 > 0.65 && c.landTop - y < 10) return WARPED_WART_BLOCK;
		return NETHERRACK;
	}

	/** Kann ein Erz diesen Block ersetzen? Gibt die Erz-Variante zurück oder null. */
	static boolean isStoneLike(BlockState s) {
		return s == STONE || s == GRANITE || s == DIORITE || s == ANDESITE || s == TUFF;
	}

	static boolean isDeepslate(BlockState s) {
		return s == DEEPSLATE;
	}

	static boolean isNetherBase(BlockState s) {
		return s == NETHERRACK;
	}

	static boolean isNetherHard(BlockState s) {
		return s == NETHERRACK || s == BASALT || s == BLACKSTONE;
	}

	static BlockState obsidian() {
		return OBSIDIAN;
	}

	static BlockState rootedDirt() {
		return ROOTED_DIRT;
	}

	static BlockState moss() {
		return MOSS;
	}
}
