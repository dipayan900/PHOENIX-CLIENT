package net.phoenix.client.branding.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.phoenix.client.branding.PhoenixLoadoutState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
abstract class PlayerEntityModelEmoteMixin {
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void phoenix$animateEmote(LivingEntity entity, float limbAngle, float limbDistance,
                                      float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (entity.getWorld().isClient) {
            PhoenixLoadoutState.Loadout l = PhoenixLoadoutState.get(entity.getUuid());
            if (l == null || l.emote().isEmpty()) return;
            float t = (entity.age + animationProgress) * 0.22F;
            switch (l.emote()) {
                case "wave" -> { rightArm.pitch = -1.65F; rightArm.roll = -0.2F + (float)Math.sin(t) * 0.45F; }
                case "salute" -> { rightArm.pitch = -1.45F; rightArm.yaw = 0.12F; }
                case "victory_pose", "spin" -> { rightArm.pitch = -2.55F; leftArm.pitch = -2.55F; body.roll = (float)Math.sin(t) * 0.12F; }
                case "clap" -> { rightArm.pitch = -1.2F; leftArm.pitch = -1.2F; rightArm.yaw = -0.18F; leftArm.yaw = 0.18F; }
                case "bow" -> { body.pitch = 0.45F; headYaw = 0.0F; }
                case "shrug" -> { rightArm.pitch = -1.0F; leftArm.pitch = -1.0F; rightArm.roll = -0.35F; leftArm.roll = 0.35F; }
                case "pixel_dance", "dance2", "moonwalk" -> {
                    rightArm.pitch = -0.8F + (float)Math.sin(t) * 0.45F;
                    leftArm.pitch = -0.8F - (float)Math.sin(t) * 0.45F;
                    rightLeg.pitch += (float)Math.sin(t) * 0.35F;
                    leftLeg.pitch -= (float)Math.sin(t) * 0.35F;
                    body.roll = (float)Math.sin(t) * 0.16F;
                }
                case "laugh" -> { body.pitch = 0.12F + (float)Math.sin(t) * 0.08F; }
                case "sit" -> { rightLeg.pitch = -1.25F; leftLeg.pitch = -1.25F; }
                case "meditate" -> { rightArm.pitch = -1.1F; leftArm.pitch = -1.1F; }
                case "guitar" -> { rightArm.pitch = -1.15F; leftArm.pitch = -0.9F; rightArm.roll = -0.25F; }
                default -> { }
            }
        }
    }
}
