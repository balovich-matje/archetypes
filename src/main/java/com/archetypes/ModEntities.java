package com.archetypes;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	/** All five Seeker spells in flight — see {@link SpellProjectile}. */
	public static final EntityType<SpellProjectile> SPELL_PROJECTILE = register("spell_projectile",
			EntityType.Builder.<SpellProjectile>of(SpellProjectile::new, MobCategory.MISC)
					.sized(0.25F, 0.25F)
					.clientTrackingRange(8)
					.updateInterval(10));

	private ModEntities() {
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(
			final String path, final EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Archetypes.id(path));
		// `EntityType.Builder.build` takes the registry KEY from 1.21.11 and the bare id
		// STRING below it. The registration itself is unchanged — `Registry.register` has
		// taken a ResourceKey on every version in range (measured).
		//? if >=1.21.11 {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
		//?} else {
		/*return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(path));
		*///?}
	}

	public static void initialize() {
		// Forces static initialization at mod init time.
	}
}
