package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.screen.LabeledRedstoneLinkScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenLabeledRedstoneLinkGuiPacket(
        BlockPos pos,
        String frequencyText
) implements CustomPacketPayload {

    public static final Type<OpenLabeledRedstoneLinkGuiPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "open_labeled_redstone_link_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenLabeledRedstoneLinkGuiPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos());
                        buf.writeUtf(pkt.frequencyText());
                    },
                    buf -> new OpenLabeledRedstoneLinkGuiPacket(buf.readBlockPos(), buf.readUtf())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenLabeledRedstoneLinkGuiPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> LabeledRedstoneLinkScreen.open(packet));
    }
}