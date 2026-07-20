package com.archetypes;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/**
 * The mod's own damage types. There is exactly one, and it exists because
 * Decimate has to read the same to everybody.
 *
 * <h2>Why a type of its own</h2>
 * Decimate is the one hit in the mod whose whole design is "your gear is not
 * the answer, your class is". A {@code playerAttack} cannot say that: it goes
 * through armour, Protection, a raised shield and the carried-shield passive,
 * and every one of those is bought rather than pressed. {@code archetypes:decimate}
 * is authored into {@code #minecraft:bypasses_armor},
 * {@code #minecraft:bypasses_enchantments} and {@code #minecraft:bypasses_shield}
 * (see {@code data/minecraft/tags/damage_type/}), so all four drop out and what
 * is left standing is Mana Shield, Magic Armor's temporary hearts, Bulwark's
 * absorption bank, Ghost Form's roll and the parry — the archetypes' own
 * answers, each of which costs an input or a resource banked in advance.
 *
 * <p>It is deliberately NOT in {@code archetypes:magical}: Barbarian's cut is
 * zero-input, so it would be one more piece of gear-shaped mitigation on the
 * one hit that is supposed to have none.
 *
 * <p>It is deliberately NOT in {@code #minecraft:bypasses_effects} (Resistance
 * is a legitimate answer, and the tag would also short-circuit the magic-absorb
 * stage entirely) nor in {@code #minecraft:bypasses_invulnerability} (Ghost Form
 * rolls off the vanilla invulnerability path, and the Nemesis's fallback would
 * disappear with it).
 *
 * <p>The type's {@code scaling} is {@code never}, and that is load-bearing
 * rather than a default: {@code Player.hurtServer} applies the difficulty
 * multiplier BEFORE it calls up to {@code LivingEntity.hurtServer}, i.e. before
 * any of this mod's {@code ModifyVariable} shapers can see the number. Authored
 * {@code always}, a Decimate calibrated at 44 would arrive as 66 on Hard and 23
 * on Easy — a capstone that cannot be tuned, and a guaranteed overkill on Hard
 * with no counterplay attached to it. Vanilla's own {@code player_attack} uses
 * {@code when_caused_by_living_non_player} for the same reason, which for a
 * player attacker evaluates to false; {@code never} says the same thing
 * unconditionally, including for the (impossible today, cheap tomorrow) case of
 * a non-player casting it.
 */
public final class ModDamageTypes {
	/** {@code archetypes:decimate} — the Slayer capstone's own type. Datapack
	 * content: the JSON at {@code data/archetypes/damage_type/decimate.json} is
	 * the definition, this is only the key to look it up with. */
	public static final ResourceKey<DamageType> DECIMATE =
			ResourceKey.create(Registries.DAMAGE_TYPE, Archetypes.id("decimate"));

	private ModDamageTypes() {
	}

	/**
	 * A Decimate source attributed to its caster, so death messages, mob anger
	 * and kill credit all name the Slayer.
	 *
	 * <p>The caster is both the causing and the direct entity, exactly as a melee
	 * blow is — the parry's {@code direct instanceof LivingEntity && direct ==
	 * source.getEntity()} test has to keep classifying a Decimate as melee, or
	 * the one answer the author kept would stop answering the one hit it was
	 * kept for.
	 */
	public static DamageSource decimate(final ServerLevel level, final Entity caster) {
		return new DamageSource(holder(level, DECIMATE), caster);
	}

	private static Holder<DamageType> holder(final ServerLevel level,
			final ResourceKey<DamageType> key) {
		return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
	}
}
