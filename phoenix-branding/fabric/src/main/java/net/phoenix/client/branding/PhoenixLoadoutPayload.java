package net.phoenix.client.branding;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PhoenixLoadoutPayload(String data) implements CustomPayload {
    public static final Id<PhoenixLoadoutPayload> ID =
            new Id<>(Identifier.of("phoenix", "loadout"));
    public static final PacketCodec<RegistryByteBuf, PhoenixLoadoutPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, PhoenixLoadoutPayload::data, PhoenixLoadoutPayload::new);

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
