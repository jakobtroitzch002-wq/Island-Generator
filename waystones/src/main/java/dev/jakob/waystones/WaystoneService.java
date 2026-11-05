package dev.jakob.waystones;

import dev.jakob.waystones.WaystoneStore.Info;
import dev.jakob.waystones.WaystoneStore.PlayerData;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Die Spiellogik der Wegsteine auf dem Server. */
public final class WaystoneService {
	public static final int COST_LEVELS = 5;
	public static final int COOLDOWN_TICKS = 30 * 20;
	/** Nachdem jemand durchgegangen ist, bleibt das Portal noch kurz offen, damit andere folgen können. */
	public static final int CLOSE_DELAY_TICKS = 5 * 20;
	/** Geht niemand durch, schliesst sich das Portal nach 15 Sekunden von selbst. */
	public static final int OPEN_TICKS = 15 * 20;

	private final MinecraftServer server;
	private final WaystoneStore store;

	WaystoneService(MinecraftServer server, WaystoneStore store) {
		this.server = server;
		this.store = store;
	}

	public WaystoneStore store() {
		return store;
	}

	private static String key(Level level, BlockPos low) {
		return WaystoneStore.key(level.dimension().identifier().toString(), low.getX(), low.getY(), low.getZ());
	}

	private static String uuid(ServerPlayer p) {
		return p.getUUID().toString();
	}

	private static String name(ServerPlayer p) {
		return p.getName().getString();
	}

	// ------------------------------------------------------------------ Öffnen

	/** Rechtsklick auf einen Wegstein. Der erste Spieler entdeckt ihn und wird Besitzer. */
	public void open(ServerPlayer player, ServerLevel level, BlockPos low) {
		String key = key(level, low);
		Info info = store.waystones.get(key);
		if (info == null) {
			info = new Info();
			info.key = key;
			info.dim = level.dimension().identifier().toString();
			info.x = low.getX();
			info.y = low.getY();
			info.z = low.getZ();
			info.finder = name(player);
			info.ownerUuid = uuid(player);
			info.ownerName = name(player);
			info.isPublic = true;
			info.biome = surfaceBiome(level, low);
			store.waystones.put(key, info);
			player.sendSystemMessage(Component.literal("§dDu hast einen neuen Wegstein entdeckt! Du bist jetzt sein Besitzer."));
			level.playSound(null, low, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		if (!info.canUse(uuid(player), name(player))) {
			player.sendSystemMessage(Component.literal("§cDieser Wegstein ist privat."));
			return;
		}
		PlayerData pd = store.player(uuid(player));
		pd.recent.remove(key);
		pd.recent.add(0, key);
		while (pd.recent.size() > 50) pd.recent.remove(pd.recent.size() - 1);
		store.save();
		sendMenu(player, info);
	}

	/** Biom an der Inseloberfläche über dem Wegstein (der Wegstein selbst steht in einer Lush-Höhle). */
	private static String surfaceBiome(ServerLevel level, BlockPos low) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, low.getX(), low.getZ());
		BlockPos at = new BlockPos(low.getX(), Math.max(top, low.getY()) , low.getZ());
		return level.getBiome(at).unwrapKey().map(k -> k.identifier().toString()).orElse("minecraft:plains");
	}

	void sendMenu(ServerPlayer player, Info current) {
		String u = uuid(player), n = name(player);
		PlayerData pd = store.player(u);
		List<Net.Entry> entries = new ArrayList<>();
		for (Info i : store.waystones.values()) {
			if (!i.canUse(u, n)) continue;
			BiomeStyle.Style st = BiomeStyle.of(i.biome);
			entries.add(new Net.Entry(i.key, i.displayName(), i.x, i.y, i.z, st.item(), st.color(), i.finder == null ? "?" : i.finder,
					i.ownerName == null ? "?" : i.ownerName, i.isPublic, pd.favorites.contains(i.key), pd.recent.indexOf(i.key)));
		}
		long now = player.level().getGameTime();
		int cd = (int) Math.max(0, (pd.lastTeleport + COOLDOWN_TICKS - now + 19) / 20);
		ServerPlayNetworking.send(player, new Net.OpenPayload(current.key, current.displayName(), current.isOwner(u), current.isPublic,
				List.copyOf(current.allowed), player.experienceLevel, cd, player.isCreative(), entries));
	}

