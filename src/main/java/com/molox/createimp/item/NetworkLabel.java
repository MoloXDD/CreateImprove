package com.molox.createimp.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record NetworkLabel(String name, ItemStack icon) {

    public static final Codec<NetworkLabel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(NetworkLabel::name),
            ItemStack.CODEC.fieldOf("icon").forGetter(NetworkLabel::icon)
    ).apply(instance, NetworkLabel::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkLabel> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, NetworkLabel::name,
                    ItemStack.STREAM_CODEC, NetworkLabel::icon,
                    NetworkLabel::new
            );
}