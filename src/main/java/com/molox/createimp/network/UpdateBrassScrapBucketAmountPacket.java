package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateBrassScrapBucketAmountPacket(
        int currentAmount,
        int currentStacks
) implements CustomPacketPayload {

    public static final Type<UpdateBrassScrapBucketAmountPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "update_brass_scrap_bucket_amount"));

    public static final StreamCodec<ByteBuf, UpdateBrassScrapBucketAmountPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, UpdateBrassScrapBucketAmountPacket::currentAmount,
                    ByteBufCodecs.INT, UpdateBrassScrapBucketAmountPacket::currentStacks,
                    UpdateBrassScrapBucketAmountPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}