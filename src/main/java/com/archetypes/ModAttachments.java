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

	/** Normal points committed to base sub-trees. Earned minus this is what's
	 * spendable there. */
	public static final AttachmentType<Integer> SPENT_POINTS = AttachmentRegistry.create(
			Archetypes.id("spent_points"),
			builder -> builder
					.persistent(Codec.INT)
					.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
					.copyOnDeath());

	/** Epic points committed to epic sub-trees. Kept apart from the normal
	 * pool so levels 46-60 feed only the epic trees. */
	public static final AttachmentType<Integer> EPIC_SPENT_POINTS = AttachmentRegistry.create(
			Archetypes.id("epic_spent_points"),
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

	/**
	 * Magic Armaments channel: the real wand pulled out of the hand while the
	 * conjured weapon stands in for it. Presence is the channel's on-flag.
	 * Persistent and copyOnDeath so a relog, crash or death never eats the
	 * player's wand — {@link MagicArmaments#restoreDirty} puts it back on JOIN if
	 * a channel died mid-flight, and the death hook restores it before drops.
	 * Server-only; no client mirrors it.
	 */
	public static final AttachmentType<net.minecraft.world.item.ItemStack> ARMAMENTS_WAND =
			AttachmentRegistry.create(Archetypes.id("armaments_wand"),
					builder -> builder
							.persistent(net.minecraft.world.item.ItemStack.CODEC)
							.copyOnDeath());

	/** The hotbar slot the wand was pulled from, so it goes back exactly where
	 * it was. Persistent/copyOnDeath alongside the wand it pairs with. */
	public static final AttachmentType<Integer> ARMAMENTS_SLOT = AttachmentRegistry.create(
			Archetypes.id("armaments_slot"),
			builder -> builder.persistent(Codec.INT).copyOnDeath());

	// No upkeep-beat or flight-grant attachment: upkeep is charged every tick
	// (nothing to remember between charges) and Levitation glides through
	// vanilla's canGlide rather than borrowing a mayfly that must be given back.

	/** On arrows: where a True Shot left the bow (it despawns 64 blocks out),
	 * and whether it steers itself. Transient — a saved arrow forgets. */
	public static final AttachmentType<net.minecraft.world.phys.Vec3> TRUE_SHOT_ORIGIN =
			AttachmentRegistry.<net.minecraft.world.phys.Vec3>create(Archetypes.id("true_shot_origin"));

	public static final AttachmentType<Boolean> TRUE_SHOT_HOMING =
			AttachmentRegistry.<Boolean>create(Archetypes.id("true_shot_homing"));

	/**
	 * On arrows: conjured by the Spellbow, so it falls at reduced gravity and
	 * wears the Magic Missile's trail. Transient — a saved arrow forgets and
	 * reverts to an ordinary one.
	 *
	 * <p>Synced to everyone, unlike the True Shot marks above, because gravity
	 * is the one arrow property the CLIENT also integrates: arrows tick their
	 * own physics between the server's position updates, so an unsynced flag
	 * would have the client drop the arrow and snap it back every update.
	 */
	public static final AttachmentType<Boolean> SPELLBOW_ARROW = AttachmentRegistry.create(
			Archetypes.id("spellbow_arrow"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** On arrows, for one hit-handler call: the return-to-sender velocity a
	 * Reflection block computed. Applied and cleared at the end of the hit —
	 * vanilla's post-deflect drop would stomp it if set any earlier. */
	public static final AttachmentType<net.minecraft.world.phys.Vec3> REFLECT_AIM =
			AttachmentRegistry.<net.minecraft.world.phys.Vec3>create(Archetypes.id("reflect_aim"));

	/**
	 * Game tick when the running Aura of Radiance ends; absent means no aura.
	 * Deliberately transient and NOT copyOnDeath: the aura is a ten-second
	 * consequence of a cast, so a relog or a death simply ends it, and
	 * {@link RadianceAura}'s ticker never has to reconcile an aura it did not
	 * start. Synced to EVERY client, not just the owner: the aura's light is
	 * drawn client-side around whoever is glowing, so an onlooker's client has
	 * to know the aura is up. Read it through {@link RadianceAura#isActive}.
	 */
	public static final AttachmentType<Long> RADIANCE_END = AttachmentRegistry.create(
			Archetypes.id("radiance_end"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	// --- Nemesis Shadow (epic): the Dark Ritual and the night form ---
	// The three attachments below are the FX agent's whole contract with this
	// tree; read them through {@link NightForm}, never directly.

	/**
	 * Game tick the night form was ENTERED; absent means not transformed. The
	 * form itself has no end — it is a toggle (author's spec) — so presence is
	 * the "you are a vampire" boolean and the value is only ever read to answer
	 * "has the lockout passed yet", i.e. may this player turn back.
	 *
	 * <p>Persistent and copyOnDeath: the form is now permanent state, so a
	 * relog, a crash or a death that cured vampirism would erase a commitment
	 * the player cannot otherwise undo for an hour. Synced to EVERY client,
	 * because it is other players' renderers that need to know what just walked
	 * into the room.
	 *
	 * <p>The id deliberately differs from the retired {@code night_form_end}:
	 * that stamp meant an expiry, and reading an old save's expiry as an entry
	 * time would hand a lapsed vampire a fresh, silent hour. Saves written
	 * before the toggle simply wake up mortal.
	 */
	public static final AttachmentType<Long> NIGHT_FORM_SINCE = AttachmentRegistry.create(
			Archetypes.id("night_form_since"),
			builder -> builder
					.persistent(Codec.LONG)
					.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
					.copyOnDeath());

	/**
	 * Game tick the running Dark Ritual channel completes; absent means no
	 * channel. Transient — a channel cannot survive a relog, and it costs
	 * nothing to lose one. Synced to everyone: the channel has a first- AND
	 * third-person animation, so onlookers' renderers read this too.
	 */
	public static final AttachmentType<Long> NIGHT_CHANNEL_END = AttachmentRegistry.create(
			Archetypes.id("night_channel_end"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/**
	 * True while the transformed player stands in sunlight strong enough to
	 * burn them. Synced to everyone (it drives an on-screen effect for the
	 * owner and could dress the avatar for onlookers); set and cleared by
	 * {@link NightFormTicker}, never by the client.
	 */
	public static final AttachmentType<Boolean> NIGHT_SUNLIT = AttachmentRegistry.create(
			Archetypes.id("night_sunlit"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * Extra Sensory Perception's two rosters of entity ids, refreshed every
	 * {@link Tuning#ESP_REFRESH_TICKS}: everything living in range, and the
	 * players among them kept apart so the renderer can mark them out
	 * distinctly (author's spec). Target-only — nobody else's client has any
	 * use for what this player can sense.
	 */
	public static final AttachmentType<List<Integer>> NIGHT_SENSED = AttachmentRegistry.create(
			Archetypes.id("night_sensed"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),
					AttachmentSyncPredicate.targetOnly()));

	public static final AttachmentType<List<Integer>> NIGHT_SENSED_PLAYERS = AttachmentRegistry.create(
			Archetypes.id("night_sensed_players"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),
					AttachmentSyncPredicate.targetOnly()));

	/** Ghost Form's sneak-dash clock, same shape as the bash's. */
	public static final AttachmentType<Long> NIGHT_DASH_READY_AT = AttachmentRegistry.create(
			Archetypes.id("night_dash_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/** Channel bookkeeping the interrupt test needs: the hotbar slot the ritual
	 * began on, and last tick's hurtTime so a NEW hit can be told from the tail
	 * of an old one. Server-side only. */
	public static final AttachmentType<Integer> NIGHT_CHANNEL_SLOT =
			AttachmentRegistry.<Integer>create(Archetypes.id("night_channel_slot"));

	public static final AttachmentType<Integer> NIGHT_CHANNEL_HURT =
			AttachmentRegistry.<Integer>create(Archetypes.id("night_channel_hurt"));

	// --- Nemesis Marksman (epic): Deadeye ---

	/**
	 * Game tick the running Deadeye stance ends; absent means no stance. Read
	 * it through {@link Deadeye#isActive}, never directly.
	 *
	 * <p>Transient: fifteen seconds cannot meaningfully survive a relog, and
	 * {@code Archetypes}' JOIN handler clears it the way it clears the Dark
	 * Ritual's channel. Synced to EVERY client, for two reasons — the owner's
	 * client predicts a crossbow's charge time from
	 * {@code CrossbowItemMixin.getChargeDuration}, which must return the same
	 * number on both sides, and every client draws the stance's arrow trail.
	 */
	public static final AttachmentType<Long> DEADEYE_END = AttachmentRegistry.create(
			Archetypes.id("deadeye_end"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** Deadeye's cooldown, same shape as the bash's. */
	public static final AttachmentType<Long> DEADEYE_READY_AT = AttachmentRegistry.create(
			Archetypes.id("deadeye_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/**
	 * Siege: the game tick the player last stopped moving, or absent while
	 * they are moving. Planted means this is set AND
	 * {@link Tuning#SIEGE_ARM_TICKS} have passed. Server-side only — the arm's
	 * particles and its pling are sent from the ticker, so no client reads it.
	 */
	public static final AttachmentType<Long> DEADEYE_STILL_SINCE =
			AttachmentRegistry.<Long>create(Archetypes.id("deadeye_still_since"));

	/** Last tick's server-side position, which is what the still test compares.
	 * Deliberately NOT getDeltaMovement: the client owns a player's movement
	 * and the server's copy of it is not trustworthy tick to tick. */
	public static final AttachmentType<net.minecraft.world.phys.Vec3> DEADEYE_LAST_POS =
			AttachmentRegistry.<net.minecraft.world.phys.Vec3>create(Archetypes.id("deadeye_last_pos"));

	/**
	 * On arrows: this one left a bow or crossbow while Deadeye held, and this
	 * one left while the shooter was planted. The stance can lapse mid-flight
	 * — 64 blocks is three seconds — so the arrow carries what it was owed
	 * rather than asking the shooter at impact.
	 *
	 * <p>DEADEYE_ARROW is synced to everyone because every client draws the
	 * crit trail off it; the Siege stamp is server-side damage bookkeeping.
	 */
	public static final AttachmentType<Boolean> DEADEYE_ARROW = AttachmentRegistry.create(
			Archetypes.id("deadeye_arrow"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	public static final AttachmentType<Boolean> DEADEYE_SIEGE_ARROW =
			AttachmentRegistry.<Boolean>create(Archetypes.id("deadeye_siege_arrow"));

	/**
	 * On arrows: empowered by True Shot or conjured by Snap Shot. The epic
	 * tree's multipliers refuse it — the base tree owns the one big shot and
	 * the epic tree buffs the stream, or Snap Shot x Long Shot 2 x Siege is
	 * x24 on a single armour-bypassed arrow.
	 */
	public static final AttachmentType<Boolean> TRUE_SHOT_ARROW =
			AttachmentRegistry.<Boolean>create(Archetypes.id("true_shot_arrow"));

	/** On projectiles: Evasion already waved this one through someone, so the
	 * puff is drawn once per projectile rather than once per collision sweep
	 * ({@code canHitEntity} is asked several times a tick). */
	public static final AttachmentType<Boolean> DEADEYE_PHASED =
			AttachmentRegistry.<Boolean>create(Archetypes.id("deadeye_phased"));

	// --- Nemesis Assassin (epic): Death Mark ---
	// Read all four through {@link DeathMark}, never directly.

	/**
	 * The entity id of the creature this player has marked, and the game tick
	 * the mark lapses; both absent means no mark. Id rather than UUID because
	 * the client-side indicator is an id test and the server resolves the body
	 * with {@code level.getEntity(int)} each time it needs it.
	 *
	 * <p>Target-only: this pair is the OWNER's copy of the mark. What every
	 * other client needs rides on the marked entity itself
	 * ({@link #MARKED_BY}), so nobody has to be told another player's roster.
	 *
	 * <p>Transient: a minute cannot meaningfully survive a relog, and an entity
	 * id is not stable across one anyway — {@code Archetypes}' JOIN handler
	 * clears the mark the way it clears the Deadeye stance.
	 */
	public static final AttachmentType<Integer> MARK_TARGET = AttachmentRegistry.create(
			Archetypes.id("mark_target"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	public static final AttachmentType<Long> MARK_END = AttachmentRegistry.create(
			Archetypes.id("mark_end"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/** Death Mark's cooldown, same shape as the bash's. */
	public static final AttachmentType<Long> DEATH_MARK_READY_AT = AttachmentRegistry.create(
			Archetypes.id("death_mark_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/**
	 * On the MARKED entity: the entity id of the assassin who named it, absent
	 * when nothing has. This is the mark's client-visible channel and the same
	 * one {@code BULWARK_ACTIVE} and {@code DEADEYE_ARROW} already use — state
	 * written onto the entity it describes and synced to everyone, so a
	 * renderer can ask the body rather than be handed a roster. Stalk's
	 * through-wall outline is one id comparison against it.
	 *
	 * <p>Server-side writers only, and always in step with the owner's
	 * {@link #MARK_TARGET} — {@link DeathMark} is the only class that touches
	 * either.
	 */
	public static final AttachmentType<Integer> MARKED_BY = AttachmentRegistry.create(
			Archetypes.id("marked_by"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	// --- Colossus Crusher (epic): Titan's Leap ---
	// Read all four through {@link TitansLeap}, never directly.

	/** Titan's Leap's cooldown, same shape as the bash's. */
	public static final AttachmentType<Long> LEAP_READY_AT = AttachmentRegistry.create(
			Archetypes.id("leap_ready_at"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/**
	 * The game tick a leap left the ground; absent means nobody is in the air
	 * on our account. Presence is the in-flight flag the landing consumes and
	 * the fall-damage waiver reads.
	 *
	 * <p>Server-side only and transient: a leap cannot survive a relog, and
	 * {@code Archetypes}' JOIN handler clears it the way it clears the Dark
	 * Ritual's channel — a restored stamp would waive fall damage forever.
	 */
	public static final AttachmentType<Long> LEAP_AT =
			AttachmentRegistry.<Long>create(Archetypes.id("leap_at"));

	/**
	 * The highest Y the running leap has reached. This, minus the Y it lands
	 * at, is what Aftershock pays per block fallen — deliberately NOT
	 * {@code fallDistance}, which vanilla has already zeroed by the time an
	 * END_SERVER_TICK listener sees the ground (the same trap
	 * {@code SMASH_AT} exists to dodge). Server-side bookkeeping.
	 */
	public static final AttachmentType<Double> LEAP_PEAK_Y =
			AttachmentRegistry.<Double>create(Archetypes.id("leap_peak_y"));

	/** Immovable's last anvil, so a nullified shove is announced once a second
	 * rather than once per hit. Server-side only. */
	public static final AttachmentType<Long> IMMOVABLE_CUE_AT =
			AttachmentRegistry.<Long>create(Archetypes.id("immovable_cue_at"));

	// --- Colossus Slayer (epic): the parry window ---
	// Both are server-side only and transient, and both are read through
	// {@link ColossusSlayer}, never directly. Nothing about a 0.3-second
	// window is worth syncing: the client already knows it pressed the combo,
	// and what the press was WORTH comes back as one {@link ParrySwingPayload}.

	/** The game tick the open parry window closes; absent means no window. */
	public static final AttachmentType<Long> PARRY_UNTIL =
			AttachmentRegistry.<Long>create(Archetypes.id("parry_until"));

	/**
	 * The game tick the window opened. Kept because the miss penalty is
	 * measured from the PRESS, not from the moment the window lapses — a
	 * doubled swing cooldown that started six ticks late would be 2x plus the
	 * window.
	 */
	public static final AttachmentType<Long> PARRY_AT =
			AttachmentRegistry.<Long>create(Archetypes.id("parry_at"));

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
		// A live Magic Armaments channel must end with the node that powers
		// it: Amnesia II and the creative reset drop the archetype the
		// ticker's guards are gated on, so nothing else would restore the
		// wand or revoke the channel's flight and modifiers until relog.
		if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			MagicArmaments.restoreDirty(serverPlayer);
			// Same reason: RadianceAura's ticker is gated on the archetype, so
			// the Amnesia II and reset paths would leave Steadfast's knockback
			// immunity standing on a player who no longer owns the node.
			RadianceAura.end(serverPlayer);
		}

		((AttachmentTarget) player).removeAttached(PURCHASED);
		((AttachmentTarget) player).removeAttached(SPENT_POINTS);
		((AttachmentTarget) player).removeAttached(EPIC_SPENT_POINTS);
		// Proc bookkeeping tied to owned nodes goes too, or a respec inherits
		// it: a Mind Well counter at 7/8 would empower the first missile after
		// re-buying, and an armed True Shot fires without the node.
		((AttachmentTarget) player).removeAttached(MISSILE_CAST_COUNT);
		((AttachmentTarget) player).removeAttached(TRUE_SHOT_ARMED);
		((AttachmentTarget) player).removeAttached(CROSSBOW_PRIMED);

		// The night form outlives everything else this mod grants — an hour is
		// its whole price — so a respec that drops the Dark Ritual has to end
		// it here, or the player keeps the vampire without the node.
		if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			NightForm.end(serverPlayer);
			// Deadeye's Slowness and Fleet's Speed are re-asserted by the
			// ticker, which is gated on the node — so a respec mid-stance would
			// leave the stance's stamp standing and the arrows still free.
			Deadeye.end(serverPlayer);
			// A mark outlives a respec the same way: the flag lives on ANOTHER
			// entity, so nothing but this call would ever take it back off.
			DeathMark.clear(serverPlayer);
			// And an in-flight leap: the stamp is what waives fall damage, and
			// only the landing clears it. A respec mid-air would land on a
			// player who no longer owns the node and never take the waiver back.
			TitansLeap.clear(serverPlayer);
		}

		((AttachmentTarget) player).removeAttached(NIGHT_CHANNEL_END);
		((AttachmentTarget) player).removeAttached(NIGHT_CHANNEL_SLOT);
		((AttachmentTarget) player).removeAttached(NIGHT_CHANNEL_HURT);
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
