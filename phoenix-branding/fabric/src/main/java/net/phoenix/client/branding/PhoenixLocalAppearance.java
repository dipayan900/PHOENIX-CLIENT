package net.phoenix.client.branding;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads profile-local PNG cosmetics into Minecraft's texture manager.
 *
 * Minecraft's runtime uses intermediary method names. Calls made through
 * reflection with Yarn names (for example "setImage") are not remapped by
 * Loom, so all Minecraft method invocations must remain ordinary bytecode
 * calls. Constructor reflection is retained only for the constructor shape
 * that differs between the legacy and modern texture APIs.
 */
public final class PhoenixLocalAppearance {
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private record Entry(long modified, long length, Identifier id) {}

    private PhoenixLocalAppearance() {}

    public static Identifier skin() {
        return load("skin");
    }

    public static Identifier cape() {
        return load("cape");
    }

    private static Identifier load(String kind) {
        try {
            File gameDir = FabricLoader.getInstance().getGameDir().toFile();
            File file = new File(new File(gameDir, "phoenix/cosmetics/equipped"),
                    kind + ".png").getCanonicalFile();

            if (!file.isFile() || file.length() < 8 || file.length() > 2_097_152L) {
                return null;
            }

            String key = kind + ":" + file.getAbsolutePath();
            long modified = file.lastModified();
            long length = file.length();
            Entry cached = CACHE.get(key);
            if (cached != null && cached.modified() == modified && cached.length() == length) {
                return cached.id();
            }

            NativeImage image;
            try (InputStream input = Files.newInputStream(file.toPath())) {
                image = NativeImage.read(input);
            }

            NativeImageBackedTexture texture;
            try {
                Constructor<NativeImageBackedTexture> constructor =
                        NativeImageBackedTexture.class.getConstructor(
                                int.class, int.class, boolean.class);
                texture = constructor.newInstance(image.getWidth(), image.getHeight(), true);
            } catch (NoSuchMethodException legacyConstructorMissing) {
                Constructor<NativeImageBackedTexture> constructor =
                        NativeImageBackedTexture.class.getConstructor(
                                String.class, int.class, int.class, boolean.class);
                texture = constructor.newInstance(
                        "Phoenix " + kind, image.getWidth(), image.getHeight(), true);
            }

            // Direct calls are remapped by Fabric Loom to the runtime's
            // intermediary names (method_4526 / method_4617).
            texture.setImage(image);
            texture.upload();

            // Use the stable registration API shared by the supported Yarn
            // versions. The selected 1.21.11 mappings do not expose
            // registerDynamicTexture on TextureManager.
            Identifier id = Identifier.of("phoenix", "local_" + kind);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);

            CACHE.put(key, new Entry(modified, length, id));
            System.out.println("[Phoenix] Applied local " + kind
                    + " texture from " + file + " as " + id);
            return id;
        } catch (Throwable error) {
            System.err.println("[Phoenix] local " + kind
                    + " texture unavailable: " + error);
            error.printStackTrace();
            return null;
        }
    }
}
