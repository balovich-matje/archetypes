package com.archetypes;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

/**
 * Every Seeker spell in flight is this one entity wearing a different item:
 * the mode picks the physics (gravity, range, steering), the on-hit rules and
 * the trail; the item it renders as travels along free via the vanilla
 * thrown-item renderer. Spells are seconds-long — one that gets saved and
 * chunk-reloaded wakes up with no mode and removes itself.
 */
public class SpellProjectile extends ThrowableItemProjectile {
	public enum Mode {
		FIREBALL, METEOR, FLAME_BOLT, MISSILE, HOLY_LIGHT, ICE_BLAST, GLACIAL_SPIKE
	}

	/** Holy Light's palette: gold by default; Benediction burns orange;
	 * Renewal keeps vanilla's green glow (see holyParticle). */
	// 1.21.11 gave DustParticleOptions an int-RGB constructor; below it the parameter is an
	// org.joml.Vector3f, and vanilla itself builds that with `Vec3.fromRGB24(rgb)` — the same
	// 0xRRGGBB word unpacked one step earlier. The colour CONSTANTS stay single and shared;
	// only the constructor call forks.
	//? if >=1.21.11 {
	private static final net.minecraft.core.particles.DustParticleOptions HOLY_DUST =
			new net.minecraft.core.particles.DustParticleOptions(0xFFD75E, 1.0F);
	private static final net.minecraft.core.particles.DustParticleOptions BENEDICTION_DUST =
			new net.minecraft.core.particles.DustParticleOptions(0xF07818, 1.0F);
	//?} else {
	/*private static final net.minecraft.core.particles.DustParticleOptions HOLY_DUST =
			new net.minecraft.core.particles.DustParticleOptions(
					net.minecraft.world.phys.Vec3.fromRGB24(0xFFD75E).toVector3f(), 1.0F);
	private static final net.minecraft.core.particles.DustParticleOptions BENEDICTION_DUST =
			new net.minecraft.core.particles.DustParticleOptions(
					net.minecraft.world.phys.Vec3.fromRGB24(0xF07818).toVector3f(), 1.0F);
	*///?}
	/** Lance's travelling ring: how many sparks trace the sweep's width. */
	private static final int LANCE_RING_POINTS = 6;

	/** Missile FX variant A, the Arcane Mote's palette: a faint violet
	 * thread for the rank and file, a bright one for empowered/homing. */
	//? if >=1.21.11 {
	private static final net.minecraft.core.particles.DustParticleOptions MISSILE_DUST =
			new net.minecraft.core.particles.DustParticleOptions(Tuning.MISSILE_DUST_COLOR, 0.6F);
	private static final net.minecraft.core.particles.DustParticleOptions MISSILE_DUST_BRIGHT =
			new net.minecraft.core.particles.DustParticleOptions(Tuning.MISSILE_DUST_BRIGHT_COLOR, 1.0F);
	//?} else {
	/*private static final net.minecraft.core.particles.DustParticleOptions MISSILE_DUST =
			new net.minecraft.core.particles.DustParticleOptions(
					net.minecraft.world.phys.Vec3.fromRGB24(Tuning.MISSILE_DUST_COLOR).toVector3f(), 0.6F);
	private static final net.minecraft.core.particles.DustParticleOptions MISSILE_DUST_BRIGHT =
			new net.minecraft.core.particles.DustParticleOptions(
					net.minecraft.world.phys.Vec3.fromRGB24(Tuning.MISSILE_DUST_BRIGHT_COLOR).toVector3f(), 1.0F);
	*///?}

