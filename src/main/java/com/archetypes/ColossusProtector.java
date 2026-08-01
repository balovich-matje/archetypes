package com.archetypes;

import com.archetypes.ColossusProtectorNodes.Family;

import com.archetypes.platform.ArchetypeStore;

import net.minecraft.world.entity.Entity;
//? if >=1.21.11 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
// ─── R-A5 IS CLOSED: NOTHING IN THIS CLUSTER IS EXCISED ANY MORE ─────────────────────────
// The row began as four nodes said to have no host below 1.21.11, on the premise that the
// `BlocksAttacks` COMPONENT and `LivingEntity.applyItemBlocking` arrive together at that
// version and that everything the cluster does depends on them. Each of the four has since
// been re-derived from the legacy bytecode, and all four are LIVE on the legacy family:
//
//   * IMMOVABLE OBJECT — `Player.disableShield` is ONE chokepoint, not two. The
//     `ItemCooldowns` write IS the body of `disableShield`, and `disableShield` is named by
//     exactly one class in the whole jar on every legacy target (`Player` itself, one call,
//     from `blockUsingShield`). See `PlayerMixin.archetypes$immovableObject`.
//   * UNSTOPPABLE FORCE (a Colossus CRUSHER node sharing this cluster's host) — re-rooted
//     onto `isDamageSourceBlocked`, which is the whole legacy blocking branch in one
//     boolean. See `LivingEntityMixin.archetypes$unstoppableForce`'s legacy arm.
//   * INSTINCTIVE GUARD — this class, below. The legacy predicate is vanilla's own
//     `isDamageSourceBlocked` clause list minus the facing test, and the 26.x arithmetic
//     collapses onto it EXACTLY rather than approximately: the shield component's
//     `DamageReduction(90°, base 0, factor 1)` resolved at the literal angle 0 that
//     Instinctive Guard passes is `blockable == amount` always, and legacy's
//     `hurtCurrentlyUsedShield` rule `if (amount >= 3) hurt(1 + floor(amount))` is
//     `ItemDamageFunction(3, 1, 1)`'s `floor(1 + amount)` byte for byte (floor(1+x) ==
//     1+floor(x) for x >= 0). The node keeps its promise, not a version of it.
//   * OMNI BLOCK — `Vec3.dot` inside `isDamageSourceBlocked` is the facing test's ENTIRE
//     contribution and it occurs exactly once in the whole `LivingEntity` class on all four
//     legacy targets. See `LivingEntityMixin.archetypes$bulwark`'s legacy arm.
//
// ONE RESIDUAL DIVERGENCE, deliberately kept: 26.2's shield lists `lightning_bolt` (and
// several null-position sources) in `bypassed_by`, where the legacy `#bypasses_shield` tag
// does not. The null-position ones are covered by keeping vanilla's own
// `getSourcePosition() == null` clause; lightning is not, so a legacy Instinctive Guard
// soaks it — which is exactly what a RAISED vanilla shield does on 1.21.1/1.20.1. The node
// keeps its own version's contract on its own version.
//
// `inertNodeKeys` in the three node scripts is empty as a result, and the lang strings ship
// unqualified on every node. Everything else in this class — Ironclad's armour multiplier,
// Hearty Meal, Well Fed, Free Hand, and the two `blocking(...)` reads — was never affected.
//? if >=1.21.11 {
import net.minecraft.world.item.component.BlocksAttacks;
//?}

// STAGE 6 — the loader-event helpers live in `com.archetypes.platform`, the one package
// allowed to name loader API (conventions §5g). Only this import and the registration line
// below fork; the tick body is one implementation on all seven nodes.
//? if fabric {
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
//?} elif neoforge {
/*import com.archetypes.platform.NeoForgeEvents;
*///?} elif forge {
/*import com.archetypes.platform.ForgeEvents;
*///?}
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

