package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagerLink.WiFiParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WorkWarehouseActivateEffectPacket(
        BlockPos pos
) implements CustomPacketPayload {

    public static final Type<WorkWarehouseActivateEffectPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "work_warehouse_activate_effect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WorkWarehouseActivateEffectPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> buf.writeBlockPos(pkt.pos()),
                    buf -> new WorkWarehouseActivateEffectPacket(buf.readBlockPos())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @OnlyIn(Dist.CLIENT)
    public static void handle(WorkWarehouseActivateEffectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            Vec3 center = Vec3.atCenterOf(packet.pos());
            AllSoundEvents.CONFIRM.playAt(level, center, 0.5f, 1.5f, false);
            AllSoundEvents.STOCK_LINK.playAt(level, center, 1.0f, 1.0f, false);
            level.addParticle(new WiFiParticle.Data(), center.x, center.y, center.z, 1.0, 1.0, 1.0);
        });
    }
}