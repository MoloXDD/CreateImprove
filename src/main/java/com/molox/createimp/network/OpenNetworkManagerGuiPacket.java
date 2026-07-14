package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.item.NetworkLabel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import java.util.List;

public record OpenNetworkManagerGuiPacket(InteractionHand hand, List<NetworkLabel> labels) implements CustomPacketPayload {

    public static final Type<OpenNetworkManagerGuiPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "open_network_manager_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNetworkManagerGuiPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL.map(
                            b -> b ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                            h -> h == InteractionHand.MAIN_HAND
                    ), OpenNetworkManagerGuiPacket::hand,
                    NetworkLabel.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenNetworkManagerGuiPacket::labels,
                    OpenNetworkManagerGuiPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}