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
import org.jspecify.annotations.Nullable;

/**
 * Every Seeker spell in flight is this one entity wearing a different item:
 * the mode picks the physics (gravity, range, steering), the on-hit rules and
 * the trail; the item it renders as travels along free via the vanilla
 * thrown-item renderer. Spells are seconds-long — one that gets saved and
 * chunk-reloaded wakes up with no mode and removes itself.
 */
public class SpellProjectile extends ThrowableItemProjectile {
	public enum Mode {
		FIREBALL, METEOR, FLAME_BOLT, MISSILE, HOLY_LIGHT, ICE_BLAST, SNOW_BOLT
	}

	/** Mind Well's empowered missile — synced, because the client renders it
	 * half again bigger; it's also the only missile that keeps the trail. */
	private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_EMPOWERED =
			net.minecraft.network.syncher.SynchedEntityData.defineId(SpellProjectile.class,
					net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

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

	public SpellProjectile(final EntityType<? extends SpellProjectile> type, final Level level) {
		super(type, level);
	}

	public SpellProjectile(final LivingEntity owner, final Level level, final Mode mode, final ItemStack look) {
		super(ModEntities.SPELL_PROJECTILE, owner, level, look);
		this.mode = mode;
	}

	@Override
	protected void defineSynchedData(final net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_EMPOWERED, false);
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

	@Override
	protected double getDefaultGravity() {
		return this.mode == Mode.HOLY_LIGHT ? (this.flatArc ? 0.02 : 0.05) : 0.0;
	}

	@Override
	protected float getAirDrag() {
		return this.mode == Mode.HOLY_LIGHT ? 0.99F : 1.0F;
	}

	@Override
	public void tick() {
		super.tick();

		if (this.level().isClientSide() || this.isRemoved()) {
			return;
		}

		if (this.mode == null) {
			this.discard();
			return;
		}

		this.traveled += this.getDeltaMovement().length();

		if (this.mode == Mode.MISSILE && this.traveled
				> (this.rangeOverride > 0.0 ? this.rangeOverride : Tuning.MISSILE_RANGE)) {
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

	/** Vaporize boils water off the flight path; Permafrost glazes it over
	 * with the same self-melting ice frost walkers leave. */
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
			} else {
				level.setBlockAndUpdate(pos,
						net.minecraft.world.level.block.Blocks.FROSTED_ICE.defaultBlockState());
			}
		}
	}

