package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.registry.ModDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClearNetworkSelectionPacket(InteractionHand hand) implements CustomPacketPayload {

    public static final Type<ClearNetworkSelectionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "clear_network_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClearNetworkSelectionPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeBoolean(pkt.hand() == InteractionHand.MAIN_HAND),
                    buf -> new ClearNetworkSelectionPacket(
                            buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND)
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClearNetworkSelectionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ItemStack stack = player.getItemInHand(packet.hand());
            stack.remove(ModDataComponents.NETWORK_SELECTED_STATE.get());
        });
    }
}