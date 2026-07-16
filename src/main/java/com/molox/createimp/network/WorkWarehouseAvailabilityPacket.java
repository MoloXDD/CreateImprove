package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 服务端→客户端，回复 {@link RequestWorkWarehouseAvailabilityPacket} 的查询结果。
 * 纯数据记录，客户端专属的处理逻辑放在 {@link com.molox.createimp.client.ClientPayloadHandlers}
 * 里，不直接写在这个类自己身上——这个记录类本身在服务端也需要正常加载（编码发送
 * 这个包时需要用到），不能包含任何客户端专属引用。
 */
public record WorkWarehouseAvailabilityPacket(UUID freqId, int availableCount) implements CustomPacketPayload {

    public static final Type<WorkWarehouseAvailabilityPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "work_warehouse_availability"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WorkWarehouseAvailabilityPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, WorkWarehouseAvailabilityPacket::freqId,
                    ByteBufCodecs.VAR_INT, WorkWarehouseAvailabilityPacket::availableCount,
                    WorkWarehouseAvailabilityPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}