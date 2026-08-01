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
// ─── R-A5: THE SHIELD-MODIFIER CLUSTER IS EXCISED BELOW 1.21.11 ──────────────────────────
// The decision in force (design §4.2 / R-A5), applied here and in LivingEntityMixin,
// BlocksAttacksMixin and DamageTraceMixin. What is missing is not a name but a mechanism:
// the `BlocksAttacks` COMPONENT, `LivingEntity.applyItemBlocking` and
// `BlocksAttacks.disable` all arrive together at 1.21.11. Below it, blocking is resolved
// inline inside `LivingEntity.hurt` (`isDamageSourceBlocked` + `hurtCurrentlyUsedShield`)
// and a shield is knocked aside through `Player.disableShield()` plus `ItemCooldowns` —
// two chokepoints, not one, and neither of them is a place where "how much would this
// shield have stopped" is a question that can be asked at all.
//
// So the two nodes whose whole effect is a number taken off a BLOCKED hit — Instinctive
// Guard and Omni Block — no-op on this node family rather than being approximated through
// a different chokepoint. Approximating a defensive multiplier somewhere vanilla resolves
// blocking differently is exactly the silent-divergence class R-20 exists to catch.
//
// THE NODES STAY PURCHASABLE AND THE TREE STAYS VALID: both sit mid-tree with children
// beyond them, and a hole in a constellation would strand the rest of the epic branch. The
// lang file carries a per-node-family note saying the effect is inactive on this version.
// Everything else in this class — Ironclad's armour multiplier, Hearty Meal, Well Fed,
// Free Hand, and the two `blocking(...)` reads — is unaffected and ports cleanly.
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
	 * That is the same override Bulwark makes for a raised shield.
	 *
	 * <p>The player keeps only {@code rank x 25%} of what the block was worth,
	 * but {@code hurtBlockingItem} is called with the whole of it: the author's
	 * "shield still takes full damage for those blocks". A shield on cooldown —
	 * one an axe or a Colossus Crusher has knocked aside — guards nothing.
	 *
	 * <p>What it deliberately does NOT do is call {@code blockUsingItem}: the
	 * base Protector's block-gated nodes (Iron Spikes, Braced) still want a
	 * shield actually held up, so a passive block procs neither.
	 *
	 * @return the damage left after the guard
	 */
	public static float instinctiveGuard(final ServerPlayer player, final ServerLevel level,
			final DamageSource source, final float amount) {
		//? if <1.21.11 {
		/*// R-A5, see the header: no BlocksAttacks component, so there is no shield to ask
		// what it would have stopped. The node stays purchasable and does nothing.
		return amount;
		*///?}
		//? if >=1.21.11 {
		int rank = rank(player, Family.INSTINCTIVE_GUARD);

		if (rank <= 0 || amount <= 0.0F || player.getItemBlockingWith() != null) {
			return amount;
		}

		InteractionHand hand = guardHand(player);

		if (hand == null) {
			return amount;
		}

		ItemStack shield = player.getItemInHand(hand);
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

		if (blockable <= 0.0F) {
			return amount;
		}

		blocksAttacks.hurtBlockingItem(level, shield, player, hand, blockable);
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
		//?}
	}

	/** The hand a carried shield is in, offhand first because that is where one
	 * lives; null if neither hand holds something that blocks or the shield is
	 * on its disable cooldown. */
	// Instinctive Guard is its only caller, so it goes with it (R-A5, see the header).
	//? if >=1.21.11 {
	private static @Nullable InteractionHand guardHand(final Player player) {
		for (InteractionHand hand : new InteractionHand[] {
				InteractionHand.OFF_HAND, InteractionHand.MAIN_HAND }) {
			ItemStack stack = player.getItemInHand(hand);

			if (stack.has(DataComponents.BLOCKS_ATTACKS)
					&& !player.getCooldowns().isOnCooldown(stack)) {
				return hand;
			}
		}

		return null;
	}
	//?}

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
		// Its only caller is BlocksAttacksMixin, which does not exist below 1.21.11 (R-A5,
		// see the header) — the disable path there is `Player.disableShield()` plus a raw
		// `ItemCooldowns` write, two chokepoints rather than the one this node's promise
		// ("nothing normal breaks it") depends on.
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
