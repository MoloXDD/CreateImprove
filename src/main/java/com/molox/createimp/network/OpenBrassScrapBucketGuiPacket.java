package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenBrassScrapBucketGuiPacket(
        BlockPos pos,
        int attachType,
        int keepAmount,
        boolean keepInStacks,
        int maxItems,
        int maxStacks,
        int currentAmount,
        int currentStacks
) implements CustomPacketPayload {

    public static final Type<OpenBrassScrapBucketGuiPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "open_brass_scrap_bucket_gui"));

    public static final StreamCodec<ByteBuf, OpenBrassScrapBucketGuiPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenBrassScrapBucketGuiPacket decode(ByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    int attachType = buf.readInt();
                    int keepAmount = buf.readInt();
                    boolean keepInStacks = buf.readBoolean();
                    int maxItems = buf.readInt();
                    int maxStacks = buf.readInt();
                    int currentAmount = buf.readInt();
                    int currentStacks = buf.readInt();
                    return new OpenBrassScrapBucketGuiPacket(pos, attachType, keepAmount, keepInStacks, maxItems, maxStacks, currentAmount, currentStacks);
                }

                @Override
                public void encode(ByteBuf buf, OpenBrassScrapBucketGuiPacket packet) {
                    BlockPos.STREAM_CODEC.encode(buf, packet.pos());
                    buf.writeInt(packet.attachType());
                    buf.writeInt(packet.keepAmount());
                    buf.writeBoolean(packet.keepInStacks());
                    buf.writeInt(packet.maxItems());
                    buf.writeInt(packet.maxStacks());
                    buf.writeInt(packet.currentAmount());
                    buf.writeInt(packet.currentStacks());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}