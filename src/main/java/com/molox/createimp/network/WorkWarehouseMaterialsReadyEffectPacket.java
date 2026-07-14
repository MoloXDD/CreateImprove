package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WorkWarehouseMaterialsReadyEffectPacket(
        BlockPos pos
) implements CustomPacketPayload {

    public static final Type<WorkWarehouseMaterialsReadyEffectPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "work_warehouse_materials_ready_effect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WorkWarehouseMaterialsReadyEffectPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeBlockPos(pkt.pos()),
                    buf -> new WorkWarehouseMaterialsReadyEffectPacket(buf.readBlockPos())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}