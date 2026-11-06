package net.phoenix.client.branding.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Accesses the inherited feature registration method on LivingEntityRenderer. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {
    @Invoker("addFeature")
    boolean phoenix$invokeAddFeature(FeatureRenderer feature);
}
