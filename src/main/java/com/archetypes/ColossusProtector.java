package com.archetypes;

import com.archetypes.ColossusProtectorNodes.Family;

import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.BlocksAttacks;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.jspecify.annotations.Nullable;

/**
 * Every node of the epic Colossus-Protector tree. The sketch dropped the
 * planted-Aegis active the design doc drafted, so this tree has no ability key
 * and no timed window: it is six passives that are simply always on, and the
 * whole class is stateless — nothing here allocates an attachment, and every
 * answer is recomputed from node ownership and what is in the player's hands.
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
		ServerTickEvents.END_SERVER_TICK.register(server -> {
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

	private static void multiplier(final ServerPlayer player,
			final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
			final Identifier id, final boolean should) {
		AttributeInstance instance = player.getAttribute(attribute);

		if (instance == null) {
			return;
		}

		if (should && !instance.hasModifier(id)) {
			instance.addTransientModifier(new AttributeModifier(id, Tuning.IRONCLAD_ARMOUR_BONUS,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (!should && instance.hasModifier(id)) {
			instance.removeModifier(id);
		}
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
	 * 20 at rank 0, then 30 and 40.
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

		if (blocksAttacks == null
				|| blocksAttacks.bypassedBy().map(types -> types.contains(source.typeHolder()))
						.orElse(false)
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
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.5F, 1.3F);
		level.sendParticles(ParticleTypes.CRIT,
				player.getX(), player.getY() + 1.2, player.getZ(), 5, 0.25, 0.25, 0.25, 0.05);

		return amount - blockable * Tuning.INSTINCTIVE_GUARD_PER_RANK * rank;
	}

	/** The hand a carried shield is in, offhand first because that is where one
	 * lives; null if neither hand holds something that blocks or the shield is
	 * on its disable cooldown. */
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

	/**
	 * Free Hand: the shield a player is no longer holding up, because both
	 * hands went to do something else.
	 *
	 * <p>This is the half of the node the server owns. Vanilla has exactly one
	 * item-in-use slot per entity, so eating and blocking cannot both be "the
	 * item I am using" — starting the meal is what drops the guard. Rather than
	 * fake a second use, {@code getItemBlockingWith} is answered with the
	 * shield in the other hand for as long as the meal runs, which puts the
	 * whole of vanilla's blocking back on: the arc, the durability, the block
	 * sound, Iron Spikes and Braced, and the shield-up pose every client draws.
	 *
	 * <p>The shield's own {@code block_delay_seconds} is still paid, measured
	 * on the action that displaced it — a quarter second of chewing before the
	 * guard is up again, exactly as if it had been raised.
	 *
	 * @return the shield that is still blocking, or null if none is
	 */
	public static @Nullable ItemStack freeHandBlock(final Player player) {
		InteractionHand hand = freeHandBlockHand(player);
		return hand == null ? null : player.getItemInHand(hand);
	}

	/**
	 * Which hand {@link #freeHandBlock}'s shield is in — the hand that is NOT
	 * the one in use.
	 *
	 * <p>Two places downstream need it and both get it wrong without it,
	 * because vanilla assumes the blocking item and the item in use are the
	 * same one: {@code applyItemBlocking} charges the durability to
	 * {@code getUsedItemHand}'s equipment slot, which would report a broken
	 * shield against the hand holding the sandwich, and {@code hurtServer}
	 * reads the block sound off {@code getUseItem} rather than off what
	 * actually blocked.
	 */
	public static @Nullable InteractionHand freeHandBlockHand(final Player player) {
		// Cheapest test first, and deliberately: this runs inside
		// isBlocking(), which every render frame asks of every player, while
		// the node lookup allocates.
		if (!player.isUsingItem()) {
			return null;
		}

		InteractionHand busy = player.getUsedItemHand();

		// A player who is simply blocking is vanilla's business, not ours.
		if (player.getItemInHand(busy).has(DataComponents.BLOCKS_ATTACKS)
				|| rank(player, Family.FREE_HAND) <= 0) {
			return null;
		}

		InteractionHand free = busy == InteractionHand.MAIN_HAND
				? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack other = player.getItemInHand(free);
		BlocksAttacks blocksAttacks = other.get(DataComponents.BLOCKS_ATTACKS);

		if (blocksAttacks == null || player.getCooldowns().isOnCooldown(other)
				|| player.getTicksUsingItem() < blocksAttacks.blockDelayTicks()) {
			return null;
		}

		return free;
	}

	/**
	 * Whether this player's hands stay free while they block — the client-side
	 * half of Free Hand and of Immovable Object.
	 *
	 * <p>Blocking locks nothing out on the server: {@code Player.attack},
	 * {@code handleUseItemOn} and {@code handleUseItem} all run happily while
	 * an item is in use, and nothing in them stops the use. The lockout is
	 * purely {@code Minecraft.handleKeybinds}, which swallows every attack and
	 * use click while {@code isUsingItem()}. So these two nodes are input
	 * permissions, checked against the client's synced copy of {@code
	 * PURCHASED} — the same mirror that paints a node buyable.
	 */
	public static boolean blocking(final Player player) {
		return player.getItemBlockingWith() != null;
	}

	public static boolean canUseWhileBlocking(final Player player) {
		return rank(player, Family.FREE_HAND) > 0 && blocking(player);
	}

	public static boolean canAttackWhileBlocking(final Player player) {
		return rank(player, Family.IMMOVABLE_OBJECT) > 0 && blocking(player);
	}
}
