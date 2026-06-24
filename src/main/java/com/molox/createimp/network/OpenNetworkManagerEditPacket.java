package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.item.NetworkLabel;
import com.molox.createimp.registry.ModMenuTypes;
import com.molox.createimp.screen.NetworkManagerLabelEditMenu;
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

public record OpenNetworkManagerEditPacket(
        InteractionHand hand,
        List<NetworkLabel> existingLabels,
        int editingIndex,
        ItemStack editingIcon,
        String editingName
) implements CustomPacketPayload {

    public static final Type<OpenNetworkManagerEditPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "open_network_manager_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNetworkManagerEditPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        ByteBufCodecs.BOOL.encode(buf, pkt.hand() == InteractionHand.MAIN_HAND);
                        NetworkLabel.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, pkt.existingLabels());
                        buf.writeInt(pkt.editingIndex());
                        ItemStack.STREAM_CODEC.encode(buf, pkt.editingIcon());
                        ByteBufCodecs.STRING_UTF8.encode(buf, pkt.editingName());
                    },
                    buf -> {
                        InteractionHand hand = buf.readBoolean()
                                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                        List<NetworkLabel> labels =
                                NetworkLabel.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                        int editingIndex = buf.readInt();
                        ItemStack editingIcon = ItemStack.STREAM_CODEC.decode(buf);
                        String editingName = ByteBufCodecs.STRING_UTF8.decode(buf);
                        return new OpenNetworkManagerEditPacket(
                                hand, labels, editingIndex, editingIcon, editingName);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenNetworkManagerEditPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            com.molox.createimp.CreateImp.LOGGER.info("[NetworkManager] 服务端收到编辑包，index={}, name={}", packet.editingIndex(), packet.editingName());
            NetworkManagerLabelEditMenu.setPendingIcon(packet.editingIcon().copy());
            player.openMenu(new MenuProvider() {
                @Override
                public net.minecraft.network.chat.Component getDisplayName() {
                    return net.minecraft.network.chat.Component.empty();
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new NetworkManagerLabelEditMenu(
                            ModMenuTypes.NETWORK_MANAGER_LABEL_EDIT.get(),
                            id, inv, packet.hand(), packet.existingLabels(),
                            packet.editingIndex(), packet.editingIcon(), packet.editingName());
                }
            }, buf -> {
                ByteBufCodecs.BOOL.encode(buf, packet.hand() == InteractionHand.MAIN_HAND);
                NetworkLabel.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, packet.existingLabels());
                buf.writeInt(packet.editingIndex());
                ItemStack.STREAM_CODEC.encode(buf, packet.editingIcon());
                ByteBufCodecs.STRING_UTF8.encode(buf, packet.editingName());
            });
        });
    }
}