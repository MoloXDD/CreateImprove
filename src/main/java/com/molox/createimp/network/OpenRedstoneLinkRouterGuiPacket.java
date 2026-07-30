package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenRedstoneLinkRouterGuiPacket(
        BlockPos pos
) implements CustomPacketPayload {

    public static final Type<OpenRedstoneLinkRouterGuiPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "open_redstone_link_router_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRedstoneLinkRouterGuiPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeBlockPos(pkt.pos()),
                    buf -> new OpenRedstoneLinkRouterGuiPacket(buf.readBlockPos())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}