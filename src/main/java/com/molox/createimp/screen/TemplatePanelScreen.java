package com.molox.createimp.screen;

import com.molox.createimp.block.template_panel.TemplatePanelBehaviour;
import com.molox.createimp.block.template_panel.TemplatePanelConnection;
import com.molox.createimp.block.template_panel.TemplatePanelConnectionHandler;
import com.molox.createimp.block.template_panel.TemplatePanelPosition;
import com.molox.createimp.compat.extragauges.ExtraGaugesCompat;
import com.molox.createimp.compat.fluidlogistics.FluidLogisticsCompat;
import com.molox.createimp.compat.fluidlogistics.TemplateFluidDisplayHelper;
import com.molox.createimp.network.TemplatePanelConfigurationPacket;
import com.molox.createimp.registry.ModItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.molox.createimp.registry.ModGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TemplatePanelScreen extends AbstractSimiScreen {

    private static final net.minecraft.resources.ResourceLocation DEMAND_MODE_ICON =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    com.molox.createimp.CreateImp.MODID, "textures/gui/demand_request_button.png");
    /**
     * 动力合成表过大无法逐格渲染时使用的遮盖贴图，直接复用额外仪表自带的
     * 同一张贴图（其资源包内已经包含这张图，不需要我们自己再打包一份）。
     * 这个字段只会在 {@link #tooLargeToRender} 判定为 true 时才会被用于绘制，
     * 而 {@code availableMechanicalRecipe} 只有在 {@link ExtraGaugesCompat#isLoaded()}
     * 为真时才可能非空，因此这里引用到的贴图资源在被绘制的那一刻必定真实
     * 存在，不会出现资源缺失。
     */
    private static final net.minecraft.resources.ResourceLocation LARGE_RECIPE_PLACEHOLDER_TEXTURE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    ExtraGaugesCompat.MOD_ID, "textures/gui/auto_crafting_gauge.png");

    private AddressEditBox addressBox;
    private IconButton confirmButton;
    private IconButton deleteButton;
    private IconButton newInputButton;
    private IconButton relocateButton;
    private IconButton activateCraftingButton;
    private IconButton demandModeButton;
    private TemplatePanelBehaviour behaviour;
    private boolean sendReset;
    private BigItemStack outputConfig;
    private List<BigItemStack> inputConfig;
    private List<TemplatePanelConnection> connections;
    private CraftingRecipe availableCraftingRecipe;
    /**
     * 额外仪表（Extra Gauges）兼容：识别到的动力合成表，仅在安装了额外仪表
     * 时才会被搜索、被赋值，未安装时恒为 null，行为与本次改动之前完全一致。
     * 只有 {@link #availableCraftingRecipe} 找不到匹配的普通合成表时，才会
     * 尝试搜索这个字段，两者不会同时非空。
     */
    private MechanicalCraftingRecipe availableMechanicalRecipe;
    private boolean craftingActive;
    private List<BigItemStack> craftingIngredients;
    /**
     * 动力合成模式下，渲染材料图标网格时使用的每行列数：普通合成表固定 3 列
     * （沿用原有的补齐到 3 的倍数逻辑）；动力合成表按补齐后的正方形边长 n
     * 渲染（见 {@link #convertMechanicalRecipeToPackageOrderContext}），避免
     * 非正方形配方（比如 1×3、4×1）在固定 3 列的网格里显示错位。只影响
     * 客户端展示，不影响实际发往工作仓库的合成表数据。
     */
    private int craftingColumns = 3;

    public TemplatePanelScreen(TemplatePanelBehaviour behaviour) {
        this.behaviour = behaviour;
        this.minecraft = Minecraft.getInstance();
        this.availableCraftingRecipe = null;
        this.availableMechanicalRecipe = null;
        this.craftingActive = !behaviour.activeCraftingArrangement.isEmpty();
        this.updateConfigs();
    }

    private void updateConfigs() {
        this.connections = new ArrayList<>(this.behaviour.targetedBy.values());
        this.outputConfig = new BigItemStack(this.behaviour.getFilter(), this.behaviour.recipeOutput);
        this.inputConfig = this.connections.stream().map(c -> {
            ItemStack filter = TemplatePanelBehaviour.getExternalFilter(this.minecraft.level, c.from.pos(), c.from.slot());
            return new BigItemStack(filter, c.amount);
        }).toList();
        this.searchForCraftingRecipe();
        if (this.availableCraftingRecipe != null) {
            this.craftingColumns = 3;
            this.craftingIngredients = convertRecipeToPackageOrderContext(this.availableCraftingRecipe, this.inputConfig);
            return;
        }
        if (this.availableMechanicalRecipe != null) {
            this.craftingColumns = Math.max(1, Math.max(
                    this.availableMechanicalRecipe.getWidth(), this.availableMechanicalRecipe.getHeight()));
            this.craftingIngredients = convertMechanicalRecipeToPackageOrderContext(this.availableMechanicalRecipe, this.inputConfig);
            return;
        }
        this.craftingActive = false;
    }

    /**
     * 把一个动力合成表转换成材料列表：按左上角对齐，补齐成一个 n×n 的正方形
     * （n = max(配方宽度, 配方高度)），空位一律填充空气占位。
     * <p>
     * 这里补齐成正方形是与批量动力合成器一侧（{@code BatchCrafterUnpackingHandler}）
     * 的约定配套的：那一侧只能拿到这份摊平的列表本身，没有单独的"宽度"字段
     * 可看，只能靠列表长度是不是完全平方数反推边长；如果这里不补齐、直接
     * 按配方原始宽×高的长度传出去，配方本身不是正方形时那一侧根本无法正确
     * 反推出边长，会按错误的边长换算行列号，导致材料摆放位置错乱。
     * 补齐之后，配方原始内容固定摆在这个正方形的左上角，物理合成器链条
     * 只要边长不小于这个 n 就能正确对应上，允许链条比配方本身更大。
     */
    public static List<BigItemStack> convertMechanicalRecipeToPackageOrderContext(
            MechanicalCraftingRecipe recipe, List<BigItemStack> inputs) {
        int width = recipe.getWidth();
        int height = recipe.getHeight();
        int n = Math.max(width, height);
        BigItemStack emptyIngredient = new BigItemStack(ItemStack.EMPTY, 1);
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        List<BigItemStack> mutableInputs = BigItemStack.duplicateWrappers(inputs);

        BigItemStack[] square = new BigItemStack[n * n];
        java.util.Arrays.fill(square, emptyIngredient);
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                Ingredient ingredient = ingredients.get(row * width + col);
                BigItemStack craftingIngredient = emptyIngredient;
                if (!ingredient.isEmpty()) {
                    for (BigItemStack bigItemStack : mutableInputs) {
                        if (bigItemStack.count <= 0 || !ingredient.test(bigItemStack.stack)) continue;
                        craftingIngredient = new BigItemStack(bigItemStack.stack, 1);
                        break;
                    }
                }
                square[row * n + col] = craftingIngredient;
            }
        }
        return new ArrayList<>(java.util.Arrays.asList(square));
    }

    public static List<BigItemStack> convertRecipeToPackageOrderContext(CraftingRecipe availableCraftingRecipe, List<BigItemStack> inputs) {
        ArrayList<BigItemStack> craftingIngredients = new ArrayList<>();
        BigItemStack emptyIngredient = new BigItemStack(ItemStack.EMPTY, 1);
        NonNullList<Ingredient> ingredients = availableCraftingRecipe.getIngredients();
        List<BigItemStack> mutableInputs = BigItemStack.duplicateWrappers(inputs);
        int width = Math.min(3, ingredients.size());
        int height = Math.min(3, ingredients.size() / 3 + 1);
        if (availableCraftingRecipe instanceof ShapedRecipe shaped) {
            width = shaped.getWidth();
            height = shaped.getHeight();
        }
        if (height == 1) {
            for (int i = 0; i < 3; ++i) {
                craftingIngredients.add(emptyIngredient);
            }
        }
        if (width == 1) {
            craftingIngredients.add(emptyIngredient);
        }
        for (int i = 0; i < ingredients.size(); ++i) {
            Ingredient ingredient = ingredients.get(i);
            BigItemStack craftingIngredient = emptyIngredient;
            if (!ingredient.isEmpty()) {
                for (BigItemStack bigItemStack : mutableInputs) {
                    if (bigItemStack.count <= 0 || !ingredient.test(bigItemStack.stack)) continue;
                    craftingIngredient = new BigItemStack(bigItemStack.stack, 1);
                    break;
                }
            }
            craftingIngredients.add(craftingIngredient);
            if (width >= 3 || (i + 1) % width != 0) continue;
            for (int j = 0; j < 3 - width; ++j) {
                if (craftingIngredients.size() >= 9) continue;
                craftingIngredients.add(emptyIngredient);
            }
        }
        while (craftingIngredients.size() < 9) {
            craftingIngredients.add(emptyIngredient);
        }
        return craftingIngredients;
    }

    @Override
    protected void init() {
        int sizeX = ModGuiTextures.TEMPLATE_PANEL_BOTTOM.getWidth();
        int sizeY = ModGuiTextures.TEMPLATE_PANEL_RECIPE.getHeight() + ModGuiTextures.TEMPLATE_PANEL_BOTTOM.getHeight();
        this.setWindowSize(sizeX, sizeY);
        super.init();
        this.clearWidgets();
        int x = this.guiLeft;
        int y = this.guiTop;

        if (this.addressBox == null) {
            this.addressBox = new AddressEditBox(this, new NoShadowFontWrapper(this.font), x + 36, y + this.windowHeight - 51, 108, 10, false, null);
            this.addressBox.setValue(this.behaviour.recipeAddress);
            this.addressBox.setTextColor(0x555555);
        }
        this.addressBox.setX(x + 36);
        this.addressBox.setY(y + this.windowHeight - 51);
        this.addRenderableWidget(this.addressBox);

        this.confirmButton = new IconButton(x + sizeX - 33, y + sizeY - 25, AllIcons.I_CONFIRM);
        this.confirmButton.withCallback(() -> this.minecraft.setScreen(null));
        this.confirmButton.setToolTip(CreateLang.translate("gui.factory_panel.save_and_close").component());
        this.addRenderableWidget(this.confirmButton);

        this.deleteButton = new IconButton(x + sizeX - 55, y + sizeY - 25, AllIcons.I_TRASH);
        this.deleteButton.withCallback(() -> {
            this.sendReset = true;
            this.minecraft.setScreen(null);
        });
        this.deleteButton.setToolTip(CreateLang.translate("gui.factory_panel.reset").component());
        this.addRenderableWidget(this.deleteButton);

        this.newInputButton = new IconButton(x + 31, y + 47, AllIcons.I_ADD);
        this.newInputButton.withCallback(() -> {
            TemplatePanelConnectionHandler.startConnection(this.behaviour);
            this.minecraft.setScreen(null);
        });
        this.newInputButton.setToolTip(CreateLang.translate("gui.factory_panel.connect_input").component());
        this.addRenderableWidget(this.newInputButton);

        this.relocateButton = new IconButton(x + 31, y + 67, AllIcons.I_MOVE_GAUGE);
        this.relocateButton.withCallback(() -> {
            TemplatePanelConnectionHandler.startRelocating(this.behaviour);
            this.minecraft.setScreen(null);
        });
        this.relocateButton.setToolTip(CreateLang.translate("gui.factory_panel.relocate").component());
        this.addRenderableWidget(this.relocateButton);

        this.activateCraftingButton = null;
        CraftingRecipe recipeForActivation = this.availableCraftingRecipe != null
                ? this.availableCraftingRecipe : this.availableMechanicalRecipe;
        if (recipeForActivation != null) {
            CraftingRecipe finalRecipeForActivation = recipeForActivation;
            this.activateCraftingButton = new IconButton(x + 31, y + 27, AllIcons.I_3x3);
            this.activateCraftingButton.withCallback(() -> {
                this.craftingActive = !this.craftingActive;
                this.init();
                if (this.craftingActive) {
                    this.outputConfig.count = finalRecipeForActivation.getResultItem((HolderLookup.Provider) this.minecraft.level.registryAccess()).getCount();
                }
            });
            this.activateCraftingButton.setToolTip(CreateLang.translate("gui.factory_panel.activate_crafting").component());
            this.addRenderableWidget(this.activateCraftingButton);
        }

        this.demandModeButton = null;
        if (!this.behaviour.targetedBy.isEmpty()
                && com.molox.createimp.CreateImp.getConfig().functionConfig.featureToggles.templateDemandModeEnabled) {
            net.createmod.catnip.gui.element.ScreenElement demandIcon = (graphics, ix, iy) ->
                    graphics.blit(DEMAND_MODE_ICON, ix, iy, 0, 0, 16, 16, 16, 16);
            this.demandModeButton = new IconButton(x + 159, y + 67, demandIcon);
            this.demandModeButton.green = this.behaviour.demandMode;
            this.demandModeButton.withCallback(() -> {
                boolean newState = !this.behaviour.demandMode;
                this.behaviour.demandMode = newState;
                this.demandModeButton.green = newState;
                CatnipServices.NETWORK.sendToServer((CustomPacketPayload) new com.molox.createimp.network.SaveTemplatePanelDemandModePacket(
                        this.behaviour.getPanelPosition(), newState));
            });
            this.demandModeButton.setToolTip(Component.translatable("createimp.gui.factory_panel.demand_mode.title"));
            this.demandModeButton.getToolTip().add(
                    Component.translatable("createimp.gui.factory_panel.demand_mode.desc").withStyle(ChatFormatting.GRAY));
            this.addRenderableWidget(this.demandModeButton);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.inputConfig.size() != this.behaviour.targetedBy.size()) {
            this.updateConfigs();
            this.init();
        }
        if (this.activateCraftingButton != null) {
            this.activateCraftingButton.green = this.craftingActive;
        }
        if (this.demandModeButton != null) {
            boolean shouldShow = !this.behaviour.targetedBy.isEmpty()
                    && com.molox.createimp.CreateImp.getConfig().functionConfig.featureToggles.templateDemandModeEnabled;
            this.demandModeButton.visible = shouldShow;
            this.demandModeButton.active = shouldShow;
            if (shouldShow) {
                this.demandModeButton.green = this.behaviour.demandMode;
            }
        }
        this.addressBox.tick();
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = this.guiLeft;
        int y = this.guiTop;
        ModGuiTextures.TEMPLATE_PANEL_RECIPE.render(graphics, x, y);
        ModGuiTextures.TEMPLATE_PANEL_BOTTOM.render(graphics, x, y + ModGuiTextures.TEMPLATE_PANEL_RECIPE.getHeight());

        int slot = 0;
        if (this.craftingActive) {
            boolean tooLargeToRender = this.availableMechanicalRecipe != null
                    && Math.max(this.availableMechanicalRecipe.getWidth(), this.availableMechanicalRecipe.getHeight()) > 3;
            if (tooLargeToRender) {
                graphics.blit(LARGE_RECIPE_PLACEHOLDER_TEXTURE, x + 56, y + 23, 0, 0, 79, 72);
                this.showLargeRecipeTooltip(graphics, mouseX, mouseY);
            } else {
                for (BigItemStack itemStack : this.craftingIngredients) {
                    this.renderInputItem(graphics, slot++, itemStack, mouseX, mouseY);
                }
            }
        } else {
            for (BigItemStack itemStack : this.inputConfig) {
                this.renderInputItem(graphics, slot++, itemStack, mouseX, mouseY);
            }
            if (this.inputConfig.isEmpty()) {
                int inputX = this.guiLeft + 68 + slot % 3 * 20;
                int inputY = this.guiTop + 28 + slot / 3 * 20;
                if (mouseY > inputY && mouseY < inputY + 60 && mouseX > inputX && mouseX < inputX + 60) {
                    graphics.renderComponentTooltip(this.font, List.of(
                            CreateLang.translate("gui.factory_panel.unconfigured_input").component(),
                            CreateLang.translate("gui.factory_panel.unconfigured_input_tip").style(ChatFormatting.GRAY).component(),
                            CreateLang.translate("gui.factory_panel.unconfigured_input_tip_1").style(ChatFormatting.GRAY).component()
                    ), mouseX, mouseY);
                }
            }
        }

        int outputX = x + 160;
        int outputY = y + 48;
        graphics.renderItem(this.outputConfig.stack, outputX, outputY);
        graphics.renderItemDecorations(this.font, this.behaviour.getFilter(), outputX, outputY,
                createimp$formatAmount(this.outputConfig.stack, this.outputConfig.count));
        if (mouseX >= outputX - 1 && mouseX < outputX - 1 + 18 && mouseY >= outputY - 1 && mouseY < outputY - 1 + 18) {
            MutableComponent c1 = CreateLang.translate("gui.factory_panel.expected_output",
                    CreateLang.itemName(this.outputConfig.stack).add(CreateLang.text(" x" + createimp$formatAmount(this.outputConfig.stack, this.outputConfig.count))).string()).component();
            MutableComponent c2 = CreateLang.translate("gui.factory_panel.expected_output_tip").style(ChatFormatting.GRAY).component();
            MutableComponent c3 = CreateLang.translate("gui.factory_panel.expected_output_tip_1").style(ChatFormatting.GRAY).component();
            graphics.renderComponentTooltip(this.font, List.of(c1, c2, c3), mouseX, mouseY);
        }

        if (this.addressBox.isHovered() && !this.addressBox.isFocused()) {
            this.showAddressBoxTooltip(graphics, mouseX, mouseY);
        }

        MutableComponent title = CreateLang.translate("gui.factory_panel.title_as_recipe").component();
        graphics.drawString(this.font, (Component) title, x + 97 - this.font.width((FormattedText) title) / 2, y + 4, 4013128, false);

        GuiGameElement.of(new ItemStack(ModItems.TEMPLATE_PANEL.get())).scale(4.0).at(0.0f, 0.0f, -200.0f).render(graphics, x + 195, y + 55);
        if (!this.behaviour.getFilter().isEmpty()) {
            GuiGameElement.of(this.behaviour.getFilter()).scale(1.625).at(0.0f, 0.0f, 100.0f).render(graphics, x + 214, y + 68);
        }
    }

    private void renderInputItem(GuiGraphics graphics, int slot, BigItemStack itemStack, int mouseX, int mouseY) {
        int columns = this.craftingActive ? this.craftingColumns : 3;
        int inputX = this.guiLeft + 68 + slot % columns * 20;
        int inputY = this.guiTop + 28 + slot / columns * 20;
        graphics.renderItem(itemStack.stack, inputX, inputY);
        if (!this.craftingActive && !itemStack.stack.isEmpty()) {
            graphics.renderItemDecorations(this.font, itemStack.stack, inputX, inputY,
                    createimp$formatAmount(itemStack.stack, itemStack.count));
        }
        if (mouseX < inputX - 2 || mouseX >= inputX - 2 + 20 || mouseY < inputY - 2 || mouseY >= inputY - 2 + 20) {
            return;
        }
        if (this.craftingActive) {
            graphics.renderComponentTooltip(this.font, List.of(
                    CreateLang.translate("gui.factory_panel.crafting_input").component(),
                    CreateLang.translate("gui.factory_panel.crafting_input_tip").style(ChatFormatting.GRAY).component(),
                    CreateLang.translate("gui.factory_panel.crafting_input_tip_1").style(ChatFormatting.GRAY).component()
            ), mouseX, mouseY);
            return;
        }
        if (itemStack.stack.isEmpty()) {
            graphics.renderComponentTooltip(this.font, List.of(
                    CreateLang.translate("gui.factory_panel.empty_panel").component(),
                    CreateLang.translate("gui.factory_panel.left_click_disconnect").style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component()
            ), mouseX, mouseY);
            return;
        }
        graphics.renderComponentTooltip(this.font, List.of(
                CreateLang.translate("gui.factory_panel.sending_item", CreateLang.itemName(itemStack.stack).add(CreateLang.text(" x" + createimp$formatAmount(itemStack.stack, itemStack.count))).string()).component(),
                CreateLang.translate("gui.factory_panel.scroll_to_change_amount").style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component(),
                CreateLang.translate("gui.factory_panel.left_click_disconnect").style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component()
        ), mouseX, mouseY);
    }

    /**
     * 动力合成表宽或高超过 3（额外仪表兼容功能）时，材料展示区域不再逐格
     * 渲染每一种材料的图标，鼠标悬停在这块区域上时改为显示这条提示。
     * 只影响这一块区域的展示，配方实际参与生产的数据不受影响。
     */
    private void showLargeRecipeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int areaX = this.guiLeft + 68 - 2;
        int areaY = this.guiTop + 28 - 2;
        if (mouseX < areaX || mouseX >= areaX + 62 || mouseY < areaY || mouseY >= areaY + 62) {
            return;
        }
        graphics.renderComponentTooltip(this.font, List.of(
                Component.translatable("createimp.gui.factory_panel.large_recipe_no_render").withStyle(ChatFormatting.GRAY),
                Component.translatable("createimp.gui.factory_panel.large_recipe_no_render_tip").withStyle(ChatFormatting.DARK_GRAY)
        ), mouseX, mouseY);
    }

    private void showAddressBoxTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.addressBox.getValue().isBlank()) {
            graphics.renderComponentTooltip(this.font, List.of(
                    CreateLang.translate("gui.factory_panel.recipe_address").component(),
                    CreateLang.translate("gui.factory_panel.recipe_address_tip").style(ChatFormatting.GRAY).component(),
                    CreateLang.translate("gui.factory_panel.recipe_address_tip_1").style(ChatFormatting.GRAY).component(),
                    CreateLang.translate("gui.schedule.lmb_edit").style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component()
            ), mouseX, mouseY);
        } else {
            graphics.renderComponentTooltip(this.font, List.of(
                    CreateLang.translate("gui.factory_panel.recipe_address_given").component(),
                    CreateLang.text("'" + this.addressBox.getValue() + "'").style(ChatFormatting.GRAY).component()
            ), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int pButton) {
        if (this.getFocused() != null && !this.getFocused().isMouseOver(mouseX, mouseY)) {
            this.setFocused(null);
        }
        int x = this.guiLeft;
        int y = this.guiTop;
        if (!this.craftingActive) {
            for (int i = 0; i < this.connections.size(); ++i) {
                int inputX = x + 68 + i % 3 * 20;
                int inputY = y + 28 + i / 3 * 20;
                if (mouseX >= inputX && mouseX < inputX + 16 && mouseY >= inputY && mouseY < inputY + 16) {
                    this.sendIt(this.connections.get(i).from, false);
                    this.playButtonSound();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, pButton);
    }

    public void playButtonSound() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f));
    }

    /**
     * 判断一个连接/产出的过滤物是不是流体包裹的虚拟流体过滤物，只有这样才
     * 需要把数量按流体单位（mB/B/KB）展示和调整，其余情况（普通物品）行为
     * 完全不变。
     */
    private static boolean createimp$isFluidStack(ItemStack stack) {
        return FluidLogisticsCompat.isLoaded() && TemplateFluidDisplayHelper.isVirtualFluidDisplay(stack);
    }

    private static String createimp$formatAmount(ItemStack stack, int count) {
        if (createimp$isFluidStack(stack)) {
            return TemplateFluidDisplayHelper.formatStorageAmount(count);
        }
        return "" + count;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = this.guiLeft;
        int y = this.guiTop;
        if (this.addressBox.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (this.craftingActive) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        for (int i = 0; i < this.inputConfig.size(); ++i) {
            int inputX = x + 68 + i % 3 * 20;
            int inputY = y + 26 + i / 3 * 20;
            if (mouseX >= inputX && mouseX < inputX + 16 && mouseY >= inputY && mouseY < inputY + 16) {
                BigItemStack itemStack = this.inputConfig.get(i);
                if (itemStack.stack.isEmpty()) {
                    return true;
                }
                if (createimp$isFluidStack(itemStack.stack)) {
                    itemStack.count = TemplateFluidDisplayHelper.adjustFluidAmount(itemStack.count,
                            scrollY > 0, hasShiftDown(), hasControlDown(), 1, TemplateFluidDisplayHelper.maxFluidAmount());
                } else {
                    itemStack.count = Mth.clamp((int) (itemStack.count + Math.signum(scrollY) * (hasShiftDown() ? 10 : 1)), 1, 64);
                }
                return true;
            }
        }
        int outputX = x + 160;
        int outputY = y + 48;
        if (mouseX >= outputX && mouseX < outputX + 16 && mouseY >= outputY && mouseY < outputY + 16) {
            if (createimp$isFluidStack(this.outputConfig.stack)) {
                this.outputConfig.count = TemplateFluidDisplayHelper.adjustFluidAmount(this.outputConfig.count,
                        scrollY > 0, hasShiftDown(), hasControlDown(), 1, TemplateFluidDisplayHelper.maxFluidAmount());
            } else {
                this.outputConfig.count = Mth.clamp((int) (this.outputConfig.count + Math.signum(scrollY) * (hasShiftDown() ? 10 : 1)), 1, 64);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void removed() {
        this.sendIt(null, false);
        super.removed();
    }

    private void sendIt(TemplatePanelPosition toRemove, boolean unused) {
        HashMap<TemplatePanelPosition, Integer> inputs = new HashMap<>();
        if (this.inputConfig.size() == this.connections.size()) {
            for (int i = 0; i < this.inputConfig.size(); ++i) {
                BigItemStack stackInConfig = this.inputConfig.get(i);
                int amount = this.craftingActive
                        ? (int) this.craftingIngredients.stream().filter(b -> !b.stack.isEmpty() && ItemStack.isSameItemSameComponents(b.stack, stackInConfig.stack)).count()
                        : stackInConfig.count;
                inputs.put(this.connections.get(i).from, amount);
            }
        }
        List<ItemStack> craftingArrangement = this.craftingActive ? this.craftingIngredients.stream().map(b -> b.stack).toList() : List.of();
        TemplatePanelPosition pos = this.behaviour.getPanelPosition();
        String address = this.addressBox.getValue();
        TemplatePanelConfigurationPacket packet = new TemplatePanelConfigurationPacket(
                pos, address, inputs, craftingArrangement, this.outputConfig.count, toRemove, this.sendReset);
        CatnipServices.NETWORK.sendToServer((CustomPacketPayload) packet);
    }

    private void searchForCraftingRecipe() {
        this.availableCraftingRecipe = null;
        this.availableMechanicalRecipe = null;
        ItemStack output = this.outputConfig.stack;
        if (output.isEmpty()) {
            return;
        }
        if (this.behaviour.targetedBy.isEmpty()) {
            return;
        }
        Set<Item> itemsToUse = this.inputConfig.stream().map(b -> b.stack).filter(i -> !i.isEmpty()).map(ItemStack::getItem).collect(Collectors.toSet());
        ClientLevel level = Minecraft.getInstance().level;
        this.availableCraftingRecipe = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).parallelStream()
                .filter(r -> output.getItem() == r.value().getResultItem((HolderLookup.Provider) level.registryAccess()).getItem())
                .filter(r -> {
                    if (AllRecipeTypes.shouldIgnoreInAutomation(r)) {
                        return false;
                    }
                    HashSet<Item> itemsUsed = new HashSet<>();
                    for (Ingredient ingredient : r.value().getIngredients()) {
                        if (ingredient.isEmpty()) continue;
                        boolean available = false;
                        for (BigItemStack bis : this.inputConfig) {
                            if (bis.stack.isEmpty() || !ingredient.test(bis.stack)) continue;
                            available = true;
                            itemsUsed.add(bis.stack.getItem());
                            break;
                        }
                        if (!available) {
                            return false;
                        }
                    }
                    return itemsUsed.size() >= itemsToUse.size();
                }).findAny().map(RecipeHolder::value).orElse(null);
        if (this.availableCraftingRecipe != null) {
            return;
        }
        // 额外仪表兼容：只有在没有匹配到任何普通合成表、且额外仪表确实已安装时，
        // 才尝试搜索动力合成表；未安装额外仪表时这段代码完全不会执行，
        // availableMechanicalRecipe 恒为 null，行为与本次改动之前完全一致。
        if (!ExtraGaugesCompat.isLoaded()) {
            return;
        }
        RecipeType<MechanicalCraftingRecipe> mechanicalType = AllRecipeTypes.MECHANICAL_CRAFTING.getType();
        this.availableMechanicalRecipe = level.getRecipeManager().getAllRecipesFor(mechanicalType).parallelStream()
                .filter(r -> output.getItem() == r.value().getResultItem((HolderLookup.Provider) level.registryAccess()).getItem())
                .filter(r -> {
                    if (AllRecipeTypes.shouldIgnoreInAutomation(r)) {
                        return false;
                    }
                    HashSet<Item> itemsUsed = new HashSet<>();
                    for (Ingredient ingredient : r.value().getIngredients()) {
                        if (ingredient.isEmpty()) continue;
                        boolean available = false;
                        for (BigItemStack bis : this.inputConfig) {
                            if (bis.stack.isEmpty() || !ingredient.test(bis.stack)) continue;
                            available = true;
                            itemsUsed.add(bis.stack.getItem());
                            break;
                        }
                        if (!available) {
                            return false;
                        }
                    }
                    return itemsUsed.size() >= itemsToUse.size();
                }).findAny().map(RecipeHolder::value).orElse(null);
    }
}