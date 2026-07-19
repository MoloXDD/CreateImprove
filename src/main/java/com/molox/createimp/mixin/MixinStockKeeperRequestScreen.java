package com.molox.createimp.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.molox.createimp.CreateImp;
import com.molox.createimp.CreateImpConfig;
import com.molox.createimp.block.work_warehouse.WorkWarehouseNetworkHelper;
import com.molox.createimp.client.ClientWorkWarehouseAvailabilityCache;
import com.molox.createimp.client.TemplateOrderTooltipHandler;
import com.molox.createimp.item.TemplateOrderTarget;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.molox.createimp.network.RequestTemplateMaterialsPacket;
import com.molox.createimp.network.RequestWorkWarehouseAvailabilityPacket;
import com.molox.createimp.util.StockKeeperRequestScreenInvoker;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.data.Couple;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mixin(value = StockKeeperRequestScreen.class, priority = 500, remap = false)
public abstract class MixinStockKeeperRequestScreen implements StockKeeperRequestScreenInvoker {

    @Unique
    private static final ResourceLocation TEMPLATE_SLOT_BG =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/stock_keeper_template_slot_bg.png");

    @Unique
    private static final ResourceLocation TEMPLATE_REQUEST_SLOT_BG =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/stock_keeper_template_request_slot_bg.png");

    @Unique
    private static final ResourceLocation TEMPLATE_SLOT_BG_2 =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/stock_keeper_template_slot_bg2.png");

    @Unique
    private static final ResourceLocation TEMPLATE_REQUEST_SLOT_BG_2 =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/stock_keeper_template_request_slot_bg2.png");

    @Unique
    private static boolean createimp$isStyle2() {
        return CreateImp.getConfig().templateConfig.stockKeeperTemplateDisplayStyle
                == CreateImpConfig.TemplateConfig.TemplateDisplayStyle.STYLE_2;
    }

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
    List<List<ClipboardEntry>> clipboardItem;

    @Shadow
    public native boolean isSchematicListMode();

    @Shadow
    private native boolean isConfirmHovered(int mouseX, int mouseY);

    @Shadow
    private boolean encodeRequester;

    @Shadow
    ItemStack itemToProgram;

    @Shadow
    private native BigItemStack getOrderForItem(ItemStack stack);

    @Shadow
    private native void sendIt();

    @Shadow
    public List<com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack> recipesToOrder;

    @Shadow
    private native Couple<Integer> getHoveredSlot(int x, int y);

    @Shadow
    Couple<Integer> noneHovered;

    @Shadow
    private native void drawItemCount(GuiGraphics graphics, int count, int customCount);

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
        if (createimp$isConfiguringRedstoneRequester()) {
            return;
        }
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

    /**
     * 只有主手是桌布类物品（{@code AllTags.AllItemTags.TABLE_CLOTHS}）时才
     * 禁止把模板点入请求栏；主手是红石请求器本身时放开，模板会原样随整单
     * 一起编程进红石请求器，交给它接收脉冲时再判断（见
     * {@link MixinRedstoneRequesterBlockEntity}）。
     */
    @Unique
    private boolean createimp$blocksTemplateOrder() {
        if (!this.encodeRequester) {
            return false;
        }
        return com.simibubi.create.AllTags.AllItemTags.TABLE_CLOTHS.matches(this.itemToProgram);
    }

    /**
     * 正在把请求栏"配置"进红石请求器本身（不是桌布，也不是直接发送）。
     * 这种情况下不检查可用工作仓库数量，随意让玩家配置，真正的检查放到
     * 红石请求器自己接收脉冲触发时（见 MixinRedstoneRequesterBlockEntity）。
     */
    @Unique
    private boolean createimp$isConfiguringRedstoneRequester() {
        return this.encodeRequester && com.simibubi.create.AllBlocks.REDSTONE_REQUESTER.isIn(this.itemToProgram);
    }

    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;getOrderForItem(Lnet/minecraft/world/item/ItemStack;)Lcom/simibubi/create/content/logistics/BigItemStack;"))
    private BigItemStack createimp$blockTemplateOrderClick(StockKeeperRequestScreen instance, ItemStack stack) {
        BigItemStack existing = this.getOrderForItem(stack);
        if (existing == null && createimp$blocksTemplateOrder() && TemplateOrderTokenHelper.isToken(stack)) {
            return new BigItemStack(stack, 0);
        }
        return existing;
    }

