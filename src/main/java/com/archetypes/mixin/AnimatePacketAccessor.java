package com.archetypes.mixin;

import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code ClientboundAnimatePacket}'s only constructor reads the id off a real
 * {@code Entity}. The phalanx's spearmen are entity ids that exist on clients
 * and nowhere else, so the swing that makes them stab has to name one directly:
 * the packet is built around the caster and then re-addressed.
 */
@Mixin(ClientboundAnimatePacket.class)
public interface AnimatePacketAccessor {
	@Mutable
	@Accessor("id")
	void archetypes$setId(int id);
}
