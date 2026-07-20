package com.archetypes.client;

import com.archetypes.Archetypes;
import com.archetypes.NightForm;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * What the vanilla bars become while the player is a creature of the night:
 * the hearts lose their colour and the hunger row goes away entirely (the form
 * pins hunger full and stops natural regeneration, so a hunger bar would be a
 * gauge of nothing).
 *
 * <p>Both effects are pure display and both revert the tick the form lapses —
 * {@link #active} is the single gate, read fresh every frame, so there is no
 * state to restore and nothing to leak if the form ends in an unusual way
 * (death, respec, a lapse while the screen is up).
 */
public final class UndeadHud {
	/** Our own grey heart set, keyed by the same flags vanilla's sprite names
	 * carry. NOT vanilla's withered set: that sprite means the Wither's effect
	 * and must keep meaning only that. Ours is vanilla's exact heart geometry
	 * with the red ramp swapped for a grey one, so it still aligns pixel for
	 * pixel with the container sockets behind it. */
	private static final String GREY = "hud/heart/grey_";
	private static final String HEART_PREFIX = "hud/heart/";

	private UndeadHud() {
	}

	/** Whether the LOCAL player is transformed. Every use is per-frame display,
	 * so this asks the client's own player and nobody else's. */
	public static boolean active() {
		Player player = Minecraft.getInstance().player;
		return player != null && NightForm.isActive(player);
	}

	/**
	 * A heart sprite drained of its colour, or the sprite unchanged when the
	 * player is mortal. Containers (the empty sockets) and vehicle hearts pass
	 * through: the sockets are already grey, and a mount's health is the
	 * mount's business. Withered hearts pass through as well — a vampire who
	 * is ALSO withering should read as withering.
	 */
	public static Identifier drain(final Identifier sprite) {
		if (!active() || !sprite.getNamespace().equals("minecraft")) {
			return sprite;
		}

		String path = sprite.getPath();

		if (!path.startsWith(HEART_PREFIX) || path.contains("container")
				|| path.contains("vehicle") || path.contains("withered")) {
			return sprite;
		}

		// We ship all eight combinations of the flags that survive this
		// mapping (hardcore x half x blinking), so every name reachable here
		// maps onto a sprite that exists. The poisoned/frozen/absorbing
		// variants collapse into the plain grey ones on purpose: while
		// transformed, the health row says one thing.
		StringBuilder drained = new StringBuilder(GREY);

		if (path.contains("hardcore")) {
			drained.append("hardcore_");
		}

		drained.append(path.contains("half") ? "half" : "full");

		if (path.contains("blinking")) {
			drained.append("_blinking");
		}

		return Archetypes.id(drained.toString());
	}
}
