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
import net.neoforged.neoforge.network.handling.IPayloadContext;

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

    public static void handle(WorkWarehouseMaterialsReadyEffectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            Vec3 center = Vec3.atCenterOf(packet.pos());
            // 与工厂仪表检测物品数量满足需求时（FactoryPanelBehaviour 中
            // !satisfied && shouldSatisfy 分支）播放的同一组音效。
            AllSoundEvents.CONFIRM.playAt(level, center, 0.075f, 1.0f, false);
            AllSoundEvents.CONFIRM_2.playAt(level, center, 0.125f, 0.575f, false);
            level.addParticle(new WiFiParticle.Data(), center.x, center.y, center.z, 1.0, 1.0, 1.0);
        });
    }
}