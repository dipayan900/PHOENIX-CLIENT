package net.phoenix.client.branding.mixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.phoenix.client.branding.PhoenixTitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Redirects the vanilla title screen to the Phoenix-branded launcher menu. */
@Mixin(TitleScreen.class)
abstract class TitleScreenMixin {
 @Inject(method="init",at=@At("HEAD"),cancellable=true)
 private void phoenix$replaceTitleMenu(CallbackInfo ci){
  MinecraftClient.getInstance().setScreen(new PhoenixTitleScreen());
  ci.cancel();
 }
}