	private void trail(final ServerLevel level) {
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
			// Only the empowered missile wears the white trail; the rank and
			// file fly clean (user sketch, new-edits-for-wizard).
			case MISSILE -> {
				if (this.isEmpowered()) {
					level.sendParticles(ParticleTypes.END_ROD,
							this.getX(), this.getY(), this.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
				}
			}
			case HOLY_LIGHT -> level.sendParticles(ParticleTypes.GLOW,
					this.getX(), this.getY(), this.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
			case ICE_BLAST -> level.sendParticles(ParticleTypes.SNOWFLAKE,
					this.getX(), this.getY(), this.getZ(), 3, 0.15, 0.15, 0.15, 0.01);
			case SNOW_BOLT -> level.sendParticles(ParticleTypes.SNOWFLAKE,
					this.getX(), this.getY(), this.getZ(), 1, 0.05, 0.05, 0.05, 0.005);
			case null -> {
			}
		}
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

		switch (this.mode) {
			case FIREBALL -> {
				victim.igniteForSeconds(this.igniteSeconds >= 0
						? this.igniteSeconds : Tuning.FIREBALL_FIRE_SECONDS);
				victim.hurtServer(level, this.damageSources().thrown(this, this.getOwner()),
						this.shattered(victim, this.damageOverride > 0.0F
								? this.damageOverride : Tuning.FIREBALL_DAMAGE));
			}
			case FLAME_BOLT -> {
				victim.igniteForSeconds(this.igniteSeconds >= 0
						? this.igniteSeconds : Tuning.FLAME_BOLT_FIRE_SECONDS);
				victim.hurtServer(level, this.damageSources().thrown(this, this.getOwner()),
						this.shattered(victim, this.damageOverride > 0.0F
								? this.damageOverride : Tuning.FLAME_BOLT_DAMAGE));
			}
			case MISSILE -> this.missileHit(level, victim);
			case ICE_BLAST, SNOW_BOLT -> {
				// Shatter reads the slow/freeze the victim ALREADY has, so a
				// volley ramps: the first bolt tags, the next ones profit.
				float damage = this.shattered(victim, this.damageOverride > 0.0F
						? this.damageOverride
						: this.mode == Mode.ICE_BLAST ? Tuning.ICE_BLAST_DAMAGE : Tuning.SNOW_BOLT_DAMAGE);

				if (this.slowAmp >= 0) {
					victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
							this.slowTicks, this.slowAmp));
				}

				if (this.freezeTicks > 0) {
					victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), this.freezeTicks));
				}

				victim.hurtServer(level, this.damageSources().indirectMagic(this, this.getOwner()), damage);
			}
			case null, default -> {
				// Meteor and Holy Light area-effect from onHit, ground or flesh alike.
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

		switch (this.mode) {
			case FIREBALL -> {
				level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(),
						1, 0.0, 0.0, 0.0, 0.0);
				level.playSound(null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
			}
			case METEOR -> this.impact(level);
			case HOLY_LIGHT -> this.burst(level);
			case ICE_BLAST -> {
				level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(),
						15, 0.3, 0.3, 0.3, 0.05);
				level.playSound(null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
			}
			case null, default -> {
			}
		}

		this.discard();
	}

	/**
	 * Meteorite's landing: damage and radius grow with the mana poured in,
	 * and anything with zero hardness in the crater — torches, flowers, snow
	 * — is wiped. Real blocks are safe: this is a spell, not TNT.
	 */
	private void impact(final ServerLevel level) {
		float radius = Tuning.METEOR_RADIUS_BASE
				+ Math.max(0.0F, this.power - Tuning.METEOR_MIN_MANA) * Tuning.METEOR_RADIUS_PER_EXTRA_MANA;
		float damage = this.power * Tuning.METEOR_DAMAGE_PER_MANA;

		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
				this.getBoundingBox().inflate(radius),
				living -> living.isAlive() && living != this.getOwner()
						&& living.distanceToSqr(this) <= radius * radius)) {
			victim.igniteForSeconds(3);
			victim.hurtServer(level, this.damageSources().indirectMagic(this, this.getOwner()), damage);
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
				1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 0.7F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.5F, 0.6F);
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
					creature.igniteForSeconds(Tuning.IMMOLATION_FIRE_SECONDS_PER_RANK * this.immolationRank);
				}

				creature.hurtServer(level, this.damageSources().indirectMagic(this, this.getOwner()),
						this.harmOverride > 0.0F ? this.harmOverride : Tuning.HOLY_AMOUNT);

				// Judgement: the light saps the undead arm.
				if (this.judgementRank > 0 && creature.isAlive()) {
					creature.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
							Tuning.JUDGEMENT_WEAKNESS_TICKS, this.judgementRank - 1));
				}
			} else {
				creature.heal(this.healOverride > 0.0F ? this.healOverride : Tuning.HOLY_AMOUNT);
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

		level.sendParticles(ParticleTypes.GLOW, this.getX(), this.getY(), this.getZ(),
				40, radius * 0.4, 0.6, radius * 0.4, 0.5);
		level.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.0F, 1.3F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.7F);
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

		victim.hurtServer(level, this.damageSources().indirectMagic(this, this.getOwner()), damage);

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

	private static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> randomBlessing(
			final RandomSource random) {
		return switch (random.nextInt(4)) {
			case 0 -> MobEffects.SPEED;
			case 1 -> MobEffects.STRENGTH;
			case 2 -> MobEffects.RESISTANCE;
			default -> MobEffects.FIRE_RESISTANCE;
		};
	}
}
