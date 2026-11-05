package net.phoenix.client.branding.mixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import net.phoenix.client.branding.PhoenixLocalAppearance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractClientPlayerEntity.class)
abstract class LocalPlayerSkinMixin {
 @Inject(method="getSkinTextures",at=@At("RETURN"),cancellable=true)
 private void phoenix$localAppearance(CallbackInfoReturnable<SkinTextures> cir){
  if(MinecraftClient.getInstance().player!=(Object)this)return;
  SkinTextures s=cir.getReturnValue();Identifier skin=PhoenixLocalAppearance.skin(),cape=PhoenixLocalAppearance.cape();
  if(skin==null&&cape==null)return;
  cir.setReturnValue(new SkinTextures(skin==null?s.texture():skin,s.textureUrl(),cape==null?s.capeTexture():cape,s.elytraTexture(),s.model(),s.secure()));
 }
}
