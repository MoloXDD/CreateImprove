package com.molox.createimp.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.molox.createimp.CreateImp;
import com.molox.createimp.block.work_warehouse.WorkWarehouseNetworkHelper;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mixin(value = StockKeeperRequestScreen.class, remap = false)
public abstract class MixinStockKeeperRequestScreen {

    @Unique
    private static final ResourceLocation TEMPLATE_SLOT_BG =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/stock_keeper_template_slot_bg.png");

    @Unique
    private static final ResourceLocation TEMPLATE_REQUEST_SLOT_BG =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/stock_keeper_template_request_slot_bg.png");

    @Unique
    private static final int TEMPLATE_CATEGORY_ID = -2;

    @Shadow
    public List<List<BigItemStack>> displayedItems;

    @Shadow
    public List<StockKeeperRequestScreen.CategoryEntry> categories;

    @Shadow
    public List<List<BigItemStack>> currentItemSource;

    @Shadow
    private Set<Integer> hiddenCategories;

    @Shadow
    public List<BigItemStack> itemsToOrder;

    @Shadow
    StockTickerBlockEntity blockEntity;

    @Shadow
    public native boolean isSchematicListMode();

    @Shadow
    private native boolean isConfirmHovered(int mouseX, int mouseY);

    @Unique
    private boolean createimp$isTemplateSendBlocked() {
        boolean hasTemplate = false;
        for (BigItemStack entry : this.itemsToOrder) {
            if (TemplateOrderTokenHelper.isToken(entry.stack)) {
                hasTemplate = true;
                break;
            }
        }
        if (!hasTemplate) {
            return false;
        }
        if (this.blockEntity == null || this.blockEntity.behaviour == null) {
            return true;
        }
        return !WorkWarehouseNetworkHelper.hasAvailableWorkWarehouse(this.blockEntity.behaviour.freqId);
    }

