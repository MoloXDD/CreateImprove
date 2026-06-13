package com.molox.createimp.screen;

import com.molox.createimp.CreateImp;
import com.molox.createimp.registry.ModItems;
import com.molox.createimp.block.brass_scrap_bucket.BrassScrapBucketBlockEntity;
import com.molox.createimp.network.OpenBrassScrapBucketGuiPacket;
import com.molox.createimp.network.SaveBrassScrapBucketConfigPacket;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class BrassScrapBucketScreen extends AbstractSimiScreen {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/brass_scrap_bucket.png");

    private static final int GUI_WIDTH = 182;
    private static final int GUI_HEIGHT = 79;

    private static final int CONFIRM_BUTTON_X = 149;
    private static final int CONFIRM_BUTTON_Y = 55;

    // 废料桶自身图标位置（相对于窗口左上角）
    private static final int SELF_ICON_X = 24;
    private static final int SELF_ICON_Y = 24;

    private static final int ATTACH_ICON_X = 13;
    private static final int ATTACH_ICON_Y = 56;

    private static final int TITLE_X = -1;
    private static final int TITLE_Y = 4;

    private static final int INPUTS_BG_X = 44;
    private static final int INPUTS_BG_Y = 21;

    private static final int VALUE_INPUT_X = 48;
    private static final int VALUE_INPUT_Y = 23;
    private static final int VALUE_INPUT_W = 48;
    private static final int VALUE_INPUT_H = 18;

    private static final int MEASURE_INPUT_X = 100;
    private static final int MEASURE_INPUT_Y = 23;
    private static final int MEASURE_INPUT_W = 52;
    private static final int MEASURE_INPUT_H = 18;

    private static final int VALUE_TEXT_X = 53;
    private static final int VALUE_TEXT_Y = 28;

    private static final int MEASURE_TEXT_X = 105;
    private static final int MEASURE_TEXT_Y = 28;

    private final BlockPos pos;
    private final int attachType;
    private final int maxItems;
    private final int maxStacks;
    private final int currentAmount;
    private final int currentStacks;

    private int currentKeepAmount;
    private boolean currentKeepInStacks;

    private IconButton confirmButton;
    private ScrollInput valueInput;
    private SelectionScrollInput measureInput;

    public BrassScrapBucketScreen(OpenBrassScrapBucketGuiPacket packet) {
        super(Component.empty());
        this.pos = packet.pos();
        this.attachType = packet.attachType();
        this.currentKeepAmount = packet.keepAmount();
        this.currentKeepInStacks = packet.keepInStacks();
        this.maxItems = packet.maxItems();
        this.maxStacks = packet.maxStacks();
        this.currentAmount = packet.currentAmount();
        this.currentStacks = packet.currentStacks();
    }

    public static void open(OpenBrassScrapBucketGuiPacket packet) {
        ScreenOpener.open(new BrassScrapBucketScreen(packet));
    }

    private int getItemsPerStack() {
        if (maxStacks <= 0) return 64;
        return Math.max(1, maxItems / maxStacks);
    }

    private int getMaxInCurrentUnit() {
        if (attachType == BrassScrapBucketBlockEntity.ATTACH_ITEM) {
            return currentKeepInStacks ? maxStacks : maxItems;
        }
        return maxItems;
    }

    private int toCurrentUnit(int amountInItems) {
        if (attachType == BrassScrapBucketBlockEntity.ATTACH_ITEM && currentKeepInStacks) {
            return amountInItems / getItemsPerStack();
        }
        return amountInItems;
    }

    private int toItems(int amountInCurrentUnit) {
        if (amountInCurrentUnit < 0) return -1;
        if (attachType == BrassScrapBucketBlockEntity.ATTACH_ITEM && currentKeepInStacks) {
            return amountInCurrentUnit * getItemsPerStack();
        }
        return amountInCurrentUnit;
    }

    private String getUnitString() {
        if (attachType == BrassScrapBucketBlockEntity.ATTACH_FLUID) {
            return Component.translatable("create.schedule.condition.threshold.buckets").getString();
        }
        if (currentKeepInStacks) {
            return Component.translatable("create.schedule.condition.threshold.stacks").getString();
        }
        return Component.translatable("create.schedule.condition.threshold.items").getString();
    }

    @Override
    protected void init() {
        setWindowSize(GUI_WIDTH, GUI_HEIGHT);
        super.init();

        if (attachType == BrassScrapBucketBlockEntity.ATTACH_ITEM) {
            measureInput = (SelectionScrollInput) new SelectionScrollInput(
                    guiLeft + MEASURE_INPUT_X, guiTop + MEASURE_INPUT_Y,
                    MEASURE_INPUT_W, MEASURE_INPUT_H)
                    .forOptions(List.of(
                            Component.translatable("create.schedule.condition.threshold.items"),
                            Component.translatable("create.schedule.condition.threshold.stacks")
                    ))
                    .titled(Component.translatable("create.schedule.condition.threshold.item_measure"))
                    .setState(currentKeepInStacks ? 1 : 0)
                    .calling(this::onMeasureChanged);
            boolean disabled = currentKeepAmount < 0;
            measureInput.active = !disabled;
            measureInput.visible = !disabled;
            addRenderableWidget(measureInput);

            int initMax = getMaxInCurrentUnit();
            int initValue = currentKeepAmount < 0 ? -1 : toCurrentUnit(currentKeepAmount);
            valueInput = new ScrollInput(
                    guiLeft + VALUE_INPUT_X, guiTop + VALUE_INPUT_Y,
                    VALUE_INPUT_W, VALUE_INPUT_H)
                    .withRange(-1, initMax + 1)
                    .titled(Component.translatable("create.gui.threshold_switch.upper_threshold"))
                    .calling(this::onValueChanged)
                    .withStepFunction(ctx -> ctx.shift ? 8 : 1)
                    .setState(Math.max(-1, Math.min(initValue, initMax)));
            addRenderableWidget(valueInput);

        } else if (attachType == BrassScrapBucketBlockEntity.ATTACH_FLUID) {
            int initValue = Math.max(-1, Math.min(currentKeepAmount, maxItems));
            valueInput = new ScrollInput(
                    guiLeft + VALUE_INPUT_X, guiTop + VALUE_INPUT_Y,
                    VALUE_INPUT_W, VALUE_INPUT_H)
                    .withRange(-1, maxItems + 1)
                    .titled(Component.translatable("create.gui.threshold_switch.upper_threshold"))
                    .calling(val -> currentKeepAmount = val)
                    .withStepFunction(ctx -> ctx.shift ? 8 : 1)
                    .setState(initValue);
            addRenderableWidget(valueInput);
        }

        confirmButton = new IconButton(
                guiLeft + CONFIRM_BUTTON_X,
                guiTop + CONFIRM_BUTTON_Y,
                18, 18,
                AllIcons.I_CONFIRM
        );
        confirmButton.withCallback(this::saveAndClose);
        addRenderableWidget(confirmButton);
    }

    private void onValueChanged(int val) {
        currentKeepAmount = toItems(val);
        if (measureInput != null) {
            boolean disabled = val < 0;
            measureInput.active = !disabled;
            measureInput.visible = !disabled;
        }
    }

    private void onMeasureChanged(int state) {
        if (valueInput == null) return;
        boolean wasInStacks = currentKeepInStacks;
        currentKeepInStacks = (state == 1);

        int oldDisplayValue = valueInput.getState();
        if (oldDisplayValue < 0) return;

        int amountInItems = wasInStacks ? oldDisplayValue * getItemsPerStack() : oldDisplayValue;
        int newDisplayValue = currentKeepInStacks ? amountInItems / getItemsPerStack() : amountInItems;

        int newMax = getMaxInCurrentUnit();
        newDisplayValue = Math.min(newDisplayValue, newMax);

        valueInput.withRange(-1, newMax + 1);
        valueInput.setState(newDisplayValue);
        currentKeepAmount = toItems(newDisplayValue);
    }

    private void saveAndClose() {
        PacketDistributor.sendToServer(
                new SaveBrassScrapBucketConfigPacket(pos, currentKeepAmount, currentKeepInStacks));
        onClose();
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.blit(TEXTURE, guiLeft, guiTop, 0, 0, GUI_WIDTH, GUI_HEIGHT, 256, 256);

        GuiGameElement.of(new ItemStack(ModItems.BRASS_SCRAP_BUCKET.get()))
                .at(guiLeft + SELF_ICON_X, guiTop + SELF_ICON_Y, 0)
                .render(graphics);

        Component titleComp = Component.translatable("block.createimp.brass_scrap_bucket");
        int titleX = TITLE_X < 0
                ? guiLeft + GUI_WIDTH / 2 - font.width(titleComp) / 2
                : guiLeft + TITLE_X;
        graphics.drawString(font, titleComp, titleX, guiTop + TITLE_Y, 0x592424, false);

        if (attachType == BrassScrapBucketBlockEntity.ATTACH_ITEM) {
            AllGuiTextures.THRESHOLD_SWITCH_ITEMCOUNT_INPUTS.render(
                    graphics, guiLeft + INPUTS_BG_X, guiTop + INPUTS_BG_Y);
        } else {
            AllGuiTextures.THRESHOLD_SWITCH_MISC_INPUTS.render(
                    graphics, guiLeft + INPUTS_BG_X, guiTop + INPUTS_BG_Y);
        }

        ItemStack displayItem = attachType == BrassScrapBucketBlockEntity.ATTACH_NONE
                ? new ItemStack(Items.BARRIER)
                : getAboveBlockItem();
        GuiGameElement.of(displayItem)
                .at(guiLeft + ATTACH_ICON_X, guiTop + ATTACH_ICON_Y, 0)
                .render(graphics);

        if (attachType != BrassScrapBucketBlockEntity.ATTACH_NONE && valueInput != null) {
            int displayValue = valueInput.getState();
            String leftText;
            if (displayValue < 0) {
                leftText = Component.translatable("createimp.gui.brass_scrap_bucket.disabled").getString();
            } else if (attachType == BrassScrapBucketBlockEntity.ATTACH_FLUID) {
                leftText = displayValue + " " + Component.translatable("create.schedule.condition.threshold.buckets").getString();
            } else {
                leftText = String.valueOf(displayValue);
            }
            graphics.drawString(font, Component.literal(leftText),
                    guiLeft + VALUE_TEXT_X, guiTop + VALUE_TEXT_Y, 0xFFFFFF, true);
        }

        if (attachType == BrassScrapBucketBlockEntity.ATTACH_NONE) {
            graphics.drawString(font,
                    Component.translatable("createimp.gui.brass_scrap_bucket.disabled"),
                    guiLeft + VALUE_TEXT_X, guiTop + VALUE_TEXT_Y, 0xFFFFFF, true);
        }

        if (attachType == BrassScrapBucketBlockEntity.ATTACH_ITEM
                && measureInput != null && measureInput.visible
                && valueInput != null && valueInput.getState() >= 0) {
            String measureText = measureInput.getState() == 1
                    ? Component.translatable("create.schedule.condition.threshold.stacks").getString()
                    : Component.translatable("create.schedule.condition.threshold.items").getString();
            graphics.drawString(font, Component.literal(measureText),
                    guiLeft + MEASURE_TEXT_X, guiTop + MEASURE_TEXT_Y, 0xFFFFFF, true);
        }

        if (attachType != BrassScrapBucketBlockEntity.ATTACH_NONE) {
            int iconScreenX = guiLeft + ATTACH_ICON_X;
            int iconScreenY = guiTop + ATTACH_ICON_Y;
            if (mouseX >= iconScreenX && mouseX < iconScreenX + 16
                    && mouseY >= iconScreenY && mouseY < iconScreenY + 16) {
                renderAttachTooltip(graphics, mouseX, mouseY);
            }
        }
    }

    private void renderAttachTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();

        lines.add(getAboveBlockItem().getHoverName());

        String unit = getUnitString();
        int displayCurrent = (attachType == BrassScrapBucketBlockEntity.ATTACH_ITEM && currentKeepInStacks)
                ? currentStacks
                : currentAmount;
        lines.add(Component.translatable("createimp.gui.brass_scrap_bucket.tooltip.current",
                        displayCurrent + " " + unit)
                .withStyle(ChatFormatting.GREEN));

        int maxInUnit = getMaxInCurrentUnit();
        lines.add(Component.translatable("createimp.gui.brass_scrap_bucket.tooltip.max",
                        maxInUnit + " " + unit)
                .withStyle(ChatFormatting.DARK_GRAY));

        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private ItemStack getAboveBlockItem() {
        if (minecraft == null || minecraft.level == null) return new ItemStack(Items.BARRIER);
        BlockPos above = pos.above();
        var state = minecraft.level.getBlockState(above);
        if (state.isAir()) return new ItemStack(Items.BARRIER);
        return state.getBlock().getCloneItemStack(minecraft.level, above, state);
    }
}