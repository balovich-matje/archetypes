package com.archetypes.client;

import com.archetypes.Archetypes;
import com.archetypes.ModAttachments;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.resources.Identifier;

/**
 * Experiment branch: real player poses via Player Animation Library. One
 * controller layer per player; its state handler watches the synced
 * bladestorm-end attachment and loops the spin animation while the channel
 * runs — body and camera-independent, first and third person, ours to keyframe.
 */
public final class SlayerAnimations {
	private static final Identifier LAYER_ID = Archetypes.id("slayer_pose");
	private static final Identifier BLADESTORM_ANIM = Archetypes.id("bladestorm");

	private SlayerAnimations() {
	}

	public static void initialize() {
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 1000, avatar -> {
			PlayerAnimationController controller = new PlayerAnimationController(avatar,
					(ctrl, data, setter) -> {
						Long end = ((AttachmentTarget) avatar).getAttached(ModAttachments.BLADESTORM_END);
						boolean storming = end != null && end > avatar.level().getGameTime();

						if (!storming) {
							return PlayState.STOP;
						}

						return PlayerAnimResources.getAnimationOptional(BLADESTORM_ANIM)
								.map(animation -> setter.setAnimation(RawAnimation.begin().thenLoop(animation)))
								.orElse(PlayState.STOP);
					});

			// In first person, swap the vanilla arms for the animated
			// third-person model while the storm runs — otherwise the spin is
			// invisible to the player doing it.
			controller.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
			controller.setFirstPersonConfiguration(
					new FirstPersonConfiguration(true, true, true, false));
			return controller;
		});
	}
}
