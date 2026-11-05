package net.phoenix.client.branding.mixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.phoenix.client.branding.PhoenixLocalAppearance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractClientPlayerEntity.class)
abstract class LocalPlayerSkinMixinModern {
 @Inject(method="getSkin",at=@At("RETURN"),cancellable=true)
 private void phoenix$localAppearance(CallbackInfoReturnable<SkinTextures> cir){
  if(MinecraftClient.getInstance().player!=(Object)this)return;
  SkinTextures s=cir.getReturnValue();Identifier skin=PhoenixLocalAppearance.skin(),cape=PhoenixLocalAppearance.cape();
  if(skin==null&&cape==null)return;
  // The one-argument TextureAssetInfo constructor derives a resource-pack path
  // (phoenix:textures/local_skin.png). Phoenix textures are already registered
  // dynamically in TextureManager as phoenix:local_skin / phoenix:local_cape.
  // Point texturePath at that exact dynamic identifier so Minecraft resolves
  // the registered GPU texture instead of searching a nonexistent PNG resource.
  AssetInfo.TextureAssetInfo skinAsset = skin == null
          ? (AssetInfo.TextureAssetInfo) s.body()
          : new AssetInfo.TextureAssetInfo(skin, skin);
  AssetInfo.TextureAssetInfo capeAsset = cape == null
          ? (AssetInfo.TextureAssetInfo) s.cape()
          : new AssetInfo.TextureAssetInfo(cape, cape);
  cir.setReturnValue(new SkinTextures(skinAsset,capeAsset,s.elytra(),s.model(),s.secure()));
 }
}
