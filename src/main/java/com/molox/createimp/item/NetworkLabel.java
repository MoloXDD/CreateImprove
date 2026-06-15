package com.molox.createimp.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

public record NetworkLabel(String name, ItemStack icon, Optional<UUID> networkId) {

    private static final Codec<UUID> UUID_CODEC =
            Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final Codec<NetworkLabel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(NetworkLabel::name),
            ItemStack.CODEC.fieldOf("icon").forGetter(NetworkLabel::icon),
            UUID_CODEC.optionalFieldOf("networkId").forGetter(NetworkLabel::networkId)
    ).apply(instance, NetworkLabel::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkLabel> STREAM_CODEC =
            StreamCodec.of(
                    (buf, label) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, label.name());
                        ItemStack.STREAM_CODEC.encode(buf, label.icon());
                        boolean hasId = label.networkId().isPresent();
                        buf.writeBoolean(hasId);
                        if (hasId) {
                            buf.writeUUID(label.networkId().get());
                        }
                    },
                    buf -> {
                        String name = ByteBufCodecs.STRING_UTF8.decode(buf);
                        ItemStack icon = ItemStack.STREAM_CODEC.decode(buf);
                        Optional<UUID> networkId = Optional.empty();
                        if (buf.readBoolean()) {
                            networkId = Optional.of(buf.readUUID());
                        }
                        return new NetworkLabel(name, icon, networkId);
                    }
            );
}