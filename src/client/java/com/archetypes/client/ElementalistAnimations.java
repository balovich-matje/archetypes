package com.archetypes.client;

// ─── STAGE 5: NO PLAYER ANIMATION LIBRARY ON 1.20.1, SO THIS WHOLE DRIVER IS GATED OFF ───
// design §2.2 Option B, the decision in force. PAL's project declares its lowest
// `game_version` as 1.21.1 — there is no artifact for 1.20.1 on any loader at any version —
// so the dependency is absent from that node's script (no `deps.pal` key), the hard
// `depends` line is stripped out of its fabric.mod.json, and the five `*Animations` drivers
// become whole compilation units that produce no class. The pose is lost; nothing else is.
// Every one of these drivers reads either vanilla's own broadcast swing state or a synced
// key the server already owns, so no damage, cooldown or resource cost moves.
//
// THE PREDICATE IS THE FROZEN `>=1.21` ROW AND THE REAL BOUNDARY IS 1.21.1 — a dependency's
// floor rather than an API's, so §3 has no row for it and inventing one (`>=1.21.1`) would
// break the frozen vocabulary for a line that no registered node can tell apart. The two
// nodes it separates are 1.21.1 (PAL 1.1.5, `FkO8Scek`) and 1.20.1 (nothing), and no node
// exists between them. If one ever lands at 1.21.0, this is where to look: it would need
// the same gating, and `>=1.21` would silently hand it a dependency it cannot resolve.
//? if >=1.21 {
import com.archetypes.Archetypes;
import com.archetypes.SeekerSpells;
//? if >=1.21.11 {
import com.zigythebird.playeranim.accessors.IAnimatedAvatar;
//?} else {
/*import com.zigythebird.playeranim.accessors.IAnimatedPlayer;
*///?}
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
//? if >=1.21.11 {
import net.minecraft.world.entity.Avatar;
//?}

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
		// PAL's one API fork — see DaggerAnimations for the measurement.
		//? if >=1.21.11 {
		if (!(((Avatar) player) instanceof IAnimatedAvatar animated)
		//?} else {
		/*if (!(player instanceof IAnimatedPlayer animated)
		*///?}
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
//?}
