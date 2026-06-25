package com.molox.createimp.client;

import com.molox.createimp.CreateImp;
import com.molox.createimp.item.NetworkManagerItem;
import com.molox.createimp.item.NetworkSelectedState;
import com.molox.createimp.network.ApplyNetworkPacket;
import com.molox.createimp.registry.ModDataComponents;
import com.molox.createimp.screen.NetworkManagerConfigScreen;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedClientHandler;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.UUID;

public class NetworkManagerClientHandler {

    private static int longPressTicks = -1;
    private static BlockPos longPressPos = null;
    private static Vec3 longPressClickLocation = null;
    private static InteractionHand longPressHand = null;

    public static void startLongPressTracking(BlockPos pos, Vec3 clickLocation, InteractionHand hand) {
        longPressTicks = 0;
        longPressPos = pos;
        longPressClickLocation = clickLocation;
        longPressHand = hand;
    }

    public static void cancelLongPress() {
        longPressTicks = -1;
        longPressPos = null;
        longPressClickLocation = null;
        longPressHand = null;
    }

    /**
     * 统一处理 RightClickBlock 事件（以 HIGH 优先级注册，先于 ValueSettingsInputHandler）。
     *
     * 情况一：长按计时已在进行中，且点击的仍是同一方块 → 取消事件，防止框架重置按键状态。
     * 情况二：长按计时尚未开始，但玩家手持处于选择状态的网络管理器，且目标方块是可配置的
     *         网络元件（含 LogisticallyLinkedBehaviour 或 FactoryPanelBlockEntity）→ 立即取消
     *         事件（在 ValueSettingsInputHandler 之前），并启动长按计时。
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide() != net.neoforged.fml.LogicalSide.CLIENT) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        BlockPos pos = event.getPos();

        // 情况一：计时已在进行，防止框架重置
        if (longPressTicks != -1) {
            if (longPressPos != null && longPressPos.equals(pos)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
            return;
        }

        // 情况二：检测是否应该接管本次右键
        NetworkSelectedState state = getSelectedState(player);
        if (state == null) return;

        if (player.isShiftKeyDown()) return;

        // 判断目标方块是否是可配置的网络元件
        if (mc.level == null) return;
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (be == null) return;

        boolean isTarget = NetworkManagerItem.getBehaviour(be) != null
                || be instanceof FactoryPanelBlockEntity;
        if (!isTarget) return;

        // 满足条件：取消事件，接管交互
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);

        Vec3 clickLocation = event.getHitVec() != null
                ? event.getHitVec().getLocation()
                : Vec3.atCenterOf(pos);

        startLongPressTracking(pos, clickLocation, event.getHand());
    }

    private static Field previouslyHeldFrequencyField = null;

    private static void initField() {
        if (previouslyHeldFrequencyField != null) return;
        try {
            previouslyHeldFrequencyField = LogisticallyLinkedClientHandler.class
                    .getDeclaredField("previouslyHeldFrequency");
            previouslyHeldFrequencyField.setAccessible(true);
        } catch (Exception ignored) {
        }
    }

    private static void setPreviouslyHeldFrequency(UUID uuid) {
        initField();
        if (previouslyHeldFrequencyField == null) return;
        try {
            previouslyHeldFrequencyField.set(null, uuid);
        } catch (Exception ignored) {
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        NetworkSelectedState state = getSelectedState(player);
        if (state != null) {
            UUID networkId = state.networkId();
            setPreviouslyHeldFrequency(networkId);

            Collection<LogisticallyLinkedBehaviour> linked =
                    LogisticallyLinkedBehaviour.getAllPresent(networkId, false, false);

            for (LogisticallyLinkedBehaviour behaviour : linked) {
                BlockEntity be = behaviour.blockEntity;
                if (be == null || be.isRemoved()) continue;
                if (!player.blockPosition().closerThan(be.getBlockPos(), 64)) continue;

                VoxelShape shape = be.getBlockState().getShape(be.getLevel(), be.getBlockPos());
                if (shape.isEmpty()) continue;

                java.util.List<AABB> aabbs = shape.toAabbs();
                for (int i = 0; i < aabbs.size(); i++) {
                    AABB aabb = aabbs.get(i)
                            .inflate(-1 / 128.0)
                            .move(be.getBlockPos());
                    Outliner.getInstance()
                            .showAABB(Pair.of(be.getBlockPos(), i), aabb)
                            .lineWidth(1f / 32f)
                            .disableLineNormals()
                            .colored(0xFFFFFF);
                }
            }
        }

        if (longPressTicks == -1) return;

        if (!mc.options.keyUse.isDown()) {
            int threshold = CreateImp.getConfig().networkManagerConfig.longPressThreshold;
            if (longPressTicks < threshold) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new ApplyNetworkPacket(longPressHand, longPressPos, longPressClickLocation, false));
            }
            cancelLongPress();
            return;
        }

        if (longPressTicks > 3) {
            player.swinging = false;
        }

        longPressTicks++;

        int threshold = CreateImp.getConfig().networkManagerConfig.longPressThreshold;
        if (longPressTicks >= threshold) {
            ScreenOpener.open(new NetworkManagerConfigScreen(
                    longPressHand, longPressPos, longPressClickLocation));
            cancelLongPress();
        }
    }

    public static void renderHud(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        NetworkSelectedState state = getSelectedState(player);
        if (state == null) return;

        Component text = Component.translatable(
                "createimp.hud.network_manager.selected", state.labelName());

        int screenWidth  = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int textWidth    = mc.font.width(text);

        int x = (screenWidth - textWidth) / 2;
        int y = screenHeight - 59;

        graphics.drawString(mc.font, text, x, y, 0xFFFFFF, true);
    }

    private static NetworkSelectedState getSelectedState(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof NetworkManagerItem
                    && stack.has(ModDataComponents.NETWORK_SELECTED_STATE.get())) {
                return stack.get(ModDataComponents.NETWORK_SELECTED_STATE.get());
            }
        }
        return null;
    }
}