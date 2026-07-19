package com.archetypes.client;

import com.archetypes.Archetypes;
import com.archetypes.NightForm;
import com.zigythebird.playeranim.accessors.IAnimatedAvatar;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;

/**
 * The Dark Ritual's pose, on its own PAL layer.
 *
 * <p>Same drive shape as {@link SlayerAnimations} and for the same reason: the
 * controller's state handler is never consulted for an unrendered player, so
 * the channel is mirrored into explicit trigger/stop calls from a client tick
 * pass instead. A separate layer id keeps the ritual off the Slayer poses —
 * nothing shares a Cutpurse with a greatsword, but the two are unrelated
 * concerns and one stop() must never cancel the other.
 *
 * <p>The animation is authored at exactly {@code DARK_RITUAL_CHANNEL_TICKS}
 * long, so its arms reach overhead on the tick the form takes; an interrupt
 * stops it wherever it stands.
 */
public final class NightAnimations {
	private static final Identifier LAYER_ID = Archetypes.id("night_pose");
	private static final Identifier RITUAL_ANIM = Archetypes.id("dark_ritual");

	private NightAnimations() {
	}

	public static void initialize() {
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 1001, avatar -> {
			PlayerAnimationController controller =
					new PlayerAnimationController(avatar, (ctrl, data, setter) -> PlayState.STOP);
			// The ritual is worth watching from inside your own head, so the
			// animated third-person model replaces the vanilla arms while it
			// runs — the rising hands are the whole point of the pose.
			controller.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
			controller.setFirstPersonConfiguration(
					new FirstPersonConfiguration(true, true, true, false));
			return controller;
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				return;
			}

			for (AbstractClientPlayer player : client.level.players()) {
				drive(player);
			}
		});
	}

	private static void drive(final AbstractClientPlayer player) {
		if (!(((Avatar) player) instanceof IAnimatedAvatar animated)
				|| !(animated.playerAnimLib$getAnimation(LAYER_ID)
						instanceof PlayerAnimationController controller)) {
			return;
		}

		boolean channelling = NightForm.isChannelling(player);

		if (channelling && !controller.isActive()) {
			controller.triggerAnimation(RITUAL_ANIM);
		} else if (!channelling && controller.isActive()
				&& controller.getTriggeredAnimation() != null) {
			controller.stop();
		}
	}
}
