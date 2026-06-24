package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.item.NetworkManagerItem;
import com.molox.createimp.item.NetworkSelectedState;
import com.molox.createimp.registry.ModDataComponents;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record ApplyNetworkPacket(
        InteractionHand hand,
        BlockPos targetPos,
        Vec3 clickLocation,
        boolean applyToWholeNetwork
) implements CustomPacketPayload {

    public static final Type<ApplyNetworkPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "apply_network"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ApplyNetworkPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBoolean(pkt.hand() == InteractionHand.MAIN_HAND);
                        buf.writeBlockPos(pkt.targetPos());
                        buf.writeDouble(pkt.clickLocation().x);
                        buf.writeDouble(pkt.clickLocation().y);
                        buf.writeDouble(pkt.clickLocation().z);
                        buf.writeBoolean(pkt.applyToWholeNetwork());
                    },
                    buf -> new ApplyNetworkPacket(
                            buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                            buf.readBlockPos(),
                            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                            buf.readBoolean()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ApplyNetworkPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ItemStack stack = player.getItemInHand(packet.hand());
            if (!stack.has(ModDataComponents.NETWORK_SELECTED_STATE.get())) return;

            NetworkSelectedState selectedState = stack.get(ModDataComponents.NETWORK_SELECTED_STATE.get());
            UUID newNetworkId = selectedState.networkId();

            Level level = player.level();
            BlockPos pos = packet.targetPos();
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) return;

            LogisticallyLinkedBehaviour linkedBehaviour = NetworkManagerItem.getBehaviour(be);
            boolean isFactoryPanel = be instanceof FactoryPanelBlockEntity;
            if (linkedBehaviour == null && !isFactoryPanel) return;

            if (packet.applyToWholeNetwork()) {
                handleWholeNetwork(player, level, be, linkedBehaviour, isFactoryPanel,
                        pos, packet.clickLocation(), newNetworkId);
            } else {
                handleSingle(player, level, be, linkedBehaviour, isFactoryPanel,
                        pos, packet.clickLocation(), newNetworkId);
            }

            player.displayClientMessage(
                    Component.translatable("createimp.message.network_manager.network_changed"),
                    true
            );
        });
    }

    private static void handleSingle(ServerPlayer player, Level level, BlockEntity be,
                                     LogisticallyLinkedBehaviour linkedBehaviour,
                                     boolean isFactoryPanel, BlockPos pos, Vec3 clickLocation,
                                     UUID newNetworkId) {
        if (linkedBehaviour != null) {
            NetworkManagerItem.reassignNetwork(linkedBehaviour, be, newNetworkId);
        } else {
            FactoryPanelBehaviour targeted = getTargetedBehaviour(
                    (FactoryPanelBlockEntity) be, pos,
                    level.getBlockState(pos), clickLocation);
            if (targeted == null || !targeted.isActive()) return;
            targeted.setNetwork(newNetworkId);
            ((FactoryPanelBlockEntity) be).notifyUpdate();
            ((FactoryPanelBlockEntity) be).setChanged();
        }
    }

    private static void handleWholeNetwork(ServerPlayer player, Level level, BlockEntity be,
                                           LogisticallyLinkedBehaviour linkedBehaviour,
                                           boolean isFactoryPanel, BlockPos pos, Vec3 clickLocation,
                                           UUID newNetworkId) {
        UUID oldNetworkId = null;

        if (linkedBehaviour != null) {
            oldNetworkId = NetworkManagerItem.getFreqId(be);
        } else {
            FactoryPanelBehaviour targeted = getTargetedBehaviour(
                    (FactoryPanelBlockEntity) be, pos,
                    level.getBlockState(pos), clickLocation);
            if (targeted != null && targeted.isActive() && targeted.network != null) {
                oldNetworkId = targeted.network;
            }
        }

        if (oldNetworkId == null) {
            handleSingle(player, level, be, linkedBehaviour, isFactoryPanel,
                    pos, clickLocation, newNetworkId);
            return;
        }

        final UUID finalOldNetworkId = oldNetworkId;

        Collection<LogisticallyLinkedBehaviour> allLinked =
                LogisticallyLinkedBehaviour.getAllPresent(finalOldNetworkId, false, false);

        for (LogisticallyLinkedBehaviour behaviour : allLinked) {
            BlockEntity linkedBe = behaviour.blockEntity;
            if (linkedBe == null || linkedBe.isRemoved()) continue;
            NetworkManagerItem.reassignNetwork(behaviour, linkedBe, newNetworkId);
        }

        updateFactoryPanels(level, finalOldNetworkId, newNetworkId);
    }

    private static FactoryPanelBehaviour getTargetedBehaviour(
            FactoryPanelBlockEntity fpbe, BlockPos pos, BlockState state, Vec3 clickLocation) {
        try {
            FactoryPanelBlock.PanelSlot slot = FactoryPanelBlock.getTargetedSlot(pos, state, clickLocation);
            if (slot == null) return null;
            var type = FactoryPanelBehaviour.getTypeForSlot(slot);
            var b = BlockEntityBehaviour.get(fpbe, type);
            return b instanceof FactoryPanelBehaviour fpb ? fpb : null;
        } catch (Exception e) {
            CreateImp.LOGGER.error("NetworkManager: failed to get targeted FactoryPanelBehaviour", e);
            return null;
        }
    }

    private static void updateFactoryPanels(Level level, UUID oldId, UUID newId) {
        java.util.Set<FactoryPanelBlockEntity> found = new java.util.HashSet<>();
        try {
            Field tickersField = Level.class.getDeclaredField("blockEntityTickers");
            tickersField.setAccessible(true);
            List<?> tickers = (List<?>) tickersField.get(level);

            for (Object wrapper : tickers) {
                BlockEntity be = unwrapTickingBlockEntity(wrapper);
                if (be instanceof FactoryPanelBlockEntity fpbe) {
                    found.add(fpbe);
                }
            }
        } catch (Exception e) {
            CreateImp.LOGGER.error("NetworkManager: failed to scan level for factory panels", e);
        }

        for (FactoryPanelBlockEntity fpbe : found) {
            if (fpbe.isRemoved()) continue;
            boolean changed = false;
            for (var type : new com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType[]{
                    FactoryPanelBehaviour.TOP_LEFT, FactoryPanelBehaviour.TOP_RIGHT,
                    FactoryPanelBehaviour.BOTTOM_LEFT, FactoryPanelBehaviour.BOTTOM_RIGHT
            }) {
                var b = BlockEntityBehaviour.get(fpbe, type);
                if (!(b instanceof FactoryPanelBehaviour fpb)) continue;
                if (!fpb.isActive()) continue;
                if (oldId.equals(fpb.network)) {
                    fpb.setNetwork(newId);
                    fpbe.notifyUpdate();
                    changed = true;
                }
            }
            if (changed) fpbe.setChanged();
        }
    }

    /**
     * 穿透 RebindableTickingBlockEntityWrapper → BoundTickingBlockEntity 两层包装，
     * 取出真正的 BlockEntity 实例。
     * 结构：
     *   RebindableTickingBlockEntityWrapper { TickingBlockEntity ticker }
     *     └─ BoundTickingBlockEntity { T blockEntity, BlockEntityTicker ticker }
     */
    @Nullable
    private static BlockEntity unwrapTickingBlockEntity(Object obj) {
        if (obj == null) return null;
        try {
            Object current = obj;
            // 最多拆 3 层，防止无限循环
            for (int depth = 0; depth < 3; depth++) {
                // 优先找类型为 BlockEntity 子类的字段
                for (Field f : current.getClass().getDeclaredFields()) {
                    if (BlockEntity.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        return (BlockEntity) f.get(current);
                    }
                }
                // 没找到 BlockEntity 字段，找类型为 TickingBlockEntity 接口的字段继续向下拆
                Field nextField = null;
                for (Field f : current.getClass().getDeclaredFields()) {
                    if (net.minecraft.world.level.block.entity.TickingBlockEntity.class
                            .isAssignableFrom(f.getType())) {
                        nextField = f;
                        break;
                    }
                }
                if (nextField == null) break;
                nextField.setAccessible(true);
                current = nextField.get(current);
                if (current == null) break;
            }
        } catch (Exception e) {
            // 忽略单个 ticker 的反射失败
        }
        return null;
    }
}