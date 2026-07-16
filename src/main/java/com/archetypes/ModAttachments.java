package com.archetypes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

public final class ModAttachments {
	/**
	 * The player's chosen archetype, or absent if they haven't picked yet.
	 * Server-authoritative; persisted with the player and synced to the owning
	 * client only.
	 */
	public static final AttachmentType<String> ARCHETYPE = AttachmentRegistry.create(
			Archetypes.id("archetype"),
			builder -> builder
					.persistent(Codec.STRING)
					.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly())
					.copyOnDeath());

	/**
	 * Total experience the archetype has banked, in vanilla XP points. Mirrors the
	 * player's own XP gain rather than spending it — see "archetype XP is vanilla
	 * XP" in notes/design.md — so archetype progress never competes with
	 * enchanting.
	 */
	public static final AttachmentType<Integer> ARCHETYPE_XP = AttachmentRegistry.create(
			Archetypes.id("archetype_xp"),
			builder -> builder
					.persistent(Codec.INT)
					.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
					.copyOnDeath());

	/** Points already committed to nodes. Earned minus this is what's spendable. */
	public static final AttachmentType<Integer> SPENT_POINTS = AttachmentRegistry.create(
			Archetypes.id("spent_points"),
			builder -> builder
					.persistent(Codec.INT)
					.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
					.copyOnDeath());

	/**
	 * Game-time tick when the bash's ability layer comes off cooldown. Transient
	 * on purpose — a relog clearing a few seconds of cooldown is harmless — but
	 * synced, so the client can draw the countdown without a bespoke packet.
	 */
	public static final AttachmentType<Long> BASH_READY_AT = AttachmentRegistry.create(
			Archetypes.id("bash_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/**
	 * True while a Bulwark holder is actively blocking. Synced to every client —
	 * the aura is information for the mob circling behind you, not just for you.
	 * Maintained by ProtectorTicker; absent means off.
	 */
	public static final AttachmentType<Boolean> BULWARK_ACTIVE = AttachmentRegistry.create(
			Archetypes.id("bulwark_active"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Slayer active cooldowns, same shape as the bash's. */
	public static final AttachmentType<Long> DECIMATE_READY_AT = AttachmentRegistry.create(
			Archetypes.id("decimate_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	public static final AttachmentType<Long> BLADESTORM_READY_AT = AttachmentRegistry.create(
			Archetypes.id("bladestorm_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/** Game-time tick when a Decimate swing started; synced to every client so
	 * the cleave pose plays for onlookers too. */
	public static final AttachmentType<Long> DECIMATE_SWING_AT = AttachmentRegistry.create(
			Archetypes.id("decimate_swing_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));


	/** Game-time tick when the current bladestorm channel ends; synced to every
	 * client so the spinning-blade renderer works for onlookers too. */
	public static final AttachmentType<Long> BLADESTORM_END = AttachmentRegistry.create(
			Archetypes.id("bladestorm_end"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** Haymaker's cooldown, same shape as the bash's. */
	public static final AttachmentType<Long> HAYMAKER_READY_AT = AttachmentRegistry.create(
			Archetypes.id("haymaker_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/** Adrenaline's buff window and Battle Trance's last-hit mark. Pure
	 * server-side bookkeeping — the ticker reads them, nobody else. */
	public static final AttachmentType<Long> ADRENALINE_UNTIL =
			AttachmentRegistry.<Long>create(Archetypes.id("adrenaline_until"));

	public static final AttachmentType<Long> TRANCE_HIT_AT =
			AttachmentRegistry.<Long>create(Archetypes.id("trance_hit_at"));

	/** Lunge's little hop cooldown. Server-only decision, synced for symmetry. */
	public static final AttachmentType<Long> LUNGE_READY_AT = AttachmentRegistry.create(
			Archetypes.id("lunge_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/** As BASH_READY_AT, for the rush's own short anti-exploit cooldown. */
	public static final AttachmentType<Long> RUSH_READY_AT = AttachmentRegistry.create(
			Archetypes.id("rush_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/** Owned nodes, per sub-tree id, as indices into its constellation's node list. */
	public static final AttachmentType<Map<String, List<Integer>>> PURCHASED = AttachmentRegistry.create(
			Archetypes.id("purchased"),
			builder -> builder
					.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT.listOf()))
					.syncWith(
							ByteBufCodecs.<io.netty.buffer.ByteBuf, String, List<Integer>, Map<String, List<Integer>>>map(
									HashMap::new,
									ByteBufCodecs.STRING_UTF8,
									ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list())),
							AttachmentSyncPredicate.targetOnly())
					.copyOnDeath());

	private ModAttachments() {
	}

	public static void initialize() {
		// Forces static initialization at mod init time.
	}

	/** The player's archetype, or null if unpicked. */
	public static @Nullable Archetype get(final Player player) {
		String id = ((AttachmentTarget) player).getAttached(ARCHETYPE);
		return id == null ? null : Archetype.byId(id).orElse(null);
	}

	public static void set(final Player player, final Archetype archetype) {
		((AttachmentTarget) player).setAttached(ARCHETYPE, archetype.id());
	}

	/**
	 * Back to unpicked, so the picker opens again. Creative-only, for testing.
	 * Progress goes with it: keeping banked levels or owned nodes across a
	 * re-pick would make reset a respec exploit rather than a clean slate.
	 */
	public static void clear(final Player player) {
		((AttachmentTarget) player).removeAttached(ARCHETYPE);
		((AttachmentTarget) player).removeAttached(ARCHETYPE_XP);
		((AttachmentTarget) player).removeAttached(SPENT_POINTS);
		((AttachmentTarget) player).removeAttached(PURCHASED);
	}
}
