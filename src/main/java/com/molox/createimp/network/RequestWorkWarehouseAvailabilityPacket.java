package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.work_warehouse.WorkWarehouseNetworkHelper;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * 客户端→服务端，查询"某个物流网络频率下当前有多少可用工作仓库"。
 * 纯只读查询，服务端算完直接原样回复给发起请求的这个玩家（见 {@link #handle}）。
 * <p>
 * 引入这一对包的原因：仓储管理员请求界面和材料确认窗口，此前都是在客户端本地
 * 直接调用 {@link WorkWarehouseNetworkHelper} 来判断"确认键能不能点"——但这个
 * 判断依赖的注册表只在服务端进程里真正维护，独立服务端环境下客户端和服务端是
 * 两个完全独立的进程，客户端那份查询永远查不到任何数据。现在改为客户端只负责
 * 定期发起查询，由服务端给出权威答案。
 */
public record RequestWorkWarehouseAvailabilityPacket(UUID freqId) implements CustomPacketPayload {

    public static final Type<RequestWorkWarehouseAvailabilityPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "request_work_warehouse_availability"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestWorkWarehouseAvailabilityPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, RequestWorkWarehouseAvailabilityPacket::freqId,
                    RequestWorkWarehouseAvailabilityPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestWorkWarehouseAvailabilityPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            int count = WorkWarehouseNetworkHelper.countAvailableWorkWarehouses(packet.freqId());
            context.reply(new WorkWarehouseAvailabilityPacket(packet.freqId(), count));
        });
    }
}