    @Redirect(method = "mouseScrolled", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;getOrderForItem(Lnet/minecraft/world/item/ItemStack;)Lcom/simibubi/create/content/logistics/BigItemStack;"))
    private BigItemStack createimp$blockTemplateOrderScroll(StockKeeperRequestScreen instance, ItemStack stack) {
        BigItemStack existing = this.getOrderForItem(stack);
        if (existing == null && createimp$blocksTemplateOrder() && TemplateOrderTokenHelper.isToken(stack)) {
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
        if (createimp$isConfiguringRedstoneRequester()) {
            return false;
        }
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
        if (createimp$isConfiguringRedstoneRequester() || createimp$countTemplateEntries() == 0) {
            // 配置红石请求器时，不管请求栏里有没有模板，都直接走原有的
            // "编程"发送逻辑，模板原样跟着一起写进物品，不弹材料检查窗口——
            // 那个窗口检查的是当下的网络库存，对红石请求器要在未来某次脉冲
            // 触发时才会用到的材料没有意义。
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
        if (createimp$isConfiguringRedstoneRequester()) {
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

    @Unique
    private static boolean createimp$isMergeMode() {
        return CreateImp.getConfig().templateConfig.mergeTemplateWithStock;
    }

    @Inject(method = "refreshSearchResults", at = @At("RETURN"))
    private void createimp$pinTemplateCategory(boolean scrollBackUp, CallbackInfo ci) {
        if (this.currentItemSource == null || this.isSchematicListMode()) {
            return;
        }
        if (this.displayedItems == null || this.displayedItems.isEmpty()) {
            return;
        }

        if (createimp$isMergeMode()) {
            createimp$mergeTemplatesIntoCategories();
            createimp$recomputeCategoryLayout();
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
        createimp$recomputeCategoryLayout();
    }

    @Unique
    private void createimp$recomputeCategoryLayout() {
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

    /**
     * 合并模式：不再把模板抽成单独分类，而是每个分类桶内部原地把模板条目
     * 挪到最前面；如果一个展示物已经有对应模板，它自己原本的普通物品条目
     * 就不再单独出现——用模板条目本身当作这个展示物的"合并槽位"，角标数字
     * 另外用当前真实库存现算（见 {@link #createimp$drawMergedTemplateStockBadge}），
     * 不使用这里的桶结构。分类桶本身的数量、顺序、每个分类归属的物品完全
     * 不变，跟随仓储发报机原有的分类结果。
     */
    @Unique
    private void createimp$mergeTemplatesIntoCategories() {
        List<ItemStack> templateDisplays = new ArrayList<>();
        List<List<BigItemStack>> merged = new ArrayList<>();
        for (List<BigItemStack> bucket : this.displayedItems) {
            List<BigItemStack> tokens = new ArrayList<>();
            List<BigItemStack> regulars = new ArrayList<>();
            for (BigItemStack entry : bucket) {
                if (TemplateOrderTokenHelper.isToken(entry.stack)) {
                    tokens.add(entry);
                    TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(entry.stack);
                    if (target != null) {
                        templateDisplays.add(target.display());
                    }
                } else {
                    regulars.add(entry);
                }
            }
            if (tokens.isEmpty()) {
                merged.add(bucket);
                continue;
            }
            List<BigItemStack> filteredRegulars = new ArrayList<>();
            for (BigItemStack regular : regulars) {
                if (!createimp$hasMatchingTemplate(tokens, regular.stack)) {
                    filteredRegulars.add(regular);
                }
            }
            List<BigItemStack> newBucket = new ArrayList<>(tokens.size() + filteredRegulars.size());
            newBucket.addAll(tokens);
            newBucket.addAll(filteredRegulars);
            merged.add(newBucket);
        }
        TemplateOrderTooltipHandler.updateCurrentTemplateDisplays(templateDisplays);
        this.displayedItems = merged;
    }

    @Unique
    private static boolean createimp$hasMatchingTemplate(List<BigItemStack> tokens, ItemStack displayStack) {
        for (BigItemStack token : tokens) {
            TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(token.stack);
            if (target != null && ItemStack.isSameItemSameComponents(target.display(), displayStack)) {
                return true;
            }
        }
        return false;
    }

    @Inject(method = "renderItemEntry", at = @At("HEAD"))
    private void createimp$drawTemplateOrderBackground(GuiGraphics graphics, float scale, BigItemStack entry,
                                                       boolean isStackHovered, boolean isRenderingOrders, CallbackInfo ci) {
        if (isRenderingOrders && !createimp$isStyle2() && TemplateOrderTokenHelper.isToken(entry.stack)) {
            graphics.blit(TEMPLATE_REQUEST_SLOT_BG, 0, 0, 0, 0, 18, 18, 18, 18);
        }
    }

    @Redirect(method = "renderItemEntry", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void createimp$redirectSlotBackground(AllGuiTextures instance, GuiGraphics graphics, int x, int y,
                                                  @Local(argsOnly = true) BigItemStack entry) {
        if (!createimp$isStyle2() && TemplateOrderTokenHelper.isToken(entry.stack)) {
            graphics.blit(TEMPLATE_SLOT_BG, x, y, 0, 0, 18, 18, 18, 18);
        } else {
            instance.render(graphics, x, y);
        }
    }

    /**
     * 样式2：模板贴图改为绘制在物品图标之上的"前景"，并且要跟随物品一起
     * 缩放（悬浮时放大 7.5%）。这里不去挂接 GuiGameElement 内部的渲染调用
     * （那是 catnip 库的类，没有源码在手，不能猜它的方法签名），而是在
     * renderItemEntry 结束后，自己独立重建一遍原版用来绘制物品图标的那套
     * pushPose/translate/scale 变换（数值直接照抄原版反编译出来的写法），
     * 在同样的变换下画一张 18x18 的贴图，效果上会和物品图标完全同步缩放。
     */
    @Inject(method = "renderItemEntry", at = @At("TAIL"))
    private void createimp$drawTemplateForeground(GuiGraphics graphics, float scale, BigItemStack entry,
                                                  boolean isStackHovered, boolean isRenderingOrders, CallbackInfo ci) {
        if (!createimp$isStyle2() || !TemplateOrderTokenHelper.isToken(entry.stack)) {
            return;
        }
        ResourceLocation texture = isRenderingOrders ? TEMPLATE_REQUEST_SLOT_BG_2 : TEMPLATE_SLOT_BG_2;
        float scaleFromHover = isStackHovered ? 1.075f : 1.0f;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(1.0, 1.0, 0.0);
        pose.translate(9.0, 9.0, 0.0);
        pose.scale(scale, scale, scale);
        pose.scale(scaleFromHover, scaleFromHover, scaleFromHover);
        pose.translate(-9.0, -9.0, 0.0);
        pose.translate(0.0, 0.0, 150.0);
        graphics.blit(texture, -1, -1, 0, 0, 18, 18, 18, 18);
        pose.popPose();
    }

    @WrapWithCondition(method = "renderItemEntry", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;drawItemCount(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private boolean createimp$hideTemplateItemCount(StockKeeperRequestScreen self, GuiGraphics graphics, int count, int customCount,
                                                    @Local(argsOnly = true) BigItemStack entry,
                                                    @Local(argsOnly = true, ordinal = 1) boolean isRenderingOrders) {
        return isRenderingOrders || !TemplateOrderTokenHelper.isToken(entry.stack);
    }

    /**
     * 手持带材料清单的剪贴板右击仓管时，自动把清单填进请求栏：网络里能凑
     * 够的部分照常填真实物品；凑不够的缺口部分，如果这个展示物在当前网络
     * 里有对应的模板，就用模板补上缺口数量；同一个展示物同时有多个模板时
     * 取遍历顺序里靠前的那一个；没有对应模板的缺口部分维持原版行为——直接
     * 不请求那一部分。
     * <p>
     * 模板集中排在所有普通物品前面再一起填进请求栏，让它们有更大机会完整
     * 显示在只显示前几项、其余用[+n]折叠的请求栏里。
     * <p>
     * 客户端已经从服务端同步过来的库存快照（{@code getLastClientsideStockSnapshotAsSummary}）
     * 本身就已经混入了当前网络所有可下单模板的令牌条目（见
     * {@link com.molox.createimp.item.TemplateOrderSummaryHelper#augment}），
     * 这里直接复用这份数据找模板，不需要额外的网络请求。
     */
    @Inject(method = "requestSchematicList", at = @At("HEAD"), cancellable = true)
    private void createimp$fillSchematicListWithTemplates(CallbackInfo ci) {
        ci.cancel();
        this.itemsToOrder.clear();
        InventorySummary availableItems = this.blockEntity.getLastClientsideStockSnapshotAsSummary();
        List<BigItemStack> tokenPool = availableItems.getStacks();

        List<BigItemStack> templateEntries = new ArrayList<>();
        List<BigItemStack> regularEntries = new ArrayList<>();
        for (List<ClipboardEntry> page : this.clipboardItem) {
            for (ClipboardEntry entry : page) {
                ItemStack stack = entry.icon;
                if (stack.isEmpty()) {
                    continue;
                }
                int needed = entry.itemAmount;
                int available = Math.min(needed, availableItems.getCountOf(stack));
                if (available > 0) {
                    regularEntries.add(new BigItemStack(stack, available));
                }
                int shortfall = needed - available;
                if (shortfall <= 0) {
                    continue;
                }
                BigItemStack token = createimp$findTemplateToken(tokenPool, stack);
                if (token != null) {
                    templateEntries.add(new BigItemStack(token.stack.copy(), shortfall));
                }
            }
        }
        this.itemsToOrder.addAll(templateEntries);
        this.itemsToOrder.addAll(regularEntries);
    }

    @Unique
    private static BigItemStack createimp$findTemplateToken(List<BigItemStack> pool, ItemStack targetDisplay) {
        for (BigItemStack candidate : pool) {
            if (!TemplateOrderTokenHelper.isToken(candidate.stack)) {
                continue;
            }
            TemplateOrderTarget candidateTarget = TemplateOrderTokenHelper.getTarget(candidate.stack);
            if (candidateTarget != null && ItemStack.isSameItemSameComponents(candidateTarget.display(), targetDisplay)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 合并模式下，上方物品来源列表里模板条目的右下角数字不用原版那套（那套
     * 用的是令牌自己携带的、没有实际意义的数量），而是现算"这个展示物当前
     * 的真实库存减去请求栏里已经占用的真实物品数量"。原版自己的 drawItemCount
     * 调用只在 customCount>1 时才会触发，库存为 0 或 1 时不会触发，所以这里
     * 完全独立于原版那次调用，直接自己再画一次；哪怕库存是 0 也会画出"0"，
     * 不会因为原版的触发条件而被跳过。
     */
    @Inject(method = "renderItemEntry", at = @At("TAIL"))
    private void createimp$drawMergedTemplateStockBadge(GuiGraphics graphics, float scale, BigItemStack entry,
                                                        boolean isStackHovered, boolean isRenderingOrders, CallbackInfo ci) {
        if (isRenderingOrders || !createimp$isMergeMode() || !TemplateOrderTokenHelper.isToken(entry.stack)) {
            return;
        }
        TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(entry.stack);
        if (target == null) {
            return;
        }
        int liveStock = this.blockEntity.getLastClientsideStockSnapshotAsSummary().getCountOf(target.display());
        BigItemStack realOrder = this.getOrderForItem(target.display());
        int already = realOrder != null ? realOrder.count : 0;
        int liveCustom = Math.max(0, liveStock - already);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0, 0.0, 250.0);
        this.drawItemCount(graphics, liveStock, liveCustom);
        pose.popPose();
    }

    /**
     * 合并模式下点击上方物品来源列表里的模板条目：
     * <p>
     * 左键：剩余库存（当前真实库存减去请求栏里这个物品已占用的数量）大于 0
     * 时，往真实物品那一条请求上加，单次最多加到刚好用完剩余库存为止，绝不
     * 会因为这一次操作就超发模板（哪怕是 shift 一次性加一组也一样，最多只
     * 加到库存上限）；剩余库存为 0 时，才转为往这一条具体点到的模板自己的
     * 请求上加。
     * <p>
     * 中键：不管有没有库存，永远只加这一条具体点到的模板本身，支持 shift/ctrl
     * 一次加一组/加10个，和左键新增数量的判定方式一致。
     * <p>
     * 右键：优先减这一条具体点到的模板自己的请求，模板请求已经清空了才改成
     * 减真实物品那条请求。
     */
    /**
     * Create Cyber Goggles 这个模组也在 mouseClicked 的 HEAD 位置注入了一个
     * "按住某个按键点击弹出数量输入框"的功能，且不会检查点到的是不是模板。
     * Mixin 没有单个注入点级别的 priority 属性，实际能控制先后顺序的是
     * 整个 Mixin 类的 priority（见本文件顶部 {@code @Mixin(..., priority = 500)}，
     * 比默认值 1000 小，会更早被应用），这样只要是合并模式下点到模板、我们
     * 要自己处理的这次点击，就会抢在它之前把事件截停，它就没有机会再弹出
     * 数量框；不满足条件的点击完全不受影响，正常轮到它或原版处理。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void createimp$handleMergedTemplateClick(double mouseX, double mouseY, int button,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (!createimp$isMergeMode() || (button != 0 && button != 1 && button != 2)) {
            return;
        }
        Couple<Integer> hovered = this.getHoveredSlot((int) mouseX, (int) mouseY);
        if (hovered == this.noneHovered) {
            return;
        }
        int categoryIndex = hovered.getFirst();
        int itemIndex = hovered.getSecond();
        if (categoryIndex < 0 || categoryIndex >= this.displayedItems.size()) {
            return;
        }
        List<BigItemStack> bucket = this.displayedItems.get(categoryIndex);
        if (itemIndex < 0 || itemIndex >= bucket.size()) {
            return;
        }
        BigItemStack clicked = bucket.get(itemIndex);
        if (!TemplateOrderTokenHelper.isToken(clicked.stack)) {
            return;
        }
        TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(clicked.stack);
        if (target == null) {
            return;
        }

        cir.setReturnValue(true);
        int transfer = Screen.hasShiftDown() ? clicked.stack.getMaxStackSize()
                : (Screen.hasControlDown() ? 10 : 1);

        if (button == 2) {
            createimp$adjustOrder(clicked.stack, transfer);
            return;
        }
        if (button == 1) {
            BigItemStack templateOrder = this.getOrderForItem(clicked.stack);
            if (templateOrder != null && templateOrder.count > 0) {
                createimp$adjustOrder(clicked.stack, -transfer);
            } else {
                createimp$adjustOrder(target.display(), -transfer);
            }
            return;
        }
        int remaining = createimp$remainingStock(target);
        if (remaining > 0) {
            createimp$adjustOrder(target.display(), Math.min(transfer, remaining));
        } else {
            createimp$adjustOrder(clicked.stack, transfer);
        }
    }

    /**
     * 滚轮同理：向上滚等价于左键（剩余库存优先加真实物品，没有剩余库存才加
     * 模板），向下滚等价于右键（优先减模板，模板请求清空了再减真实物品）；
     * shift/ctrl 同样分别对应一组/10个，和点击保持一致。
     */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void createimp$handleMergedTemplateScroll(double mouseX, double mouseY, double scrollX, double scrollY,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (!createimp$isMergeMode()) {
            return;
        }
        Couple<Integer> hovered = this.getHoveredSlot((int) mouseX, (int) mouseY);
        if (hovered == this.noneHovered) {
            return;
        }
        int categoryIndex = hovered.getFirst();
        int itemIndex = hovered.getSecond();
        if (categoryIndex < 0 || categoryIndex >= this.displayedItems.size()) {
            return;
        }
        List<BigItemStack> bucket = this.displayedItems.get(categoryIndex);
        if (itemIndex < 0 || itemIndex >= bucket.size()) {
            return;
        }
        BigItemStack hoveredEntry = bucket.get(itemIndex);
        if (!TemplateOrderTokenHelper.isToken(hoveredEntry.stack)) {
            return;
        }
        TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(hoveredEntry.stack);
        if (target == null) {
            return;
        }

        cir.setReturnValue(true);
        int transfer = Screen.hasShiftDown() ? hoveredEntry.stack.getMaxStackSize()
                : (Screen.hasControlDown() ? 10 : 1);

        if (scrollY < 0) {
            BigItemStack templateOrder = this.getOrderForItem(hoveredEntry.stack);
            if (templateOrder != null && templateOrder.count > 0) {
                createimp$adjustOrder(hoveredEntry.stack, -transfer);
            } else {
                createimp$adjustOrder(target.display(), -transfer);
            }
            return;
        }
        if (scrollY > 0) {
            int remaining = createimp$remainingStock(target);
            if (remaining > 0) {
                createimp$adjustOrder(target.display(), Math.min(transfer, remaining));
            } else {
                createimp$adjustOrder(hoveredEntry.stack, transfer);
            }
        }
    }

    @Unique
    private int createimp$remainingStock(TemplateOrderTarget target) {
        int liveStock = this.blockEntity.getLastClientsideStockSnapshotAsSummary().getCountOf(target.display());
        BigItemStack realOrder = this.getOrderForItem(target.display());
        int already = realOrder != null ? realOrder.count : 0;
        return liveStock - already;
    }

    @Unique
    private void createimp$adjustOrder(ItemStack referenceStack, int delta) {
        if (delta == 0) {
            return;
        }
        BigItemStack existing = this.getOrderForItem(referenceStack);
        if (existing == null) {
            if (delta <= 0 || this.itemsToOrder.size() >= 9) {
                return;
            }
            existing = new BigItemStack(referenceStack.copyWithCount(1), 0);
            this.itemsToOrder.add(existing);
        }
        existing.count += delta;
        if (existing.count <= 0) {
            this.itemsToOrder.remove(existing);
        }
    }
}