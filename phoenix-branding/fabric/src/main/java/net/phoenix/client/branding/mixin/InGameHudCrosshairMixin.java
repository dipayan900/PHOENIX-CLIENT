package net.phoenix.client.branding.mixin;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import net.phoenix.client.branding.PhoenixHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(InGameHud.class)
abstract class InGameHudCrosshairMixin {
 @Inject(method="renderCrosshair",at=@At("HEAD"),cancellable=true)
 private void phoenix$replaceVanillaCrosshair(DrawContext context,CallbackInfo ci){if(PhoenixHud.isCustomCrosshairEnabled())ci.cancel();}
}