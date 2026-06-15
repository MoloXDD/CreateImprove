package com.molox.createimp.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.molox.createimp.item.NetworkLabel;
import com.molox.createimp.network.OpenNetworkManagerEditorPacket;
import com.molox.createimp.network.OpenNetworkManagerGuiPacket;
import com.molox.createimp.network.SaveNetworkManagerDataPacket;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class NetworkManagerScreen extends AbstractSimiScreen {

    private static final int BODY_REPEAT = 4;

    private static final int GUI_WIDTH  = AllGuiTextures.STOCK_KEEPER_CATEGORY.getWidth();
    private static final int HEADER_H   = AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.getHeight();
    private static final int BODY_H     = AllGuiTextures.STOCK_KEEPER_CATEGORY.getHeight();
    private static final int FOOTER_H   = AllGuiTextures.STOCK_KEEPER_CATEGORY_FOOTER.getHeight();
    private static final int GUI_HEIGHT = HEADER_H + BODY_H * BODY_REPEAT + FOOTER_H;

    private static final int ENTRY_STEP  = 20;
    private static final int SCISSOR_X   = 3;
    private static final int SCISSOR_Y   = 16;
    private static final int SCISSOR_X2  = 187;
    private static final int SCISSOR_Y2  = 19 + BODY_H * BODY_REPEAT;
    // 条目列表在 GUI 内的起始 Y（相对 guiTop）
    private static final int ENTRY_START_Y = 24;
    private static final int DELETE_X    = 153;
    private static final int DELETE_W    = 16;
    private static final int DELETE_H    = 16;
    private static final int TEXT_COLOR  = 0x656565;

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

    private void syncToServer() {
        PacketDistributor.sendToServer(new SaveNetworkManagerDataPacket(hand, labels));
    }

    /**
     * 最大滚动量 = 条目列表总高度超出可视区的部分。
     *
     * 可视区高度   = SCISSOR_Y2 - SCISSOR_Y = (19 + BODY_H*BODY_REPEAT) - 16 = 3 + BODY_H*BODY_REPEAT
     * 列表顶部距可视区顶部偏移 = ENTRY_START_Y - SCISSOR_Y = 24 - 16 = 8
     * 列表内容总高 = 8 + labels.size() * ENTRY_STEP
     * 超出量       = 列表内容总高 - 可视区高度
     *             = 8 + labels.size()*ENTRY_STEP - (3 + BODY_H*BODY_REPEAT)
     *             = labels.size()*ENTRY_STEP + 5 - BODY_H*BODY_REPEAT
     */
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
        if (button == 2) {
            int sx  = guiLeft + SCISSOR_X;
            int sy  = guiTop  + SCISSOR_Y;
            int sx2 = guiLeft + SCISSOR_X2;
            int sy2 = guiTop  + SCISSOR_Y2;
            if (mouseX >= sx && mouseX < sx2 && mouseY >= sy && mouseY < sy2) {
                openEditor();
                return true;
            }
        }

        if (button == 0) {
            float scrollOff  = scroll.getValue(1.0f);
            int relX         = (int) mouseX - guiLeft - 7;
            int relYBase     = (int) mouseY - guiTop - ENTRY_START_Y + (int) scrollOff;

            for (int i = 0; i < labels.size(); i++) {
                int entryRelY = relYBase - i * ENTRY_STEP;
                if (entryRelY <= 0 || entryRelY > DELETE_H) continue;
                if (relX <= DELETE_X || relX > DELETE_X + DELETE_W) continue;
                labels.remove(i);
                clampScroll();
                syncToServer();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openEditor() {
        PacketDistributor.sendToServer(new OpenNetworkManagerEditorPacket(hand, labels));
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

        Component title  = Component.translatable("item.createimp.network_manager");
        int titleX       = guiLeft + GUI_WIDTH / 2 - font.width(title) / 2;
        int titleY       = guiTop  + (HEADER_H - font.lineHeight) / 2;
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

        // 悬停删除按钮 Tooltip
        float scrollOff2 = scroll.getValue(partialTicks);
        int relX         = mouseX - guiLeft - 7;
        int relYBase     = mouseY - guiTop  - ENTRY_START_Y + (int) scrollOff2;
        for (int i = 0; i < labels.size(); i++) {
            int entryRelY = relYBase - i * ENTRY_STEP;
            if (entryRelY <= 0 || entryRelY > DELETE_H) continue;
            if (relX <= DELETE_X || relX > DELETE_X + DELETE_W) continue;
            int entryScreenY = guiTop + ENTRY_START_Y + i * ENTRY_STEP - (int) scrollOff2;
            if (entryScreenY + ENTRY_STEP <= guiTop + SCISSOR_Y
                    || entryScreenY >= guiTop + SCISSOR_Y2) continue;
            graphics.renderComponentTooltip(font,
                    List.of(Component.translatable("create.gui.stock_ticker.delete_category")),
                    mouseX, mouseY);
            break;
        }
    }
}