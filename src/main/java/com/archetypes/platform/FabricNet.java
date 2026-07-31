package com.archetypes.platform;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

//? if >=1.20.5 {
import com.archetypes.ActiveAbilityPayload;
import com.archetypes.BuyNodePayload;
import com.archetypes.DisengagePayload;
import com.archetypes.MeleeSwingPayload;
import com.archetypes.NightDashPayload;
import com.archetypes.ParrySwingPayload;
import com.archetypes.PassiveProcPayload;
import com.archetypes.PickArchetypePayload;
import com.archetypes.ResetArchetypePayload;
import com.archetypes.RushPayload;
import com.archetypes.SpellChannelPayload;
//?}
import com.archetypes.state.WireId;

import io.netty.buffer.Unpooled;
//? if >=1.20.5 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//?}
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.server.level.ServerPlayer;

/**
 * {@link Net} on Fabric: the eleven {@link WireId}s become the eleven payload
 * records and fabric-api's networking calls.
 *
 * <p>This is the ONLY file in {@code src/main} that names {@code PayloadTypeRegistry},
 * {@code ServerPlayNetworking}, {@code ClientPlayNetworking} or any of the eleven
 * payload records — the seam-hygiene rule (Skill Proficiencies' conventions §5g),
 * and a grep is the review gate for it.
 *
 * <p><b>The buffer round-trip on each hop is deliberate.</b> The seam speaks
 * {@code FriendlyByteBuf} because that is the one thing every node has; the records
 * speak fields. One heap buffer per message reconciles the two, and it costs nothing
 * that matters: the busiest of the eleven fires on a key edge. Keeping the records
 * (rather than collapsing them into one raw-bytes payload) is what makes the
 * below-1.20.5 fork a swap of THIS file's internals for {@code FabricPacket} +
 * {@code PacketType.create}, with the wire bytes untouched.
 */
final class FabricNet implements Net {
	// STAGE 5 — THE PAYLOAD TABLE IS `>=1.20.5` AND SO IS EVERY TYPE IN IT.
	// `CustomPacketPayload`, `StreamCodec`, `ByteBufCodecs`, `RegistryFriendlyByteBuf` and
	// `PayloadTypeRegistry` all arrived together, and below them fabric-api 0.92.11 speaks the
	// RAW channel API: an id and a `FriendlyByteBuf`, in both directions. That is exactly what
	// this seam already speaks (design §3.2 — every one of the eleven carries only ints and
	// strings, so nothing at the boundary ever needed a codec), so the legacy arm needs no
	// table, no records and no registration: `Archetypes.id(id.path())` IS the channel.
	//
	// The design sketched `FabricPacket` + `PacketType.create` here instead. Raw channels are
	// taken over it deliberately: `FabricPacket` would need the eleven records to implement a
	// SECOND interface (a fork in eleven files rather than none), buys nothing this seam uses,
	// and produces the same bytes on the wire — the channel id and the writer's own output,
	// which is what the frozen contract in `WireId` is about.
	//? if >=1.20.5 {
	/** Registration, encode and decode for one channel, in one place. */
	private record Wire<P extends CustomPacketPayload>(
			CustomPacketPayload.Type<P> type,
			StreamCodec<? super RegistryFriendlyByteBuf, P> codec,
			Function<FriendlyByteBuf, P> fromBuf,
			BiConsumer<P, FriendlyByteBuf> toBuf) {
	}

	private static final Map<WireId, Wire<?>> WIRES = new EnumMap<>(WireId.class);

	private static <P extends CustomPacketPayload> void wire(final WireId id,
			final CustomPacketPayload.Type<P> type,
			final StreamCodec<? super RegistryFriendlyByteBuf, P> codec,
			final Function<FriendlyByteBuf, P> fromBuf,
			final BiConsumer<P, FriendlyByteBuf> toBuf) {
		WIRES.put(id, new Wire<>(type, codec, fromBuf, toBuf));
	}

