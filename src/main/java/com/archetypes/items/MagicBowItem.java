package com.archetypes.items;

import java.util.function.Predicate;

import com.archetypes.MagicArmaments;
import com.archetypes.ModAttachments;
import com.archetypes.NodePurchases;
import com.archetypes.OracleWizardNodes;
import com.archetypes.SubTree;
import com.archetypes.Tuning;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Spellbow variant of Magic Armaments. It needs no ammo — every draw
 * conjures its own arrow, fired with pickup disallowed so nothing is left to
 * gather (an infinity bow's manners). The arrow's damage sits at the conjured
 * sword's budget and rises with the same Sharpness the sword carries, so
 * choosing the bow costs no power. The item itself is unbreakable and only ever
 * exists inside a channel.
 *
 * <p>Three things separate it from the bow it extends, and all three are the
 * arrow's, not the item's: it draws in a quarter of the time (stacking with
 * Specialities' Archery, see {@link MagicArmaments#drawTimeFactor}), its arrows
 * fall at a quarter gravity, and they wear the Magic Missile's violet trail.
 * The last two ride the {@code SPELLBOW_ARROW} mark and are applied in
 * {@code AbstractArrowMixin}, so an arrow keeps them after the bow that
 * conjured it is gone.
 */
public class MagicBowItem extends BowItem {
	private static final DustParticleOptions TRAIL_DUST =
			new DustParticleOptions(Tuning.MISSILE_DUST_COLOR, 0.6F);
	private static final DustParticleOptions TRAIL_BRIGHT_DUST =
			new DustParticleOptions(Tuning.MISSILE_DUST_BRIGHT_COLOR, 1.0F);

	public MagicBowItem(final Properties properties) {
		super(properties);
	}

	/** The conjured arrow's trail, drawn from the Magic Missile's palette so the
	 * two read as one school (see Tuning.MISSILE_DUST_COLOR). Driven from
	 * {@code AbstractArrowMixin}; server-side, one particle per emission —
	 * a bow this fast keeps several arrows in the air at once. */
	public static void flightFx(final ServerLevel level, final AbstractArrow arrow, final int age) {
		if (age % Tuning.SPELLBOW_ARROW_TRAIL_PERIOD_TICKS == 0) {
			level.sendParticles(TRAIL_DUST, arrow.getX(), arrow.getY(), arrow.getZ(),
					1, 0.03, 0.03, 0.03, 0.0);
		}

		if (age % Tuning.SPELLBOW_ARROW_SPARKLE_PERIOD_TICKS == 0) {
			level.sendParticles(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(),
					1, 0.03, 0.03, 0.03, 0.0);
		}

		if (age % Tuning.SPELLBOW_ARROW_CHIME_PERIOD_TICKS == 0) {
			level.playSound(null, arrow.getX(), arrow.getY(), arrow.getZ(),
					SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4F,
					1.45F + (level.getRandom().nextFloat() - 0.5F) * 0.2F);
		}
	}

	/** The missile's landing tick, on entity hits only — the same event
	 * SpellProjectile.missileHit marks, at the same volume. */
	public static void impactFx(final ServerLevel level, final AbstractArrow arrow) {
		level.sendParticles(TRAIL_BRIGHT_DUST, arrow.getX(), arrow.getY(), arrow.getZ(),
				4, 0.1, 0.1, 0.1, 0.0);
		level.playSound(null, arrow.getX(), arrow.getY(), arrow.getZ(),
				SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.5F,
				1.1F + (level.getRandom().nextFloat() - 0.5F) * 0.4F);
	}

	@Override
	public Predicate<ItemStack> getAllSupportedProjectiles() {
		return stack -> true;
	}

	@Override
	public void inventoryTick(final ItemStack stack, final ServerLevel level, final Entity entity,
			final @Nullable EquipmentSlot slot) {
		MagicArmaments.purgeStray(stack, entity);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		// Draw regardless of ammo — the arrow is conjured on release.
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public boolean releaseUsing(final ItemStack stack, final Level level,
			final LivingEntity entity, final int timeLeft) {
		int used = this.getUseDuration(stack, entity) - timeLeft;

		// Vanilla's power curve is the only thing that knows about draw time, so
		// the draw is shortened by stretching the time fed into it rather than
		// by rewriting the curve — same shape, same 1.0 ceiling, reached sooner.
		// This is also where Specialities' Archery bonus enters: their mixin
		// wraps BowItem.releaseUsing, which this override never calls.
		if (entity instanceof Player drawer) {
			used = (int) (used / MagicArmaments.drawTimeFactor(drawer));
		}

		float power = BowItem.getPowerForTime(used);

		if (power < 0.1F) {
			return false;
		}

		// Only a live channel conjures arrows: a strayed bow (a dupe attempt
		// caught mid-juggle) fires nothing even in the tick before it purges.
		if (level instanceof ServerLevel serverLevel && entity instanceof ServerPlayer player
				&& MagicArmaments.isActive(player)) {
			// Sharpness rides the conjured sword, not a bow. The arrow reads the
			// bonus that enchantment would have been worth and takes a third of
			// it into its base, which the 3x full-draw velocity restores whole.
			// A real Power enchantment on the stack would NOT be equivalent:
			// vanilla adds it to this same base and then multiplies, so the
			// bonus would be paid three times over (see the SHARPNESS_SHARE note
			// in Tuning).
			int mom = OracleWizardNodes.rank(SubTree.ORACLE_WIZARD,
					NodePurchases.owned(player, SubTree.ORACLE_WIZARD),
					OracleWizardNodes.Family.MIND_OVER_MATTER);
			double baseDamage = Tuning.MAGIC_BOW_ARROW_BASE_DAMAGE
					+ MagicArmaments.sharpnessBonus(mom) * Tuning.MAGIC_BOW_ARROW_SHARPNESS_SHARE;

			Arrow arrow = new Arrow(serverLevel, player, new ItemStack(Items.ARROW), stack);
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
			arrow.setBaseDamage(baseDamage);
			arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * 3.0F, 1.0F);

			if (power == 1.0F) {
				arrow.setCritArrow(true);
			}

			// Marked before it enters the world, so the flag rides the same
			// packet that spawns it and the client never integrates one tick of
			// full gravity (see ModAttachments.SPELLBOW_ARROW).
			((AttachmentTarget) arrow).setAttached(ModAttachments.SPELLBOW_ARROW, true);

			serverLevel.addFreshEntity(arrow);
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F,
					1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
		}

		return true;
	}
}
