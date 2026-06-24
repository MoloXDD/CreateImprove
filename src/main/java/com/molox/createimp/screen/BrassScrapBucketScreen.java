package com.molox.createimp.screen;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.brass_scrap_bucket.BrassScrapBucketBlockEntity;
import com.molox.createimp.network.SaveBrassScrapBucketConfigPacket;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class BrassScrapBucketScreen extends AbstractSimiContainerScreen<BrassScrapBucketMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/brass_scrap_bucket.png");

    private static final int GUI_WIDTH = 182;
    private static final int GUI_TOP_HEIGHT = 79;

    private static final int PLAYER_INV_RENDER_X = -1;
    private static final int PLAYER_INV_RENDER_Y = 83;

    private static final int GUI_HEIGHT = GUI_TOP_HEIGHT + 14 + 76;

    private static final int CONFIRM_BUTTON_X = 149;
    private static final int CONFIRM_BUTTON_Y = 55;

    public static final int FILTER_ICON_X = 24;
    public static final int FILTER_ICON_Y = 24;

    private static final int ATTACH_ICON_X = 13;
    private static final int ATTACH_ICON_Y = 56;

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

    private int currentKeepAmount;
    private boolean currentKeepInStacks;

    private int currentAmount;
    private int currentStacks;

    private IconButton confirmButton;
    private ScrollInput valueInput;
    private SelectionScrollInput measureInput;

    public BrassScrapBucketScreen(BrassScrapBucketMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.pos = menu.pos;
        this.attachType = menu.attachType;
        this.currentKeepAmount = menu.keepAmount;
        this.currentKeepInStacks = menu.keepInStacks;
        this.maxItems = menu.maxItems;
        this.maxStacks = menu.maxStacks;
        this.currentAmount = menu.currentAmount;
        this.currentStacks = menu.currentStacks;
    }

    public int getGuiLeft() { return leftPos; }
    public int getGuiTop() { return topPos; }

    public void setFilterIcon(ItemStack stack) {
        menu.ghostInventory.setStackInSlot(0, stack.copy());
    }

    public void updateCurrentAmounts(int newAmount, int newStacks) {
        this.currentAmount = newAmount;
        this.currentStacks = newStacks;
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
                    leftPos + MEASURE_INPUT_X, topPos + MEASURE_INPUT_Y,
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
                    leftPos + VALUE_INPUT_X, topPos + VALUE_INPUT_Y,
                    VALUE_INPUT_W, VALUE_INPUT_H)
                    .withRange(-1, initMax + 1)
                    .titled(Component.translatable("create.gui.threshold_switch.upper_threshold"))
                    .calling(this::onValueChanged)
                    .withStepFunction(ctx -> ctx.shift ? 10 : 1)
                    .setState(Math.max(-1, Math.min(initValue, initMax)));
            addRenderableWidget(valueInput);

        } else if (attachType == BrassScrapBucketBlockEntity.ATTACH_FLUID) {
            int initValue = Math.max(-1, Math.min(currentKeepAmount, maxItems));
            valueInput = new ScrollInput(
                    leftPos + VALUE_INPUT_X, topPos + VALUE_INPUT_Y,
                    VALUE_INPUT_W, VALUE_INPUT_H)
                    .withRange(-1, maxItems + 1)
                    .titled(Component.translatable("create.gui.threshold_switch.upper_threshold"))
                    .calling(val -> currentKeepAmount = val)
                    .withStepFunction(ctx -> ctx.shift ? 10 : 1)
                    .setState(initValue);
            addRenderableWidget(valueInput);
        }

        confirmButton = new IconButton(
                leftPos + CONFIRM_BUTTON_X,
                topPos + CONFIRM_BUTTON_Y,
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

    private boolean isMouseOverFilterIconSlot(double mouseX, double mouseY) {
        int slotScreenX = leftPos + FILTER_ICON_X;
        int slotScreenY = topPos + FILTER_ICON_Y;
        return mouseX >= slotScreenX && mouseX < slotScreenX + 16
                && mouseY >= slotScreenY && mouseY < slotScreenY + 16;
    }

    private void saveAndClose() {
        PacketDistributor.sendToServer(
                new SaveBrassScrapBucketConfigPacket(pos, currentKeepAmount, currentKeepInStacks));
        onClose();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, GUI_WIDTH, GUI_TOP_HEIGHT, 256, 256);
        renderPlayerInventory(graphics, leftPos + PLAYER_INV_RENDER_X, topPos + PLAYER_INV_RENDER_Y);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        boolean onFilterSlot = isMouseOverFilterIconSlot(mouseX, mouseY);
        var savedSlot = hoveredSlot;
        if (onFilterSlot) hoveredSlot = null;

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);

        if (onFilterSlot) hoveredSlot = savedSlot;

        ItemStack filterIcon = menu.ghostInventory.getStackInSlot(0);
        if (!filterIcon.isEmpty()) {
            graphics.renderItem(filterIcon, leftPos + FILTER_ICON_X, topPos + FILTER_ICON_Y);
        }

        Component titleComp = Component.translatable("block.createimp.brass_scrap_bucket");
        int titleX = leftPos + GUI_WIDTH / 2 - font.width(titleComp) / 2;
        graphics.drawString(font, titleComp, titleX, topPos + TITLE_Y, 0x592424, false);

        if (attachType == BrassScrapBucketBlockEntity.ATTACH_ITEM) {
            AllGuiTextures.THRESHOLD_SWITCH_ITEMCOUNT_INPUTS.render(
                    graphics, leftPos + INPUTS_BG_X, topPos + INPUTS_BG_Y);
        } else {
            AllGuiTextures.THRESHOLD_SWITCH_MISC_INPUTS.render(
                    graphics, leftPos + INPUTS_BG_X, topPos + INPUTS_BG_Y);
        }

        ItemStack displayItem = attachType == BrassScrapBucketBlockEntity.ATTACH_NONE
                ? new ItemStack(Items.BARRIER)
                : getAboveBlockItem();
        graphics.renderItem(displayItem, leftPos + ATTACH_ICON_X, topPos + ATTACH_ICON_Y);

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
                    leftPos + VALUE_TEXT_X, topPos + VALUE_TEXT_Y, 0xFFFFFF, true);
        }

        if (attachType == BrassScrapBucketBlockEntity.ATTACH_NONE) {
            graphics.drawString(font,
                    Component.translatable("createimp.gui.brass_scrap_bucket.disabled"),
                    leftPos + VALUE_TEXT_X, topPos + VALUE_TEXT_Y, 0xFFFFFF, true);
        }

        if (attachType == BrassScrapBucketBlockEntity.ATTACH_ITEM
                && measureInput != null && measureInput.visible
                && valueInput != null && valueInput.getState() >= 0) {
            String measureText = measureInput.getState() == 1
                    ? Component.translatable("create.schedule.condition.threshold.stacks").getString()
                    : Component.translatable("create.schedule.condition.threshold.items").getString();
            graphics.drawString(font, Component.literal(measureText),
                    leftPos + MEASURE_TEXT_X, topPos + MEASURE_TEXT_Y, 0xFFFFFF, true);
        }

        if (attachType != BrassScrapBucketBlockEntity.ATTACH_NONE) {
            int iconScreenX = leftPos + ATTACH_ICON_X;
            int iconScreenY = topPos + ATTACH_ICON_Y;
            if (mouseX >= iconScreenX && mouseX < iconScreenX + 16
                    && mouseY >= iconScreenY && mouseY < iconScreenY + 16) {
                renderAttachTooltip(graphics, mouseX, mouseY);
            }
        }

        if (onFilterSlot) {
            renderFilterIconTooltip(graphics, mouseX, mouseY);
        }
    }

    private void renderFilterIconTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack filterIcon = menu.ghostInventory.getStackInSlot(0);
        if (filterIcon.isEmpty()) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("createimp.gui.brass_scrap_bucket.filter_icon.empty")
                            .withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        } else {
            List<Component> lines = new ArrayList<>();
            lines.add(filterIcon.getHoverName());
            lines.add(Component.translatable("createimp.gui.brass_scrap_bucket.filter_icon.click_to_clear")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .withStyle(ChatFormatting.ITALIC));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
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