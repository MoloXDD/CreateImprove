package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.util.IFactoryPanelBehaviourDemandMode;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
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

public record SaveFactoryPanelDemandModePacket(
        FactoryPanelPosition position,
        boolean demandMode
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaveFactoryPanelDemandModePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "save_factory_panel_demand_mode")
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveFactoryPanelDemandModePacket> STREAM_CODEC =
            StreamCodec.composite(
                    FactoryPanelPosition.STREAM_CODEC, SaveFactoryPanelDemandModePacket::position,
                    ByteBufCodecs.BOOL, SaveFactoryPanelDemandModePacket::demandMode,
                    SaveFactoryPanelDemandModePacket::new
            );

    @Override
    public CustomPacketPayload.Type<SaveFactoryPanelDemandModePacket> type() {
        return TYPE;
    }

    public static void handle(SaveFactoryPanelDemandModePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ServerLevel level = player.serverLevel();
            BlockPos pos = packet.position().pos();
            if (!level.isLoaded(pos)) return;
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof FactoryPanelBlockEntity fpbe)) return;
            FactoryPanelBlock.PanelSlot slot = packet.position().slot();
            FactoryPanelBehaviour behaviour = fpbe.panels.get(slot);
            if (behaviour == null || !behaviour.isActive()) return;
            ((IFactoryPanelBehaviourDemandMode) behaviour).createimp$setDemandMode(packet.demandMode());
            fpbe.setChanged();
            fpbe.sendData();
        });
    }
}