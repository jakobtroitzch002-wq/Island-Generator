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
import net.minecraft.server.level.WorldGenRegion;
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
		TerrainBuilder.fill(chunk, layout());
		return CompletableFuture.completedFuture(chunk);
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {
		ChunkPos chunkPos = region.getCenter();
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