	// ------------------------------------------------------------------ Aktionen aus dem Menü

	public void action(ServerPlayer player, Net.ActionPayload a) {
		Info cur = store.waystones.get(a.current());
		if (cur == null) return;
		String u = uuid(player), n = name(player);
		if (!cur.canUse(u, n)) return;
		// Spieler muss in der Nähe des Wegsteins sein
		if (player.position().distanceToSqr(cur.x + 0.5, cur.y + 1, cur.z + 0.5) > 12 * 12) return;
		PlayerData pd = store.player(u);
		switch (a.action()) {
			case "fav" -> {
				if (!pd.favorites.remove(a.target()) && store.waystones.containsKey(a.target())) pd.favorites.add(a.target());
			}
			case "tp" -> {
				teleport(player, cur, a.target());
				store.save();
				return;
			}
			case "rename" -> {
				if (cur.isOwner(u)) {
					String t = a.text().strip();
					cur.name = t.isEmpty() ? null : (t.length() > 32 ? t.substring(0, 32) : t);
				}
			}
			case "public" -> {
				if (cur.isOwner(u)) cur.isPublic = true;
			}
			case "private" -> {
				if (cur.isOwner(u)) cur.isPublic = false;
			}
			case "allow" -> {
				String t = a.text().strip();
				if (cur.isOwner(u) && !t.isEmpty() && cur.allowed.stream().noneMatch(s -> s.equalsIgnoreCase(t))) cur.allowed.add(t);
			}
			case "disallow" -> {
				if (cur.isOwner(u)) cur.allowed.removeIf(s -> s.equalsIgnoreCase(a.text().strip()));
			}
			case "transfer" -> {
				if (cur.isOwner(u)) {
					ServerPlayer target = server.getPlayerList().getPlayerByName(a.text().strip());
					if (target == null) {
						player.sendSystemMessage(Component.literal("§cSpieler nicht gefunden (er muss online sein)."));
					} else {
						cur.ownerUuid = uuid(target);
						cur.ownerName = name(target);
						player.sendSystemMessage(Component.literal("§aDer Wegstein gehört jetzt " + cur.ownerName + "."));
						target.sendSystemMessage(Component.literal("§a" + n + " hat dir den Wegstein \"" + cur.displayName() + "\" übergeben."));
					}
				}
			}
			default -> { }
		}
		store.save();
		sendMenu(player, cur);
	}

	private void teleport(ServerPlayer player, Info from, String targetKey) {
		Info to = store.waystones.get(targetKey);
		String u = uuid(player), n = name(player);
		if (to == null || to.key.equals(from.key) || !to.canUse(u, n)) return;
		PlayerData pd = store.player(u);
		long now = player.level().getGameTime();
		boolean creative = player.isCreative();
		if (!creative && now - pd.lastTeleport < COOLDOWN_TICKS) {
			player.sendSystemMessage(Component.literal("§cDu musst noch " + ((pd.lastTeleport + COOLDOWN_TICKS - now + 19) / 20) + " Sekunden warten."));
			return;
		}
		if (!creative && player.experienceLevel < COST_LEVELS) {
			player.sendSystemMessage(Component.literal("§cDu brauchst " + COST_LEVELS + " Level zum Teleportieren."));
			return;
		}
		ServerLevel level = levelOf(from.dim);
		if (level == null) return;
		BlockPos low = new BlockPos(from.x, from.y, from.z);
		BlockState s = level.getBlockState(low);
		if (!(s.getBlock() instanceof WaystoneBlock block)) return;
		if (!creative) player.giveExperienceLevels(-COST_LEVELS);
		pd.lastTeleport = now;
		WaystoneStore.Portal portal = new WaystoneStore.Portal();
		portal.target = to.key;
		portal.closeAt = now + OPEN_TICKS;
		store.portals.put(from.key, portal);
		block.startOpening(level, low);
		player.sendSystemMessage(Component.literal("§dDas Portal nach \"" + to.displayName() + "\" öffnet sich..."));
	}

