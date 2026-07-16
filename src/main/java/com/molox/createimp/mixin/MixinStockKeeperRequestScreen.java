package com.molox.createimp.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.molox.createimp.CreateImp;
import com.molox.createimp.block.work_warehouse.WorkWarehouseNetworkHelper;
import com.molox.createimp.client.ClientWorkWarehouseAvailabilityCache;
import com.molox.createimp.client.TemplateOrderTooltipHandler;
import com.molox.createimp.item.TemplateOrderTarget;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.molox.createimp.network.RequestTemplateMaterialsPacket;
import com.molox.createimp.network.RequestWorkWarehouseAvailabilityPacket;
import com.molox.createimp.util.StockKeeperRequestScreenInvoker;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
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
public abstract class MixinStockKeeperRequestScreen implements StockKeeperRequestScreenInvoker {

    @Unique
    private static final ResourceLocation TEMPLATE_SLOT_BG =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/stock_keeper_template_slot_bg.png");

    @Unique
    private static final ResourceLocation TEMPLATE_REQUEST_SLOT_BG =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/stock_keeper_template_request_slot_bg.png");

    @Unique
    private static final int TEMPLATE_CATEGORY_ID = -2;

    /**
     * 向服务端查询工作仓库可用数量的轮询节奏，与材料窗口 {@code TemplateMaterialsScreen}
     * 使用的 STOCK_POLL_TICKS 保持一致的量级。
     */
    @Unique
    private static final int WORK_WAREHOUSE_POLL_TICKS = 15;

    @Unique
    private int createimp$workWarehousePollCooldown = 0;

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

    @Shadow
    private boolean encodeRequester;

    @Shadow
    private native BigItemStack getOrderForItem(ItemStack stack);

    @Shadow
    private native void sendIt();

    @Shadow
    public List<com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack> recipesToOrder;

    @Override
    public void createimp$invokeSendIt() {
        this.sendIt();
    }

    @Override
    public void createimp$clearRequestBar() {
        this.itemsToOrder = new ArrayList<>();
        this.recipesToOrder = new ArrayList<>();
    }

    /**
     * 请求栏含有模板时，按固定节奏向服务端查询一次这个频率下的可用工作仓库
     * 数量，结果异步写入 {@link ClientWorkWarehouseAvailabilityCache}，供
     * {@link #createimp$isTemplateSendBlocked()}、悬浮提示读取。不再像之前
     * 那样在客户端本地直接调用 {@link WorkWarehouseNetworkHelper}——那个
     * 注册表只在服务端进程里维护，独立服务端环境下客户端永远查不到数据。
     */
    @Override
    public void createimp$pollWorkWarehouseAvailability() {
        int templateCount = createimp$countTemplateEntries();
        if (templateCount == 0) {
            return;
        }
        if (this.blockEntity == null || this.blockEntity.behaviour == null
                || this.blockEntity.behaviour.freqId == null) {
            return;
        }
        if (createimp$workWarehousePollCooldown-- > 0) {
            return;
        }
        createimp$workWarehousePollCooldown = WORK_WAREHOUSE_POLL_TICKS;
        PacketDistributor.sendToServer(new RequestWorkWarehouseAvailabilityPacket(this.blockEntity.behaviour.freqId));
    }

    @Shadow
    private native void revalidateOrders();

    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;getOrderForItem(Lnet/minecraft/world/item/ItemStack;)Lcom/simibubi/create/content/logistics/BigItemStack;"))
    private BigItemStack createimp$blockTemplateOrderClick(StockKeeperRequestScreen instance, ItemStack stack) {
        BigItemStack existing = this.getOrderForItem(stack);
        if (existing == null && this.encodeRequester && TemplateOrderTokenHelper.isToken(stack)) {
            return new BigItemStack(stack, 0);
        }
        return existing;
    }

