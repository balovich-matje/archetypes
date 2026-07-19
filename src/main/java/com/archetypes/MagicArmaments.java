package com.archetypes;

import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Magic Armaments, the Oracle Wizard's channel: a conjured weapon stands in for
 * the wand until the player toggles it off, the mana runs dry, or the weapon
 * leaves the hand. The real wand is parked in a persistent attachment
 * ({@link ModAttachments#ARMAMENTS_WAND}) and restored exactly where it sat, so
 * a relog, crash or death can never eat it — {@link #restoreDirty} cleans up a
 * channel that died mid-flight on JOIN, and the death hook restores the wand
 * before drops while the conjured weapon (which can never drop, be stored, or
 * survive death) simply vanishes.
 *
 * <p>The per-second upkeep follows the Flamethrower's shape (a stored last-tick
 * plus a lump charge), but ticks server-side rather than off a held key: this
 * channel is a toggle, not a hold.
 */
public final class MagicArmaments {
	private static final Identifier MOM_DAMAGE_ID = Archetypes.id("mind_over_matter");
	private static final Identifier ARMOR_CAP_ID = Archetypes.id("magic_armor_cap");

	private MagicArmaments() {
	}

	public static boolean isActive(final ServerPlayer player) {
		return ((AttachmentTarget) player).getAttached(ModAttachments.ARMAMENTS_WAND) != null;
	}

	/** The Ability-6 press: toggle the channel on or off. */
	public static void toggle(final ServerPlayer player) {
		if (isActive(player)) {
			end(player);
		} else {
			start(player);
		}
	}

	private static void start(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.ORACLE_WIZARD);

		if (OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned,
				OracleWizardNodes.Family.MAGIC_ARMAMENTS) <= 0
				|| !ModItems.isWand(player.getMainHandItem())
				|| isActive(player)) {
			return;
		}

		if (!Mana.spend(player, Tuning.MAGIC_ARMAMENTS_COST)) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		Inventory inventory = player.getInventory();
		int slot = inventory.getSelectedSlot();

		AttachmentTarget target = (AttachmentTarget) player;
		target.setAttached(ModAttachments.ARMAMENTS_WAND, player.getMainHandItem().copy());
		target.setAttached(ModAttachments.ARMAMENTS_SLOT, slot);
		target.setAttached(ModAttachments.ARMAMENTS_LAST_UPKEEP, level.getGameTime());

		boolean bow = OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned,
				OracleWizardNodes.Family.SPELLBOW) > 0;
		inventory.setItem(slot, new ItemStack(bow ? ModItems.MAGIC_BOW : ModItems.MAGIC_SWORD));

		// The cap must exist before the opening cost's absorption is banked, or
		// it clamps straight to zero (see Battle Trance).
		applyArmorCap(player, owned, true);
		grantArmor(player, owned, Tuning.MAGIC_ARMAMENTS_COST);

		player.swing(InteractionHand.MAIN_HAND, true);
		level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(),
				30, 0.4, 0.6, 0.4, 0.5);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.4F);
	}

	/** End the channel and hand the wand back to its slot. Safe to call when the
	 * channel is already off (it just clears any stray conjured items). */
	public static void end(final ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;
		ItemStack wand = target.getAttached(ModAttachments.ARMAMENTS_WAND);
		Integer slot = target.getAttached(ModAttachments.ARMAMENTS_SLOT);

		purgeSummoned(player);

		if (wand != null && !wand.isEmpty()) {
			Inventory inventory = player.getInventory();

			if (slot != null && slot >= 0 && slot < inventory.getContainerSize()
					&& inventory.getItem(slot).isEmpty()) {
				inventory.setItem(slot, wand);
			} else {
				inventory.placeItemBackInInventory(wand);
			}
		}

		target.removeAttached(ModAttachments.ARMAMENTS_WAND);
		target.removeAttached(ModAttachments.ARMAMENTS_SLOT);
		target.removeAttached(ModAttachments.ARMAMENTS_LAST_UPKEEP);

		// Strip the channel's grants immediately, don't wait for the next tick.
		Set<Integer> owned = NodePurchases.owned(player, SubTree.ORACLE_WIZARD);
		applyMindOverMatter(player, owned, false);
		applyArmorCap(player, owned, false);
		revokeFlight(player);

		if (player.isAlive()) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.2F);
		}
	}

	/** On JOIN: a channel that outlived its server (crash/logout) is torn down
	 * here — the wand goes back, the conjured weapon is purged. */
	public static void restoreDirty(final ServerPlayer player) {
		if (isActive(player)) {
			end(player);
		}
	}

	/** Per-tick, for every Seeker (called from {@link OracleWizardTicker}). */
	public static void tick(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.ORACLE_WIZARD);
		boolean active = isActive(player);

		// Attribute grants exist exactly while the channel does; the apply()
		// helpers add or remove them idempotently.
		applyMindOverMatter(player, owned, active);
		applyArmorCap(player, owned, active);

		if (!active) {
			manageFlight(player, false);

			// A conjured weapon with no channel behind it (a dupe or a dirty
			// state) must not linger in a hand.
			if (ModItems.isSummoned(player.getMainHandItem())) {
				purgeSummoned(player);
			}

			return;
		}

		// The channel dies the instant its weapon leaves the hand, the node is
		// respecced away, or the wielder does.
		if (!player.isAlive()
				|| OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned,
						OracleWizardNodes.Family.MAGIC_ARMAMENTS) <= 0
				|| !ModItems.isSummoned(player.getMainHandItem())) {
			end(player);
			return;
		}

		manageFlight(player, true);
		ward(player, owned);
		upkeep(player, owned);
	}

	private static void upkeep(final ServerPlayer player, final Set<Integer> owned) {
		AttachmentTarget target = (AttachmentTarget) player;
		long now = player.level().getGameTime();
		Long last = target.getAttached(ModAttachments.ARMAMENTS_LAST_UPKEEP);

		if (last == null || now - last < 20L) {
			return;
		}

		int mom = OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned,
				OracleWizardNodes.Family.MIND_OVER_MATTER);
		float cost = Tuning.MAGIC_ARMAMENTS_UPKEEP_PER_SECOND
				+ mom * Tuning.MIND_OVER_MATTER_UPKEEP_PER_RANK;

		if (!Mana.spend(player, cost)) {
			end(player);
			return;
		}

		target.setAttached(ModAttachments.ARMAMENTS_LAST_UPKEEP, now);
		grantArmor(player, owned, cost);
	}

	/** Warding: harmful effects are swept off periodically while the channel
	 * runs — indistinguishable from a true block at this cadence. */
	private static void ward(final ServerPlayer player, final Set<Integer> owned) {
		if (OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned, OracleWizardNodes.Family.WARD) <= 0
				|| player.level().getGameTime() % Tuning.MAGIC_ARMAMENTS_WARD_PERIOD_TICKS != 0) {
			return;
		}

		List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> harmful =
				new java.util.ArrayList<>();

		for (MobEffectInstance instance : player.getActiveEffects()) {
			if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				harmful.add(instance.getEffect());
			}
		}

		for (var effect : harmful) {
			player.removeEffect(effect);
		}
	}

	/** Blink: a conjured-sword swing with no hostile under the crosshair jumps
	 * the player forward, safe landings only. A swing that IS aimed at a hostile
	 * is an attack and blinks nowhere. */
	public static void blink(final ServerPlayer player) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.ORACLE_WIZARD);

		if (!isActive(player)
				|| !ModItems.isMagicSword(player.getMainHandItem())
				|| OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned,
						OracleWizardNodes.Family.BLINK) <= 0) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 reach = eye.add(player.getLookAngle().scale(Tuning.MAGIC_ARMAMENTS_BLINK_DISTANCE));
		EntityHitResult hostile = ProjectileUtil.getEntityHitResult(level, player, eye, reach,
				player.getBoundingBox().expandTowards(reach.subtract(eye)).inflate(1.0),
				e -> e instanceof LivingEntity living && living.isAlive() && living instanceof Enemy
						&& living != player, 0.3F);

		if (hostile != null) {
			return;
		}

		// Step forward along the flat look, keeping the farthest spot the body
		// still fits, so a blink never lands inside a wall.
		Vec3 look = player.getLookAngle();
		Vec3 forward = new Vec3(look.x, 0.0, look.z);

		if (forward.lengthSqr() < 1.0E-4) {
			return;
		}

		forward = forward.normalize();
		Vec3 start = player.position();
		Vec3 best = start;

		for (double step = 0.5; step <= Tuning.MAGIC_ARMAMENTS_BLINK_DISTANCE; step += 0.5) {
			Vec3 candidate = start.add(forward.scale(step));

			if (level.noCollision(player, player.getBoundingBox()
					.move(candidate.subtract(start)))) {
				best = candidate;
			} else {
				break;
			}
		}

		if (best.equals(start)) {
			return;
		}

		level.sendParticles(ParticleTypes.PORTAL, start.x, start.y + 1.0, start.z, 16, 0.3, 0.6, 0.3, 0.4);
		player.teleportTo(level, best.x, best.y, best.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		level.sendParticles(ParticleTypes.PORTAL, best.x, best.y + 1.0, best.z, 16, 0.3, 0.6, 0.3, 0.4);
		level.playSound(null, best.x, best.y, best.z,
				SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.4F);
		player.swing(InteractionHand.MAIN_HAND, true);
	}

	private static void grantArmor(final ServerPlayer player, final Set<Integer> owned, final float manaSpent) {
		int rank = OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned, OracleWizardNodes.Family.MAGIC_ARMOR);

		if (rank <= 0) {
			return;
		}

		// setAbsorptionAmount clamps to MAX_ABSORPTION, so the cap holds.
		float grant = manaSpent * rank * Tuning.MAGIC_ARMOR_HP_PER_MANA_PER_RANK;
		player.setAbsorptionAmount(player.getAbsorptionAmount() + grant);
	}

	private static void applyMindOverMatter(final ServerPlayer player, final Set<Integer> owned,
			final boolean active) {
		int rank = OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned,
				OracleWizardNodes.Family.MIND_OVER_MATTER);
		apply(player.getAttribute(Attributes.ATTACK_DAMAGE), MOM_DAMAGE_ID,
				active && rank > 0, rank * Tuning.MIND_OVER_MATTER_DAMAGE_PER_RANK);
	}

	private static void applyArmorCap(final ServerPlayer player, final Set<Integer> owned,
			final boolean active) {
		int rank = OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned, OracleWizardNodes.Family.MAGIC_ARMOR);
		apply(player.getAttribute(Attributes.MAX_ABSORPTION), ARMOR_CAP_ID,
				active && rank > 0, rank * Tuning.MAGIC_ARMOR_CAP_PER_RANK);
	}

	/** Levitation: creative-style flight for the channel's length. A creative or
	 * spectator player's own flight is never touched. */
	private static void manageFlight(final ServerPlayer player, final boolean active) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}

		boolean shouldFly = active && OracleWizardNodes.rank(SubTree.ORACLE_WIZARD,
				NodePurchases.owned(player, SubTree.ORACLE_WIZARD),
				OracleWizardNodes.Family.LEVITATION) > 0;

		if (shouldFly && !player.getAbilities().mayfly) {
			player.getAbilities().mayfly = true;
			player.onUpdateAbilities();
		} else if (!shouldFly && player.getAbilities().mayfly) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
	}

	private static void revokeFlight(final ServerPlayer player) {
		if (player.isCreative() || player.isSpectator() || !player.getAbilities().mayfly) {
			return;
		}

		player.getAbilities().mayfly = false;
		player.getAbilities().flying = false;
		player.onUpdateAbilities();
	}

	/** Clear every conjured weapon out of the player (hotbar/inventory/offhand). */
	private static void purgeSummoned(final ServerPlayer player) {
		Inventory inventory = player.getInventory();

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (ModItems.isSummoned(inventory.getItem(i))) {
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}

		if (ModItems.isSummoned(player.getOffhandItem())) {
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		}
	}

	/** CrusherTicker's transient-modifier idiom: the modifier exists exactly
	 * while {@code should} holds, retuning in place when its value changes. */
	private static void apply(final AttributeInstance attribute, final Identifier id,
			final boolean should, final double value) {
		if (attribute == null) {
			return;
		}

		if (should && !attribute.hasModifier(id)) {
			attribute.addTransientModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
		} else if (!should && attribute.hasModifier(id)) {
			attribute.removeModifier(id);
		} else if (should) {
			AttributeModifier current = attribute.getModifier(id);

			if (current == null || current.amount() != value) {
				attribute.removeModifier(id);
				attribute.addTransientModifier(
						new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
			}
		}
	}
}