	/** Mind Well's empowered missile — synced, because the client renders it
	 * half again bigger; it's also the only missile that keeps the trail. */
	private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_EMPOWERED =
			net.minecraft.network.syncher.SynchedEntityData.defineId(SpellProjectile.class,
					net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

	/** The meteor's render scale — synced so the client draws the rock as
	 * big as the mana that bought it. */
	private static final net.minecraft.network.syncher.EntityDataAccessor<Float> DATA_VISUAL_SCALE =
			net.minecraft.network.syncher.SynchedEntityData.defineId(SpellProjectile.class,
					net.minecraft.network.syncher.EntityDataSerializers.FLOAT);

	private @Nullable Mode mode;
	/** Meteorite: the mana that was poured in — impact scales off it. */
	private float power;
	private boolean homing;
	private boolean pierce;
	private boolean blessRegen;
	private boolean blessRandom;
	/** Elementalist shaping, decided at cast: Scorch/Arcane Power damage,
	 * Ignition's longer burn, the water interactions, Shatter's bonus and
	 * the ice spells' slow/freeze payloads. */
	private float damageOverride;
	private int igniteSeconds = -1;
	private boolean vaporize;
	private boolean permafrost;
	private int shatterRank;
	private int slowAmp = -1;
	private int slowTicks;
	private int freezeTicks;
	/** Holy Light: the heal side of the burst (the harm side stays base). */
	private float healOverride;
	/** Wizard/Priest shaping, decided at cast. */
	private float harmOverride;
	private double rangeOverride;
	private double radiusOverride;
	private int weaknessTicks;
	private float overwhelmBonus;
	private float shatterBonus;
	private boolean flatArc;
	private int aegisRank;
	private int sanctuaryRank;
	private int immolationRank;
	private int judgementRank;
	private final Set<Integer> pierced = new HashSet<>();
	private double traveled;
	/**
	 * Where the spell was born, captured on its first SERVER tick. The
	 * flamethrower's range is straight-line from here rather than the summed
	 * per-tick delta {@link #traveled} keeps: a bolt that stalls in water
	 * still adds a little length every tick and would take an age to reach a
	 * path-length cap while going nowhere.
	 */
	private @Nullable Vec3 origin;
	/** Vaporize: how much water this ONE projectile has already boiled. */
	private int vaporized;

	public SpellProjectile(final EntityType<? extends SpellProjectile> type, final Level level) {
		super(type, level);
	}

	public SpellProjectile(final LivingEntity owner, final Level level, final Mode mode, final ItemStack look) {
		// 1.21.11 folded the display stack into the owner constructor; below it that
		// constructor takes no ItemStack and the stack is a separate `setItem` — which is
		// exactly what vanilla's own 1.21.1 throwables do. Same field, same value, one call
		// later, and `setItem` is before `addFreshEntity` at every call site, so the stack
		// still rides the spawn packet rather than a later sync.
		super(ModEntities.SPELL_PROJECTILE, owner, level
				//? if >=1.21.11 {
				, look
				//?}
				);
		//? if <1.21.11 {
		/*this.setItem(look);
		*///?}
		this.mode = mode;
	}

	// STAGE 5: 1.20.5 gave `defineSynchedData` a BUILDER; below it the method takes no
	// arguments and each accessor is defined straight onto the entity's own data map. Same
	// two accessors, same defaults, same order.
	//? if >=1.20.5 {
	@Override
	protected void defineSynchedData(final net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_EMPOWERED, false);
		builder.define(DATA_VISUAL_SCALE, 1.0F);
	}
	//?} else {
	/*@Override
	protected void defineSynchedData() {
		this.getEntityData().define(DATA_EMPOWERED, false);
		this.getEntityData().define(DATA_VISUAL_SCALE, 1.0F);
	}
	*///?}

	public SpellProjectile withVisualScale(final float scale) {
		this.getEntityData().set(DATA_VISUAL_SCALE, scale);
		return this;
	}

	public float visualScale() {
		return this.getEntityData().get(DATA_VISUAL_SCALE);
	}

	public SpellProjectile withEmpowered() {
		this.getEntityData().set(DATA_EMPOWERED, true);
		return this;
	}

	public boolean isEmpowered() {
		return this.getEntityData().get(DATA_EMPOWERED);
	}

	public SpellProjectile withPower(final float manaSpent) {
		this.power = manaSpent;
		return this;
	}

	public SpellProjectile withHoming() {
		this.homing = true;
		return this;
	}

	public SpellProjectile withPierce() {
		this.pierce = true;
		return this;
	}

	public SpellProjectile withBlessing(final boolean regen, final boolean random) {
		this.blessRegen = regen;
		this.blessRandom = random;
		return this;
	}

	public SpellProjectile withDamage(final float damage) {
		this.damageOverride = damage;
		return this;
	}

	public SpellProjectile withIgnite(final int seconds) {
		this.igniteSeconds = seconds;
		return this;
	}

	public SpellProjectile withVaporize() {
		this.vaporize = true;
		return this;
	}

	public SpellProjectile withPermafrost() {
		this.permafrost = true;
		return this;
	}

	public SpellProjectile withShatter(final int rank) {
		this.shatterRank = rank;
		return this;
	}

	public SpellProjectile withSlow(final int amplifier, final int ticks) {
		this.slowAmp = amplifier;
		this.slowTicks = ticks;
		return this;
	}

	public SpellProjectile withFreeze(final int ticks) {
		this.freezeTicks = ticks;
		return this;
	}

