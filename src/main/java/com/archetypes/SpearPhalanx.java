package com.archetypes;

import java.util.ArrayList;
import java.util.List;

import com.archetypes.mixin.DisplayAccessor;
import com.archetypes.mixin.ItemDisplayAccessor;
import com.mojang.math.Transformation;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Ground Slam, reworked: two spearmen instead of a shockwave.
 *
 * <h2>What changed and why</h2>
 * The capstone used to turn the bash into a ring — same hit, larger circle. It
 * read as "the bash, but more", which is a poor thing for a capstone to be
 * when the node opposite it is Bulwark and the tree's other half is already
 * about widening the bash. So the ring is gone. Ground Slam now plants two
 * phantom spearmen at the caster's shoulders and all three thrust forward
 * together: a formation, not an explosion, and it points the ability the same
 * way the caster is pointing instead of at everything around them.
 *
 * <h2>The hit</h2>
 * One hit per victim, of the bash's damage <em>plus</em> a spear thrust, and it
 * goes through {@link MeleeSwing} so the rest of the mod sees a real swing —
 * the same reasoning as {@code SlayerActives.resolve}: a capstone's blow that
 * skipped the funnel would be the one attack in the tree that armour handled
 * differently, and Specialities' multipliers ride the same funnel.
 *
 * <p>The stab is scaled off {@code ATTACK_DAMAGE}, which is the item, Strength
 * and the tree together — the same number the Slayer's own capstone reads.
 *
 * <h2>No spear, no phalanx</h2>
 * The node is gated on actually carrying a spear, and the bash falls back to
 * its ordinary front-cone shove when the caster is not. That is deliberately
 * not a dead capstone: it is a loadout the node asks for, on the same column
 * as Spearwall, and the ability the player keeps when they ignore it is the
 * one they already had.
 *
 * <h2>The phantoms are decoration and nothing else</h2>
 * Gameplay resolves in full at cast, server-side, the way the rest of
 * {@code ShieldBash} does. The two {@link Display.ItemDisplay} entities carry
 * no damage, no collision and no state anyone reads — they exist to be looked
 * at, and they are swept on a timer by {@link ProtectorTicker}. Keeping them
 * that way is what makes them safe: a phantom that vanished early, or that a
 * client never received, cannot cost anybody a hit.
 *
 * <p>Real fake-player entities were never on the table, and Player Animation
 * Library does not help here either — its whole API
 * ({@code PlayerAnimationAccess.getPlayerAnimManager}, {@code
 * PlayerAnimationFactory.invoke}) is keyed on {@code Avatar}, an actual player
 * entity, so it animates players that exist and offers nothing for conjuring
 * one that does not.
 */
public final class SpearPhalanx {
	/**
	 * Command tag carried by every phantom so {@code EntityMixin} can veto
	 * {@code shouldBeSaved} — a phantom written to a region file by an autosave
	 * landing inside its 10-tick life would outlive the ticker's memory of it
	 * and float forever.
	 */
	public static final String PHANTOM_TAG = "archetypes_phantom";

	/**
	 * A spawned phantom and the two gametimes that shape its short life.
	 * {@code stabbed} is a latch, not an equality check against {@code stabAt}:
	 * a lagged server tick can skip the exact gametime, and the thrust must
	 * fire on the next tick rather than never.
	 */
	private static final class Phantom {
		final Display.ItemDisplay display;
		final long stabAt;
		final long removeAt;
		boolean stabbed;

		Phantom(final Display.ItemDisplay display, final long stabAt, final long removeAt) {
			this.display = display;
			this.stabAt = stabAt;
			this.removeAt = removeAt;
		}

		Display.ItemDisplay display() {
			return display;
		}

		long stabAt() {
			return stabAt;
		}

		long removeAt() {
			return removeAt;
		}
	}

	private static final List<Phantom> LIVE = new ArrayList<>();

	private SpearPhalanx() {
	}

	/** The spear this player would fight with, or null if they carry none. */
	public static ItemStack spear(final ServerPlayer player) {
		ItemStack main = player.getMainHandItem();

		if (main.is(ItemTags.SPEARS)) {
			return main;
		}

		ItemStack off = player.getOffhandItem();
		return off.is(ItemTags.SPEARS) ? off : null;
	}

	/**
	 * The formation's blow. Called from {@code ShieldBash} in place of the
	 * normal target loop, so a victim takes this or the bash, never both.
	 *
	 * @param bashDamage the bash's own damage, already shaped by Shield Slam
	 *                   and the Concussive Blow penalty
	 */
	public static void execute(final ServerPlayer player, final ServerLevel level,
			final ItemStack spear, final float bashDamage, final int wide) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
		double range = Tuning.phalanxRange(wide);

