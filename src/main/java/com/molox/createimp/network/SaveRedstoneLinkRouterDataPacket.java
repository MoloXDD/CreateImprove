package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.redstone_link_router.RedstoneLinkRouterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端关闭路由器界面时（无论是 ESC 还是右下角确认键）把当前编辑状态整体打包
 * 发给服务端。{@code data} 里的编码格式和 {@link RedstoneLinkRouterBlockEntity}
 * 存盘用的格式完全一致（都是同一个 Codec 编出来的 Tag，套一层"Rows"键名），
 * 服务端收到后直接原样交给方块实体的 read 逻辑复用即可。
 */
public record SaveRedstoneLinkRouterDataPacket(
        BlockPos pos,
        CompoundTag data
) implements CustomPacketPayload {

    public static final Type<SaveRedstoneLinkRouterDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "save_redstone_link_router_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveRedstoneLinkRouterDataPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SaveRedstoneLinkRouterDataPacket::pos,
                    ByteBufCodecs.COMPOUND_TAG, SaveRedstoneLinkRouterDataPacket::data,
                    SaveRedstoneLinkRouterDataPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveRedstoneLinkRouterDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ServerLevel level = player.serverLevel();
            if (!level.isLoaded(packet.pos())) {
                return;
            }
            if (!player.canInteractWithBlock(packet.pos(), 20.0)) {
                return;
            }
            BlockEntity be = level.getBlockEntity(packet.pos());
            if (!(be instanceof RedstoneLinkRouterBlockEntity router)) {
                return;
            }
            router.loadRowsFromTag(packet.data(), level.registryAccess());
        });
    }
}