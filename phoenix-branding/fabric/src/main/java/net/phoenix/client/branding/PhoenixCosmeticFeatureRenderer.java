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

/** Original procedural cuboid cosmetics; uses Minecraft installed block textures. */
public final class PhoenixCosmeticFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private final ModelPart cape, leftWing, rightWing, headwear, backpack;
    public PhoenixCosmeticFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
        ModelData data = new ModelData(); ModelPartData root = data.getRoot();
        root.addChild("cape", ModelPartBuilder.create().uv(0,0).cuboid(-5,0,0,10,16,1), ModelTransform.pivot(0,0,2.2F));
        root.addChild("left_wing", ModelPartBuilder.create().uv(0,0).cuboid(-1,-2,-1,5,9,2).uv(0,0).cuboid(2,3,-0.5F,5,8,1), ModelTransform.pivot(-4.5F,0,2));
        root.addChild("right_wing", ModelPartBuilder.create().uv(0,0).cuboid(-4,-2,-1,5,9,2).uv(0,0).cuboid(-7,3,-0.5F,5,8,1), ModelTransform.pivot(4.5F,0,2));
        root.addChild("headwear", ModelPartBuilder.create().uv(0,0).cuboid(-4.5F,-9,-4.5F,9,1,9).uv(0,0).cuboid(-2,-11,-2,4,2,4), ModelTransform.pivot(0,0,0));
        root.addChild("backpack", ModelPartBuilder.create().uv(0,0).cuboid(-4,-3,2.5F,8,8,3).uv(0,0).cuboid(-2,-5,3,4,2,2), ModelTransform.pivot(0,0,0));
        ModelPart model = TexturedModelData.of(data,16,16).createModel();
        cape=model.getChild("cape"); leftWing=model.getChild("left_wing"); rightWing=model.getChild("right_wing");
        headwear=model.getChild("headwear"); backpack=model.getChild("backpack");
    }
    private static Identifier texture(String colour) { return Identifier.of("minecraft","textures/block/"+colour+"_wool.png"); }
    private static Identifier capeTexture(String id) {
        return switch(id) { case "frost_cape","ocean_cape" -> texture("light_blue"); case "nebula_cape","shadow_cape","galaxy_cape" -> texture("purple"); case "royal_cape","crimson_cape" -> texture("red"); case "aurora_cape" -> texture("lime"); case "moss_cape" -> texture("green"); case "royal_guard_cape" -> texture("yellow"); default -> texture("orange"); };
    }
    private static Identifier wingTexture(String id) {
        return switch(id) { case "frost_wings","celestial_wings","fairy_wings" -> texture("light_blue"); case "void_wings","dragon_wings" -> texture("purple"); case "pixel_glider" -> texture("white"); case "bat_wings" -> texture("black"); case "mechanical_wings" -> texture("gray"); default -> texture("orange"); };
    }
    private static Identifier headTexture(String id) {
        return switch(id) { case "starlight_halo","wizard_hat" -> texture("purple"); case "cyber_visor","respirator" -> texture("cyan"); case "cat_ears" -> texture("pink"); case "horns","pixel_shades" -> texture("black"); case "samurai_helmet","oni_mask","scarf_red" -> texture("red"); case "crown_ice" -> texture("light_blue"); case "astronaut_helmet" -> texture("gray"); case "fox_mask" -> texture("orange"); case "halo_lunar" -> texture("white"); case "gold_monocle","halo_solar","earrings_star" -> texture("yellow"); default -> texture("yellow"); };
    }
    @Override public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        PhoenixLoadoutState.Loadout l=PhoenixLoadoutState.get(player.getUuid()); if(l==null || player.isInvisible()) return;
        if(!l.cape().isEmpty()) { matrices.push(); cape.pitch=0.08F+(float)Math.sin((player.age+tickDelta)*0.08F)*0.035F+Math.min(0.5F,limbDistance*0.12F); cape.yaw=0; cape.roll=0; cape.render(matrices,vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(capeTexture(l.cape()))),light,OverlayTexture.DEFAULT_UV); matrices.pop(); }
        if(!l.wings().isEmpty()) { matrices.push(); float flap=(float)Math.sin((player.age+tickDelta)*0.18F)*0.28F; leftWing.roll=-0.18F-flap; rightWing.roll=0.18F+flap; leftWing.pitch=0.04F; rightWing.pitch=0.04F; var b=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(wingTexture(l.wings()))); leftWing.render(matrices,b,light,OverlayTexture.DEFAULT_UV); rightWing.render(matrices,b,light,OverlayTexture.DEFAULT_UV); matrices.pop(); }
        if(!l.head().isEmpty()) { matrices.push(); headwear.render(matrices,vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(headTexture(l.head()))),light,OverlayTexture.DEFAULT_UV); matrices.pop(); }
        if(!l.back().isEmpty()) { matrices.push(); backpack.render(matrices,vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(texture("brown"))),light,OverlayTexture.DEFAULT_UV); matrices.pop(); }
    }
}