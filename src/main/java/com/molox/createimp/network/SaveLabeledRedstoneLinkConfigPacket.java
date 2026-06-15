package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.labeled_redstone_link.LabeledRedstoneLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SaveLabeledRedstoneLinkConfigPacket(
        BlockPos pos,
        String frequencyText
) implements CustomPacketPayload {

    public static final Type<SaveLabeledRedstoneLinkConfigPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "save_labeled_redstone_link_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveLabeledRedstoneLinkConfigPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos());
                        buf.writeUtf(pkt.frequencyText());
                    },
                    buf -> new SaveLabeledRedstoneLinkConfigPacket(buf.readBlockPos(), buf.readUtf())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveLabeledRedstoneLinkConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!(player.level().getBlockEntity(packet.pos()) instanceof LabeledRedstoneLinkBlockEntity be))
                return;
            be.setFrequencyText(packet.frequencyText());
        });
    }
}