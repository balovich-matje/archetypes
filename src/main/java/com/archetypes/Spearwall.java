package com.archetypes;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.UseEffects;
import org.jspecify.annotations.Nullable;

/**
 * Spearwall: the shield stays up while the spear is braced.
 *
 * <h2>The thing vanilla will not let you do</h2>
 * A braced spear and a raised shield are the same action to 26.2 — both are
 * "using an item", and {@code LivingEntity} has exactly one slot for that.
 * {@code startUsingItem} opens with
 *
 * <pre>if (stack.isEmpty() || this.isUsingItem()) return;</pre>
 *
 * so whichever hand gets there first wins and the second request is dropped on
 * the floor. Worse, the winner is decided for you: the use key walks
 * {@code InteractionHand.values()}, main hand first, so a player holding spear
 * and shield the normal way around braces the spear and never raises the
 * shield at all. The two states are not merely hard to hold together — vanilla
 * picks one for you and it is always the same one.
 *
 * <h2>What this node actually changes</h2>
 * Nothing about the spear. The spear braces natively: it is the real
 * {@code useItem}, vanilla ticks its {@code KINETIC_WEAPON} component through
 * {@code ItemStack.onUseTick}, and every speed condition, contact cooldown and
 * stab sound is vanilla's own. Touching that was never necessary and would
 * have meant reimplementing it.
 *
 * <p>The shield is the half that is synthesised, through the single function
 * every part of blocking is asked: {@code LivingEntity.getItemBlockingWith}.
 * {@code isBlocking}, the block pose, the block arc, {@code applyItemBlocking}'s
 * damage reduction, {@code blockUsingItem}'s disable path — all of them read
 * it and nothing reads {@code useItem} directly. So answering "the off-hand
 * shield" there is the whole node, and everything downstream comes with it for
 * free, including this tree's own block-gated nodes: Iron Spikes still
 * reflects and Braced still refunds the bash, because they hang off
 * {@code blockedByItem}, which only ever fires because a block happened.
 *
 * <p>There are exactly two seams, and both are places where vanilla reaches past
 * {@code getItemBlockingWith} for the USE ITEM instead. Both are closed with a
 * {@code @ModifyExpressionValue} in {@code LivingEntityMixin}, and neither
 * touches the guard itself:
 *
 * <ol>
 * <li><b>Durability.</b> {@code applyItemBlocking} charges the block to
 * {@code getUsedItemHand()}, which is the spear's hand, not the shield's — the
 * damaged stack is right (it is passed the stack we returned) but the slot the
 * break effect plays in would be wrong, so the shield would shatter in the wrong
 * hand.</li>
 * <li><b>Feedback.</b> {@code hurtServer} stashes {@code getUseItem()} in a
 * local and later reads {@code BLOCKS_ATTACKS} off THAT stack to choose between
 * the block sound and a plain hurt broadcast. The spear has no such component,
 * so a real block was silent and went out to every client as an ordinary hit —
 * which is what a player reports as "the attacks pass through". The fix hands
 * that one read the shield.</li>
 * </ol>
 *
 * <h2>Free Hand does not extend to spears</h2>
 * Free Hand (Colossus Protector) is an input permission: it spends the attack
 * click at the head of {@code Minecraft.handleKeybinds} before the
 * {@code isUsingItem()} arm can swallow it, and it is gated on "am I
 * blocking?". Under Spearwall the answer is yes — that is the point — so
 * without a word from us Free Hand would happily let a braced spear swing, and
 * the player would get shield, brace and full melee at once.
 *
 * <p>It does not, and the refusal lives in
 * {@code ColossusProtector.canAttackWhileBlocking}, not here: it is one gate on
 * the one function, so there is no second path to keep in step. A braced spear
 * is a planted weapon. It hurts what runs onto it, at vanilla's speeds, and it
 * does not swing.
 */
public final class Spearwall {
	private Spearwall() {
	}

