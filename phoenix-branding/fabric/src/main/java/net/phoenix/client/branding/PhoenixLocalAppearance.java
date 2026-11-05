package net.phoenix.client.branding;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
/** Profile-local texture loader. Never modifies account credentials or server state. */
public final class PhoenixLocalAppearance {
 private static final Map<String,Entry> CACHE=new ConcurrentHashMap<>();
 private record Entry(long modified,Identifier id){}
 private PhoenixLocalAppearance(){}
 public static Identifier skin(){return load("skin");}
 public static Identifier cape(){return load("cape");}
 private static Identifier load(String kind){
  try{
   File f=FabricLoader.getInstance().getGameDir().resolve("phoenix/cosmetics/equipped/"+kind+".png").toFile();
   if(!f.isFile()||f.length()<8||f.length()>2097152)return null;
   String key=kind+f.getAbsolutePath();long modified=f.lastModified();Entry old=CACHE.get(key);if(old!=null&&old.modified==modified)return old.id;
   NativeImage image;try(InputStream in=Files.newInputStream(f.toPath())){image=NativeImage.read(in);}
   Class<?> type=NativeImageBackedTexture.class;Object texture;
   try{Constructor<?> c=type.getConstructor(int.class,int.class,boolean.class);texture=c.newInstance(image.getWidth(),image.getHeight(),true);}
   catch(NoSuchMethodException newer){Constructor<?> c=type.getConstructor(String.class,int.class,int.class,boolean.class);texture=c.newInstance("Phoenix "+kind,image.getWidth(),image.getHeight(),true);}
   type.getMethod("setImage",NativeImage.class).invoke(texture,image);
   try{type.getMethod("upload").invoke(texture);}catch(NoSuchMethodException ignored){}
   Object manager=MinecraftClient.getInstance().getTextureManager();
   Method register=manager.getClass().getMethod("registerDynamicTexture",String.class,type);
   Identifier id=(Identifier)register.invoke(manager,"phoenix_local_"+kind,texture);
   CACHE.put(key,new Entry(modified,id));return id;
  }catch(Throwable e){System.err.println("[Phoenix] local "+kind+" texture unavailable: "+e.getMessage());return null;}
 }
}
