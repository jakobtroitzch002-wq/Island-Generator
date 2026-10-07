package dev.jakob.islandgen;

import dev.jakob.islandgen.world.IslandBiomeSource;
import dev.jakob.islandgen.world.IslandChunkGenerator;
import dev.jakob.islandgen.world.IslandStructurePlacement;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IslandGen implements ModInitializer {
	public static final String MOD_ID = "islandgen";
	public static final Logger LOGGER = LoggerFactory.getLogger("Island Generator");

	public static StructurePlacementType<IslandStructurePlacement> ISLAND_PLACEMENT;

	private static volatile long seed;

	public static long seed() {
		return seed;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("islands"), IslandChunkGenerator.CODEC);
		Registry.register(BuiltInRegistries.BIOME_SOURCE, id("islands"), IslandBiomeSource.CODEC);
		ISLAND_PLACEMENT = Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, id("island"), () -> IslandStructurePlacement.CODEC);

		// Amethyst-Geoden auf Inselhöhe (die normalen Geoden liegen tief unten in der Leere).
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.LOCAL_MODIFICATIONS,
				ResourceKey.create(Registries.PLACED_FEATURE, id("island_geode")));

		// Der Seed wird vor dem Laden der Welten gesetzt; die Inselpositionen hängen davon ab.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			seed = server.getWorldGenSettings().options().seed();
			LOGGER.info("Island Generator aktiv (Seed {})", seed);
		});

		if (Boolean.getBoolean("islandgen.selftest")) {
			ServerLifecycleEvents.SERVER_STARTED.register(SelfTest::run);
		}
	}
}
