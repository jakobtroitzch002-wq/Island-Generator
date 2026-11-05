package dev.jakob.waystones;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Waystones implements ModInitializer {
	public static final String MOD_ID = "islandwaystones";
	public static final Logger LOGGER = LoggerFactory.getLogger("Island Waystones");

	public static final ResourceKey<Block> WAYSTONE_KEY = ResourceKey.create(Registries.BLOCK, id("waystone"));
	public static final WaystoneBlock WAYSTONE = new WaystoneBlock(BlockBehaviour.Properties.of()
			.setId(WAYSTONE_KEY)
			.mapColor(MapColor.DEEPSLATE)
			.sound(SoundType.DEEPSLATE_BRICKS)
			.strength(4.0F, 1200.0F)
			.noOcclusion()
			.noLootTable()
			.pushReaction(PushReaction.BLOCK)
			.lightLevel(s -> s.getValue(WaystoneBlock.STAGE) > 0 ? 11 : 3));

	private static WaystoneService service;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static WaystoneService service() {
		return service;
	}

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.BLOCK, WAYSTONE_KEY, WAYSTONE);

		PayloadTypeRegistry.clientboundPlay().register(Net.OpenPayload.TYPE, Net.OpenPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Net.ActionPayload.TYPE, Net.ActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Net.ActionPayload.TYPE, (payload, context) -> {
			if (service != null) service.action(context.player(), payload);
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			WaystoneStore store = WaystoneStore.load(server.getWorldPath(LevelResource.ROOT).resolve("islandwaystones.json"));
			service = new WaystoneService(server, store);
			LOGGER.info("Island Waystones aktiv: {} entdeckte Wegsteine", store.waystones.size());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (service != null) service.store().save();
			service = null;
		});

		// Nur der Besitzer darf einen Wegstein abbauen
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(state.getBlock() instanceof WaystoneBlock) || level.isClientSide() || service == null) return true;
			return player instanceof ServerPlayer sp && service.canBreak(sp, (ServerLevel) level, WaystoneBlock.lower(pos, state));
		});
	}
}
