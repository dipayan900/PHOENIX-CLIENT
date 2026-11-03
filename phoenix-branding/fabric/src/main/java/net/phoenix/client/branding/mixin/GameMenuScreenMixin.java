package net.phoenix.client.branding.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.phoenix.client.branding.PhoenixPauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
abstract class GameMenuScreenMixin {
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void phoenix$replacePauseMenu(CallbackInfo ci) {
        MinecraftClient.getInstance().setScreen(new PhoenixPauseScreen());
        ci.cancel();
    }
}