/**
 * Every node of the epic Colossus-Protector tree. The sketch dropped the
 * planted-Aegis active the design doc drafted, so this tree has no ability key
 * and no timed window: it is six passives that are simply always on, and every
 * answer here is recomputed from node ownership and what is in the player's
 * hands. The one piece of state is Immovable Object's cue stamp, and even that
 * is borrowed rather than owned (see {@link #immovableObject}).
 *
 * <p>The hooks that call in live where the mod already keeps hooks of their
 * shape: the armour multiplier is a transient attribute modifier asserted from
 * a ticker ({@link RadianceAura}'s Steadfast), Instinctive Guard is a
 * victim-side {@code hurtServer} shaper ({@code LivingEntityMixin}'s Mana
 * Shield), and the rest are small mixins named in {@code archetypes.mixins.json}.
 */
public final class ColossusProtector {
	/** No vanilla tag names either group: {@code minecraft:meat} exists but is
	 * land animals only, and there is no fruit tag at all. Ours wrap the
	 * vanilla sets where they exist rather than re-listing them. */
	public static final TagKey<Item> MEAT = TagKey.create(Registries.ITEM, Archetypes.id("meat"));
	public static final TagKey<Item> FRUIT = TagKey.create(Registries.ITEM, Archetypes.id("fruit"));

	private static final Identifier IRONCLAD_ARMOUR_ID = Archetypes.id("ironclad_armour");
	private static final Identifier IRONCLAD_TOUGHNESS_ID = Archetypes.id("ironclad_toughness");

	private ColossusProtector() {
	}

	public static int rank(final Player player, final Family family) {
		return ColossusProtectorNodes.rank(player, family);
	}

