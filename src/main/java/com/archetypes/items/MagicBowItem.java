package com.archetypes.items;

import java.util.function.Predicate;

import com.archetypes.MagicArmaments;
import com.archetypes.NodePurchases;
import com.archetypes.OracleWizardNodes;
import com.archetypes.SubTree;
import com.archetypes.Tuning;

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
 * sword's budget and rises with Mind over Matter, so choosing the bow costs no
 * power. The item itself is unbreakable and only ever exists inside a channel.
 */
public class MagicBowItem extends BowItem {
	public MagicBowItem(final Properties properties) {
		super(properties);
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
		float power = BowItem.getPowerForTime(used);

		if (power < 0.1F) {
			return false;
		}

		// Only a live channel conjures arrows: a strayed bow (a dupe attempt
		// caught mid-juggle) fires nothing even in the tick before it purges.
		if (level instanceof ServerLevel serverLevel && entity instanceof ServerPlayer player
				&& MagicArmaments.isActive(player)) {
			int mom = OracleWizardNodes.rank(SubTree.ORACLE_WIZARD,
					NodePurchases.owned(player, SubTree.ORACLE_WIZARD),
					OracleWizardNodes.Family.MIND_OVER_MATTER);
			double baseDamage = Tuning.MAGIC_BOW_ARROW_BASE_DAMAGE
					+ mom * Tuning.MAGIC_BOW_ARROW_MOM_PER_RANK;

			Arrow arrow = new Arrow(serverLevel, player, new ItemStack(Items.ARROW), stack);
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
			arrow.setBaseDamage(baseDamage);
			arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * 3.0F, 1.0F);

			if (power == 1.0F) {
				arrow.setCritArrow(true);
			}

			serverLevel.addFreshEntity(arrow);
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F,
					1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
		}

		return true;
	}
}
