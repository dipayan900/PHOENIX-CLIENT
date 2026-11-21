package dev.brutalxd.distantterrain;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class TerrainSettings {
    public static boolean enabled = true;
    public static int distanceChunks = 512;
    private static Path file;
    private static KeyMapping openSettings;

    private TerrainSettings() {}

    public static void initialize() {
        file = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("brutal_xd_distant_terrain.properties");
        load();
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(BrutalDistantTerrainClient.MOD_ID, "controls"));
        openSettings = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.brutal_xd_distant_terrain.open_settings",
                InputConstants.Type.KEYSYM, InputConstants.KEY_F11, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettings.consumeClick()) {
                if (client.player != null) client.setScreen(new TerrainConfigScreen(client.screen));
            }
        });
    }

    public static void save() {
        if (file == null) return;
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("distanceChunks", Integer.toString(distanceChunks));
        try {
            Files.createDirectories(file.getParent());
            try (var out = Files.newOutputStream(file)) { properties.store(out, "Brutal_XD Distant Terrain"); }
        } catch (IOException e) {
            System.err.println("[Brutal_XD Distant Terrain] Settings save failed: " + e.getMessage());
        }
    }

    private static void load() {
        if (file == null || !Files.exists(file)) return;
        Properties properties = new Properties();
        try (var in = Files.newInputStream(file)) {
            properties.load(in);
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
            distanceChunks = Math.max(32, Math.min(512,
                    Integer.parseInt(properties.getProperty("distanceChunks", "512"))));
        } catch (Exception e) {
            System.err.println("[Brutal_XD Distant Terrain] Settings load failed: " + e.getMessage());
        }
    }
}