    @Redirect(method = "mouseScrolled", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;getOrderForItem(Lnet/minecraft/world/item/ItemStack;)Lcom/simibubi/create/content/logistics/BigItemStack;"))
    private BigItemStack createimp$blockTemplateOrderScroll(StockKeeperRequestScreen instance, ItemStack stack) {
        BigItemStack existing = this.getOrderForItem(stack);
        if (existing == null && this.encodeRequester && TemplateOrderTokenHelper.isToken(stack)) {
            return new BigItemStack(stack, 0);
        }
        return existing;
    }

    @Unique
    private int createimp$countTemplateEntries() {
        int count = 0;
        for (BigItemStack entry : this.itemsToOrder) {
            if (TemplateOrderTokenHelper.isToken(entry.stack)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 判断确认键是否应该被禁用：请求栏里的模板数量超过服务端最近一次回复的
     * 可用工作仓库数量时禁用。还没收到过服务端回应时，
     * {@link ClientWorkWarehouseAvailabilityCache#get} 返回 -1，
     * 天然小于任意 templateCount（此时 templateCount 必然 >= 1），
     * 因此会正确地默认按"禁用"处理，不需要额外的未知状态特判。
     */
    @Unique
    private boolean createimp$isTemplateSendBlocked() {
        int templateCount = createimp$countTemplateEntries();
        if (templateCount == 0) {
            return false;
        }
        if (this.blockEntity == null || this.blockEntity.behaviour == null) {
            return true;
        }
        int available = ClientWorkWarehouseAvailabilityCache.get(this.blockEntity.behaviour.freqId);
        return available < templateCount;
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

    /**
     * 拦截确认键真正触发的发送动作：请求栏内不含模板时，行为与原版完全一致；
     * 含模板时（此时已确认工作仓库数量足够，否则外层的 isConfirmHovered 重定向
     * 会让点击根本走不到这里），改为向服务端请求一次材料计算，不清空请求栏，
     * 不立即真正发送打包请求。
     */
    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;sendIt()V"))
    private void createimp$redirectSendIt(StockKeeperRequestScreen instance) {
        if (createimp$countTemplateEntries() == 0) {
            this.sendIt();
            return;
        }
        this.revalidateOrders();
        if (this.itemsToOrder.isEmpty() || this.blockEntity == null) {
            return;
        }
        PacketDistributor.sendToServer(new RequestTemplateMaterialsPacket(
                this.blockEntity.getBlockPos(), new ArrayList<>(this.itemsToOrder)));
    }

    @Inject(method = "renderForeground", at = @At("TAIL"))
    private void createimp$drawWorkWarehouseTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!this.isConfirmHovered(mouseX, mouseY)) {
            return;
        }
        int templateCount = createimp$countTemplateEntries();
        if (templateCount == 0) {
            return;
        }
        UUID freqId = (this.blockEntity != null && this.blockEntity.behaviour != null)
                ? this.blockEntity.behaviour.freqId : null;
        int availableCount = ClientWorkWarehouseAvailabilityCache.get(freqId);
        List<Component> lines = new ArrayList<>();
        if (availableCount < templateCount) {
            lines.add(Component.translatable("createimp.gui.stock_keeper.not_enough_work_warehouse"));
        }
        // 还没收到服务端回应（-1）时，提示文案里不显示负数，展示为 0 更符合直觉，
        // 不影响上面"是否禁用"这条判断本身（判断依然用的是原始的 -1）。
        lines.add(Component.translatable("createimp.gui.stock_keeper.work_warehouse_available",
                Math.max(0, availableCount)));
        graphics.renderComponentTooltip(net.minecraft.client.Minecraft.getInstance().font, lines, mouseX, mouseY);
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
            TemplateOrderTooltipHandler.updateCurrentTemplateDisplays(List.of());
            return;
        }
        List<ItemStack> templateDisplays = new ArrayList<>();
        for (BigItemStack entry : templateBucket) {
            TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(entry.stack);
            if (target != null) {
                templateDisplays.add(target.display());
            }
        }
        TemplateOrderTooltipHandler.updateCurrentTemplateDisplays(templateDisplays);
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