package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.work_warehouse.WorkWarehouseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SaveWorkWarehouseAddressPacket(
        BlockPos pos,
        String addressText
) implements CustomPacketPayload {

    public static final Type<SaveWorkWarehouseAddressPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "save_work_warehouse_address"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveWorkWarehouseAddressPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos());
                        buf.writeUtf(pkt.addressText());
                    },
                    buf -> new SaveWorkWarehouseAddressPacket(buf.readBlockPos(), buf.readUtf())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveWorkWarehouseAddressPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!(player.level().getBlockEntity(packet.pos()) instanceof WorkWarehouseBlockEntity be))
                return;
            be.setAddress(packet.addressText());
        });
    }
}