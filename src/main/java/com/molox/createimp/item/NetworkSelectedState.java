package com.molox.createimp.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record NetworkSelectedState(String labelName, UUID networkId) {

    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final Codec<NetworkSelectedState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("labelName").forGetter(NetworkSelectedState::labelName),
                    UUID_CODEC.fieldOf("networkId").forGetter(NetworkSelectedState::networkId)
            ).apply(instance, NetworkSelectedState::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkSelectedState> STREAM_CODEC =
            StreamCodec.of(
                    (buf, state) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, state.labelName());
                        buf.writeUUID(state.networkId());
                    },
                    buf -> new NetworkSelectedState(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            buf.readUUID()
                    )
            );
}