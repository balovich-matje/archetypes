package com.archetypes.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The skin-layer byte, so a conjured player can wear the caster's second layer.
 *
 * <p>{@code Avatar.DATA_PLAYER_MODE_CUSTOMISATION} is {@code protected static}.
 * Reading which model parts a player shows is public
 * ({@code Avatar.isModelPartShown}), but naming the tracked-data key to put the
 * answer on the wire is not — and a hand-rolled {@code EntityDataAccessor} with
 * a guessed id would be a silent mis-set the first time Mojang inserts a field.
 */
@Mixin(Avatar.class)
public interface AvatarAccessor {
	@Accessor("DATA_PLAYER_MODE_CUSTOMISATION")
	static EntityDataAccessor<Byte> archetypes$modelCustomisation() {
		throw new AssertionError("mixin accessor");
	}
}
