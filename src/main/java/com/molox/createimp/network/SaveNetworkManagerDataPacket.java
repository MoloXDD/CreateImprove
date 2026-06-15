package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.data.NetworkManagerSavedData;
import com.molox.createimp.item.NetworkLabel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
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
            var player = (net.minecraft.server.level.ServerPlayer) context.player();
            NetworkManagerSavedData savedData = NetworkManagerSavedData.get(player.server);
            savedData.setLabels(packet.labels());
            if (packet.reopenMainMenu()) {
                PacketDistributor.sendToPlayer(player,
                        new OpenNetworkManagerGuiPacket(packet.hand(), savedData.getLabels()));
            }
        });
    }
}