	public static void initialize() {
		// Registration only; the body below is shared. A loader helper fires its consumer ONCE
		// per server tick, at the END of it, with the `MinecraftServer` — the END_SERVER_TICK
		// contract, which is what R-20 says a re-rooted event has to reproduce rather than
		// merely fire somewhere plausible.
		//? if fabric {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
		//?} elif neoforge {
		/*NeoForgeEvents.endServerTick(server -> {
		*///?} elif forge {
		/*ForgeEvents.endServerTick(server -> {
		*///?}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				// Every player, not just Brawlers: revoking the multiplier is
				// this call's job, and a player whose archetype was wiped
				// (Amnesia II, the creative reset) would never be visited
				// again and would keep x1.5 armour for the session.
				ironclad(player);
			}
		});
	}

	/**
	 * Ironclad: armour and armour toughness from every source, times 1.5.
	 *
	 * <p>{@code ADD_MULTIPLIED_TOTAL} is precisely the author's "works as a
	 * final multiplier" — {@code AttributeInstance.calculateValue} sums every
	 * {@code ADD_VALUE} (which is how armour pieces, enchantments and potions
	 * all arrive), then applies the base multipliers, and only then multiplies
	 * by {@code 1 + amount}. So the node is blind to where the armour came
	 * from, which is what the description promises.
	 *
	 * <p>Asserted and revoked from the same call every tick, {@link
	 * RadianceAura#steadfast}'s shape, so no path — a respec, an archetype
	 * wiped by Amnesia II — can leave the multiplier standing on a player who
	 * no longer owns the node.
	 */
	private static void ironclad(final ServerPlayer player) {
		boolean should = rank(player, Family.IRONCLAD) > 0;

		multiplier(player, Attributes.ARMOR, IRONCLAD_ARMOUR_ID, should);
		multiplier(player, Attributes.ARMOR_TOUGHNESS, IRONCLAD_TOUGHNESS_ID, should);
	}

	//? if >=1.21 {
	private static void multiplier(final ServerPlayer player,
			final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			final Identifier id, final boolean should) {
	//?} else {
	/*private static void multiplier(final ServerPlayer player,
			final net.minecraft.world.entity.ai.attributes.Attribute attribute,
			final Identifier id, final boolean should) {
	*///?}
		AttributeInstance instance = player.getAttribute(attribute);

		if (instance == null) {
			return;
		}

		//? if >=1.21 {
		if (should && !instance.hasModifier(id)) {
			instance.addTransientModifier(new AttributeModifier(id, Tuning.IRONCLAD_ARMOUR_BONUS,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (!should && instance.hasModifier(id)) {
			instance.removeModifier(id);
		}
		//?} else {
		/*if (should && !LegacyAttributes.has(instance, id)) {
			instance.addTransientModifier(LegacyAttributes.modifier(id, Tuning.IRONCLAD_ARMOUR_BONUS,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (!should && LegacyAttributes.has(instance, id)) {
			LegacyAttributes.remove(instance, id);
		}
		*///?}
	}

	/**
	 * Well Fed's first half: what a bite of food costs in time, as a factor of
	 * vanilla's. Read on both sides — {@code PURCHASED} is synced to its owner,
	 * so the client that predicts the eat and the server that finishes it agree
	 * on the number. Onlookers' clients see rank 0 and time the chewing
	 * animation the vanilla way; that is cosmetic and stays cosmetic.
	 */
	public static float eatSpeedFactor(final Player player) {
		int rank = rank(player, Family.WELL_FED);
		return rank <= 0 ? 1.0F
				: Math.max(0.0F, 1.0F - Tuning.WELL_FED_EAT_SPEED_PER_RANK * rank);
	}

	/**
	 * Well Fed's second half: how far the hunger bar can be filled. Vanilla's
	 * 20 at rank 0, then 30 and 40 — the sketch's "50/100% more", unchanged by
	 * the eating-speed retune beside it.
	 *
	 * <p>Only the ceiling moves. The regeneration thresholds in
	 * {@code FoodData.tick} (saturated regen at 20, slow regen at 18) are left
	 * exactly where vanilla put them, so banked hunger buys time above the
	 * line rather than a different line — the author's "the vanilla
	 * regeneration rules still apply".
	 */
	public static int hungerCeiling(final Player player) {
		int rank = rank(player, Family.WELL_FED);
		return rank <= 0 ? FoodConstants.MAX_FOOD
				: FoodConstants.MAX_FOOD
						+ Math.round(FoodConstants.MAX_FOOD * Tuning.WELL_FED_BANK_PER_RANK * rank);
	}

	/**
	 * Well Fed's banked hunger is not a modifier that can be revoked and not a
	 * key that can be removed: it is food points sitting in vanilla's own
	 * {@code FoodData}, above the twenty the bar draws, and
	 * {@link com.archetypes.client.BankedHungerHud} paints the halo straight
	 * off {@code foodLevel - 20}. So nothing about dropping the node takes them
	 * back — the player kept the extra cap and the halo until they ate the
	 * points down (user report, 2026-08-01).
	 *
	 * <p>Called from {@code ModState.forgetNodes} AFTER {@code PURCHASED} is
	 * gone, so {@link #hungerCeiling} already answers the vanilla 20 and this
	 * needs no special case for "was reset" versus "sold a rank": it trims to
	 * whatever ceiling the player is entitled to right now. Vanilla's
	 * {@code ServerPlayer.doTick} sees {@code foodLevel != lastSentFood} on the
	 * next tick and sends the {@code ClientboundSetHealthPacket} itself, so the
	 * halo goes with it and there is no packet to write here.
	 */
	public static void trimBankedHunger(final ServerPlayer player) {
		int ceiling = hungerCeiling(player);

		if (player.getFoodData().getFoodLevel() <= ceiling) {
			return;
		}

		player.getFoodData().setFoodLevel(ceiling);
		// Saturation never legitimately rides above the food level, but a bank
		// that was full when the node went is the one place it could be left
		// there — and the fast-regen rules read it.
		player.getFoodData().setSaturation(
				Math.min(player.getFoodData().getSaturationLevel(), (float) ceiling));
	}

	/**
	 * Hearty Meal: what a swallowed item leaves behind. Called from the one
	 * place vanilla finishes any consumable, after that item's own effects have
	 * run — milk's clear-everything included, which is why the Regeneration it
	 * grants survives the bucket that granted it.
	 */
	public static void heartyMeal(final ServerPlayer player, final ItemStack stack) {
		if (rank(player, Family.HEARTY_MEAL) <= 0) {
			return;
		}

		MobEffectInstance buff;

		if (stack.is(Items.MILK_BUCKET)) {
			buff = new MobEffectInstance(MobEffects.REGENERATION, Tuning.HEARTY_MEAL_TICKS,
					Tuning.HEARTY_MEAL_MILK_AMPLIFIER);
		} else if (stack.is(MEAT)) {
			buff = new MobEffectInstance(MobEffects.STRENGTH, Tuning.HEARTY_MEAL_TICKS,
					Tuning.HEARTY_MEAL_MEAT_AMPLIFIER);
		} else if (stack.is(FRUIT)) {
			buff = new MobEffectInstance(MobEffects.SPEED, Tuning.HEARTY_MEAL_TICKS,
					Tuning.HEARTY_MEAL_FRUIT_AMPLIFIER);
		} else {
			return;
		}

		player.addEffect(buff);
		ProcIndicators.send(player, SubTree.COLOSSUS_PROTECTOR, Family.HEARTY_MEAL);

		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.35F, 1.8F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
				player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.35, 0.5, 0.35, 0.0);
	}

	/**
	 * Instinctive Guard: a shield carried, not raised, still eats a share of
	 * every hit — and pays for it in full.
	 *
	 * <p>Everything about <em>what</em> a shield stops is the item's own
	 * {@link BlocksAttacks} component, asked the same questions vanilla's
	 * {@code applyItemBlocking} asks it: the damage type must not be in the
	 * shield's {@code bypassed_by} set (which is how fall, drowning, starvation
	 * and magic stay unblockable), and a piercing arrow still goes through. The
	 * one question answered differently is the facing one — the angle handed to
	 * {@code resolveBlockedDamage} is 0, i.e. always inside the arc, because
	 * the node says "all attacks" and a guard you are not aiming has no front.
	 * That is the same override Omni Block makes for a raised shield.
	 *
	 * <p>Below 1.21.11 the component does not exist and the same three
	 * questions are asked of vanilla's own {@code isDamageSourceBlocked}
	 * clauses instead — {@code #bypasses_shield}, the piercing arrow, and a
	 * null source position. The numbers come out identical; see the note under
	 * this javadoc for why that is a measurement and not a hope.
	 *
	 * <p>The player keeps only {@code rank x 25%} of what the block was worth,
	 * but the shield is charged the whole of it: the author's "shield still
	 * takes full damage for those blocks". A shield on cooldown — one an axe or
	 * a Colossus Crusher has knocked aside — guards nothing.
	 *
	 * <p>What it deliberately does NOT do is call {@code blockUsingItem}: the
	 * base Protector's block-gated nodes (Iron Spikes, Braced) still want a
	 * shield actually held up, so a passive block procs neither.
	 *
	 * @return the damage left after the guard
	 */
	// ---- THE LEGACY ARM IS A REIMPLEMENTATION, NOT AN APPROXIMATION (R-A5, closed) ----
	//
	// What forks is the middle of this method and nothing else: the head, the hand search's
	// shape, the tail's cue and the returned arithmetic are one implementation on all seven
	// nodes. The two forked steps, both measured rather than read off a javadoc:
	//
	//  1. WHAT THE SHIELD WOULD STOP. Above the boundary that is the component's own
	//     `resolveBlockedDamage(source, amount, 0.0)`. Below it, the same question is
	//     vanilla's `LivingEntity.isDamageSourceBlocked` clause list, minus exactly one
	//     clause. Six clauses there, disassembled on all four legacy targets: the direct
	//     entity, the piercing-arrow test, `#bypasses_shield`, `isBlocking()`, the pierce
	//     re-test, and `getSourcePosition() == null` — then the facing test last.
	//       * KEPT: piercing arrow (26.x has the identical explicit test), the
	//         `#bypasses_shield` tag (this IS the shield's `bypassedBy()` below the boundary)
	//         and `getSourcePosition() == null`. That last one is load-bearing: 26.2's
	//         `bypassed_by` gained cactus/campfire/dry_out/hot_floor/in_fire/lava/
	//         sweet_berry_bush/sulfur_cube_hot, which the legacy tag does not list, and every
	//         one of them has a null source position. Drop the clause and a legacy
	//         Instinctive Guard soaks lava and fire where 26.x does not.
	//       * INVERTED INTO THE HEAD: `isBlocking()`, which is a gate and not a requirement
	//         — a RAISED shield is vanilla's business, this node is about a carried one. It
	//         is `blocking(player)`, already forked, reused rather than copied.
	//       * DROPPED: the facing test. 26.x passes angle `0.0` unconditionally, so there is
	//         nothing to reproduce.
	//     With base 0 / factor 1 / 90° and the literal 0.0 angle, 26.x's resolve is
	//     `blockable == amount` always — so the legacy arm's `blockable = amount` is the same
	//     number, and this node is a STRICTLY PURE MULTIPLICATION of the funnel on all seven.
	//  2. WHAT THE BLOCK COSTS THE SHIELD. `BlocksAttacks.hurtBlockingItem` above; below,
	//     `Player.hurtCurrentlyUsedShield`'s own rule written out (see `hurtGuard`).
	public static float instinctiveGuard(final ServerPlayer player, final ServerLevel level,
			final DamageSource source, final float amount) {
		int rank = rank(player, Family.INSTINCTIVE_GUARD);

		if (rank <= 0 || amount <= 0.0F || blocking(player)) {
			return amount;
		}

		InteractionHand hand = guardHand(player);

		if (hand == null) {
			return amount;
		}

		ItemStack shield = player.getItemInHand(hand);
		//? if >=1.21.11 {
		BlocksAttacks blocksAttacks = shield.get(DataComponents.BLOCKS_ATTACKS);

		// `BlocksAttacks.bypassedBy()` carries a resolved `HolderSet<DamageType>` on 26.x and
		// the unresolved `TagKey<DamageType>` below it (measured on all three jars). Only the
		// membership TEST forks; the `orElse(false)` default and everything around it is
		// shared, and `DamageSource.is(TagKey)` exists on every node so the legacy arm is a
		// direct read rather than a resolution the caller has to do.
		if (blocksAttacks == null
				//? if >=26.1 {
				|| blocksAttacks.bypassedBy().map(types -> types.contains(source.typeHolder()))
						.orElse(false)
				//?} else {
				/*|| blocksAttacks.bypassedBy().map(source::is).orElse(false)
				*///?}
				|| source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) {
			return amount;
		}

		float blockable = blocksAttacks.resolveBlockedDamage(source, amount, 0.0);
		//?} else {
		/*if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)
				|| source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0
				|| source.getSourcePosition() == null) {
			return amount;
		}

		float blockable = amount;
		*///?}

		if (blockable <= 0.0F) {
			return amount;
		}

		//? if >=1.21.11 {
		blocksAttacks.hurtBlockingItem(level, shield, player, hand, blockable);
		//?} else {
		/*hurtGuard(player, level, shield, hand, blockable);
		*///?}
		ProcIndicators.send(player, SubTree.COLOSSUS_PROTECTOR, Family.INSTINCTIVE_GUARD);

		// Audibly a lesser block than a raised one: the same clang, quieter and
		// higher, so the guard is legible without pretending to be a shield up.
		//? if >=1.21.11 {
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.5F, 1.3F);
		//?} else {
		/*level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.5F, 1.3F);
		*///?}
		level.sendParticles(ParticleTypes.CRIT,
				player.getX(), player.getY() + 1.2, player.getZ(), 5, 0.25, 0.25, 0.25, 0.05);

		return amount - blockable * Tuning.INSTINCTIVE_GUARD_PER_RANK * rank;
	}

	/** The hand a carried shield is in, offhand first because that is where one
	 * lives; null if neither hand holds something that blocks or the shield is
	 * on its disable cooldown. */
	// Only the "is this a shield" test and the cooldown call's ARITY fork. The legacy test is
	// vanilla's own: `LivingEntity.isBlocking` is `getUseAnimation() == UseAnim.BLOCK` on both
	// legacy jars, and `ShieldItem.getUseAnimation` is a bare `getstatic; areturn` of it — so
	// modded shields are covered and swords are not, which is the same set the component
	// answers for above the boundary.
	private static @Nullable InteractionHand guardHand(final Player player) {
		for (InteractionHand hand : new InteractionHand[] {
				InteractionHand.OFF_HAND, InteractionHand.MAIN_HAND }) {
			ItemStack stack = player.getItemInHand(hand);

			//? if >=1.21.11 {
			if (stack.has(DataComponents.BLOCKS_ATTACKS)
					&& !player.getCooldowns().isOnCooldown(stack)) {
			//?} else {
			/*if (stack.getUseAnimation() == net.minecraft.world.item.UseAnim.BLOCK
					&& !player.getCooldowns().isOnCooldown(stack.getItem())) {
			*///?}
				return hand;
			}
		}

		return null;
	}

	// ---- `BlocksAttacks.hurtBlockingItem`, below the boundary, written out ----
	//
	// `Player.hurtCurrentlyUsedShield(float)` is the legacy equivalent and it is NOT called
	// directly, for one reason: it is `protected` and it reads `this.useItem` — the RAISED
	// stack — where this node has a CARRIED one in a known hand. So its rule is reproduced
	// against the stack we actually hold. The rule itself is copied, not invented
	// (`pl1211.txt:2232`, `pl1201.txt:2128`):
	//
	//     if (amount >= 3.0F) { int i = 1 + Mth.floor(amount); hurtAndBreak(i, ...); }
	//
	// which is `ItemDamageFunction(threshold 3, base 1, factor 1)` — the vanilla shield's own
	// component above the boundary — evaluated as `floor(1 + 1*amount)`. Identical for every
	// non-negative amount. The stat award is `hurtBlockingItem`'s first act on 26.x and it is
	// unconditional there, so it is unconditional here.
	//
	// The arithmetic lives HERE, once, outside every `//?`; only the `hurtAndBreak` overload
	// forks, and that fork is copied verbatim from `NightForm`'s helmet burn, which is the
	// same two overloads at the same boundary.
	//? if <1.21.11 {
	/*private static void hurtGuard(final ServerPlayer player, final ServerLevel level,
			final ItemStack shield, final InteractionHand hand, final float blockable) {
		player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(shield.getItem()));

		if (blockable < 3.0F) {
			return;
		}

		// NOT `InteractionHand.asEquipmentSlot()` — that is the 26.x form and does not exist
		// on either legacy jar.
		net.minecraft.world.entity.EquipmentSlot slot = hand == InteractionHand.MAIN_HAND
				? net.minecraft.world.entity.EquipmentSlot.MAINHAND
				: net.minecraft.world.entity.EquipmentSlot.OFFHAND;

		breakGuard(player, level, shield, slot, 1 + net.minecraft.util.Mth.floor(blockable));
	}
	*///?}
	//? if >=1.21 && <1.21.11 {
	/*private static void breakGuard(final ServerPlayer player, final ServerLevel level,
			final ItemStack shield, final net.minecraft.world.entity.EquipmentSlot slot,
			final int damage) {
		shield.hurtAndBreak(damage, level, player, broken -> player.onEquippedItemBroken(broken, slot));
	}
	*///?}
	//? if <1.21 {
	/*private static void breakGuard(final ServerPlayer player, final ServerLevel level,
			final ItemStack shield, final net.minecraft.world.entity.EquipmentSlot slot,
			final int damage) {
		shield.hurtAndBreak(damage, player, broken -> broken.broadcastBreakEvent(slot));
	}
	*///?}

	/** Whether this player is blocking at all, by vanilla's single definition of
	 * it — {@code getItemBlockingWith} is what {@code isBlocking}, the block
	 * arc and every blocking pose read. */
	public static boolean blocking(final Player player) {
		// NOT excised: `isBlocking()` answers the identical question on every version, and
		// on 1.21.11 it is literally implemented as `getItemBlockingWith() != null`. Free
		// Hand therefore works unchanged on the legacy node family.
		//? if >=1.21.11 {
		return player.getItemBlockingWith() != null;
		//?} else {
		/*return player.isBlocking();
		*///?}
	}

	/**
	 * Free Hand: a raised shield no longer costs the sword arm.
	 *
	 * <p>There is nothing to lift on the server, because the server never
	 * forbade it: {@code Player.attack} runs happily while an item is in use,
	 * and nothing in it ends the use. The whole prohibition is the {@code
	 * if (isUsingItem())} arm of {@code Minecraft.handleKeybinds}, which drains
	 * the attack-click queue into an empty loop and throws it away. So the node
	 * is an input permission and nothing else, answered against the client's
	 * synced copy of {@code PURCHASED} — the same mirror that paints a node
	 * buyable — and consumed by {@code MinecraftMixin}.
	 *
	 * <p>It asks nothing but "do I own the node and is my guard up", and that is
	 * now the whole of it. Every weapon, and no weapon gate — the node's promise
	 * is "swing your weapon", not "swing some weapons", and there is nothing in
	 * the input path that could tell them apart cheaply anyway. The one
	 * exclusion this ever carried was for a braced spear, and it existed only
	 * because Spearwall could make {@link #blocking} true without a shield being
	 * raised. Spearwall is gone and so is every spear rule in this mod, so the
	 * only way to be blocking again is to be holding a shield up.
	 */
	public static boolean canAttackWhileBlocking(final Player player) {
		return rank(player, Family.FREE_HAND) > 0 && blocking(player);
	}

	/**
	 * Immovable Object: a guard that normal means cannot break.
	 *
	 * <p>Asked from the head of {@link BlocksAttacks#disable}, which is the one
	 * choke point every shield-disable in the game passes through: vanilla
	 * reaches it from {@code Player.blockUsingItem}, fed by whatever the
	 * attacker's {@code getSecondsToDisableBlocking} returns — an axe's, the
	 * Warden's — and this mod's Unstoppable Force calls it by hand for the same
	 * reason. Refusing there is what lets the node promise "by normal means"
	 * without naming a single attacker: it is not a list of exceptions to keep
	 * up to date, and anything that learns to break a guard later is covered
	 * the day it lands.
	 *
	 * <p>Our own Colossus Crusher is deliberately NOT exempted. Two epic
	 * capstones that both claim to be absolute have to meet somewhere, and the
	 * meeting is authored as its own event on the Crusher's hook rather than as
	 * a quiet win for whoever we special-cased here.
	 *
	 * <p>Cued, and rate-limited, for the reason any node like it needs one: a
	 * node whose whole effect is that nothing happened is invisible without it.
	 *
	 * @return true if the disable must not happen
	 */
	public static boolean immovableObject(final ServerPlayer player, final ServerLevel level) {
		// THREE callers, and the point of the design is that they all ask THIS ONE FUNCTION
		// rather than each keeping their own copy of the rule:
		//   * BlocksAttacksMixin, at the head of `BlocksAttacks.disable`   (1.21.11 and up)
		//   * PlayerMixin, at the head of `Player.disableShield`           (below 1.21.11 —
		//     the single chokepoint, measured; see the ⚠ correction in this class's header)
		//   * LivingEntityMixin's legacy `archetypes$breakGuard`, so this mod's own
		//     Unstoppable Force is refused exactly as vanilla's axe is.
		// One rule, one cue clock, one place to change it.
		if (rank(player, Family.IMMOVABLE_OBJECT) <= 0) {
			return false;
		}

		final Entity target = player;
		long now = level.getGameTime();
		Long last = ArchetypeStore.INSTANCE.get(target, ModState.IMMOVABLE_OBJECT_CUE_AT);

		if (last == null || now - last >= Tuning.IMMOVABLE_CUE_PERIOD_TICKS) {
			ArchetypeStore.INSTANCE.set(target, ModState.IMMOVABLE_OBJECT_CUE_AT, now);

			// The shield's own note, dropped an octave: the guard held, and it
			// held harder than a block normally does.
			//? if >=1.21.11 {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.9F, 0.5F);
			//?} else {
			/*level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.9F, 0.5F);
			*///?}
			level.sendParticles(ParticleTypes.CRIT,
					player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
			ProcIndicators.send(player, SubTree.COLOSSUS_PROTECTOR, Family.IMMOVABLE_OBJECT);
		}

		return true;
	}
}
