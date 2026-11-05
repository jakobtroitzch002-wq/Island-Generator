package dev.jakob.waystones.client;

import dev.jakob.waystones.Net;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class WaystonesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(Net.OpenPayload.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			mc.execute(() -> {
				if (mc.gui.screen() instanceof SettingsScreen s && s.key().equals(payload.current())) {
					s.update(payload);
				} else if (mc.gui.screen() instanceof WaystoneScreen w && w.key().equals(payload.current())) {
					w.update(payload);
				} else {
					mc.gui.setScreen(new WaystoneScreen(payload));
				}
			});
		});
	}

	static void send(String action, String current, String target, String text) {
		ClientPlayNetworking.send(new Net.ActionPayload(action, current, target == null ? "" : target, text == null ? "" : text));
	}
}
