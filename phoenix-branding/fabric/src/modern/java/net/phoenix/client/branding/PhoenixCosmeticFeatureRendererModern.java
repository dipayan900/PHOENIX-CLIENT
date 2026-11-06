package net.phoenix.client.branding;


import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Procedural, layered 3D cosmetic meshes.  Separate mesh layers and material
 * textures give the accessories visible depth instead of one flat silhouette.
 */
public final class PhoenixCosmeticFeatureRendererModern extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private final ModelPart cape, capeTrim, capeLining, customCape;
    private final ModelPart leftWing, rightWing, leftFeathers, rightFeathers;
    private final ModelPart headwear, crown, halo, visor, faceMask, horns;
    private final ModelPart backpack, packDetails;
    private final ModelPart tailBase, tailMid, tailTip;
    private final ModelPart leftBracer, rightBracer, shoulderPet;

    public PhoenixCosmeticFeatureRendererModern(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        root.addChild("cape", ModelPartBuilder.create().uv(0,0)
                .cuboid(-5,0,0,10,15,1).cuboid(-4.5F,1,1,9,13,0.5F),
                ModelTransform.of(0,0,2.2F, 0, 0, 0));
        root.addChild("cape_trim", ModelPartBuilder.create().uv(0,0)
                .cuboid(-5.5F,0,0,1,16,1.5F).cuboid(4.5F,0,0,1,16,1.5F)
                .cuboid(-5,15,0,10,1,1.5F), ModelTransform.of(0,0,2.2F, 0, 0, 0));
        root.addChild("cape_lining", ModelPartBuilder.create().uv(0,0)
                .cuboid(-3.5F,2,1.1F,7,11,0.35F), ModelTransform.of(0,0,2.2F, 0, 0, 0));

        root.addChild("left_wing", ModelPartBuilder.create().uv(0,0)
                .cuboid(-1,-2,-1,4,7,2).cuboid(0,1,-0.7F,5,8,1.4F)
                .cuboid(2,5,-0.5F,5,7,1), ModelTransform.of(-4.5F,0,2, 0, 0, 0));
        root.addChild("right_wing", ModelPartBuilder.create().uv(0,0)
                .cuboid(-3,-2,-1,4,7,2).cuboid(-5,1,-0.7F,5,8,1.4F)
                .cuboid(-7,5,-0.5F,5,7,1), ModelTransform.of(4.5F,0,2, 0, 0, 0));
        // Individually layered feather vanes create a visibly dimensional fan,
        // rather than a single flat wing silhouette. Each feather has its own
        // volume and sits at a slightly different depth.
        root.addChild("left_feathers", ModelPartBuilder.create().uv(0,0)
                .cuboid(0,2,-1.2F,2,8,1.8F).cuboid(2,3,-1.0F,2,9,1.7F)
                .cuboid(4,4,-0.8F,2,9,1.6F).cuboid(6,5,-0.6F,2,8,1.5F)
                .cuboid(8,7,-0.4F,2,7,1.4F).cuboid(1,8,-1.4F,2,7,1.8F)
                .cuboid(3,10,-1.2F,2,7,1.7F).cuboid(5,11,-1.0F,2,6,1.6F)
                .cuboid(7,12,-0.8F,2,5,1.5F).cuboid(9,13,-0.6F,2,4,1.4F)
                .cuboid(-1,5,-0.8F,2,6,1.5F),
                ModelTransform.of(-4.5F,0,2, 0, 0, 0));
        root.addChild("right_feathers", ModelPartBuilder.create().uv(0,0)
                .cuboid(-2,2,-1.2F,2,8,1.8F).cuboid(-4,3,-1.0F,2,9,1.7F)
                .cuboid(-6,4,-0.8F,2,9,1.6F).cuboid(-8,5,-0.6F,2,8,1.5F)
                .cuboid(-10,7,-0.4F,2,7,1.4F).cuboid(-3,8,-1.4F,2,7,1.8F)
                .cuboid(-5,10,-1.2F,2,7,1.7F).cuboid(-7,11,-1.0F,2,6,1.6F)
                .cuboid(-9,12,-0.8F,2,5,1.5F).cuboid(-11,13,-0.6F,2,4,1.4F)
                .cuboid(-1,5,-0.8F,2,6,1.5F),
                ModelTransform.of(4.5F,0,2, 0, 0, 0));

        root.addChild("headwear", ModelPartBuilder.create().uv(0,0)
                .cuboid(-4.5F,-9,-4.5F,9,1,9).cuboid(-2,-11,-2,4,2,4),
                ModelTransform.of(0,0,0, 0, 0, 0));
        root.addChild("crown", ModelPartBuilder.create().uv(0,0)
                .cuboid(-4.5F,-9,-4.5F,9,1,9)
                .cuboid(-4,-12,-1,1.5F,3,1.5F).cuboid(-1,-13,-1,2,4,2)
                .cuboid(2.5F,-12,-1,1.5F,3,1.5F),
                ModelTransform.of(0,0,0, 0, 0, 0));
        root.addChild("halo", ModelPartBuilder.create().uv(0,0)
                .cuboid(-4.5F,-12,-0.5F,9,0.8F,1)
                .cuboid(-0.5F,-12,-4.5F,1,0.8F,9)
                .cuboid(-3.5F,-12,-3.5F,1,0.8F,1)
                .cuboid(2.5F,-12,-3.5F,1,0.8F,1)
                .cuboid(-3.5F,-12,2.5F,1,0.8F,1)
                .cuboid(2.5F,-12,2.5F,1,0.8F,1),
                ModelTransform.of(0,0,0, 0, 0, 0));
        root.addChild("visor", ModelPartBuilder.create().uv(0,0)
                .cuboid(-4,-6,-4.7F,8,2,1).cuboid(-3.5F,-5,-4.8F,7,1,0.8F),
                ModelTransform.of(0,0,0, 0, 0, 0));
        root.addChild("face_mask", ModelPartBuilder.create().uv(0,0)
                .cuboid(-3,-4,-4.65F,6,3,1).cuboid(-2.5F,-3.5F,-4.9F,5,1,0.6F),
                ModelTransform.of(0,0,0, 0, 0, 0));
        root.addChild("horns", ModelPartBuilder.create().uv(0,0)
                .cuboid(-4,-8,-1,2,3,2).cuboid(-4.5F,-11,-1,1.5F,3,1.5F)
                .cuboid(2,-8,-1,2,3,2).cuboid(3,-11,-1,1.5F,3,1.5F),
                ModelTransform.of(0,0,0, 0, 0, 0));

        root.addChild("backpack", ModelPartBuilder.create().uv(0,0)
                .cuboid(-4,-3,2.5F,8,8,3).cuboid(-2,-5,3,4,2,2),
                ModelTransform.of(0,0,0, 0, 0, 0));
        root.addChild("pack_details", ModelPartBuilder.create().uv(0,0)
                .cuboid(-3.5F,-2,5.6F,7,1,0.6F).cuboid(-3.5F,2,5.6F,7,1,0.6F)
                .cuboid(-1,-1,5.7F,2,3,0.7F),
                ModelTransform.of(0,0,0, 0, 0, 0));

        ModelPartData tail = root.addChild("tail_base", ModelPartBuilder.create().uv(0,0)
                .cuboid(-1,0,0,2,5,2), ModelTransform.of(0,0,3, 0, 0, 0));
        ModelPartData mid = tail.addChild("tail_mid", ModelPartBuilder.create().uv(0,0)
                .cuboid(-1,0,0,2,5,2), ModelTransform.of(0,5,0, 0, 0, 0));
        mid.addChild("tail_tip", ModelPartBuilder.create().uv(0,0)
                .cuboid(-1,0,0,2,4,2).cuboid(-1.5F,3,0,3,2,3),
                ModelTransform.of(0,5,0, 0, 0, 0));
        root.addChild("left_bracer", ModelPartBuilder.create().uv(0,0)
                .cuboid(-2,5,-2,4,3,4).cuboid(-1.5F,4.5F,-1.5F,3,1,3),
                ModelTransform.of(-5,2,0, 0, 0, 0));
        root.addChild("right_bracer", ModelPartBuilder.create().uv(0,0)
                .cuboid(-2,5,-2,4,3,4).cuboid(-1.5F,4.5F,-1.5F,3,1,3),
                ModelTransform.of(5,2,0, 0, 0, 0));
        root.addChild("shoulder_pet", ModelPartBuilder.create().uv(0,0)
                .cuboid(-2,-3,-2,4,4,4).cuboid(-1,-5,-1,2,2,2)
                .cuboid(-2.5F,0,-1,1,2,2).cuboid(1.5F,0,-1,1,2,2),
                ModelTransform.of(5,-2,0, 0, 0, 0));

        ModelPart model = TexturedModelData.of(data,16,16).createModel();
        cape=model.getChild("cape"); capeTrim=model.getChild("cape_trim"); capeLining=model.getChild("cape_lining");
        ModelData customCapeData = new ModelData();
        customCapeData.getRoot().addChild("custom_cape",
                ModelPartBuilder.create().uv(0,0).cuboid(-5,0,0,10,16,1),
                ModelTransform.of(0,0,2.2F, 0, 0, 0));
        customCape = TexturedModelData.of(customCapeData,64,32).createModel().getChild("custom_cape");
        leftWing=model.getChild("left_wing"); rightWing=model.getChild("right_wing");
        leftFeathers=model.getChild("left_feathers"); rightFeathers=model.getChild("right_feathers");
        headwear=model.getChild("headwear"); crown=model.getChild("crown"); halo=model.getChild("halo");
        visor=model.getChild("visor"); faceMask=model.getChild("face_mask"); horns=model.getChild("horns");
        backpack=model.getChild("backpack"); packDetails=model.getChild("pack_details");
        tailBase=model.getChild("tail_base"); tailMid=tailBase.getChild("tail_mid"); tailTip=tailMid.getChild("tail_tip");
        leftBracer=model.getChild("left_bracer"); rightBracer=model.getChild("right_bracer");
        shoulderPet=model.getChild("shoulder_pet");
    }

    private static Identifier texture(String colour) {
        return Identifier.of("minecraft","textures/block/"+colour+"_wool.png");
    }
    private static Identifier capeTexture(String id) {
        return switch(id) {
            case "frost_cape","ocean_cape" -> texture("light_blue");
            case "nebula_cape","shadow_cape","galaxy_cape" -> texture("purple");
            case "royal_cape","crimson_cape" -> texture("red");
            case "aurora_cape" -> texture("lime");
            case "moss_cape" -> texture("green");
            case "royal_guard_cape" -> texture("yellow");
            default -> texture("orange");
        };
    }
    private static Identifier wingTexture(String id) {
        return switch(id) {
            case "frost_wings","celestial_wings","fairy_wings" -> texture("light_blue");
            case "void_wings","dragon_wings" -> texture("purple");
            case "pixel_glider" -> texture("white");
            case "bat_wings" -> texture("black");
            case "mechanical_wings" -> texture("gray");
            default -> texture("orange");
        };
    }
    private static Identifier detailTexture(String id) {
        if (id.contains("frost") || id.contains("ice") || id.contains("celestial")) return texture("white");
        if (id.contains("void") || id.contains("shadow") || id.contains("galaxy")) return texture("black");
        if (id.contains("mechanical") || id.contains("robot")) return texture("light_gray");
        if (id.contains("ocean") || id.contains("lunar") || id.contains("cyber")) return texture("cyan");
        if (id.contains("royal") || id.contains("crimson") || id.contains("inferno")) return texture("yellow");
        return texture("yellow");
    }
    private static Identifier headTexture(String id) {
        return switch(id) {
            case "starlight_halo","wizard_hat" -> texture("purple");
            case "cyber_visor","respirator" -> texture("cyan");
            case "cat_ears" -> texture("pink");
            case "horns","pixel_shades" -> texture("black");
            case "samurai_helmet","oni_mask","scarf_red" -> texture("red");
            case "crown_ice" -> texture("light_blue");
            case "astronaut_helmet" -> texture("gray");
            case "fox_mask" -> texture("orange");
            case "halo_lunar" -> texture("white");
            default -> texture("yellow");
        };
    }

    @Override public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light,
            PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        var world = MinecraftClient.getInstance().world;
        if (world == null || state.invisible) return;
        var entity = world.getEntityById(state.id);
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;
        PhoenixLoadoutState.Loadout l=PhoenixLoadoutState.get(player.getUuid());
        Identifier uploadedCape = player == MinecraftClient.getInstance().player
                ? PhoenixLocalAppearance.cape() : null;
        if (uploadedCape != null || (l != null && !l.cape().isEmpty())) {
            matrices.push();
            float sway=(float)Math.sin(state.age*0.08F);
            float pitch=0.08F+sway*0.035F+Math.min(0.5F,limbDistance*0.12F);
            if (uploadedCape != null) {
                customCape.pitch=pitch;
                customCape.yaw=0;
                customCape.roll=sway*0.025F;
                customCape.render(matrices,vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(uploadedCape)),light,OverlayTexture.DEFAULT_UV);
            } else {
                cape.pitch=pitch;
                cape.yaw=0; cape.roll=sway*0.025F;
                var base=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(capeTexture(l.cape())));
                cape.render(matrices,base,light,OverlayTexture.DEFAULT_UV);
                capeTrim.render(matrices,vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(detailTexture(l.cape()))),light,OverlayTexture.DEFAULT_UV);
                capeLining.render(matrices,vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(texture("white"))),light,OverlayTexture.DEFAULT_UV);
            }
            matrices.pop();
        }
        if(l==null) return;
        if(!l.wings().isEmpty()) {
            matrices.push();
            float flap=(float)Math.sin(state.age*0.18F)*0.32F;
            leftWing.roll=-0.18F-flap; rightWing.roll=0.18F+flap;
            leftFeathers.roll=leftWing.roll*0.82F; rightFeathers.roll=rightWing.roll*0.82F;
            leftWing.pitch=0.04F; rightWing.pitch=0.04F;
            var base=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(wingTexture(l.wings())));
            var detail=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(detailTexture(l.wings())));
            leftWing.render(matrices,base,light,OverlayTexture.DEFAULT_UV);
            rightWing.render(matrices,base,light,OverlayTexture.DEFAULT_UV);
            leftFeathers.render(matrices,detail,light,OverlayTexture.DEFAULT_UV);
            rightFeathers.render(matrices,detail,light,OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
        if(!l.head().isEmpty()) {
            matrices.push();
            String id=l.head();
            var main=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(headTexture(id)));
            if(id.contains("halo")) {
                halo.yaw=state.age*0.035F;
                halo.render(matrices,main,light,OverlayTexture.DEFAULT_UV);
            } else if(id.contains("crown")) {
                crown.render(matrices,main,light,OverlayTexture.DEFAULT_UV);
            } else if(id.contains("horn")) {
                horns.render(matrices,main,light,OverlayTexture.DEFAULT_UV);
            } else if(id.contains("visor") || id.contains("shades")) {
                visor.render(matrices,main,light,OverlayTexture.DEFAULT_UV);
            } else if(id.contains("mask") || id.contains("respirator")) {
                faceMask.render(matrices,main,light,OverlayTexture.DEFAULT_UV);
            } else {
                headwear.render(matrices,main,light,OverlayTexture.DEFAULT_UV);
            }
            matrices.pop();
        }
        if(!l.back().isEmpty()) {
            matrices.push();
            String id=l.back();
            if(id.endsWith("_tail")) {
                float sway=(float)Math.sin(state.age*0.16F);
                tailBase.roll=sway*0.18F; tailMid.roll=sway*0.28F+0.12F; tailTip.roll=sway*0.38F+0.2F;
                var b=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(id.equals("fox_tail")?texture("orange"):texture("red")));
                tailBase.render(matrices,b,light,OverlayTexture.DEFAULT_UV);
            } else if(id.equals("robot_arm") || id.equals("crystal_bracers")) {
                var b=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(id.equals("robot_arm")?texture("gray"):texture("light_blue")));
                leftBracer.render(matrices,b,light,OverlayTexture.DEFAULT_UV);
                rightBracer.render(matrices,b,light,OverlayTexture.DEFAULT_UV);
            } else if(id.startsWith("shoulder_")) {
                shoulderPet.render(matrices,vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(texture("orange"))),light,OverlayTexture.DEFAULT_UV);
            } else {
                backpack.render(matrices,vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(texture("brown"))),light,OverlayTexture.DEFAULT_UV);
                packDetails.render(matrices,vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(texture("yellow"))),light,OverlayTexture.DEFAULT_UV);
            }
            matrices.pop();
        }
    }
}
