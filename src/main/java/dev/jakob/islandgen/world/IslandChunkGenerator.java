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
import dev.jakob.islandgen.layout.Geode;
import dev.jakob.islandgen.layout.Group;
import dev.jakob.islandgen.layout.Island;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
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
		if (!nether) spawnGeodeAxolotls(region, chunkPos);
		if (!nether) decorateMineshafts(region, chunkPos);
		int x = chunkPos.getMinBlockX() + 8, z = chunkPos.getMinBlockZ() + 8;
		Column c = TerrainBuilder.sampleColumn(layout(), x, z);
		if (c.island == null) return;
		BlockPos at = new BlockPos(x, c.highest() + 1, z);
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
		random.setDecorationSeed(region.getSeed(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
		NaturalSpawner.spawnMobsForChunkGeneration(region, at, chunkPos, random);
	}

	/**
	 * Auf der Startinsel (und ihren kleinen Satelliten) spawnen keine Monster natürlich.
	 * Spawner, Beschwörungen usw. funktionieren weiterhin normal.
	 */
	@Override
	public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Level level, StructureManager structures, MobCategory category, BlockPos pos) {
		if (!nether && category == MobCategory.MONSTER && isStarterArea(pos.getX(), pos.getZ())) {
			return WeightedList.of(List.<Weighted<MobSpawnSettings.SpawnerData>>of());
		}
		// In den Geoden spawnen nur Axolotl, sonst nichts
		if (!nether && category != MobCategory.AXOLOTLS && geodeAt(pos) != null) {
			return WeightedList.of(List.<Weighted<MobSpawnSettings.SpawnerData>>of());
		}
		return super.getMobsAt(level, structures, category, pos);
	}

	private Geode geodeAt(BlockPos pos) {
		Group g = layout().groupAt(pos.getX(), pos.getZ());
		for (Island is : g.islands) {
			Geode geo = is.geode;
			if (geo != null && geo.inBox(pos.getX(), pos.getZ()) && geo.isInside(pos.getX(), pos.getY(), pos.getZ(), 0.45)) return geo;
		}
		return null;
	}

	/** Truhen mit Minenschacht-Beute und ein Höhlenspinnen-Spawner in den Minenschächten der Tafelberge. */
	private void decorateMineshafts(WorldGenRegion region, ChunkPos chunkPos) {
		Group g = layout().groupAt(chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ());
		for (Island is : g.islands) {
			dev.jakob.islandgen.layout.Mineshaft m = is.mineshaft;
			if (m == null) continue;
			try {
				for (int[] p : m.chests) {
					if ((p[0] >> 4) != chunkPos.x() || (p[2] >> 4) != chunkPos.z()) continue;
					BlockPos pos = new BlockPos(p[0], p[1], p[2]);
					if (!region.getBlockState(pos).isAir() || region.getBlockState(pos.below()).isAir()) continue;
					region.setBlock(pos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 2);
					net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(region, region.getRandom(), pos,
							net.minecraft.world.level.storage.loot.BuiltInLootTables.ABANDONED_MINESHAFT);
				}
				int[] sp = m.spawner;
				if (sp != null && (sp[0] >> 4) == chunkPos.x() && (sp[2] >> 4) == chunkPos.z()) {
					BlockPos pos = new BlockPos(sp[0], sp[1], sp[2]);
					if (!region.getBlockState(pos.below()).isAir()) {
						region.setBlock(pos, net.minecraft.world.level.block.Blocks.SPAWNER.defaultBlockState(), 2);
						if (region.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner) {
							spawner.setEntityId(EntityTypes.CAVE_SPIDER, region.getRandom());
						}
					}
				}
			} catch (Exception e) {
				IslandGen.LOGGER.warn("Minenschacht-Truhen konnten nicht gesetzt werden", e);
			}
		}
	}

	/** Zwei Axolotl pro Geoden-Teich, beim Erzeugen des Chunks mit dem Teich. */
	private void spawnGeodeAxolotls(WorldGenRegion region, ChunkPos chunkPos) {
		Group g = layout().groupAt(chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ());
		for (Island is : g.islands) {
			Geode geo = is.geode;
			if (geo == null || ((int) Math.floor(geo.pondX) >> 4) != chunkPos.x() || ((int) Math.floor(geo.pondZ) >> 4) != chunkPos.z()) continue;
			if (!geo.pondOk()) continue;
			for (int i = 0; i < 2; i++) {
				try {
					Entity entity = EntityTypes.AXOLOTL.create(region.getLevel(), EntitySpawnReason.CHUNK_GENERATION);
					if (entity == null) continue;
					entity.snapTo(Math.floor(geo.pondX) + 0.5 + (i == 0 ? 0 : 0.4), geo.floorLevel - 2, Math.floor(geo.pondZ) + 0.5, i * 170, 0.0F);
					if (entity instanceof Mob mob) {
						mob.finalizeSpawn(region, region.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.CHUNK_GENERATION, null);
						mob.setPersistenceRequired();
					}
					region.addFreshEntityWithPassengers(entity);
				} catch (Exception e) {
					IslandGen.LOGGER.warn("Axolotl konnte nicht gespawnt werden", e);
				}
			}
		}
	}

	private boolean isStarterArea(int x, int z) {
		Group g = layout().group(0, 0);
		return Math.hypot(x - g.centerX, z - g.centerZ) <= g.reach + 4;
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