    @Redirect(method = "renderBg", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;isConfirmHovered(II)Z"))
    private boolean createimp$redirectConfirmHoveredRender(StockKeeperRequestScreen instance, int mouseX, int mouseY) {
        return this.isConfirmHovered(mouseX, mouseY) && !createimp$isTemplateSendBlocked();
    }

    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;isConfirmHovered(II)Z"))
    private boolean createimp$redirectConfirmHoveredClick(StockKeeperRequestScreen instance, int mouseX, int mouseY) {
        return this.isConfirmHovered(mouseX, mouseY) && !createimp$isTemplateSendBlocked();
    }

    @Inject(method = "renderForeground", at = @At("TAIL"))
    private void createimp$drawWorkWarehouseTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!this.isConfirmHovered(mouseX, mouseY)) {
            return;
        }
        boolean hasTemplate = false;
        for (BigItemStack entry : this.itemsToOrder) {
            if (TemplateOrderTokenHelper.isToken(entry.stack)) {
                hasTemplate = true;
                break;
            }
        }
        if (!hasTemplate) {
            return;
        }
        UUID freqId = (this.blockEntity != null && this.blockEntity.behaviour != null)
                ? this.blockEntity.behaviour.freqId : null;
        int availableCount = WorkWarehouseNetworkHelper.countAvailableWorkWarehouses(freqId);
        Component message = availableCount > 0
                ? Component.translatable("createimp.gui.stock_keeper.work_warehouse_available", availableCount)
                : Component.translatable("createimp.gui.stock_keeper.no_work_warehouse");
        graphics.renderComponentTooltip(net.minecraft.client.Minecraft.getInstance().font, List.of(message), mouseX, mouseY);
    }

    @Inject(method = "refreshSearchResults", at = @At("RETURN"))
    private void createimp$pinTemplateCategory(boolean scrollBackUp, CallbackInfo ci) {
        if (this.currentItemSource == null || this.isSchematicListMode()) {
            return;
        }
        if (this.displayedItems == null || this.displayedItems.isEmpty()) {
            return;
        }

        List<BigItemStack> templateBucket = new ArrayList<>();
        List<List<BigItemStack>> filteredDisplayedItems = new ArrayList<>();
        for (List<BigItemStack> bucket : this.displayedItems) {
            List<BigItemStack> filteredBucket = new ArrayList<>();
            for (BigItemStack entry : bucket) {
                if (TemplateOrderTokenHelper.isToken(entry.stack)) {
                    templateBucket.add(entry);
                } else {
                    filteredBucket.add(entry);
                }
            }
            filteredDisplayedItems.add(filteredBucket);
        }
        if (templateBucket.isEmpty()) {
            return;
        }
        this.displayedItems = filteredDisplayedItems;

        List<StockKeeperRequestScreen.CategoryEntry> newCategories = new ArrayList<>();
        List<List<BigItemStack>> newDisplayedItems = new ArrayList<>();

        StockKeeperRequestScreen.CategoryEntry templateCategory =
                new StockKeeperRequestScreen.CategoryEntry(TEMPLATE_CATEGORY_ID,
                        Component.translatable("createimp.gui.stock_keeper.template_category").getString(), 0);
        StockKeeperCategoryEntryAccessor templateAccessor =
                (StockKeeperCategoryEntryAccessor) (Object) templateCategory;
        templateAccessor.createimp$setHidden(this.hiddenCategories.contains(TEMPLATE_CATEGORY_ID));
        newCategories.add(templateCategory);
        newDisplayedItems.add(templateBucket);

        if (this.categories.isEmpty()) {
            List<BigItemStack> leftover = new ArrayList<>();
            for (List<BigItemStack> bucket : filteredDisplayedItems) {
                leftover.addAll(bucket);
            }
            if (!leftover.isEmpty()) {
                StockKeeperRequestScreen.CategoryEntry unsortedCategory =
                        new StockKeeperRequestScreen.CategoryEntry(-1,
                                CreateLang.translate("gui.stock_keeper.unsorted_category").string(), 0);
                StockKeeperCategoryEntryAccessor unsortedAccessor =
                        (StockKeeperCategoryEntryAccessor) (Object) unsortedCategory;
                unsortedAccessor.createimp$setHidden(this.hiddenCategories.contains(-1));
                newCategories.add(unsortedCategory);
                newDisplayedItems.add(leftover);
            }
        } else {
            newCategories.addAll(this.categories);
            newDisplayedItems.addAll(filteredDisplayedItems);
        }

        this.categories = newCategories;
        this.displayedItems = newDisplayedItems;

        int categoryY = 0;
        for (int i = 0; i < this.categories.size(); ++i) {
            StockKeeperCategoryEntryAccessor accessor =
                    (StockKeeperCategoryEntryAccessor) (Object) this.categories.get(i);
            accessor.createimp$setY(categoryY);
            List<BigItemStack> bucket = this.displayedItems.get(i);
            if (bucket.isEmpty()) {
                continue;
            }
            categoryY += 20;
            if (accessor.createimp$isHidden()) {
                continue;
            }
            categoryY += (int) Math.ceil(bucket.size() / 9.0) * 20;
        }
    }

    @Inject(method = "renderItemEntry", at = @At("HEAD"))
    private void createimp$drawTemplateOrderBackground(GuiGraphics graphics, float scale, BigItemStack entry,
                                                       boolean isStackHovered, boolean isRenderingOrders, CallbackInfo ci) {
        if (isRenderingOrders && TemplateOrderTokenHelper.isToken(entry.stack)) {
            graphics.blit(TEMPLATE_REQUEST_SLOT_BG, 0, 0, 0, 0, 18, 18, 18, 18);
        }
    }

    @Redirect(method = "renderItemEntry", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void createimp$redirectSlotBackground(AllGuiTextures instance, GuiGraphics graphics, int x, int y,
                                                  @Local(argsOnly = true) BigItemStack entry) {
        if (TemplateOrderTokenHelper.isToken(entry.stack)) {
            graphics.blit(TEMPLATE_SLOT_BG, x, y, 0, 0, 18, 18, 18, 18);
        } else {
            instance.render(graphics, x, y);
        }
    }

    @WrapWithCondition(method = "renderItemEntry", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;drawItemCount(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private boolean createimp$hideTemplateItemCount(StockKeeperRequestScreen self, GuiGraphics graphics, int count, int customCount,
                                                    @Local(argsOnly = true) BigItemStack entry,
                                                    @Local(argsOnly = true, ordinal = 1) boolean isRenderingOrders) {
        return isRenderingOrders || !TemplateOrderTokenHelper.isToken(entry.stack);
    }
}