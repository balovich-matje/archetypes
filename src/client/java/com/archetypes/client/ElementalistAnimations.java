package com.archetypes.client;

import com.archetypes.Archetypes;
import com.archetypes.SeekerSpells;
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
 * The Flamethrower's aimed-wand pose, on its own PAL layer.
 *
 * <p>Same drive shape as {@link SlayerAnimations} and {@link NightAnimations},
 * and for the same reason spelled out there: PAL only consults a controller's
 * own state handler while the controller is active or its player is being
 * rendered, so a stopped controller on an unrendered player — the first-person
 * case, which is the one this pose exists for — would never start itself.
 * A client tick pass mirrors the synced channel state into explicit
 * trigger/stop calls instead, which force the controller RUNNING from any
 * state, on every camera and for onlookers alike.
 *
 * <p>The pose is a LOOP, not a one-shot, because the channel has no length: it
 * runs while the key is held. That is the whole difference from the Dark
 * Ritual, which is authored at exactly its channel's length and ends itself.
 * {@code flame_channel.json} therefore loops a slow breath around one held
 * aiming stance and is stopped from here the moment the stream does — the
 * alternative, a one-shot that ends on its own, would be re-triggered by the
 * next tick of this same pass and visibly restart every couple of seconds.
 *
 * <p>The layer id and priority are its own so a stop() here can never cancel a
 * Slayer pose or the ritual: nothing shares an Elementalist with a greatsword,
 * but the layers are separate concerns and are kept separable.
 */
public final class ElementalistAnimations {
	private static final Identifier LAYER_ID = Archetypes.id("elementalist_pose");
	private static final Identifier FLAME_ANIM = Archetypes.id("flame_channel");

	private ElementalistAnimations() {
	}

	public static void initialize() {
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 1002, avatar -> {
			PlayerAnimationController controller =
					new PlayerAnimationController(avatar, (ctrl, data, setter) -> PlayState.STOP);

			// The pose is FOR the first-person view before anyone else's: the
			// complaint was that the caster's own wand stayed up by their ear
			// while fire poured out of the middle of the screen. Swapping the
			// vanilla arms for the animated model is what lets the caster see
			// the wand come down and point where they are aiming.
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

	/** Mirror the player's synced channel state onto their pose controller. */
	private static void drive(final AbstractClientPlayer player) {
		if (!(((Avatar) player) instanceof IAnimatedAvatar animated)
				|| !(animated.playerAnimLib$getAnimation(LAYER_ID)
						instanceof PlayerAnimationController controller)) {
			return;
		}

		// One question, asked of the same synced attachment the server prices
		// the channel from, so the pose can never outlive the stream or lag it
		// by more than the grace window they share.
		boolean channelling = SeekerSpells.isChannellingFlame(player);

		if (channelling && !controller.isActive()) {
			controller.triggerAnimation(FLAME_ANIM);
		} else if (!channelling && controller.isActive()
				&& controller.getTriggeredAnimation() != null) {
			controller.stop();
		}
	}
}
