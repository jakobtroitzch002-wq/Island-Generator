package dev.jakob.waystones.client;

import dev.jakob.waystones.Net;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Das Wegstein-Menü: Seite "Favoriten" und Seite "Alle". Jede Zeile hat links einen Stern, dann das Biom-Symbol,
 * den Namen und daneben die Koordinaten. Der zuletzt geöffnete Wegstein steht oben.
 */
public class WaystoneScreen extends Screen {
	static final int PANEL_W = 320;
	static final int ROW_H = 20;
	static final int ROWS = 6;

	private Net.OpenPayload data;
	private long openedAt;
	private boolean favoritesTab;
	private String selected;
	private int page;

	private Button teleportButton;

	public WaystoneScreen(Net.OpenPayload data) {
		super(Component.literal("Wegstein"));
		this.data = data;
		this.openedAt = System.currentTimeMillis();
		// Erste Seite: Favoriten – ausser es gibt noch keine
		this.favoritesTab = data.entries().stream().anyMatch(e -> e.favorite() && !e.key().equals(data.current()));
	}

	public String key() {
		return data.current();
	}

	public void update(Net.OpenPayload d) {
		this.data = d;
		this.openedAt = System.currentTimeMillis();
		rebuildWidgets();
	}

	int left() {
		return (width - PANEL_W) / 2;
	}

	int top() {
		return (height - (ROWS * ROW_H + 76)) / 2;
	}

	private List<Net.Entry> visible() {
		List<Net.Entry> list = new ArrayList<>();
		for (Net.Entry e : data.entries()) {
			if (favoritesTab && !e.favorite() && !e.key().equals(data.current())) continue;
			list.add(e);
		}
		list.sort(Comparator.<Net.Entry>comparingInt(e -> e.key().equals(data.current()) ? 0 : 1)
				.thenComparingInt(e -> e.recentIndex() < 0 ? Integer.MAX_VALUE : e.recentIndex())
				.thenComparing(e -> e.name().toLowerCase()));
		return list;
	}

	private int pages() {
		return Math.max(1, (visible().size() + ROWS - 1) / ROWS);
	}

	@Override
	protected void init() {
		int l = left(), t = top();
		page = Math.min(page, pages() - 1);

		addRenderableWidget(Button.builder(Component.literal("Favoriten"), b -> {
			favoritesTab = true;
			page = 0;
			rebuildWidgets();
		}).bounds(l + 6, t + 22, 100, 18).build()).active = !favoritesTab;
		addRenderableWidget(Button.builder(Component.literal("Alle"), b -> {
			favoritesTab = false;
			page = 0;
			rebuildWidgets();
		}).bounds(l + 110, t + 22, 100, 18).build()).active = favoritesTab;
		if (data.owner()) {
			addRenderableWidget(Button.builder(Component.literal("Einstellungen"), b -> minecraft.gui.setScreen(new SettingsScreen(this, data)))
					.bounds(l + PANEL_W - 106, t + 22, 100, 18).build());
		}

		List<Net.Entry> list = visible();
		int y = t + 46;
		for (int i = page * ROWS; i < Math.min(list.size(), (page + 1) * ROWS); i++) {
			addRenderableWidget(new Row(l + 6, y, PANEL_W - 12, ROW_H - 2, list.get(i)));
			y += ROW_H;
		}

		int by = t + 46 + ROWS * ROW_H + 4;
		addRenderableWidget(Button.builder(Component.literal("<"), b -> {
			page--;
			rebuildWidgets();
		}).bounds(l + 6, by, 20, 20).build()).active = page > 0;
		addRenderableWidget(Button.builder(Component.literal(">"), b -> {
			page++;
			rebuildWidgets();
		}).bounds(l + 62, by, 20, 20).build()).active = page < pages() - 1;

		teleportButton = addRenderableWidget(Button.builder(Component.literal("Teleportieren (5 Level)"), b -> {
			if (selected == null) return;
			WaystonesClient.send("tp", data.current(), selected, "");
			onClose();
		}).bounds(l + PANEL_W - 156, by, 150, 20).build());
		updateTeleportButton();
	}

	private int cooldownLeft() {
		return (int) Math.max(0, data.cooldownSeconds() - (System.currentTimeMillis() - openedAt) / 1000);
	}

