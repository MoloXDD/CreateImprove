package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
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

public record SaveNetworkManagerSearchPacket(
        InteractionHand hand,
        String searchText
) implements CustomPacketPayload {

    public static final Type<SaveNetworkManagerSearchPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "save_network_manager_search"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveNetworkManagerSearchPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBoolean(pkt.hand() == InteractionHand.MAIN_HAND);
                        ByteBufCodecs.STRING_UTF8.encode(buf, pkt.searchText());
                    },
                    buf -> new SaveNetworkManagerSearchPacket(
                            buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                            ByteBufCodecs.STRING_UTF8.decode(buf)
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveNetworkManagerSearchPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ItemStack stack = player.getItemInHand(packet.hand());
            if (packet.searchText().isEmpty()) {
                stack.remove(ModDataComponents.NETWORK_MANAGER_SEARCH.get());
            } else {
                stack.set(ModDataComponents.NETWORK_MANAGER_SEARCH.get(), packet.searchText());
            }
        });
    }
}