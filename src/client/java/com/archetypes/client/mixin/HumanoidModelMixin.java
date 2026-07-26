package com.archetypes.client.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spearwall's guard arm actually goes up.
 *
 * <h2>Why {@code AvatarRendererMixin} was not enough</h2>
 * That mixin turns the shield hand's {@code ArmPose} into {@code BLOCK}, and
 * the pose is written onto the render state — but under Spearwall vanilla then
 * throws it away unread. {@code HumanoidModel.setupAnim} takes this shape when
 * the entity is using an item:
 *
 * <pre>
 * if (usingArmIsRight == mainArmIsRight) {
 *     poseRightArm(state);
 *     if (!state.rightArmPose.affectsOffhandPose()) poseLeftArm(state);
 * } else {
 *     poseLeftArm(state);
 *     if (!state.leftArmPose.affectsOffhandPose()) poseRightArm(state);
 * }
 * </pre>
 *
 * i.e. it poses the USING arm and then poses the other arm only if the using
 * arm's pose does not claim the off hand. {@code ArmPose.SPEAR} is constructed
 * {@code (twoHanded = false, affectsOffhandPose = true)}, so a braced spear
 * takes the branch that never calls the guard arm's {@code pose*Arm} at all —
 * for either hand configuration, because the {@code affectsOffhandPose} guard
 * is duplicated in both arms of the {@code if}. The shield's blocking MESH
 * swapped correctly the whole time ({@code IsUsingItemMixin} answers the item
 * property, which nothing here touches); only the arm stayed down on the shaft.
 *
 * <h2>What this does instead</h2>
 * Runs vanilla's own {@code poseBlockingArm} on the guard arm, at the exact
 * point in {@code setupAnim} where vanilla stopped posing arms — the call to
 * {@code setupAttackAnimation}, which is the next statement after the block
 * above and appears exactly once in the method.
 *
 * <p>Deliberately NOT at {@code TAIL}: everything between that call and the end
 * of {@code setupAnim} — the crouch block, the swim and fall-flying lerps —
 * READS the arm rotations this would be writing. Vanilla adds crouch's
 * {@code +0.4} on top of a posed blocking arm; posing at {@code TAIL} would
 * instead feed the {@code +0.4} into {@code poseBlockingArm}'s own
 * {@code xRot * 0.5}, and land the arm about 11 degrees off vanilla's every
 * time a guarding player sneaks. Injecting where vanilla's arm posing ends
 * reproduces vanilla exactly rather than approximately.
 *
 * <p>The gate is the negative of vanilla's skip, so this fires exactly when
 * vanilla declined to: the entity is using an item, the guard arm is the one
 * that is NOT using it, the guard arm's pose is {@code BLOCK}, and the using
 * arm's pose claims the off hand. Nothing vanilla produces can satisfy that —
 * {@code AvatarRenderer.getArmPose} only reaches its {@code BLOCK} branch when
 * {@code getUsedItemHand() == hand}, so the non-using arm is never {@code BLOCK}
 * unless {@code AvatarRendererMixin} made it one. That is why no extra
 * Spearwall flag is consulted here: the render state already carries the whole
 * decision, and this class stays a pure function of it.
 *
 * <p>Handedness is read off the state ({@code mainArm}), never assumed: the
 * guard arm is whichever arm holds the hand opposite {@code useItemHand}, which
 * for a left-handed player is the mirror of the usual answer.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends HumanoidRenderState> {
	@Shadow
	@Final
	public ModelPart rightArm;

	@Shadow
	@Final
	public ModelPart leftArm;

	/** Vanilla's blocking-arm pose, which is private and has no other caller. */
	@Invoker("poseBlockingArm")
	abstract void archetypes$poseBlockingArm(ModelPart arm, boolean isRightArm);

	@Inject(
			method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/model/HumanoidModel;setupAttackAnimation("
							+ "Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V"))
	private void archetypes$spearwallGuardArm(final HumanoidRenderState state,
			final CallbackInfo ci) {
		if (!state.isUsingItem || state.useItemHand == null || state.mainArm == null) {
			return;
		}

		HumanoidArm using = state.useItemHand == InteractionHand.MAIN_HAND
				? state.mainArm : state.mainArm.getOpposite();
		boolean guardIsRight = using != HumanoidArm.RIGHT;

		HumanoidModel.ArmPose guardPose = guardIsRight ? state.rightArmPose : state.leftArmPose;
		HumanoidModel.ArmPose usingPose = guardIsRight ? state.leftArmPose : state.rightArmPose;

		if (guardPose != HumanoidModel.ArmPose.BLOCK || usingPose == null
				|| !usingPose.affectsOffhandPose()) {
			return;
		}

		this.archetypes$poseBlockingArm(guardIsRight ? this.rightArm : this.leftArm, guardIsRight);
	}
}
