package net.phoenix.client.branding.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws Phoenix branding over Minecraft's resource-reload overlay while leaving
 * the vanilla overlay lifecycle and fade/removal logic intact.
 */
@Mixin(SplashOverlay.class)
abstract class SplashOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void phoenix$drawLoadingScreen(DrawContext context, int mouseX, int mouseY,
                                           float delta, CallbackInfo ci) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        context.fillGradient(0, 0, width, height, 0xFF08070D, 0xFF24101B);

        int panelWidth = Math.min(320, width - 32);
        int left = (width - panelWidth) / 2;
        int center = width / 2;
        int centerY = height / 2;

        context.fill(left, centerY - 55, left + panelWidth, centerY + 65, 0xCC100D16);
        context.fill(left, centerY - 56, left + panelWidth, centerY - 54, 0xFFFF702E);
        context.fill(left, centerY + 64, left + panelWidth, centerY + 65, 0x884D2930);

        context.drawCenteredTextWithShadow(minecraft.textRenderer,
                Text.literal("PHOENIX"), center, centerY - 31, 0xFFFF8547);
        context.drawCenteredTextWithShadow(minecraft.textRenderer,
                Text.literal("CLIENT INITIALIZING"), center, centerY - 8, 0xFFE8DFEA);
        context.drawCenteredTextWithShadow(minecraft.textRenderer,
                Text.literal("PREPARING YOUR GAME ENVIRONMENT"), center, centerY + 15, 0xFFB8AEBB);

        int barWidth = Math.min(220, panelWidth - 36);
        int barLeft = center - barWidth / 2;
        int barY = centerY + 39;
        context.fill(barLeft, barY, barLeft + barWidth, barY + 3, 0xFF382A36);

        int segmentWidth = Math.max(24, barWidth / 4);
        int travel = Math.max(1, barWidth - segmentWidth);
        int offset = (int) ((System.currentTimeMillis() / 12L) % (travel * 2L));
        if (offset > travel) offset = travel * 2 - offset;
        context.fill(barLeft + offset, barY, barLeft + offset + segmentWidth, barY + 3, 0xFFFF702E);

        context.drawCenteredTextWithShadow(minecraft.textRenderer,
                Text.literal("PHOENIX CLIENT  •  FABRIC 1.21.1"),
                center, height - 22, 0xFF9B909F);
    }
}
