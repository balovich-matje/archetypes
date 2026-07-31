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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@link Net} on NeoForge: the eleven {@link WireId}s become the same eleven payload
 * records the Fabric implementation uses, registered through one
 * {@code RegisterPayloadHandlersEvent}.
 *
 * <p>This is the ONLY file on this node that names {@code PayloadRegistrar},
 * {@code PacketDistributor} or any of the eleven payload records — the seam-hygiene rule
 * (Skill Proficiencies' conventions §5g), and a grep is the review gate for it.
 *
 * <p><b>The eleven records and their codecs are shared with the four modern Fabric nodes,
 * unchanged.</b> That is worth stating because it is what keeps the wire contract in
 * {@code WireId} true across the loader axis: a client and a server built from different
 * nodes of the same Minecraft version send the same bytes down the same channel names,
 * because they run the same {@code CODEC} objects. Nothing in this file re-describes a
 * payload.
 *
 * <p><b>Both directions are registered from ONE common-init call</b>, which is the whole
 * reason {@link Net#clientReceivers} exists in the shape it does:
 * {@code PayloadRegistrar.playToClient(TYPE, CODEC, handler)} takes the handler as an
 * argument to the registration call, and a dedicated server has to register the two
 * clientbound types too. So the client hands its sinks down and this class holds them in
 * two static fields until the registrar asks. The same trick, and the same reason, as Skill
 * Proficiencies' {@code NeoForgeNet}.
 *
 * <p>The buffer round-trip on each hop is the Fabric implementation's, verbatim and for the
 * same reason: the seam speaks {@code FriendlyByteBuf} because that is the one thing every
 * node has, and the records speak fields.
 *
 * <p>No {@code //?} anywhere: the file is excluded from every non-NeoForge node's source set
 * by anchored glob, so it only ever compiles at 1.21.1 — see {@link NeoForgePlatform}'s
 * javadoc for the same note at length.
 */
final class NeoForgeNet implements Net {
	/**
	 * The network protocol version this mod's channels advertise.
	 *
	 * <p>Deliberately NOT derived from {@code mod.version}: {@link WireId} freezes the wire
	 * contract, so the number moves only when a channel's bytes do, and pinning it to the
	 * release number would make every release refuse to connect to the last one for nothing.
	 * The registrar is left {@code optional()}-free, i.e. required on both sides, which
	 * matches the Fabric nodes — there a missing channel is a missing mod, not a soft
	 * degrade.
	 */
	private static final String PROTOCOL = "1";

	/** Registration, encode and decode for one channel, in one place — FabricNet's shape. */
	private record Wire<P extends CustomPacketPayload>(
			CustomPacketPayload.Type<P> type,
			StreamCodec<? super RegistryFriendlyByteBuf, P> codec,
			Function<FriendlyByteBuf, P> fromBuf,
			BiConsumer<P, FriendlyByteBuf> toBuf) {
	}

	private static final Map<WireId, Wire<?>> WIRES = new EnumMap<>(WireId.class);

	/** Server-side handlers, installed by {@link #onServerbound} from common init. */
	private static final Map<WireId, BiConsumer<ServerPlayer, FriendlyByteBuf>> HANDLERS =
			new EnumMap<>(WireId.class);

	/** Client-side sinks, handed down from client init through {@link #clientReceivers}. */
	private static final Map<WireId, Consumer<FriendlyByteBuf>> SINKS = new EnumMap<>(WireId.class);

	private static <P extends CustomPacketPayload> void wire(final WireId id,
			final CustomPacketPayload.Type<P> type,
			final StreamCodec<? super RegistryFriendlyByteBuf, P> codec,
			final Function<FriendlyByteBuf, P> fromBuf,
			final BiConsumer<P, FriendlyByteBuf> toBuf) {
		WIRES.put(id, new Wire<>(type, codec, fromBuf, toBuf));
	}

	static {
		// Field order here IS the frozen wire contract (see WireId). Kept character for
		// character in step with FabricNet's table — the two are one contract written twice
		// because neither file may name the other's API, and a divergence in this block is a
		// cross-loader desync, not a compile error.
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

	NeoForgeNet() {
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

	/**
	 * Arms the one registration event. Called from common init, on both sides.
	 *
	 * <p><b>It only ADDS A LISTENER here; nothing is registered yet</b>, and that is what
	 * makes the ordering safe on this node.
	 * {@code RegisterPayloadHandlersEvent} is posted from {@code NetworkRegistry.setup},
	 * which {@code CommonModLoader.finish} runs as its very last init task — long after the
	 * {@code RegisterEvent} window {@link ArchetypesNeoForge} runs common init inside. So the
	 * handler map and the sink map are both fully populated by the time the registrar asks
	 * for them, whichever order common and client init ran in.
	 */
	@Override
	public void registerAll() {
		ArchetypesNeoForge.modEventBus().addListener(RegisterPayloadHandlersEvent.class,
				NeoForgeNet::onRegisterPayloads);
	}

	private static void onRegisterPayloads(final RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar(PROTOCOL);

		for (final WireId id : WireId.values()) {
			register(registrar, id, wire(id));
		}
	}

	private static <P extends CustomPacketPayload> void register(final PayloadRegistrar registrar,
			final WireId id, final Wire<P> w) {
		if (id.direction() == WireId.Direction.CLIENTBOUND) {
			// The sink is looked up per message rather than captured, because client init may
			// legitimately run after this listener was installed. `context.enqueueWork` is
			// deliberately NOT used: the sinks hand themselves to Minecraft#execute, exactly
			// as the Fabric arm's do, which keeps the thread hop one implementation.
			registrar.playToClient(w.type(), w.codec(), (payload, context) -> {
				Consumer<FriendlyByteBuf> sink = SINKS.get(id);

				if (sink != null) {
					sink.accept(decode(id, payload));
				}
			});
		} else {
			registrar.playToServer(w.type(), w.codec(), (payload, context) -> {
				BiConsumer<ServerPlayer, FriendlyByteBuf> handler = HANDLERS.get(id);

				if (handler != null && context.player() instanceof ServerPlayer player) {
					// Same guarantee the Fabric arm gets from `context.server().execute`: the
					// handler runs on the server thread. NeoForge's context does not hand out
					// the server, and `enqueueWork` is the documented way to get there.
					FriendlyByteBuf decoded = decode(id, payload);
					context.enqueueWork(() -> handler.accept(player, decoded));
				}
			});
		}
	}

	@Override
	public void sendToServer(final WireId id, final Consumer<FriendlyByteBuf> writer) {
		// Common API on this loader, unlike Fabric's — see Net#sendToServer's javadoc, which
		// says so and names PacketDistributor as the reason the method is on the seam at all.
		PacketDistributor.sendToServer(encode(id, writer));
	}

	@Override
	public void sendToClient(final ServerPlayer to, final WireId id,
			final Consumer<FriendlyByteBuf> writer) {
		PacketDistributor.sendToPlayer(to, encode(id, writer));
	}

	@Override
	public void onServerbound(final WireId id,
			final BiConsumer<ServerPlayer, FriendlyByteBuf> handler) {
		HANDLERS.put(id, handler);
	}

	@Override
	public void clientReceivers(final Map<WireId, Consumer<FriendlyByteBuf>> sinks) {
		SINKS.putAll(sinks);
	}
}
