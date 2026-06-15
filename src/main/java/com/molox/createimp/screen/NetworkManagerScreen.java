package com.molox.createimp.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.molox.createimp.item.NetworkLabel;
import com.molox.createimp.item.NetworkSelectedState;
import com.molox.createimp.network.OpenNetworkManagerGuiPacket;
import com.molox.createimp.network.SaveNetworkManagerDataPacket;
import com.molox.createimp.registry.ModDataComponents;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NetworkManagerScreen extends AbstractSimiScreen {

    private static final int BODY_REPEAT = 4;

    private static final int GUI_WIDTH   = AllGuiTextures.STOCK_KEEPER_CATEGORY.getWidth();
    private static final int HEADER_H    = AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.getHeight();
    private static final int BODY_H      = AllGuiTextures.STOCK_KEEPER_CATEGORY.getHeight();
    private static final int FOOTER_H    = AllGuiTextures.STOCK_KEEPER_CATEGORY_FOOTER.getHeight();
    private static final int GUI_HEIGHT  = HEADER_H + BODY_H * BODY_REPEAT + FOOTER_H;

    private static final int ENTRY_STEP    = 20;
    private static final int SCISSOR_X    = 3;
    private static final int SCISSOR_Y    = 16;
    private static final int SCISSOR_X2   = 187;
    private static final int SCISSOR_Y2   = 19 + BODY_H * BODY_REPEAT;
    private static final int ENTRY_START_Y = 24;
    private static final int DELETE_X     = 153;
    private static final int DELETE_W     = 16;
    private static final int DELETE_H     = 16;
    private static final int TEXT_COLOR   = 0x656565;

    private final InteractionHand hand;
    private final List<NetworkLabel> labels;
    private final LerpedFloat scroll;
    private IconButton confirmButton;

    public NetworkManagerScreen(OpenNetworkManagerGuiPacket packet) {
        super(Component.translatable("item.createimp.network_manager"));
        this.hand   = packet.hand();
        this.labels = new ArrayList<>(packet.labels());
        this.scroll = LerpedFloat.linear().startWithValue(0);
    }

    public static void open(OpenNetworkManagerGuiPacket packet) {
        // 打开主菜单时清除选中状态
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ItemStack stack = mc.player.getItemInHand(packet.hand());
            if (stack.has(ModDataComponents.NETWORK_SELECTED_STATE.get())) {
                // 发包让服务端清除选中状态
                PacketDistributor.sendToServer(
                        new com.molox.createimp.network.ClearNetworkSelectionPacket(packet.hand()));
            }
        }
        ScreenOpener.open(new NetworkManagerScreen(packet));
    }

    @Override
    protected void init() {
        setWindowSize(GUI_WIDTH, GUI_HEIGHT);
        super.init();

        confirmButton = new IconButton(
                guiLeft + GUI_WIDTH - 25,
                guiTop + GUI_HEIGHT - 25,
                18, 18,
                AllIcons.I_CONFIRM
        );
        confirmButton.withCallback(this::onClose);
        addRenderableWidget(confirmButton);
    }

    @Override
    public void tick() {
        super.tick();
        scroll.tickChaser();
    }

    private int getMaxScroll() {
        return Math.max(0, labels.size() * ENTRY_STEP + 5 - BODY_H * BODY_REPEAT);
    }

    private void clampScroll() {
        float clamped = Mth.clamp(scroll.getChaseTarget(), 0, getMaxScroll());
        scroll.chase(clamped, 0.4, LerpedFloat.Chaser.EXP);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (getMaxScroll() > 0) {
            float newTarget = scroll.getChaseTarget() - (float) scrollY * ENTRY_STEP;
            newTarget = Mth.clamp(newTarget, 0, getMaxScroll());
            scroll.chase(newTarget, 0.4, LerpedFloat.Chaser.EXP);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            float scrollOff = scroll.getValue(1.0f);
            int relX        = (int) mouseX - guiLeft - 7;
            int relYBase    = (int) mouseY - guiTop - ENTRY_START_Y + (int) scrollOff;

            for (int i = 0; i < labels.size(); i++) {
                int entryRelY = relYBase - i * ENTRY_STEP;
                if (entryRelY <= 0 || entryRelY > DELETE_H) continue;

                // 点击删除按钮
                if (relX > DELETE_X && relX <= DELETE_X + DELETE_W) {
                    labels.remove(i);
                    clampScroll();
                    PacketDistributor.sendToServer(
                            new SaveNetworkManagerDataPacket(hand, labels, false));
                    return true;
                }

                // 点击标签主体区域（排除删除按钮）→ 选中
                if (relX > 0 && relX <= DELETE_X) {
                    NetworkLabel label = labels.get(i);
                    if (label.networkId().isPresent()) {
                        // 发包让服务端设置选中状态，然后关闭界面
                        PacketDistributor.sendToServer(
                                new com.molox.createimp.network.SetNetworkSelectionPacket(
                                        hand, label.name(), label.networkId().get()));
                        onClose();
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int y = guiTop;
        AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.render(graphics, guiLeft, y);
        y += HEADER_H;
        for (int i = 0; i < BODY_REPEAT; i++) {
            AllGuiTextures.STOCK_KEEPER_CATEGORY.render(graphics, guiLeft, y);
            y += BODY_H;
        }
        AllGuiTextures.STOCK_KEEPER_CATEGORY_FOOTER.render(graphics, guiLeft, y);

        Component title = Component.translatable("item.createimp.network_manager");
        int titleX      = guiLeft + GUI_WIDTH / 2 - font.width(title) / 2;
        int titleY      = guiTop  + (HEADER_H - font.lineHeight) / 2;
        graphics.drawString(font, title, titleX, titleY, 0xFFFFFF, false);

        graphics.enableScissor(
                guiLeft + SCISSOR_X,  guiTop + SCISSOR_Y,
                guiLeft + SCISSOR_X2, guiTop + SCISSOR_Y2
        );

        float scrollOff = scroll.getValue(partialTicks);
        PoseStack pose  = graphics.pose();
        pose.pushPose();
        pose.translate(0, -scrollOff, 0);

        for (int i = 0; i < labels.size(); i++) {
            NetworkLabel label = labels.get(i);
            int entryX = guiLeft + 7;
            int entryY = guiTop  + ENTRY_START_Y + i * ENTRY_STEP;

            AllGuiTextures.STOCK_KEEPER_CATEGORY_ENTRY.render(graphics, entryX, entryY);

            ItemStack icon = label.icon().isEmpty()
                    ? new ItemStack(net.minecraft.world.item.Items.BARRIER)
                    : label.icon();
            graphics.renderItem(icon, entryX + 14, entryY + 1);

            String text = label.name();
            if (text.length() > 20) text = text.substring(0, 20) + "...";
            graphics.drawString(font, Component.literal(text),
                    entryX + 35, entryY + 5, TEXT_COLOR, false);
        }

        pose.popPose();
        graphics.disableScissor();

        // 悬停 tooltip
        float scrollOff2 = scroll.getValue(partialTicks);
        int relX         = mouseX - guiLeft - 7;
        int relYBase     = mouseY - guiTop  - ENTRY_START_Y + (int) scrollOff2;
        for (int i = 0; i < labels.size(); i++) {
            int entryRelY = relYBase - i * ENTRY_STEP;
            if (entryRelY <= 0 || entryRelY > DELETE_H) continue;
            int entryScreenY = guiTop + ENTRY_START_Y + i * ENTRY_STEP - (int) scrollOff2;
            if (entryScreenY + ENTRY_STEP <= guiTop + SCISSOR_Y
                    || entryScreenY >= guiTop + SCISSOR_Y2) continue;

            NetworkLabel label = labels.get(i);

            // 删除按钮悬停
            if (relX > DELETE_X && relX <= DELETE_X + DELETE_W) {
                graphics.renderComponentTooltip(font,
                        List.of(Component.translatable("create.gui.stock_ticker.delete_category")),
                        mouseX, mouseY);
                break;
            }

            // 标签主体悬停（仅有 networkId 的标签才可选中）
            if (relX > 0 && relX <= DELETE_X) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal(label.name()).withStyle(ChatFormatting.WHITE));
                if (label.networkId().isPresent()) {
                    tooltip.add(Component.translatable("createimp.gui.network_manager.lmb_select")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                }
                graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                break;
            }
        }
    }
}