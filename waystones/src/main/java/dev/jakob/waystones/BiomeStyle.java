package dev.jakob.waystones;

/** Symbol (Gegenstand) und Farbe des Knopfs je nach Biom der Insel. */
public final class BiomeStyle {
	private BiomeStyle() {
	}

	public record Style(String item, int color) {
	}

	public static Style of(String biome) {
		String b = biome == null ? "" : biome.replace("minecraft:", "");
		return switch (b) {
			case "plains", "sunflower_plains" -> new Style("minecraft:short_grass", 0x6FA33A);
			case "flower_forest", "meadow" -> new Style("minecraft:cornflower", 0x7FB86A);
			case "forest" -> new Style("minecraft:oak_sapling", 0x4E8A32);
			case "birch_forest" -> new Style("minecraft:birch_sapling", 0x8DB360);
			case "desert" -> new Style("minecraft:cactus", 0xD8C88A);
			case "savanna", "savanna_plateau" -> new Style("minecraft:acacia_sapling", 0xB0A048);
			case "snowy_plains", "snowy_taiga" -> new Style("minecraft:snowball", 0xDDE8F0);
			case "ice_spikes" -> new Style("minecraft:packed_ice", 0x9FC3F0);
			case "taiga", "old_growth_spruce_taiga", "old_growth_pine_taiga" -> new Style("minecraft:spruce_sapling", 0x3F6B48);
			case "jungle", "sparse_jungle" -> new Style("minecraft:jungle_sapling", 0x2F9A2A);
			case "bamboo_jungle" -> new Style("minecraft:bamboo", 0x5DA62A);
			case "swamp" -> new Style("minecraft:lily_pad", 0x5A6B3A);
			case "mangrove_swamp" -> new Style("minecraft:mangrove_propagule", 0x4F6B30);
			case "dark_forest" -> new Style("minecraft:dark_oak_sapling", 0x2C4A22);
			case "pale_garden" -> new Style("minecraft:pale_oak_sapling", 0xB8BAB0);
			case "badlands", "wooded_badlands", "eroded_badlands" -> new Style("minecraft:dead_bush", 0xC0612C);
			case "cherry_grove" -> new Style("minecraft:cherry_sapling", 0xF0A8C8);
			case "mushroom_fields" -> new Style("minecraft:red_mushroom", 0x9A6A9C);
			case "deep_lukewarm_ocean", "warm_ocean", "beach" -> new Style("minecraft:tube_coral", 0x3B8FD0);
			case "jagged_peaks", "stony_peaks" -> new Style("minecraft:snow_block", 0x9AA0A8);
			case "windswept_gravelly_hills" -> new Style("minecraft:gravel", 0x8A8A8A);
			case "lush_caves" -> new Style("minecraft:azalea", 0x5E8F34);
			default -> new Style("minecraft:grass_block", 0x6FA33A);
		};
	}
}
