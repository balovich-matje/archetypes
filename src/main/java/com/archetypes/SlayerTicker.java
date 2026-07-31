package com.archetypes;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.archetypes.platform.ArchetypeStore;

// STAGE 6 — the loader-event helpers live in `com.archetypes.platform`, the one package
// allowed to name loader API (conventions §5g). Only this import and the registration line
// below fork; the tick body is one implementation on all seven nodes.
//? if fabric {
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
//?} elif neoforge {
/*import com.archetypes.platform.NeoForgeEvents;
*///?} elif forge {
/*import com.archetypes.platform.ForgeEvents;
*///?}
import net.minecraft.world.entity.Entity;
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
 * <li><b>Greatsword stance</b> — Immovable's knockback resistance and Heavy
 * Blows' swing-speed cost live as transient attribute modifiers, applied while
 * a greatsword is held and stripped the moment it is not.</li>
 * <li><b>Bladestorm</b> — the channel: six half-damage volleys over three
 * seconds, cancelled early if the sword leaves the hand.</li>
 * <li><b>Bleeds</b> — Rend's open wounds, one damage tick a second.</li>
 * </ul>
 */
public final class SlayerTicker {
	private static final Identifier KBRES_ID = Archetypes.id("greatsword_kbres");
	private static final Identifier HEAVY_SPEED_ID = Archetypes.id("greatsword_heavy_speed");

	private record Bleed(ServerPlayer source, int rank) {
	}

	/**
	 * True only while a Rend pulse is resolving. Two things read it: the
	 * knockback funnel, because a wound should not shove (author's call —
	 * a bleeding mob was being nudged away once a second by its own wound),
	 * and nothing else needs to, because the pulse is not a swing and
	 * {@code MeleeSwing} already keeps the on-hit passives off it.
	 */
	private static boolean bleeding;

	/** Wounded entity -> remaining ticks are tracked in the paired int. */
	private static final Map<LivingEntity, int[]> BLEED_TICKS = new IdentityHashMap<>();
	private static final Map<LivingEntity, Bleed> BLEEDS = new IdentityHashMap<>();

	private SlayerTicker() {
	}

	/** Whether a Rend pulse is landing right now — the knockback funnel's
	 * question, same shape as {@code BlizzardZones.isPulsing}. */
	public static boolean isBleeding() {
		return bleeding;
	}

	public static void startBleed(final LivingEntity victim, final ServerPlayer source, final int rank) {
		BLEEDS.put(victim, new Bleed(source, rank));
		BLEED_TICKS.put(victim, new int[] { Tuning.BLEED_DURATION_TICKS });
	}

	public static void initialize() {
		// Registration only; the body below is shared. A loader helper fires its consumer ONCE
		// per server tick, at the END of it, with the `MinecraftServer` — the END_SERVER_TICK
		// contract, which is what R-20 says a re-rooted event has to reproduce rather than
		// merely fire somewhere plausible.
		//? if fabric {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
		//?} elif neoforge {
		/*NeoForgeEvents.endServerTick(server -> {
		*///?} elif forge {
		/*ForgeEvents.endServerTick(server -> {
		*///?}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tickStance(player);
				tickBladestorm(player);
			}

