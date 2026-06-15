package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.item.NetworkLabel;
import com.molox.createimp.registry.ModDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record SaveNetworkManagerDataPacket(
        InteractionHand hand,
        List<NetworkLabel> labels
) implements CustomPacketPayload {

    public static final Type<SaveNetworkManagerDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "save_network_manager_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveNetworkManagerDataPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL.map(
                            b -> b ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                            h -> h == InteractionHand.MAIN_HAND
                    ), SaveNetworkManagerDataPacket::hand,
                    NetworkLabel.STREAM_CODEC.apply(ByteBufCodecs.list()), SaveNetworkManagerDataPacket::labels,
                    SaveNetworkManagerDataPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveNetworkManagerDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            ItemStack stack = player.getItemInHand(packet.hand());
            stack.set(ModDataComponents.NETWORK_MANAGER_LABELS.get(), packet.labels());
        });
    }
}