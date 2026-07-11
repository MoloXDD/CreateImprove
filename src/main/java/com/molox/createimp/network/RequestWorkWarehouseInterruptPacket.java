package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.work_warehouse.WorkWarehouseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 进程面板详情界面里，玩家二次确认"中断请求"按钮后，客户端发给服务端
 * 的请求：让指定坐标的工作仓库进入请求中断阶段。
 */
public record RequestWorkWarehouseInterruptPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<RequestWorkWarehouseInterruptPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "request_work_warehouse_interrupt"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestWorkWarehouseInterruptPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeBlockPos(pkt.pos()),
                    buf -> new RequestWorkWarehouseInterruptPacket(buf.readBlockPos())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestWorkWarehouseInterruptPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            Level level = player.level();
            if (level.getBlockEntity(packet.pos()) instanceof WorkWarehouseBlockEntity warehouse) {
                warehouse.requestInterrupt();
            }
        });
    }
}