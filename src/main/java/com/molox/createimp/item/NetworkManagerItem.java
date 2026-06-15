package com.molox.createimp.item;

import com.molox.createimp.data.NetworkManagerSavedData;
import com.molox.createimp.network.OpenNetworkManagerGuiPacket;
import com.molox.createimp.registry.ModDataComponents;
import com.molox.createimp.registry.ModMenuTypes;
import com.molox.createimp.screen.NetworkManagerLabelEditorMenu;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class NetworkManagerItem extends Item {

    private static Field blockEntityListField = null;

    public NetworkManagerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        if (!level.isClientSide()) {
            List<NetworkLabel> labels = NetworkManagerSavedData.get(
                    ((ServerPlayer) player).server).getLabels();
            PacketDistributor.sendToPlayer(
                    (ServerPlayer) player,
                    new OpenNetworkManagerGuiPacket(hand, labels)
            );
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!stack.has(ModDataComponents.NETWORK_SELECTED_STATE.get())) return InteractionResult.PASS;

        Level level = context.getLevel();
        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        if (be == null) return InteractionResult.PASS;

        LogisticallyLinkedBehaviour linkedBehaviour = getBehaviour(be);
        boolean isFactoryPanel = be instanceof FactoryPanelBlockEntity;

        if (linkedBehaviour == null && !isFactoryPanel) return InteractionResult.PASS;

        if (level.isClientSide()) return InteractionResult.SUCCESS;

        NetworkSelectedState selectedState = stack.get(ModDataComponents.NETWORK_SELECTED_STATE.get());
        UUID newNetworkId = selectedState.networkId();

        if (linkedBehaviour != null) {
            UUID oldNetworkId = getFreqId(be);
            reassignNetwork(linkedBehaviour, be, newNetworkId);
            if (oldNetworkId != null) {
                updateFactoryPanels(level, oldNetworkId, newNetworkId);
            }
        } else {
            setFactoryPanelNetwork((FactoryPanelBlockEntity) be, newNetworkId);
        }

        player.displayClientMessage(
                Component.translatable("createimp.message.network_manager.network_changed"),
                true
        );

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;

        Level level = context.getLevel();
        InteractionHand hand = context.getHand();

        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        ServerPlayer serverPlayer = (ServerPlayer) player;

        UUID networkId = null;
        if (be != null) {
            networkId = getFreqId(be);
            if (networkId == null && be instanceof FactoryPanelBlockEntity fpbe) {
                networkId = getFactoryPanelNetworkId(fpbe);
            }
        }

        if (networkId == null) {
            List<NetworkLabel> labels = NetworkManagerSavedData.get(serverPlayer.server).getLabels();
            PacketDistributor.sendToPlayer(serverPlayer,
                    new OpenNetworkManagerGuiPacket(hand, labels));
        } else {
            final UUID finalNetworkId = networkId;
            List<NetworkLabel> labels = NetworkManagerSavedData.get(serverPlayer.server).getLabels();
            Optional<UUID> targetNetworkId = Optional.of(finalNetworkId);
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.empty();
                }
                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new NetworkManagerLabelEditorMenu(
                            ModMenuTypes.NETWORK_MANAGER_LABEL_EDITOR.get(),
                            id, inv, hand, labels, targetNetworkId);
                }
            }, buf -> {
                buf.writeBoolean(hand == InteractionHand.MAIN_HAND);
                NetworkLabel.STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list())
                        .encode(buf, labels);
                buf.writeBoolean(true);
                buf.writeUUID(finalNetworkId);
            });
        }
        return InteractionResult.SUCCESS;
    }

    // ---- 工厂仪表 ----

    private static List<FactoryPanelBehaviour> getFactoryPanelBehaviours(FactoryPanelBlockEntity fpbe) {
        List<FactoryPanelBehaviour> result = new ArrayList<>();
        for (var type : new com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType[]{
                FactoryPanelBehaviour.TOP_LEFT,
                FactoryPanelBehaviour.TOP_RIGHT,
                FactoryPanelBehaviour.BOTTOM_LEFT,
                FactoryPanelBehaviour.BOTTOM_RIGHT
        }) {
            var b = BlockEntityBehaviour.get(fpbe, type);
            if (b instanceof FactoryPanelBehaviour fpb) {
                result.add(fpb);
            }
        }
        return result;
    }

    private static UUID getFactoryPanelNetworkId(FactoryPanelBlockEntity fpbe) {
        for (FactoryPanelBehaviour fpb : getFactoryPanelBehaviours(fpbe)) {
            if (fpb.network != null) return fpb.network;
        }
        return null;
    }

    /**
     * 设置工厂仪表的网络。
     * 工厂仪表的面板由 active 字段控制是否写入 NBT。
     * 对已激活（有连线）的面板：直接 setNetwork + notifyUpdate。
     * 对未激活（没有连线）的面板：先 enable()（会设 active=true 并 notifyUpdate），再 setNetwork。
     */
    private static void setFactoryPanelNetwork(FactoryPanelBlockEntity fpbe, UUID newNetworkId) {
        boolean anyChanged = false;
        for (FactoryPanelBehaviour fpb : getFactoryPanelBehaviours(fpbe)) {
            if (!fpb.isActive()) {
                // enable() 会设 active=true 并调 notifyUpdate()
                fpb.enable();
            }
            fpb.setNetwork(newNetworkId);
            // notifyUpdate 同步到客户端
            fpbe.notifyUpdate();
            anyChanged = true;
        }
        if (anyChanged) {
            fpbe.setChanged();
        }
    }

    @SuppressWarnings("unchecked")
    private static void updateFactoryPanels(Level level, UUID oldId, UUID newId) {
        try {
            if (blockEntityListField == null) {
                for (Field f : Level.class.getDeclaredFields()) {
                    if (f.getType() == List.class) {
                        f.setAccessible(true);
                        Object val = f.get(level);
                        if (val instanceof List<?> list && !list.isEmpty()
                                && list.get(0) instanceof BlockEntity) {
                            blockEntityListField = f;
                            break;
                        }
                    }
                }
            }
            if (blockEntityListField == null) return;

            List<BlockEntity> beList = (List<BlockEntity>) blockEntityListField.get(level);
            for (BlockEntity be : beList) {
                if (!(be instanceof FactoryPanelBlockEntity fpbe)) continue;
                boolean changed = false;
                for (FactoryPanelBehaviour fpb : getFactoryPanelBehaviours(fpbe)) {
                    if (oldId.equals(fpb.network)) {
                        fpb.setNetwork(newId);
                        fpbe.notifyUpdate();
                        changed = true;
                    }
                }
                if (changed) fpbe.setChanged();
            }
        } catch (Exception e) {
            Create.LOGGER.error("NetworkManager: failed to update factory panels", e);
        }
    }

    // ---- 连接站网络重新分配 ----

    private static void reassignNetwork(LogisticallyLinkedBehaviour behaviour,
                                        BlockEntity be, UUID newNetworkId) {
        try {
            // 1. 从 LINKS 心跳缓存移除
            LogisticallyLinkedBehaviour.remove(behaviour);

            // 2. 从旧网络的 totalLinks 和 loadedLinks 永久注销
            behaviour.destroy();

            // 3. 修改 freqId 为新网络
            Field freqIdField = LogisticallyLinkedBehaviour.class.getDeclaredField("freqId");
            freqIdField.setAccessible(true);
            freqIdField.set(behaviour, newNetworkId);

            // 4. 重置 addedGlobally，让 initialize 走 linkAdded 路径
            Field addedField = LogisticallyLinkedBehaviour.class.getDeclaredField("addedGlobally");
            addedField.setAccessible(true);
            addedField.set(behaviour, false);

            // 5. 重置 loadedGlobally，让 initialize 同时走 linkLoaded 路径
            //    不重置的话 initialize 会跳过 linkLoaded，导致 loadedLinks 不更新
            //    造成 getUnloadedLinkCount = totalLinks(1) - loadedLinks(0) = 1，工厂仪表显示 ?
            Field loadedField = LogisticallyLinkedBehaviour.class.getDeclaredField("loadedGlobally");
            loadedField.setAccessible(true);
            loadedField.set(behaviour, false);

            // 6. 重新注册到新网络（linkAdded + linkLoaded），更新 LINKS 缓存
            behaviour.initialize();

            // 7. 触发 NBT 保存
            be.setChanged();

        } catch (Exception e) {
            Create.LOGGER.error("NetworkManager: failed to reassign network", e);
        }
    }

    // ---- 工具方法 ----

    public static LogisticallyLinkedBehaviour getBehaviour(BlockEntity be) {
        return (LogisticallyLinkedBehaviour) BlockEntityBehaviour.get(
                be, LogisticallyLinkedBehaviour.TYPE);
    }

    public static UUID getFreqId(BlockEntity be) {
        try {
            LogisticallyLinkedBehaviour behaviour = getBehaviour(be);
            if (behaviour == null) return null;
            Field freqIdField = LogisticallyLinkedBehaviour.class.getDeclaredField("freqId");
            freqIdField.setAccessible(true);
            return (UUID) freqIdField.get(behaviour);
        } catch (Exception e) {
            return null;
        }
    }
}