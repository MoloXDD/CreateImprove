package com.molox.createimp.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.molox.createimp.CreateImp;
import com.molox.createimp.CreateImpConfig;
import com.molox.createimp.client.ClientWorkWarehouseAvailabilityCache;
import com.molox.createimp.client.TemplateOrderTooltipHandler;
import com.molox.createimp.compat.fluidlogistics.FluidLogisticsCompat;
import com.molox.createimp.compat.fluidlogistics.TemplateFluidDisplayHelper;
import com.molox.createimp.item.TemplateOrderTarget;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.molox.createimp.network.RequestTemplateMaterialsPacket;
import com.molox.createimp.network.RequestWorkWarehouseAvailabilityPacket;
import com.molox.createimp.util.StockKeeperRequestScreenInvoker;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yision.phantom.item.ticker.ClientScreenStorage;
import com.yision.phantom.item.ticker.TunablePortableTickerScreen;
import com.yision.phantom.item.ticker.access.TunablePortableTickerLocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.data.Couple;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
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

/**
 * 混入幻翼物流（CreatePhantom）可调频便携式发报机界面
 * （{@code TunablePortableTickerScreen}），使其能够展示与请求模板，并兼容
 * 本模组仓储管理员界面的各项配置（展示样式、合并展示）。
 * <p>
 * 幻翼物流是可选依赖，本类使用 {@code targets} 字符串而不是
 * {@code value = X.class}，原因与 {@link MixinFluidPackagerArrivalDedup}
 * 一致：避免 Mixin 处理本类注解时就去解析这个可选依赖类型。
 * <p>
 * 这个界面本身是幻翼物流自行重新实现的一套，不是对 Create 原版
 * {@code StockKeeperRequestScreen} 的继承或复用，因此这里的处理逻辑是
 * 参照 {@link MixinStockKeeperRequestScreen} 重新对照真实反编译结果实现的
 * 一份镜像，不是简单复制——两者字段名、方法签名、部分渲染细节均不完全
 * 一致，已逐一反编译核实。
 * <p>
 * 【与幻翼物流自身流体包裹兼容层的相互避让】幻翼物流自己内置了一套
 * {@code FluidLogisticsTickerCompat}，用来识别流体包裹的虚拟流体物品并
 * 接管提示框/点击步进/数量角标绘制。我们的流体模板令牌复用的正是同一个
 * 虚拟流体物品（只是多挂了模板数据组件），会被它的
 * {@code isPackageResourceStack} 一并误判为"这是一份流体包裹资源"。本类
 * 因此需要在提示框、点击、滚轮这几处都抢在它的判断之前短路掉，具体见
 * 各方法上的单独说明。
 * <p>
 * 【与频道切换的关系】幻翼这个界面同一时刻只会展示"当前选中频道"对应的
 * 那一个物流网络（{@code activeSessionNetwork}），切换频道时它自己会
 * 重新调用一遍 {@code refreshSearchResults(true)}，本类挂在这个方法尾部
 * 的分类置顶/合并逻辑因此会自动使用切换后的新网络重新计算，不需要额外
 * 监听频道切换事件。
 * 【类型引用的例外】{@code @Mixin} 注解本身用 {@code targets} 字符串避免
 * 提前解析这个可选依赖类型；但 {@code @Redirect}/{@code @WrapWithCondition}
 * 拦截"目标类自身实例方法调用"（比如 {@code this.sendIt()}）时，Mixin
 * 强制要求处理方法的接收者参数必须是目标类的真实类型，写成 {@code Object}
 * 反而会在校验阶段报错（已实测确认）。{@code TunablePortableTickerScreen}
 * 本身是 public 类，这里直接引用它的类型是安全且必须的，只是不会把它
 * 写进 {@code @Mixin} 注解值本身。
 */
@Mixin(targets = "com.yision.phantom.item.ticker.TunablePortableTickerScreen", remap = false)
public abstract class MixinTunablePortableTickerScreen implements StockKeeperRequestScreenInvoker {

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

    @Unique
    private static final int WORK_WAREHOUSE_POLL_TICKS = 15;

    @Unique
    private int createimp$workWarehousePollCooldown = 0;

    @Shadow
    public List<List<BigItemStack>> displayedItems;

    @Shadow
    public List categories;

    @Shadow
    private Set<Integer> hiddenCategories;

    @Shadow
    public List<BigItemStack> itemsToOrder;

    @Shadow
    public List recipesToOrder;

    @Shadow
    private UUID activeSessionNetwork;

