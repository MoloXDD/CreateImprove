package com.molox.createimp.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.molox.createimp.item.NetworkLabel;
import com.molox.createimp.item.NetworkSelectedState;
import com.molox.createimp.network.OpenNetworkManagerGuiPacket;
import com.molox.createimp.network.SaveNetworkManagerDataPacket;
import com.molox.createimp.network.SaveNetworkManagerSearchPacket;
import com.molox.createimp.registry.ModDataComponents;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class NetworkManagerScreen extends AbstractSimiScreen {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("createimp", "textures/gui/network_manager_categories.png");
    private static final int TEXTURE_W = 256;
    private static final int TEXTURE_H = 256;

    // 以下坐标完全对应原版 stock_keeper_categories.png 中各元素的位置
    private static final int HEADER_SRC_X  = 32;  private static final int HEADER_SRC_Y  = 0;
    private static final int BODY_SRC_X    = 32;  private static final int BODY_SRC_Y    = 32;
    private static final int FOOTER_SRC_X  = 32;  private static final int FOOTER_SRC_Y  = 79;
    private static final int ENTRY_SRC_X   = 38;  private static final int ENTRY_SRC_Y   = 159;
    private static final int UP_SRC_X      = 211; private static final int UP_SRC_Y      = 160;
    private static final int DOWN_SRC_X    = 211; private static final int DOWN_SRC_Y    = 169;

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

    private static final int MOVE_UP_X    = 172;
    private static final int MOVE_UP_Y    = 2;
    private static final int MOVE_UP_W    = 8;
    private static final int MOVE_UP_H    = 8;

    private static final int MOVE_DOWN_X  = 172;
    private static final int MOVE_DOWN_Y  = 10;
    private static final int MOVE_DOWN_W  = 8;
    private static final int MOVE_DOWN_H  = 8;

    // 搜索框相对于 guiLeft/guiTop 的偏移和尺寸
    private static final int SEARCH_X     = 15;
    private static final int SEARCH_Y     = GUI_HEIGHT - FOOTER_H + 13;
    private static final int SEARCH_W     = 130;
    private static final int SEARCH_H     = 10;

    private static final int TEXT_COLOR          = 0x656565;
    private static final int SEARCH_TEXT_COLOR   = 0xEEEEEE;
    private static final int SEARCH_HINT_COLOR   = 0xCC444444;

    private final InteractionHand hand;
    private final List<NetworkLabel> labels;
    private final LerpedFloat scroll;
    private IconButton confirmButton;
    private EditBox searchBox;
    private String lastSyncedSearch = null;

    public NetworkManagerScreen(OpenNetworkManagerGuiPacket packet) {
        super(Component.translatable("item.createimp.network_manager"));
        this.hand   = packet.hand();
        this.labels = new ArrayList<>(packet.labels());
        this.scroll = LerpedFloat.linear().startWithValue(0);
    }

    public static void open(OpenNetworkManagerGuiPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ItemStack stack = mc.player.getItemInHand(packet.hand());
            if (stack.has(ModDataComponents.NETWORK_SELECTED_STATE.get())) {
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

        searchBox = new EditBox(
                font,
                guiLeft + SEARCH_X,
                guiTop + SEARCH_Y,
                SEARCH_W,
                SEARCH_H,
                Component.empty()
        );
        searchBox.setTextColor(SEARCH_TEXT_COLOR);
        searchBox.setBordered(false);
        searchBox.setMaxLength(64);
        searchBox.setFocused(false);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ItemStack stack = mc.player.getItemInHand(hand);
            String saved = stack.get(ModDataComponents.NETWORK_MANAGER_SEARCH.get());
            if (saved != null) {
                searchBox.setValue(saved);
            }
        }
        lastSyncedSearch = searchBox.getValue();

        addRenderableWidget(searchBox);
    }

    private List<NetworkLabel> getFilteredLabels() {
        String query = searchBox != null ? searchBox.getValue().trim() : "";
        if (query.isEmpty()) return labels;
        List<NetworkLabel> result = new ArrayList<>();
        for (NetworkLabel label : labels) {
            if (label.name().contains(query)) result.add(label);
        }
        return result;
    }

    @Override
    public void tick() {
        super.tick();
        scroll.tickChaser();
    }

    private int getMaxScroll() {
        return Math.max(0, getFilteredLabels().size() * ENTRY_STEP + 5 - BODY_H * BODY_REPEAT);
    }

    private void clampScroll() {
        float clamped = Mth.clamp(scroll.getChaseTarget(), 0, getMaxScroll());
        scroll.chase(clamped, 0.4, LerpedFloat.Chaser.EXP);
    }

    private void syncSearchToServer() {
        String current = searchBox.getValue();
        if (!current.equals(lastSyncedSearch)) {
            lastSyncedSearch = current;
            PacketDistributor.sendToServer(new SaveNetworkManagerSearchPacket(hand, current));
        }
    }

    @Override
    public void onClose() {
        syncSearchToServer();
        super.onClose();
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

    private void playClickSound() {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private void syncToServer() {
        PacketDistributor.sendToServer(
                new SaveNetworkManagerDataPacket(hand, labels, false));
    }

    private void openEditor(int editingIndex) {
        if (editingIndex < 0 || editingIndex >= labels.size()) return;
        NetworkLabel label = labels.get(editingIndex);
        syncSearchToServer();
        PacketDistributor.sendToServer(new com.molox.createimp.network.OpenNetworkManagerEditPacket(
                hand, labels, editingIndex, label.icon().copy(), label.name()));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (searchBox != null && searchBox.isFocused()) {
                searchBox.setFocused(false);
                setFocused(null);
                return true;
            }
            onClose();
            return true;
        }
        if ((keyCode == 257 || keyCode == 335) && searchBox != null && searchBox.isFocused()) {
            searchBox.setFocused(false);
            setFocused(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchBox != null && searchBox.isMouseOver(mouseX, mouseY)) {
            if (!searchBox.isFocused()) {
                searchBox.setFocused(true);
                setFocused(searchBox);
                if (!searchBox.getValue().isEmpty()) {
                    searchBox.setHighlightPos(0);
                    searchBox.moveCursorToEnd(false);
                }
                return true;
            }
        } else if (searchBox != null && searchBox.isFocused()) {
            searchBox.setFocused(false);
            setFocused(null);
        }

        if (button == 0 || button == 1) {
            float scrollOff = scroll.getValue(1.0f);
            int relX        = (int) mouseX - guiLeft - 7;
            int relYBase    = (int) mouseY - guiTop - ENTRY_START_Y + (int) scrollOff;

            List<NetworkLabel> filtered = getFilteredLabels();
            for (int i = 0; i < filtered.size(); i++) {
                int entryRelY = relYBase - i * ENTRY_STEP;
                if (entryRelY <= 0 || entryRelY > ENTRY_STEP) continue;

                // 条目的屏幕 Y 位置必须在裁剪区域内，否则不响应点击
                int entryScreenY = guiTop + ENTRY_START_Y + i * ENTRY_STEP - (int) scroll.getValue(1.0f);
                if (entryScreenY + ENTRY_STEP <= guiTop + SCISSOR_Y
                        || entryScreenY >= guiTop + SCISSOR_Y2) continue;

                NetworkLabel label = filtered.get(i);
                int realIndex = labels.indexOf(label);
                boolean isFiltering = !searchBox.getValue().trim().isEmpty();

                if (button == 0) {
                    if (relX > DELETE_X && relX <= DELETE_X + DELETE_W) {
                        labels.remove(realIndex);
                        clampScroll();
                        syncToServer();
                        playClickSound();
                        return true;
                    }

                    if (!isFiltering && relX >= MOVE_UP_X && relX < MOVE_UP_X + MOVE_UP_W
                            && entryRelY > MOVE_UP_Y && entryRelY <= MOVE_UP_Y + MOVE_UP_H
                            && realIndex > 0) {
                        NetworkLabel entry = labels.remove(realIndex);
                        labels.add(hasShiftDown() ? 0 : realIndex - 1, entry);
                        syncToServer();
                        playClickSound();
                        return true;
                    }

                    if (!isFiltering && relX >= MOVE_DOWN_X && relX < MOVE_DOWN_X + MOVE_DOWN_W
                            && entryRelY > MOVE_DOWN_Y && entryRelY <= MOVE_DOWN_Y + MOVE_DOWN_H
                            && realIndex < labels.size() - 1) {
                        NetworkLabel entry = labels.remove(realIndex);
                        labels.add(hasShiftDown() ? labels.size() : realIndex + 1, entry);
                        syncToServer();
                        playClickSound();
                        return true;
                    }

                    if (relX > 0 && relX <= DELETE_X) {
                        if (label.networkId().isPresent()) {
                            PacketDistributor.sendToServer(
                                    new com.molox.createimp.network.SetNetworkSelectionPacket(
                                            hand, label.name(), label.networkId().get()));
                            playClickSound();
                            onClose();
                            return true;
                        }
                    }
                }

                if (button == 1 && relX > 0 && relX <= DELETE_X) {
                    playClickSound();
                    openEditor(realIndex);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // 渲染背景
        int y = guiTop;
        graphics.blit(TEXTURE, guiLeft, y, HEADER_SRC_X, HEADER_SRC_Y,
                AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.getWidth(),
                AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.getHeight(),
                TEXTURE_W, TEXTURE_H);
        y += HEADER_H;
        for (int i = 0; i < BODY_REPEAT; i++) {
            graphics.blit(TEXTURE, guiLeft, y, BODY_SRC_X, BODY_SRC_Y,
                    AllGuiTextures.STOCK_KEEPER_CATEGORY.getWidth(),
                    AllGuiTextures.STOCK_KEEPER_CATEGORY.getHeight(),
                    TEXTURE_W, TEXTURE_H);
            y += BODY_H;
        }
        graphics.blit(TEXTURE, guiLeft, y, FOOTER_SRC_X, FOOTER_SRC_Y,
                AllGuiTextures.STOCK_KEEPER_CATEGORY_FOOTER.getWidth(),
                AllGuiTextures.STOCK_KEEPER_CATEGORY_FOOTER.getHeight(),
                TEXTURE_W, TEXTURE_H);

        // 搜索框未聚焦且无文字时显示提示文字
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            graphics.drawString(font,
                    Component.translatable("createimp.gui.network_manager.search_hint"),
                    guiLeft + SEARCH_X, guiTop + SEARCH_Y,
                    SEARCH_HINT_COLOR, false);
        }

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

        List<NetworkLabel> filtered = getFilteredLabels();
        boolean isFiltering = !searchBox.getValue().trim().isEmpty();
        for (int i = 0; i < filtered.size(); i++) {
            NetworkLabel label = filtered.get(i);
            int realIndex = labels.indexOf(label);
            int entryX = guiLeft + 7;
            int entryY = guiTop  + ENTRY_START_Y + i * ENTRY_STEP;

            graphics.blit(TEXTURE, entryX, entryY, ENTRY_SRC_X, ENTRY_SRC_Y,
                    AllGuiTextures.STOCK_KEEPER_CATEGORY_ENTRY.getWidth(),
                    AllGuiTextures.STOCK_KEEPER_CATEGORY_ENTRY.getHeight(),
                    TEXTURE_W, TEXTURE_H);

            if (!isFiltering && realIndex > 0) {
                graphics.blit(TEXTURE, entryX + MOVE_UP_X, entryY + MOVE_UP_Y,
                        UP_SRC_X, UP_SRC_Y, 8, 8, TEXTURE_W, TEXTURE_H);
            }
            if (!isFiltering && realIndex < labels.size() - 1) {
                graphics.blit(TEXTURE, entryX + MOVE_DOWN_X, entryY + MOVE_DOWN_Y,
                        DOWN_SRC_X, DOWN_SRC_Y, 8, 8, TEXTURE_W, TEXTURE_H);
            }

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
        for (int i = 0; i < filtered.size(); i++) {
            int entryRelY = relYBase - i * ENTRY_STEP;
            if (entryRelY <= 0 || entryRelY > ENTRY_STEP) continue;
            int entryScreenY = guiTop + ENTRY_START_Y + i * ENTRY_STEP - (int) scrollOff2;
            if (entryScreenY + ENTRY_STEP <= guiTop + SCISSOR_Y
                    || entryScreenY >= guiTop + SCISSOR_Y2) continue;

            NetworkLabel label = filtered.get(i);
            int realIndex = labels.indexOf(label);

            if (relX > DELETE_X && relX <= DELETE_X + DELETE_W) {
                graphics.renderComponentTooltip(font,
                        List.of(Component.translatable("create.gui.stock_ticker.delete_category")),
                        mouseX, mouseY);
                break;
            }

            if (!isFiltering && relX >= MOVE_UP_X && relX < MOVE_UP_X + MOVE_UP_W
                    && entryRelY > MOVE_UP_Y && entryRelY <= MOVE_UP_Y + MOVE_UP_H
                    && realIndex > 0) {
                graphics.renderComponentTooltip(font, List.of(
                        CreateLang.translateDirect("gui.schedule.move_up"),
                        CreateLang.translate("gui.stock_ticker.shift_moves_top")
                                .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component()
                ), mouseX, mouseY);
                break;
            }

            if (!isFiltering && relX >= MOVE_DOWN_X && relX < MOVE_DOWN_X + MOVE_DOWN_W
                    && entryRelY > MOVE_DOWN_Y && entryRelY <= MOVE_DOWN_Y + MOVE_DOWN_H
                    && realIndex < labels.size() - 1) {
                graphics.renderComponentTooltip(font, List.of(
                        CreateLang.translateDirect("gui.schedule.move_down"),
                        CreateLang.translate("gui.stock_ticker.shift_moves_bottom")
                                .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component()
                ), mouseX, mouseY);
                break;
            }

            if (relX > 0 && relX <= DELETE_X) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal(label.name()).withStyle(ChatFormatting.WHITE));
                if (label.networkId().isPresent()) {
                    tooltip.add(Component.translatable("createimp.gui.network_manager.lmb_select")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                }
                tooltip.add(Component.translatable("createimp.gui.network_manager.rmb_edit")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                break;
            }
        }
    }
}