package dev.jakob.islandgen.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.jakob.islandgen.IslandGen;
import dev.jakob.islandgen.layout.Column;
import dev.jakob.islandgen.layout.Group;
import dev.jakob.islandgen.layout.Island;
import dev.jakob.islandgen.layout.Layout;
import dev.jakob.islandgen.layout.Theme;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Biome kommen direkt aus dem Insel-Layout: jede Insel hat ein Biom, manche Hauptinseln
 * zusätzlich ein Höhlenbiom im Inneren (Lush Caves, Dripstone Caves, Deep Dark ...).
 */
public final class IslandBiomeSource extends BiomeSource {
	public static final MapCodec<IslandBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			RegistryOps.retrieveGetter(Registries.BIOME),
			Codec.BOOL.optionalFieldOf("nether", false).forGetter(s -> s.nether)
	).apply(i, i.stable(IslandBiomeSource::new)));

	private static final ThreadLocal<Column> COLUMN = ThreadLocal.withInitial(Column::new);

	private final boolean nether;
	private final Map<String, Holder<Biome>> biomes = new LinkedHashMap<>();
	private final Holder<Biome> fallback;

	public IslandBiomeSource(HolderGetter<Biome> getter, boolean nether) {
		this.nether = nether;
		Set<String> ids = new LinkedHashSet<>();
		for (Theme t : nether ? Theme.NETHER : Theme.OVERWORLD) {
			ids.add(t.mainBiome);
			ids.addAll(List.of(t.sideBiomes));
		}
		if (!nether) {
			ids.addAll(List.of("minecraft:plains", "minecraft:stony_peaks", "minecraft:lush_caves",
					"minecraft:dripstone_caves", "minecraft:deep_dark", "minecraft:sulfur_caves"));
		}
		for (String id : ids) {
			Optional<? extends Holder<Biome>> h = getter.get(ResourceKey.create(Registries.BIOME, Identifier.parse(id)));
			if (h.isPresent()) biomes.put(id, h.get());
			else IslandGen.LOGGER.warn("Biom {} existiert nicht, wird ersetzt", id);
		}
		this.fallback = biomes.get(nether ? "minecraft:nether_wastes" : "minecraft:plains");
	}

	public boolean isNether() {
		return nether;
	}

	@Override
	public MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return biomes.values().stream();
	}

	@Override
	public BiomeResolver createResolver(Climate.Sampler sampler) {
		return (qx, qy, qz) -> biomeAt(QuartPos.toBlock(qx) + 2, QuartPos.toBlock(qy) + 2, QuartPos.toBlock(qz) + 2);
	}

	public Holder<Biome> biomeAt(int x, int y, int z) {
		Layout layout = Layout.get(IslandGen.seed(), nether);
		Group g = layout.groupAt(x, z);
		Column c = COLUMN.get();
		if (g.sample(x, z, c)) {
			Island is = c.island;
			if (is.underground != null && y < c.landTop - 16 && y > c.bottom + 3) {
				Holder<Biome> u = biomes.get(is.underground);
				if (u != null) return u;
			}
			return biomes.getOrDefault(is.biome, fallback);
		}
		if (g.isEmpty()) return fallback;
		return biomes.getOrDefault(g.islands.get(0).biome, fallback);
	}

	@Override
	public void addDebugInfo(List<String> lines, BlockPos pos, Climate.Sampler sampler) {
		Layout layout = Layout.get(IslandGen.seed(), nether);
		Group g = layout.groupAt(pos.getX(), pos.getZ());
		lines.add("Island group: cell " + g.cellX + "," + g.cellZ + " theme " + g.theme);
	}
}
