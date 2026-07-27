package com.molox.createimp.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.molox.createimp.CreateImp;
import com.molox.createimp.CreateImpConfig;
import com.molox.createimp.compat.fluidlogistics.FluidLogisticsCompat;
import com.molox.createimp.compat.fluidlogistics.TemplateFluidDisplayHelper;
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
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.ChatFormatting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector4f;
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

    // ==== 流体模板悬浮裁剪手动校准区 ====
    // 非悬浮状态的裁剪窗口用当前变换直接算，跟非悬浮时的原有表现完全一致；
    // 悬浮状态不再信任变换矩阵里悬浮缩放那部分算出来的结果，而是在非悬浮
    // 裁剪框的基础上，宽、高各增长 FLUID_TEMPLATE_HOVER_GROWTH 像素，中心点
    // 在非悬浮中心的基础上偏移 (FLUID_TEMPLATE_HOVER_CENTER_OFFSET_X,
    // FLUID_TEMPLATE_HOVER_CENTER_OFFSET_Y) 像素——实机看偏了就直接改这两个
    // 偏移量，正数分别代表往右、往下偏。
    @Unique
    private static final float FLUID_TEMPLATE_HOVER_GROWTH = 3.0f;
    @Unique
    private static final float FLUID_TEMPLATE_HOVER_CENTER_OFFSET_X = 0.0f;
    @Unique
    private static final float FLUID_TEMPLATE_HOVER_CENTER_OFFSET_Y = 0.0f;

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
        return CreateImp.getConfig().templateFunctionConfig.stockKeeperTemplateDisplayStyle
                == CreateImpConfig.TemplateFunctionConfig.TemplateDisplayStyle.STYLE_2;
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

    /**
     * 根因：{@code maxCraftable()} 在给"某个配方现在最多能凑出几份"计算候选
     * 材料时，是拿仓储发报机发回来的库存汇总（{@code InventorySummary}）逐条
     * 用 {@code Ingredient.test(stack)} 去匹配——而这份汇总被
     * {@link MixinLogisticalStockRequestPacket} 用
     * {@link com.molox.createimp.item.TemplateOrderSummaryHelper#augment} 额外
     * 掺入了每个当前存在的模板对应的"模板令牌"条目（数量固定填了一个没有实际
     * 意义的巨大占位值，见该方法注释）。这个令牌本身就是"目标物品原样复制一份、
     * 只是另外挂了一个标记数据组件"，{@code Ingredient.test} 只认物品种类，
     * 不管这个额外的数据组件，于是只要配方需要的材料恰好对应着某个现存模板，
     * 令牌就会被当成"库存里还有一大堆这种材料"混进候选列表——不管是 JEI 配方
     * 界面的"+"号（{@code StockKeeperTransferHandler} 最终也是调用
     * {@code requestCraftable}→{@code maxCraftable}），还是仓管自己配方图标
     * 上的"+"号，走的都是这同一个方法，因此都会命中这个问题，并不是 JEI 那边
     * 单独出的错。
     * <p>
     * 修复：只在这一个匹配点上，令牌本身直接判定为不匹配，不影响
     * {@code Ingredient.test} 其他任何调用方；这样"现存材料"的匹配范围就
     * 恢复成只看真实库存条目，模板令牌不会再被当成材料来源，也就不会被错误地
     * 当作"这个配方缺的材料"填进请求栏。
     */
    @Redirect(method = "maxCraftable", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/Ingredient;test(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean createimp$excludeTemplateTokenFromCraftableMatch(net.minecraft.world.item.crafting.Ingredient instance, ItemStack stack) {
        if (TemplateOrderTokenHelper.isToken(stack)) {
            return false;
        }
        return instance.test(stack);
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
        if (this.itemsToOrder.isEmpty() || this.blockEntity == null || this.blockEntity.behaviour == null) {
            CreateImp.LOGGER.info(
                    "[模板材料] 仓管界面放弃发送材料请求：请求栏为空={}, 仓管方块为空={}, 网络行为为空={}",
                    this.itemsToOrder.isEmpty(), this.blockEntity == null,
                    this.blockEntity != null && this.blockEntity.behaviour == null);
            return;
        }
        CreateImp.LOGGER.info("[模板材料] 仓管界面发送材料计算请求：网络={}, 请求栏条目数={}",
                this.blockEntity.behaviour.freqId, this.itemsToOrder.size());
        PacketDistributor.sendToServer(new RequestTemplateMaterialsPacket(
                this.blockEntity.behaviour.freqId, new ArrayList<>(this.itemsToOrder)));
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
        return CreateImp.getConfig().templateFunctionConfig.mergeTemplateWithStock;
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

    /**
     * 判断这一条展示条目是不是"监测流体的模板令牌"：必须先是模板令牌，
     * 且流体包裹已安装（未安装时模板不可能监测到流体过滤物，这里的判断
     * 只是双重保险），且令牌对应的展示物本身是流体包裹的虚拟流体过滤物。
     */
    @Unique
    private static boolean createimp$isFluidTemplateEntry(ItemStack stack) {
        if (!TemplateOrderTokenHelper.isToken(stack)) {
            return false;
        }
        if (!FluidLogisticsCompat.isLoaded()) {
            return false;
        }
        TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(stack);
        return target != null && TemplateFluidDisplayHelper.isVirtualFluidDisplay(target.display());
    }

    /**
     * 样式1（背景样式）下，流体包裹的虚拟流体压缩罐物品自己的图标渲染会
     * 铺满整个 16x16 图标区域，把下面刚画好的模板背景贴图整个遮住。
     * <p>
     * 反编译确认原版 {@code renderItemEntry} 画图标那一句是：
     * <pre>
     * GuiGameElement.of(stackWithCount).render(graphics);
     * </pre>
     * 用 {@code @WrapOperation} 包住这一句调用，在渲染前后分别
     * {@code enableScissor}/{@code disableScissor}，达到"裁剪图标"的效果。
     * <p>
     * 非悬浮状态：直接用当前姿态矩阵，把本地坐标 (MARGIN,MARGIN) 和
     * (16-MARGIN,16-MARGIN) 投影成屏幕坐标，作为裁剪范围——这跟非悬浮时
     * 原本的表现完全一致，没有改动。
     * <p>
     * 悬浮状态：之前几版都是想办法推导"悬浮时图标真实缩放中心在哪"，反复
     * 验证都跟实机表现对不上，不再猜了。这里改成纯粹的经验做法：先按上面
     * 同一套本地坐标算出"如果不悬浮，这一帧应该是什么样子"（用悬浮系数把
     * 当前矩阵的缩放部分除掉，还原出非悬浮时的框，公式推导见下），然后在
     * 这个非悬浮框的基础上，宽、高各增长 {@code FLUID_TEMPLATE_HOVER_GROWTH}
     * 像素，中心点整体偏移 ({@code FLUID_TEMPLATE_HOVER_CENTER_OFFSET_X},
     * {@code FLUID_TEMPLATE_HOVER_CENTER_OFFSET_Y})——这两个偏移量放在类最
     * 开头，实机对不上就直接改这两个数字。
     * <p>
     * 【还原非悬浮框的公式】原版对图标做缩放用的轴心是本地坐标 (9,9)，这个
     * 点的屏幕投影位置 pivotScreen 不随缩放倍数变化（缩放轴心本身不动）。
     * 设当前矩阵对某点 P 的投影为 mat(P)，悬浮缩放系数为 h（悬浮时 1.075，
     * 不悬浮时 1.0），则有：mat(P) = pivotScreen + h × (matRest(P) - pivotScreen)，
     * 也就是 matRest(P) = pivotScreen + (mat(P) - pivotScreen) / h。不悬浮时
     * h=1，matRest(P) 直接等于 mat(P)，跟"非悬浮状态直接用当前矩阵"这一条
     * 是同一回事，写成统一公式只是为了悬浮、非悬浮共用一套代码。
     * <p>
     * {@code GuiGraphics} 内部的裁剪范围是一个真正的栈（{@code ScissorStack}），
     * {@code enableScissor}/{@code disableScissor} 分别对应入栈/出栈，出栈后
     * 会恢复外层原有的裁剪范围（这里是仓管列表本身的滚动裁剪区），不会把
     * 外层裁剪整个关掉，可以放心嵌套使用。
     */
    @Unique
    private static final float FLUID_TEMPLATE_CROP_MARGIN = 1.0f;
    @Unique
    private static final float FLUID_TEMPLATE_HOVER_SCALE = 1.075f;

    @WrapOperation(method = "renderItemEntry", at = @At(value = "INVOKE",
            target = "Lnet/createmod/catnip/gui/element/GuiGameElement$GuiRenderBuilder;render(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private void createimp$clipFluidTemplateIcon(GuiGameElement.GuiRenderBuilder instance, GuiGraphics graphics,
                                                 Operation<Void> original,
                                                 @Local(argsOnly = true) BigItemStack entry,
                                                 @Local(argsOnly = true, ordinal = 0) boolean isStackHovered) {
        if (createimp$isStyle2() || !createimp$isFluidTemplateEntry(entry.stack)) {
            original.call(instance, graphics);
            return;
        }
        Matrix4f mat = new Matrix4f(graphics.pose().last().pose());
        Vector4f pivot = mat.transform(new Vector4f(9.0f, 9.0f, 0.0f, 1.0f));
        Vector4f hoverTopLeft = mat.transform(new Vector4f(FLUID_TEMPLATE_CROP_MARGIN, FLUID_TEMPLATE_CROP_MARGIN, 0.0f, 1.0f));
        Vector4f hoverBottomRight = mat.transform(new Vector4f(16.0f - FLUID_TEMPLATE_CROP_MARGIN, 16.0f - FLUID_TEMPLATE_CROP_MARGIN, 0.0f, 1.0f));

        float h = isStackHovered ? FLUID_TEMPLATE_HOVER_SCALE : 1.0f;
        float restLeft = pivot.x() + (hoverTopLeft.x() - pivot.x()) / h;
        float restTop = pivot.y() + (hoverTopLeft.y() - pivot.y()) / h;
        float restRight = pivot.x() + (hoverBottomRight.x() - pivot.x()) / h;
        float restBottom = pivot.y() + (hoverBottomRight.y() - pivot.y()) / h;

        float x0f;
        float y0f;
        float x1f;
        float y1f;
        if (!isStackHovered) {
            x0f = restLeft;
            y0f = restTop;
            x1f = restRight;
            y1f = restBottom;
        } else {
            float centerX = (restLeft + restRight) / 2.0f + FLUID_TEMPLATE_HOVER_CENTER_OFFSET_X;
            float centerY = (restTop + restBottom) / 2.0f + FLUID_TEMPLATE_HOVER_CENTER_OFFSET_Y;
            // 这里传入 GROWTH/2 而不是 GROWTH：实机测试反馈过，按 GROWTH
            // 直接算，最终视觉增长量是设定值的 2 倍，这里先除以 2 校准过，
            // 使得 FLUID_TEMPLATE_HOVER_GROWTH 这个常量的取值本身就等于
            // 玩家在游戏里实际看到的宽、高增长像素数。
            float halfWidth = (restRight - restLeft + FLUID_TEMPLATE_HOVER_GROWTH / 2.0f) / 2.0f;
            float halfHeight = (restBottom - restTop + FLUID_TEMPLATE_HOVER_GROWTH / 2.0f) / 2.0f;
            x0f = centerX - halfWidth;
            y0f = centerY - halfHeight;
            x1f = centerX + halfWidth;
            y1f = centerY + halfHeight;
        }

        int x0 = Math.round(Math.min(x0f, x1f));
        int y0 = Math.round(Math.min(y0f, y1f));
        int x1 = Math.round(Math.max(x0f, x1f));
        int y1 = Math.round(Math.max(y0f, y1f));
        graphics.enableScissor(x0, y0, x1, y1);
        original.call(instance, graphics);
        graphics.disableScissor();
    }


    @WrapWithCondition(method = "renderItemEntry", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;drawItemCount(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private boolean createimp$hideTemplateItemCount(StockKeeperRequestScreen self, GuiGraphics graphics, int count, int customCount,
                                                    @Local(argsOnly = true) BigItemStack entry,
                                                    @Local(argsOnly = true, ordinal = 1) boolean isRenderingOrders) {
        if (!TemplateOrderTokenHelper.isToken(entry.stack)) {
            return true;
        }
        if (isRenderingOrders) {
            // 请求栏里普通物品模板保留原版数字角标（表示请求了几份模板）；
            // 流体模板改成流体格式，由下面 createimp$drawFluidOrderBadge 接手画。
            return !createimp$isFluidTemplateEntry(entry.stack);
        }
        return false;
    }

    /**
     * 请求栏里的流体模板角标：Create 原版 {@code renderItemEntry} 在请求栏
     * 场景下 {@code customCount} 就是原始的 {@code entry.count}（跳过了主
     * 列表那段"减去已请求数量"的调整），所以这里直接用 {@code entry.count}
     * 本身即可，不需要另外拿 {@code customCount} 局部变量。
     */
    @Inject(method = "renderItemEntry", at = @At("TAIL"))
    private void createimp$drawFluidOrderBadge(GuiGraphics graphics, float scale, BigItemStack entry,
                                               boolean isStackHovered, boolean isRenderingOrders, CallbackInfo ci) {
        if (!isRenderingOrders || !createimp$isFluidTemplateEntry(entry.stack) || entry.count <= 1) {
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0, 0.0, 200.0);
        TemplateFluidDisplayHelper.renderFluidAmountBadge(graphics, entry.count);
        pose.popPose();
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
        if (FluidLogisticsCompat.isLoaded() && TemplateFluidDisplayHelper.isVirtualFluidDisplay(target.display())) {
            if (liveCustom > 0) {
                TemplateFluidDisplayHelper.renderFluidAmountBadge(graphics, liveCustom);
            }
        } else {
            this.drawItemCount(graphics, liveStock, liveCustom);
        }
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
    @Unique
    private static int createimp$templateTransferStep(ItemStack stack, boolean orderBar) {
        if (createimp$isFluidTemplateEntry(stack)) {
            return orderBar
                    ? TemplateFluidDisplayHelper.orderBarFluidStep(Screen.hasShiftDown(), Screen.hasControlDown())
                    : TemplateFluidDisplayHelper.stockKeeperFluidStep(Screen.hasShiftDown(), Screen.hasControlDown());
        }
        return Screen.hasShiftDown() ? stack.getMaxStackSize() : (Screen.hasControlDown() ? 10 : 1);
    }

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
        int transfer = createimp$templateTransferStep(clicked.stack, false);

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
        int transfer = createimp$templateTransferStep(hoveredEntry.stack, false);

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

    /**
     * 分离模式下，流体模板走的是 Create 原版自己的点击/滚轮下单逻辑（不再
     * 被流包接管），但原版的单次步进是按"物品个数"设计的（无修饰键=1，
     * Ctrl=10，Shift=物品堆叠上限），对流体模板来说单位是 mB，"+1"等于每次
     * 只加 1mB，几乎不可用。这里专门接管流体模板的点击/滚轮，步进按
     * {@code orderClicked}（是否点在请求栏本身）分别使用流包对应的两套
     * 步进规则，其余逻辑照抄原版 {@code mouseClicked}/{@code mouseScrolled}
     * 对应分支，行为完全一致，只是步进数值不同。
     * <p>
     * 【为什么请求栏部分不排除合并模式】上面两个方法（
     * {@code createimp$handleMergedTemplateClick}/{@code Scroll}）只处理了
     * 合并模式下"库存列表"里的模板条目，从来没处理过请求栏本身——这里按
     * {@code orderClicked} 单独判断：点的是库存列表且当前是合并模式，交给
     * 上面两个方法处理，这里跳过；点的是请求栏本身，不管合并/分离模式，
     * 都统一在这里处理。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void createimp$handleFluidTemplateOrderClick(double mouseX, double mouseY, int button,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 && button != 1) {
            return;
        }
        Couple<Integer> hovered = this.getHoveredSlot((int) mouseX, (int) mouseY);
        if (hovered == this.noneHovered) {
            return;
        }
        int categoryIndex = hovered.getFirst();
        int itemIndex = hovered.getSecond();
        boolean orderClicked = categoryIndex == -1;
        if (!orderClicked && createimp$isMergeMode()) {
            return;
        }
        BigItemStack entry;
        if (orderClicked) {
            if (itemIndex < 0 || itemIndex >= this.itemsToOrder.size()) {
                return;
            }
            entry = this.itemsToOrder.get(itemIndex);
        } else if (categoryIndex == -2) {
            return;
        } else {
            if (categoryIndex < 0 || categoryIndex >= this.displayedItems.size()) {
                return;
            }
            List<BigItemStack> bucket = this.displayedItems.get(categoryIndex);
            if (itemIndex < 0 || itemIndex >= bucket.size()) {
                return;
            }
            entry = bucket.get(itemIndex);
        }
        if (!createimp$isFluidTemplateEntry(entry.stack)) {
            return;
        }
        cir.setReturnValue(true);
        int transfer = createimp$templateTransferStep(entry.stack, orderClicked);
        BigItemStack existingOrder = orderClicked ? entry : this.getOrderForItem(entry.stack);
        if (existingOrder == null) {
            if (button == 1 || this.itemsToOrder.size() >= 9) {
                return;
            }
            existingOrder = new BigItemStack(entry.stack.copyWithCount(1), 0);
            this.itemsToOrder.add(existingOrder);
        }
        int current = existingOrder.count;
        if (button == 1 || orderClicked) {
            existingOrder.count = current - transfer;
            if (existingOrder.count <= 0) {
                this.itemsToOrder.remove(existingOrder);
            }
            return;
        }
        existingOrder.count = current + Math.min(transfer, entry.count - current);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void createimp$handleFluidTemplateOrderScroll(double mouseX, double mouseY, double scrollX, double scrollY,
                                                          CallbackInfoReturnable<Boolean> cir) {
        Couple<Integer> hovered = this.getHoveredSlot((int) mouseX, (int) mouseY);
        if (hovered == this.noneHovered) {
            return;
        }
        int categoryIndex = hovered.getFirst();
        int itemIndex = hovered.getSecond();
        boolean orderClicked = categoryIndex == -1;
        if (!orderClicked && createimp$isMergeMode()) {
            return;
        }
        BigItemStack entry;
        if (orderClicked) {
            if (itemIndex < 0 || itemIndex >= this.itemsToOrder.size()) {
                return;
            }
            entry = this.itemsToOrder.get(itemIndex);
        } else if (categoryIndex == -2) {
            return;
        } else {
            if (categoryIndex < 0 || categoryIndex >= this.displayedItems.size()) {
                return;
            }
            List<BigItemStack> bucket = this.displayedItems.get(categoryIndex);
            if (itemIndex < 0 || itemIndex >= bucket.size()) {
                return;
            }
            entry = bucket.get(itemIndex);
        }
        if (!createimp$isFluidTemplateEntry(entry.stack)) {
            return;
        }
        cir.setReturnValue(true);
        boolean remove = scrollY < 0;
        int transfer = createimp$templateTransferStep(entry.stack, orderClicked);
        BigItemStack existingOrder = orderClicked ? entry : this.getOrderForItem(entry.stack);
        if (existingOrder == null) {
            if (this.itemsToOrder.size() >= 9 || remove) {
                return;
            }
            existingOrder = new BigItemStack(entry.stack.copyWithCount(1), 0);
            this.itemsToOrder.add(existingOrder);
        }
        int current = existingOrder.count;
        if (remove) {
            existingOrder.count = current - transfer;
            if (existingOrder.count <= 0) {
                this.itemsToOrder.remove(existingOrder);
            }
            return;
        }
        existingOrder.count = current + Math.min(transfer, entry.count - current);
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

    /**
     * 请求栏悬浮流体模板时，补一行"实际详细请求量"，跟流包给真实流体库存
     * 悬浮请求栏时加的那一行是同一个效果（只是数字含义换成模板令牌的
     * 请求量），用的也是流包同一个精确格式化方法。
     * <p>
     * 【为什么用 WrapOperation 而不是 Redirect】流包自己对这同一处
     * {@code GuiGraphics.renderTooltip} 调用已经打了一个 {@code @Redirect}
     * （用来给它自己的真实流体库存补详细请求量提示），一个调用点只能有一个
     * 普通 {@code @Redirect}，两边都写会冲突。MixinExtras 的
     * {@code @WrapOperation} 是专门设计成可以跟别的模组（包括普通
     * {@code @Redirect}）叠在同一个调用点上而不冲突的机制，流包自己另一处
     * 也在用 MixinExtras 的 {@code @WrapOperation}（见 {@code LogisticsManagerMixin}），
     * 说明这套机制在这个模组加载环境里是正常工作的。这里只处理"物品是我们
     * 自己的流体模板令牌"这一种情况，不满足条件时原样调用
     * {@code operation.call(...)}，交给原本该处理这个调用的一方（流包的
     * Redirect 或者原版本身）处理，不影响任何其他情况。
     */
    @WrapOperation(method = "renderForeground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V"))
    private void createimp$fluidTemplateOrderTooltip(GuiGraphics graphics, Font font, ItemStack stack, int mouseX, int mouseY,
                                                     Operation<Void> operation) {
        if (!createimp$isFluidTemplateEntry(stack)) {
            operation.call(graphics, font, stack, mouseX, mouseY);
            return;
        }
        Couple<Integer> hovered = this.getHoveredSlot(mouseX, mouseY);
        if (hovered == this.noneHovered || hovered.getFirst() != -1) {
            operation.call(graphics, font, stack, mouseX, mouseY);
            return;
        }
        int itemIndex = hovered.getSecond();
        if (itemIndex < 0 || itemIndex >= this.itemsToOrder.size()) {
            operation.call(graphics, font, stack, mouseX, mouseY);
            return;
        }
        BigItemStack orderEntry = this.itemsToOrder.get(itemIndex);
        List<Component> lines = new ArrayList<>();
        lines.add(stack.getHoverName());
        lines.add(CreateLang.text("x" + TemplateFluidDisplayHelper.formatPreciseAmount(orderEntry.count))
                .style(ChatFormatting.DARK_GRAY).component());
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }
}