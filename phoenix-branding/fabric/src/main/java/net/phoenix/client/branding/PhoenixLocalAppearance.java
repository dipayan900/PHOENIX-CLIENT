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
import java.util.function.Supplier;

/**
 * Loads profile-local PNG cosmetics into Minecraft's texture manager.
 * A single cache owns both skin and cape textures so the vanilla skin hook
 * and the 3D cape renderer never register competing textures under one ID.
 */
public final class PhoenixLocalAppearance {
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final long MAX_FILE_BYTES = 8L * 1024L * 1024L;

    private record Entry(long modified, long length, Identifier id) {}

    private PhoenixLocalAppearance() {}

    public static Identifier skin() {
        return load("skin");
    }

    public static Identifier cape() {
        return load("cape");
    }

    private static void clearCachedTexture(String kind) {
        Entry previous = CACHE.remove(kind);
        if (previous == null) return;
        try {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(previous.id());
        } catch (Throwable error) {
            System.err.println("[Phoenix] Could not dispose old " + kind + " texture: " + error);
        }
    }

    private static boolean validDimensions(String kind, int width, int height) {
        if ("skin".equals(kind)) {
            // Accept standard 64x64 skins, legacy 64x32 skins, and integer
            // scale-ups while preserving the exact Minecraft UV proportions.
            return width >= 64 && width <= 1024 && width % 64 == 0
                    && (height == width || height * 2 == width);
        }
        // A cape atlas is 64x32. HD capes must preserve that exact aspect ratio.
        return width >= 64 && width <= 1024 && height >= 32 && height <= 512
                && width % 64 == 0 && height % 32 == 0 && width / 64 == height / 32;
    }

    private static NativeImageBackedTexture createTexture(String kind, NativeImage image) throws Exception {
        Constructor<?>[] constructors = NativeImageBackedTexture.class.getDeclaredConstructors();

        // Modern Minecraft (including 1.21.11) accepts the decoded NativeImage
        // directly. This avoids allocating an empty texture and replacing its
        // backing image after construction.
        for (Constructor<?> constructor : constructors) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length == 1 && p[0] == NativeImage.class) {
                constructor.setAccessible(true);
                return (NativeImageBackedTexture) constructor.newInstance(image);
            }
            if (p.length == 2 && p[1] == NativeImage.class) {
                Object name = null;
                if (p[0] == String.class) {
                    name = "Phoenix " + kind;
                } else if (Supplier.class.isAssignableFrom(p[0])) {
                    name = (Supplier<String>) () -> "Phoenix " + kind;
                }
                if (name != null) {
                    constructor.setAccessible(true);
                    return (NativeImageBackedTexture) constructor.newInstance(name, image);
                }
            }
        }

        // Older mappings expose only dimension-based constructors. Keep this
        // compatibility path isolated and explicitly upload after setImage.
        for (Constructor<?> constructor : constructors) {
            Class<?>[] p = constructor.getParameterTypes();
            constructor.setAccessible(true);
            if (p.length == 3 && p[0] == int.class && p[1] == int.class
                    && (p[2] == boolean.class || p[2] == Boolean.class)) {
                NativeImageBackedTexture texture = (NativeImageBackedTexture)
                        constructor.newInstance(image.getWidth(), image.getHeight(), true);
                texture.setImage(image);
                texture.upload();
                return texture;
            }
            if (p.length == 4 && p[1] == int.class && p[2] == int.class
                    && (p[3] == boolean.class || p[3] == Boolean.class)) {
                Object name = p[0] == String.class ? "Phoenix " + kind
                        : (Supplier.class.isAssignableFrom(p[0])
                                ? (Supplier<String>) () -> "Phoenix " + kind : null);
                if (name != null) {
                    NativeImageBackedTexture texture = (NativeImageBackedTexture)
                            constructor.newInstance(name, image.getWidth(), image.getHeight(), true);
                    texture.setImage(image);
                    texture.upload();
                    return texture;
                }
            }
        }
        throw new IllegalStateException("No compatible NativeImageBackedTexture constructor for " + kind);
    }

    private static Identifier load(String kind) {
        NativeImage image = null;
        NativeImageBackedTexture texture = null;
        boolean imageTransferred = false;
        try {
            File gameDir = FabricLoader.getInstance().getGameDir().toFile();
            File file = new File(new File(gameDir, "phoenix/cosmetics/equipped"),
                    kind + ".png").getCanonicalFile();

            if (!file.isFile() || file.length() < 8 || file.length() > MAX_FILE_BYTES) {
                clearCachedTexture(kind);
                return null;
            }

            long modified = file.lastModified();
            long length = file.length();
            Entry cached = CACHE.get(kind);
            if (cached != null && cached.modified() == modified && cached.length() == length) {
                return cached.id();
            }
            if (cached != null) clearCachedTexture(kind);

            try (InputStream input = Files.newInputStream(file.toPath())) {
                image = NativeImage.read(input);
            }

            if (!validDimensions(kind, image.getWidth(), image.getHeight())) {
                System.err.println("[Phoenix] Rejected " + kind + " PNG dimensions "
                        + image.getWidth() + "x" + image.getHeight() + ": " + file);
                return null;
            }

            texture = createTexture(kind, image);
            imageTransferred = true;

            Identifier id = Identifier.of("phoenix", "local_" + kind);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            texture = null; // TextureManager now owns the texture lifecycle.

            CACHE.put(kind, new Entry(modified, length, id));
            System.out.println("[Phoenix] Applied local " + kind + " texture "
                    + image.getWidth() + "x" + image.getHeight() + " as " + id);
            return id;
        } catch (Throwable error) {
            clearCachedTexture(kind);
            System.err.println("[Phoenix] Local " + kind + " texture unavailable: " + error);
            error.printStackTrace();
            return null;
        } finally {
            if (texture != null) {
                try { texture.close(); } catch (Throwable ignored) {}
            }
            if (image != null && !imageTransferred) {
                try { image.close(); } catch (Throwable ignored) {}
            }
        }
    }
}