	static {
		// Field order here IS the frozen wire contract (see WireId).
		wire(WireId.PASSIVE_PROC, PassiveProcPayload.TYPE, PassiveProcPayload.CODEC,
				buf -> new PassiveProcPayload(buf.readUtf(), buf.readUtf()),
				(p, buf) -> {
					buf.writeUtf(p.subTreeId());
					buf.writeUtf(p.family());
				});
		wire(WireId.PARRY_SWING, ParrySwingPayload.TYPE, ParrySwingPayload.CODEC,
				buf -> new ParrySwingPayload(buf.readVarInt()),
				(p, buf) -> buf.writeVarInt(p.ticker()));
		wire(WireId.PICK_ARCHETYPE, PickArchetypePayload.TYPE, PickArchetypePayload.CODEC,
				buf -> new PickArchetypePayload(buf.readUtf()),
				(p, buf) -> buf.writeUtf(p.archetypeId()));
		wire(WireId.RESET_ARCHETYPE, ResetArchetypePayload.TYPE, ResetArchetypePayload.CODEC,
				buf -> new ResetArchetypePayload(), (p, buf) -> { });
		wire(WireId.BUY_NODE, BuyNodePayload.TYPE, BuyNodePayload.CODEC,
				buf -> new BuyNodePayload(buf.readUtf(), buf.readVarInt()),
				(p, buf) -> {
					buf.writeUtf(p.subTreeId());
					buf.writeVarInt(p.node());
				});
		wire(WireId.ACTIVE_ABILITY, ActiveAbilityPayload.TYPE, ActiveAbilityPayload.CODEC,
				buf -> new ActiveAbilityPayload(buf.readVarInt()),
				(p, buf) -> buf.writeVarInt(p.slot()));
		wire(WireId.SPELL_CHANNEL, SpellChannelPayload.TYPE, SpellChannelPayload.CODEC,
				buf -> new SpellChannelPayload(), (p, buf) -> { });
		wire(WireId.MELEE_SWING, MeleeSwingPayload.TYPE, MeleeSwingPayload.CODEC,
				buf -> new MeleeSwingPayload(), (p, buf) -> { });
		wire(WireId.RUSH, RushPayload.TYPE, RushPayload.CODEC,
				buf -> new RushPayload(), (p, buf) -> { });
		wire(WireId.DISENGAGE, DisengagePayload.TYPE, DisengagePayload.CODEC,
				buf -> new DisengagePayload(), (p, buf) -> { });
		wire(WireId.NIGHT_DASH, NightDashPayload.TYPE, NightDashPayload.CODEC,
				buf -> new NightDashPayload(), (p, buf) -> { });
	}

	//?}

	FabricNet() {
	}

	//? if >=1.20.5 {
	@SuppressWarnings("unchecked")
	private static <P extends CustomPacketPayload> Wire<P> wire(final WireId id) {
		return (Wire<P>) WIRES.get(id);
	}

	//?} else {
	/*private static net.minecraft.resources.Identifier channel(final WireId id) {
		return com.archetypes.Archetypes.id(id.path());
	}

	*///?}
	private static FriendlyByteBuf buffer() {
		return new FriendlyByteBuf(Unpooled.buffer());
	}

	//? if >=1.20.5 {
	private static <P extends CustomPacketPayload> P encode(final WireId id,
			final Consumer<FriendlyByteBuf> writer) {
		Wire<P> w = wire(id);
		FriendlyByteBuf buf = buffer();
		writer.accept(buf);
		return w.fromBuf().apply(buf);
	}

	private static <P extends CustomPacketPayload> FriendlyByteBuf decode(final WireId id,
			final P payload) {
		Wire<P> w = wire(id);
		FriendlyByteBuf buf = buffer();
		w.toBuf().accept(payload, buf);
		return buf;
	}

	//?} else {
	/*private static FriendlyByteBuf encode(final Consumer<FriendlyByteBuf> writer) {
		FriendlyByteBuf buf = buffer();
		writer.accept(buf);
		return buf;
	}

	// The buffer a raw receiver is handed belongs to netty and is released the moment the
	// handler returns, while both directions defer the read onto the game thread. So the
	// readable bytes are copied into a heap buffer of our own first — the same guarantee
	// the payload records give above by being decoded before the hop.
	private static FriendlyByteBuf detach(final FriendlyByteBuf buf) {
		return new FriendlyByteBuf(Unpooled.copiedBuffer(buf));
	}

	*///?}
	//? if >=1.20.5 {
	@Override
	public void registerAll() {
		for (final WireId id : WireId.values()) {
			register(id, wire(id));
		}
	}

	//?} else {
	/*@Override
	public void registerAll() {
		// Nothing to register: a raw channel exists as soon as something listens on it,
		// and the listeners are installed by onServerbound/clientReceivers. Keeping the
		// method (rather than moving the call site) is what keeps common init shared.
	}

	*///?}
	//? if >=1.20.5 {
	private static <P extends CustomPacketPayload> void register(final WireId id, final Wire<P> w) {
		// 26.1 renamed both accessors: `playS2C()`/`playC2S()` -> `clientboundPlay()`/
		// `serverboundPlay()`. Same registry, same `register(type, codec)` (measured on
		// fabric-networking-api-v1 6.3.x and 5.1.6) — only the getter's name moves, so the
		// direction branch itself is shared.
		if (id.direction() == WireId.Direction.CLIENTBOUND) {
			//? if >=26.1 {
			PayloadTypeRegistry.clientboundPlay().register(w.type(), w.codec());
			//?} else {
			/*PayloadTypeRegistry.playS2C().register(w.type(), w.codec());
			*///?}
		} else {
			//? if >=26.1 {
			PayloadTypeRegistry.serverboundPlay().register(w.type(), w.codec());
			//?} else {
			/*PayloadTypeRegistry.playC2S().register(w.type(), w.codec());
			*///?}
		}
	}

	//?}

	//? if >=1.20.5 {
	@Override
	public void sendToServer(final WireId id, final Consumer<FriendlyByteBuf> writer) {
		ClientSide.send(encode(id, writer));
	}

