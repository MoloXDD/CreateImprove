package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.screen.BrassScrapBucketMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SubmitBrassScrapBucketFilterPacket(
        BlockPos pos,
        ItemStack item
) implements CustomPacketPayload {

    public static final Type<SubmitBrassScrapBucketFilterPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "submit_brass_scrap_bucket_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitBrassScrapBucketFilterPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SubmitBrassScrapBucketFilterPacket decode(RegistryFriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    return new SubmitBrassScrapBucketFilterPacket(pos, item);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, SubmitBrassScrapBucketFilterPacket packet) {
                    BlockPos.STREAM_CODEC.encode(buf, packet.pos());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.item());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SubmitBrassScrapBucketFilterPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!(player.containerMenu instanceof BrassScrapBucketMenu menu)) return;
            if (!menu.pos.equals(packet.pos())) return;
            menu.submitGhostFilterItem(packet.item(), player);
        });
    }
}