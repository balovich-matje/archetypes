package com.archetypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import com.archetypes.platform.ArchetypeStore;
import com.archetypes.state.StateKey;
import com.archetypes.state.StateKey.Sync;
import com.archetypes.state.WireCodec;

import net.minecraft.world.entity.player.Player;
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

public final class ModState {
	/**
	 * Every key below, in declaration order. Declared FIRST on purpose: the constants
	 * file themselves into it as they are constructed, so a list declared after them
	 * is still null when the first one runs and class initialisation dies with an
	 * {@code ExceptionInInitializerError} the compiler cannot see.
	 */
	private static final List<StateKey<?>> ALL = new ArrayList<>();

	/**
	 * The player's chosen archetype, or absent if they haven't picked yet.
	 * Server-authoritative; persisted with the player and synced to the owning
	 * client only.
	 */
	public static final StateKey<String> ARCHETYPE = key(StateKey.<String>of("archetype", String.class)
			.persist(Codec.STRING)
			.sync(WireCodec.STRING, Sync.TARGET_ONLY)
			.copyOnDeath());

	/**
	 * Total experience the archetype has banked, in vanilla XP points. Mirrors the
	 * player's own XP gain rather than spending it — see "archetype XP is vanilla
	 * XP" in notes/design.md — so archetype progress never competes with
	 * enchanting.
	 */
	public static final StateKey<Integer> ARCHETYPE_XP = key(StateKey.<Integer>of("archetype_xp", Integer.class)
			.persist(Codec.INT)
			.sync(WireCodec.VAR_INT, Sync.TARGET_ONLY)
			.copyOnDeath());

	/** Cached count of completed non-recipe advancements, all frames together.
	 * Transient (recounted on join and on every real advancement) but synced,
	 * so the tree screen can show the live rate. */
	public static final StateKey<Integer> ADVANCEMENT_COUNT = key(StateKey.<Integer>of("advancement_count", Integer.class)
			.sync(WireCodec.VAR_INT, Sync.TARGET_ONLY)
			.copyOnDeath());

	/** How many of {@link #ADVANCEMENT_COUNT} carry the goal frame. The XP rate
	 * is frame-weighted, so the three tiers are counted apart; tasks are the
	 * remainder, which keeps the total attachment above meaning what it says. */
	public static final StateKey<Integer> ADVANCEMENT_GOALS = key(StateKey.<Integer>of("advancement_goals", Integer.class)
			.sync(WireCodec.VAR_INT, Sync.TARGET_ONLY)
			.copyOnDeath());

	/** How many of {@link #ADVANCEMENT_COUNT} carry the challenge frame. */
	public static final StateKey<Integer> ADVANCEMENT_CHALLENGES = key(StateKey.<Integer>of("advancement_challenges", Integer.class)
			.sync(WireCodec.VAR_INT, Sync.TARGET_ONLY)
			.copyOnDeath());

	/** Normal points committed to base sub-trees. Earned minus this is what's
	 * spendable there. */
	public static final StateKey<Integer> SPENT_POINTS = key(StateKey.<Integer>of("spent_points", Integer.class)
			.persist(Codec.INT)
			.sync(WireCodec.VAR_INT, Sync.TARGET_ONLY)
			.copyOnDeath());

	/** Epic points committed to epic sub-trees. Kept apart from the normal
	 * pool so levels 46-60 feed only the epic trees. */
	public static final StateKey<Integer> EPIC_SPENT_POINTS = key(StateKey.<Integer>of("epic_spent_points", Integer.class)
			.persist(Codec.INT)
			.sync(WireCodec.VAR_INT, Sync.TARGET_ONLY)
			.copyOnDeath());

