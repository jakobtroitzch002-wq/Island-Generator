#!/usr/bin/env bash
# Hilfsskript fuer die Entwicklung: listet die Signaturen einiger Minecraft-Klassen auf,
# damit die Mod gegen die exakte API von 26.3 geschrieben werden kann.
set -u
JAR=""
for j in $(find "$HOME/.gradle" .gradle build -name '*.jar' 2>/dev/null | grep -i minecraft); do
  if unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/world/level/chunk/ChunkGenerator.class'; then
    JAR="$j"; break
  fi
done
echo "JAR=$JAR"
[ -z "$JAR" ] && exit 0
CLASSES="
net.minecraft.world.level.chunk.ChunkGenerator
net.minecraft.world.level.chunk.ChunkAccess
net.minecraft.world.level.chunk.LevelChunkSection
net.minecraft.world.level.ChunkPos
net.minecraft.world.level.levelgen.Heightmap
net.minecraft.world.level.biome.BiomeSource
net.minecraft.world.level.biome.BiomeResolver
net.minecraft.world.level.biome.FixedBiomeSource
net.minecraft.world.level.levelgen.structure.placement.StructurePlacement
net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement
net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType
net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType
net.minecraft.world.level.chunk.ChunkGeneratorStructureState
net.minecraft.world.level.NaturalSpawner
net.minecraft.server.level.WorldGenRegion
net.minecraft.world.level.NoiseColumn
net.minecraft.world.level.levelgen.WorldgenRandom
net.minecraft.world.level.levelgen.RandomSupport
net.minecraft.world.level.levelgen.FlatLevelSource
net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator
net.minecraft.core.registries.BuiltInRegistries
net.minecraft.world.level.levelgen.placement.PlacedFeature
"
for c in $CLASSES; do
  echo "=================== $c"
  javap -p -cp "$JAR" "$c" 2>&1 | grep -v '^Compiled'
done
echo "=================== MinecraftServer (Auszug)"
javap -p -cp "$JAR" net.minecraft.server.MinecraftServer 2>&1 | grep -iE 'seed|WorldGen|overworld|getCommands|halt|createCommandSourceStack|registryAccess' 
echo "=================== ServerLevel (Auszug)"
javap -p -cp "$JAR" net.minecraft.server.level.ServerLevel 2>&1 | grep -iE 'spawn|respawn|getSeed|getChunk\(' 
echo "=================== Commands (Auszug)"
javap -p -cp "$JAR" net.minecraft.commands.Commands 2>&1 | grep -iE 'perform'
echo "=================== Level / LevelReader (Auszug)"
javap -p -cp "$JAR" net.minecraft.world.level.Level 2>&1 | grep -iE 'getChunk|getHeight|getBlockState'
javap -p -cp "$JAR" net.minecraft.world.level.LevelReader 2>&1 | grep -iE 'getChunk|getHeight'
echo "=================== Registry (Auszug)"
javap -p -cp "$JAR" net.minecraft.core.Registry 2>&1 | grep -iE 'getValue|getKey|get\('
echo "=================== WorldGenSettings / WorldOptions"
javap -p -cp "$JAR" net.minecraft.world.level.levelgen.WorldGenSettings 2>&1
javap -p -cp "$JAR" net.minecraft.world.level.levelgen.WorldOptions 2>&1 | grep -i seed
echo "=================== LevelData\$RespawnData"
javap -p -cp "$JAR" 'net.minecraft.world.level.storage.LevelData$RespawnData' 2>&1 | head -20
