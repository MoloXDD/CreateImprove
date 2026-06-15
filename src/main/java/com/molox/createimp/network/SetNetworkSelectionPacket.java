package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.item.NetworkSelectedState;
import com.molox.createimp.registry.ModDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record SetNetworkSelectionPacket(
        InteractionHand hand,
        String labelName,
        UUID networkId
) implements CustomPacketPayload {

    public static final Type<SetNetworkSelectionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "set_network_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetNetworkSelectionPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBoolean(pkt.hand() == InteractionHand.MAIN_HAND);
                        ByteBufCodecs.STRING_UTF8.encode(buf, pkt.labelName());
                        buf.writeUUID(pkt.networkId());
                    },
                    buf -> new SetNetworkSelectionPacket(
                            buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            buf.readUUID()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetNetworkSelectionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ItemStack stack = player.getItemInHand(packet.hand());
            stack.set(ModDataComponents.NETWORK_SELECTED_STATE.get(),
                    new NetworkSelectedState(packet.labelName(), packet.networkId()));
        });
    }
}