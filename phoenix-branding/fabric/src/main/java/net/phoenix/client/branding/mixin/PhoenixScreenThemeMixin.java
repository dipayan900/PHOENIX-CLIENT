package net.phoenix.client.branding.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies Phoenix styling to vanilla screens without replacing their working controls. */
@Mixin(Screen.class)
abstract class PhoenixScreenThemeMixin {
    @Inject(method = "renderBackground", at = @At("TAIL"))
    private void phoenix$themeVanillaScreens(DrawContext draw, int mouseX, int mouseY,
                                             float delta, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        String section;
        if (screen instanceof OptionsScreen) section = "OPTIONS";
        else if (screen instanceof MultiplayerScreen) section = "MULTIPLAYER";
        else if (screen instanceof SelectWorldScreen) section = "SINGLEPLAYER";
        else return;

        int width = draw.getScaledWindowWidth();
        draw.fillGradient(0, 0, width, draw.getScaledWindowHeight(), 0xFF09070D, 0xFF24101B);
        draw.fill(0, 0, width, 37, 0xD50B0910);
        draw.fill(0, 36, width, 37, 0xFFFF702E);
        MinecraftClient client = MinecraftClient.getInstance();
        draw.drawTextWithShadow(client.textRenderer, Text.literal("PHOENIX"), 12, 10, 0xFFFF8547);
        draw.drawCenteredTextWithShadow(client.textRenderer,
                Text.literal("CLIENT  /  " + section), width / 2, 13, 0xFFB8AEBB);
    }
}
