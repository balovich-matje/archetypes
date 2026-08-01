package com.archetypes;

import java.util.List;
import java.util.Set;

import com.archetypes.compat.SpecialitiesBridge;

import com.archetypes.platform.ArchetypeStore;

import net.minecraft.world.entity.Entity;
import net.minecraft.core.Holder;
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
//?}
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
//? if >=1.21 {
import net.minecraft.world.item.enchantment.ItemEnchantments;
//?}
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Magic Armaments, the Oracle Wizard's channel: a conjured weapon stands in for
 * the wand until the player toggles it off, the mana runs dry, or the weapon
 * leaves the hand. The real wand is parked in a persistent attachment
 * ({@link ModState#ARMAMENTS_WAND}) and restored exactly where it sat, so
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
		return ArchetypeStore.INSTANCE.get(player, ModState.ARMAMENTS_WAND) != null;
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
		// `Inventory.selected` became the private field behind `getSelectedSlot()` in
		// 1.21.11; below it the field is public and there is no accessor.
		//? if >=1.21.11 {
		int slot = inventory.getSelectedSlot();
		//?} else {
		/*int slot = inventory.selected;
		*///?}

		final Entity target = player;
		ArchetypeStore.INSTANCE.set(target, ModState.ARMAMENTS_WAND, player.getMainHandItem().copy());
		ArchetypeStore.INSTANCE.set(target, ModState.ARMAMENTS_SLOT, slot);

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
		final Entity target = player;
		ItemStack wand = ArchetypeStore.INSTANCE.get(target, ModState.ARMAMENTS_WAND);
		Integer slot = ArchetypeStore.INSTANCE.get(target, ModState.ARMAMENTS_SLOT);

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

		ArchetypeStore.INSTANCE.remove(target, ModState.ARMAMENTS_WAND);
		ArchetypeStore.INSTANCE.remove(target, ModState.ARMAMENTS_SLOT);

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

		//? if >=1.21 {
		if (EnchantmentHelper.getItemEnchantmentLevel(breach(level), held) != breachLevel(owned)) {
			enchant(level, held, owned);
		}
		//?}
	}

	/**
	 * Levitation: the conjured weapon IS the glider.
	 *
	 * <p>The obvious implementation on THIS version — overriding
	 * {@code Player.canGlide} — is a server crash. Vanilla trusts that hook:
	 * every twentieth gliding tick {@code LivingEntity.updateFallFlying}
	 * collects the equipment slots holding a glider and calls
	 * {@code Util.getRandom} on that list to pick one to damage. Claiming a
	 * glide with no glider equipped hands it an empty list, and
	 * {@code nextInt(0)} throws mid-tick.
	 *
	 * <p>So the weapon carries the real components instead: GLIDER plus an
	 * EQUIPPABLE that names the hand it is already in. Vanilla then answers its
	 * own question, the slot it finds to damage is an unbreakable stack (a
	 * no-op), and deploy, boosts, physics and landing are all stock. The glide
	 * cannot outlive the channel because the weapon cannot: the ticker ends the
	 * channel the tick it leaves the hand.
	 */
	// ---- R-A6 REOPENED: LEVITATION IS LIVE ON ALL FOUR LEGACY NODES, THROUGH REAL VANILLA
	// FALL-FLYING. THE CRASH PREMISE ABOVE IS 1.21.11+-ONLY. ----
	//
	// The boundary this comment used to state is right and the conclusion drawn from it was
	// not. Right: `DataComponents.GLIDER`, `DataComponents.EQUIPPABLE` and the whole
	// `net.minecraft.world.item.equipment` package are absent from the 1.21.1 mojmap jar too,
	// not just 1.20.1 — so the component route has no host on any legacy node. Wrong: that
	// there is therefore nothing to do but excise.
	//
	// THREE MEASUREMENTS OVERTURNED IT.
	//
	//  1. THE CRASH CANNOT HAPPEN BELOW THE BOUNDARY. There is no slot list and no
	//     `Util.getRandom` anywhere in the legacy `updateFallFlying` — it reads the CHEST
	//     stack directly. `Util.getRandom` appears in `LivingEntity` only inside
	//     `tickEffects()` on 1.21.1 and NeoForge, and not at all on 1.20.1 or LexForge.
	//     `canGlide` does not exist as a method on any of the four (`javap -p` on `Player`
	//     and `LivingEntity`: 0 hits). The hook the javadoc above is afraid of is not there.
	//  2. A SERVER-ONLY FIX WOULD BE A TOTAL NO-OP, not merely rubber-bandy.
	//     `LocalPlayer.aiStep` INLINES the chest-glider test before it will even call
	//     `tryToStartFallFlying()` or send `START_FALL_FLYING` — `chest.is(Items.ELYTRA) &&
	//     ElytraItem.isFlyEnabled(chest)` on Fabric, `chest.canElytraFly(this)` on both
	//     loaders. Without a client half the packet is never sent. That is a GATE, not a
	//     prediction mismatch, which is why it is fixable.
	//  3. ONCE STARTED THERE IS NO PREDICTION PROBLEM AT ALL. `LivingEntity.travel`'s glide
	//     branch is gated on `isFallFlying()` = shared flag 7 alone, and the client never
	//     writes that flag: both `updateFallFlying`'s write and `travel`'s landing clear sit
	//     behind `if (!level.isClientSide)`, and every clear reaches the owner because
	//     `ServerEntity.sendDirtyEntityData` broadcasts to the entity itself when it is a
	//     `ServerPlayer`. `handleMovePlayer` even picks its 300-vs-100 speed cap off the
	//     SERVER's flag, so there is no "moved too quickly" either.
	//
	// THE ROUTE: wrap the CHEST-slot READ, not the boolean. The boolean forks per loader
	// (`is(ELYTRA) && isFlyEnabled` vs `canElytraFly`, plus `elytraFlightTick` in the loaders'
	// `updateFallFlying`); `getItemBySlot(EquipmentSlot)ItemStack` is present exactly once in
	// each target method on ALL FOUR arms, same structural position, same owner — so the
	// handler is ONE shape with ZERO annotation fork. Three anchors, because the client gate
	// is where the node actually died:
	//
	//   Player.tryToStartFallFlying()Z   offset 35 on all four   — mixin/PlayerMixin
	//   LivingEntity.updateFallFlying()V offset 39 on all four   — mixin/LivingEntityMixin
	//   LocalPlayer.aiStep()V            858/825/956/924         — client/mixin/LocalPlayerMixin
	//
	// One occurrence each, so no ordinal, and `injectors.defaultRequire: 1` is the detector.
	// `ServerPlayer` and `LocalPlayer` override none of `tryToStartFallFlying` /
	// `startFallFlying` / `stopFallFlying` / `updateFallFlying`, so the two `src/main` anchors
	// cover both logical sides.
	//
	// WHAT COMES FREE, all keyed on flag 7: the `fallFlyTicks` lean interpolation, the
	// `ElytraOnPlayerSoundInstance` wind on the flag edge, the glide pose, firework boosts,
	// and the landing rules. And `ElytraLayer` keys on `Items.ELYTRA` being in the CHEST, so
	// no wings are drawn — which is exactly what 26.x looks like, because its EQUIPPABLE
	// names MAINHAND.
	//? if >=1.21.11 {
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
	//?} else {
	/*private static void fitGlider(final ItemStack stack, final Set<Integer> owned) {
	}
	*///?}

	/**
	 * The legacy glide, answered where vanilla asks it: the CHEST slot.
	 *
	 * <p>Wrapped by three {@code @WrapOperation}s (see the note above
	 * {@code fitGlider}). Hands vanilla a stand-in elytra while the channel is
	 * up and the node is owned, so vanilla answers its own question with its own
	 * code on both sides — deploy gesture, packet, flag 7, physics, boosts and
	 * landing are all stock, exactly as the component route makes them stock
	 * above the boundary.
	 *
	 * <p>The stand-in is rebuilt on every call and never stored anywhere, so it
	 * cannot be dropped, kept or duped; a REAL elytra in the chest is passed
	 * through untouched so it keeps taking its own durability.
	 */
	// THE PREDICATE IS IDENTICAL ON BOTH SIDES AND READS ONLY SYNCED STATE — that is the whole
	// reason this works without a packet.
	//   * `MagicArmaments.isActive` CANNOT be the test: `ARMAMENTS_WAND` is server-only (see
	//     ModState's own note on it). `ModItems.isSummoned(mainHand)` is the SAME QUESTION —
	//     `tick()` ends the channel the tick that stops being true — and the main-hand stack
	//     is inventory-synced.
	//   * `NodePurchases.owned(Player, SubTree)` is client-safe by its own contract (the
	//     attachment syncs to its owning client); `ArchetypeScreen` and `CooldownBarHud`
	//     already call it with `minecraft.player`.
	// If the two sides ever disagreed the failure would be a one-tick flap — the client sets
	// the flag optimistically, the server's `updateFallFlying` clears it and syncs — not a
	// rubber-band and not a kick, because the server's own speed cap follows its own flag.
	//
	// WHY THE STAND-IN SATISFIES EVERY DOWNSTREAM TEST, measured on the real jars:
	//   * `ElytraItem.isFlyEnabled(stack)` is `getDamageValue() < getMaxDamage() - 1` -> 0 <
	//     431 -> true (UNBREAKABLE does not zero `getMaxDamage`).
	//   * `ElytraItem.canElytraFly(stack, entity)` on BOTH loaders is a one-line
	//     `return isFlyEnabled(stack)` -> true.
	//   * `ElytraItem.elytraFlightTick(...)` on both loaders ends `iconst_1; ireturn`, so the
	//     `&&` in the loaders' `updateFallFlying` holds.
	//   * `ItemStack.hurtAndBreak` early-returns on `!isDamageableItem()`, which UNBREAKABLE
	//     makes false — so the damage step, `processDurabilityChange` and the
	//     `ITEM_DURABILITY_CHANGED` trigger are all skipped. That is the 26.x note's "the slot
	//     it finds to damage is an unbreakable stack (a no-op)", reproduced.
	//   * `gameEvent(GameEvent.ELYTRA_GLIDE)` still fires server-side, which is correct: sculk
	//     hears gliders on 26.x too.
	//
	// KNOWN NARROWING, stated rather than papered over: on the two loader nodes another mod's
	// `canElytraFly` chestpiece is shadowed by the stand-in for the duration of the channel.
	// Widening the pass-through from `is(ELYTRA)` to `canElytraFly` would fix it and would
	// fork this method two ways for a case no vanilla install has; not done.
	//? if <1.21.11 {
	/*public static ItemStack legacyGliderSlot(final Entity holder,
			final net.minecraft.world.entity.EquipmentSlot slot, final ItemStack real) {
		if (slot != net.minecraft.world.entity.EquipmentSlot.CHEST
				|| !(holder instanceof Player player)
				|| real.is(net.minecraft.world.item.Items.ELYTRA)
				|| !ModItems.isSummoned(player.getMainHandItem())
				|| OracleWizardNodes.rank(SubTree.ORACLE_WIZARD,
						NodePurchases.owned(player, SubTree.ORACLE_WIZARD),
						OracleWizardNodes.Family.LEVITATION) <= 0) {
			return real;
		}

		return legacyGlider();
	}
	*///?}
	// Only the UNBREAKABLE stamp forks — the data-components row, already in the frozen
	// vocabulary. Written as two top-level arms rather than one nested inside the arm above,
	// so no marker has to escalate to `^` and the predicate stays written exactly once.
	//? if >=1.21 && <1.21.11 {
	/*private static ItemStack legacyGlider() {
		ItemStack glider = new ItemStack(net.minecraft.world.item.Items.ELYTRA);

		glider.set(DataComponents.UNBREAKABLE,
				new net.minecraft.world.item.component.Unbreakable(false));
		return glider;
	}
	*///?}
	//? if <1.21 {
	/*private static ItemStack legacyGlider() {
		ItemStack glider = new ItemStack(net.minecraft.world.item.Items.ELYTRA);

		glider.getOrCreateTag().putBoolean("Unbreakable", true);
		return glider;
	}
	*///?}

	/** A twentieth of the per-second rate, every tick. Same cost per second, but
	 * the pool trickles instead of stepping — and because Magic Armor's grant is
	 * linear in mana spent, its absorption still totals the same per second. */
	private static void upkeep(final ServerPlayer player, final Set<Integer> owned) {
		float cost = Tuning.MAGIC_ARMAMENTS_UPKEEP_PER_SECOND / 20.0F;

		// The wand is stashed, not held, for as long as this runs — price the
		// upkeep off it anyway, or the Oracle's Wand would quietly exempt the
		// one spell the player is paying for by the tick.
		ItemStack stashed = ArchetypeStore.INSTANCE.get(player, ModState.ARMAMENTS_WAND);

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

		/*? if >=1.21 {*/List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> harmful =
		/*?} else *///List<net.minecraft.world.effect.MobEffect> harmful =
				new java.util.ArrayList<>();

		for (MobEffectInstance instance : player.getActiveEffects()) {
			/*? if >=1.21 {*/if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
			/*?} else *///if (instance.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
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
		// The trailing `setCamera` flag — see AgilityActives for the note.
		//? if >=1.21.11 {
		player.teleportTo(level, best.x, best.y, best.z, Set.of(), player.getYRot(), player.getXRot(), false);
		//?} else {
		/*player.teleportTo(level, best.x, best.y, best.z, Set.of(), player.getYRot(), player.getXRot());
		*///?}
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
		// STAGE 5: below 1.21 there is no MAX_ABSORPTION attribute and
		// `setAbsorptionAmount` clamps nothing, so the cap `applyArmorCap` carries above
		// has to be applied here instead — the same `rank * MAGIC_ARMOR_CAP_PER_RANK`,
		// read at the one site that grants this absorption. It is the whole ceiling on
		// this node because the only other absorption cap in the mod, Battle Trance's,
		// belongs to a STRENGTH sub-tree and this one to an INTELLECT sub-tree: one
		// player can never hold both, so no summing is being lost.
		//? if >=1.21 {
		player.setAbsorptionAmount(player.getAbsorptionAmount() + grant);
		//?} else {
		/*player.setAbsorptionAmount(Math.min(rank * Tuning.MAGIC_ARMOR_CAP_PER_RANK,
				player.getAbsorptionAmount() + grant));
		*///?}
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
	 * the conjured weapon, which sheds 0.15 of the victim's armor effectiveness
	 * per level inside {@code CombatRules.getDamageAfterAbsorb} — two levels,
	 * so 30% of their armor, not all of it (a full ignore playtested as the
	 * strongest damage-per-mana in the mod). The doubling is NOT here — see
	 * {@link #shapeHit}: Sharpness is flat but Power (and any bonus folded into
	 * an arrow's base) is multiplied by the full-draw velocity, so no single
	 * enchantment doubles both weapons by the same amount.
	 *
	 * <p>Sharpness rides the sword only, for the same reason: on the bow it
	 * would reach the arrow's base through {@code EnchantmentHelper.modifyDamage}
	 * and be paid out three times over.
	 */
	// STAGE 5: no component map below 1.21 — the enchantments are a plain map written into
	// the stack's NBT, and `setEnchantments` takes its two arguments the other way round.
	// BREACH IS EXCISED HERE AND NOWHERE ELSE (design R-A5's treatment, applied to a second
	// case): the enchantment does not exist below 1.21 and there is nothing else on that
	// version that reduces armour effectiveness the way it does. Mind over Matter keeps its
	// other, larger half — the damage multiplier in shapeHit — so the node stays worth
	// buying and its lang entry says the armour-piercing is inactive.
	//? if >=1.21 {
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
	//?} else {
	/*private static void enchant(final ServerLevel level, final ItemStack stack, final Set<Integer> owned) {
		java.util.Map<Enchantment, Integer> enchantments = new java.util.LinkedHashMap<>();

		if (ModItems.isMagicSword(stack)) {
			enchantments.put(sharpness(level), Tuning.MAGIC_ARMAMENTS_SHARPNESS);
		}

		EnchantmentHelper.setEnchantments(enchantments, stack);
	}
	*///?}

	private static int breachLevel(final Set<Integer> owned) {
		return OracleWizardNodes.rank(SubTree.ORACLE_WIZARD, owned,
				OracleWizardNodes.Family.MIND_OVER_MATTER) > 0
						? Tuning.MIND_OVER_MATTER_BREACH
						: 0;
	}

	/** The Holder must come from the level's registries — enchantments are
	 * datapack content and {@code Enchantments.SHARPNESS} is only a key. */
	//? if >=1.21 {
	private static Holder<Enchantment> sharpness(final ServerLevel level) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.SHARPNESS);
	}

	private static Holder<Enchantment> breach(final ServerLevel level) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.BREACH);
	}
	//?} else {
	/*// Below 1.21 enchantments are objects rather than datapack content, so the registry
	// lookup goes and the level parameter goes unread. `breach` has no legacy twin at all:
	// the enchantment does not exist there (see `enchant`).
	private static Enchantment sharpness(final ServerLevel level) {
		return Enchantments.SHARPNESS;
	}
	*///?}

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
		//? if >=1.21 {
		apply(player.getAttribute(Attributes.MAX_ABSORPTION), ARMOR_CAP_ID,
				active && rank > 0, rank * Tuning.MAGIC_ARMOR_CAP_PER_RANK);
		//?}
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

		//? if >=1.21 {
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
		//?} else {
		/*if (should && !LegacyAttributes.has(attribute, id)) {
			attribute.addTransientModifier(
					LegacyAttributes.modifier(id, value, AttributeModifier.Operation.ADD_VALUE));
		} else if (!should && LegacyAttributes.has(attribute, id)) {
			LegacyAttributes.remove(attribute, id);
		} else if (should) {
			AttributeModifier current = LegacyAttributes.get(attribute, id);

			if (current == null || current.getAmount() != value) {
				LegacyAttributes.remove(attribute, id);
				attribute.addTransientModifier(
						LegacyAttributes.modifier(id, value, AttributeModifier.Operation.ADD_VALUE));
			}
		}
		*///?}
	}
}
