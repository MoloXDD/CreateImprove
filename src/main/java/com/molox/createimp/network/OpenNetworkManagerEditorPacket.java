package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.item.NetworkLabel;
import com.molox.createimp.registry.ModMenuTypes;
import com.molox.createimp.screen.NetworkManagerLabelEditorMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record OpenNetworkManagerEditorPacket(
        InteractionHand hand,
        List<NetworkLabel> existingLabels,
        Optional<UUID> targetNetworkId
) implements CustomPacketPayload {

    public static final Type<OpenNetworkManagerEditorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "open_network_manager_editor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNetworkManagerEditorPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        ByteBufCodecs.BOOL.encode(buf, pkt.hand() == InteractionHand.MAIN_HAND);
                        NetworkLabel.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, pkt.existingLabels());
                        boolean hasId = pkt.targetNetworkId().isPresent();
                        buf.writeBoolean(hasId);
                        if (hasId) {
                            buf.writeUUID(pkt.targetNetworkId().get());
                        }
                    },
                    buf -> {
                        InteractionHand hand = buf.readBoolean()
                                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                        List<NetworkLabel> labels =
                                NetworkLabel.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                        Optional<UUID> networkId = Optional.empty();
                        if (buf.readBoolean()) {
                            networkId = Optional.of(buf.readUUID());
                        }
                        return new OpenNetworkManagerEditorPacket(hand, labels, networkId);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenNetworkManagerEditorPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            player.openMenu(new MenuProvider() {
                @Override
                public net.minecraft.network.chat.Component getDisplayName() {
                    return net.minecraft.network.chat.Component.empty();
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new NetworkManagerLabelEditorMenu(
                            ModMenuTypes.NETWORK_MANAGER_LABEL_EDITOR.get(),
                            id, inv, packet.hand(), packet.existingLabels(), packet.targetNetworkId());
                }
            }, buf -> {
                ByteBufCodecs.BOOL.encode(buf, packet.hand() == InteractionHand.MAIN_HAND);
                NetworkLabel.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, packet.existingLabels());
                boolean hasId = packet.targetNetworkId().isPresent();
                buf.writeBoolean(hasId);
                if (hasId) {
                    buf.writeUUID(packet.targetNetworkId().get());
                }
            });
        });
    }
}