package com.molox.createimp.client;

import com.molox.createimp.item.NetworkManagerItem;
import com.molox.createimp.item.NetworkSelectedState;
import com.molox.createimp.registry.ModDataComponents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedClientHandler;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.UUID;

public class NetworkManagerClientHandler {

    private static Field previouslyHeldFrequencyField = null;

    private static void initField() {
        if (previouslyHeldFrequencyField != null) return;
        try {
            previouslyHeldFrequencyField = LogisticallyLinkedClientHandler.class
                    .getDeclaredField("previouslyHeldFrequency");
            previouslyHeldFrequencyField.setAccessible(true);
        } catch (Exception e) {
            // 获取失败则高亮工厂仪表功能不可用，不影响其他功能
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
        if (state == null) return;

        UUID networkId = state.networkId();

        // 设置 previouslyHeldFrequency，让 Create 的 tickPanel 正确高亮工厂仪表
        // FactoryPanelBehaviour.lazyTick 会调 LogisticallyLinkedClientHandler.tickPanel(this)
        // tickPanel 检查 previouslyHeldFrequency.equals(fpb.network) 来决定是否高亮
        setPreviouslyHeldFrequency(networkId);

        // 高亮所有同网络的连接站等元件（LogisticallyLinkedBehaviour）
        Collection<LogisticallyLinkedBehaviour> linked =
                LogisticallyLinkedBehaviour.getAllPresent(networkId, false, false);

        for (LogisticallyLinkedBehaviour behaviour : linked) {
            BlockEntity be = behaviour.blockEntity;
            if (be == null || be.isRemoved()) continue;
            if (!player.blockPosition().closerThan(be.getBlockPos(), 64)) continue;

            VoxelShape shape = be.getBlockState().getShape(
                    be.getLevel(), be.getBlockPos());
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

    public static void renderHud(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        NetworkSelectedState state = getSelectedState(player);
        if (state == null) return;

        Component text = Component.translatable(
                "createimp.hud.network_manager.selected", state.labelName());

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int textWidth = mc.font.width(text);

        int x = (screenWidth - textWidth) / 2;
        int y = screenHeight - 59;

        graphics.drawString(mc.font, text, x, y, 0xFFFFFF, true);
    }

    private static NetworkSelectedState getSelectedState(LocalPlayer player) {
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof NetworkManagerItem
                    && stack.has(ModDataComponents.NETWORK_SELECTED_STATE.get())) {
                return stack.get(ModDataComponents.NETWORK_SELECTED_STATE.get());
            }
        }
        return null;
    }
}