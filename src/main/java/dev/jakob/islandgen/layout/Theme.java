package dev.jakob.islandgen.layout;

/**
 * Biom-Themen einer Inselgruppe. Die Hauptinsel(n) bekommen {@link #mainBiome}, Neben- und
 * Satelliteninseln zufällig eines der {@link #sideBiomes}.
 */
public enum Theme {
	// ---- Overworld (15)
	PLAINS("minecraft:plains", new String[]{"minecraft:flower_forest", "minecraft:forest", "minecraft:birch_forest", "minecraft:sunflower_plains"}, Pools.WATER, 2.5, 40, 62),
	DESERT("minecraft:desert", new String[]{"minecraft:desert"}, Pools.LAVA, 3.0, 40, 62),
	SAVANNA("minecraft:savanna", new String[]{"minecraft:savanna", "minecraft:savanna_plateau"}, Pools.WATER, 2.5, 40, 60),
	SNOWY("minecraft:snowy_plains", new String[]{"minecraft:snowy_taiga", "minecraft:snowy_plains"}, Pools.WATER, 2.0, 40, 60),
	TAIGA("minecraft:taiga", new String[]{"minecraft:old_growth_spruce_taiga", "minecraft:taiga", "minecraft:old_growth_pine_taiga"}, Pools.WATER, 3.5, 40, 60),
	JUNGLE("minecraft:jungle", new String[]{"minecraft:bamboo_jungle", "minecraft:sparse_jungle"}, Pools.WATER, 4.0, 42, 62),
	SWAMP("minecraft:swamp", new String[]{"minecraft:swamp"}, Pools.WATER_MANY, 1.2, 40, 58),
	MANGROVE("minecraft:mangrove_swamp", new String[]{"minecraft:mangrove_swamp"}, Pools.WATER_MANY, 1.2, 40, 58),
	DARK_FOREST("minecraft:dark_forest", new String[]{"minecraft:pale_garden", "minecraft:dark_forest"}, Pools.WATER, 2.5, 62, 72),
	BADLANDS("minecraft:badlands", new String[]{"minecraft:wooded_badlands", "minecraft:eroded_badlands"}, Pools.LAVA, 5.0, 42, 62),
	CHERRY("minecraft:cherry_grove", new String[]{"minecraft:cherry_grove", "minecraft:meadow"}, Pools.WATER, 4.0, 40, 58),
	MUSHROOM("minecraft:mushroom_fields", new String[]{"minecraft:mushroom_fields"}, Pools.WATER, 2.5, 38, 56),
	ICE("minecraft:ice_spikes", new String[]{"minecraft:snowy_plains", "minecraft:ice_spikes"}, Pools.WATER, 2.0, 40, 60),
	OCEAN("minecraft:deep_lukewarm_ocean", new String[]{"minecraft:beach", "minecraft:warm_ocean"}, Pools.NONE, 1.0, 68, 76),
	MOUNTAIN("minecraft:jagged_peaks", new String[]{"minecraft:meadow", "minecraft:stony_peaks"}, Pools.NONE, 18.0, 42, 58),

	// ---- Nether (5)
	NETHER_WASTES("minecraft:nether_wastes", new String[]{"minecraft:nether_wastes"}, Pools.LAVA, 4.0, 38, 56),
	SOUL_SAND_VALLEY("minecraft:soul_sand_valley", new String[]{"minecraft:soul_sand_valley"}, Pools.NONE, 3.0, 38, 56),
	CRIMSON_FOREST("minecraft:crimson_forest", new String[]{"minecraft:crimson_forest"}, Pools.NONE, 3.0, 38, 56),
	WARPED_FOREST("minecraft:warped_forest", new String[]{"minecraft:warped_forest"}, Pools.NONE, 3.0, 38, 56),
	BASALT_DELTAS("minecraft:basalt_deltas", new String[]{"minecraft:basalt_deltas"}, Pools.LAVA_MANY, 4.0, 38, 56);

	public enum Pools { NONE, WATER, WATER_MANY, LAVA, LAVA_MANY }

	public static final Theme[] OVERWORLD = {PLAINS, DESERT, SAVANNA, SNOWY, TAIGA, JUNGLE, SWAMP, MANGROVE, DARK_FOREST, BADLANDS, CHERRY, MUSHROOM, ICE, OCEAN, MOUNTAIN};
	public static final Theme[] NETHER = {NETHER_WASTES, SOUL_SAND_VALLEY, CRIMSON_FOREST, WARPED_FOREST, BASALT_DELTAS};

	public final String mainBiome;
	public final String[] sideBiomes;
	public final Pools pools;
	public final double hillAmp;
	public final int mainRadiusMin, mainRadiusMax;

	Theme(String mainBiome, String[] sideBiomes, Pools pools, double hillAmp, int mainRadiusMin, int mainRadiusMax) {
		this.mainBiome = mainBiome;
		this.sideBiomes = sideBiomes;
		this.pools = pools;
		this.hillAmp = hillAmp;
		this.mainRadiusMin = mainRadiusMin;
		this.mainRadiusMax = mainRadiusMax;
	}

	public boolean isNether() {
		return ordinal() >= NETHER_WASTES.ordinal();
	}
}
