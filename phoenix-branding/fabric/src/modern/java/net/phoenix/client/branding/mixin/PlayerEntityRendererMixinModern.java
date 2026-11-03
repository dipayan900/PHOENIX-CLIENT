package net.phoenix.client.branding.mixin;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.phoenix.client.branding.PhoenixCosmeticFeatureRendererModern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
abstract class PlayerEntityRendererMixinModern {
    @Shadow protected abstract boolean addFeature(FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> feature);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void phoenix$addCosmeticLayer(EntityRendererFactory.Context context, boolean slim, CallbackInfo ci) {
        @SuppressWarnings("unchecked")
        FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> renderer =
                (FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>) (Object) this;
        addFeature(new PhoenixCosmeticFeatureRendererModern(renderer));
    }
}