	/**
	 * The shield this entity is guarding with purely because of Spearwall, or
	 * null if the node is not carrying them right now.
	 *
	 * <p>Ordered cheapest-first on purpose. This is asked from
	 * {@code getItemBlockingWith}, which runs on every damage event and on
	 * every render frame that poses a player, and {@code NodePurchases.owned}
	 * builds a set — so the node lookup sits last, behind gates that reject
	 * almost every caller on an instance check or a tag test.
	 */
	public static @Nullable ItemStack guardingShield(final LivingEntity entity) {
		if (!(entity instanceof Player player) || !player.isUsingItem()) {
			return null;
		}

		ItemStack braced = player.getUseItem();

		// A spear, and actually a braced one: the component is what does the
		// bracing, and the tag is what makes this node about spears rather
		// than about anything that might one day carry the component.
		if (!braced.is(ItemTags.SPEARS) || !braced.has(DataComponents.KINETIC_WEAPON)) {
			return null;
		}

		ItemStack shield = player.getItemInHand(
				player.getUsedItemHand() == InteractionHand.MAIN_HAND
						? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);

		// A shield knocked aside by an axe is knocked aside here too: the
		// cooldown is where vanilla parks a disabled shield, and a node that
		// ignored it would quietly repeal Unstoppable Force.
		if (blocks == null || player.getCooldowns().isOnCooldown(shield)) {
			return null;
		}

		// The guard costs a beat more than a shield simply held up would:
		// the shield's own blockDelayTicks plus ours. Without the surcharge
		// the node is a pure addition to a stance the player was already in.
		if (player.getTicksUsingItem()
				< blocks.blockDelayTicks() + Tuning.SPEARWALL_BRACE_DELAY_TICKS) {
			return null;
		}

		int rank = ProtectorNodes.rank(SubTree.PROTECTOR,
				NodePurchases.owned(player, SubTree.PROTECTOR),
				ProtectorNodes.Family.SPEARWALL);

		return rank > 0 ? shield : null;
	}

	/** Whether this player is bracing a spear at all — the test Free Hand asks
	 * so that its permission stops short of a planted weapon. Deliberately not
	 * gated on owning Spearwall: a braced spear does not swing for anyone. */
	public static boolean bracingSpear(final Player player) {
		return player.isUsingItem()
				&& player.getUseItem().is(ItemTags.SPEARS)
				&& player.getUseItem().has(DataComponents.KINETIC_WEAPON);
	}

	/** The hand the Spearwall guard is really in, for the one vanilla call that
	 * would otherwise charge the block to the spear's slot. */
	public static InteractionHand guardHand(final LivingEntity entity) {
		return entity instanceof Player player
				&& player.getUsedItemHand() == InteractionHand.MAIN_HAND
						? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}

	/**
	 * The rendering-side answer to "is this entity's guard up", read off the
	 * published {@link ModAttachments#SPEARWALL_GUARD} flag rather than
	 * recomputed.
	 *
	 * <p>{@link #guardingShield} is the truth and every gameplay path asks it,
	 * but it cannot be asked about somebody ELSE on a client:
	 * {@code NodePurchases.owned} reads a target-only attachment, so an
	 * onlooker's copy of another player's build is empty and the answer comes
	 * back no. Every renderer therefore asks this instead, and pays a tick of
	 * latency for being right about everybody.
	 */
	public static boolean guardRaised(final LivingEntity entity) {
		return Boolean.TRUE.equals(((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) entity)
				.getAttached(ModAttachments.SPEARWALL_GUARD));
	}

	/** Whether this exact stack is the raised guard — the test both the shield's
	 * blocking MODEL and its arm pose are decided by. */
	public static boolean isRaisedGuard(final LivingEntity entity, final ItemStack stack) {
		return !stack.isEmpty() && guardRaised(entity)
				&& entity.getItemInHand(guardHand(entity)) == stack;
	}

	/**
	 * The movement multiplier a Spearwall guard should move at.
	 *
	 * <h2>Why the guard is not slow on its own</h2>
	 * Vanilla scales a using player's input by the USE ITEM's
	 * {@code UseEffects.speedMultiplier} ({@code LocalPlayer.modifyInput} →
	 * {@code itemUseSpeedMultiplier}). A raised shield has no {@code USE_EFFECTS}
	 * of its own, so it takes {@code UseEffects.DEFAULT} — {@code 0.2F}, and
	 * {@code canSprint = false}. A spear declares its own:
	 * {@code Item.Properties.spear} sets {@code new UseEffects(true, false, 1.0F)},
	 * because a braced spear is supposed to be carried at a run.
	 *
	 * <p>Under Spearwall the use item is the SPEAR, so a player got the shield's
	 * whole guard at the spear's full running speed — a shield that costs
	 * nothing to hold. This returns the guard's own multiplier instead, and
	 * {@code min} rather than a swap so a future item that braces slower than a
	 * shield blocks is not accidentally sped up.
	 */
	public static float guardSpeedMultiplier(final Player player, final float braced) {
		ItemStack shield = guardingShield(player);

		if (shield == null) {
			return braced;
		}

		return Math.min(braced, shield
				.getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT).speedMultiplier());
	}

	/** The other half of the same stance: a guard that can be sprinted with is
	 * not a guard. Vanilla gates {@code canStartSprinting} on the use item's
	 * {@code canSprint}, and the spear's is true. */
	public static boolean guardStopsSprint(final Player player) {
		ItemStack shield = guardingShield(player);

		return shield != null
				&& !shield.getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT).canSprint();
	}
}
