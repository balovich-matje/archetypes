package com.archetypes.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.archetypes.Archetypes;
import com.archetypes.ModAttachments;
import com.archetypes.WeaponClass;
import com.zigythebird.playeranim.accessors.IAnimatedAvatar;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;

/**
 * The combat swings: every change of the synced melee counter plays the next
 * pose in the weapon class's cycle, on the swinger and every onlooker. Same
 * trigger-driven pattern as the capstone poses — the state handler never
 * starts anything (PAL wouldn't consult it on unrendered players) — but on
 * its own layer BELOW the capstones, so a bladestorm channel outranks a
 * mid-storm click.
 */
public final class MeleeAnimations {
	private static final Identifier LAYER_ID = Archetypes.id("melee_swing");

	/** Animation names per weapon class, in cycle order. */
	private static final Map<WeaponClass, Identifier[]> POSES = Map.of(
			WeaponClass.GREATSWORD, new Identifier[] {
					Archetypes.id("gs_arc_lr"), Archetypes.id("gs_arc_rl"), Archetypes.id("gs_overhead")},
			WeaponClass.SWORD, new Identifier[] {
					Archetypes.id("sword_slash_rl"), Archetypes.id("sword_slash_lr")},
			WeaponClass.MACE, new Identifier[] {
					Archetypes.id("mace_overhead"), Archetypes.id("mace_side")},
			WeaponClass.HANDS, new Identifier[] {
					Archetypes.id("punch_r"), Archetypes.id("punch_l")});

	private static final Map<UUID, Integer> LAST_SEEN = new HashMap<>();

	private MeleeAnimations() {
	}

	public static void initialize() {
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 900, avatar -> {
			PlayerAnimationController controller =
					new PlayerAnimationController(avatar, (ctrl, data, setter) -> PlayState.STOP);
			controller.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
			controller.setFirstPersonConfiguration(
					new FirstPersonConfiguration(true, true, true, false));
			return controller;
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				LAST_SEEN.clear();
				return;
			}

			for (AbstractClientPlayer player : client.level.players()) {
				drive(player);
			}
		});
	}

	private static void drive(final AbstractClientPlayer player) {
		Integer swing = ((AttachmentTarget) player).getAttached(ModAttachments.MELEE_SWING);

		if (swing == null || swing.equals(LAST_SEEN.get(player.getUUID()))) {
			return;
		}

		LAST_SEEN.put(player.getUUID(), swing);

		if (!(((Avatar) player) instanceof IAnimatedAvatar animated)
				|| !(animated.playerAnimLib$getAnimation(LAYER_ID)
						instanceof PlayerAnimationController controller)) {
			return;
		}

		WeaponClass weapon = WeaponClass.values()[swing & 0x3];
		Identifier[] poses = POSES.get(weapon);

		if (poses != null) {
			controller.triggerAnimation(poses[(swing >> 2) % poses.length]);
		}
	}
}
