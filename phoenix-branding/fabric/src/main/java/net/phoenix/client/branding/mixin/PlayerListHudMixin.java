package net.phoenix.client.branding.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.phoenix.client.branding.PhoenixBrandingClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
abstract class PlayerListHudMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void phoenix$markLocalPlayer(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !client.player.getUuid().equals(entry.getProfile().getId())) return;
        Text current = cir.getReturnValue();
        if (current != null && !current.getString().startsWith(PhoenixBrandingClient.PREFIX)) {
            cir.setReturnValue(Text.literal(PhoenixBrandingClient.PREFIX).append(current));
        }
    }
}
