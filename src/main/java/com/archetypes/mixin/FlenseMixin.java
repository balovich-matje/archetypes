package com.archetypes.mixin;

import com.archetypes.ArmourMath;
import com.archetypes.AssassinNodes;
import com.archetypes.DamageTrace;
import com.archetypes.ModItems;
import com.archetypes.NodePurchases;
import com.archetypes.SubTree;
import com.archetypes.Tuning;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Flense, and nothing else — one stage in a class of its own so that its
 * position in the {@code hurtServer} chain is a number in an annotation instead
 * of an accident.
 *
 * <h2>Why it is not in {@link LivingEntityMixin}</h2>
 * Flense is not a damage bonus. {@link ArmourMath#ignoreArmourFactor} answers
 * "what would this blow have landed for if the victim wore 40% of their armour",
 * and vanilla's armour curve degrades armour by {@code damage / toughness} — so
 * the answer depends on how big THIS blow is. The number it must be fed is
 * therefore the damage that will actually arrive: everything the funnel does,
 * from any mod, and nothing of Flense's own.
 *
 * <p>Inside {@code LivingEntityMixin} that is unachievable. Position within a
 * mixin orders a handler against its siblings in the same class and against
 * nothing else; Skill Proficiencies' Combat multiplier and stealth crit are
 * {@code @ModifyVariable}s at the same HEAD, in a different config, and no
 * amount of moving lines about in our file can get underneath them.
 *
 * <h2>The ordering rule, and what it is worth here</h2>
 * Application order is priority ASCENDING, and — the direction is the
 * counter-intuitive one — the mixin applied FIRST runs FIRST. Mixin resolves
 * every injection point against the ORIGINAL instruction list before it applies
 * any of them, so every HEAD injector anchors to the same original first
 * instruction and each application inserts immediately before it, i.e. after
 * everything already inserted. {@link DamageTraceMixin} documents the same rule
 * and uses priority 500 to be first; this class uses 1500 to be last.
 *
 * <p><b>What happens without the pin.</b> Our {@code LivingEntityMixin} and
 * Skill Proficiencies' are BOTH at the default priority 1000. Mixin breaks a
 * priority tie on {@code MixinInfo}'s registration order, which is the order
 * Fabric handed the configs over, which is mod load order — so which of the two
 * mods shaped the blow first was decided by neither of them, and could change
 * under a loader update. Whichever way it fell, it was not a decision.
 *
 * <p>That used to be numerically inert: every case landed in
 * {@code ArmourMath.rawForAfterArmour}'s branch 3, a damage-independent ratio.
 * It is not inert any more. At the magnitudes the retuned ambush box produces,
 * the pre-sibling number (73.0 against full netherite) lands in BRANCH 2 — the
 * quadratic — while the true landed number (109.5) lands in branch 3. Flense
 * returns x1.196 fed the first and x1.114 fed the second: 13%, worth 2.7 HP on
 * a target the design leaves alive on 3.1. The entire 2-5 HP PvP window is
 * narrower than the ordering error, so the ordering stopped being a matter of
 * taste and became the tuning.
 *
 * <p>Pinned at 1500, Flense runs on the far side of Skill Proficiencies' x1.5
 * Combat multiplier and their mob-only x3.0 stealth crit — it is fed the damage
 * that will actually arrive, which is the only input its own arithmetic is
 * defined against.
 *
 * <h2>Nothing else may be added to this class</h2>
 * A second handler here would inherit 1500 and jump the whole funnel for no
 * reason of its own. The shapers belong in {@link LivingEntityMixin}; this file
 * is the one stage whose position is part of its definition.
 */
@Mixin(value = LivingEntity.class, priority = 1500)
public abstract class FlenseMixin {
	/**
	 * The same gate as {@code archetypes$daggerDamage}, kept verbatim rather
	 * than factored out: the two must fire on exactly the same blows or the
	 * trace's stage pair stops describing one chain, and a shared helper would
	 * hide that they have to agree.
	 */
	@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), argsOnly = true)
	private float archetypes$flense(final float amount, final ServerLevel level,
			final DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player)
				|| source.getDirectEntity() != player
				|| !com.archetypes.MeleeSwing.isSwinging(player)
				|| !ModItems.isDagger(player.getMainHandItem())) {
			return amount;
		}

		LivingEntity victim = (LivingEntity) (Object) this;
		int flense = AssassinNodes.rank(SubTree.ASSASSIN,
				NodePurchases.owned(player, SubTree.ASSASSIN), AssassinNodes.Family.FLENSE);
		float result = flense <= 0 ? amount
				: amount * ArmourMath.ignoreArmourFactor(victim, amount,
						Tuning.FLENSE_ARMOUR_IGNORE_PER_RANK * flense);

		// Recorded even at x1.00 — an unowned Flense, or a victim wearing no
		// armour at all, is exactly the answer to "why was that stab small",
		// and the stage is registered as having a breakdown so the line prints.
		DamageTrace.record(DamageTrace.STAGE_FLENSE, amount, result);
		return result;
	}
}