	ServerLevel levelOf(String dim) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim)));
	}

	// ------------------------------------------------------------------ Portal

	/** Ist das Portal an diesem Wegstein offen? Abgelaufene Portale werden hier entfernt. */
	public boolean portalActive(ServerLevel level, BlockPos low) {
		String key = key(level, low);
		WaystoneStore.Portal p = store.portals.get(key);
		if (p == null) return false;
		if (p.closeAt > 0 && level.getGameTime() >= p.closeAt) {
			store.portals.remove(key);
			store.save();
			return false;
		}
		return store.waystones.containsKey(p.target);
	}

	/** Ziel beim Durchgehen: vor dem Ziel-Wegstein. */
	public TeleportTransition destination(ServerLevel level, Entity entity, BlockPos low) {
		String key = key(level, low);
		WaystoneStore.Portal p = store.portals.get(key);
		Info to = p == null ? null : store.waystones.get(p.target);
		ServerLevel targetLevel = to == null ? null : levelOf(to.dim);
		if (to == null || targetLevel == null) return null;
		BlockPos tl = new BlockPos(to.x, to.y, to.z);
		Vec3 arrive = arrival(targetLevel, tl);
		float yaw = (float) Math.toDegrees(Math.atan2(-(tl.getX() + 0.5 - arrive.x), tl.getZ() + 0.5 - arrive.z));
		return new TeleportTransition(targetLevel, arrive, Vec3.ZERO, yaw, 0.0F, e -> {
			// Nach dem ersten Durchgang schliesst das Portal nach ein paar Sekunden
			WaystoneStore.Portal pp = store.portals.get(key);
			if (pp != null && (pp.closeAt == 0 || pp.closeAt > level.getGameTime() + CLOSE_DELAY_TICKS)) {
				pp.closeAt = level.getGameTime() + CLOSE_DELAY_TICKS;
				store.save();
			}
			targetLevel.playSound(null, tl, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
		});
	}

	/** Freier Platz neben dem Wegstein (2 Blöcke Luft über festem Boden). */
	private static Vec3 arrival(ServerLevel level, BlockPos low) {
		for (Direction d : new Direction[]{Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST}) {
			for (int dist = 1; dist <= 2; dist++) {
				BlockPos p = low.relative(d, dist);
				for (int dy = 0; dy <= 1; dy++) {
					BlockPos q = p.above(dy - 0);
					if (level.getBlockState(q).getCollisionShape(level, q).isEmpty()
							&& level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()
							&& !level.getBlockState(q.below()).getCollisionShape(level, q.below()).isEmpty()) {
						return new Vec3(q.getX() + 0.5, q.getY(), q.getZ() + 0.5);
					}
				}
			}
		}
		return new Vec3(low.getX() + 0.5, low.getY() + 2, low.getZ() + 0.5);
	}

	/** Wegstein abgebaut: für alle weg. */
	public void removed(ServerLevel level, BlockPos low) {
		String key = key(level, low);
		if (store.waystones.remove(key) != null) {
			store.portals.remove(key);
			store.portals.values().removeIf(p -> key.equals(p.target));
			for (PlayerData pd : store.players.values()) {
				pd.favorites.remove(key);
				pd.recent.remove(key);
			}
			store.save();
		}
	}

	/** Darf der Spieler den Wegstein abbauen? Nur der Besitzer (oder im Kreativmodus). */
	public boolean canBreak(ServerPlayer player, ServerLevel level, BlockPos low) {
		if (player.isCreative()) return true;
		Info info = store.waystones.get(key(level, low));
		if (info != null && info.isOwner(uuid(player))) return true;
		player.sendSystemMessage(Component.literal(info == null
				? "§cDiesen Wegstein kann erst sein Besitzer abbauen. Öffne ihn zuerst."
				: "§cNur der Besitzer (" + info.ownerName + ") kann diesen Wegstein abbauen."));
		return false;
	}
}
