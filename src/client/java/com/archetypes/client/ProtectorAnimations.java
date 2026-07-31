package com.archetypes.client;

import com.archetypes.Archetypes;
import com.archetypes.ModState;
import com.zigythebird.playeranim.accessors.IAnimatedAvatar;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;

import com.archetypes.platform.ArchetypeStore;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Shield Sweep's swing, on its own PAL layer.
 *
 * <p>Same drive shape as {@link SlayerAnimations} and {@link DaggerAnimations},
 * and for the reason spelled out in the first of them: PAL only consults a
 * controller's own state handler while the controller is active or the player
 * is being rendered, so a stopped controller on an unrendered player — the
 * first-person case — would never start. A client tick pass therefore mirrors
 * the synced stamp into explicit {@code triggerAnimation} calls, which force
 * the controller RUNNING from any state.
 *
 * <h2>Three files, because a mirror is not a rotation</h2>
 * The sweep opens from the pose vanilla's own {@code poseBlockingArm} holds a
 * raised shield in and swings OUTWARD, so which arm is doing it decides which
 * way it goes: the right arm leaves to the player's right, the left arm to
 * their left, and two shields do both at once from the same centre. Those are
 * three different bone tracks, not one track played differently, so they are
 * three files — {@code shield_sweep_right}, {@code _left} and {@code _dual} —
 * generated from a single authored arm track so the mirror is exact.
 *
 * <p>The choice is made per ARM rather than per HAND. For the ordinary
 * right-handed player that is the sketch's "main hand swings to the right,
 * off-hand swings to the left" exactly; for a left-handed one it keeps each
 * arm sweeping out on its own side, which is the only reading that stays
 * physical.
 *
 * <p><b>Which arms hold shields is not synced.</b> It does not have to be:
 * held items are tracked equipment, so an onlooker's client already knows
 * whether this player has one shield or two. All the server publishes is the
 * TICK, because {@code player.swing} is broadcast for every bash and only a
 * capstone holder's bash is a sweep.
 */
public final class ProtectorAnimations {
	private static final Identifier LAYER_ID = Archetypes.id("protector_pose");
	private static final Identifier SWEEP_RIGHT = Archetypes.id("shield_sweep_right");
	private static final Identifier SWEEP_LEFT = Archetypes.id("shield_sweep_left");
	private static final Identifier SWEEP_DUAL = Archetypes.id("shield_sweep_dual");

	/**
	 * The window the pose owns, in ticks — and it must equal the files' own
	 * {@code stopTick}. Longer would re-trigger and loop a one-shot; shorter
	 * would call {@code stop()} mid-swing and snap the arms back to vanilla.
	 * All three files end on an all-zero keyframe at tick 8, so a stop anywhere
	 * in the two-tick tail is invisible.
	 */
	private static final int SWEEP_TICKS = 10;

	private ProtectorAnimations() {
	}

	public static void initialize() {
		// 1000 Slayer, 1001 the ritual, 1002 the Elementalist channel, 1003 the
		// dagger stab; the shield sweep takes the next slot up. Nothing shares
		// an arm with it, so the ordering only has to be unique.
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 1004, avatar -> {
			// STOP is the whole handler: we only ever trigger, and the handler
			// is consulted just once more, after the one-shot has finished.
			PlayerAnimationController controller =
					new PlayerAnimationController(avatar, (ctrl, data, setter) -> PlayState.STOP);

			// A swing only onlookers can see is not a swing. Swapping the
			// vanilla arms for the animated third-person model is what puts the
			// sweep on the caster's own screen; the flags keep both arms and the
			// main-hand item drawn and hide the off-hand one, which would
			// otherwise float where the animated left arm no longer is.
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
				drive(player, client.level.getGameTime());
			}
		});
	}

	private static void drive(final AbstractClientPlayer player, final long now) {
		if (!(((Avatar) player) instanceof IAnimatedAvatar animated)
				|| !(animated.playerAnimLib$getAnimation(LAYER_ID)
						instanceof PlayerAnimationController controller)) {
			return;
		}

		Long sweptAt = ArchetypeStore.INSTANCE.get(player, ModState.SHIELD_SWEEP_AT);
		// Ticks the swing is already into itself: normally 0 on the tick the
		// stamp arrives, but not for a client that learned late.
		long since = sweptAt == null ? 0L : now - sweptAt;
		boolean sweeping = sweptAt != null && since >= 0 && since < SWEEP_TICKS;

		if (sweeping && !controller.isActive()) {
			Identifier animation = animationFor(player);

			if (animation != null) {
				// Entered where it already is rather than at zero, so a client
				// that heard about the cast late still sees the swing land on
				// the tick the server resolved it.
				controller.triggerAnimation(animation, since);
			}
		} else if (!sweeping && controller.isActive()
				&& controller.getTriggeredAnimation() != null) {
			controller.stop();
		}
	}

	/** Which arms are carrying a shield right now, read off tracked equipment. */
	private static @Nullable Identifier animationFor(final AbstractClientPlayer player) {
		boolean main = player.getMainHandItem().is(Items.SHIELD);
		boolean off = player.getOffhandItem().is(Items.SHIELD);

		if (main && off) {
			return SWEEP_DUAL;
		}

		if (!main && !off) {
			// The shield left the hand between the stamp and this frame. Nothing
			// to sweep with, so nothing is played rather than a guess.
			return null;
		}

		// The server prefers the OFF hand when only one shield is carried, so
		// this asks the same question in the same order.
		HumanoidArm arm = off ? player.getMainArm().getOpposite() : player.getMainArm();
		return arm == HumanoidArm.RIGHT ? SWEEP_RIGHT : SWEEP_LEFT;
	}
}
