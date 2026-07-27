package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 服务端→客户端，回复 {@link RequestTemplateStockSamplePacket} 的查询结果，
 * 数量顺序与请求时传入的 samples 顺序一一对应。纯数据记录，客户端专属的
 * 处理逻辑放在 {@link com.molox.createimp.client.ClientPayloadHandlers} 里。
 */
public record TemplateStockSampleResultPacket(List<Integer> counts) implements CustomPacketPayload {

    public static final Type<TemplateStockSampleResultPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "template_stock_sample_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TemplateStockSampleResultPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT.apply(ByteBufCodecs.list()), TemplateStockSampleResultPacket::counts,
                    TemplateStockSampleResultPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