	public SpellProjectile withHeal(final float amount) {
		this.healOverride = amount;
		return this;
	}

	public SpellProjectile withHarm(final float amount) {
		this.harmOverride = amount;
		return this;
	}

	public SpellProjectile withRange(final double range) {
		this.rangeOverride = range;
		return this;
	}

	public SpellProjectile withRadius(final double radius) {
		this.radiusOverride = radius;
		return this;
	}

	public SpellProjectile withWeakness(final int ticks) {
		this.weaknessTicks = ticks;
		return this;
	}

	public SpellProjectile withOverwhelm(final float bonus) {
		this.overwhelmBonus = bonus;
		return this;
	}

	public SpellProjectile withShatterpoint(final float bonus) {
		this.shatterBonus = bonus;
		return this;
	}

	public SpellProjectile withFlatArc() {
		this.flatArc = true;
		return this;
	}

	public SpellProjectile withAegis(final int rank) {
		this.aegisRank = rank;
		return this;
	}

	public SpellProjectile withSanctuary(final int rank) {
		this.sanctuaryRank = rank;
		return this;
	}

	public SpellProjectile withImmolation(final int rank) {
		this.immolationRank = rank;
		return this;
	}

	public SpellProjectile withJudgement(final int rank) {
		this.judgementRank = rank;
		return this;
	}

	public @Nullable Mode mode() {
		return this.mode;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.FIRE_CHARGE;
	}

	// STAGE 5: `getDefaultGravity()D` is 1.20.5's; below it the hook is
	// `ThrowableProjectile.getGravity()F`, called from the same place in `tick()` and
	// subtracted from the delta the same way — a float rather than a double, and a rename.
	// The three numbers are the shared ones and are not written twice.
	//? if >=1.20.5 {
	@Override
	protected double getDefaultGravity() {
		return this.mode == Mode.HOLY_LIGHT ? (this.flatArc ? 0.02 : 0.05) : 0.0;
	}
	//?} else {
	/*@Override
	protected float getGravity() {
		return (float) (this.mode == Mode.HOLY_LIGHT ? (this.flatArc ? 0.02 : 0.05) : 0.0);
	}
	*///?}

	/**
	 * Air drag, and the ONE place the numbers live on every node.
	 *
	 * <p>26.2 introduced {@code ThrowableProjectile.getAirDrag()} as the overridable
	 * hook; 26.1 and below hard-code the float 0.99 inline in
	 * {@code ThrowableProjectile.applyInertia()} (measured: the 26.1.2 bytecode does
	 * {@code ldc 0.99f} where 26.2 does {@code invokevirtual getAirDrag()F}, and the
	 * water arm is the constant 0.8 on both). So below 26.2 the annotation goes — this
	 * stays a plain method nobody calls from vanilla — and {@link #tick()} pre-scales
	 * instead. The VALUES never fork.
	 */
	//? if >=26.2 {
	@Override
	//?}
	protected float getAirDrag() {
		return this.mode == Mode.HOLY_LIGHT ? 0.99F : 1.0F;
	}

	@Override
	public void tick() {
		// Below 26.2 there is no getAirDrag hook, so reproduce its CONTRACT rather than
		// approximate it (R-20). vanilla's applyInertia runs first thing in super.tick()
		// and does `delta *= 0.99` in the non-water arm, then moves by that delta in the
		// same tick — so pre-scaling by ours/0.99 here leaves applyInertia landing on
		// exactly `delta * getAirDrag()`, which is what 26.2 computes. The water arm
		// (0.8) is untouched on both, hence the same isInWater() test vanilla branches on.
		//? if <26.2 {
		/*if (!this.isInWater()) {
			this.setDeltaMovement(this.getDeltaMovement().scale(this.getAirDrag() / 0.99F));
		}
		*///?}
		super.tick();

		if (this.level().isClientSide() || this.isRemoved()) {
			return;
		}

		if (this.mode == null) {
			this.discard();
			return;
		}

		if (this.origin == null) {
			this.origin = this.position();
		}

		this.traveled += this.getDeltaMovement().length();

		if (this.mode == Mode.MISSILE && this.traveled
				> (this.rangeOverride > 0.0 ? this.rangeOverride : Tuning.MISSILE_RANGE)) {
			this.discard();
			return;
		}

		// The flamethrower's bolts burn out by themselves. Nothing in vanilla
		// stops them: water is not a collision for a throwable, it only drags
		// the delta to nearly nothing, so a bolt sprayed into a lake used to
		// hang there for good — dozens of them piling up in the same puddle
		// (user report). Distance is straight-line from the muzzle, so a
		// stalled bolt is killed by the time cap instead of creeping toward a
		// path-length one.
		if (this.mode == Mode.FLAME_BOLT
				&& (this.tickCount > Tuning.FLAME_BOLT_MAX_TICKS
						|| this.position().distanceToSqr(this.origin)
								> Tuning.FLAME_BOLT_MAX_DISTANCE * Tuning.FLAME_BOLT_MAX_DISTANCE)) {
			this.discard();
			return;
		}

		if (this.homing) {
			this.steer();
		}

		if (this.pierce) {
			this.pierceSweep();
		}

		this.meddleWithWater((ServerLevel) this.level());

		// meddleWithWater can spend the projectile (Vaporize), and a trail
		// drawn after that is a puff of flame at a position nothing occupies.
		if (this.isRemoved()) {
			return;
		}

		this.trail((ServerLevel) this.level());
	}