    @Shadow
    private int activeChannel;

    @Shadow
    private native boolean isConfirmHovered(double mouseX, double mouseY);

    @Shadow
    private native void sendIt();

    @Shadow
    private native void revalidateOrders();

    @Shadow
    private native BigItemStack getOrderForItem(ItemStack stack);

    @Shadow
    private native Couple<Integer> getHoveredSlot(int x, int y);

    @Shadow
    private native void drawItemCount(GuiGraphics graphics, int count);

    /**
     * 请求确认材料后从材料检查窗口调用回来，这一路会连续发生两次库存
     * 刷新请求：{@code sendIt()} 自己内部末尾那次，紧接着材料窗口关闭
     * 返回触发的 {@code init()} 那次——两者几乎同一 tick 内前后脚发生，
     * 必然撞上服务端 10 tick 节流窗口，导致要么回复被后一次请求的新
     * 请求号顶掉丢弃，要么请求本身直接被服务端节流吞掉，界面因此会
     * 短暂显示空列表，最长要等 5 秒后 {@code containerTick} 自己的兜底
     * 轮询才会恢复（此前用延迟补发的方式缓解过，但没法完全消除这段
     * 空窗）。
     * <p>
     * 更彻底的做法是从源头避免两次请求同时发生：这里标记"接下来这次
     * {@code init()} 里的刷新请求应该被跳过"，只留 {@code sendIt()} 自己
     * 那次生效——它是这个时间窗口里唯一的请求，不会被节流，回复能立刻
     * 到达，界面不会经历"先空/先旧数据"的过渡。
     */
    @Unique
    private boolean createimp$suppressNextInitRefresh = false;

    @Override
    public void createimp$invokeSendIt() {
        this.sendIt();
        this.createimp$suppressNextInitRefresh = true;
    }

    /**
     * 精确拦截 {@code init()} 内部那一次 {@code ClientScreenStorage
     * .manualUpdate(...)} 调用：如果是紧跟在我们自己触发的
     * {@code sendIt()} 之后发生的，直接跳过不发这次请求，把机会完全让给
     * {@code sendIt()} 自己那次；其它情况下（正常打开设备、切换频道等）
     * 照常放行，不影响原有行为。
     */
    @Redirect(method = "init", at = @At(value = "INVOKE",
            target = "Lcom/yision/phantom/item/ticker/ClientScreenStorage;manualUpdate(Lcom/yision/phantom/item/ticker/access/TunablePortableTickerLocator;ILjava/util/UUID;)V"))
    private void createimp$maybeSkipInitRefresh(TunablePortableTickerLocator locator, int channel, UUID sessionNetwork) {
        if (this.createimp$suppressNextInitRefresh) {
            this.createimp$suppressNextInitRefresh = false;
            return;
        }
        ClientScreenStorage.manualUpdate(locator, channel, sessionNetwork);
    }

    @Override
    public void createimp$clearRequestBar() {
        this.itemsToOrder = new ArrayList<>();
        this.recipesToOrder = new ArrayList<>();
    }

