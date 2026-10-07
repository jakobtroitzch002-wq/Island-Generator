package dev.jakob.islandgen.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.jakob.islandgen.IslandGen;
import dev.jakob.islandgen.layout.Group;
import dev.jakob.islandgen.layout.Island;
import dev.jakob.islandgen.layout.Layout;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Platziert eine Struktur genau in der Mitte einer bestimmten Insel jeder Gruppe.
 * <p>
 * {@code role}: {@code none} (nie), {@code main_1}, {@code main_2}, {@code side_0} .. {@code side_6}, {@code stronghold}.
 * <p>
 * Erbt von RandomSpread, damit /locate und Enderaugen mit dem Raster (eine Zelle = ein "spacing") funktionieren.
 */
public final class IslandStructurePlacement extends RandomSpreadStructurePlacement {
	public static final MapCodec<IslandStructurePlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.fieldOf("role").forGetter(p -> p.role),
			Codec.BOOL.optionalFieldOf("nether", false).forGetter(p -> p.nether),
			Codec.INT.optionalFieldOf("salt", 0).forGetter(p -> p.saltValue),
			Codec.floatRange(0, 1).optionalFieldOf("frequency", 1f).forGetter(p -> p.frequencyValue)
	).apply(i, IslandStructurePlacement::new));

	private static final int CELL_CHUNKS = Layout.CELL / 16;

	private final String role;
	private final boolean nether;
	private final int saltValue;
	private final float frequencyValue;
	private final Island.Kind kind;
	private final int index;

	public IslandStructurePlacement(String role, boolean nether, int salt, float frequency) {
		super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, frequency, salt, Optional.empty(), CELL_CHUNKS, CELL_CHUNKS - 1, RandomSpreadType.LINEAR);
		this.role = role;
		this.nether = nether;
		this.saltValue = salt;
		this.frequencyValue = frequency;
		if (role.equals("main_1")) { kind = Island.Kind.MAIN; index = 0; }
		else if (role.equals("main_2")) { kind = Island.Kind.MAIN; index = 1; }
		else if (role.equals("stronghold")) { kind = Island.Kind.STRONGHOLD; index = 0; }
		else if (role.startsWith("side_")) { kind = Island.Kind.SIDE; index = Integer.parseInt(role.substring(5)); }
		else { kind = null; index = 0; }
	}

	/** Chunk der Inselmitte in der Zelle, in der (chunkX, chunkZ) liegt, oder null. */
	private ChunkPos target(int chunkX, int chunkZ) {
		if (kind == null) return null;
		Layout layout = Layout.get(IslandGen.seed(), nether);
		Group g = layout.groupAt(chunkX * 16 + 8, chunkZ * 16 + 8);
		Island is = g.find(kind, index);
		if (is == null) return null;
		return new ChunkPos(((int) Math.floor(is.cx)) >> 4, ((int) Math.floor(is.cz)) >> 4);
	}

	@Override
	public ChunkPos getPotentialStructureChunk(long seed, int chunkX, int chunkZ) {
		ChunkPos p = target(chunkX, chunkZ);
		// Ohne passende Insel: eine Position zurückgeben, die nie eine Struktur bekommt.
		return p != null ? p : new ChunkPos(chunkX + 1_000_000, chunkZ + 1_000_000);
	}

	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
		ChunkPos p = target(chunkX, chunkZ);
		return p != null && (p.getMinBlockX() >> 4) == chunkX && (p.getMinBlockZ() >> 4) == chunkZ;
	}

	@Override
	public StructurePlacementType<?> type() {
		return IslandGen.ISLAND_PLACEMENT;
	}
}
