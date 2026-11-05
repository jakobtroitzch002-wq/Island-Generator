package dev.jakob.waystones.client;

import dev.jakob.waystones.Net;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Einstellungen für den Besitzer: Name, öffentlich/privat, Erlaubnisse, Besitz übergeben. */
public class SettingsScreen extends Screen {
	private static final int W = 300, H = 230;
	private final WaystoneScreen parent;
	private Net.OpenPayload data;
	private EditBox nameBox, allowBox, transferBox;
	private String nameDraft, allowDraft = "", transferDraft = "";
	private boolean confirmTransfer;

	public SettingsScreen(WaystoneScreen parent, Net.OpenPayload data) {
		super(Component.literal("Wegstein-Einstellungen"));
		this.parent = parent;
		this.data = data;
		this.nameDraft = data.currentName();
	}

	public String key() {
		return data.current();
	}

	public void update(Net.OpenPayload d) {
		this.data = d;
		if (nameBox != null) nameDraft = nameBox.getValue();
		if (allowBox != null) allowDraft = allowBox.getValue();
		if (transferBox != null) transferDraft = transferBox.getValue();
		parent.update(d);
		if (!d.owner()) {
			minecraft.gui.setScreen(parent);
			return;
		}
		rebuildWidgets();
	}

	private void act(String action, String text) {
		WaystonesClient.send(action, data.current(), "", text);
	}

	@Override
	protected void init() {
		int l = (width - W) / 2, t = (height - H) / 2;
		int y = t + 26;

		nameBox = addRenderableWidget(new EditBox(font, l + 10, y + 10, 190, 18, Component.literal("Name")));
		nameBox.setMaxLength(32);
		nameBox.setValue(nameDraft);
		addRenderableWidget(Button.builder(Component.literal("Umbenennen"), b -> act("rename", nameBox.getValue()))
				.bounds(l + 206, y + 9, 84, 20).build());

		y += 36;
		addRenderableWidget(Button.builder(Component.literal(data.currentPublic() ? "§aÖffentlich" : "§6Privat"),
				b -> act(data.currentPublic() ? "private" : "public", "")).bounds(l + 10, y + 10, 120, 20).build());

		y += 36;
		allowBox = addRenderableWidget(new EditBox(font, l + 10, y + 10, 190, 18, Component.literal("Spielername")));
		allowBox.setMaxLength(16);
		allowBox.setHint(Component.literal("Spielername"));
		allowBox.setValue(allowDraft);
		addRenderableWidget(Button.builder(Component.literal("Erlauben"), b -> {
			act("allow", allowBox.getValue());
			allowBox.setValue("");
		}).bounds(l + 206, y + 9, 84, 20).build());

		// erlaubte Spieler als kleine Knöpfe zum Entfernen
		int bx = l + 10, byy = y + 32;
		for (String name : data.allowed()) {
			int w = font.width(name) + 22;
			if (bx + w > l + W - 10) {
				bx = l + 10;
				byy += 22;
			}
			if (byy > y + 54) break;
			addRenderableWidget(Button.builder(Component.literal(name + " §c✕"), b -> act("disallow", name))
					.bounds(bx, byy, w, 18).build()).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Erlaubnis entziehen")));
			bx += w + 4;
		}

		y = t + H - 64;
		transferBox = addRenderableWidget(new EditBox(font, l + 10, y + 10, 190, 18, Component.literal("Neuer Besitzer")));
		transferBox.setMaxLength(16);
		transferBox.setHint(Component.literal("Spielername (online)"));
		transferBox.setValue(transferDraft);
		addRenderableWidget(Button.builder(Component.literal(confirmTransfer ? "§cSicher?" : "Übergeben"), b -> {
			if (!confirmTransfer) {
				confirmTransfer = true;
				transferDraft = transferBox.getValue();
				nameDraft = nameBox.getValue();
				allowDraft = allowBox.getValue();
				rebuildWidgets();
				return;
			}
			confirmTransfer = false;
			act("transfer", transferBox.getValue());
		}).bounds(l + 206, y + 9, 84, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Zurück"), b -> minecraft.gui.setScreen(parent))
				.bounds(l + W / 2 - 50, t + H - 24, 100, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		int l = (width - W) / 2, t = (height - H) / 2;
		g.fill(l, t, l + W, t + H, 0xE0181420);
		g.outline(l, t, W, H, 0xFF8A5CC8);
		g.fill(l + 1, t + 1, l + W - 1, t + 18, 0xFF2A2038);
		g.centeredText(font, "§d⚙ §fEinstellungen: " + data.currentName(), l + W / 2, t + 5, 0xFFFFFFFF);
		int y = t + 26;
		g.text(font, "§7Name", l + 10, y, 0xFFFFFFFF);
		y += 36;
		g.text(font, "§7Zugang " + (data.currentPublic() ? "(jeder darf hin und weg)" : "(nur du und erlaubte Spieler)"), l + 10, y, 0xFFFFFFFF);
		y += 36;
		g.text(font, "§7Erlaubte Spieler (für privat)" + (data.allowed().isEmpty() ? ": §8keine" : ""), l + 10, y, 0xFFFFFFFF);
		y = t + H - 64;
		g.text(font, "§7Besitz übergeben", l + 10, y, 0xFFFFFFFF);
		super.extractRenderState(g, mx, my, delta);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
