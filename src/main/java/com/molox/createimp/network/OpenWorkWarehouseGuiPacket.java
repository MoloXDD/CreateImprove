package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.screen.WorkWarehouseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenWorkWarehouseGuiPacket(
        BlockPos pos,
        String addressText,
        boolean working
) implements CustomPacketPayload {

    public static final Type<OpenWorkWarehouseGuiPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "open_work_warehouse_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWorkWarehouseGuiPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBlockPos(pkt.pos());
                        buf.writeUtf(pkt.addressText());
                        buf.writeBoolean(pkt.working());
                    },
                    buf -> new OpenWorkWarehouseGuiPacket(buf.readBlockPos(), buf.readUtf(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @OnlyIn(Dist.CLIENT)
    public static void handle(OpenWorkWarehouseGuiPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> WorkWarehouseScreen.open(packet));
    }
}