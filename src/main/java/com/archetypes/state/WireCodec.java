package com.archetypes.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.network.FriendlyByteBuf;

/**
 * How one synced {@link StateKey}'s value goes on the wire — the portable half
 * of what {@code ByteBufCodecs} + {@code StreamCodec} do on a modern node.
 *
 * <p>Why this exists rather than a {@code StreamCodec} field on {@link StateKey}:
 * {@code StreamCodec}, {@code ByteBufCodecs} and {@code RegistryFriendlyByteBuf}
 * are all part of the payload stack that arrives at 1.20.5 (Skill Proficiencies'
 * frozen predicate {@code >=1.20.5}), and {@link StateKey} is shared code that has
 * to compile on every node. {@code FriendlyByteBuf} and its read/write primitives
 * are present on all of them, so the key table describes its wire format in terms
 * of those and each platform store adapts.
 *
 * <p><b>The encodings below are byte-identical to the {@code ByteBufCodecs}
 * constants they replace</b>, and they have to stay that way: the same values
 * cross the wire between a client and a server built from different nodes of the
 * same Minecraft version.
 *
 * <ul>
 * <li>{@link #STRING} = {@code ByteBufCodecs.STRING_UTF8} (var-int length, UTF-8)
 * <li>{@link #VAR_INT} = {@code ByteBufCodecs.VAR_INT}
 * <li>{@link #VAR_LONG} = {@code ByteBufCodecs.VAR_LONG}
 * <li>{@link #BOOL} = {@code ByteBufCodecs.BOOL} (one byte)
 * <li>{@link #FLOAT} = {@code ByteBufCodecs.FLOAT} (four bytes, big-endian)
 * <li>{@link #INT_LIST} = {@code ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list())}
 * <li>{@link #PURCHASE_MAP} = {@code ByteBufCodecs.map(HashMap::new, STRING_UTF8,
 *     VAR_INT.apply(list()))}
 * </ul>
 */
public interface WireCodec<T> {
	void write(FriendlyByteBuf buf, T value);

	T read(FriendlyByteBuf buf);

	WireCodec<String> STRING = new WireCodec<>() {
		@Override
		public void write(final FriendlyByteBuf buf, final String value) {
			buf.writeUtf(value);
		}

		@Override
		public String read(final FriendlyByteBuf buf) {
			return buf.readUtf();
		}
	};

	WireCodec<Integer> VAR_INT = new WireCodec<>() {
		@Override
		public void write(final FriendlyByteBuf buf, final Integer value) {
			buf.writeVarInt(value);
		}

		@Override
		public Integer read(final FriendlyByteBuf buf) {
			return buf.readVarInt();
		}
	};

	WireCodec<Long> VAR_LONG = new WireCodec<>() {
		@Override
		public void write(final FriendlyByteBuf buf, final Long value) {
			buf.writeVarLong(value);
		}

		@Override
		public Long read(final FriendlyByteBuf buf) {
			return buf.readVarLong();
		}
	};

	WireCodec<Boolean> BOOL = new WireCodec<>() {
		@Override
		public void write(final FriendlyByteBuf buf, final Boolean value) {
			buf.writeBoolean(value);
		}

		@Override
		public Boolean read(final FriendlyByteBuf buf) {
			return buf.readBoolean();
		}
	};

	WireCodec<Float> FLOAT = new WireCodec<>() {
		@Override
		public void write(final FriendlyByteBuf buf, final Float value) {
			buf.writeFloat(value);
		}

		@Override
		public Float read(final FriendlyByteBuf buf) {
			return buf.readFloat();
		}
	};

	WireCodec<List<Integer>> INT_LIST = new WireCodec<>() {
		@Override
		public void write(final FriendlyByteBuf buf, final List<Integer> value) {
			buf.writeVarInt(value.size());

			for (final Integer i : value) {
				buf.writeVarInt(i);
			}
		}

		@Override
		public List<Integer> read(final FriendlyByteBuf buf) {
			int size = buf.readVarInt();
			List<Integer> out = new ArrayList<>(size);

			for (int i = 0; i < size; i++) {
				out.add(buf.readVarInt());
			}

			return out;
		}
	};

	WireCodec<Map<String, List<Integer>>> PURCHASE_MAP = new WireCodec<>() {
		@Override
		public void write(final FriendlyByteBuf buf, final Map<String, List<Integer>> value) {
			buf.writeVarInt(value.size());

			for (final Map.Entry<String, List<Integer>> e : value.entrySet()) {
				buf.writeUtf(e.getKey());
				INT_LIST.write(buf, e.getValue());
			}
		}

		@Override
		public Map<String, List<Integer>> read(final FriendlyByteBuf buf) {
			int size = buf.readVarInt();
			Map<String, List<Integer>> out = new HashMap<>(size);

			for (int i = 0; i < size; i++) {
				String k = buf.readUtf();
				out.put(k, INT_LIST.read(buf));
			}

			return out;
		}
	};
}
