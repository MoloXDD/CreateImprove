package com.molox.createimp.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.molox.createimp.CreateImp;
import com.molox.createimp.client.ClientWorkWarehouseAvailabilityCache;
import com.molox.createimp.compat.fluidlogistics.FluidLogisticsCompat;
import com.molox.createimp.compat.fluidlogistics.TemplateFluidDisplayHelper;
import com.molox.createimp.network.RequestWorkWarehouseAvailabilityPacket;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.molox.createimp.network.OpenTemplateMaterialsGuiPacket;
import com.molox.createimp.network.RequestTemplateMaterialsPacket;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * "模板所需材料"次级窗口。背景沿用网络管理器窗口同一套三段式贴图拼接方式，
 * 中间"所需材料"区域可滚动，展示服务端计算好的"缺少材料"（仅在无法全部完成时显示）
 * 与"现有材料"两个分组；打开期间会持续监控这些材料在物流网络内的库存变化，
 * 一旦变化就重新向服务端请求一次计算并原地刷新结果。
 */
public class TemplateMaterialsScreen extends AbstractSimiScreen {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/template_materials_screen.png");
    private static final ResourceLocation CANCEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/cancel.png");
    private static final int TEXTURE_W = 256;
    private static final int TEXTURE_H = 256;

    // 与网络管理器窗口完全相同的贴图坐标，保证视觉一致
    private static final int HEADER_SRC_X = 32;
    private static final int HEADER_SRC_Y = 0;
    private static final int BODY_SRC_X = 32;
    private static final int BODY_SRC_Y = 32;
    private static final int FOOTER_SRC_X = 32;
    private static final int FOOTER_SRC_Y = 79;

    private static final int BODY_REPEAT = 4;

    private static final int GUI_WIDTH = AllGuiTextures.STOCK_KEEPER_CATEGORY.getWidth();
    private static final int HEADER_H = AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.getHeight();
    private static final int BODY_H = AllGuiTextures.STOCK_KEEPER_CATEGORY.getHeight();
    private static final int FOOTER_H = AllGuiTextures.STOCK_KEEPER_CATEGORY_FOOTER.getHeight();
    private static final int GUI_HEIGHT = HEADER_H + BODY_H * BODY_REPEAT + FOOTER_H;

    private static final int SCISSOR_X = 3;
    private static final int SCISSOR_Y = 16;
    private static final int SCISSOR_X2 = GUI_WIDTH - 5;
    private static final int SCISSOR_Y2 = 19 + BODY_H * BODY_REPEAT;
    private static final int CONTENT_START_Y = 22;

    private static final int ITEM_SIZE = 18;
    private static final int ITEM_GAP = 2;
    private static final int ITEM_STEP = ITEM_SIZE + ITEM_GAP;
    private static final int ITEMS_PER_ROW = 9;
    private static final int GRID_MARGIN_X = (GUI_WIDTH - (ITEMS_PER_ROW * ITEM_SIZE + (ITEMS_PER_ROW - 1) * ITEM_GAP)) / 2;
    private static final int ROW_STEP = 20;
    private static final int SECTION_HEADER_H = 14;
    private static final int SECTION_GAP = 6;

    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int MISSING_TEXT_COLOR = 0xFF5555;

    /**
     * ↓↓↓ 取消键位置，可自行修改 ↓↓↓
     * 这两组是相对于窗口右下角(guiLeft+GUI_WIDTH, guiTop+GUI_HEIGHT)的像素偏移。
     * ALONE：窗口内只有取消键时（无法完成所有模板）使用。
     * WITH_CONFIRM：取消键与确认键并排显示时（可以完成所有模板）使用。
     */
    private static final int CANCEL_BUTTON_X_OFFSET_ALONE = 25;
    private static final int CANCEL_BUTTON_X_OFFSET_WITH_CONFIRM = 54;
    private static final int CANCEL_BUTTON_Y_OFFSET = 25;
    // ↑↑↑ 取消键位置，可自行修改 ↑↑↑

