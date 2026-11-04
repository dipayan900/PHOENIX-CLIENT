package net.phoenix.client.branding.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.phoenix.client.branding.PhoenixBrandingClient;
import net.phoenix.client.branding.PhoenixHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void phoenix$appendHealthHearts(Entity entity, CallbackInfoReturnable<Text> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(entity instanceof net.minecraft.entity.LivingEntity living)) return;
        Text current = cir.getReturnValue();
        if (current == null) return;
        if (entity instanceof PlayerEntity && client.player != null
                && client.player.getUuid().equals(entity.getUuid())
                && !current.getString().startsWith(PhoenixBrandingClient.PREFIX)) {
            current = Text.literal(PhoenixBrandingClient.PREFIX).append(current);
        }
        if (PhoenixHud.isHealthIndicatorEnabled()) {
            float max = Math.max(1.0f, living.getMaxHealth());
            int filled = Math.max(0, Math.min(10, Math.round((living.getHealth() / max) * 10.0f)));
            String hearts = "❤".repeat(filled) + "♡".repeat(10 - filled);
            current = current.copy().append(Text.literal("  " + hearts)
                    .formatted(net.minecraft.util.Formatting.RED));
        }
        cir.setReturnValue(current);
    }

    // Minecraft 1.21.11 changed EntityRenderer.hasLabel to accept
    // (Entity, double). Keep this handler version-specific.
    @Inject(method = "hasLabel", at = @At("RETURN"), cancellable = true)
    private void phoenix$showLivingHealthLabels(Entity entity, double squaredDistance,
            CallbackInfoReturnable<Boolean> cir) {
        if (!PhoenixHud.isHealthIndicatorEnabled() || !(entity instanceof net.minecraft.entity.LivingEntity)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.squaredDistanceTo(entity) <= 256.0) {
            cir.setReturnValue(true);
        }
    }
}
