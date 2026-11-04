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
            float maxHealth = Math.max(1.0f, living.getMaxHealth());
            float currentHealth = Math.max(0.0f, Math.min(maxHealth, living.getHealth()));

            // One unit here is half a heart (1 health point). Do not cap at
            // ten hearts: servers and attributes can grant much higher max health.
            int heartSlots = Math.max(1, Math.min(100, (int) Math.ceil(maxHealth / 2.0f)));
            int currentHalfHearts = Math.max(0, Math.min(heartSlots * 2,
                    (int) Math.ceil(currentHealth)));
            int fullHearts = Math.min(heartSlots, currentHalfHearts / 2);
            boolean halfHeart = currentHalfHearts % 2 == 1 && fullHearts < heartSlots;
            int emptyHearts = Math.max(0, heartSlots - fullHearts - (halfHeart ? 1 : 0));

            String hearts = "❤".repeat(fullHearts)
                    + (halfHeart ? "◐" : "")
                    + "♡".repeat(emptyHearts);
            current = current.copy().append(Text.literal("  " + hearts)
                    .formatted(net.minecraft.util.Formatting.RED));

            // Absorption is additional health and is displayed separately in gold.
            int absorptionHalfHearts = Math.max(0, Math.min(200, (int) Math.ceil(living.getAbsorptionAmount())));
            if (absorptionHalfHearts > 0) {
                int absorptionFull = absorptionHalfHearts / 2;
                boolean absorptionHalf = absorptionHalfHearts % 2 == 1;
                String absorption = "❤".repeat(absorptionFull) + (absorptionHalf ? "◐" : "");
                current = current.copy().append(Text.literal(" " + absorption)
                        .formatted(net.minecraft.util.Formatting.GOLD));
            }
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
