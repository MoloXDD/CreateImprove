package com.molox.createimp.screen;

import com.molox.createimp.block.template_panel.TemplatePanelBehaviour;
import com.molox.createimp.block.template_panel.TemplatePanelConnection;
import com.molox.createimp.block.template_panel.TemplatePanelConnectionHandler;
import com.molox.createimp.block.template_panel.TemplatePanelPosition;
import com.molox.createimp.network.TemplatePanelConfigurationPacket;
import com.molox.createimp.registry.ModItems;
import com.simibubi.create.AllRecipeTypes;
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
    private boolean craftingActive;
    private List<BigItemStack> craftingIngredients;

    public TemplatePanelScreen(TemplatePanelBehaviour behaviour) {
        this.behaviour = behaviour;
        this.minecraft = Minecraft.getInstance();
        this.availableCraftingRecipe = null;
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
        if (this.availableCraftingRecipe == null) {
            this.craftingActive = false;
            return;
        }
        this.craftingIngredients = convertRecipeToPackageOrderContext(this.availableCraftingRecipe, this.inputConfig);
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
        if (this.availableCraftingRecipe != null) {
            this.activateCraftingButton = new IconButton(x + 31, y + 27, AllIcons.I_3x3);
            this.activateCraftingButton.withCallback(() -> {
                this.craftingActive = !this.craftingActive;
                this.init();
                if (this.craftingActive) {
                    this.outputConfig.count = this.availableCraftingRecipe.getResultItem((HolderLookup.Provider) this.minecraft.level.registryAccess()).getCount();
                }
            });
            this.activateCraftingButton.setToolTip(CreateLang.translate("gui.factory_panel.activate_crafting").component());
            this.addRenderableWidget(this.activateCraftingButton);
        }

        this.demandModeButton = null;
        if (!this.behaviour.targetedBy.isEmpty()) {
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
            boolean shouldShow = !this.behaviour.targetedBy.isEmpty();
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
            for (BigItemStack itemStack : this.craftingIngredients) {
                this.renderInputItem(graphics, slot++, itemStack, mouseX, mouseY);
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
        graphics.renderItemDecorations(this.font, this.behaviour.getFilter(), outputX, outputY, "" + this.outputConfig.count);
        if (mouseX >= outputX - 1 && mouseX < outputX - 1 + 18 && mouseY >= outputY - 1 && mouseY < outputY - 1 + 18) {
            MutableComponent c1 = CreateLang.translate("gui.factory_panel.expected_output",
                    CreateLang.itemName(this.outputConfig.stack).add(CreateLang.text(" x" + this.outputConfig.count)).string()).component();
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
        int inputX = this.guiLeft + 68 + slot % 3 * 20;
        int inputY = this.guiTop + 28 + slot / 3 * 20;
        graphics.renderItem(itemStack.stack, inputX, inputY);
        if (!this.craftingActive && !itemStack.stack.isEmpty()) {
            graphics.renderItemDecorations(this.font, itemStack.stack, inputX, inputY, "" + itemStack.count);
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
                CreateLang.translate("gui.factory_panel.sending_item", CreateLang.itemName(itemStack.stack).add(CreateLang.text(" x" + itemStack.count)).string()).component(),
                CreateLang.translate("gui.factory_panel.scroll_to_change_amount").style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component(),
                CreateLang.translate("gui.factory_panel.left_click_disconnect").style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component()
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
                itemStack.count = Mth.clamp((int) (itemStack.count + Math.signum(scrollY) * (hasShiftDown() ? 10 : 1)), 1, 64);
                return true;
            }
        }
        int outputX = x + 160;
        int outputY = y + 48;
        if (mouseX >= outputX && mouseX < outputX + 16 && mouseY >= outputY && mouseY < outputY + 16) {
            this.outputConfig.count = Mth.clamp((int) (this.outputConfig.count + Math.signum(scrollY) * (hasShiftDown() ? 10 : 1)), 1, 64);
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
    }
}