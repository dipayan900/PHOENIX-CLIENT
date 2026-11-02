package net.phoenix.client.branding;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

public final class PhoenixBrandingClient implements ClientModInitializer {
    public static final Identifier BRAND_CHANNEL = Identifier.of("phoenix", "brand");
    public static final String PREFIX = "🐦‍🔥 ";

    @Override
    public void onInitializeClient() {
        // This is a cosmetic identity signal only. It must never grant permissions,
        // purchases, owner status, moderation exemptions or gameplay advantages.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(BRAND_CHANNEL)) {
                ClientPlayNetworking.send(BRAND_CHANNEL, PacketByteBufs.empty());
            }
        });
    }
}