	/**
	 * Game-time tick when the bash's ability layer comes off cooldown. Transient
	 * on purpose — a relog clearing a few seconds of cooldown is harmless — but
	 * synced, so the client can draw the countdown without a bespoke packet.
	 */
	public static final StateKey<Long> BASH_READY_AT = key(StateKey.<Long>of("bash_ready_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	/**
	 * True while a Bulwark holder is actively blocking. Synced to every client —
	 * the aura is information for the mob circling behind you, not just for you.
	 * Maintained by ProtectorTicker; absent means off.
	 */
	public static final StateKey<Boolean> BULWARK_ACTIVE = key(StateKey.<Boolean>of("bulwark_active", Boolean.class)
			.sync(WireCodec.BOOL, Sync.ALL_TRACKING));

	/**
	 * Game-time tick a Shield Sweep swung on, synced to everyone so the shield
	 * swing plays for onlookers and not only for the caster.
	 *
	 * <p>The bash's own {@code player.swing} is broadcast by vanilla and would
	 * have been free, the way the dagger's stab is — but it is broadcast for
	 * EVERY bash, and only a capstone holder's bash is a sweep. A stamp is the
	 * cheapest thing that says which. Which arms swing is not stamped: held
	 * items are tracked equipment, so every client can already see whether this
	 * player has one shield or two.
	 */
	public static final StateKey<Long> SHIELD_SWEEP_AT = key(StateKey.<Long>of("shield_sweep_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.ALL_TRACKING));

	/** Slayer active cooldowns, same shape as the bash's. */
	public static final StateKey<Long> DECIMATE_READY_AT = key(StateKey.<Long>of("decimate_ready_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	/** The Colossus Slayer riposte's own Decimate clock. Server-side only and
	 * deliberately NOT the same stamp as {@code DECIMATE_READY_AT}: the author's
	 * rule is that a parried Decimate neither pays nor waits on the key's
	 * cooldown, so the free path needs a fence that is not that one. Unsynced
	 * because nothing draws it — the cooldown bar shows the key, and the key is
	 * genuinely untouched. */
	public static final StateKey<Long> DECIMATE_FREE_READY_AT = key(StateKey.<Long>of("decimate_free_ready_at", Long.class));

	public static final StateKey<Long> BLADESTORM_READY_AT = key(StateKey.<Long>of("bladestorm_ready_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	/** Game-time tick when a Decimate swing started; synced to every client so
	 * the cleave pose plays for onlookers too. */
	public static final StateKey<Long> DECIMATE_SWING_AT = key(StateKey.<Long>of("decimate_swing_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.ALL_TRACKING));

	/**
	 * The tick a Decimate was cast WITHOUT its wind-up — the parry riposte's
	 * instant path. Synced to everyone for the same reason the pose stamp is:
	 * the client picks the charge animation or the short cleave off these two,
	 * and an onlooker who cannot tell them apart watches a telegraph play for a
	 * blow that already landed. Equal to {@code DECIMATE_SWING_AT} exactly when
	 * this cast was the instant one; a stale value from an earlier riposte is
	 * therefore harmless.
	 */
	public static final StateKey<Long> DECIMATE_INSTANT_AT = key(StateKey.<Long>of("decimate_instant_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.ALL_TRACKING));


	/** Game-time tick when the current bladestorm channel ends; synced to every
	 * client so the spinning-blade renderer works for onlookers too. */
	public static final StateKey<Long> BLADESTORM_END = key(StateKey.<Long>of("bladestorm_end", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.ALL_TRACKING));

	/** Quake: cooldown, and the charge's end tick — synced to everyone so the
	 * rising-mace pose plays for onlookers too. */
	public static final StateKey<Long> QUAKE_READY_AT = key(StateKey.<Long>of("quake_ready_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	public static final StateKey<Long> QUAKE_CHARGE_END = key(StateKey.<Long>of("quake_charge_end", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.ALL_TRACKING));

	/** Haymaker's cooldown, same shape as the bash's. */
	public static final StateKey<Long> HAYMAKER_READY_AT = key(StateKey.<Long>of("haymaker_ready_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	/** Battle Trance's last-hit mark. Pure server-side bookkeeping — the
	 * ticker reads it, nobody else. */
	public static final StateKey<Long> TRANCE_HIT_AT = key(StateKey.<Long>of("trance_hit_at", Long.class));

	/** The tick of the player's last true mace smash. Stamped during damage
	 * shaping, where fall distance is still intact — the mace's own post-hit
	 * hook resets it before AFTER_DAMAGE listeners ever run. */
	public static final StateKey<Long> SMASH_AT = key(StateKey.<Long>of("smash_at", Long.class));

	/** Lunge's little hop cooldown. Server-only decision, synced for symmetry. */
	public static final StateKey<Long> LUNGE_READY_AT = key(StateKey.<Long>of("lunge_ready_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	/** As BASH_READY_AT, for the rush's own short anti-exploit cooldown. */
	public static final StateKey<Long> RUSH_READY_AT = key(StateKey.<Long>of("rush_ready_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	/**
	 * The Seeker's mana pool. Persistent and synced to the owner so the bottle
	 * bar reads it directly; absent means full (a fresh Seeker starts topped
	 * up). Maximum and regen live in {@link Mana}, driven by the Spellcasting
	 * skill when Specialities is installed.
	 */
	public static final StateKey<Float> MANA = key(StateKey.<Float>of("mana", Float.class)
			.persist(Codec.FLOAT)
			.sync(WireCodec.FLOAT, Sync.TARGET_ONLY)
			.copyOnDeath());

	/** Fractional Spellcasting XP not yet big enough to award as a whole point. */
	public static final StateKey<Float> MANA_XP_REMAINDER = key(StateKey.<Float>of("mana_xp_remainder", Float.class));

	/** Agility active cooldowns, same shape as the bash's. */
	public static final StateKey<Long> TRUE_SHOT_READY_AT = key(StateKey.<Long>of("true_shot_ready_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	public static final StateKey<Long> INVIS_READY_AT = key(StateKey.<Long>of("invis_ready_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	public static final StateKey<Long> SHADOW_STEP_READY_AT = key(StateKey.<Long>of("shadow_step_ready_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	/** The Last Shadow capstone's own long clock, separate from the invis one. */
	public static final StateKey<Long> CHEAT_DEATH_READY_AT = key(StateKey.<Long>of("cheat_death_ready_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	/** True Shot armed: the next bow shot leaving this player gets empowered. */
	public static final StateKey<Boolean> TRUE_SHOT_ARMED = key(StateKey.<Boolean>of("true_shot_armed", Boolean.class)
			.sync(WireCodec.BOOL, Sync.TARGET_ONLY));

	/** Cheat-death's two-second grace: hurtServer returns false until this tick. */
	public static final StateKey<Long> IMMUNE_UNTIL = key(StateKey.<Long>of("immune_until", Long.class));

	/** Deathblow: the tick of the Shadow Step strike being delivered right
	 * now, so the damage shaping can tell it from an ordinary swing. */
	public static final StateKey<Long> STEP_STRIKE_AT = key(StateKey.<Long>of("step_strike_at", Long.class));

	/** Ghost Armor: armor hides with its invisible wearer. Synced to every
	 * client — it's the OTHER players' renderers that need to know. */
	public static final StateKey<Boolean> ARMOR_HIDDEN = key(StateKey.<Boolean>of("armor_hidden", Boolean.class)
			.sync(WireCodec.BOOL, Sync.ALL_TRACKING));

	/** Disengage's short anti-spam clock. Server-side only. */
	public static final StateKey<Long> DISENGAGE_READY_AT = key(StateKey.<Long>of("disengage_ready_at", Long.class));

	/** Rapid Reload: a crossbow kill primes the next reload. Synced to the
	 * owner because the client predicts charge time for the draw animation. */
	public static final StateKey<Boolean> CROSSBOW_PRIMED = key(StateKey.<Boolean>of("crossbow_primed", Boolean.class)
			.sync(WireCodec.BOOL, Sync.TARGET_ONLY));

	/** Magic Missile bookkeeping: the last cast tick (the 200ms breath) and
	 * the running cast count Mind Well empowers every Nth of. */
	public static final StateKey<Long> MISSILE_CAST_AT = key(StateKey.<Long>of("missile_cast_at", Long.class));

	public static final StateKey<Integer> MISSILE_CAST_COUNT = key(StateKey.<Integer>of("missile_cast_count", Integer.class));

	/**
	 * Flamethrower channel: the last tick a channel payload arrived. The gap
	 * since this tick is what tells the server a channel ended (see
	 * {@link SeekerSpells#isChannellingFlame}), and the same question drives
	 * the aimed-wand pose — so it is synced to EVERY client, not just the
	 * caster's: the pose has to play for onlookers exactly the way the Dark
	 * Ritual's does. The cost is one VAR_LONG per channelling player per tick
	 * to their trackers, which is the price of the channel having no packet of
	 * its own; a channel is a handful of seconds and the alternative (a
	 * coarser "channel until" horizon synced every N ticks) would leave the
	 * pose standing for those N ticks after the key came up.
	 */
	public static final StateKey<Long> FLAME_LAST_TICK = key(StateKey.<Long>of("flame_last_tick", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.ALL_TRACKING));

	/**
	 * Magic Armaments channel: the real wand pulled out of the hand while the
	 * conjured weapon stands in for it. Presence is the channel's on-flag.
	 * Persistent and copyOnDeath so a relog, crash or death never eats the
	 * player's wand — {@link MagicArmaments#restoreDirty} puts it back on JOIN if
	 * a channel died mid-flight, and the death hook restores it before drops.
	 * Server-only; no client mirrors it.
	 */
	public static final StateKey<net.minecraft.world.item.ItemStack> ARMAMENTS_WAND = key(StateKey.<net.minecraft.world.item.ItemStack>of("armaments_wand", net.minecraft.world.item.ItemStack.class)
			.persist(net.minecraft.world.item.ItemStack.CODEC)
			.copyOnDeath());

	/** The hotbar slot the wand was pulled from, so it goes back exactly where
	 * it was. Persistent/copyOnDeath alongside the wand it pairs with. */
	public static final StateKey<Integer> ARMAMENTS_SLOT = key(StateKey.<Integer>of("armaments_slot", Integer.class)
			.persist(Codec.INT)
			.copyOnDeath());

	// No upkeep-beat or flight-grant attachment: upkeep is charged every tick
	// (nothing to remember between charges) and Levitation lets VANILLA decide
	// the glide rather than borrowing a mayfly that must be given back. Which
	// question vanilla is asked is version-shaped — the GLIDER component on the
	// conjured weapon at 1.21.11 and up, the chest-slot read below it (see
	// MagicArmaments, above fitGlider) — but the answer is stock fall-flying on
	// every node and nothing about it is state this class has to keep.

	/** On arrows: where a True Shot left the bow (it despawns 64 blocks out),
	 * and whether it steers itself. Transient — a saved arrow forgets. */
	public static final StateKey<net.minecraft.world.phys.Vec3> TRUE_SHOT_ORIGIN = key(StateKey.<net.minecraft.world.phys.Vec3>of("true_shot_origin", net.minecraft.world.phys.Vec3.class));

	public static final StateKey<Boolean> TRUE_SHOT_HOMING = key(StateKey.<Boolean>of("true_shot_homing", Boolean.class));

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
	public static final StateKey<Boolean> SPELLBOW_ARROW = key(StateKey.<Boolean>of("spellbow_arrow", Boolean.class)
			.sync(WireCodec.BOOL, Sync.ALL_TRACKING));

	/** On arrows, for one hit-handler call: the return-to-sender velocity a
	 * Reflection block computed. Applied and cleared at the end of the hit —
	 * vanilla's post-deflect drop would stomp it if set any earlier. */
	public static final StateKey<net.minecraft.world.phys.Vec3> REFLECT_AIM = key(StateKey.<net.minecraft.world.phys.Vec3>of("reflect_aim", net.minecraft.world.phys.Vec3.class));

	/**
	 * Game tick when the running Aura of Radiance ends; absent means no aura.
	 * Deliberately transient and NOT copyOnDeath: the aura is a ten-second
	 * consequence of a cast, so a relog or a death simply ends it, and
	 * {@link RadianceAura}'s ticker never has to reconcile an aura it did not
	 * start. Synced to EVERY client, not just the owner: the aura's light is
	 * drawn client-side around whoever is glowing, so an onlooker's client has
	 * to know the aura is up. Read it through {@link RadianceAura#isActive}.
	 */
	public static final StateKey<Long> RADIANCE_END = key(StateKey.<Long>of("radiance_end", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.ALL_TRACKING));

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
	public static final StateKey<Long> NIGHT_FORM_SINCE = key(StateKey.<Long>of("night_form_since", Long.class)
			.persist(Codec.LONG)
			.sync(WireCodec.VAR_LONG, Sync.ALL_TRACKING)
			.copyOnDeath());

	/**
	 * Game tick the running Dark Ritual channel completes; absent means no
	 * channel. Transient — a channel cannot survive a relog, and it costs
	 * nothing to lose one. Synced to everyone: the channel has a first- AND
	 * third-person animation, so onlookers' renderers read this too.
	 */
	public static final StateKey<Long> NIGHT_CHANNEL_END = key(StateKey.<Long>of("night_channel_end", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.ALL_TRACKING));

	/**
	 * True while the transformed player stands in sunlight strong enough to
	 * burn them. Synced to everyone (it drives an on-screen effect for the
	 * owner and could dress the avatar for onlookers); set and cleared by
	 * {@link NightFormTicker}, never by the client.
	 */
	public static final StateKey<Boolean> NIGHT_SUNLIT = key(StateKey.<Boolean>of("night_sunlit", Boolean.class)
			.sync(WireCodec.BOOL, Sync.ALL_TRACKING));

	/**
	 * Extra Sensory Perception's two rosters of entity ids, refreshed every
	 * {@link Tuning#ESP_REFRESH_TICKS}: everything living in range, and the
	 * players among them kept apart so the renderer can mark them out
	 * distinctly (author's spec). Target-only — nobody else's client has any
	 * use for what this player can sense.
	 */
	public static final StateKey<List<Integer>> NIGHT_SENSED = key(StateKey.<List<Integer>>of("night_sensed", List.class)
			.sync(WireCodec.INT_LIST, Sync.TARGET_ONLY));

	public static final StateKey<List<Integer>> NIGHT_SENSED_PLAYERS = key(StateKey.<List<Integer>>of("night_sensed_players", List.class)
			.sync(WireCodec.INT_LIST, Sync.TARGET_ONLY));

	/** Ghost Form's sneak-dash clock, same shape as the bash's. */
	public static final StateKey<Long> NIGHT_DASH_READY_AT = key(StateKey.<Long>of("night_dash_ready_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	/** Channel bookkeeping the interrupt test needs: the hotbar slot the ritual
	 * began on, and last tick's hurtTime so a NEW hit can be told from the tail
	 * of an old one. Server-side only. */
	public static final StateKey<Integer> NIGHT_CHANNEL_SLOT = key(StateKey.<Integer>of("night_channel_slot", Integer.class));

	public static final StateKey<Integer> NIGHT_CHANNEL_HURT = key(StateKey.<Integer>of("night_channel_hurt", Integer.class));

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
	public static final StateKey<Long> DEADEYE_END = key(StateKey.<Long>of("deadeye_end", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.ALL_TRACKING));

	/** Deadeye's cooldown, same shape as the bash's. */
	public static final StateKey<Long> DEADEYE_READY_AT = key(StateKey.<Long>of("deadeye_ready_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	/**
	 * Siege: the game tick the player last stopped moving, or absent while
	 * they are moving. Planted means this is set AND
	 * {@link Tuning#SIEGE_ARM_TICKS} have passed. Server-side only — the arm's
	 * particles and its pling are sent from the ticker, so no client reads it.
	 */
	public static final StateKey<Long> DEADEYE_STILL_SINCE = key(StateKey.<Long>of("deadeye_still_since", Long.class));

	/** Last tick's server-side position, which is what the still test compares.
	 * Deliberately NOT getDeltaMovement: the client owns a player's movement
	 * and the server's copy of it is not trustworthy tick to tick. */
	public static final StateKey<net.minecraft.world.phys.Vec3> DEADEYE_LAST_POS = key(StateKey.<net.minecraft.world.phys.Vec3>of("deadeye_last_pos", net.minecraft.world.phys.Vec3.class));

	/**
	 * On arrows: this one left a bow or crossbow while Deadeye held, and this
	 * one left while the shooter was planted. The stance can lapse mid-flight
	 * — 64 blocks is three seconds — so the arrow carries what it was owed
	 * rather than asking the shooter at impact.
	 *
	 * <p>DEADEYE_ARROW is synced to everyone because every client draws the
	 * crit trail off it; the Siege stamp is server-side damage bookkeeping.
	 */
	public static final StateKey<Boolean> DEADEYE_ARROW = key(StateKey.<Boolean>of("deadeye_arrow", Boolean.class)
			.sync(WireCodec.BOOL, Sync.ALL_TRACKING));

	public static final StateKey<Boolean> DEADEYE_SIEGE_ARROW = key(StateKey.<Boolean>of("deadeye_siege_arrow", Boolean.class));

	/**
	 * On arrows: empowered by True Shot or conjured by Snap Shot. The epic
	 * tree's multipliers refuse it — the base tree owns the one big shot and
	 * the epic tree buffs the stream, or Snap Shot x Long Shot 2 x Siege is
	 * x24 on a single armour-bypassed arrow.
	 */
	public static final StateKey<Boolean> TRUE_SHOT_ARROW = key(StateKey.<Boolean>of("true_shot_arrow", Boolean.class));

	/** On projectiles: Evasion already waved this one through someone, so the
	 * puff is drawn once per projectile rather than once per collision sweep
	 * ({@code canHitEntity} is asked several times a tick). */
	public static final StateKey<Boolean> DEADEYE_PHASED = key(StateKey.<Boolean>of("deadeye_phased", Boolean.class));

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
	public static final StateKey<Integer> MARK_TARGET = key(StateKey.<Integer>of("mark_target", Integer.class)
			.sync(WireCodec.VAR_INT, Sync.TARGET_ONLY));

	public static final StateKey<Long> MARK_END = key(StateKey.<Long>of("mark_end", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	/** Death Mark's cooldown, same shape as the bash's. */
	public static final StateKey<Long> DEATH_MARK_READY_AT = key(StateKey.<Long>of("death_mark_ready_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	/**
	 * On the MARKED entity: the entity id of the assassin who named it, absent
	 * when nothing has. This is the mark's client-visible channel and the same
	 * one {@code BULWARK_ACTIVE} and {@code DEADEYE_ARROW} already use — state
	 * written onto the entity it describes and synced to everyone, so a
	 * renderer can ask the body rather than be handed a roster. The mark's red
	 * outline is one id comparison against it, and Stalk's through-wall
	 * exemption is that same comparison plus a rank.
	 *
	 * <p>Server-side writers only, and always in step with the owner's
	 * {@link #MARK_TARGET} — {@link DeathMark} is the only class that touches
	 * either.
	 */
	public static final StateKey<Integer> MARKED_BY = key(StateKey.<Integer>of("marked_by", Integer.class)
			.sync(WireCodec.VAR_INT, Sync.ALL_TRACKING));

	// --- Colossus Crusher (epic): Titan's Leap, and Hardened's plates ---
	// The three leap stamps are read through {@link TitansLeap} and the plates
	// through {@link Hardened}, never directly.

	/** Titan's Leap's cooldown, same shape as the bash's. */
	public static final StateKey<Long> LEAP_READY_AT = key(StateKey.<Long>of("leap_ready_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	/**
	 * The game tick a leap left the ground; absent means nobody is in the air
	 * on our account. Presence is the in-flight flag the landing consumes and
	 * the fall-damage waiver reads.
	 *
	 * <p>Server-side only and transient: a leap cannot survive a relog, and
	 * {@code Archetypes}' JOIN handler clears it the way it clears the Dark
	 * Ritual's channel — a restored stamp would waive fall damage forever.
	 */
	public static final StateKey<Long> LEAP_AT = key(StateKey.<Long>of("leap_at", Long.class));

	/**
	 * The highest Y the running leap has reached. This, minus the Y it lands
	 * at, is what Aftershock pays per block fallen — deliberately NOT
	 * {@code fallDistance}, which vanilla has already zeroed by the time an
	 * END_SERVER_TICK listener sees the ground (the same trap
	 * {@code SMASH_AT} exists to dodge). Server-side bookkeeping.
	 */
	public static final StateKey<Double> LEAP_PEAK_Y = key(StateKey.<Double>of("leap_peak_y", Double.class));

	/**
	 * The tick a bare-fisted landing's eight seconds run out. Read only by
	 * {@code CrusherTicker}, which asserts the extra unarmed ATTACK_DAMAGE for
	 * as long as it holds, and written only by {@link TitansLeap}.
	 *
	 * <p>Transient and unsynced, for the same reason Hardened's plates are: the
	 * client already sees the ATTACK_DAMAGE attribute vanilla syncs, so there
	 * is nothing left for a packet to say, and eight seconds of a buff is not
	 * worth surviving a relog. Deliberately NOT cleared by
	 * {@link TitansLeap#clear} — clear() runs on the landing tick, immediately
	 * after the landing set this — so the ticker gates the modifier on owning
	 * Titan's Leap instead, which is what takes it off a respec.
	 */
	public static final StateKey<Long> LEAP_STOMP_END = key(StateKey.<Long>of("leap_stomp_end", Long.class));

	/**
	 * Hardened's live plates — one entry per hit taken, each with its own
	 * expiry, read and written only through {@link Hardened}. A list, not a
	 * count and a deadline, because the node's whole promise is that a new hit
	 * never refreshes an older plate.
	 *
	 * <p>Transient, unsynced and server-side: nothing about two seconds of
	 * armour is worth a packet (the client sees the ARMOR attribute vanilla
	 * already syncs), and plates must not survive a relog or a death — a fresh
	 * entity with no attachment is exactly the reset the node wants.
	 */
	public static final StateKey<List<Hardened.Plate>> HARDENED_PLATES = key(StateKey.<List<Hardened.Plate>>of("hardened_plates", List.class));

	/** The Protector's Immovable Object cue, at most one a second. Its own
	 * stamp on the Protector's own node — the Crusher's Immovable, which used
	 * to need a second one alongside it, is gone. Server-side only. */
	public static final StateKey<Long> IMMOVABLE_OBJECT_CUE_AT = key(StateKey.<Long>of("immovable_object_cue_at", Long.class));

	// --- Colossus Slayer (epic): the parry ---
	/**
	 * Game tick the Parry key answers again — eight seconds after a missed
	 * parry, one or two after a landed one depending on the blade (see
	 * {@code Tuning.PARRY_SUCCESS_GREATSWORD_COOLDOWN_TICKS}). Synced like every
	 * other ready-at stamp because the cooldown bar draws a tile from it; the
	 * server still re-checks it, since the stamp is the only thing stopping a
	 * client that presses every tick from standing in a permanent window.
	 */
	public static final StateKey<Long> PARRY_READY_AT = key(StateKey.<Long>of("parry_ready_at", Long.class)
			.sync(WireCodec.VAR_LONG, Sync.TARGET_ONLY));

	/**
	 * The game tick the open parry window closes; absent means no window.
	 * Server-side only and transient, and read through {@link ColossusSlayer},
	 * never directly — nothing about a 0.4-second window is worth syncing: the
	 * client already knows it pressed the key, and what the press was WORTH
	 * comes back as one {@link ParrySwingPayload}.
	 */
	public static final StateKey<Long> PARRY_UNTIL = key(StateKey.<Long>of("parry_until", Long.class));

	/** Owned nodes, per sub-tree id, as indices into its constellation's node list. */
	public static final StateKey<Map<String, List<Integer>>> PURCHASED = key(StateKey.<Map<String, List<Integer>>>of("purchased", Map.class)
			.persist(Codec.unboundedMap(Codec.STRING, Codec.INT.listOf()))
			.sync(WireCodec.PURCHASE_MAP, Sync.TARGET_ONLY)
			.copyOnDeath());

	/**
	 * Declares one key and files it for {@link #initialize()}. Every constant above
	 * goes through here, which is what makes "the table IS the registration" true —
	 * a key that is declared but not handed to the platform store would read as
	 * permanently absent, with no error anywhere.
	 */
	private static <T> StateKey<T> key(final StateKey.Builder<T> builder) {
		StateKey<T> k = builder.build();
		ALL.add(k);
		return k;
	}

	/** The whole table, in declaration order. */
	public static List<StateKey<?>> all() {
		return List.copyOf(ALL);
	}

	private ModState() {
	}

	public static void initialize() {
		// Forces static initialization at mod init time, then hands the table to
		// whatever the loader underneath stores state in. Both halves matter: the
		// keys do not exist until this class initialises, and they do nothing until
		// the store has seen them.
		ArchetypeStore.INSTANCE.register(all());
	}

	/** The player's archetype, or null if unpicked. */
	public static @Nullable Archetype get(final Player player) {
		String id = ArchetypeStore.INSTANCE.get(player, ARCHETYPE);
		return id == null ? null : Archetype.byId(id).orElse(null);
	}

	public static void set(final Player player, final Archetype archetype) {
		ArchetypeStore.INSTANCE.set(player, ARCHETYPE, archetype.id());
	}

	/**
	 * Back to unpicked, so the picker opens again. Creative-only, for
	 * testing — banked levels SURVIVE (user call: hopping between trees in
	 * creative shouldn't need a x45 token every time). Amnesia II remains
	 * the survival path that wipes levels.
	 */
	public static void clear(final Player player) {
		forgetNodes(player);
		ArchetypeStore.INSTANCE.remove(player, ARCHETYPE);
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

		ArchetypeStore.INSTANCE.remove(player, PURCHASED);
		ArchetypeStore.INSTANCE.remove(player, SPENT_POINTS);
		ArchetypeStore.INSTANCE.remove(player, EPIC_SPENT_POINTS);
		// Proc bookkeeping tied to owned nodes goes too, or a respec inherits
		// it: a Mind Well counter at 7/8 would empower the first missile after
		// re-buying, and an armed True Shot fires without the node.
		ArchetypeStore.INSTANCE.remove(player, MISSILE_CAST_COUNT);
		ArchetypeStore.INSTANCE.remove(player, TRUE_SHOT_ARMED);
		ArchetypeStore.INSTANCE.remove(player, CROSSBOW_PRIMED);

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
			// Hardened's plates and the ARMOR modifier they feed: the ticker
			// would drop both next tick anyway, but "next tick" is a tick of
			// armour a player who just sold the node has no claim to.
			Hardened.clear(serverPlayer);
			ColossusSlayer.clearWindow(serverPlayer);
			// Well Fed's banked hunger, which is the odd one out: not a
			// modifier and not a key of ours but points sitting in vanilla's
			// FoodData above twenty, so removing PURCHASED above lowers the
			// CEILING and leaves the bank standing over it — the extra cap and
			// its halo survived every reset (user report). Runs after the
			// removal on purpose: the trim reads the ceiling the player is
			// entitled to NOW.
			ColossusProtector.trimBankedHunger(serverPlayer);
		}

		ArchetypeStore.INSTANCE.remove(player, NIGHT_CHANNEL_END);
		ArchetypeStore.INSTANCE.remove(player, NIGHT_CHANNEL_SLOT);
		ArchetypeStore.INSTANCE.remove(player, NIGHT_CHANNEL_HURT);
	}

	/**
	 * Amnesia II: the choice itself forgotten — nodes, archetype AND every
	 * banked level. A full restart, so switching class late costs what it
	 * should (user call; Amnesia I's gentler price lives in SkillPoints).
	 */
	public static void forgetArchetype(final Player player) {
		forgetNodes(player);
		ArchetypeStore.INSTANCE.remove(player, ARCHETYPE);
		ArchetypeStore.INSTANCE.remove(player, ARCHETYPE_XP);
	}
}
