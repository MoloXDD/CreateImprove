package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.template_panel.TemplatePanelBehaviour;
import com.molox.createimp.block.template_panel.TemplatePanelBlockEntity;
import com.molox.createimp.block.template_panel.TemplatePanelPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SaveTemplatePanelDemandModePacket(
        TemplatePanelPosition position,
        boolean demandMode
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaveTemplatePanelDemandModePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "save_template_panel_demand_mode")
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveTemplatePanelDemandModePacket> STREAM_CODEC =
            StreamCodec.composite(
                    TemplatePanelPosition.STREAM_CODEC, SaveTemplatePanelDemandModePacket::position,
                    ByteBufCodecs.BOOL, SaveTemplatePanelDemandModePacket::demandMode,
                    SaveTemplatePanelDemandModePacket::new
            );

    @Override
    public CustomPacketPayload.Type<SaveTemplatePanelDemandModePacket> type() {
        return TYPE;
    }

    public static void handle(SaveTemplatePanelDemandModePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ServerLevel level = player.serverLevel();
            BlockPos pos = packet.position().pos();
            if (!level.isLoaded(pos)) return;
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof TemplatePanelBlockEntity tpbe)) return;
            TemplatePanelBehaviour behaviour = tpbe.panels.get(packet.position().slot());
            if (behaviour == null || !behaviour.isActive()) return;
            behaviour.demandMode = packet.demandMode();
            tpbe.setChanged();
            tpbe.sendData();
        });
    }
}