package com.archetypes;

import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Whether the damage being resolved right now came out of a player actually
 * swinging their weapon.
 *
 * <h2>Why this has to exist</h2>
 * A {@code DamageSource} cannot answer the question. Iron Spikes reflects with
 * {@code damageSources().thorns(player)} and Rend pulses with
 * {@code playerAttack(source)} — deliberately, so a bleed kill still credits
 * the sword that opened the wound — and in both the entity AND the direct
 * entity are the player, which is exactly what a swing looks like. So the
 * Slayer's on-hit passives fired for damage nobody swung for, and the author
 * found what that composes into: raise a shield with Iron Spikes, and every
 * blocked hit reflected thorns that applied Rend, and every Rend pulse rolled
 * Blade Dance, and every lash and every pulse fed Taste of Blood.
 *
 * <p>The fix is a positive signal rather than a list of exceptions. A swing is
 * a swing because it came through {@code Player.attack} (see
 * {@code PlayerMixin}), which is the one path a mouse click takes — and, since
 * the Colossus Slayer's parry answers a parried blow with
 * {@code player.attack(attacker)}, the parry's counter is a swing for free
 * while the same parry's reflected damage is not.
 *
 * <p>The tree's own sword actives {@link #begin} around their damage too:
 * Decimate and a Bladestorm volley are swings in every sense the player cares
 * about, and nothing about this change was meant to stop them counting.
 *
 * <h2>Nesting</h2>
 * {@link #begin} returns the previous swinger and {@link #end} puts it back, so
 * a swing that provokes a swing (Blade Dance's lash, a parry's counter inside a
 * hit) unwinds correctly instead of clearing the flag its caller was relying
 * on. Server thread only, like everything that reads it.
 */
public final class MeleeSwing {
	private static @Nullable Entity swinger;

	private MeleeSwing() {
	}

	/** Open a swing by this entity; hand the return value back to {@link #end}. */
	public static @Nullable Entity begin(final Entity entity) {
		Entity previous = swinger;
		swinger = entity;
		return previous;
	}

	/** Close the swing, restoring whatever was open around it. Always in a
	 * {@code finally}: a swing that throws must not leave the flag standing. */
	public static void end(final @Nullable Entity previous) {
		swinger = previous;
	}

	/** Whether this entity is mid-swing right now. */
	public static boolean isSwinging(final Entity entity) {
		return swinger == entity;
	}
}
