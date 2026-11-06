package net.phoenix.client.branding.mixin;

import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.phoenix.client.branding.mixin.LivingEntityRendererAccessor;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.phoenix.client.branding.PhoenixCosmeticFeatureRendererModern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
abstract class PlayerEntityRendererMixinModern {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void phoenix$addCosmeticLayer(EntityRendererFactory.Context context, boolean slim, CallbackInfo ci) {
        @SuppressWarnings("unchecked")
        FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> renderer =
                (FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>) (Object) this;
        ((LivingEntityRendererAccessor) (Object) this).phoenix$invokeAddFeature(
                new PhoenixCosmeticFeatureRendererModern(renderer));
    }
}
