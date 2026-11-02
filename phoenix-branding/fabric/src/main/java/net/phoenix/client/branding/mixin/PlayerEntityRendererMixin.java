package net.phoenix.client.branding.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.phoenix.client.branding.PhoenixCosmeticFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
abstract class PlayerEntityRendererMixin {
    @Shadow protected abstract boolean addFeature(
            FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> feature);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void phoenix$addCosmeticLayer(EntityRendererFactory.Context context, boolean slim, CallbackInfo ci) {
        @SuppressWarnings("unchecked")
        FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> renderer =
                (FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>>) (Object) this;
        addFeature(new PhoenixCosmeticFeatureRenderer(renderer));
    }
}