    private static final ScreenElement CANCEL_ICON = (graphics, x, y) ->
            graphics.blit(CANCEL_TEXTURE, x - 1, y - 1, 0, 0, 18, 18, 18, 18);

    /**
     * 库存变化监控：每隔多少 tick 检查一次是否需要刷新客户端库存快照并重新
     * 计算材料（与原版仓管界面自身使用的节奏一致）。这一步开销较大（涉及
     * 递归遍历配方链和库存查询），所以只在检测到跟踪物品的数量真的变化了
     * 才会重新请求完整计算。
     */
    private static final int STOCK_POLL_TICKS = 15;

    /**
     * 向服务端查询工作仓库可用数量的轮询节奏，与上面的库存监控保持同一量级。
     */
    private static final int WORK_WAREHOUSE_POLL_TICKS = 15;

    private boolean canCompleteAll;
    private List<BigItemStack> missing;
    private List<BigItemStack> usedFromStock;
    private final LerpedFloat scroll;
    private UUID freqId;
    private int templateCount;
    private final BlockPos stockTickerPos;
    private final List<BigItemStack> originalItemsToOrder;
    private final net.minecraft.client.gui.screens.Screen previousScreen;

    private IconButton confirmButton;
    private IconButton cancelButton;

    private final List<TrackedItem> trackedItems = new ArrayList<>();
    private int stockPollCooldown;
    private int workWarehousePollCooldown;

    public TemplateMaterialsScreen(OpenTemplateMaterialsGuiPacket packet, net.minecraft.client.gui.screens.Screen previousScreen) {
        super(Component.translatable("createimp.gui.template_materials.title"));
        this.canCompleteAll = packet.completionState().canCompleteAll();
        this.missing = new ArrayList<>(packet.missing());
        this.usedFromStock = new ArrayList<>(packet.usedFromStock());
        this.scroll = LerpedFloat.linear().startWithValue(0);
        this.freqId = packet.freqId();
        this.templateCount = packet.templateCount();
        this.stockTickerPos = packet.requestContext().stockTickerPos();
        this.originalItemsToOrder = new ArrayList<>(packet.requestContext().itemsToOrder());
        this.previousScreen = previousScreen;
        this.rebuildTrackedItems();
    }

    public static void open(OpenTemplateMaterialsGuiPacket packet) {
        net.minecraft.client.gui.screens.Screen previous = Minecraft.getInstance().screen;
        ScreenOpener.open(new TemplateMaterialsScreen(packet, previous));
    }

    /**
     * 收到服务端重新计算后的结果时调用（定期轮询触发的刷新），原地更新
     * 展示内容与按钮状态，不重新打开窗口、不影响滚动位置与返回栈。
     * 调用前 {@link OpenTemplateMaterialsGuiPacket.CompletionState#anyChainBroken()}
     * 已经在 {@link OpenTemplateMaterialsGuiPacket#handle} 里判断过是 false，
     * 这里不需要重复处理链失效的情况。
     */
    public void applyResult(OpenTemplateMaterialsGuiPacket packet) {
        this.canCompleteAll = packet.completionState().canCompleteAll();
        this.missing = new ArrayList<>(packet.missing());
        this.usedFromStock = new ArrayList<>(packet.usedFromStock());
        this.freqId = packet.freqId();
        this.templateCount = packet.templateCount();
        this.rebuildTrackedItems();
        this.rebuildButtons();
        this.clampScrollToContent();
    }

    /**
     * 服务端检测到本次请求里有任意一个模板的链已经失效（仪表被拆除、
     * 所在区块卸载、连接/地址被清空等）时调用：清空仓管界面的请求栏，
     * 然后退回该界面，不再展示这份已经过期的材料计算结果。
     */
    public void handleChainBroken() {
        if (previousScreen instanceof com.molox.createimp.util.StockKeeperRequestScreenInvoker invoker) {
            invoker.createimp$clearRequestBar();
        }
        onClose();
    }