    /**
     * 请求栏含有模板时，按固定节奏向服务端查询一次当前选中频道对应网络
     * 的可用工作仓库数量。做法与 {@link MixinStockKeeperRequestScreen
     * #createimp$pollWorkWarehouseAvailability} 完全一致，只是这里的
     * 网络来自 {@code activeSessionNetwork} 而不是仓管方块自己的频率。
     */
    @Override
    public void createimp$pollWorkWarehouseAvailability() {
        int templateCount = createimp$countTemplateEntries();
        if (templateCount == 0) {
            return;
        }
        if (this.activeSessionNetwork == null) {
            return;
        }
        if (createimp$workWarehousePollCooldown-- > 0) {
            return;
        }
        createimp$workWarehousePollCooldown = WORK_WAREHOUSE_POLL_TICKS;
        PacketDistributor.sendToServer(new RequestWorkWarehouseAvailabilityPacket(this.activeSessionNetwork));
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

    @Unique
    private boolean createimp$isTemplateSendBlocked() {
        int templateCount = createimp$countTemplateEntries();
        if (templateCount == 0) {
            return false;
        }
        if (this.activeSessionNetwork == null) {
            return true;
        }
        int available = ClientWorkWarehouseAvailabilityCache.get(this.activeSessionNetwork);
        return available < templateCount;
    }

    /**
     * 与 {@link MixinStockKeeperRequestScreen#createimp$excludeTemplateTokenFromCraftableMatch}
     * 对应的修复，但这里只需要一处：幻翼把"配方材料匹配"统一收在
     * {@code getMatchingStacks} 这一个私有方法里，同时被自己配方图标的
     * +/-号（经由 {@code maxCraftable}/{@code requestCraftable}）和 JEI
     * 配方界面的+号（经由 {@code getTransferCandidates}）共用，不需要像
     * 原版仓管那样再单独处理 JEI 那一侧。
     */
    @Redirect(method = "getMatchingStacks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/Ingredient;test(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean createimp$excludeTemplateTokenFromMatch(Ingredient instance, ItemStack stack) {
        if (TemplateOrderTokenHelper.isToken(stack)) {
            return false;
        }
        return instance.test(stack);
    }

    /**
     * 确认键悬浮高亮：请求栏含模板且可用工作仓库数量不足时不予高亮，
     * 效果与原版仓管一致。
     * <p>
     * 【重要修正说明】这里最初直接在 {@link #isConfirmHovered} 方法本身
     * 上做 {@code @Inject(at=RETURN)} 改写返回值，问题在于：这样一来
     * 任何调用这个方法的地方都会拿到"被工作仓库数量拦截过"的结果——
     * 包括 {@link #createimp$drawWorkWarehouseTooltip} 自己内部那句
     * "是否悬浮在确认键上"的判断，导致恰好在最需要弹出"工作仓库数量
     * 不足"提示的那一刻，判断被自己搞成"根本没悬浮"，提示直接消失。
     * <p>
     * 改成和原版仓管一致的做法：分别 {@code @Redirect}
     * {@code renderBg}/{@code mouseClicked} 里两处具体的调用点（反编译
     * 确认幻翼这两个方法内部都是直接内联 {@code this.isConfirmHovered
     * (mouseX, mouseY)}），保持 {@link #isConfirmHovered} 方法本身不被
     * 改写，其它调用方（比如提示框方法）拿到的还是真实的悬浮判断结果。
     */
    @Redirect(method = "renderBg", at = @At(value = "INVOKE",
            target = "Lcom/yision/phantom/item/ticker/TunablePortableTickerScreen;isConfirmHovered(DD)Z"))
    private boolean createimp$redirectConfirmHoveredRender(TunablePortableTickerScreen self, double mouseX, double mouseY) {
        return this.isConfirmHovered(mouseX, mouseY) && !createimp$isTemplateSendBlocked();
    }

    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE",
            target = "Lcom/yision/phantom/item/ticker/TunablePortableTickerScreen;isConfirmHovered(DD)Z"))
    private boolean createimp$redirectConfirmHoveredClick(TunablePortableTickerScreen self, double mouseX, double mouseY) {
        return this.isConfirmHovered(mouseX, mouseY) && !createimp$isTemplateSendBlocked();
    }


    /**
     * 悬浮确认键时，如果请求栏含有模板，额外展示"可用工作仓库数量"这行
     * 提示（数量不足时再加一行红字警告），与原版仓管
     * {@code createimp$drawWorkWarehouseTooltip} 完全一致。之前漏掉了
     * 这一个方法。
     */
    @Inject(method = "renderForeground", at = @At("TAIL"))
    private void createimp$drawWorkWarehouseTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
                                                    CallbackInfo ci) {
        if (!this.isConfirmHovered(mouseX, mouseY)) {
            return;
        }
        int templateCount = createimp$countTemplateEntries();
        if (templateCount == 0) {
            return;
        }
        int availableCount = ClientWorkWarehouseAvailabilityCache.get(this.activeSessionNetwork);
        List<Component> lines = new ArrayList<>();
        if (availableCount < templateCount) {
            lines.add(Component.translatable("createimp.gui.stock_keeper.not_enough_work_warehouse"));
        }
        lines.add(Component.translatable("createimp.gui.stock_keeper.work_warehouse_available",
                Math.max(0, availableCount)));
        graphics.renderComponentTooltip(net.minecraft.client.Minecraft.getInstance().font, lines, mouseX, mouseY);
    }

    /**
     * 点击确认键真正触发的发送动作：请求栏内不含模板时行为与原版完全
     * 一致；含模板时（此时已经确认工作仓库数量足够，否则上面对
     * {@code mouseClicked} 里 {@code isConfirmHovered} 调用点的 Redirect
     * 会让点击根本走不到这里），改为向服务端请求一次材料计算，不立即
     * 真正发送。
     */
    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE",
            target = "Lcom/yision/phantom/item/ticker/TunablePortableTickerScreen;sendIt()V"))
    private void createimp$redirectSendIt(TunablePortableTickerScreen self) {
        if (createimp$countTemplateEntries() == 0) {
            this.sendIt();
            return;
        }
        this.revalidateOrders();
        if (this.itemsToOrder.isEmpty() || this.activeSessionNetwork == null) {
            CreateImp.LOGGER.info(
                    "[模板材料] 便携发报机放弃发送材料请求：请求栏为空={}, 当前网络为空={}",
                    this.itemsToOrder.isEmpty(), this.activeSessionNetwork == null);
            return;
        }
        CreateImp.LOGGER.info("[模板材料] 便携发报机发送材料计算请求：网络={}, 请求栏条目数={}",
                this.activeSessionNetwork, this.itemsToOrder.size());
        PacketDistributor.sendToServer(new RequestTemplateMaterialsPacket(
                this.activeSessionNetwork, new ArrayList<>(this.itemsToOrder)));
    }

    @Unique
    private static boolean createimp$isMergeMode() {
        return CreateImp.getConfig().templateFunctionConfig.mergeTemplateWithStock;
    }

    @Unique
    private static final String CATEGORY_ENTRY_CLASS_NAME =
            "com.yision.phantom.item.ticker.TunablePortableTickerScreen$CategoryEntry";

    /**
     * 反射构造一个新的 {@code CategoryEntry} 实例。原本想用 Mixin 的
     * {@code @Invoker("<init>")} 做（见 {@link PhantomCategoryEntryAccessor}
     * 类注释），但实测确认 Mixin 的构造器 Invoker 要求返回类型必须与目标
     * 类型精确匹配，私有嵌套类没法在源码里写出真实类型来满足这一要求，
     * 因此改用普通反射：只在这个方法真正被调用时（也就是幻翼物流确实
     * 已加载、这个界面确实被打开时）才会触发 {@code Class.forName}，
     * 与本类其余部分"只在目标类被真正用到时才接触幻翼类型"的安全模型
     * 一致。
     */
    @Unique
    private static Object createimp$newCategoryEntry(int targetCategory, String name) {
        try {
            Class<?> clazz = Class.forName(CATEGORY_ENTRY_CLASS_NAME);
            java.lang.reflect.Constructor<?> ctor = clazz.getDeclaredConstructor(int.class, String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(targetCategory, name);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("无法反射构造幻翼CategoryEntry实例", e);
        }
    }

    /**
     * 分离模式：把模板令牌从原有分类桶里摘出来，合并组成一个置顶插入的
     * "模板"分类。由于 {@code CategoryEntry} 是私有内部类，这里通过
     * {@link #createimp$newCategoryEntry} 反射构造新实例，再通过
     * {@link PhantomCategoryEntryAccessor} 读写其字段，不能像对 Create
     * 原版那样直接 {@code new}。
     */
    @Inject(method = "refreshSearchResults", at = @At("RETURN"))
    private void createimp$pinTemplateCategory(boolean scrollBackUp, CallbackInfo ci) {
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

        List<Object> newCategories = new ArrayList<>();
        List<List<BigItemStack>> newDisplayedItems = new ArrayList<>();

        Object templateCategory = createimp$newCategoryEntry(
                TEMPLATE_CATEGORY_ID, Component.translatable("createimp.gui.stock_keeper.template_category").getString());
        PhantomCategoryEntryAccessor templateAccessor = (PhantomCategoryEntryAccessor) templateCategory;
        templateAccessor.createimp$setHidden(this.hiddenCategories.contains(TEMPLATE_CATEGORY_ID));
        newCategories.add(templateCategory);
        newDisplayedItems.add(templateBucket);

        if (this.categories.isEmpty()) {
            List<BigItemStack> leftover = new ArrayList<>();
            for (List<BigItemStack> bucket : filteredDisplayedItems) {
                leftover.addAll(bucket);
            }
            if (!leftover.isEmpty()) {
                Object unsortedCategory = createimp$newCategoryEntry(
                        -1, CreateLang.translate("gui.stock_keeper.unsorted_category").string());
                PhantomCategoryEntryAccessor unsortedAccessor = (PhantomCategoryEntryAccessor) unsortedCategory;
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
    @SuppressWarnings("unchecked")
    private void createimp$recomputeCategoryLayout() {
        int categoryY = 0;
        for (int i = 0; i < this.categories.size(); ++i) {
            PhantomCategoryEntryAccessor accessor = (PhantomCategoryEntryAccessor) this.categories.get(i);
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
     * 合并模式：每个分类桶内部原地把模板条目挪到最前面，同展示物的普通
     * 条目不再单独出现。逻辑与 {@link MixinStockKeeperRequestScreen
     * #createimp$mergeTemplatesIntoCategories} 完全一致。
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

    /**
     * 请求栏（orderRow=true）背景：幻翼原版这一情况下完全不绘制任何背景
     * 贴图（反编译确认，与 Create 原版不同），模板请求栏条目需要单独补上。
     */
    @Inject(method = "renderItemEntry", at = @At("HEAD"))
    private void createimp$drawTemplateOrderBackground(GuiGraphics graphics, BigItemStack entry,
                                                       boolean hovered, boolean orderRow, CallbackInfo ci) {
        if (orderRow && !createimp$isStyle2() && TemplateOrderTokenHelper.isToken(entry.stack)) {
            graphics.blit(TEMPLATE_REQUEST_SLOT_BG, 0, 0, 0, 0, 18, 18, 18, 18);
        }
    }

    /**
     * 上方库存列表（orderRow=false）背景：幻翼原版在这一情况下直接调用
     * {@code AllGuiTextures.STOCK_KEEPER_REQUEST_SLOT.render(graphics, 0, 0)}，
     * 模板条目改用专属贴图替换这次调用。
     */
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
     * 样式2：模板贴图改为绘制在物品图标之上的前景，并跟随物品一起缩放。
     * 幻翼这里图标缩放变换只有"悬浮放大 7.5%"这一层（反编译确认 renderItemEntry
     * 内部只有 {@code float scale = hovered ? 1.075f : 1.0f;} 这一个局部变量，
     * 不像 Create 原版那样还额外接收一个外部传入的缩放参数），因此这里只
     * 复现这一层变换，不需要（也没有）额外的外部 scale 相乘。
     */
    @Inject(method = "renderItemEntry", at = @At("TAIL"))
    private void createimp$drawTemplateForeground(GuiGraphics graphics, BigItemStack entry,
                                                  boolean hovered, boolean orderRow, CallbackInfo ci) {
        if (!createimp$isStyle2() || !TemplateOrderTokenHelper.isToken(entry.stack)) {
            return;
        }
        ResourceLocation texture = orderRow ? TEMPLATE_REQUEST_SLOT_BG_2 : TEMPLATE_SLOT_BG_2;
        float scale = hovered ? 1.075f : 1.0f;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(1.0, 1.0, 0.0);
        pose.translate(9.0, 9.0, 0.0);
        pose.scale(scale, scale, scale);
        pose.translate(-9.0, -9.0, 0.0);
        pose.translate(0.0, 0.0, 150.0);
        graphics.blit(texture, -1, -1, 0, 0, 18, 18, 18, 18);
        pose.popPose();
    }

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
     * 样式1（背景样式）下，流体虚拟物品自己的图标渲染会铺满整个图标区域，
     * 把模板背景贴图整个遮住。重新画一遍背景贴图四条窄边框，缩放变换同样
     * 只有"悬浮放大 7.5%"这一层，理由同上。
     */
    @Unique
    private static final int FLUID_TEMPLATE_BORDER_MARGIN = 3;

    @Inject(method = "renderItemEntry", at = @At("TAIL"))
    private void createimp$drawFluidTemplateBorderStyle1(GuiGraphics graphics, BigItemStack entry,
                                                         boolean hovered, boolean orderRow, CallbackInfo ci) {
        if (createimp$isStyle2() || !createimp$isFluidTemplateEntry(entry.stack)) {
            return;
        }
        ResourceLocation texture = orderRow ? TEMPLATE_REQUEST_SLOT_BG : TEMPLATE_SLOT_BG;
        float scale = hovered ? 1.075f : 1.0f;
        int mTop = FLUID_TEMPLATE_BORDER_MARGIN - 1;
        int mLeft = FLUID_TEMPLATE_BORDER_MARGIN - 1;
        int mRight = FLUID_TEMPLATE_BORDER_MARGIN - 1;
        int mBottom = FLUID_TEMPLATE_BORDER_MARGIN - 1;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(1.0, 1.0, 0.0);
        pose.translate(9.0, 9.0, 0.0);
        pose.scale(scale, scale, scale);
        pose.translate(-9.0, -9.0, 0.0);
        pose.translate(0.0, 0.0, 150.0);
        graphics.blit(texture, -1, -1, 0, 0, 18, mTop, 18, 18);
        graphics.blit(texture, -1, 17 - mBottom, 0, 18 - mBottom, 18, mBottom, 18, 18);
        graphics.blit(texture, -1, -1 + mTop, 0, mTop, mLeft, 18 - mTop - mBottom, 18, 18);
        graphics.blit(texture, 17 - mRight, -1 + mTop, 18 - mRight, mTop, mRight, 18 - mTop - mBottom, 18, 18);
        pose.popPose();
    }

    /**
     * 屏蔽上方库存列表模板条目的数量角标；请求栏模板条目里，普通模板保留
     * 原版数字角标，流体模板改由 {@link #createimp$drawFluidOrderBadge}
     * 接手绘制 mB 格式角标。
     * <p>
     * 必须用 {@code @WrapWithCondition} 而不是 {@code @Inject(cancellable)}
     * ——后者只能取消整个 {@code renderItemEntry} 方法，会连带跳过后续
     * 所有渲染；前者可以精确跳过这一次具体的 {@code drawItemCount} 调用，
     * 不影响方法其余部分。
     */
    @WrapWithCondition(method = "renderItemEntry", at = @At(value = "INVOKE",
            target = "Lcom/yision/phantom/item/ticker/TunablePortableTickerScreen;drawItemCount(Lnet/minecraft/client/gui/GuiGraphics;I)V"))
    private boolean createimp$hideTemplateItemCount(TunablePortableTickerScreen self, GuiGraphics graphics, int count,
                                                    @Local(argsOnly = true) BigItemStack entry,
                                                    @Local(argsOnly = true, ordinal = 1) boolean orderRow) {
        if (!TemplateOrderTokenHelper.isToken(entry.stack)) {
            return true;
        }
        if (orderRow) {
            return !createimp$isFluidTemplateEntry(entry.stack);
        }
        return false;
    }

    @Inject(method = "renderItemEntry", at = @At("TAIL"))
    private void createimp$drawFluidOrderBadge(GuiGraphics graphics, BigItemStack entry,
                                               boolean hovered, boolean orderRow, CallbackInfo ci) {
        if (!orderRow || !createimp$isFluidTemplateEntry(entry.stack) || entry.count <= 1) {
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0, 0.0, 200.0);
        TemplateFluidDisplayHelper.renderFluidAmountBadge(graphics, entry.count);
        pose.popPose();
    }

    /**
     * 合并模式下，库存列表里模板条目的角标不用令牌自己携带的占位数量
     * （被 {@link #createimp$hideTemplateItemCount} 屏蔽掉了），而是现算
     * "这个展示物当前的真实库存减去请求栏里已经占用的真实物品数量"，
     * 与 {@link MixinStockKeeperRequestScreen#createimp$drawMergedTemplateStockBadge}
     * 完全一致。之前漏掉了这一处，导致合并模式下库存列表的模板条目
     * （不论是否流体）全都不显示角标——流体的尤其明显，因为流体虚拟物品
     * 图标本身不会像普通物品那样有任何默认角标兜底。
     */
    @Inject(method = "renderItemEntry", at = @At("TAIL"))
    private void createimp$drawMergedTemplateStockBadge(GuiGraphics graphics, BigItemStack entry,
                                                        boolean hovered, boolean orderRow, CallbackInfo ci) {
        if (orderRow || !createimp$isMergeMode() || !TemplateOrderTokenHelper.isToken(entry.stack)) {
            return;
        }
        TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(entry.stack);
        if (target == null) {
            return;
        }
        int liveStock = this.getLatestSummary().getCountOf(target.display());
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
            this.drawItemCount(graphics, liveCustom);
        }
        pose.popPose();
    }

    /**
     * 重新按 {@code renderForeground} 里同样的规则算一次"当前悬浮的是哪个
     * 条目"，只用于悬浮提示判断。不使用 {@code @Local} 去抓方法内部局部
     * 变量——那需要精确对应反编译结果里三元表达式展开出的临时变量槽位，
     * 稳定性不如自己重新算一遍可靠；这里唯一依赖的
     * {@link #getHoveredSlot} 是本类已有的 shadow 方法，逻辑与原方法内部
     * 完全一致。
     */
    @Unique
    private BigItemStack createimp$hoveredEntryForTooltip(int mouseX, int mouseY) {
        Couple<Integer> hovered = this.getHoveredSlot(mouseX, mouseY);
        if (hovered.getFirst() == -1 && hovered.getSecond() == -1) {
            return null;
        }
        int categoryIndex = hovered.getFirst();
        int itemIndex = hovered.getSecond();
        if (categoryIndex == -2) {
            // 配方蓝图槽位（recipesToOrder），模板令牌不会出现在这里。
            return null;
        }
        if (categoryIndex == -1) {
            if (itemIndex < 0 || itemIndex >= this.itemsToOrder.size()) {
                return null;
            }
            return this.itemsToOrder.get(itemIndex);
        }
        if (categoryIndex < 0 || categoryIndex >= this.displayedItems.size()) {
            return null;
        }
        List<BigItemStack> bucket = this.displayedItems.get(categoryIndex);
        if (itemIndex < 0 || itemIndex >= bucket.size()) {
            return null;
        }
        return bucket.get(itemIndex);
    }

    /**
     * 【重要修正说明】这里最初的实现是错的：不管流体还是非流体模板，
     * 悬浮库存列表/请求栏都被硬编码成"只显示名称一行"，完全绕开了
     * {@link TemplateOrderTooltipHandler} 的全局 {@code ItemTooltipEvent}
     * 监听——导致合并模式的斜体名称提示、"使用材料 A B C 的配方"这一行、
     * 持有桌布类物品时"当前无法请求模板"这一行统统消失了。
     * <p>
     * 正确的设计（和原版仓管 {@code MixinStockKeeperRequestScreen} 完全
     * 一致）应该是：**默认什么都不拦截**——非流体模板令牌走到的就是幻翼
     * 自己那次通用的 {@code graphics.renderTooltip(font, entry.stack, x, y)}
     * 调用，这个方法内部本来就会调用 {@code ItemStack.getTooltipLines(...)}
     * 从而正常触发 {@code ItemTooltipEvent}，{@link TemplateOrderTooltipHandler}
     * 会自动介入修改提示内容，完全不需要我们插手。
     * <p>
     * 真正需要特殊处理的只有一种情况：**流体模板令牌**会被幻翼自己
     * "是不是流体包裹资源"的判断命中（复用的是同一个虚拟流体物品），
     * 走到它自己 {@code FluidLogisticsTickerCompat.tooltipLines(...)} 生成
     * 的一份提示、再调用 {@code renderComponentTooltip}——这条路径完全绕开
     * 了 {@code getTooltipLines}/事件管线，我们的 tooltip handler 天然介入
     * 不了，必须在这里补救。补救方式按悬浮位置区分（与原版仓管的
     * {@code createimp$fluidTemplateOrderTooltip} 完全一致的行为）：
     * <ul>
     *   <li>悬浮请求栏：只显示"名称 + 精确 mB 数值"两行，不经过事件管线
     *       （原版对这个场景就是这么处理的，请求栏语境不需要"重复配方"
     *       之类的提示）。</li>
     *   <li>悬浮库存列表：手动调用 {@code Screen.getTooltipFromItem(...)}
     *       ——这正是 {@code GuiGraphics.renderTooltip} 内部使用的同一个
     *       工具方法，会照常触发 {@code ItemTooltipEvent}，从而让
     *       {@link TemplateOrderTooltipHandler} 的增强内容正常生效，
     *       再把结果交给 {@code renderComponentTooltip} 画出来。</li>
     * </ul>
     * 完全不触碰幻翼自己的 {@code FluidLogisticsTickerCompat}——那是它
     * 自己包下的类型，本类作为可选依赖 mixin 不应该在任何方法体里引用
     * 幻翼的具体类型（哪怕是它自己的兼容层），这里用到的
     * {@code GuiGraphics}/{@code Font}/{@code Screen} 都是原版类型，安全。
     */
    @WrapOperation(method = "renderForeground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"))
    private void createimp$templateResourceTooltip(GuiGraphics graphics, Font font, List<Component> lines, int mouseX, int mouseY,
                                                   Operation<Void> operation) {
        BigItemStack hoveredEntry = createimp$hoveredEntryForTooltip(mouseX, mouseY);
        if (hoveredEntry == null || !createimp$isFluidTemplateEntry(hoveredEntry.stack)) {
            operation.call(graphics, font, lines, mouseX, mouseY);
            return;
        }
        Couple<Integer> hovered = this.getHoveredSlot(mouseX, mouseY);
        boolean orderBarHovered = hovered.getFirst() == -1;
        if (orderBarHovered) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(hoveredEntry.stack.getHoverName());
            tooltip.add(CreateLang.text("x" + TemplateFluidDisplayHelper.formatPreciseAmount(hoveredEntry.count))
                    .style(ChatFormatting.DARK_GRAY).component());
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return;
        }
        List<Component> enriched = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(
                net.minecraft.client.Minecraft.getInstance(), hoveredEntry.stack);
        graphics.renderComponentTooltip(font, enriched, mouseX, mouseY);
    }

    @Unique
    private static int createimp$templateTransferStep(ItemStack stack, boolean orderBar) {
        if (createimp$isFluidTemplateEntry(stack)) {
            return orderBar
                    ? TemplateFluidDisplayHelper.orderBarFluidStep(Screen.hasShiftDown(), Screen.hasControlDown())
                    : TemplateFluidDisplayHelper.stockKeeperFluidStep(Screen.hasShiftDown(), Screen.hasControlDown());
        }
        return Screen.hasShiftDown() ? stack.getMaxStackSize() : (Screen.hasControlDown() ? 10 : 1);
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

    @Unique
    private int createimp$remainingStock(TemplateOrderTarget target) {
        int liveStock = this.createimp$getLatestSummary().getCountOf(target.display());
        BigItemStack realOrder = this.getOrderForItem(target.display());
        int already = realOrder != null ? realOrder.count : 0;
        return liveStock - already;
    }

    @Shadow
    private native InventorySummary getLatestSummary();

    @Unique
    private InventorySummary createimp$getLatestSummary() {
        return this.getLatestSummary();
    }

    /**
     * 点击处理：合并模式下点击库存列表模板条目、以及非合并模式下点击
     * 流体模板条目（点击/滚轮步进），都必须抢在幻翼自身
     * {@code FluidLogisticsTickerCompat.isPackageResourceStack} 判断分支
     * 之前短路掉——那个分支对我们的流体模板令牌同样会返回 true（复用的
     * 是同一个虚拟流体物品），如果不抢先处理，会被它当成一份普通的流体
     * 包裹资源来处理点击，行为不正确。挂在 {@code @At("HEAD")} 且
     * {@code cancellable}，与 Create 原版仓管界面的处理顺序心智一致
     * （见 {@link MixinStockKeeperRequestScreen#createimp$handleMergedTemplateClick}）。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void createimp$handleTemplateClick(double mouseX, double mouseY, int button,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 && button != 1 && button != 2) {
            return;
        }
        Couple<Integer> hovered = this.getHoveredSlot((int) mouseX, (int) mouseY);
        if (hovered.getFirst() == -1 && hovered.getSecond() == -1) {
            return;
        }
        int categoryIndex = hovered.getFirst();
        int itemIndex = hovered.getSecond();
        boolean orderClicked = categoryIndex == -1;
        boolean mergeMode = createimp$isMergeMode();

        if (!orderClicked) {
            if (categoryIndex == -2) {
                return;
            }
            if (!mergeMode) {
                // 分离模式下，库存列表里的模板已经被摘到独立的"模板"分类，
                // 走原版通用点击逻辑即可正常识别为模板令牌本身，这里只
                // 处理流体步进（见下方公共分支），不需要额外处理。
            } else {
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
                return;
            }
        }

        // 公共分支：非合并模式下的库存列表模板条目、以及请求栏本身的模板
        // 条目，只要是流体模板就在这里接管步进（button 只有 0/1 有意义）。
        if (button != 0 && button != 1) {
            return;
        }
        BigItemStack entry;
        if (orderClicked) {
            if (itemIndex < 0 || itemIndex >= this.itemsToOrder.size()) {
                return;
            }
            entry = this.itemsToOrder.get(itemIndex);
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
    private void createimp$handleTemplateScroll(double mouseX, double mouseY, double scrollX, double scrollY,
                                                CallbackInfoReturnable<Boolean> cir) {
        Couple<Integer> hovered = this.getHoveredSlot((int) mouseX, (int) mouseY);
        if (hovered.getFirst() == -1 && hovered.getSecond() == -1) {
            return;
        }
        int categoryIndex = hovered.getFirst();
        int itemIndex = hovered.getSecond();
        boolean orderClicked = categoryIndex == -1;
        boolean mergeMode = createimp$isMergeMode();

        if (!orderClicked && mergeMode && categoryIndex != -2) {
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
            return;
        }

        if (categoryIndex == -2) {
            return;
        }
        BigItemStack entry;
        if (orderClicked) {
            if (itemIndex < 0 || itemIndex >= this.itemsToOrder.size()) {
                return;
            }
            entry = this.itemsToOrder.get(itemIndex);
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
}