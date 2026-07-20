package com.archetypes;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The clash: an Unstoppable Force landing on an Immovable Object. The Colossus
 * Crusher's Siegebreaker knocks any raised shield aside; the Colossus
 * Protector's Immovable Object says the block cannot be broken. Two absolutes
 * meet, so neither is allowed to win — the blow stays blocked, the shield stays
 * up, and the physics bill comes due at the point between them.
 *
 * <p>Driven from {@code LivingEntityMixin#archetypes$clash}, a cancelling head
 * on the blocker's {@code hurtServer} — deliberately NOT from
 * {@code archetypes$unstoppableForce}, where it first lived. That hook sits
 * inside {@code applyItemBlocking}, and everything vanilla does to a blocked
 * blow happens after it returns: {@code hurtBlockingItem} charges the shield
 * the full damage, and {@code blockUsingItem} → {@code blockedByItem} calls
 * {@code knockback} on the blocker along the axis between the two players. That
 * last one is fatal to a clash — it runs after {@link #shove} and overwrites
 * it, so the Protector ended up drifting two blocks TOWARDS the Crusher while
 * the Crusher flew five back. A blow the clash eats has to stop before vanilla
 * starts resolving it, which means cancelling the hit outright.
 *
 * <h2>Why this is not an Explosion</h2>
 * Vanilla's {@code ServerExplosion} would be the obvious tool and is the wrong
 * one twice over. Its block pass is driven by blast resistance, which would take
 * obsidian and leave netherrack standing in the wrong places; the author asked
 * for a hardness ceiling instead ({@link Tuning#CLASH_MAX_HARDNESS}), which is
 * the pickaxe's ordering, not TNT's. And its push runs through
 * {@code EXPLOSION_KNOCKBACK_RESISTANCE}, which is exactly the attribute a
 * Colossus in full armour has been raising all game. So the blast is spelled
 * out by hand: our own block filter, our own velocity write.
 *
 * <h2>Re-entry</h2>
 * This runs from inside the blocker's {@code hurtServer}, and it calls
 * {@code hurtServer} on that same blocker. {@link #clashing} is the same guard
 * {@code ColossusSlayer.parrying} and {@code SlayerCombat.dancing} use for the
 * same reason: a payout that re-enters the hook that triggered it must find the
 * door shut. {@link #lastClashAt} plus the two ids is the second half of it —
 * one blow, one detonation, however many times the hook is reached for it.
 */
public final class ProtectorClash {
	/** True while a clash is paying itself out, so neither player's blast
	 * damage can detonate a second clash under the first. */
	private static boolean clashing;

	/**
	 * The tick and the pair of the last clash. A blow is one tick, so a repeat
	 * from the same two players on the same tick is the same blow arriving
	 * twice, not a second meeting. Ids rather than references: this outlives
	 * the entities and must not hold them.
	 */
	private static long lastClashAt = Long.MIN_VALUE;
	private static UUID lastAttacker;
	private static UUID lastBlocker;

	private ProtectorClash() {
	}

	/**
	 * Try to detonate. Returns true when the clash fired, which is the caller's
	 * signal to cancel the blow entirely: no damage, no shield durability, no
	 * Iron Spikes, no block knockback. False means nothing happened and the hit
	 * should resolve as usual.
	 *
	 * <p>The blocker must actually have a guard up. The old call site could
	 * take that for granted — it only ran from inside {@code applyItemBlocking}
	 * — and this one cannot, so it is tested here: an Immovable Object with the
	 * shield down is just a player, and a mace to the face is just a mace.
	 */
	public static boolean clash(final ServerPlayer attacker, final ServerPlayer blocker,
			final ServerLevel level) {
		if (clashing || attacker == blocker || blocker.getItemBlockingWith() == null) {
			return false;
		}

		if (!attacker.isAlive() || !blocker.isAlive()
				|| attacker.isSpectator() || blocker.isSpectator()
				|| attacker.level() != level || blocker.level() != level) {
			return false;
		}

		WeaponClass weapon = WeaponClass.of(attacker);

		if (weapon != WeaponClass.MACE && weapon != WeaponClass.HANDS) {
			return false;
		}

		if (ColossusCrusherNodes.rank(attacker, ColossusCrusherNodes.Family.SIEGEBREAKER) <= 0
				|| ColossusProtectorNodes.rank(blocker,
						ColossusProtectorNodes.Family.IMMOVABLE_OBJECT) <= 0) {
			return false;
		}

		long now = level.getGameTime();

		if (now == lastClashAt && attacker.getUUID().equals(lastAttacker)
				&& blocker.getUUID().equals(lastBlocker)) {
			return false;
		}

		lastClashAt = now;
		lastAttacker = attacker.getUUID();
		lastBlocker = blocker.getUUID();

		// The meeting point, not either player's feet: the crater belongs to
		// neither of them.
		Vec3 epicentre = attacker.position().add(blocker.position()).scale(0.5);

		shatter(level, attacker, epicentre);
		wound(level, attacker, blocker);
		shove(attacker, blocker);
		detonationFx(level, epicentre);

		ProcIndicators.send(attacker, SubTree.COLOSSUS_CRUSHER,
				ColossusCrusherNodes.Family.SIEGEBREAKER);
		ProcIndicators.send(blocker, SubTree.COLOSSUS_PROTECTOR,
				ColossusProtectorNodes.Family.IMMOVABLE_OBJECT);
		return true;
	}

	// ------------------------------------------------------------------
	// The crater.
	// ------------------------------------------------------------------

	/**
	 * The box: {@link Tuning#CLASH_RADIUS} x 2 blocks across in X and Z —
	 * ten — by {@link Tuning#CLASH_HEIGHT}, centred on the epicentre. The
	 * epicentre is a point between two players and almost never block-aligned,
	 * so the span is derived from it rather than from a block: floor of
	 * {@code x - radius + 0.5} is the first column whose centre is inside the
	 * box, and exactly {@code 2 x radius} columns follow it whatever the
	 * alignment. The same arithmetic on Y puts one layer under the feet and two
	 * at body height, which is what a ground detonation should take.
	 *
	 * <p>What may go is {@code EARTH_SHATTER}'s judgement, unchanged: air is
	 * nothing, a negative destroy speed is unbreakable by definition (bedrock,
	 * portal frame, the end portal itself) and is never counted, and anything
	 * over the ceiling is too tough to care. Water and lava have a destroy speed
	 * of 100 and fall out of the same test. {@code destroyBlock} with
	 * {@code dropResources} is how every block-breaking node in this mod removes
	 * a block: it drops the loot table, clears the block entity, and plays the
	 * block's own break effect, which doubles as our debris.
	 *
	 * @param breaker the attacker, handed to {@code destroyBlock} so the break
	 *     is attributed to a player and every server-side protection that hangs
	 *     off that attribution sees an owner
	 */
	private static void shatter(final ServerLevel level, final ServerPlayer breaker,
			final Vec3 epicentre) {
		int minX = Mth.floor(epicentre.x - Tuning.CLASH_RADIUS + 0.5);
		int minZ = Mth.floor(epicentre.z - Tuning.CLASH_RADIUS + 0.5);
		int minY = Mth.floor(epicentre.y - Tuning.CLASH_HEIGHT / 2.0 + 0.5);
		int span = Tuning.CLASH_RADIUS * 2;

		for (int dx = 0; dx < span; dx++) {
			for (int dz = 0; dz < span; dz++) {
				for (int dy = 0; dy < Tuning.CLASH_HEIGHT; dy++) {
					BlockPos pos = new BlockPos(minX + dx, minY + dy, minZ + dz);
					BlockState state = level.getBlockState(pos);

					if (state.isAir()) {
						continue;
					}

					float hardness = state.getDestroySpeed(level, pos);

					if (hardness < 0.0F || hardness > Tuning.CLASH_MAX_HARDNESS) {
						continue;
					}

					level.destroyBlock(pos, true, breaker);
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// The bill, paid by both.
	// ------------------------------------------------------------------

	/**
	 * {@link Tuning#CLASH_DAMAGE} to each of them, before armour: the blast is
	 * handed to {@code hurtServer} as ordinary explosion damage and their own
	 * armour, toughness and resistance take it down from there — which is the
	 * whole joke, since both of these builds are wearing rather a lot of it.
	 *
	 * <p>Each player is hurt by an explosion whose cause is the OTHER one, so
	 * kill credit and the death message land where they belong. The direct
	 * entity is deliberately left null: with it set, the source would read as a
	 * player standing right there swinging, and a shield still raised would put
	 * us straight back through {@code archetypes$unstoppableForce} — a blast
	 * that knocks aside the shield it just failed to break. Null direct entity
	 * makes it what it actually is, a detonation with an owner.
	 */
	private static void wound(final ServerLevel level, final ServerPlayer attacker,
			final ServerPlayer blocker) {
		DamageSource onAttacker = attacker.damageSources().explosion(null, blocker);
		DamageSource onBlocker = blocker.damageSources().explosion(null, attacker);

		clashing = true;

		try {
			attacker.hurtServer(level, onAttacker, Tuning.CLASH_DAMAGE);
			blocker.hurtServer(level, onBlocker, Tuning.CLASH_DAMAGE);
		} finally {
			clashing = false;
		}
	}

	/**
	 * Five blocks apart, each along the line between them, and nothing gets to
	 * opt out. Written as a velocity rather than through {@code knockback} or an
	 * explosion's own push because both of these builds are wearing the answer
	 * to those: {@code KNOCKBACK_RESISTANCE} and
	 * {@code EXPLOSION_KNOCKBACK_RESISTANCE} are what netherite armour and the
	 * Crusher's own Clinch raise, and a shove that both participants can shrug
	 * off is not a clash. {@code setDeltaMovement} consults neither, and
	 * {@code hurtMarked} is what publishes forced motion — {@code ServerEntity}
	 * sends the packet to tracking players AND to the moved entity itself, so
	 * the client stops predicting and goes where it is put.
	 *
	 * <p>After the damage, never before: {@code hurtServer} ends in
	 * {@code dealDefaultKnockback}, and anything written first would be
	 * overwritten by it.
	 *
	 * <p>Velocity is SET, not added, so a clash met mid-sprint or mid-leap is
	 * still exactly one send-off. {@link Tuning#CLASH_LIFT} breaks ground
	 * contact so the horizontal drag figure behind {@link Tuning#CLASH_PUSH}
	 * actually holds. Two players occupying the same spot cannot give a
	 * direction, so that degenerate case takes a fixed axis rather than
	 * normalising a zero vector.
	 */
	private static void shove(final ServerPlayer attacker, final ServerPlayer blocker) {
		Vec3 apart = blocker.position().subtract(attacker.position());
		Vec3 flat = new Vec3(apart.x, 0.0, apart.z);
		Vec3 away = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();

		launch(blocker, away);
		launch(attacker, away.scale(-1.0));
	}

	private static void launch(final ServerPlayer player, final Vec3 direction) {
		player.setDeltaMovement(direction.x * Tuning.CLASH_PUSH, Tuning.CLASH_LIFT,
				direction.z * Tuning.CLASH_PUSH);
		player.hurtMarked = true;
	}

	// ------------------------------------------------------------------
	// The tell.
	// ------------------------------------------------------------------

	/**
	 * A real detonation, staged the way {@code CrusherActives.slamFx} stages a
	 * slam but louder: vanilla's own explosion note at the volume vanilla uses
	 * for TNT (4.0, which is what carries it 64 blocks), the mace's heavy smash
	 * under it for weight, and the anvil on top — the Immovable Object's own
	 * voice, since half of this belongs to the player who did not move.
	 *
	 * <p>One {@code EXPLOSION_EMITTER}, which is the big one and multiplies
	 * badly, plus a spread of the small {@code EXPLOSION} puffs to fill the
	 * crater. Both go out through {@code sendParticles}, and the sounds with a
	 * null player, so everyone in range sees and hears it rather than just the
	 * two who caused it.
	 */
	private static void detonationFx(final ServerLevel level, final Vec3 epicentre) {
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
				epicentre.x, epicentre.y + 1.0, epicentre.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION,
				epicentre.x, epicentre.y + 1.0, epicentre.z, 24,
				Tuning.CLASH_RADIUS * 0.5, 0.8, Tuning.CLASH_RADIUS * 0.5, 0.0);

		level.playSound(null, epicentre.x, epicentre.y, epicentre.z,
				SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0F, 0.6F);
		level.playSound(null, epicentre.x, epicentre.y, epicentre.z,
				SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0F, 0.5F);
		level.playSound(null, epicentre.x, epicentre.y, epicentre.z,
				SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2F, 0.6F);
	}
}
