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
echo "################ WAYSTONE-API"
for c in net.minecraft.world.level.block.Portal 'net.minecraft.world.level.block.Portal$Transition' net.minecraft.world.level.portal.TeleportTransition \
  net.minecraft.world.level.block.NetherPortalBlock net.minecraft.world.level.block.DoublePlantBlock net.minecraft.world.level.block.TallFlowerBlock \
  net.minecraft.world.level.block.Block net.minecraft.world.level.block.state.BlockBehaviour 'net.minecraft.world.level.block.state.BlockBehaviour$Properties' \
  net.minecraft.client.gui.GuiGraphicsExtractor net.minecraft.client.gui.screens.Screen net.minecraft.client.gui.components.Button 'net.minecraft.client.gui.components.Button$Builder' \
  net.minecraft.client.gui.components.EditBox net.minecraft.client.gui.components.AbstractWidget net.minecraft.client.gui.Font \
  net.minecraft.world.level.storage.LevelResource net.minecraft.world.entity.player.Player net.minecraft.server.level.ServerPlayer \
  net.minecraft.world.entity.Entity net.minecraft.world.phys.shapes.Shapes net.minecraft.world.level.block.state.properties.BlockStateProperties \
  net.minecraft.client.renderer.chunk.ChunkSectionLayer net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap \
  net.minecraft.core.particles.ParticleTypes net.minecraft.sounds.SoundEvents net.minecraft.world.level.Level net.minecraft.server.level.ServerLevel; do
  echo "=================== $c"
  javap -p -cp "$JAR" "$c" 2>&1 | grep -v '^Compiled' | grep -v 'lambda\$' | grep -vE '^\s*(private|static final)' | head -400
done
FAPI=$(find "$HOME/.gradle" -name 'fabric-rendering-v1*.jar' 2>/dev/null | head -1)
echo "FAPI rendering jar: $FAPI"
[ -n "$FAPI" ] && javap -p -cp "$FAPI" net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap 2>&1 | head -30
FB=$(find "$HOME/.gradle" -name 'fabric-events-interaction-v0*.jar' 2>/dev/null | head -1)
[ -n "$FB" ] && javap -p -cp "$FB" net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents 'net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents$Before' 2>&1 | head -30
echo "################ CLIENT-API 2"
javap -p -cp "$JAR" net.minecraft.client.Minecraft 2>&1 | grep -iE 'screen|gui|execute|font|player' | head -60
for c in net.minecraft.client.input.MouseButtonEvent net.minecraft.world.level.material.PushReaction net.minecraft.client.gui.screens.ScreenManager net.minecraft.client.gui.Gui; do
  echo "=================== $c"
  javap -p -cp "$JAR" "$c" 2>&1 | grep -v 'lambda\$' | grep -iE 'screen|public|enum|record' | head -60
done
javap -p -cp "$JAR" net.minecraft.client.gui.screens.Screen 2>&1 | grep -iE 'minecraft|onClose|mouseScrolled|tick' 