			tickBleeds();
		});
	}

	private static void tickStance(final ServerPlayer player) {
		var owned = NodePurchases.owned(player, SubTree.SLAYER);
		boolean holding = ModItems.isGreatsword(player.getMainHandItem());

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

		//? if >=1.21 {
		if (should && !attribute.hasModifier(id)) {
			attribute.addTransientModifier(new AttributeModifier(id, value, operation));
		} else if (!should && attribute.hasModifier(id)) {
			attribute.removeModifier(id);
		}
		//?} else {
		/*if (should && !LegacyAttributes.has(attribute, id)) {
			attribute.addTransientModifier(LegacyAttributes.modifier(id, value, operation));
		} else if (!should && LegacyAttributes.has(attribute, id)) {
			LegacyAttributes.remove(attribute, id);
		}
		*///?}
	}

	private static void tickBladestorm(final ServerPlayer player) {
		final Entity target = player;
		Long end = ArchetypeStore.INSTANCE.get(target, ModState.BLADESTORM_END);

		if (end == null) {
			return;
		}

		long now = player.level().getGameTime();

		// Over, or the sword left the hand: the storm dies with its blade.
		if (now >= end || !ModItems.isSword(player.getMainHandItem())) {
			ArchetypeStore.INSTANCE.remove(target, ModState.BLADESTORM_END);
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		long remaining = end - now;

		// Sound and sweeps pulse twice as often as the damage, so the storm
		// feels continuous rather than metronomic: riptide whoosh layered
		// under pitched sweeps on the half-beats.
		if (remaining % (Tuning.BLADESTORM_VOLLEY_PERIOD / 2) == 0) {
			// No arm swing: on this branch the Player Animation Library pose
			// owns the body during the channel.
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F,
					0.8F + level.getRandom().nextFloat() * 0.5F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					/*? if >=1.21 {*/SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.7F,
					/*?} else *///SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.7F,
					1.2F + level.getRandom().nextFloat() * 0.3F);

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

		if (remaining % Tuning.BLADESTORM_VOLLEY_PERIOD != 0) {
			return;
		}

		float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE)
				* Tuning.BLADESTORM_DAMAGE_FACTOR);

		var previousSwing = MeleeSwing.begin(player);

		try {
			for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
					player.getBoundingBox().inflate(Tuning.BLADESTORM_RADIUS, 1.0,
							Tuning.BLADESTORM_RADIUS),
					entity -> entity != player && entity.isAlive() && !entity.isSpectator())) {
				//? if >=1.21.2 {
				victim.hurtServer(level, player.damageSources().playerAttack(player), damage);
				//?} else {
				/*victim.hurt(player.damageSources().playerAttack(player), damage);
				*///?}
			}
		} finally {
			MeleeSwing.end(previousSwing);
		}
	}

	/**
	 * Every running bleed, in two passes: the walk that ages them, then the
	 * damage.
	 *
	 * <p>They cannot be one pass, and the crash that proved it is worth
	 * recording. A bleed pulse is dealt as a {@code playerAttack} from the
	 * sword that opened the wound, which is indistinguishable from a real swing
	 * to everything downstream — so a pulse landing on a bleeding mob re-enters
	 * {@code SlayerCombat}'s hit handler, and Blade Dance's lash then starts a
	 * bleed on a DIFFERENT nearby creature. That is a new key in the map this
	 * loop is iterating, and {@code IdentityHashMap} answers a new key mid-walk
	 * with a {@code ConcurrentModificationException} that takes the server tick
	 * loop down with it.
	 *
	 * <p>A re-entry flag would have stopped that one path (it is what
	 * {@code NightForm}'s Feast does). Collecting first is stronger: it holds
	 * for anything the damage sets off, including whatever gets added to this
	 * mod later, because nothing at all runs while the map is open.
	 */
	private static void tickBleeds() {
		if (BLEEDS.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<LivingEntity, Bleed>> iterator = BLEEDS.entrySet().iterator();
		List<Map.Entry<LivingEntity, Bleed>> due = new ArrayList<>();

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

			if (ticks[0] % 20 == 0) {
				due.add(Map.entry(victim, entry.getValue()));
			}
		}

		for (Map.Entry<LivingEntity, Bleed> entry : due) {
			LivingEntity victim = entry.getKey();

			// Re-checked: an earlier pulse in this same batch may have killed
			// it, and a bleed pulse on a corpse would credit a second kill.
			if (victim.isRemoved() || !victim.isAlive()) {
				continue;
			}

			ServerLevel level = (ServerLevel) victim.level();
			ServerPlayer source = entry.getValue().source();
			bleeding = true;

			try {
				//? if >=1.21.2 {
				victim.hurtServer(level,
						source.isAlive() ? victim.damageSources().playerAttack(source)
								: victim.damageSources().generic(),
						entry.getValue().rank());
				//?} else {
				/*victim.hurt(
						source.isAlive() ? victim.damageSources().playerAttack(source)
								: victim.damageSources().generic(),
						entry.getValue().rank());
				*///?}
			} finally {
				bleeding = false;
			}
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
					victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
					2, 0.2, 0.2, 0.2, 0.0);
		}
	}
}
