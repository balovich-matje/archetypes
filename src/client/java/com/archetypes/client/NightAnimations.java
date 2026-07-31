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
import com.archetypes.NightForm;
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

// STAGE 6 — only this import and the registration line fork. The loader helpers need no
// import: `NeoForgeClientEvents`/`ForgeClientEvents` live in this same package, which they
// have to — a client helper cannot live behind the `platform` seam (`src/main` cannot see
// `net.minecraft.client` at all).
//? if fabric {
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//?}
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
//? if >=1.21.11 {
import net.minecraft.world.entity.Avatar;
//?}

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

		// Registration only; the driver body is shared. This whole file is already a `>=1.21`
		// compilation unit (no PAL below 1.21.1 on any loader), so the `forge` arm below is
		// unreachable on every node that exists — it is written for symmetry and because a
		// 1.21.1-forge would be the node that needs it. The live loader arm here is NeoForge's.
		//? if fabric {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
		//?} elif neoforge {
		/*NeoForgeClientEvents.endClientTick(client -> {
		*///?} elif forge {
		/*ForgeClientEvents.endClientTick(client -> {
		*///?}
			if (client.level == null) {
				return;
			}

			for (AbstractClientPlayer player : client.level.players()) {
				drive(player);
			}
		});
	}

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

		boolean channelling = NightForm.isChannelling(player);

		if (channelling && !controller.isActive()) {
			controller.triggerAnimation(RITUAL_ANIM);
		} else if (!channelling && controller.isActive()
				&& controller.getTriggeredAnimation() != null) {
			controller.stop();
		}
	}
}
//?}
