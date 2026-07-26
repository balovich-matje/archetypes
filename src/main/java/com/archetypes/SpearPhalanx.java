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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Spear Phalanx (the node is still {@code Family.GROUND_SLAM}): two spears
 * instead of a shockwave.
 *
 * <h2>What changed and why</h2>
 * The capstone used to turn the bash into a ring — same hit, larger circle. It
 * read as "the bash, but more", which is a poor thing for a capstone to be
 * when the node opposite it is Bulwark and the tree's other half is already
 * about widening the bash. So the ring is gone. The bash now plants a spear at
 * each of the caster's shoulders and all three thrust forward together: a
 * formation, not an explosion, and it points the ability the same way the
 * caster is pointing instead of at everything around them.
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
 * <h2>Spears, not spearmen</h2>
 * An earlier pass conjured two clones of the caster out of clientbound packets
 * and let vanilla's own arm pose aim their weapons. The in-game pass killed it:
 * a clone's skin comes from the profile's signed {@code textures} property, and
 * an offline account has none, so every spearman on a dev or LAN launch wore a
 * random default skin instead of the caster's. What is left is the two things
 * the formation was ever about — a spear at each shoulder, and a thrust.
 *
 * <p>So the pair are {@link Display.ItemDisplay} entities: no hitbox (a Display
 * carries a zero-size bounding box and {@code noPhysics}), no AI, and
 * {@code Display.hurtServer} is a hard {@code false}, so nothing can be hit,
 * pushed or killed. They carry no gameplay at all — the hit already resolved at
 * cast, server-side, the way the rest of {@code ShieldBash} does, which is what
 * makes a spear that vanished early or that a client never received unable to
 * cost anybody a hit.
 *
 * <p>They are real entities, though, and that is the one failure this design
 * owns: an {@code ItemDisplay} serialises for its whole short life, so an
 * autosave landing inside the window would write it to a region file and leave
 * it hanging there forever. {@code EntityMixin} vetoes {@code shouldBeSaved}
 * for anything wearing {@link #PHANTOM_TAG}.
 *
 * <p>Player Animation Library is no help here either: its whole API
 * ({@code PlayerAnimationAccess.getPlayerAnimManager},
 * {@code PlayerAnimationFactory.invoke}) is keyed on {@code Avatar} — an actual
 * player entity — so it animates players that exist and offers nothing for a
 * floating weapon.
 */
public final class SpearPhalanx {
	/**
	 * Command tag carried by every spear so {@code EntityMixin} can veto
	 * {@code shouldBeSaved} — a spear written to a region file by an autosave
	 * landing inside its 12-tick life would outlive the ticker's memory of it
	 * and float forever.
	 */
	public static final String PHANTOM_TAG = "archetypes_phantom";

	/**
	 * A cast in flight: the two spears and the two gametimes that shape their
	 * short life.
	 *
	 * <p>{@code stabbed} is a latch, not an equality check against
	 * {@code stabAt}: a lagged server tick can skip the exact gametime, and the
	 * thrust must fire on the next tick rather than never.
	 *
	 * <p>One latch for the pair rather than one each, because the pair thrust
	 * together — a formation whose two halves lunged on different ticks would
	 * read as two spears, which is the thing this node is not.
	 */
	private static final class Formation {
		final List<Display.ItemDisplay> spears;
		final long stabAt;
		final long removeAt;
		boolean stabbed;

		Formation(final List<Display.ItemDisplay> spears, final long stabAt, final long removeAt) {
			this.spears = spears;
			this.stabAt = stabAt;
			this.removeAt = removeAt;
		}
	}

	private static final List<Formation> LIVE = new ArrayList<>();

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
		formUp(level, player, spear, flat);
	}

	/**
	 * The crit cloud along the line the spears cover, thickest where the
	 * flanking pair actually reach rather than evenly down the middle — the
	 * particles ARE the hitbox as far as a player reading the ability goes, so
	 * they are drawn from the same range the victim query used, at the same
	 * height the spears are planted at.
	 */
	private static void stabTrail(final ServerLevel level, final ServerPlayer player,
			final Vec3 flat, final double range) {
		Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
		double y = player.getY() + player.getBbHeight() * Tuning.PHALANX_SHOULDER_HEIGHT;

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
	 * Plants the pair, one at each shoulder.
	 *
	 * <p>Both are spawned drawn BACK, and {@link #tick} pushes them forward a
	 * beat later with an interpolation duration set. That two-step is the whole
	 * animation: a Display lerps from the transformation it is currently showing
	 * to the one it is given, so a single transformation at spawn would simply
	 * appear in its final place and never move.
	 */
	private static void formUp(final ServerLevel level, final ServerPlayer player,
			final ItemStack spear, final Vec3 flat) {
		Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
		List<Display.ItemDisplay> spears = new ArrayList<>(2);

		for (int lane = -1; lane <= 1; lane += 2) {
			Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level,
					EntitySpawnReason.TRIGGERED);

			if (display == null) {
				continue;
			}

			Vec3 at = player.position()
					.add(right.scale(lane * Tuning.PHALANX_FLANK_OFFSET))
					.add(0.0, player.getBbHeight() * Tuning.PHALANX_SHOULDER_HEIGHT, 0.0);

			display.setPos(at.x, at.y, at.z);
			// The caster's yaw, so the formation points where they are pointing
			// and so the pose below can talk about "forward" at all. Their PITCH
			// deliberately is not copied: the display's own xRot is a second
			// rotation the renderer applies before ours, so a cast aimed at the
			// sky would tilt the whole formation up with it and the 47 degrees
			// would stop being measured from the horizon.
			display.setYRot(player.getYRot());
			display.setXRot(0.0F);
			display.addTag(PHANTOM_TAG);

			ItemDisplayAccessor item = (ItemDisplayAccessor) display;
			item.archetypes$setItemStack(spear.copyWithCount(1));
			item.archetypes$setItemTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);

			DisplayAccessor shape = (DisplayAccessor) display;
			shape.archetypes$setViewRange(Tuning.PHALANX_VIEW_RANGE);
			shape.archetypes$setInterpolationDelay(0);
			shape.archetypes$setInterpolationDuration(0);
			shape.archetypes$setTransformation(pose(-Tuning.PHALANX_DRAW_BACK));

			level.addFreshEntity(display);
			spears.add(display);
		}

		if (spears.isEmpty()) {
			return;
		}

		// One clock for spawn and sweep: the overworld's, which every dimension
		// shares, so a formation cast in the Nether is not compared against a
		// gametime read somewhere else.
		long now = level.getServer().overworld().getGameTime();
		LIVE.add(new Formation(spears,
				now + Tuning.PHALANX_WINDUP_TICKS, now + Tuning.PHALANX_LIFE_TICKS));
	}

	/**
	 * The pose of one spear: depressed {@link Tuning#PHALANX_SPEAR_ANGLE_DEGREES}
	 * below the horizon along the caster's facing, slid {@code along} its own
	 * shaft.
	 *
	 * <h2>The frame</h2>
	 * A {@code Transformation} composes as {@code T · L · S · R} (see
	 * {@code Transformation.compose}), so the left rotation turns the model and
	 * the translation is applied OUTSIDE it, in the display's own unrotated
	 * axes. Those axes are readable straight off
	 * {@code DisplayRenderer.calculateOrientation}: a FIXED billboard — the
	 * default, and what these are — is posed with
	 * {@code rotationYXZ(-yRot, +xRot, 0)}, and {@code Ry(-yRot)} carries local
	 * {@code +Z} onto {@code (-sin yRot, 0, cos yRot)}, which is Minecraft's own
	 * facing vector for that yaw. So in this frame {@code +Z} is FORWARD,
	 * {@code +Y} is UP, {@code +X} is the caster's left, and a POSITIVE rotation
	 * about {@code +X} tips forward down — the same sign the renderer itself
	 * uses to spend a positive (downward) Minecraft pitch.
	 *
	 * <h2>The base pose</h2>
	 * {@code ItemDisplayRenderer.submitInner} spins the item 180 degrees about
	 * {@code +Y} and then draws it under its {@code THIRD_PERSON_RIGHT_HAND}
	 * display transform. For a spear that transform is
	 * {@code rotation [5, 270, -40]} with {@code scale [1.7, 1.7, 0.85]}, and
	 * composed as {@code Rx(5)·Ry(270)·Rz(-40)} against the sprite's own
	 * 45-degree diagonal (tip at the texture's top-left, butt at its
	 * bottom-right) it lands the shaft on exactly {@code +Y}, tip up. The Y-flip
	 * cannot disturb that, because {@code +Y} is the one axis it fixes. So the
	 * spear arrives pointing straight UP and this is the only rotation acting on
	 * it.
	 *
	 * <h2>The rotation</h2>
	 * {@code Rx(θ)} carries {@code +Y} onto {@code (0, cos θ, sin θ)}. At
	 * {@code θ = 90°} that is {@code (0, 0, 1)} — level, pointing forward — so
	 * the depression wanted is simply the next {@code 47°}:
	 *
	 * <pre>L = Rx(90° + 47°)  ⇒  +Y ↦ (0, −sin 47°, +cos 47°)</pre>
	 *
	 * which is forward and 47 degrees under the horizon, since
	 * {@code atan2(sin 47°, cos 47°) = 47°}. Writing the angle as
	 * {@code 90 + PHALANX_SPEAR_ANGLE_DEGREES} rather than as one baked number
	 * is the point: the 90 is the base pose, the 47 is the tuning knob, and
	 * nothing has to be re-derived to move the knob.
	 *
	 * <h2>The thrust</h2>
	 * {@code along} slides the spear down that same vector rather than along
	 * {@code +Z}, so the lunge runs up the shaft and reads as a stab instead of
	 * a depressed spear sliding level. Negative is drawn back, positive is
	 * thrust — signed in the display's frame, where {@code +Z} is provably the
	 * caster's facing, so this is arithmetic rather than a guess a headless
	 * server cannot check.
	 */
	private static Transformation pose(final float along) {
		double depression = Math.toRadians(Tuning.PHALANX_SPEAR_ANGLE_DEGREES);
		Vector3f shaft = new Vector3f(0.0F,
				(float) -Math.sin(depression), (float) Math.cos(depression));

		return new Transformation(
				shaft.mul(along, new Vector3f()),
				new Quaternionf().rotationX((float) (Math.PI / 2.0 + depression)),
				new Vector3f(1.0F, 1.0F, 1.0F),
				new Quaternionf());
	}

	/**
	 * Drops any formation that does not belong to this server.
	 *
	 * <p>{@link #LIVE} is static and an integrated server is torn down and
	 * rebuilt inside one JVM every time a single-player world is left, so
	 * without this a cast made in the last ten ticks of a world would be ticked
	 * against the next one. The spears themselves need no discard on the way
	 * out: the level they are in is going away with them, and
	 * {@code EntityMixin}'s veto is what keeps the shutdown save from writing
	 * them down.
	 */
	public static void forget() {
		LIVE.clear();
	}

	/** Drives the thrust and sweeps the dead. Called once a server tick. */
	public static void tick(final MinecraftServer server) {
		if (LIVE.isEmpty()) {
			return;
		}

		long now = server.overworld().getGameTime();
		var iterator = LIVE.iterator();

		while (iterator.hasNext()) {
			Formation formation = iterator.next();

			// Something outside this class removed one of them — a /kill, an
			// unloading chunk. The pair is the unit, so the survivor goes too
			// rather than being left standing on its own.
			if (formation.spears.stream().anyMatch(Display.ItemDisplay::isRemoved)) {
				discard(formation);
				iterator.remove();
				continue;
			}

			if (!formation.stabbed && now >= formation.stabAt) {
				formation.stabbed = true;

				for (Display.ItemDisplay display : formation.spears) {
					DisplayAccessor shape = (DisplayAccessor) display;
					shape.archetypes$setInterpolationDelay(0);
					shape.archetypes$setInterpolationDuration(Tuning.PHALANX_STAB_TICKS);
					shape.archetypes$setTransformation(pose(Tuning.PHALANX_THRUST));
				}
			}

			if (now >= formation.removeAt) {
				discard(formation);
				iterator.remove();
			}
		}
	}

	private static void discard(final Formation formation) {
		for (Display.ItemDisplay display : formation.spears) {
			display.discard();
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
