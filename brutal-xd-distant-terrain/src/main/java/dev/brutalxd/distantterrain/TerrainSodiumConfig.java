package dev.brutalxd.distantterrain;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Adds the terrain configuration screen to Sodium's Video Settings sidebar.
 * Sodium is an optional runtime integration; the mod remains usable without it.
 */
public final class TerrainSodiumConfig implements ConfigEntryPoint {
    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        builder.registerOwnModOptions()
                .setName("Brutal_XD Distant Terrain")
                .setVersion("0.1.0-alpha")
                .addPage(builder.createExternalPage()
                        .setName(Component.literal("Distant Terrain"))
                        .setScreenConsumer(parent ->
                                Minecraft.getInstance().setScreen(new TerrainConfigScreen(parent))));
    }
}