	private void updateTeleportButton() {
		if (teleportButton == null) return;
		boolean exists = selected != null && data.entries().stream().anyMatch(e -> e.key().equals(selected));
		teleportButton.active = exists && (data.creative() || (data.level() >= 5 && cooldownLeft() == 0));
	}

	@Override
	public void tick() {
		updateTeleportButton();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		int l = left(), t = top(), h = ROWS * ROW_H + 76;
		g.fill(l, t, l + PANEL_W, t + h, 0xC0101010);
		g.centeredText(font, data.currentName(), l + PANEL_W / 2, t + 7, 0xFFFFFFFF);

		if (visible().stream().noneMatch(e -> !e.key().equals(data.current()))) {
			g.centeredText(font, favoritesTab ? "Noch keine Favoriten (Stern anklicken)"
					: "Noch keine anderen Wegsteine entdeckt", l + PANEL_W / 2, t + 46 + ROW_H + 8, 0xFFAAAAAA);
		}
		int by = t + 46 + ROWS * ROW_H + 4;
		g.centeredText(font, (page + 1) + "/" + pages(), l + 44, by + 6, 0xFFDDDDDD);

		String status = null;
		int cd = cooldownLeft();
		if (data.creative()) status = null;
		else if (cd > 0) status = "Noch " + cd + " s";
		else if (data.level() < 5) status = "Zu wenig Level";
		if (status != null) g.text(font, status, l + 90, by + 6, 0xFFAAAAAA);

		super.extractRenderState(g, mx, my, delta);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	static ItemStack icon(String id) {
		try {
			var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
			return new ItemStack(item == null || item == Items.AIR ? Items.GRASS_BLOCK : item);
		} catch (Exception e) {
			return new ItemStack(Items.GRASS_BLOCK);
		}
	}

	static int darken(int rgb, float f) {
		int r = (int) (((rgb >> 16) & 255) * f), gg = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
		return 0xFF000000 | r << 16 | gg << 8 | b;
	}

	/** Eine Zeile in der Liste, im Stil des Bioms. */
	private class Row extends AbstractWidget {
		private final Net.Entry e;
		private final ItemStack icon;
		private final boolean here;

		Row(int x, int y, int w, int h, Net.Entry e) {
			super(x, y, w, h, Component.literal(e.name()));
			this.e = e;
			this.icon = icon(e.icon());
			this.here = e.key().equals(data.current());
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
			Font f = Minecraft.getInstance().font;
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			boolean sel = e.key().equals(selected);
			g.fill(x, y, x + w, y + h, sel ? 0x60FFFFFF : (isHovered() && !here ? 0x30FFFFFF : 0x18FFFFFF));
			g.fill(x, y, x + 2, y + h, 0xFF000000 | e.color());
			boolean starHover = mx >= x + 4 && mx < x + 18 && my >= y && my < y + h;
			g.text(f, e.favorite() ? "★" : "☆", x + 6, y + (h - 8) / 2, e.favorite() ? 0xFFFFD84A : (starHover ? 0xFFFFFFFF : 0xFF808080));
			g.item(icon, x + 20, y + (h - 16) / 2);
			String coords = e.x() + ", " + e.y() + ", " + e.z();
			String name = e.name();
			int maxName = w - 44 - f.width(coords) - 10;
			while (f.width(name) > maxName && name.length() > 3) name = name.substring(0, name.length() - 1);
			if (!name.equals(e.name())) name = name + "…";
			g.text(f, name, x + 40, y + (h - 8) / 2, here ? 0xFF909090 : 0xFFFFFFFF);
			g.text(f, coords, x + w - 6 - f.width(coords), y + (h - 8) / 2, 0xFF909090);
			boolean rowHover = mx >= x + 18 && mx < x + w && my >= y && my < y + h;
			if (rowHover) g.setTooltipForNextFrame(f, Component.literal(here ? "Du stehst hier" : "Gefunden von " + e.finder() + (e.isPublic() ? "" : " (privat)")), mx, my);
			if (starHover) g.setTooltipForNextFrame(f, Component.literal(e.favorite() ? "Aus Favoriten entfernen" : "Zu Favoriten hinzufügen"), mx, my);
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			if (event.x() < getX() + 18) {
				WaystonesClient.send("fav", data.current(), e.key(), "");
				return;
			}
			if (here) return;
			selected = e.key();
			updateTeleportButton();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			defaultButtonNarrationText(out);
		}
	}
}
