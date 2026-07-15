package com.archetypes;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The Slayer's per-tick work:
 *
 * <ul>
 * <li><b>Claymore stance</b> — Immovable's knockback resistance and Heavy
 * Blows' swing-speed cost live as transient attribute modifiers, applied while
 * a claymore is held and stripped the moment it is not.</li>
 * <li><b>Bladestorm</b> — the channel: six half-damage volleys over three
 * seconds, cancelled early if the sword leaves the hand.</li>
 * <li><b>Bleeds</b> — Rend's open wounds, one damage tick a second.</li>
 * </ul>
 */
public final class SlayerTicker {
	private static final Identifier KBRES_ID = Archetypes.id("claymore_kbres");
	private static final Identifier HEAVY_SPEED_ID = Archetypes.id("claymore_heavy_speed");

	private record Bleed(ServerPlayer source, int rank) {
	}

	/** Wounded entity -> remaining ticks are tracked in the paired int. */
	private static final Map<LivingEntity, int[]> BLEED_TICKS = new IdentityHashMap<>();
	private static final Map<LivingEntity, Bleed> BLEEDS = new IdentityHashMap<>();

	private SlayerTicker() {
	}

	public static void startBleed(final LivingEntity victim, final ServerPlayer source, final int rank) {
		BLEEDS.put(victim, new Bleed(source, rank));
		BLEED_TICKS.put(victim, new int[] { Tuning.BLEED_DURATION_TICKS });
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tickStance(player);
				tickBladestorm(player);
			}

			tickBleeds();
		});
	}

	private static void tickStance(final ServerPlayer player) {
		var owned = NodePurchases.owned(player, SubTree.SLAYER);
		boolean holding = ModItems.isClaymore(player.getMainHandItem());

		int kbres = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.KBRES);
		applyStanceModifier(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), KBRES_ID,
				holding && kbres > 0, kbres * Tuning.KBRES_PER_RANK,
				AttributeModifier.Operation.ADD_VALUE);

		int heavy = SlayerNodes.rank(SubTree.SLAYER, owned, SlayerNodes.Family.HEAVY);
		applyStanceModifier(player.getAttribute(Attributes.ATTACK_SPEED), HEAVY_SPEED_ID,
				holding && heavy > 0, -heavy * Tuning.HEAVY_PER_RANK,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void applyStanceModifier(final AttributeInstance attribute, final Identifier id,
			final boolean should, final double value, final AttributeModifier.Operation operation) {
		if (attribute == null) {
			return;
		}

		if (should && !attribute.hasModifier(id)) {
			attribute.addTransientModifier(new AttributeModifier(id, value, operation));
		} else if (!should && attribute.hasModifier(id)) {
			attribute.removeModifier(id);
		}
	}

	private static void tickBladestorm(final ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;
		Long end = target.getAttached(ModAttachments.BLADESTORM_END);

		if (end == null) {
			return;
		}

		long now = player.level().getGameTime();

		// Over, or the sword left the hand: the storm dies with its blade.
		if (now >= end || !ModItems.isSword(player.getMainHandItem())) {
			target.removeAttached(ModAttachments.BLADESTORM_END);
			return;
		}

		if ((end - now) % Tuning.BLADESTORM_VOLLEY_PERIOD != 0) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE)
				* Tuning.BLADESTORM_DAMAGE_FACTOR);

		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F,
				0.9F + level.getRandom().nextFloat() * 0.3F);

		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(Tuning.BLADESTORM_RADIUS, 1.0, Tuning.BLADESTORM_RADIUS),
				entity -> entity != player && entity.isAlive() && !entity.isSpectator())) {
			victim.hurtServer(level, player.damageSources().playerAttack(player), damage);
		}

		// A ring of sweeps marks each volley.
		for (int i = 0; i < 6; i++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			double distance = 1.0 + level.getRandom().nextDouble() * 1.5;
			level.sendParticles(ParticleTypes.SWEEP_ATTACK,
					player.getX() + Math.cos(angle) * distance,
					player.getY() + 0.8 + level.getRandom().nextDouble() * 0.6,
					player.getZ() + Math.sin(angle) * distance,
					1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	private static void tickBleeds() {
		Iterator<Map.Entry<LivingEntity, Bleed>> iterator = BLEEDS.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<LivingEntity, Bleed> entry = iterator.next();
			LivingEntity victim = entry.getKey();
			int[] ticks = BLEED_TICKS.get(victim);

			if (victim.isRemoved() || !victim.isAlive() || ticks == null || ticks[0] <= 0) {
				iterator.remove();
				BLEED_TICKS.remove(victim);
				continue;
			}

			ticks[0]--;

			if (ticks[0] % 20 != 0) {
				continue;
			}

			ServerLevel level = (ServerLevel) victim.level();
			ServerPlayer source = entry.getValue().source();
			victim.hurtServer(level,
					source.isAlive() ? victim.damageSources().playerAttack(source)
							: victim.damageSources().generic(),
					entry.getValue().rank());
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
					victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
					2, 0.2, 0.2, 0.2, 0.0);
		}
	}
}
