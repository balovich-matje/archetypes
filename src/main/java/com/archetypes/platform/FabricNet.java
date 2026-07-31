package com.archetypes.platform;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

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
import com.archetypes.state.WireId;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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

	FabricNet() {
	}

	@SuppressWarnings("unchecked")
	private static <P extends CustomPacketPayload> Wire<P> wire(final WireId id) {
		return (Wire<P>) WIRES.get(id);
	}

	private static FriendlyByteBuf buffer() {
		return new FriendlyByteBuf(Unpooled.buffer());
	}

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

	@Override
	public void registerAll() {
		for (final WireId id : WireId.values()) {
			register(id, wire(id));
		}
	}

	private static <P extends CustomPacketPayload> void register(final WireId id, final Wire<P> w) {
		if (id.direction() == WireId.Direction.CLIENTBOUND) {
			PayloadTypeRegistry.clientboundPlay().register(w.type(), w.codec());
		} else {
			PayloadTypeRegistry.serverboundPlay().register(w.type(), w.codec());
		}
	}

	@Override
	public void sendToServer(final WireId id, final Consumer<FriendlyByteBuf> writer) {
		ClientSide.send(encode(id, writer));
	}

	@Override
	public void sendToClient(final ServerPlayer to, final WireId id,
			final Consumer<FriendlyByteBuf> writer) {
		ServerPlayNetworking.send(to, encode(id, writer));
	}

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

	@Override
	public void clientReceivers(final Map<WireId, Consumer<FriendlyByteBuf>> sinks) {
		for (final Map.Entry<WireId, Consumer<FriendlyByteBuf>> e : sinks.entrySet()) {
			ClientSide.receive(e.getKey(), wire(e.getKey()), e.getValue());
		}
	}

	/**
	 * The two calls that only exist on a client.
	 *
	 * <p>Nested so that a dedicated server, which loads {@link FabricNet} for
	 * {@code registerAll} and {@code sendToClient}, never resolves
	 * {@code ClientPlayNetworking} — this class initialises only when one of its own
	 * methods is first entered, and neither is reachable from the server.
	 *
	 * <p>It compiles here at all because this project puts the fabric-api umbrella on
	 * the {@code implementation} configuration, so {@code src/main} does see the
	 * client half. Skill Proficiencies measured the opposite on its per-module layout.
	 * If the workspace's node script moves to per-module dependencies and takes
	 * {@code fabric-networking-api-v1}'s client classes off this source set, this
	 * holder is the one place that breaks, and it moves to {@code src/client} behind
	 * a hook installed the way {@link Net#clientReceivers} is.
	 */
	private static final class ClientSide {
		private ClientSide() {
		}

		static void send(final CustomPacketPayload payload) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
		}

		// Deliberately does NOT touch `context.client()`: that returns Minecraft, and
		// Minecraft is the one client type src/main genuinely cannot name here. The sink
		// schedules itself onto the client thread instead — see Net#clientReceivers.
		static <P extends CustomPacketPayload> void receive(final WireId id, final Wire<P> w,
				final Consumer<FriendlyByteBuf> sink) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
					.registerGlobalReceiver(w.type(), (payload, context) ->
							sink.accept(decode(id, payload)));
		}
	}
}