	/** Seeker Missile: bend toward the nearest enemy ahead, keeping speed. */
	private void steer() {
		LivingEntity target = Homing.pickTarget(this, Tuning.MISSILE_HOMING_RADIUS,
				living -> living instanceof Enemy && living != this.getOwner());

		if (target != null) {
			Homing.steer(this, target);
		}
	}

	/**
	 * The Lance capstone: vanilla hit detection is bypassed for entities
	 * (see canHitEntity) and this wider sweep damages everything the missile
	 * passes through, each victim once.
	 */
	private void pierceSweep() {
		for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class,
				this.getBoundingBox().inflate(Tuning.MISSILE_PIERCE_INFLATE),
				living -> living.isAlive() && living != this.getOwner())) {
			if (this.pierced.add(victim.getId())) {
				this.missileHit((ServerLevel) this.level(), victim);
			}
		}
	}

	/**
	 * Vaporize boils water off the flight path; Permafrost glazes it over
	 * with the same self-melting ice frost walkers leave.
	 *
	 * <p>Vaporize is budgeted: one projectile boils {@code VAPORIZE_MAX_BLOCKS}
	 * blocks (source or flowing — both are {@code Blocks.WATER}) and is then
	 * spent and discarded. It used to clear every water block in the 3x3x2
	 * around itself EVERY tick, and since a bolt in water barely moves, one
	 * bolt emptied a lake and then hung in the hole it dug.
	 */
	private void meddleWithWater(final ServerLevel level) {
		if (!this.vaporize && !this.permafrost) {
			return;
		}

		net.minecraft.core.BlockPos center = this.blockPosition();

		for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
				center.offset(-1, -1, -1), center.offset(1, 0, 1))) {
			if (!level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.WATER)) {
				continue;
			}

			if (this.vaporize) {
				level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				level.sendParticles(ParticleTypes.CLOUD,
						pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.01);
				level.playSound(null, pos.getX(), pos.getY(), pos.getZ(),
						SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.6F);
				this.vaporized++;

				if (this.vaporized >= Tuning.VAPORIZE_MAX_BLOCKS) {
					this.discard();
					return;
				}
			} else {
				level.setBlockAndUpdate(pos,
						net.minecraft.world.level.block.Blocks.FROSTED_ICE.defaultBlockState());
			}
		}
	}

	private void trail(final ServerLevel level) {
		// The null guard is explicit rather than a null switch LABEL: labelled
		// nulls are a Java 21 switch pattern and Java 17 is the shared-code ceiling
		// (conventions §5e). Switching on a null enum throws, so the check has to
		// be here and not a `default` arm.
		if (this.mode == null) {
			return;
		}

		switch (this.mode) {
			case FIREBALL -> {
				level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(),
						2, 0.1, 0.1, 0.1, 0.005);
				level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(),
						1, 0.05, 0.05, 0.05, 0.0);
			}
			case METEOR -> {
				level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(),
						8, 0.4, 0.4, 0.4, 0.02);
				level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY(), this.getZ(),
						1, 0.2, 0.2, 0.2, 0.0);
			}
			case FLAME_BOLT -> level.sendParticles(ParticleTypes.FLAME,
					this.getX(), this.getY(), this.getZ(), 1, 0.05, 0.05, 0.05, 0.005);
			// Missile FX variant A: a near-nothing violet thread for the
			// spammed cast (the sprite's hot core carries the glow), the
			// bright thread every tick for empowered and for the homing
			// capstone (so the curve is legible), plus END_ROD sparkle on
			// empowered only. Lance keeps its ring, tinted into the family.
			case MISSILE -> {
				if (this.pierce) {
					this.lanceRing(level);
				}

				// The glint sings from the missile itself, looping until it
				// hits or expires (user direction) — a chime tick every 3
				// flight ticks, pitched down and louder when empowered.
				if (this.tickCount % 3 == 0) {
					level.playSound(null, this.getX(), this.getY(), this.getZ(),
							SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
							this.isEmpowered() ? 0.7F : 0.5F,
							(this.isEmpowered() ? 1.0F : 1.45F)
									+ (this.random.nextFloat() - 0.5F) * 0.2F);
				}

				if (this.isEmpowered()) {
					level.sendParticles(MISSILE_DUST_BRIGHT,
							this.getX(), this.getY(), this.getZ(), 1, 0.05, 0.05, 0.05, 0.0);

					if (this.tickCount % 2 == 0) {
						level.sendParticles(ParticleTypes.END_ROD,
								this.getX(), this.getY(), this.getZ(), 1, 0.03, 0.03, 0.03, 0.0);
					}
				} else if (this.homing) {
					level.sendParticles(MISSILE_DUST_BRIGHT,
							this.getX(), this.getY(), this.getZ(), 1, 0.03, 0.03, 0.03, 0.0);
				} else if (this.tickCount % 2 == 0) {
					level.sendParticles(MISSILE_DUST,
							this.getX(), this.getY(), this.getZ(), 1, 0.03, 0.03, 0.03, 0.0);
				}
			}
			case HOLY_LIGHT -> level.sendParticles(this.holyParticle(),
					this.getX(), this.getY(), this.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
			case ICE_BLAST -> level.sendParticles(ParticleTypes.SNOWFLAKE,
					this.getX(), this.getY(), this.getZ(), 3, 0.15, 0.15, 0.15, 0.01);
			case GLACIAL_SPIKE -> level.sendParticles(ParticleTypes.SNOWFLAKE,
					this.getX(), this.getY(), this.getZ(), 5, 0.1, 0.1, 0.1, 0.02);
			default -> {
			}
		}
	}

	/**
	 * Lance: a ring of sparks perpendicular to the flight path, at the radius
	 * pierceSweep actually damages — it spins a little each tick, so the
	 * missile drills forward leaving a faint helix.
	 */
	private void lanceRing(final ServerLevel level) {
		Vec3 dir = this.getDeltaMovement();

		if (dir.lengthSqr() < 1.0E-4) {
			return;
		}

		dir = dir.normalize();
		Vec3 side = Math.abs(dir.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
		Vec3 u = dir.cross(side).normalize();
		Vec3 w = dir.cross(u);
		double radius = Tuning.MISSILE_PIERCE_INFLATE + this.getBbWidth() / 2.0;
		double spin = this.tickCount * 0.5;

		for (int i = 0; i < LANCE_RING_POINTS; i++) {
			double angle = spin + i * (Math.PI * 2.0 / LANCE_RING_POINTS);
			Vec3 point = this.position()
					.add(u.scale(Math.cos(angle) * radius))
					.add(w.scale(Math.sin(angle) * radius));
			level.sendParticles(MISSILE_DUST_BRIGHT, point.x, point.y, point.z,
					1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** Gold for the plain light; Renewal stays the green glow the spell
	 * always had; Benediction burns orange. The capstones are exclusive,
	 * so the two flags never collide. */
	private net.minecraft.core.particles.ParticleOptions holyParticle() {
		if (this.blessRegen) {
			return ParticleTypes.GLOW;
		}

		return this.blessRandom ? BENEDICTION_DUST : HOLY_DUST;
	}

	@Override
	protected boolean canHitEntity(final Entity entity) {
		// The piercing missile never "hits" an entity in vanilla's sense —
		// it flies through, and pierceSweep does the damage.
		return super.canHitEntity(entity) && !(this.pierce && this.mode == Mode.MISSILE);
	}

	@Override
	protected void onHitEntity(final EntityHitResult hit) {
		if (this.level().isClientSide() || !(hit.getEntity() instanceof LivingEntity victim)) {
			return;
		}

		ServerLevel level = (ServerLevel) this.level();

		// Java 17 ceiling again — see trail().
		if (this.mode == null) {
			return;
		}

		switch (this.mode) {
			case FLAME_BOLT -> {
				/*? if >=1.21 {*/victim.igniteForSeconds(this.igniteSeconds >= 0
				/*?} else *///victim.setSecondsOnFire(this.igniteSeconds >= 0
						? this.igniteSeconds : Tuning.FLAME_BOLT_FIRE_SECONDS);
				//? if >=1.21.2 {
				victim.hurtServer(level, this.damageSources().thrown(this, this.getOwner()),
						this.shattered(victim, this.damageOverride > 0.0F
								? this.damageOverride : Tuning.FLAME_BOLT_DAMAGE));
				//?} else {
				/*victim.hurt(this.damageSources().thrown(this, this.getOwner()),
						this.shattered(victim, this.damageOverride > 0.0F
								? this.damageOverride : Tuning.FLAME_BOLT_DAMAGE));
				*///?}
			}
			case MISSILE -> this.missileHit(level, victim);
			case GLACIAL_SPIKE -> {
				// The ice synergy's finisher: x10 against anything already
				// slowed or freezing, x2 cold. Deliberately NOT run through
				// shattered() — the multiplier IS the chill payoff, and
				// stacking the passive on top would double-count it.
				boolean chilled = victim.hasEffect(MobEffects.SLOWNESS)
						|| victim.getTicksFrozen() > 0;
				float base = this.damageOverride > 0.0F ? this.damageOverride : Tuning.ICE_BLAST_DAMAGE;

				if (this.freezeTicks > 0) {
					victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), this.freezeTicks));
				}

				//? if >=1.21.2 {
				victim.hurtServer(level, this.damageSources().indirectMagic(this, this.getOwner()),
						base * (chilled ? Tuning.GLACIAL_CHILLED_MULTIPLIER : Tuning.GLACIAL_BASE_MULTIPLIER));
				//?} else {
				/*victim.hurt(this.damageSources().indirectMagic(this, this.getOwner()),
						base * (chilled ? Tuning.GLACIAL_CHILLED_MULTIPLIER : Tuning.GLACIAL_BASE_MULTIPLIER));
				*///?}
			}
			default -> {
				// Fireball, Ice Blast, Meteor and Holy Light all area-effect
				// from onHit — ground or flesh alike.
			}
		}
	}

	@Override
	protected void onHit(final HitResult result) {
		super.onHit(result);

		if (this.level().isClientSide()) {
			return;
		}

		ServerLevel level = (ServerLevel) this.level();

		// Guarded rather than returned early — the discard() below this switch has
		// to happen either way. Java 17 ceiling, as in trail().
		if (this.mode != null) {
			switch (this.mode) {
				case FIREBALL -> {
					this.elementBurst(level, true);
					level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(),
							1, 0.0, 0.0, 0.0, 0.0);
					level.playSound(null, this.getX(), this.getY(), this.getZ(),
							/*? if >=1.21 {*/SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
							/*?} else *///SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.4F);
				}
				case METEOR -> this.impact(level);
				case HOLY_LIGHT -> this.burst(level);
				case ICE_BLAST -> {
					this.elementBurst(level, false);
					level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(),
							15, 0.3, 0.3, 0.3, 0.05);
					level.playSound(null, this.getX(), this.getY(), this.getZ(),
							SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
				}
				case GLACIAL_SPIKE -> {
					level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(),
							20, 0.2, 0.2, 0.2, 0.08);
					level.playSound(null, this.getX(), this.getY(), this.getZ(),
							SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.9F);
				}
				default -> {
				}
			}
		}

		this.discard();
	}

	/** Fireball and Ice Blast burst in a 3x3 on ANY impact — the direct hit
	 * and its neighbours all take the payload. */
	private void elementBurst(final ServerLevel level, final boolean fire) {
		double radius = Tuning.ELEMENT_BURST_RADIUS;

		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
				this.getBoundingBox().inflate(radius),
				living -> living.isAlive() && living != this.getOwner()
						&& living.distanceToSqr(this) <= radius * radius)) {
			if (fire) {
				/*? if >=1.21 {*/victim.igniteForSeconds(this.igniteSeconds >= 0
				/*?} else *///victim.setSecondsOnFire(this.igniteSeconds >= 0
						? this.igniteSeconds : Tuning.FIREBALL_FIRE_SECONDS);
				//? if >=1.21.2 {
				victim.hurtServer(level, this.damageSources().thrown(this, this.getOwner()),
						this.shattered(victim, this.damageOverride > 0.0F
								? this.damageOverride : Tuning.FIREBALL_DAMAGE));
				//?} else {
				/*victim.hurt(this.damageSources().thrown(this, this.getOwner()),
						this.shattered(victim, this.damageOverride > 0.0F
								? this.damageOverride : Tuning.FIREBALL_DAMAGE));
				*///?}
			} else {
				// Shatter reads the slow/freeze the victim ALREADY has, so a
				// volley ramps: the first bolt tags, the next ones profit.
				float damage = this.shattered(victim, this.damageOverride > 0.0F
						? this.damageOverride : Tuning.ICE_BLAST_DAMAGE);

				if (this.slowAmp >= 0) {
					victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
							this.slowTicks, this.slowAmp));
				}

				if (this.freezeTicks > 0) {
					victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), this.freezeTicks));
				}

				//? if >=1.21.2 {
				victim.hurtServer(level, this.damageSources().indirectMagic(this, this.getOwner()), damage);
				//?} else {
				/*victim.hurt(this.damageSources().indirectMagic(this, this.getOwner()), damage);
				*///?}
			}
		}
	}

	/**
	 * Meteorite's landing, all of it scaled by m = power/100 (user formula):
	 * 8 hearts across a 5x5 at the 100-mana minimum, and damage, area,
	 * particles and loudness all grow together with every point above.
	 * Anything with zero hardness in the crater — torches, flowers, snow —
	 * is wiped. Real blocks are safe: this is a spell, not TNT.
	 */
	private void impact(final ServerLevel level) {
		float m = this.power / Tuning.METEOR_MIN_MANA;
		float radius = Tuning.METEOR_BASE_RADIUS * m;
		float damage = Tuning.METEOR_BASE_DAMAGE * m;
		float fx = Math.min(m, Tuning.METEOR_FX_SCALE_CAP);

		// Knockback level = ceil(m): Knockback I at 100 mana, III at 250 —
		// the crowd is thrown violently from the crater (user spec).
		int knockback = (int) Math.ceil(m);

		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
				this.getBoundingBox().inflate(radius),
				living -> living.isAlive() && living != this.getOwner()
						&& living.distanceToSqr(this) <= radius * radius)) {
			/*? if >=1.21 {*/victim.igniteForSeconds(3);
			/*?} else *///victim.setSecondsOnFire(3);
			//? if >=1.21.2 {
			victim.hurtServer(level, this.damageSources().indirectMagic(this, this.getOwner()), damage);
			//?} else {
			/*victim.hurt(this.damageSources().indirectMagic(this, this.getOwner()), damage);
			*///?}

			if (victim.isAlive()) {
				Vec3 away = new Vec3(victim.getX() - this.getX(), 0.0, victim.getZ() - this.getZ());
				Vec3 dir = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
				victim.push(dir.x * (0.4 + 0.5 * knockback), 0.3 + 0.1 * knockback,
						dir.z * (0.4 + 0.5 * knockback));
				victim.hurtMarked = true;
			}
		}

		BlockPos center = this.blockPosition();
		int reach = (int) Math.ceil(radius);

		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-reach, -1, -reach),
				center.offset(reach, reach, reach))) {
			if (pos.distSqr(center) <= radius * radius) {
				var state = level.getBlockState(pos);

				if (!state.isAir() && state.getDestroySpeed(level, pos) == 0.0F) {
					level.destroyBlock(pos, true);
				}
			}
		}

		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY(), this.getZ(),
				Math.max(1, Math.round(fx)), radius * 0.3, 0.3, radius * 0.3, 0.0);
		level.playSound(null, this.getX(), this.getY(), this.getZ(),
				/*? if >=1.21 {*/SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F * fx, 0.7F);
				/*?} else *///SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F * fx, 0.7F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(),
				/*? if >=1.21 {*/SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.5F * fx, 0.6F);
				/*?} else *///SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.5F * fx, 0.6F);
	}

	/**
	 * Holy Light's burst, splash-potion shaped: the living are healed, the
	 * undead take the same number as damage (their own rules, inverted) plus
	 * Immolation's fire and Judgement's weakness, and Sanctuary's shell and
	 * the capstone blessings land on anything that isn't hostile.
	 */
	private void burst(final ServerLevel level) {
		double radius = this.radiusOverride > 0.0 ? this.radiusOverride : Tuning.HOLY_RADIUS;

		for (LivingEntity creature : level.getEntitiesOfClass(LivingEntity.class,
				this.getBoundingBox().inflate(radius),
				living -> living.isAlive()
						&& living.distanceToSqr(this) <= radius * radius)) {
			if (creature.isInvertedHealAndHarm()) {
				// Immolation: lit before the hit, so even a killing blow burns.
				if (this.immolationRank > 0) {
					/*? if >=1.21 {*/creature.igniteForSeconds(Tuning.IMMOLATION_FIRE_SECONDS_PER_RANK * this.immolationRank);
					/*?} else *///creature.setSecondsOnFire(Tuning.IMMOLATION_FIRE_SECONDS_PER_RANK * this.immolationRank);
				}

				//? if >=1.21.2 {
				creature.hurtServer(level, this.damageSources().indirectMagic(this, this.getOwner()),
						this.harmOverride > 0.0F ? this.harmOverride : Tuning.HOLY_AMOUNT);
				//?} else {
				/*creature.hurt(this.damageSources().indirectMagic(this, this.getOwner()),
						this.harmOverride > 0.0F ? this.harmOverride : Tuning.HOLY_AMOUNT);
				*///?}

				// Judgement: the light saps the undead arm.
				if (this.judgementRank > 0 && creature.isAlive()) {
					creature.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
							Tuning.JUDGEMENT_WEAKNESS_TICKS, this.judgementRank - 1));
				}
			} else {
				// Declared magical so a Colossus Slayer's Barbarian can cut it —
				// the author listed "healing from priest" by name.
				ColossusSlayer.magicalHeal(creature,
						this.healOverride > 0.0F ? this.healOverride : Tuning.HOLY_AMOUNT);
			}

			if (!(creature instanceof Enemy)) {
				// Sanctuary: friends get the same shell Aegis gives the
				// caster (whose own comes from Aegis, below).
				if (this.sanctuaryRank > 0 && creature != this.getOwner()) {
					creature.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
							Tuning.AEGIS_TICKS, this.sanctuaryRank - 1));
				}

				if (this.blessRegen) {
					creature.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
							Tuning.HOLY_EFFECT_TICKS));
				}

				if (this.blessRandom) {
					creature.addEffect(new MobEffectInstance(randomBlessing(this.random),
							Tuning.HOLY_EFFECT_TICKS));
				}
			}
		}

		// Aegis: the light leaves a shell on its caster.
		if (this.aegisRank > 0 && this.getOwner() instanceof net.minecraft.server.level.ServerPlayer owner) {
			owner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
					Tuning.AEGIS_TICKS, this.aegisRank - 1));
		}

		level.sendParticles(this.holyParticle(), this.getX(), this.getY(), this.getZ(),
				40, radius * 0.4, 0.6, radius * 0.4, 0.5);
		// Bright and immediate on impact — the bell's slow bloom read as a
		// delayed ring (user call), so the light lands as a blessing pling
		// with a high chime on top.
		level.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.9F, 1.3F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.5F);
	}

	/**
	 * One missile landing: Overwhelm rewards finishing the wounded,
	 * Shatterpoint rewards opening on the whole, Concussion saps the
	 * target's arm.
	 */
	private void missileHit(final ServerLevel level, final LivingEntity victim) {
		float damage = this.damageOverride > 0.0F ? this.damageOverride : Tuning.MISSILE_DAMAGE;

		if (victim.getHealth() >= victim.getMaxHealth() - 0.01F) {
			damage *= 1.0F + this.shatterBonus;
		} else {
			damage *= 1.0F + this.overwhelmBonus;
		}

		//? if >=1.21.2 {
		victim.hurtServer(level, this.damageSources().indirectMagic(this, this.getOwner()), damage);
		//?} else {
		/*victim.hurt(this.damageSources().indirectMagic(this, this.getOwner()), damage);
		*///?}

		// Variant A: hits tick softly — connection you can hear without a
		// fourth loud event in the spam.
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
				SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.5F,
				1.1F + (this.random.nextFloat() - 0.5F) * 0.4F);

		if (this.weaknessTicks > 0 && victim.isAlive()) {
			victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, this.weaknessTicks));
		}
	}

	/** Shatter: the cold-cracked take more from every one of your spells. */
	private float shattered(final LivingEntity victim, final float damage) {
		if (this.shatterRank > 0
				&& (victim.hasEffect(MobEffects.SLOWNESS) || victim.isFullyFrozen())) {
			return damage * (1.0F + Tuning.SHATTER_PER_RANK * this.shatterRank);
		}

		return damage;
	}

	//? if >=1.21 {
	private static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> randomBlessing(
			final RandomSource random) {
	//?} else {
	/*private static net.minecraft.world.effect.MobEffect randomBlessing(final RandomSource random) {
	*///?}
		return switch (random.nextInt(4)) {
			case 0 -> MobEffects.SPEED;
			case 1 -> MobEffects.STRENGTH;
			case 2 -> MobEffects.RESISTANCE;
			default -> MobEffects.FIRE_RESISTANCE;
		};
	}
}
