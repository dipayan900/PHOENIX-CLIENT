package net.phoenix.client.branding;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;

/** Empty, versioned identity-decoration payload for supported Phoenix servers. */
public record PhoenixBrandPayload() implements CustomPayload {
    public static final Id<PhoenixBrandPayload> ID =
            new Id<>(Identifier.of("phoenix", "brand"));
    public static final PacketCodec<PacketByteBuf, PhoenixBrandPayload> CODEC =
            PacketCodec.unit(new PhoenixBrandPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
