package com.molox.createimp.screen;

import com.molox.createimp.block.labeled_redstone_link.LabeledRedstoneLinkBlockEntity;
import com.molox.createimp.gui.FrequencyEditBox;
import com.molox.createimp.network.OpenLabeledRedstoneLinkGuiPacket;
import com.molox.createimp.network.SaveLabeledRedstoneLinkConfigPacket;
import com.molox.createimp.registry.ModItems;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Consumer;

public class LabeledRedstoneLinkScreen extends AbstractSimiScreen {

    private static final int SUGGESTIONS_Y = -40;

    private static final int GUI_WIDTH  = AllGuiTextures.STOCK_KEEPER_CATEGORY.getWidth();
    private static final int HEADER_H   = AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.getHeight();
    private static final int EDIT_H     = AllGuiTextures.STOCK_KEEPER_CATEGORY_EDIT.getHeight();
    private static final int FOOTER_H   = AllGuiTextures.STOCK_KEEPER_CATEGORY_FOOTER.getHeight();
    private static final int GUI_HEIGHT = HEADER_H + EDIT_H + FOOTER_H;

    private static final int ICON_OFFSET_X = 16;
    private static final int ICON_OFFSET_Y = 29;

    private static final int EDITBOX_X = 47;
    private static final int EDITBOX_Y = 33;
    private static final int EDITBOX_W = 124;
    private static final int EDITBOX_H = 10;

    private static final int CONFIRM_X = 167;
    private static final int CONFIRM_Y = 64;

    private static final int TITLE_Y       = 4;
    private static final int EDITBOX_COLOR = 0xEEEEEE;
    private static final int TITLE_COLOR   = 0x3D3C48;

    private final String initialText;
    private final Consumer<String> onSave;
    private final Runnable onCloseAction;

    private FrequencyEditBox frequencyEditBox;
    private IconButton confirmButton;

    private LabeledRedstoneLinkScreen(String initialText, Consumer<String> onSave, Runnable onCloseAction) {
        super(CommonComponents.EMPTY);
        this.initialText = initialText;
        this.onSave = onSave;
        this.onCloseAction = onCloseAction;
    }

    /** 真实的标码无线红石信号终端方块：打开后直接把频率保存到对应方块实体。 */
    public static void open(OpenLabeledRedstoneLinkGuiPacket packet) {
        BlockPos pos = packet.pos();
        Minecraft.getInstance().setScreen(new LabeledRedstoneLinkScreen(
                packet.frequencyText(),
                text -> PacketDistributor.sendToServer(new SaveLabeledRedstoneLinkConfigPacket(pos, text)),
                null
        ));
    }

    /**
     * 路由器文本终端模块：不对应任何真实方块，保存目标由调用方通过 {@code onSave}
     * 回调决定；关闭这个界面（无论是确认键还是 Esc，两者都会走 {@code onClose()}）
     * 之后，通过 {@code returnToRouter} 决定回到哪个界面——通常是重新打开路由器界面。
     */
    public static void openForRouter(String initialText, Consumer<String> onSave, Runnable returnToRouter) {
        ScreenOpener.open(new LabeledRedstoneLinkScreen(initialText, onSave, returnToRouter));
    }

    @Override
    protected void init() {
        setWindowSize(GUI_WIDTH, GUI_HEIGHT);
        super.init();

        confirmButton = new IconButton(
                guiLeft + CONFIRM_X,
                guiTop + CONFIRM_Y,
                AllIcons.I_CONFIRM
        );
        confirmButton.withCallback(this::onConfirm);
        addRenderableWidget(confirmButton);

        frequencyEditBox = new FrequencyEditBox(
                this, font,
                guiLeft + EDITBOX_X,
                guiTop + EDITBOX_Y,
                EDITBOX_W, EDITBOX_H,
                initialText,
                guiTop + SUGGESTIONS_Y
        );
        frequencyEditBox.setTextColor(EDITBOX_COLOR);
        frequencyEditBox.setBordered(false);
        frequencyEditBox.setMaxLength(64);
        frequencyEditBox.setValue(initialText);
        frequencyEditBox.setOnDefocus(this::saveCurrentFrequency);
        addRenderableWidget(frequencyEditBox);
    }

    private void onConfirm() {
        if (frequencyEditBox.isFocused()) {
            frequencyEditBox.exitEditMode(currentDefaultFrequency());
        }
        saveCurrentFrequency();
        onClose();
    }

    private void saveCurrentFrequency() {
        String text = frequencyEditBox.getValue().isBlank()
                ? currentDefaultFrequency()
                : frequencyEditBox.getValue();
        onSave.accept(text);
    }

    /** 只在真正需要应用默认值的时刻（这里：编辑框为空、准备退出编辑/保存）检查一次客户端当前语言。 */
    private String currentDefaultFrequency() {
        String languageCode = Minecraft.getInstance().getLanguageManager().getSelected();
        return LabeledRedstoneLinkBlockEntity.defaultFrequencyFor(languageCode);
    }

    @Override
    public void onClose() {
        if (onCloseAction != null) {
            onCloseAction.run();
        } else {
            super.onClose();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (frequencyEditBox != null) frequencyEditBox.tick();
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int y = guiTop;
        AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.render(graphics, guiLeft, y);
        y += HEADER_H;
        AllGuiTextures.STOCK_KEEPER_CATEGORY_EDIT.render(graphics, guiLeft, y);
        y += EDIT_H;
        AllGuiTextures.STOCK_KEEPER_CATEGORY_FOOTER.render(graphics, guiLeft, y);

        var titleText = Component.translatable("createimp.gui.labeled_redstone_link.title")
                .getVisualOrderText();
        graphics.drawString(font, titleText,
                (int) (guiLeft + GUI_WIDTH / 2f - font.width(titleText) / 2f),
                guiTop + TITLE_Y,
                TITLE_COLOR, false);

        graphics.renderItem(
                ModItems.LABELED_REDSTONE_LINK.get().asItem().getDefaultInstance(),
                guiLeft + ICON_OFFSET_X,
                guiTop + ICON_OFFSET_Y
        );
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Esc：无论是否在编辑状态，都以当前文本退出窗口
        if (keyCode == 256) {
            if (frequencyEditBox.isFocused()) {
                frequencyEditBox.exitEditMode(currentDefaultFrequency());
            }
            onConfirm();
            return true;
        }

        // 编辑状态：其余按键由 addRenderableWidget 自动分发给 FrequencyEditBox
        if (frequencyEditBox.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        // 非编辑状态：Enter 退出窗口
        if (keyCode == 257 || keyCode == 335) {
            onConfirm();
            return true;
        }
        if (minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onConfirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 确认按钮
        if (button == 0 && confirmButton != null && confirmButton.isMouseOver(mouseX, mouseY)) {
            onConfirm();
            return true;
        }
        // 其余交给 super（会自动分发给所有 addRenderableWidget 的 widget，含 frequencyEditBox）
        return super.mouseClicked(mouseX, mouseY, button);
    }
}