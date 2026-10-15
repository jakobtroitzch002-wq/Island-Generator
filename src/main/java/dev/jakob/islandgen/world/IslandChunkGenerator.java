package dev.jakob.islandgen.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.jakob.islandgen.IslandGen;
import dev.jakob.islandgen.layout.Column;
import dev.jakob.islandgen.layout.Layout;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.jspecify.annotations.Nullable;

/**
 * Der Insel-Weltgenerator für Overworld und Nether.
 */
public final class IslandChunkGenerator extends ChunkGenerator {
	public static final MapCodec<IslandChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
			Codec.BOOL.optionalFieldOf("nether", false).forGetter(g -> g.nether)
	).apply(i, i.stable(IslandChunkGenerator::new)));

	/** Generator-Bereich: Y 0 bis 256 (die Dimension selbst bleibt unverändert). */
	private static final int MIN_Y = 0, GEN_DEPTH = 256;

	private final boolean nether;

	public IslandChunkGenerator(BiomeSource biomeSource, boolean nether) {
		super(biomeSource);
		this.nether = nether;
	}

	private Layout layout() {
		return Layout.get(IslandGen.seed(), nether);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	@Override
	public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState, StructureManager structureManager,
			BiomeManager biomeManager, @Nullable WorldGenRegion carverBiomeRegion, Set<Holder<Biome>> possibleBiomes) {
		TerrainBuilder.fill(chunk, layout(), structureManager);
		return CompletableFuture.completedFuture(chunk);
	}

	/** Tiere, die immer auf der Startinsel stehen (Chunk 0,0, ausserhalb des Camps in der Mitte). */
	private record StarterAnimal(EntityType<?> type, int x, int z) {
	}

	private static final StarterAnimal[] STARTER_ANIMALS = {
			new StarterAnimal(EntityTypes.SHEEP, 8, 9),
			new StarterAnimal(EntityTypes.SHEEP, 10, 12),
			new StarterAnimal(EntityTypes.SHEEP, 12, 8),
			new StarterAnimal(EntityTypes.SHEEP, 13, 12),
			new StarterAnimal(EntityTypes.COW, 14, 5),
			new StarterAnimal(EntityTypes.COW, 6, 14),
			new StarterAnimal(EntityTypes.CHICKEN, 11, 3),
			new StarterAnimal(EntityTypes.CHICKEN, 4, 11),
			new StarterAnimal(EntityTypes.PIG, 14, 10),
			new StarterAnimal(EntityTypes.PIG, 9, 14),
	};

	private void spawnStarterAnimals(WorldGenRegion region) {
		ServerLevel level = region.getLevel();
		int spawned = 0;
		for (StarterAnimal a : STARTER_ANIMALS) {
			try {
				Column c = TerrainBuilder.sampleColumn(layout(), a.x(), a.z());
				if (c.island == null) continue;
				int y = region.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, a.x(), a.z());
				if (y < c.solidTop) y = c.solidTop + 1;
				Entity entity = a.type().create(level, EntitySpawnReason.CHUNK_GENERATION);
				if (entity == null) continue;
				entity.snapTo(a.x() + 0.5, y, a.z() + 0.5, (a.x() * 37 + a.z() * 11) % 360, 0.0F);
				if (entity instanceof Mob mob) {
					mob.finalizeSpawn(region, region.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.CHUNK_GENERATION, null);
					mob.setPersistenceRequired();
				}
				region.addFreshEntityWithPassengers(entity);
				spawned++;
			} catch (Exception e) {
				IslandGen.LOGGER.warn("Starttier {} konnte nicht gespawnt werden", a.type(), e);
			}
		}
		IslandGen.LOGGER.info("Startinsel: {} Tiere gespawnt", spawned);
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {
		ChunkPos chunkPos = region.getCenter();
		if (!nether && chunkPos.x() == 0 && chunkPos.z() == 0) spawnStarterAnimals(region);
		int x = chunkPos.getMinBlockX() + 8, z = chunkPos.getMinBlockZ() + 8;
		Column c = TerrainBuilder.sampleColumn(layout(), x, z);
		if (c.island == null) return;
		BlockPos at = new BlockPos(x, c.highest() + 1, z);
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
		random.setDecorationSeed(region.getSeed(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
		NaturalSpawner.spawnMobsForChunkGeneration(region, at, chunkPos, random);
	}

	@Override
	public int getGenDepth() {
		return GEN_DEPTH;
	}

	@Override
	public int getSeaLevel() {
		return nether ? 32 : Layout.OCEAN_WATER + 1;
	}

	@Override
	public int getMinY() {
		return MIN_Y;
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
		boolean solidOnly = type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG;
		return TerrainBuilder.surfaceHeight(layout(), x, z, solidOnly, MIN_Y);
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
		return new NoiseColumn(MIN_Y, TerrainBuilder.columnStates(layout(), x, z, MIN_Y, GEN_DEPTH));
	}

	@Override
	public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos pos, SamplerContext samplerContext) {
		Column c = TerrainBuilder.sampleColumn(layout(), pos.getX(), pos.getZ());
		list.add("Island: " + (c.island == null ? "-" : c.island.toString()));
	}
}
