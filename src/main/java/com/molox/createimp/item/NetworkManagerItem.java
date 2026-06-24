package com.molox.createimp.item;

import com.molox.createimp.network.OpenNetworkManagerGuiPacket;
import com.molox.createimp.registry.ModDataComponents;
import com.molox.createimp.registry.ModMenuTypes;
import com.molox.createimp.screen.NetworkManagerLabelEditorMenu;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class NetworkManagerItem extends Item {

    private static Field blockEntityListField = null;

    public NetworkManagerItem(Properties properties) {
        super(properties);
    }

    private static List<NetworkLabel> getLabels(ItemStack stack) {
        List<NetworkLabel> labels = stack.get(ModDataComponents.NETWORK_MANAGER_LABELS.get());
        return labels != null ? labels : Collections.emptyList();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            List<NetworkLabel> labels = getLabels(stack);
            PacketDistributor.sendToPlayer(
                    (ServerPlayer) player,
                    new OpenNetworkManagerGuiPacket(hand, labels)
            );
        }
        return InteractionResultHolder.success(stack);
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
            FactoryPanelBehaviour targeted = getTargetedBehaviour(
                    (FactoryPanelBlockEntity) be,
                    context.getClickedPos(),
                    level.getBlockState(context.getClickedPos()),
                    context.getClickLocation()
            );
            if (targeted == null || !targeted.isActive()) return InteractionResult.PASS;
            targeted.setNetwork(newNetworkId);
            ((FactoryPanelBlockEntity) be).notifyUpdate();
            ((FactoryPanelBlockEntity) be).setChanged();
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

        ItemStack stack = player.getItemInHand(hand);
        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        ServerPlayer serverPlayer = (ServerPlayer) player;

        UUID networkId = null;
        if (be != null) {
            networkId = getFreqId(be);
            if (networkId == null && be instanceof FactoryPanelBlockEntity fpbe) {
                FactoryPanelBehaviour targeted = getTargetedBehaviour(
                        fpbe,
                        context.getClickedPos(),
                        level.getBlockState(context.getClickedPos()),
                        context.getClickLocation()
                );
                if (targeted != null && targeted.isActive() && targeted.network != null) {
                    networkId = targeted.network;
                }
            }
        }

        if (networkId == null) {
            List<NetworkLabel> labels = getLabels(stack);
            PacketDistributor.sendToPlayer(serverPlayer,
                    new OpenNetworkManagerGuiPacket(hand, labels));
        } else {
            final UUID finalNetworkId = networkId;
            List<NetworkLabel> labels = getLabels(stack);
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

    private static FactoryPanelBehaviour getTargetedBehaviour(
            FactoryPanelBlockEntity fpbe, net.minecraft.core.BlockPos pos,
            BlockState state, Vec3 clickLocation) {
        try {
            FactoryPanelBlock.PanelSlot slot = FactoryPanelBlock.getTargetedSlot(pos, state, clickLocation);
            if (slot == null) return null;
            var type = FactoryPanelBehaviour.getTypeForSlot(slot);
            var b = BlockEntityBehaviour.get(fpbe, type);
            return b instanceof FactoryPanelBehaviour fpb ? fpb : null;
        } catch (Exception e) {
            Create.LOGGER.error("NetworkManager: failed to get targeted FactoryPanelBehaviour", e);
            return null;
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
        } catch (Exception e) {
            Create.LOGGER.error("NetworkManager: failed to update factory panels", e);
        }
    }

    private static void reassignNetwork(LogisticallyLinkedBehaviour behaviour,
                                        BlockEntity be, UUID newNetworkId) {
        try {
            LogisticallyLinkedBehaviour.remove(behaviour);
            behaviour.destroy();

            Field freqIdField = LogisticallyLinkedBehaviour.class.getDeclaredField("freqId");
            freqIdField.setAccessible(true);
            freqIdField.set(behaviour, newNetworkId);

            Field addedField = LogisticallyLinkedBehaviour.class.getDeclaredField("addedGlobally");
            addedField.setAccessible(true);
            addedField.set(behaviour, false);

            Field loadedField = LogisticallyLinkedBehaviour.class.getDeclaredField("loadedGlobally");
            loadedField.setAccessible(true);
            loadedField.set(behaviour, false);

            behaviour.initialize();
            be.setChanged();
        } catch (Exception e) {
            Create.LOGGER.error("NetworkManager: failed to reassign network", e);
        }
    }

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