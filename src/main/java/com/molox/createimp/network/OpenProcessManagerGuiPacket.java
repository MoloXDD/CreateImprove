package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.screen.ProcessManagerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenProcessManagerGuiPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<OpenProcessManagerGuiPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "open_process_manager_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenProcessManagerGuiPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeBlockPos(pkt.pos()),
                    buf -> new OpenProcessManagerGuiPacket(buf.readBlockPos())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenProcessManagerGuiPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ProcessManagerScreen.open(packet));
    }
}