package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.item.NetworkLabel;
import com.molox.createimp.registry.ModDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record SaveNetworkManagerDataPacket(
        InteractionHand hand,
        List<NetworkLabel> labels,
        boolean reopenMainMenu
) implements CustomPacketPayload {

    public static final Type<SaveNetworkManagerDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "save_network_manager_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveNetworkManagerDataPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        ByteBufCodecs.BOOL.encode(buf, pkt.hand() == InteractionHand.MAIN_HAND);
                        NetworkLabel.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, pkt.labels());
                        buf.writeBoolean(pkt.reopenMainMenu());
                    },
                    buf -> {
                        InteractionHand hand = buf.readBoolean()
                                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                        List<NetworkLabel> labels =
                                NetworkLabel.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                        boolean reopen = buf.readBoolean();
                        return new SaveNetworkManagerDataPacket(hand, labels, reopen);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveNetworkManagerDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ItemStack stack = player.getItemInHand(packet.hand());
            stack.set(ModDataComponents.NETWORK_MANAGER_LABELS.get(), packet.labels());
            if (packet.reopenMainMenu()) {
                PacketDistributor.sendToPlayer(player,
                        new OpenNetworkManagerGuiPacket(packet.hand(), packet.labels()));
            }
        });
    }
}