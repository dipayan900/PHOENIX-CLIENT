package net.phoenix.client.branding.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.phoenix.client.branding.PhoenixClientScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the real vanilla TitleScreen lifecycle intact so Essential and other
 * title-screen integrations can render their player preview, Wardrobe and
 * Social controls. Phoenix adds its own entry point without replacing the screen.
 */
@Mixin(TitleScreen.class)
abstract class TitleScreenMixin {
    @Shadow protected int width;
    @Shadow protected int height;

    @Shadow
    protected abstract <T extends Element & Drawable & Selectable> T addDrawableChild(T drawableElement);

    @Inject(method = "init", at = @At("TAIL"))
    private void phoenix$addClientMenuButton(CallbackInfo ci) {
        int buttonWidth = Math.max(1, Math.min(220, width - 24));
        int buttonX = (width - buttonWidth) / 2;
        int buttonY = Math.max(4, height - 54);
        Screen parent = (Screen) (Object) this;

        addDrawableChild(ButtonWidget.builder(Text.literal("PHOENIX CLIENT"),
                button -> MinecraftClient.getInstance().setScreen(new PhoenixClientScreen(parent)))
                .dimensions(buttonX, buttonY, buttonWidth, 20)
                .build());
    }
}
