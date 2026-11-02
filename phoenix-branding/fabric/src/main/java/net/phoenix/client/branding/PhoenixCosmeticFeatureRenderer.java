package net.phoenix.client.branding;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Procedural, original cuboid meshes attached to the player model. No third-party model files. */
public final class PhoenixCosmeticFeatureRenderer
        extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier CAPE_TEXTURE = Identifier.of("minecraft", "textures/block/red_wool.png");
    private static final Identifier WING_TEXTURE = Identifier.of("minecraft", "textures/block/light_blue_wool.png");
    private final ModelPart cape;
    private final ModelPart leftWing;
    private final ModelPart rightWing;

    public PhoenixCosmeticFeatureRenderer(
            FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("cape", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-5.0F, 0.0F, 0.0F, 10.0F, 16.0F, 1.0F),
                ModelTransform.pivot(0.0F, 0.0F, 2.2F));
        root.addChild("left_wing", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-1.0F, -2.0F, -1.0F, 5.0F, 9.0F, 2.0F)
                .uv(0, 0).cuboid(2.0F, 3.0F, -0.5F, 5.0F, 8.0F, 1.0F),
                ModelTransform.pivot(-4.5F, 0.0F, 2.0F));
        root.addChild("right_wing", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-4.0F, -2.0F, -1.0F, 5.0F, 9.0F, 2.0F)
                .uv(0, 0).cuboid(-7.0F, 3.0F, -0.5F, 5.0F, 8.0F, 1.0F),
                ModelTransform.pivot(4.5F, 0.0F, 2.0F));
        ModelPart model = TexturedModelData.of(data, 16, 16).createModel();
        cape = model.getChild("cape");
        leftWing = model.getChild("left_wing");
        rightWing = model.getChild("right_wing");
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light,
                       AbstractClientPlayerEntity player, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        PhoenixLoadoutState.Loadout loadout = PhoenixLoadoutState.get(player.getUuid());
        if (loadout == null || player.isInvisible()) return;

        if (!loadout.cape().isEmpty()) {
            matrices.push();
            float sway = (float)Math.sin((player.age + tickDelta) * 0.08F) * 0.035F;
            cape.pitch = 0.08F + sway + Math.min(0.5F, limbDistance * 0.12F);
            cape.yaw = 0.0F;
            cape.roll = 0.0F;
            cape.render(matrices, vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(CAPE_TEXTURE)),
                    light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }

        if (!loadout.wings().isEmpty()) {
            matrices.push();
            float flap = (float)Math.sin((player.age + tickDelta) * 0.18F) * 0.28F;
            leftWing.roll = -0.18F - flap;
            rightWing.roll = 0.18F + flap;
            leftWing.pitch = 0.04F;
            rightWing.pitch = 0.04F;
            var buffer = vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(WING_TEXTURE));
            leftWing.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV);
            rightWing.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
    }
}
