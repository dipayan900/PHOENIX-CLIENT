package net.phoenix.client.branding.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.phoenix.client.branding.PhoenixBrandingClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void phoenix$markLocalName(Entity entity, CallbackInfoReturnable<Text> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(entity instanceof PlayerEntity) || client.player == null
                || !client.player.getUuid().equals(entity.getUuid())) return;
        Text current = cir.getReturnValue();
        if (current != null && !current.getString().startsWith(PhoenixBrandingClient.PREFIX)) {
            cir.setReturnValue(Text.literal(PhoenixBrandingClient.PREFIX).append(current));
        }
    }
}
