package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.brass_scrap_bucket.BrassScrapBucketBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SaveBrassScrapBucketConfigPacket(
        BlockPos pos,
        int keepAmount,
        boolean keepInStacks
) implements CustomPacketPayload {

    public static final Type<SaveBrassScrapBucketConfigPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "save_brass_scrap_bucket_config"));

    public static final StreamCodec<ByteBuf, SaveBrassScrapBucketConfigPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SaveBrassScrapBucketConfigPacket::pos,
                    ByteBufCodecs.INT, SaveBrassScrapBucketConfigPacket::keepAmount,
                    ByteBufCodecs.BOOL, SaveBrassScrapBucketConfigPacket::keepInStacks,
                    SaveBrassScrapBucketConfigPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveBrassScrapBucketConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!(player.level().getBlockEntity(packet.pos()) instanceof BrassScrapBucketBlockEntity be)) return;
            be.keepAmount = packet.keepAmount();
            be.keepInStacks = packet.keepInStacks();
            be.setChanged();
        });
    }
}