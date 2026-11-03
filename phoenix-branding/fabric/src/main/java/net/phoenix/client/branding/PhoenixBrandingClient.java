package net.phoenix.client.branding;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PhoenixBrandingClient implements ClientModInitializer {
    public static final Identifier BRAND_CHANNEL = Identifier.of("phoenix", "brand");
    public static final String PREFIX = "🐦‍🔥 ";

    @Override
    public void onInitializeClient() {
        PhoenixHud.register();
        PayloadTypeRegistry.playC2S().register(PhoenixBrandPayload.ID, PhoenixBrandPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PhoenixLoadoutPayload.ID, PhoenixLoadoutPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(PhoenixLoadoutPayload.ID,
                (payload, context) -> PhoenixLoadoutState.accept(payload.data()));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            PhoenixLoadoutState.clear();
            if (ClientPlayNetworking.canSend(BRAND_CHANNEL)) {
                ClientPlayNetworking.send(new PhoenixBrandPayload());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PhoenixLoadoutState.clear());

        try {
            Path marker = FabricLoader.getInstance().getConfigDir().resolve("phoenix-client-loaded.txt");
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "Phoenix Client initialized successfully.\n");
            System.out.println("[Phoenix Client] Fabric client mod initialized successfully.");
        } catch (IOException error) {
            System.err.println("[Phoenix Client] Could not write load marker: " + error.getMessage());
        }
    }
}
