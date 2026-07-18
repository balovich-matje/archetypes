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

	/** Cached count of completed non-recipe advancements — the XP-rate
	 * multiplier's input. Transient (recounted on join and on every real
	 * advancement) but synced, so the tree screen can show the live rate. */
	public static final AttachmentType<Integer> ADVANCEMENT_COUNT = AttachmentRegistry.create(
			Archetypes.id("advancement_count"),
			builder -> builder
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

	/** Quake: cooldown, and the charge's end tick — synced to everyone so the
	 * rising-mace pose plays for onlookers too. */
	public static final AttachmentType<Long> QUAKE_READY_AT = AttachmentRegistry.create(
			Archetypes.id("quake_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	public static final AttachmentType<Long> QUAKE_CHARGE_END = AttachmentRegistry.create(
			Archetypes.id("quake_charge_end"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** Haymaker's cooldown, same shape as the bash's. */
	public static final AttachmentType<Long> HAYMAKER_READY_AT = AttachmentRegistry.create(
			Archetypes.id("haymaker_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/** Battle Trance's last-hit mark. Pure server-side bookkeeping — the
	 * ticker reads it, nobody else. */
	public static final AttachmentType<Long> TRANCE_HIT_AT =
			AttachmentRegistry.<Long>create(Archetypes.id("trance_hit_at"));

	/** The tick of the player's last true mace smash. Stamped during damage
	 * shaping, where fall distance is still intact — the mace's own post-hit
	 * hook resets it before AFTER_DAMAGE listeners ever run. */
	public static final AttachmentType<Long> SMASH_AT =
			AttachmentRegistry.<Long>create(Archetypes.id("smash_at"));

	/** Lunge's little hop cooldown. Server-only decision, synced for symmetry. */
	public static final AttachmentType<Long> LUNGE_READY_AT = AttachmentRegistry.create(
			Archetypes.id("lunge_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/** As BASH_READY_AT, for the rush's own short anti-exploit cooldown. */
	public static final AttachmentType<Long> RUSH_READY_AT = AttachmentRegistry.create(
			Archetypes.id("rush_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/**
	 * The Seeker's mana pool. Persistent and synced to the owner so the bottle
	 * bar reads it directly; absent means full (a fresh Seeker starts topped
	 * up). Maximum and regen live in {@link Mana}, driven by the Spellcasting
	 * skill when Specialities is installed.
	 */
	public static final AttachmentType<Float> MANA = AttachmentRegistry.create(
			Archetypes.id("mana"),
			builder -> builder
					.persistent(Codec.FLOAT)
					.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly())
					.copyOnDeath());

	/** Fractional Spellcasting XP not yet big enough to award as a whole point. */
	public static final AttachmentType<Float> MANA_XP_REMAINDER =
			AttachmentRegistry.<Float>create(Archetypes.id("mana_xp_remainder"));

	/** Agility active cooldowns, same shape as the bash's. */
	public static final AttachmentType<Long> TRUE_SHOT_READY_AT = AttachmentRegistry.create(
			Archetypes.id("true_shot_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	public static final AttachmentType<Long> INVIS_READY_AT = AttachmentRegistry.create(
			Archetypes.id("invis_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	public static final AttachmentType<Long> SHADOW_STEP_READY_AT = AttachmentRegistry.create(
			Archetypes.id("shadow_step_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/** The Last Shadow capstone's own long clock, separate from the invis one. */
	public static final AttachmentType<Long> CHEAT_DEATH_READY_AT = AttachmentRegistry.create(
			Archetypes.id("cheat_death_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/** True Shot armed: the next bow shot leaving this player gets empowered. */
	public static final AttachmentType<Boolean> TRUE_SHOT_ARMED = AttachmentRegistry.create(
			Archetypes.id("true_shot_armed"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	/** Cheat-death's two-second grace: hurtServer returns false until this tick. */
	public static final AttachmentType<Long> IMMUNE_UNTIL =
			AttachmentRegistry.<Long>create(Archetypes.id("immune_until"));

	/** Deathblow: the tick of the Shadow Step strike being delivered right
	 * now, so the damage shaping can tell it from an ordinary swing. */
	public static final AttachmentType<Long> STEP_STRIKE_AT =
			AttachmentRegistry.<Long>create(Archetypes.id("step_strike_at"));

	/** Ghost Armor: armor hides with its invisible wearer. Synced to every
	 * client — it's the OTHER players' renderers that need to know. */
	public static final AttachmentType<Boolean> ARMOR_HIDDEN = AttachmentRegistry.create(
			Archetypes.id("armor_hidden"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Disengage's short anti-spam clock. Server-side only. */
	public static final AttachmentType<Long> DISENGAGE_READY_AT =
			AttachmentRegistry.<Long>create(Archetypes.id("disengage_ready_at"));

	/** Rapid Reload: a crossbow kill primes the next reload. Synced to the
	 * owner because the client predicts charge time for the draw animation. */
	public static final AttachmentType<Boolean> CROSSBOW_PRIMED = AttachmentRegistry.create(
			Archetypes.id("crossbow_primed"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	/** Magic Missile bookkeeping: the last cast tick (the 200ms breath) and
	 * the running cast count Mind Well empowers every Nth of. */
	public static final AttachmentType<Long> MISSILE_CAST_AT =
			AttachmentRegistry.<Long>create(Archetypes.id("missile_cast_at"));

	public static final AttachmentType<Integer> MISSILE_CAST_COUNT =
			AttachmentRegistry.<Integer>create(Archetypes.id("missile_cast_count"));

	/** Flamethrower channel: the last tick a channel payload arrived. */
	public static final AttachmentType<Long> FLAME_LAST_TICK =
			AttachmentRegistry.<Long>create(Archetypes.id("flame_last_tick"));

	/** On arrows: where a True Shot left the bow (it despawns 64 blocks out),
	 * and whether it steers itself. Transient — a saved arrow forgets. */
	public static final AttachmentType<net.minecraft.world.phys.Vec3> TRUE_SHOT_ORIGIN =
			AttachmentRegistry.<net.minecraft.world.phys.Vec3>create(Archetypes.id("true_shot_origin"));

	public static final AttachmentType<Boolean> TRUE_SHOT_HOMING =
			AttachmentRegistry.<Boolean>create(Archetypes.id("true_shot_homing"));

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
	 * Back to unpicked, so the picker opens again. Creative-only, for
	 * testing — banked levels SURVIVE (user call: hopping between trees in
	 * creative shouldn't need a x45 token every time). Amnesia II remains
	 * the survival path that wipes levels.
	 */
	public static void clear(final Player player) {
		forgetNodes(player);
		((AttachmentTarget) player).removeAttached(ARCHETYPE);
	}

	/** Amnesia I: every node refunded, the archetype and its levels untouched. */
	public static void forgetNodes(final Player player) {
		((AttachmentTarget) player).removeAttached(PURCHASED);
		((AttachmentTarget) player).removeAttached(SPENT_POINTS);
		// Proc bookkeeping tied to owned nodes goes too, or a respec inherits
		// it: a Mind Well counter at 7/8 would empower the first missile after
		// re-buying, and an armed True Shot fires without the node.
		((AttachmentTarget) player).removeAttached(MISSILE_CAST_COUNT);
		((AttachmentTarget) player).removeAttached(TRUE_SHOT_ARMED);
		((AttachmentTarget) player).removeAttached(CROSSBOW_PRIMED);
	}

	/**
	 * Amnesia II: the choice itself forgotten — nodes, archetype AND every
	 * banked level. A full restart, so switching class late costs what it
	 * should (user call; Amnesia I's gentler price lives in SkillPoints).
	 */
	public static void forgetArchetype(final Player player) {
		forgetNodes(player);
		((AttachmentTarget) player).removeAttached(ARCHETYPE);
		((AttachmentTarget) player).removeAttached(ARCHETYPE_XP);
	}
}
