package net.phoenix.client.branding.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.phoenix.client.branding.PhoenixClientScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
abstract class TitleScreenMixin {
    @Shadow
    protected abstract <T extends Element & Drawable & Selectable> T addDrawableChild(T drawableElement);

    @Inject(method = "init", at = @At("TAIL"))
    private void phoenix$addClientMenu(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        TitleScreen screen = (TitleScreen) (Object) this;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("PHOENIX CLIENT"), button ->
                client.setScreen(new PhoenixClientScreen(screen)))
                .dimensions(Math.max(4, width - 108), Math.max(4, height - 30), 104, 20).build());
    }
}
