package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.template_panel.TemplatePanelBehaviour;
import com.molox.createimp.block.template_panel.TemplatePanelBlockEntity;
import com.molox.createimp.block.template_panel.TemplatePanelConnection;
import com.molox.createimp.block.template_panel.TemplatePanelPosition;
import net.createmod.catnip.codecs.stream.CatnipLargerStreamCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record TemplatePanelConfigurationPacket(
        TemplatePanelPosition position,
        String address,
        Map<TemplatePanelPosition, Integer> inputAmounts,
        List<ItemStack> craftingArrangement,
        int outputAmount,
        @Nullable TemplatePanelPosition removeConnection,
        boolean reset
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TemplatePanelConfigurationPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "configure_template_panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TemplatePanelConfigurationPacket> STREAM_CODEC = CatnipLargerStreamCodecs.composite(
            TemplatePanelPosition.STREAM_CODEC, TemplatePanelConfigurationPacket::position,
            ByteBufCodecs.STRING_UTF8, TemplatePanelConfigurationPacket::address,
            ByteBufCodecs.map(HashMap::new, TemplatePanelPosition.STREAM_CODEC, ByteBufCodecs.INT), TemplatePanelConfigurationPacket::inputAmounts,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, TemplatePanelConfigurationPacket::craftingArrangement,
            ByteBufCodecs.VAR_INT, TemplatePanelConfigurationPacket::outputAmount,
            net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders.nullable(TemplatePanelPosition.STREAM_CODEC), TemplatePanelConfigurationPacket::removeConnection,
            ByteBufCodecs.BOOL, TemplatePanelConfigurationPacket::reset,
            TemplatePanelConfigurationPacket::new
    );

    @Override
    public CustomPacketPayload.Type<TemplatePanelConfigurationPacket> type() {
        return TYPE;
    }

    public static void handle(TemplatePanelConfigurationPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ServerLevel level = player.serverLevel();
            if (!level.isLoaded(packet.position().pos())) {
                return;
            }
            if (!player.canInteractWithBlock(packet.position().pos(), 20.0)) {
                return;
            }
            BlockEntity be = level.getBlockEntity(packet.position().pos());
            if (!(be instanceof TemplatePanelBlockEntity fpbe)) {
                return;
            }
            TemplatePanelBehaviour behaviour = fpbe.panels.get(packet.position().slot());
            if (behaviour == null) {
                return;
            }
            behaviour.recipeAddress = packet.reset() ? "" : packet.address();
            behaviour.recipeOutput = packet.reset() ? 1 : packet.outputAmount();
            behaviour.activeCraftingArrangement = packet.reset() ? List.of() : packet.craftingArrangement();
            if (packet.reset()) {
                behaviour.disconnectAll();
                behaviour.setFilter(ItemStack.EMPTY);
                fpbe.redraw = true;
                fpbe.notifyUpdate();
                return;
            }
            for (Map.Entry<TemplatePanelPosition, Integer> entry : packet.inputAmounts().entrySet()) {
                TemplatePanelPosition key = entry.getKey();
                TemplatePanelConnection connection = behaviour.targetedBy.get(key);
                if (connection == null) continue;
                connection.amount = entry.getValue();
            }
            if (packet.removeConnection() != null) {
                behaviour.targetedBy.remove(packet.removeConnection());
                TemplatePanelBehaviour source = TemplatePanelBehaviour.at((BlockAndTintGetter) level, packet.removeConnection());
                if (source != null) {
                    source.targeting.remove(behaviour.getPanelPosition());
                    source.blockEntity.sendData();
                }
            }
            fpbe.notifyUpdate();
        });
    }
}