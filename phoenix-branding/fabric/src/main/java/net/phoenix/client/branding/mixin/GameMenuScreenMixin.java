package net.phoenix.client.branding.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.phoenix.client.branding.PhoenixClientScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keep Minecraft's native pause menu and its native Save & Quit / Disconnect
 * action intact. Phoenix only adds an entry point to its own control center.
 *
 * Replacing GameMenuScreen with a custom Screen bypassed vanilla's version-
 * specific world-save and disconnect lifecycle and caused the progress screen
 * to remain open on the Android engine.
 */
@Mixin(GameMenuScreen.class)
abstract class GameMenuScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void phoenix$addControlCenterButton(CallbackInfo ci) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        int screenWidth = minecraft.getWindow().getScaledWidth();
        int screenHeight = minecraft.getWindow().getScaledHeight();
        int buttonWidth = Math.max(1, Math.min(220, screenWidth - 24));
        int buttonX = (screenWidth - buttonWidth) / 2;
        int buttonY = Math.max(4, screenHeight - 30);
        Screen parent = (Screen) (Object) this;

        ((ScreenAccessorMixin) (Object) parent).phoenix$addDrawableChild(
                ButtonWidget.builder(Text.literal("PHOENIX CONTROL CENTER"),
                                button -> minecraft.setScreen(new PhoenixClientScreen(parent)))
                        .dimensions(buttonX, buttonY, buttonWidth, 20)
                        .build());
    }
}
