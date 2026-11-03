package net.phoenix.client.branding.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.phoenix.client.branding.PhoenixClientScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
abstract class TitleScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void phoenix$addClientMenu(CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        screen.addDrawableChild(ButtonWidget.builder(Text.literal("PHOENIX CLIENT"), button ->
                MinecraftClient.getInstance().setScreen(new PhoenixClientScreen(screen)))
                .dimensions(Math.max(4, screen.width - 108), Math.max(4, screen.height - 30), 104, 20).build());
    }
}
