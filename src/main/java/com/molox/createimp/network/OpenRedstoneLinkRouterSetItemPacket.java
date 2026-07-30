package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.redstone_link_router.RedstoneLinkRouterBlockEntity;
import com.molox.createimp.registry.ModMenuTypes;
import com.molox.createimp.screen.RedstoneLinkRouterSetItemMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 路由器界面里左键点击一个物品终端模块时，客户端发给服务端的"请求打开配置菜单"包。
 * 服务端从方块实体读取这个槽位当前真实保存的两个物品数据，再打开
 * {@link RedstoneLinkRouterSetItemMenu}——写回 extraData 时用的是服务端读到的
 * 最新数据，而不是客户端提交上来的（客户端提交的两个物品只是为了在方块实体查不到
 * 数据时有个兜底显示，正常情况下都会被服务端的真实数据覆盖）。
 */
public record OpenRedstoneLinkRouterSetItemPacket(
        BlockPos pos,
        int rowIndex,
        int slotIndex,
        ItemStack fallbackItem1,
        ItemStack fallbackItem2
) implements CustomPacketPayload {

    public static final Type<OpenRedstoneLinkRouterSetItemPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "open_redstone_link_router_set_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRedstoneLinkRouterSetItemPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenRedstoneLinkRouterSetItemPacket::pos,
                    ByteBufCodecs.VAR_INT, OpenRedstoneLinkRouterSetItemPacket::rowIndex,
                    ByteBufCodecs.VAR_INT, OpenRedstoneLinkRouterSetItemPacket::slotIndex,
                    ItemStack.OPTIONAL_STREAM_CODEC, OpenRedstoneLinkRouterSetItemPacket::fallbackItem1,
                    ItemStack.OPTIONAL_STREAM_CODEC, OpenRedstoneLinkRouterSetItemPacket::fallbackItem2,
                    OpenRedstoneLinkRouterSetItemPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenRedstoneLinkRouterSetItemPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.level().isLoaded(packet.pos())) return;
            if (!player.canInteractWithBlock(packet.pos(), 20.0)) return;

            ItemStack item1 = packet.fallbackItem1();
            ItemStack item2 = packet.fallbackItem2();
            if (player.level().getBlockEntity(packet.pos()) instanceof RedstoneLinkRouterBlockEntity router) {
                var data = router.getComponent(packet.rowIndex(), packet.slotIndex());
                item1 = data.itemSlot1();
                item2 = data.itemSlot2();
            }
            ItemStack finalItem1 = item1;
            ItemStack finalItem2 = item2;

            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.empty();
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new RedstoneLinkRouterSetItemMenu(
                            ModMenuTypes.REDSTONE_LINK_ROUTER_SET_ITEM.get(),
                            id, inv, packet.pos(), packet.rowIndex(), packet.slotIndex(),
                            finalItem1, finalItem2);
                }
            }, buf -> {
                BlockPos.STREAM_CODEC.encode(buf, packet.pos());
                buf.writeVarInt(packet.rowIndex());
                buf.writeVarInt(packet.slotIndex());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, finalItem1);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, finalItem2);
            });
        });
    }
}