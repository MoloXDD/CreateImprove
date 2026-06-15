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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record OpenNetworkManagerEditorPacket(
        InteractionHand hand,
        List<NetworkLabel> existingLabels
) implements CustomPacketPayload {

    public static final Type<OpenNetworkManagerEditorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "open_network_manager_editor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNetworkManagerEditorPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL.map(
                            b -> b ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                            h -> h == InteractionHand.MAIN_HAND
                    ), OpenNetworkManagerEditorPacket::hand,
                    NetworkLabel.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenNetworkManagerEditorPacket::existingLabels,
                    OpenNetworkManagerEditorPacket::new
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
                            id, inv, packet.hand(), packet.existingLabels());
                }
            }, buf -> {
                ByteBufCodecs.BOOL.encode(buf, packet.hand() == InteractionHand.MAIN_HAND);
                NetworkLabel.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, packet.existingLabels());
            });
        });
    }
}