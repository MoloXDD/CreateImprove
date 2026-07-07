package com.molox.createimp.screen;

import com.molox.createimp.network.OpenWorkWarehouseGuiPacket;
import com.molox.createimp.network.SaveWorkWarehouseAddressPacket;
import com.molox.createimp.registry.ModGuiTextures;
import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class WorkWarehouseScreen extends AbstractSimiScreen {

    // ==================== 以下坐标可手动调整控件位置（相对 GUI 左上角），不影响控件大小 ====================
    // 地址输入框：左上角坐标
    private static final int ADDRESS_BOX_X = 52;
    private static final int ADDRESS_BOX_Y = 28;
    // 确认按钮：左上角坐标
    private static final int CONFIRM_BUTTON_X = 199;
    private static final int CONFIRM_BUTTON_Y = 55;
    // 标题文字：纵坐标（横坐标始终居中，不提供调整）
    private static final int TITLE_Y = 4;
    // ======================================================================================

    private static final int ADDRESS_BOX_WIDTH = 110;
    private static final int ADDRESS_BOX_HEIGHT = 10;

    private static final int GUI_WIDTH = ModGuiTextures.WORK_WAREHOUSE_ADDRESS_INPUT.getWidth();
    private static final int GUI_HEIGHT = ModGuiTextures.WORK_WAREHOUSE_ADDRESS_INPUT.getHeight();

    private static final int TITLE_COLOR = 0x3D3C48;

    private final BlockPos pos;
    private final String initialAddress;

    private AddressEditBox addressBox;
    private IconButton confirmButton;

    private WorkWarehouseScreen(BlockPos pos, String initialAddress) {
        super(CommonComponents.EMPTY);
        this.pos = pos;
        this.initialAddress = initialAddress;
    }

    public static void open(OpenWorkWarehouseGuiPacket packet) {
        Minecraft.getInstance().setScreen(
                new WorkWarehouseScreen(packet.pos(), packet.addressText()));
    }

    @Override
    protected void init() {
        setWindowSize(GUI_WIDTH, GUI_HEIGHT);
        super.init();

        addressBox = new AddressEditBox(
                this, new NoShadowFontWrapper(font),
                guiLeft + ADDRESS_BOX_X,
                guiTop + ADDRESS_BOX_Y,
                ADDRESS_BOX_WIDTH, ADDRESS_BOX_HEIGHT,
                false
        );
        addressBox.setValue(initialAddress);
        addressBox.setTextColor(0x555555);
        addRenderableWidget(addressBox);

        confirmButton = new IconButton(
                guiLeft + CONFIRM_BUTTON_X,
                guiTop + CONFIRM_BUTTON_Y,
                AllIcons.I_CONFIRM
        );
        confirmButton.withCallback(this::onClose);
        addRenderableWidget(confirmButton);
    }

    @Override
    public void tick() {
        super.tick();
        if (addressBox != null) addressBox.tick();
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        ModGuiTextures.WORK_WAREHOUSE_ADDRESS_INPUT.render(graphics, guiLeft, guiTop);

        var titleText = Component.translatable("block.createimp.work_warehouse").getVisualOrderText();
        graphics.drawString(font, titleText,
                (int) (guiLeft + GUI_WIDTH / 2f - font.width(titleText) / 2f),
                guiTop + TITLE_Y,
                TITLE_COLOR, false);
    }

    @Override
    public void removed() {
        PacketDistributor.sendToServer(new SaveWorkWarehouseAddressPacket(pos, addressBox.getValue()));
        super.removed();
    }
}