	//?} else {
	/*@Override
	public void sendToServer(final WireId id, final Consumer<FriendlyByteBuf> writer) {
		ClientSide.send(channel(id), encode(writer));
	}

	*///?}
	//? if >=1.20.5 {
	@Override
	public void sendToClient(final ServerPlayer to, final WireId id,
			final Consumer<FriendlyByteBuf> writer) {
		ServerPlayNetworking.send(to, encode(id, writer));
	}

	//?} else {
	/*@Override
	public void sendToClient(final ServerPlayer to, final WireId id,
			final Consumer<FriendlyByteBuf> writer) {
		ServerPlayNetworking.send(to, channel(id), encode(writer));
	}

	*///?}
	//? if >=1.20.5 {
	@Override
	public void onServerbound(final WireId id,
			final BiConsumer<ServerPlayer, FriendlyByteBuf> handler) {
		listen(id, wire(id), handler);
	}

	private static <P extends CustomPacketPayload> void listen(final WireId id, final Wire<P> w,
			final BiConsumer<ServerPlayer, FriendlyByteBuf> handler) {
		ServerPlayNetworking.registerGlobalReceiver(w.type(), (payload, context) ->
				context.server().execute(() ->
						handler.accept(context.player(), decode(id, payload))));
	}

	//?} else {
	/*@Override
	public void onServerbound(final WireId id,
			final BiConsumer<ServerPlayer, FriendlyByteBuf> handler) {
		ServerPlayNetworking.registerGlobalReceiver(channel(id),
				(server, player, listener, buf, responseSender) -> {
					FriendlyByteBuf detached = detach(buf);
					server.execute(() -> handler.accept(player, detached));
				});
	}

	*///?}
	//? if >=1.20.5 {
	@Override
	public void clientReceivers(final Map<WireId, Consumer<FriendlyByteBuf>> sinks) {
		for (final Map.Entry<WireId, Consumer<FriendlyByteBuf>> e : sinks.entrySet()) {
			ClientSide.receive(e.getKey(), wire(e.getKey()), e.getValue());
		}
	}

	//?} else {
	/*@Override
	public void clientReceivers(final Map<WireId, Consumer<FriendlyByteBuf>> sinks) {
		for (final Map.Entry<WireId, Consumer<FriendlyByteBuf>> e : sinks.entrySet()) {
			ClientSide.receive(channel(e.getKey()), e.getValue());
		}
	}

	*///?}
	/**
	 * The two calls that only exist on a client.
	 *
	 * <p>Nested so that a dedicated server, which loads {@link FabricNet} for
	 * {@code registerAll} and {@code sendToClient}, never resolves
	 * {@code ClientPlayNetworking} — this class initialises only when one of its own
	 * methods is first entered, and neither is reachable from the server.
	 *
	 * <p><b>It compiles here at all on 26.x only</b>, and Stage 3 measured why: the two
	 * 26.x nodes run plain fabric-loom, which puts the WHOLE fabric-api module jar on
	 * {@code src/main}'s compile classpath. From 1.21.11 down the node runs
	 * fabric-loom-remap, which honours the module's {@code Fabric-Loom-Split-Environment}
	 * header and hands this source set the {@code -common} half only. So the prediction
	 * this javadoc used to carry has come true, on the boundary it was always going to:
	 * below 26.1 the two calls move to {@code src/client} behind a hook installed the way
	 * {@link Net#clientReceivers} is, and {@link ClientNetHooks} is that hook.
	 */
	private static final class ClientSide {
		private ClientSide() {
		}

		// STAGE 5: below 1.20.5 the hook speaks the raw channel pair — an id and a buffer —
		// which is the same widening the rest of this file takes. Both arms still hand the
		// client only types `src/main` can name, and both still leave the thread hop to
		// the sink.
		//? if >=1.20.5 {
		static void send(final CustomPacketPayload payload) {
			//? if >=26.1 {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
			//?} else {
			/*ClientNetHooks.calls().send(payload);
			*///?}
		}

		// Deliberately does NOT touch `context.client()`: that returns Minecraft, and
		// Minecraft is the one client type src/main genuinely cannot name here. The sink
		// schedules itself onto the client thread instead — see Net#clientReceivers. That
		// is also what keeps the below-26.1 hook's signature free of any client type: it
		// hands back the payload and nothing else.
		static <P extends CustomPacketPayload> void receive(final WireId id, final Wire<P> w,
				final Consumer<FriendlyByteBuf> sink) {
			//? if >=26.1 {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
					.registerGlobalReceiver(w.type(), (payload, context) ->
							sink.accept(decode(id, payload)));
			//?} else {
			/*ClientNetHooks.calls().receive(w.type(), payload -> sink.accept(decode(id, payload)));
			*///?}
		}
		//?} else {
		/*static void send(final net.minecraft.resources.Identifier channel, final FriendlyByteBuf buf) {
			ClientNetHooks.calls().send(channel, buf);
		}

		static void receive(final net.minecraft.resources.Identifier channel,
				final Consumer<FriendlyByteBuf> sink) {
			ClientNetHooks.calls().receive(channel, sink);
		}
		*///?}
	}
}
