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
import com.archetypes.ModItems;
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
import net.minecraft.world.InteractionHand;
//? if >=1.21.11 {
import net.minecraft.world.entity.Avatar;
//?}

/**
 * The dagger's stab, on its own PAL layer.
 *
 * <p>Same drive shape as {@link SlayerAnimations}, and for the reason spelled
 * out there: PAL only consults a controller's own state handler while the
 * controller is active or the player is being rendered, so a stopped controller
 * on an unrendered player — the first-person case — would never start. A client
 * tick pass therefore mirrors the trigger condition into explicit
 * triggerAnimation calls, which force the controller RUNNING from any state.
 * A layer id of its own keeps the stab off the Slayer and ritual poses: nothing
 * stabs mid-bladestorm, but the layers are unrelated concerns and one stop()
 * must never cancel another's pose.
 *
 * <p><b>No new state.</b> The other poses ride synced attachments because the
 * abilities behind them have no vanilla footprint. A swing does: vanilla's
 * {@code LivingEntity.swing} broadcasts a {@code ClientboundAnimatePacket} to
 * every tracking player, and each receiving client answers it by calling
 * {@code swing} on its own copy of the swinger — so {@code swinging},
 * {@code swingingArm} and {@code swingTime} are already correct on every client
 * for every player, and the held item is already tracked equipment. Onlookers
 * therefore see the stab for free; no attachment, no payload, and no swing
 * mixin of our own is needed.
 *
 * <p><b>The trigger edge.</b> {@code swing} sets {@code swingTime} to -1 and the
 * owning entity's tick raises it by one each tick, resetting to 0 and clearing
 * {@code swinging} when the swing expires. So {@code swinging && swingTime == 0}
 * is exactly the first ticked frame of a new swing and nothing else: a swing
 * that ends leaves {@code swinging} false, and a swing re-fired mid-arc (which
 * vanilla allows past the halfway point) goes back to -1 and produces the edge
 * again. That is what makes rapid stabs restart cleanly rather than stack —
 * {@code triggerAnimation} stops the controller and rewinds it to tick 0, so an
 * interrupted stab starts over from the wind-up instead of stuttering. Keybind
 * handling runs before entity ticks, so the local player's fresh swing is
 * already at 0 by the end of the same client tick; a remote swing packet
 * applied after the entity tick is simply caught one tick later.
 *
 * <p>The pose is authored at 7 ticks (see {@code dagger_stab.json}), comfortably
 * inside the ~8.3-tick cooldown of a dagger's -1.6 attack speed, so a player
 * stabbing as fast as the weapon allows always sees a completed thrust.
 *
 * <p><b>Two things about the player-animator JSON schema</b>, both read out of
 * the library's own loader rather than guessed, because the shipped animations
 * do not make them visible. First, {@code dagger_stab.json} declares
 * {@code "version": 3}: below 3 the loader rewrites the bone name
 * {@code torso} to {@code body}, and those are different things — {@code torso}
 * drives the chest model part, while {@code body} is the whole-player transform
 * (and has its pitch and yaw sign-flipped on the way in). Declaring 3 keeps the
 * lean on the chest, upright and unnegated. Second, a move's {@code easing}
 * governs the segment that <em>leaves</em> that keyframe, not the one arriving
 * at it, so the thrust's ease-out is written on the tick-0 move.
 */
public final class DaggerAnimations {
	private static final Identifier LAYER_ID = Archetypes.id("dagger_pose");
	private static final Identifier STAB_ANIM = Archetypes.id("dagger_stab");

	private DaggerAnimations() {
	}

	public static void initialize() {
		// 1000 Slayer, 1001 the ritual, 1002 the Elementalist channel; the stab
		// takes the next slot up. Nothing shares a hand with it, so the ordering
		// only has to be unique, not meaningful.
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 1003, avatar -> {
			// STOP is the whole handler: we only ever trigger, and the handler
			// is consulted just once more, after the one-shot has finished.
			PlayerAnimationController controller =
					new PlayerAnimationController(avatar, (ctrl, data, setter) -> PlayState.STOP);

			// A stab that only onlookers can see is not a stab. Swapping the
			// vanilla arms for the animated third-person model is what puts the
			// thrust on the stabber's own screen; the flags keep both arms and
			// the main-hand item drawn and hide the off-hand one, which would
			// otherwise float where the animated left arm no longer is.
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

	/** Turn the first tick of a main-hand dagger swing into a stab. */
	private static void drive(final AbstractClientPlayer player) {
		// PAL'S API FORKS HERE AND NOWHERE ABOVE, and it forks in exactly one place per file
		// because the library's rename coincides with the vanilla one: 1.1.5 (MC 1.21.1) spells the
		// accessor `IAnimatedPlayer` and hangs it on `AbstractClientPlayer`, where 1.1.9/1.2.x spell
		// it `IAnimatedAvatar` on `Avatar`. MEASURED with `javap -p` on both jars, not read off a
		// changelog: everything these drivers actually call — `registerFactory`, the controller
		// constructor, `triggerAnimation`, `isActive`, `getTriggeredAnimation`, `stop`,
		// `setFirstPersonMode`, `setFirstPersonConfiguration`, `FirstPersonConfiguration(b,b,b,b)`,
		// `PlayState` — is signature-identical across the two, the setters differing only in a
		// return value no call site reads.
		//
		// The cast is the whole of it. `player` is already an `AbstractClientPlayer`, so below the
		// boundary the cast to `Avatar` simply goes.
		//? if >=1.21.11 {
		if (!(((Avatar) player) instanceof IAnimatedAvatar animated)
		//?} else {
		/*if (!(player instanceof IAnimatedPlayer animated)
		*///?}
				|| !(animated.playerAnimLib$getAnimation(LAYER_ID)
						instanceof PlayerAnimationController controller)) {
			return;
		}

		boolean dagger = ModItems.isDagger(player.getMainHandItem());

		if (dagger && player.swinging && player.swingingArm == InteractionHand.MAIN_HAND
				&& player.swingTime == 0) {
			controller.triggerAnimation(STAB_ANIM);
		} else if (!dagger && controller.isActive()
				&& controller.getTriggeredAnimation() != null) {
			// Only a weapon swap cuts a stab short. The pose is a one-shot that
			// ends itself, and it outlives the 6-tick vanilla swing window by a
			// tick, so stopping on "no longer swinging" would clip the recovery.
			controller.stop();
		}
	}
}
//?}
