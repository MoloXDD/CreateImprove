package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record OpenBrassScrapBucketGuiPacket(
        BlockPos pos,
        int attachType,
        int keepAmount,
        boolean keepInStacks,
        int maxItems,
        int maxStacks,
        int currentAmount,
        int currentStacks,
        ItemStack filterIcon
) implements CustomPacketPayload {

    public static final Type<OpenBrassScrapBucketGuiPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "open_brass_scrap_bucket_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBrassScrapBucketGuiPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenBrassScrapBucketGuiPacket decode(RegistryFriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    int attachType = buf.readInt();
                    int keepAmount = buf.readInt();
                    boolean keepInStacks = buf.readBoolean();
                    int maxItems = buf.readInt();
                    int maxStacks = buf.readInt();
                    int currentAmount = buf.readInt();
                    int currentStacks = buf.readInt();
                    ItemStack filterIcon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    return new OpenBrassScrapBucketGuiPacket(pos, attachType, keepAmount, keepInStacks,
                            maxItems, maxStacks, currentAmount, currentStacks, filterIcon);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, OpenBrassScrapBucketGuiPacket packet) {
                    BlockPos.STREAM_CODEC.encode(buf, packet.pos());
                    buf.writeInt(packet.attachType());
                    buf.writeInt(packet.keepAmount());
                    buf.writeBoolean(packet.keepInStacks());
                    buf.writeInt(packet.maxItems());
                    buf.writeInt(packet.maxStacks());
                    buf.writeInt(packet.currentAmount());
                    buf.writeInt(packet.currentStacks());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.filterIcon());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}