    /**
     * 内容行数因库存变化（材料变少甚至整个分组消失）而缩短时，立即把滚动位置
     * 收紧到新的合法范围内，避免出现"滚轮停在原先靠下的位置、内容却已经变短，
     * 导致画面空白且滚轮卡住无法回滚"的情况。
     */
    private void clampScrollToContent() {
        int max = getMaxScroll(buildRows());
        float clamped = Mth.clamp(scroll.getChaseTarget(), 0, max);
        scroll.startWithValue(clamped);
    }

    @Override
    protected void init() {
        setWindowSize(GUI_WIDTH, GUI_HEIGHT);
        super.init();
        rebuildButtons();
    }

    private void rebuildButtons() {
        if (confirmButton != null) {
            removeWidget(confirmButton);
            confirmButton = null;
        }
        if (cancelButton != null) {
            removeWidget(cancelButton);
            cancelButton = null;
        }

        if (canCompleteAll) {
            confirmButton = new IconButton(
                    guiLeft + GUI_WIDTH - 25,
                    guiTop + GUI_HEIGHT - 25,
                    18, 18,
                    AllIcons.I_CONFIRM
            );
            confirmButton.withCallback(this::createimp$confirmAndReturn);
            addRenderableWidget(confirmButton);
        }

        int cancelXOffset = canCompleteAll ? CANCEL_BUTTON_X_OFFSET_WITH_CONFIRM : CANCEL_BUTTON_X_OFFSET_ALONE;
        cancelButton = new IconButton(
                guiLeft + GUI_WIDTH - cancelXOffset,
                guiTop + GUI_HEIGHT - CANCEL_BUTTON_Y_OFFSET,
                18, 18,
                CANCEL_ICON
        );
        cancelButton.withCallback(this::onClose);
        addRenderableWidget(cancelButton);
    }

    /**
     * 确认键：材料足够、且此刻工作仓库数量仍然足够（active 已经保证了这点）
     * 时才可能被点到。重新触发一次原本仓管界面真实的发送逻辑——里面已经有
     * 完整的打包/编程红石请求器流程、发送成功动画与音效，不需要在这里重做，
     * 然后把画面切回该仓管界面（复用 onClose 的返回逻辑）。
     */
    private void createimp$confirmAndReturn() {
        if (previousScreen instanceof com.molox.createimp.util.StockKeeperRequestScreenInvoker invoker) {
            invoker.createimp$invokeSendIt();
        }
        onClose();
    }

    @Override
    public void onClose() {
        ScreenOpener.openPreviousScreen(this, null);
    }

    @Override
    public void tick() {
        super.tick();
        scroll.tickChaser();
        if (confirmButton != null) {
            // 还没收到过服务端回应时，get() 返回 -1，天然小于 templateCount
            // （此时 templateCount 必然 >= 1，否则 canCompleteAll 分支不会
            // 创建这个按钮），因此会正确地默认按钮保持禁用，不需要额外的
            // 未知状态特判。之前这里直接在客户端本地调用
            // WorkWarehouseNetworkHelper.countAvailableWorkWarehouses，
            // 但那个注册表只在服务端进程里维护，独立服务端环境下客户端
            // 永远查不到数据，导致按钮永远无法点击。
            confirmButton.active = ClientWorkWarehouseAvailabilityCache.get(freqId) >= templateCount;
        }
        pollStockChanges();
        pollWorkWarehouseAvailability();
    }

    /**
     * 按固定节奏向服务端查询一次当前频率下的可用工作仓库数量，结果异步写入
     * {@link ClientWorkWarehouseAvailabilityCache}。
     */
    private void pollWorkWarehouseAvailability() {
        if (freqId == null) {
            return;
        }
        if (workWarehousePollCooldown-- > 0) {
            return;
        }
        workWarehousePollCooldown = WORK_WAREHOUSE_POLL_TICKS;
        PacketDistributor.sendToServer(new RequestWorkWarehouseAvailabilityPacket(freqId));
    }

