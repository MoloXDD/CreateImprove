package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.template_panel.TemplatePanelBehaviour;
import com.molox.createimp.block.template_panel.TemplatePanelBlockEntity;
import com.molox.createimp.block.template_panel.TemplatePanelPosition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TemplatePanelConnectionPacket(
        TemplatePanelPosition fromPos,
        TemplatePanelPosition toPos,
        boolean relocate
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TemplatePanelConnectionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "connect_template_panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TemplatePanelConnectionPacket> STREAM_CODEC = StreamCodec.composite(
            TemplatePanelPosition.STREAM_CODEC, TemplatePanelConnectionPacket::fromPos,
            TemplatePanelPosition.STREAM_CODEC, TemplatePanelConnectionPacket::toPos,
            ByteBufCodecs.BOOL, TemplatePanelConnectionPacket::relocate,
            TemplatePanelConnectionPacket::new
    );

    @Override
    public CustomPacketPayload.Type<TemplatePanelConnectionPacket> type() {
        return TYPE;
    }

    public static void handle(TemplatePanelConnectionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ServerLevel level = player.serverLevel();
            if (!level.isLoaded(packet.toPos().pos())) {
                return;
            }
            if (!player.canInteractWithBlock(packet.toPos().pos(), 40.0)) {
                return;
            }
            BlockEntity be = level.getBlockEntity(packet.toPos().pos());
            if (!(be instanceof TemplatePanelBlockEntity)) {
                return;
            }
            TemplatePanelBehaviour behaviour = TemplatePanelBehaviour.at(level, packet.toPos());
            if (behaviour == null) {
                return;
            }
            if (packet.relocate()) {
                behaviour.moveTo(packet.fromPos(), player);
            } else {
                behaviour.addConnection(packet.fromPos());
            }
            be.setChanged();
        });
    }
}