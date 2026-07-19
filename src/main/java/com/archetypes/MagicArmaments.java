package com.archetypes;

import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
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
 * <p>Upkeep is charged every tick at a twentieth of its per-second rate rather
 * than as a once-a-second lump: mana is a float, so the pool reads as a trickle
 * instead of a step and Magic Armor's per-mana absorption still totals the same
 * per second. It ticks server-side rather than off a held key — this channel is
 * a toggle, not a hold.
 */
public final class MagicArmaments {
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

		// The wand is still in hand at conjure time, so the Oracle's Wand
		// discounts the opening cost. It cannot discount the upkeep: the
		// channel puts the conjured weapon in the main hand and stashes the
		// wand away, and every wand bonus reads the main hand only.
		if (!Mana.spend(player, SeekerSpells.wandDiscount(player, Tuning.MAGIC_ARMAMENTS_COST))) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		Inventory inventory = player.getInventory();
		int slot = inventory.getSelectedSlot();

		AttachmentTarget target = (AttachmentTarget) player;
		target.setAttached(ModAttachments.ARMAMENTS_WAND, player.getMainHandItem().copy());
		target.setAttached(ModAttachments.ARMAMENTS_SLOT, slot);

		boolean bow = OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned,
				OracleWizardNodes.Family.SPELLBOW) > 0;
		ItemStack conjured = new ItemStack(bow ? ModItems.MAGIC_BOW : ModItems.MAGIC_SWORD);

		// Only the sword: Sharpness does nothing on a bow and would read as a
		// lie in the tooltip. MagicBowItem derives its arrows from the same
		// bonus instead.
		if (!bow) {
			sharpen(level, conjured, OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned,
					OracleWizardNodes.Family.MIND_OVER_MATTER));
		}

		inventory.setItem(slot, conjured);

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

		// Strip the channel's grants immediately, don't wait for the next tick.
		applyArmorCap(player, NodePurchases.owned(player, SubTree.ORACLE_WIZARD), false);

		// A glide must not outlive the channel that lent the wings. Vanilla's
		// own canGlide would drop it within the tick, but ending it here makes
		// the fall immediate and deliberate rather than a frame late.
		if (player.isFallFlying()) {
			player.stopFallFlying();
		}

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

		// The absorption cap exists exactly while the channel does; apply()
		// adds or removes it idempotently.
		applyArmorCap(player, owned, active);

		if (!active) {
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

		ward(player, owned);
		upkeep(player, owned);
	}

	/** A twentieth of the per-second rate, every tick. Same cost per second, but
	 * the pool trickles instead of stepping — and because Magic Armor's grant is
	 * linear in mana spent, its absorption still totals the same per second. */
	private static void upkeep(final ServerPlayer player, final Set<Integer> owned) {
		int mom = OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned,
				OracleWizardNodes.Family.MIND_OVER_MATTER);
		float cost = (Tuning.MAGIC_ARMAMENTS_UPKEEP_PER_SECOND
				+ mom * Tuning.MIND_OVER_MATTER_UPKEEP_PER_RANK) / 20.0F;

		// spend() is all-or-nothing, so the channel ends on the exact tick the
		// pool cannot cover a tick's worth.
		if (!Mana.spend(player, cost)) {
			end(player);
			return;
		}

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

	/** The conjured sword's Sharpness level at a Mind over Matter rank. */
	public static int sharpnessLevel(final int mindOverMatterRank) {
		return Tuning.MAGIC_ARMAMENTS_SHARPNESS
				+ mindOverMatterRank * Tuning.MIND_OVER_MATTER_SHARPNESS_PER_RANK;
	}

	/** The damage that Sharpness level adds. Mirrors vanilla's own curve
	 * (1 + 0.5 x (level - 1) since level 1) so the Spellbow, which cannot carry
	 * the enchantment, can scale off the identical number. */
	public static float sharpnessBonus(final int mindOverMatterRank) {
		return 1.0F + 0.5F * (sharpnessLevel(mindOverMatterRank) - 1);
	}

	/** Stamp the real ENCHANTMENTS component, so the level shows in the tooltip
	 * and the damage runs through vanilla's pipeline rather than a bolted-on
	 * attribute. The Holder must come from the level's registries — enchantments
	 * are datapack content and {@code Enchantments.SHARPNESS} is only a key. */
	private static void sharpen(final ServerLevel level, final ItemStack stack, final int mindOverMatterRank) {
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		enchantments.set(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.SHARPNESS), sharpnessLevel(mindOverMatterRank));
		EnchantmentHelper.setEnchantments(stack, enchantments.toImmutable());
	}

	private static void applyArmorCap(final ServerPlayer player, final Set<Integer> owned,
			final boolean active) {
		int rank = OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned, OracleWizardNodes.Family.MAGIC_ARMOR);
		apply(player.getAttribute(Attributes.MAX_ABSORPTION), ARMOR_CAP_ID,
				active && rank > 0, rank * Tuning.MAGIC_ARMOR_CAP_PER_RANK);
	}

	/**
	 * Gliding: the channel stands in for an elytra, nothing more. Hooked from
	 * {@code PlayerMixin} onto {@code Player.canGlide()}, so vanilla owns
	 * jump-to-deploy, firework boosts, the flight physics and the landing.
	 *
	 * <p>Both terms ride state the owning client already has synced — the
	 * conjured weapon in the main hand (the channel's own invariant; the ticker
	 * ends the channel the tick it leaves) and the owned nodes — so the client's
	 * deploy attempt and the server's re-check always agree. The leading guards
	 * are vanilla's own preconditions repeated: a channel replaces the wings,
	 * not the rules that forbid a glide.
	 */
	public static boolean canGlide(final Player player) {
		if (player.getAbilities().flying || player.onGround() || player.isPassenger()
				|| player.hasEffect(MobEffects.LEVITATION)) {
			return false;
		}

		return ModItems.isSummoned(player.getMainHandItem())
				&& OracleWizardNodes.rank(SubTree.ORACLE_WIZARD,
						NodePurchases.owned(player, SubTree.ORACLE_WIZARD),
						OracleWizardNodes.Family.LEVITATION) > 0;
	}

	/** The conjured items police themselves: any inventory tick whose holder
	 * isn't mid-channel destroys the stack (see {@code MagicSwordItem} /
	 * {@code MagicBowItem}). Containers never tick their stacks, but anything
	 * withdrawn from one lands in an inventory that does — so a weapon smuggled
	 * into a chest dies the moment any player takes it back out. */
	public static void purgeStray(final ItemStack stack, final net.minecraft.world.entity.Entity holder) {
		if (!(holder instanceof ServerPlayer player) || !isActive(player)) {
			stack.setCount(0);
		}
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