    /**
     * 跟踪的物品：所需材料计算结果里出现过的每一种物品（缺少材料 + 现有材料），
     * 用于监控网络库存变化。
     */
    private record TrackedItem(ItemStack sample, int lastKnownCount) {
    }

    private void rebuildTrackedItems() {
        trackedItems.clear();
        StockTickerBlockEntity blockEntity = resolveBlockEntity();
        InventorySummary summary = blockEntity != null ? blockEntity.getLastClientsideStockSnapshotAsSummary() : null;
        List<ItemStack> allItems = new ArrayList<>();
        for (BigItemStack entry : missing) {
            allItems.add(entry.stack);
        }
        for (BigItemStack entry : usedFromStock) {
            allItems.add(entry.stack);
        }
        for (ItemStack stack : allItems) {
            int count = summary != null ? summary.getCountOf(stack) : 0;
            trackedItems.add(new TrackedItem(stack.copyWithCount(1), count));
        }
        stockPollCooldown = STOCK_POLL_TICKS;
    }

    private StockTickerBlockEntity resolveBlockEntity() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        BlockEntity be = minecraft.level.getBlockEntity(stockTickerPos);
        return be instanceof StockTickerBlockEntity stbe ? stbe : null;
    }

    /**
     * 这一路轮询同时做两件事：
     * 1. 链有效性检查（几乎零额外开销）：仓管同步给客户端的库存快照里，
     *    "模板"分类下的每个条目都是服务端 collectOrderableTemplates()
     *    实时算出来的，只有链仍然有效的模板才会出现在里面（
     *    {@link InventorySummary#getCountOf} 按 isSameItemSameComponents
     *    精确匹配，不会和网络里的同名普通物品混淆，已反编译确认）。
     *    如果本次请求里任何一个模板 token 在这份快照里查不到了，说明它的
     *    链在服务端已经失效，直接在客户端本地清空请求栏并退回仓管界面，
     *    不需要额外发包问服务端。
     * 2. 库存变化检测（开销较大）：只有确认跟踪物品的库存数量真的发生
     *    变化时，才重新向服务端请求一次完整的材料计算。
     */
    private void pollStockChanges() {
        StockTickerBlockEntity blockEntity = resolveBlockEntity();
        if (blockEntity == null) {
            return;
        }
        if (stockPollCooldown > 0) {
            stockPollCooldown--;
            return;
        }
        if (blockEntity.getTicksSinceLastUpdate() > STOCK_POLL_TICKS) {
            blockEntity.refreshClientStockSnapshot();
        }
        stockPollCooldown = STOCK_POLL_TICKS;

        InventorySummary summary = blockEntity.getLastClientsideStockSnapshotAsSummary();
        if (summary == null) {
            return;
        }

        for (BigItemStack entry : originalItemsToOrder) {
            if (!TemplateOrderTokenHelper.isToken(entry.stack)) {
                continue;
            }
            if (summary.getCountOf(entry.stack) == 0) {
                handleChainBroken();
                return;
            }
        }

        boolean changed = false;
        for (TrackedItem tracked : trackedItems) {
            if (summary.getCountOf(tracked.sample()) != tracked.lastKnownCount()) {
                changed = true;
                break;
            }
        }
        if (!changed) {
            return;
        }
        PacketDistributor.sendToServer(new RequestTemplateMaterialsPacket(stockTickerPos, originalItemsToOrder));
    }

    private record Row(Component header, List<BigItemStack> items, int headerColor) {
    }

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        if (!canCompleteAll && !missing.isEmpty()) {
            rows.add(new Row(Component.translatable("createimp.gui.template_materials.missing"), missing, MISSING_TEXT_COLOR));
        }
        if (!usedFromStock.isEmpty()) {
            rows.add(new Row(Component.translatable("createimp.gui.template_materials.used"), usedFromStock, TEXT_COLOR));
        }
        return rows;
    }

    private int contentHeight(List<Row> rows) {
        int y = 0;
        for (Row row : rows) {
            y += SECTION_HEADER_H;
            int lines = (int) Math.ceil(row.items().size() / (double) ITEMS_PER_ROW);
            y += Math.max(1, lines) * ROW_STEP;
            y += SECTION_GAP;
        }
        return y;
    }

    private int getMaxScroll(List<Row> rows) {
        int visible = SCISSOR_Y2 - SCISSOR_Y;
        return Math.max(0, contentHeight(rows) - visible);
    }

    /**
     * 按渲染时同样的顺序遍历每一个材料格子，回调其"未滚动前"的相对屏幕坐标
     * （尚未减去 scrollOff）与对应的物品堆叠。渲染与悬浮命中测试共用同一份布局逻辑。
     */
    private void forEachMaterialSlot(List<Row> rows, SlotVisitor visitor) {
        int rowY = 0;
        for (Row row : rows) {
            int headerY = CONTENT_START_Y + rowY;
            visitor.onHeader(row.header(), headerY, row.headerColor());
            rowY += SECTION_HEADER_H;

            List<BigItemStack> items = row.items();
            for (int i = 0; i < items.size(); i++) {
                int col = i % ITEMS_PER_ROW;
                int line = i / ITEMS_PER_ROW;
                int slotX = GRID_MARGIN_X + col * ITEM_STEP;
                int slotY = CONTENT_START_Y + rowY + line * ROW_STEP;
                visitor.onSlot(slotX, slotY, items.get(i), row.headerColor());
            }
            int lines = (int) Math.ceil(items.size() / (double) ITEMS_PER_ROW);
            rowY += Math.max(1, lines) * ROW_STEP;
            rowY += SECTION_GAP;
        }
    }

    private interface SlotVisitor {
        default void onHeader(Component header, int relativeY, int color) {
        }

        void onSlot(int relativeX, int relativeY, BigItemStack entry, int color);
    }

    private BigItemStack getHoveredMaterialItem(int mouseX, int mouseY, float scrollOff) {
        if (mouseX < guiLeft + SCISSOR_X || mouseX >= guiLeft + SCISSOR_X2
                || mouseY < guiTop + SCISSOR_Y || mouseY >= guiTop + SCISSOR_Y2) {
            return null;
        }
        BigItemStack[] hovered = new BigItemStack[1];
        forEachMaterialSlot(buildRows(), new SlotVisitor() {
            @Override
            public void onSlot(int relativeX, int relativeY, BigItemStack entry, int color) {
                int screenX = guiLeft + relativeX;
                int screenY = guiTop + relativeY - Math.round(scrollOff);
                if (mouseX >= screenX && mouseX < screenX + ITEM_SIZE
                        && mouseY >= screenY && mouseY < screenY + ITEM_SIZE) {
                    hovered[0] = entry;
                }
            }
        });
        return hovered[0];
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<Row> rows = buildRows();
        int max = getMaxScroll(rows);
        if (max > 0) {
            float newTarget = scroll.getChaseTarget() - (float) scrollY * ROW_STEP;
            newTarget = Mth.clamp(newTarget, 0, max);
            scroll.chase(newTarget, 0.4, LerpedFloat.Chaser.EXP);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int y = guiTop;
        graphics.blit(TEXTURE, guiLeft, y, HEADER_SRC_X, HEADER_SRC_Y,
                AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.getWidth(),
                AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.getHeight(),
                TEXTURE_W, TEXTURE_H);
        y += HEADER_H;
        for (int i = 0; i < BODY_REPEAT; i++) {
            graphics.blit(TEXTURE, guiLeft, y, BODY_SRC_X, BODY_SRC_Y,
                    AllGuiTextures.STOCK_KEEPER_CATEGORY.getWidth(),
                    AllGuiTextures.STOCK_KEEPER_CATEGORY.getHeight(),
                    TEXTURE_W, TEXTURE_H);
            y += BODY_H;
        }
        graphics.blit(TEXTURE, guiLeft, y, FOOTER_SRC_X, FOOTER_SRC_Y,
                AllGuiTextures.STOCK_KEEPER_CATEGORY_FOOTER.getWidth(),
                AllGuiTextures.STOCK_KEEPER_CATEGORY_FOOTER.getHeight(),
                TEXTURE_W, TEXTURE_H);

        Component title = Component.translatable("createimp.gui.template_materials.title");
        int titleX = guiLeft + GUI_WIDTH / 2 - font.width(title) / 2;
        int titleY = guiTop + (HEADER_H - font.lineHeight) / 2;
        graphics.drawString(font, title, titleX, titleY, 0xFFFFFF, false);

        List<Row> rows = buildRows();
        float scrollOff = scroll.getValue(partialTicks);
        BigItemStack hoveredItem = getHoveredMaterialItem(mouseX, mouseY, scrollOff);

        graphics.enableScissor(
                guiLeft + SCISSOR_X, guiTop + SCISSOR_Y,
                guiLeft + SCISSOR_X2, guiTop + SCISSOR_Y2
        );

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, -scrollOff, 0);

        forEachMaterialSlot(rows, new SlotVisitor() {
            @Override
            public void onHeader(Component header, int relativeY, int color) {
                graphics.drawString(font, header, guiLeft + GRID_MARGIN_X, guiTop + relativeY, color, true);
            }

            @Override
            public void onSlot(int relativeX, int relativeY, BigItemStack entry, int color) {
                int slotX = guiLeft + relativeX;
                int slotY = guiTop + relativeY;
                boolean isHovered = entry == hoveredItem;
                renderMaterialSlot(graphics, slotX, slotY, entry, isHovered, color);
            }
        });

        pose.popPose();
        graphics.disableScissor();
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        float scrollOff = scroll.getValue(partialTicks);
        BigItemStack hoveredItem = getHoveredMaterialItem(mouseX, mouseY, scrollOff);
        if (hoveredItem != null) {
            graphics.renderTooltip(font, hoveredItem.stack, mouseX, mouseY);
        }

        if (confirmButton == null) {
            return;
        }
        boolean confirmHovered = mouseX >= confirmButton.getX() && mouseX < confirmButton.getX() + 18
                && mouseY >= confirmButton.getY() && mouseY < confirmButton.getY() + 18;
        if (!confirmHovered) {
            return;
        }
        // 之前这里直接在客户端本地调用 WorkWarehouseNetworkHelper.countAvailableWorkWarehouses，
        // 独立服务端环境下永远查不到数据，改为读取服务端定期回应更新的缓存。
        int availableCount = ClientWorkWarehouseAvailabilityCache.get(freqId);
        List<Component> lines = new ArrayList<>();
        if (availableCount < templateCount) {
            lines.add(Component.translatable("createimp.gui.stock_keeper.not_enough_work_warehouse"));
        }
        // 还没收到服务端回应（-1）时，提示文案里不显示负数，展示为 0 更符合直觉。
        lines.add(Component.translatable("createimp.gui.stock_keeper.work_warehouse_available",
                Math.max(0, availableCount)));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /**
     * 物品格子渲染：背景复用原版仓管请求槽贴图，图标使用原版仓管界面同款的
     * GuiGameElement 渲染方式，悬浮时按原版同样的比例（1.075倍）放大，
     * 数量角标改为仿照原版仓管界面 drawItemCount 的数字贴图样式绘制
     * （数字含义不变，仍然是 entry.count），并按所在分组的颜色染色。
     */
    private void renderMaterialSlot(GuiGraphics graphics, int x, int y, BigItemStack entry, boolean isHovered, int color) {
        AllGuiTextures.STOCK_KEEPER_REQUEST_SLOT.render(graphics, x, y);
        ItemStack display = entry.stack.copyWithCount(Math.max(1, entry.count));

        PoseStack pose = graphics.pose();
        pose.pushPose();
        float scaleFromHover = isHovered ? 1.075f : 1.0f;
        pose.translate(x + 1.0, y + 1.0, 0.0);
        pose.translate(9.0, 9.0, 0.0);
        pose.scale(scaleFromHover, scaleFromHover, scaleFromHover);
        pose.translate(-9.0, -9.0, 0.0);
        net.createmod.catnip.gui.element.GuiGameElement.of(display).render(graphics);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.0f, 0.0f, 190.0f);
        String text = (FluidLogisticsCompat.isLoaded() && TemplateFluidDisplayHelper.isVirtualFluidDisplay(entry.stack))
                ? TemplateFluidDisplayHelper.formatStorageAmount(entry.count)
                : formatItemCount(entry.count);
        drawCountText(graphics, x, y, text, color);
        pose.popPose();
    }

    /**
     * 原版风格的物品数量压缩文本（k/m/+），从原来的 {@code drawItemCount}
     * 里拆出来，只负责算文本，不负责画。
     */
    private static String formatItemCount(int count) {
        if (count >= 1_000_000_000) {
            return "+";
        }
        if (count >= 1_000_000) {
            return (count / 1_000_000) + "m";
        }
        if (count >= 10_000) {
            return (count / 1000) + "k";
        }
        if (count >= 1000) {
            return ((float) (count * 10 / 1000) / 10.0f) + "k";
        }
        if (count >= 100) {
            return "" + count;
        }
        return " " + count;
    }

    /**
     * 完全照搬原版 {@code StockKeeperRequestScreen.drawItemCount} 的数字贴图
     * 绘制算法（同一份 {@code AllGuiTextures.NUMBERS} 贴图），额外支持流包
     * 数量格式里用到的 b/B（桶，"1B"/"0.5B"这种）字符——照抄流包自己
     * {@code FluidSlotAmountRenderer} 对这个字符的贴图坐标映射。不管是物品
     * 数量文本还是流体数量文本，都统一走这一个方法绘制，因此都支持按传入
     * 的 color 整体染色（缺少材料显示为红色）。
     */
    private void drawCountText(GuiGraphics graphics, int slotX, int slotY, String text, int color) {
        if (text == null || text.isBlank()) {
            return;
        }

        float red = ((color >> 16) & 0xFF) / 255.0f;
        float green = ((color >> 8) & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(red, green, blue, 1.0f);

        int x = (int) Math.floor(-text.length() * 2.5);
        for (int i = 0; i < text.length(); i++) {
            char raw = text.charAt(i);
            char c = Character.toLowerCase(raw);
            if (c == ' ') {
                x += 4;
                continue;
            }
            if (c == ',') {
                continue;
            }
            int index = c - '0';
            int xOffset = index * 6;
            int spriteWidth = AllGuiTextures.NUMBERS.getWidth();
            if (c == '.') {
                spriteWidth = 3;
                xOffset = 60;
            } else if (c == 'k') {
                xOffset = 64;
            } else if (c == 'm') {
                spriteWidth = 7;
                xOffset = 70;
            } else if (c == 'b') {
                xOffset = 78;
            } else if (c == '+') {
                spriteWidth = 9;
                xOffset = 84;
            }
            graphics.blit(AllGuiTextures.NUMBERS.location, slotX + 14 + x, slotY + 10, 0,
                    AllGuiTextures.NUMBERS.getStartX() + xOffset, AllGuiTextures.NUMBERS.getStartY(),
                    spriteWidth, AllGuiTextures.NUMBERS.getHeight(), 256, 256);
            x += spriteWidth - 1;
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}