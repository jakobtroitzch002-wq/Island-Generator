package dev.jakob.waystones;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Pakete zwischen Server und Client. */
public final class Net {
	private Net() {
	}

	/** Ein Eintrag in der Liste. */
	public record Entry(String key, String name, int x, int y, int z, String icon, int color, String finder, String owner,
			boolean isPublic, boolean favorite, int recentIndex, int cost) {
		void write(RegistryFriendlyByteBuf b) {
			b.writeUtf(key);
			b.writeUtf(name);
			b.writeInt(x);
			b.writeInt(y);
			b.writeInt(z);
			b.writeUtf(icon);
			b.writeInt(color);
			b.writeUtf(finder);
			b.writeUtf(owner);
			b.writeBoolean(isPublic);
			b.writeBoolean(favorite);
			b.writeInt(recentIndex);
			b.writeVarInt(cost);
		}

		static Entry read(RegistryFriendlyByteBuf b) {
			return new Entry(b.readUtf(), b.readUtf(), b.readInt(), b.readInt(), b.readInt(), b.readUtf(), b.readInt(),
					b.readUtf(), b.readUtf(), b.readBoolean(), b.readBoolean(), b.readInt(), b.readVarInt());
		}
	}

	/** Server → Client: Menü öffnen bzw. aktualisieren. */
	public record OpenPayload(String current, String currentName, boolean owner, boolean currentPublic, List<String> allowed,
			int level, int cooldownSeconds, boolean creative, List<Entry> entries) implements CustomPacketPayload {
		public static final Type<OpenPayload> TYPE = new Type<>(Waystones.id("open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC = StreamCodec.of(OpenPayload::write, OpenPayload::read);

		private static void write(RegistryFriendlyByteBuf b, OpenPayload p) {
			b.writeUtf(p.current);
			b.writeUtf(p.currentName);
			b.writeBoolean(p.owner);
			b.writeBoolean(p.currentPublic);
			b.writeVarInt(p.allowed.size());
			for (String s : p.allowed) b.writeUtf(s);
			b.writeVarInt(p.level);
			b.writeVarInt(p.cooldownSeconds);
			b.writeBoolean(p.creative);
			b.writeVarInt(p.entries.size());
			for (Entry e : p.entries) e.write(b);
		}

		private static OpenPayload read(RegistryFriendlyByteBuf b) {
			String current = b.readUtf(), name = b.readUtf();
			boolean owner = b.readBoolean(), pub = b.readBoolean();
			int n = b.readVarInt();
			List<String> allowed = new ArrayList<>();
			for (int i = 0; i < n; i++) allowed.add(b.readUtf());
			int level = b.readVarInt(), cd = b.readVarInt();
			boolean creative = b.readBoolean();
			int m = b.readVarInt();
			List<Entry> entries = new ArrayList<>();
			for (int i = 0; i < m; i++) entries.add(Entry.read(b));
			return new OpenPayload(current, name, owner, pub, allowed, level, cd, creative, entries);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client → Server: eine Aktion im Menü. */
	public record ActionPayload(String action, String current, String target, String text) implements CustomPacketPayload {
		public static final Type<ActionPayload> TYPE = new Type<>(Waystones.id("action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = StreamCodec.of(ActionPayload::write, ActionPayload::read);

		private static void write(RegistryFriendlyByteBuf b, ActionPayload p) {
			b.writeUtf(p.action);
			b.writeUtf(p.current);
			b.writeUtf(p.target);
			b.writeUtf(p.text);
		}

		private static ActionPayload read(RegistryFriendlyByteBuf b) {
			return new ActionPayload(b.readUtf(), b.readUtf(), b.readUtf(), b.readUtf(64));
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
