package com.archetypes;

import java.util.List;
import java.util.Set;

import com.archetypes.compat.SpecialitiesBridge;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
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
		float opening = SeekerSpells.wandDiscount(player, Tuning.MAGIC_ARMAMENTS_COST);

		if (!Mana.spend(player, opening)) {
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

		enchant(level, conjured, owned);

		// Stamped here, not left to the next tick: a player who jumps on the
		// same tick they conjure would otherwise find no wings.
		fitGlider(conjured, owned);
		inventory.setItem(slot, conjured);

		// The cap must exist before the opening cost's absorption is banked, or
		// it clamps straight to zero (see Battle Trance).
		applyArmorCap(player, owned, true);
		// Absorption is bought with the mana actually paid, discount included —
		// banking the list price would hand the Oracle's Wand free armour.
		grantArmor(player, owned, opening);

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

		// Both rides: the enchantments and the wings track whichever weapon the
		// channel conjured.
		reenchant(player, owned);
		fitGlider(player.getMainHandItem(), owned);
		ward(player, owned);
		upkeep(player, owned);
	}

	/** Mind over Matter can be bought mid-channel, so the conjured weapon has to
	 * pick up its Breach without being re-conjured. A no-op once the stamp
	 * matches, so the stack is not resynced every tick. */
	private static void reenchant(final ServerPlayer player, final Set<Integer> owned) {
		ItemStack held = player.getMainHandItem();

		if (!ModItems.isSummoned(held)) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();

		if (EnchantmentHelper.getItemEnchantmentLevel(breach(level), held) != breachLevel(owned)) {
			enchant(level, held, owned);
		}
	}

	/**
	 * Levitation: the conjured weapon IS the glider.
	 *
	 * <p>The obvious implementation — overriding {@code Player.canGlide} — is a
	 * server crash. Vanilla trusts that hook: every twentieth gliding tick
	 * {@code LivingEntity.updateFallFlying} collects the equipment slots
	 * holding a glider and calls {@code Util.getRandom} on that list to pick
	 * one to damage. Claiming a glide with no glider equipped hands it an empty
	 * list, and {@code nextInt(0)} throws mid-tick.
	 *
	 * <p>So the weapon carries the real components instead: GLIDER plus an
	 * EQUIPPABLE that names the hand it is already in. Vanilla then answers its
	 * own question, the slot it finds to damage is an unbreakable stack (a
	 * no-op), and deploy, boosts, physics and landing are all stock. The glide
	 * cannot outlive the channel because the weapon cannot: the ticker ends the
	 * channel the tick it leaves the hand.
	 */
	private static void fitGlider(final ItemStack stack, final Set<Integer> owned) {
		boolean levitation = OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned,
				OracleWizardNodes.Family.LEVITATION) > 0;

		if (levitation == stack.has(DataComponents.GLIDER)) {
			return;
		}

		if (levitation) {
			stack.set(DataComponents.GLIDER, net.minecraft.util.Unit.INSTANCE);
			// Swapping and interact-equipping off: the hand slot is where it
			// already lives, and the bow needs its right-click for the draw.
			stack.set(DataComponents.EQUIPPABLE,
					net.minecraft.world.item.equipment.Equippable
							.builder(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
							.setSwappable(false)
							.setEquipOnInteract(false)
							.setDispensable(false)
							.setDamageOnHurt(false)
							.build());
		} else {
			stack.remove(DataComponents.GLIDER);
			stack.remove(DataComponents.EQUIPPABLE);
		}
	}

	/** A twentieth of the per-second rate, every tick. Same cost per second, but
	 * the pool trickles instead of stepping — and because Magic Armor's grant is
	 * linear in mana spent, its absorption still totals the same per second. */
	private static void upkeep(final ServerPlayer player, final Set<Integer> owned) {
		float cost = Tuning.MAGIC_ARMAMENTS_UPKEEP_PER_SECOND / 20.0F;

		// The wand is stashed, not held, for as long as this runs — price the
		// upkeep off it anyway, or the Oracle's Wand would quietly exempt the
		// one spell the player is paying for by the tick.
		ItemStack stashed = ((AttachmentTarget) player).getAttached(ModAttachments.ARMAMENTS_WAND);

		if (stashed != null) {
			cost = SeekerSpells.wandDiscount(stashed, cost);
		}

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

	/**
	 * The fraction of a normal bow draw the Spellbow needs, for THIS player.
	 *
	 * <p>Both sides of the draw must read this one number: the server turns it
	 * into the power that leaves the bow ({@code MagicBowItem.releaseUsing}) and
	 * the client turns it into the pull animation
	 * ({@code UseDurationMixin}). Two factors would let the bow fire at full
	 * power while its model still shows a half-drawn string.
	 *
	 * <p>Specialities' Archery reduction stacks on the node's own, capped —
	 * their {@code BowItemMixin} cannot reach a bow whose {@code releaseUsing}
	 * never calls super, so it is folded in here instead of inherited.
	 */
	public static float drawTimeFactor(final Player player) {
		float reduction = Tuning.SPELLBOW_DRAW_TIME_REDUCTION
				+ SpecialitiesBridge.archeryDrawTimeReduction(SpecialitiesBridge.archeryLevel(player));
		return 1.0F - Math.min(reduction, Tuning.SPELLBOW_DRAW_TIME_REDUCTION_CAP);
	}

	/** The damage the conjured sword's flat Sharpness adds. Mirrors vanilla's
	 * own curve (1 + 0.5 x (level - 1) since level 1) so the Spellbow, which
	 * cannot carry the enchantment, can scale off the identical number. */
	public static float sharpnessBonus() {
		return 1.0F + 0.5F * (Tuning.MAGIC_ARMAMENTS_SHARPNESS - 1);
	}

	/**
	 * Mind over Matter's half that vanilla can carry for us: virtual Breach on
	 * the conjured weapon, which zeroes the victim's armor effectiveness inside
	 * {@code CombatRules.getDamageAfterAbsorb}. The doubling is NOT here — see
	 * {@link #shapeHit}: Sharpness is flat but Power (and any bonus folded into
	 * an arrow's base) is multiplied by the full-draw velocity, so no single
	 * enchantment doubles both weapons by the same amount.
	 *
	 * <p>Sharpness rides the sword only, for the same reason: on the bow it
	 * would reach the arrow's base through {@code EnchantmentHelper.modifyDamage}
	 * and be paid out three times over.
	 */
	private static void enchant(final ServerLevel level, final ItemStack stack, final Set<Integer> owned) {
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);

		if (ModItems.isMagicSword(stack)) {
			enchantments.set(sharpness(level), Tuning.MAGIC_ARMAMENTS_SHARPNESS);
		}

		int breach = breachLevel(owned);

		if (breach > 0) {
			enchantments.set(breach(level), breach);
		}

		EnchantmentHelper.setEnchantments(stack, enchantments.toImmutable());
	}

	private static int breachLevel(final Set<Integer> owned) {
		return OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned,
				OracleWizardNodes.Family.MIND_OVER_MATTER) > 0
						? Tuning.MIND_OVER_MATTER_BREACH
						: 0;
	}

	/** The Holder must come from the level's registries — enchantments are
	 * datapack content and {@code Enchantments.SHARPNESS} is only a key. */
	private static Holder<Enchantment> sharpness(final ServerLevel level) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.SHARPNESS);
	}

	private static Holder<Enchantment> breach(final ServerLevel level) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.BREACH);
	}

	/**
	 * The conjured weapons' on-hit half, off the {@code hurtServer} funnel:
	 * Mind over Matter's doubling and Mana Siphon's refund. Both weapons land
	 * here — the sword as a direct player attack, the Spellbow's arrow as a
	 * marked projectile whose shooter is the channeller.
	 *
	 * <p>The doubling is applied to the finished hit rather than to the sword's
	 * Sharpness or the arrow's base damage, because those two are not the same
	 * currency: {@code AbstractArrow.onHitEntity} multiplies the arrow's base by
	 * the draw velocity (3x at full draw) AFTER the enchantment bonus lands on
	 * it, while Sharpness on a sword is flat. One multiplier on the outgoing
	 * damage is the only form that doubles both by exactly two.
	 */
	public static float shapeHit(final ServerPlayer player, final ServerLevel level,
			final float amount, final boolean spellbowArrow) {
		Set<Integer> owned = NodePurchases.owned(player, SubTree.ORACLE_WIZARD);
		float result = amount;

		if (OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned,
				OracleWizardNodes.Family.MIND_OVER_MATTER) > 0) {
			result *= Tuning.MIND_OVER_MATTER_DAMAGE;
		}

		// Mana Siphon pays on the hit, not on the shot: an arrow that finds
		// nothing costs the archer the shot and nothing more.
		if (spellbowArrow && OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned,
				OracleWizardNodes.Family.MANA_SIPHON) > 0) {
			Mana.add(player, Tuning.MANA_SIPHON_PER_HIT);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5F, 1.6F);
		}

		return result;
	}

	private static void applyArmorCap(final ServerPlayer player, final Set<Integer> owned,
			final boolean active) {
		int rank = OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned, OracleWizardNodes.Family.MAGIC_ARMOR);
		apply(player.getAttribute(Attributes.MAX_ABSORPTION), ARMOR_CAP_ID,
				active && rank > 0, rank * Tuning.MAGIC_ARMOR_CAP_PER_RANK);
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