		float stab = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE)
				* Tuning.PHALANX_STAB_MULTIPLIER);
		float damage = bashDamage + stab;

		List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().expandTowards(flat.scale(range)).inflate(1.2, 0.5, 1.2),
				entity -> entity != player && entity.isAlive() && !entity.isSpectator()
						&& inFront(player, entity, flat));

		// One swing, opened once around every victim: three spears landing
		// together are one blow, and opening a swing per victim would let a
		// per-swing passive fire once per body.
		var previousSwing = MeleeSwing.begin(player);

		try {
			for (LivingEntity victim : victims) {
				victim.hurtServer(level, player.damageSources().playerAttack(player), damage);
			}
		} finally {
			MeleeSwing.end(previousSwing);
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);

		stabTrail(level, player, flat, range);
		spawnPhantom(level, player, spear, flat, true);
		spawnPhantom(level, player, spear, flat, false);
	}

	/**
	 * The crit cloud along the line the spears cover, thickest where the
	 * flanking pair actually reach rather than evenly down the middle — the
	 * particles ARE the hitbox as far as a player reading the ability goes, so
	 * they are drawn from the same range the victim query used.
	 */
	private static void stabTrail(final ServerLevel level, final ServerPlayer player,
			final Vec3 flat, final double range) {
		Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
		double y = player.getY() + player.getBbHeight() * 0.55;

		for (int lane = -1; lane <= 1; lane += 2) {
			Vec3 origin = player.position().add(right.scale(lane * Tuning.PHALANX_FLANK_OFFSET));

			for (int i = 1; i <= Tuning.PHALANX_TRAIL_POINTS; i++) {
				double along = range * i / Tuning.PHALANX_TRAIL_POINTS;
				level.sendParticles(ParticleTypes.CRIT,
						origin.x + flat.x * along, y, origin.z + flat.z * along,
						2, 0.08, 0.08, 0.08, 0.0);
			}
		}
	}

	/**
	 * One phantom, at a shoulder.
	 *
	 * <p>It is spawned drawn BACK, and {@link #tick} pushes it forward a beat
	 * later with an interpolation duration set. That two-step is the whole
	 * animation: a Display lerps from the transformation it is currently
	 * showing to the one it is given, so a single transformation at spawn would
	 * simply appear in its final place and never move.
	 */
	private static void spawnPhantom(final ServerLevel level, final ServerPlayer player,
			final ItemStack spear, final Vec3 flat, final boolean left) {
		Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level,
				net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);

		if (display == null) {
			return;
		}

		Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
		Vec3 at = player.position()
				.add(right.scale((left ? -1 : 1) * Tuning.PHALANX_FLANK_OFFSET))
				.add(0.0, player.getBbHeight() * 0.5, 0.0);

		display.setPos(at.x, at.y, at.z);
		display.setYRot(player.getYRot());
		display.setXRot(player.getXRot());
		display.addTag(PHANTOM_TAG);

		ItemDisplayAccessor item = (ItemDisplayAccessor) display;
		item.archetypes$setItemStack(spear.copyWithCount(1));
		item.archetypes$setItemTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);

		DisplayAccessor shape = (DisplayAccessor) display;
		shape.archetypes$setViewRange(Tuning.PHALANX_VIEW_RANGE);
		shape.archetypes$setInterpolationDelay(0);
		shape.archetypes$setInterpolationDuration(0);
		shape.archetypes$setTransformation(transform(-Tuning.PHALANX_DRAW_BACK));

		level.addFreshEntity(display);

		long now = level.getGameTime();
		LIVE.add(new Phantom(display, now + Tuning.PHALANX_WINDUP_TICKS,
				now + Tuning.PHALANX_LIFE_TICKS));
	}

	/**
	 * Local-space translation along the phantom's own facing.
	 *
	 * <p>+Z is forward here because the Display's yaw is already the caster's:
	 * the entity is turned to face the same way, and the transformation runs
	 * inside that. Which sign reads as "thrust" on screen is the one thing in
	 * this class that a headless server cannot answer — see the note in
	 * ARCHITECTURE.
	 */
	private static Transformation transform(final float forward) {
		return new Transformation(
				new Vector3f(0.0F, 0.0F, forward),
				new Quaternionf(),
				new Vector3f(1.0F, 1.0F, 1.0F),
				new Quaternionf());
	}

	/**
	 * Drops any phantom that does not belong to this server.
	 *
	 * <p>{@link #LIVE} is static and an integrated server is torn down and
	 * rebuilt inside one JVM every time a single-player world is left, so
	 * without this a phantom spawned in the last ten ticks of a world would be
	 * ticked against the next one. Called on stop rather than checked per tick:
	 * the common case is an empty list.
	 */
	public static void forget() {
		LIVE.clear();
	}

	/** Drives the thrust and sweeps the dead. Called once a server tick. */
	public static void tick(final MinecraftServer server) {
		if (LIVE.isEmpty()) {
			return;
		}

		var iterator = LIVE.iterator();

		while (iterator.hasNext()) {
			Phantom phantom = iterator.next();
			Display.ItemDisplay display = phantom.display();

			if (display.isRemoved()) {
				iterator.remove();
				continue;
			}

			long now = display.level().getGameTime();

			if (!phantom.stabbed && now >= phantom.stabAt()) {
				phantom.stabbed = true;
				DisplayAccessor shape = (DisplayAccessor) display;
				shape.archetypes$setInterpolationDelay(0);
				shape.archetypes$setInterpolationDuration(Tuning.PHALANX_STAB_TICKS);
				shape.archetypes$setTransformation(transform(Tuning.PHALANX_THRUST));
			}

			if (now >= phantom.removeAt()) {
				display.discard();
				iterator.remove();
			}
		}
	}

	/** In front: the same >60-degree cone the bash and Decimate both use. */
	private static boolean inFront(final ServerPlayer player, final LivingEntity target,
			final Vec3 flat) {
		Vec3 toTarget = target.position().subtract(player.position());
		Vec3 level = new Vec3(toTarget.x, 0.0, toTarget.z);

		if (level.lengthSqr() < 1.0E-4) {
			return true;
		}

		return flat.dot(level.normalize()) > 0.5;
	}
}
