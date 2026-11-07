package dev.jakob.waystones;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Alle Daten der Wegsteine (nur auf dem Server), gespeichert als JSON im Weltordner.
 * Ein Wegstein wird über seinen Schlüssel "dimension|x|y|z" (untere Hälfte) gefunden.
 */
public final class WaystoneStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Ein entdeckter Wegstein. */
	public static final class Info {
		public String key;
		public String dim;
		public int x, y, z;
		/** Eigener Name (null = Standardname aus Koordinaten und Finder). */
		public String name;
		public String finder;
		public String ownerUuid;
		public String ownerName;
		public boolean isPublic = true;
		/** Spielernamen mit Erlaubnis (für private Wegsteine). */
		public List<String> allowed = new ArrayList<>();
		/** Biom der Insel über dem Wegstein (für den Knopf im Biom-Design). */
		public String biome;

		public String displayName() {
			return name != null && !name.isBlank() ? name : "Wegstein bei " + x + ", " + z;
		}

		public boolean isOwner(String uuid) {
			return uuid.equals(ownerUuid);
		}

		public boolean canUse(String uuid, String playerName) {
			if (isPublic || isOwner(uuid)) return true;
			for (String a : allowed) if (a.equalsIgnoreCase(playerName)) return true;
			return false;
		}
	}

	/** Daten eines Spielers. */
	public static final class PlayerData {
		public Set<String> favorites = new LinkedHashSet<>();
		/** Zuletzt geöffnete Wegsteine, neueste zuerst. */
		public List<String> recent = new ArrayList<>();
		public long lastTeleport = Long.MIN_VALUE / 2;
		/** Wie oft der Spieler schon zu welchem Wegstein teleportiert ist (jedes Mal 1 Level billiger). */
		public java.util.Map<String, Integer> trips = new java.util.HashMap<>();
	}

	/** Ein offenes Portal an einem Wegstein. */
	public static final class Portal {
		public String target;
		/** Spielzeit, zu der das Portal schliesst (0 = offen, bis jemand durchgeht). */
		public long closeAt;
	}

	public Map<String, Info> waystones = new LinkedHashMap<>();
	public Map<String, PlayerData> players = new LinkedHashMap<>();
	public Map<String, Portal> portals = new LinkedHashMap<>();

	private transient Path file;

	public static String key(String dim, int x, int y, int z) {
		return dim + "|" + x + "|" + y + "|" + z;
	}

	public PlayerData player(String uuid) {
		return players.computeIfAbsent(uuid, u -> new PlayerData());
	}

	public static WaystoneStore load(Path file) {
		WaystoneStore s = null;
		try {
			if (Files.exists(file)) s = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), WaystoneStore.class);
		} catch (Exception e) {
			Waystones.LOGGER.error("Wegstein-Daten konnten nicht gelesen werden", e);
		}
		if (s == null) s = new WaystoneStore();
		if (s.waystones == null) s.waystones = new LinkedHashMap<>();
		if (s.players == null) s.players = new LinkedHashMap<>();
		if (s.portals == null) s.portals = new LinkedHashMap<>();
		s.file = file;
		return s;
	}

	public void save() {
		if (file == null) return;
		try {
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			Waystones.LOGGER.error("Wegstein-Daten konnten nicht gespeichert werden", e);
		}
	}
}
