package com.archetypes.client;

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
	/** Vanilla's grey heart set, keyed by the same four flags its sprite names
	 * carry. Withered is what the game already draws for a player rotting on
	 * their feet — the undead read is vanilla's own, not an invention. */
	private static final String WITHERED = "hud/heart/withered_";
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
	 * mount's business.
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

		// Vanilla ships the full eight-way withered set, so the flags carried
		// by the name being replaced map one-for-one onto a name that exists.
		StringBuilder drained = new StringBuilder(WITHERED);

		if (path.contains("hardcore")) {
			drained.append("hardcore_");
		}

		drained.append(path.contains("half") ? "half" : "full");

		if (path.contains("blinking")) {
			drained.append("_blinking");
		}

		return Identifier.withDefaultNamespace(drained.toString());
	}
}
