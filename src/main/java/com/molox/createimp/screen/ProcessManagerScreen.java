package com.molox.createimp.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.molox.createimp.CreateImp;
import com.molox.createimp.block.process_manager.ProcessManagerBlockEntity;
import com.molox.createimp.block.process_manager.ProcessManagerHistoryEntry;
import com.molox.createimp.block.work_warehouse.WorkWarehouseBlockEntity;
import com.molox.createimp.block.work_warehouse.WorkWarehouseNetworkHelper;
import com.molox.createimp.block.work_warehouse.WorkWarehouseTemplateSnapshot;
import com.molox.createimp.network.OpenProcessManagerGuiPacket;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 进程面板右键打开的窗口。展示所在物流网络内，所有当前处于工作状态的工作仓库
 * 各自的进度卡片（目前只有模板请求这一种类型），支持滚轮滚动。
 */
public class ProcessManagerScreen extends AbstractSimiScreen {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/process_manager_guibackg.png");
    // 贴图假定为标准的 256x256 尺寸，若实际图片不是这个尺寸，这两个常量要相应修改。
    private static final int TEXTURE_W = 256;
    private static final int TEXTURE_H = 256;

    // 贴图内背景实际绘制区域：左上角(13,0)，右下角(246,219)。
    private static final int BG_SRC_X = 13;
    private static final int BG_SRC_Y = 0;
    private static final int BG_SRC_RIGHT = 246;
    private static final int BG_SRC_BOTTOM = 219;
    // 用于居中计算的宽度只取到238（246-238=8像素是贴图右侧的附件部分，
    // 会跟着背景一起画出来，但不参与窗口居中的宽度计算）。
    private static final int BG_CENTERING_RIGHT = 238;

    private static final int BG_DRAW_WIDTH = BG_SRC_RIGHT - BG_SRC_X;
    private static final int BG_DRAW_HEIGHT = BG_SRC_BOTTOM - BG_SRC_Y;
    private static final int WINDOW_WIDTH = BG_CENTERING_RIGHT - BG_SRC_X;
    private static final int WINDOW_HEIGHT = BG_DRAW_HEIGHT;

    // 进程卡片背景贴图，尺寸固定 202x48，整张图直接绘制不做裁切。
    private static final ResourceLocation PROGRESS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/process_manager_progress_backg.png");
    // 鼠标悬停在卡片上时替换成这张贴图，尺寸和普通背景一致。
    private static final ResourceLocation PROGRESS_TEXTURE_SELECT =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/process_manager_progress_backg_select.png");
    private static final int PROGRESS_BG_W = 202;
    private static final int PROGRESS_BG_H = 48;

    // ============================================================
    // ↓↓↓ 以下是窗口内所有【非背景】组件的位置，均可自行修改 ↓↓↓
    // 除非特别说明，均为相对窗口左上角(guiLeft, guiTop)的像素偏移。
    // ============================================================

    /** 标题文字纵向位置。横向默认按窗口宽度自动居中，如需手动整体左右偏移，调整下面的 TITLE_X_ADJUST 即可（正数向右）。 */
    private static final int TITLE_Y_OFFSET = 3;
    private static final int TITLE_X_ADJUST = 0;
    /** 标题文字颜色（灰黑色）。 */
    private static final int TITLE_COLOR = 0x404040;
    /** 卡片内部一般文字颜色（白色，背景是灰黑色，灰色文字看不清）。 */
    private static final int PROGRESS_TEXT_COLOR = 0xFFFFFF;

    /** 确认键（关闭窗口用）左上角位置。 */
    private static final int CONFIRM_BUTTON_X_OFFSET = 201;
    private static final int CONFIRM_BUTTON_Y_OFFSET = 196;
    private static final int CONFIRM_BUTTON_SIZE = 18;

    /** "显示区域"：所有工作仓库进程卡片的可视/可滚动范围，超出部分用滚轮滚动。 */
    private static final int DISPLAY_X = 0;
    private static final int DISPLAY_Y = 16;
    private static final int DISPLAY_RIGHT = 235;
    private static final int DISPLAY_BOTTOM = 188;
    private static final int DISPLAY_WIDTH = DISPLAY_RIGHT - DISPLAY_X;
    private static final int DISPLAY_HEIGHT = DISPLAY_BOTTOM - DISPLAY_Y;

    /** 每张进程卡片之间的纵向间隙，最上方卡片上方、最下方卡片下方也各留一份同样大小的间隙。 */
    private static final int PROGRESS_CARD_GAP = 4;
    /** 卡片默认在显示区域内水平居中，如需整体左右微调，改这个值（正数向右）。 */
    private static final int PROGRESS_BG_X_ADJUST = -4;
    /** 每次滚轮滚动的像素步进。 */
    private static final int PROGRESS_SCROLL_STEP = 20;

    // ---- 以下是卡片【内部】控件的位置，均为相对卡片背景左上角的偏移 ----
    /** "模板请求" 标签位置。 */
    private static final int PROGRESS_LABEL_X_OFFSET = 6;
    private static final int PROGRESS_LABEL_Y_OFFSET = 7;
    /** 物品图标位置（无背景格子，数量角标固定显示在图标右下角）。 */
    private static final int PROGRESS_ITEM_X_OFFSET = 44;
    private static final int PROGRESS_ITEM_Y_OFFSET = 1;
    /** 经过时间文字纵向位置；横向固定右对齐，与卡片右边缘的距离由 PROGRESS_TIME_RIGHT_MARGIN 决定。 */
    private static final int PROGRESS_TIME_Y_OFFSET = 7;
    private static final int PROGRESS_TIME_RIGHT_MARGIN = 6;

    /** 最新日志行：位置相对卡片背景左上角的偏移，显示在卡片【内部】下方区域，不额外占用卡片间距。 */
    private static final int PROGRESS_LOG_X_OFFSET = 6;
    private static final int PROGRESS_LOG_Y_OFFSET = 35;
    /** "最新日志" 标签的位置。 */
    private static final int PROGRESS_LOG_LABEL_X_OFFSET = 6;
    private static final int PROGRESS_LOG_LABEL_Y_OFFSET = 24;
    /** 最新日志行的限制长度（超出这个宽度就横向滚动展示，而不是被截断或换行）。 */
    private static final int PROGRESS_LOG_WIDTH = PROGRESS_BG_W - PROGRESS_LOG_X_OFFSET * 2;
    /** 一般文字 / 高亮文字（"_xxx_"标记的部分）颜色。 */
    private static final int PROGRESS_LOG_TEXT_COLOR = 0xC0C0C0;
    private static final int PROGRESS_LOG_HIGHLIGHT_COLOR = 0xFFD700;
    /** 日志超长时横向滚动的速度，每 tick 移动多少像素，越大滚得越快。 */
    private static final float PROGRESS_LOG_SCROLL_SPEED = 0.8f;
    /** 单行滚动一轮结束后，到下一轮开头之间的空白间隔（像素）。 */
    private static final int PROGRESS_LOG_SCROLL_GAP = 24;

    /** 卡片列表纵向滚动的平滑过渡速度（{@code LerpedFloat.chase} 的过渡时长，单位秒，越小追得越快）。 */
    private static final double SCROLL_CHASE_SPEED = 0.4;

    /** 没有任何进程时，显示区域中上方的提示文字：纵向位置、颜色。横向固定居中。 */
    private static final int EMPTY_HINT_Y_OFFSET = 40;
    private static final int EMPTY_HINT_COLOR = 0xFFFFFF;

    /** "历史请求日志"切换按钮：位置、贴图，18x18。写法和置底按钮一致——
     *  贴图整张盖在原版按钮底色上面，选中时用 IconButton 自带的绿色底色
     *  （{@code green} 字段）表示"当前正在历史模式"。 */
    private static final ResourceLocation HISTORY_BUTTON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/process_manager_progress_history.png");
    private static final ScreenElement HISTORY_ICON = (graphics, x, y) ->
            graphics.blit(HISTORY_BUTTON_TEXTURE, x - 1, y - 1, 0, 0, 18, 18, 18, 18);
    private static final int HISTORY_BUTTON_X_OFFSET = 7;
    private static final int HISTORY_BUTTON_Y_OFFSET = 196;
    private static final int HISTORY_BUTTON_SIZE = 18;

    // ============================================================
    // ↑↑↑ 以上是窗口内所有【非背景】组件的位置，均可自行修改 ↑↑↑
    // ============================================================

    private final BlockPos pos;
    private final float initialScroll;
    private final LerpedFloat scroll = LerpedFloat.linear().startWithValue(0);
    private final List<WorkWarehouseBlockEntity> processes = new ArrayList<>();
    private final List<ProcessManagerHistoryEntry> historyEntries = new ArrayList<>();
    private UUID freqId;
    private float logScrollTicks = 0;
    /** 是否处于"历史请求日志"模式；false 为当前进程模式。 */
    private boolean historyMode;

    private IconButton confirmButton;
    private IconButton historyButton;

    public ProcessManagerScreen(OpenProcessManagerGuiPacket packet) {
        super(Component.translatable("block.createimp.process_manager"));
        this.pos = packet.pos();
        this.initialScroll = 0;
        this.historyMode = false;
    }

    /** 从详情界面按 ESC / 确认键返回时使用，恢复之前离开时的滚动位置与所在模式（当前进程 / 历史请求日志）。 */
    public ProcessManagerScreen(BlockPos pos, float initialScroll, boolean historyMode) {
        super(Component.translatable("block.createimp.process_manager"));
        this.pos = pos;
        this.initialScroll = initialScroll;
        this.historyMode = historyMode;
    }

    public static void open(OpenProcessManagerGuiPacket packet) {
        ScreenOpener.open(new ProcessManagerScreen(packet));
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        super.init();

        confirmButton = new IconButton(
                guiLeft + CONFIRM_BUTTON_X_OFFSET,
                guiTop + CONFIRM_BUTTON_Y_OFFSET,
                CONFIRM_BUTTON_SIZE, CONFIRM_BUTTON_SIZE,
                AllIcons.I_CONFIRM
        );
        confirmButton.withCallback(this::onClose);
        addRenderableWidget(confirmButton);

        historyButton = new IconButton(
                guiLeft + HISTORY_BUTTON_X_OFFSET,
                guiTop + HISTORY_BUTTON_Y_OFFSET,
                HISTORY_BUTTON_SIZE, HISTORY_BUTTON_SIZE,
                HISTORY_ICON
        );
        historyButton.withCallback(this::toggleHistoryMode);
        historyButton.setToolTip(Component.translatable("createimp.gui.process_manager.history_log"));
        historyButton.green = historyMode;
        addRenderableWidget(historyButton);

        refreshProcesses();
        refreshHistory();
        scroll.startWithValue(initialScroll);
    }

    /** 历史请求日志按钮的点击回调：切换模式，按钮保持选中态（绿色底色），切换时滚动位置归零。 */
    private void toggleHistoryMode() {
        historyMode = !historyMode;
        historyButton.green = historyMode;
        scroll.startWithValue(0);
    }

    @Override
    public void tick() {
        super.tick();
        scroll.tickChaser();
        logScrollTicks += 1;
        refreshProcesses();
        refreshHistory();
    }

    /**
     * 每 tick 重新拉取一次所在网络内当前处于工作状态的工作仓库列表——完全走客户端
     * 本地缓存（{@link WorkWarehouseNetworkHelper#findWorkingWorkWarehouses}
     * 的 clientSide=true 分支），不需要额外发包问服务端，开销和网络管理器的
     * 高亮逻辑属于同一量级。
     */
    private void refreshProcesses() {
        processes.clear();
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        if (!(minecraft.level.getBlockEntity(pos) instanceof ProcessManagerBlockEntity pmbe)
                || pmbe.behaviour == null) {
            return;
        }
        freqId = pmbe.behaviour.freqId;
        processes.addAll(WorkWarehouseNetworkHelper.findWorkingWorkWarehouses(freqId, true));
        clampScrollToContent();
    }

    /**
     * 每 tick 都会拉取一次这个进程面板自己存的历史请求日志（这份数据本身
     * 就是随方块实体完整同步下来的，不像实时进程列表那样需要额外查网络），
     * 不管当前是不是历史模式都会刷新，这样切换模式时数据已经是最新的。
     */
    private void refreshHistory() {
        historyEntries.clear();
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        if (!(minecraft.level.getBlockEntity(pos) instanceof ProcessManagerBlockEntity pmbe)) {
            return;
        }
        historyEntries.addAll(pmbe.getHistoryEntries());
        clampScrollToContent();
    }

    /** 当前模式下卡片列表的数量：历史模式看 {@link #historyEntries}，否则看 {@link #processes}。 */
    private int getActiveCount() {
        return historyMode ? historyEntries.size() : processes.size();
    }

    /**
     * 每 tick 都会调用（因为列表本身每 tick 刷新），如果直接用
     * {@code startWithValue} 强制赋值，会把 {@link #mouseScrolled} 里已经在
     * 进行中的平滑滚动动画每 tick 打断一次，表现为滚动完全不平滑。这里改为
     * 只在当前目标值确实超出新的合法范围时，才用 {@code chase}（同样是平滑
     * 过渡，不是瞬间跳变）把它拉回界内；范围内则什么都不做，不影响正在
     * 进行的滚动动画。
     */
    private void clampScrollToContent() {
        int max = getMaxScroll();
        float target = scroll.getChaseTarget();
        if (target > max) {
            scroll.chase(Math.max(0, max), SCROLL_CHASE_SPEED, LerpedFloat.Chaser.EXP);
        } else if (target < 0) {
            scroll.chase(0, SCROLL_CHASE_SPEED, LerpedFloat.Chaser.EXP);
        }
    }

    private int getMaxScroll() {
        int count = getActiveCount();
        if (count == 0) {
            return 0;
        }
        // 内容总高度 = 顶部间隙 + 每张卡片(含其下方间隙) + 底部间隙，
        // 顶部/底部间隙大小都和卡片间的间隙保持一致。
        int contentHeight = PROGRESS_CARD_GAP
                + count * (PROGRESS_BG_H + PROGRESS_CARD_GAP);
        return Math.max(0, contentHeight - DISPLAY_HEIGHT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = getMaxScroll();
        if (max > 0) {
            float newTarget = scroll.getChaseTarget() - (float) scrollY * PROGRESS_SCROLL_STEP;
            newTarget = Mth.clamp(newTarget, 0, max);
            scroll.chase(newTarget, SCROLL_CHASE_SPEED, LerpedFloat.Chaser.EXP);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.blit(TEXTURE, guiLeft, guiTop, BG_SRC_X, BG_SRC_Y,
                BG_DRAW_WIDTH, BG_DRAW_HEIGHT, TEXTURE_W, TEXTURE_H);

        Component title = Component.translatable("block.createimp.process_manager");
        int titleX = guiLeft + WINDOW_WIDTH / 2 - font.width(title) / 2 + TITLE_X_ADJUST;
        int titleY = guiTop + TITLE_Y_OFFSET;
        graphics.drawString(font, title, titleX, titleY, TITLE_COLOR, false);

        graphics.enableScissor(
                guiLeft + DISPLAY_X, guiTop + DISPLAY_Y,
                guiLeft + DISPLAY_RIGHT, guiTop + DISPLAY_BOTTOM
        );

        PoseStack pose = graphics.pose();
        pose.pushPose();
        float scrollOff = scroll.getValue(partialTicks);
        pose.translate(0, -scrollOff, 0);

        int cardX = guiLeft + DISPLAY_X + (DISPLAY_WIDTH - PROGRESS_BG_W) / 2 + PROGRESS_BG_X_ADJUST;
        int y = guiTop + DISPLAY_Y + PROGRESS_CARD_GAP;
        int hoveredIndex = getHoveredCardIndex(mouseX, mouseY, scrollOff);
        int count = getActiveCount();
        for (int i = 0; i < count; i++) {
            if (historyMode) {
                renderHistoryCard(graphics, cardX, y, historyEntries.get(i), partialTicks, scrollOff, i == hoveredIndex);
            } else {
                renderProcessCard(graphics, cardX, y, processes.get(i), partialTicks, scrollOff, i == hoveredIndex);
            }
            y += PROGRESS_BG_H + PROGRESS_CARD_GAP;
        }

        pose.popPose();
        graphics.disableScissor();

        if (!historyMode && processes.isEmpty()) {
            Component emptyHint = Component.translatable("createimp.gui.process_manager.no_active_process");
            int hintX = guiLeft + DISPLAY_X + DISPLAY_WIDTH / 2 - font.width(emptyHint) / 2;
            int hintY = guiTop + DISPLAY_Y + EMPTY_HINT_Y_OFFSET;
            graphics.drawString(font, emptyHint, hintX, hintY, EMPTY_HINT_COLOR, false);
        }
        if (historyMode && historyEntries.isEmpty()) {
            Component emptyHint = Component.translatable("createimp.gui.process_manager.no_history_log");
            int hintX = guiLeft + DISPLAY_X + DISPLAY_WIDTH / 2 - font.width(emptyHint) / 2;
            int hintY = guiTop + DISPLAY_Y + EMPTY_HINT_Y_OFFSET;
            graphics.drawString(font, emptyHint, hintX, hintY, EMPTY_HINT_COLOR, false);
        }

        if (hoveredIndex >= 0) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("createimp.gui.process_manager.lmb_detail")
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC)
            ), mouseX, mouseY);
        }
    }

    /**
     * 判断鼠标当前悬停在哪一张卡片上（下标），要求鼠标同时落在"显示区域"
     * 内部（否则被滚动裁剪掉、实际看不见的卡片不应该响应悬停/点击）。
     * 没有悬停任何卡片时返回 -1。
     */
    private int getHoveredCardIndex(int mouseX, int mouseY, float scrollOff) {
        if (mouseX < guiLeft + DISPLAY_X || mouseX >= guiLeft + DISPLAY_RIGHT
                || mouseY < guiTop + DISPLAY_Y || mouseY >= guiTop + DISPLAY_BOTTOM) {
            return -1;
        }
        int cardX = guiLeft + DISPLAY_X + (DISPLAY_WIDTH - PROGRESS_BG_W) / 2 + PROGRESS_BG_X_ADJUST;
        int step = PROGRESS_BG_H + PROGRESS_CARD_GAP;
        int firstY = guiTop + DISPLAY_Y + PROGRESS_CARD_GAP;
        int count = getActiveCount();
        for (int i = 0; i < count; i++) {
            int cardScreenY = firstY + i * step - Math.round(scrollOff);
            if (mouseX >= cardX && mouseX < cardX + PROGRESS_BG_W
                    && mouseY >= cardScreenY && mouseY < cardScreenY + PROGRESS_BG_H) {
                return i;
            }
        }
        return -1;
    }

    /** 左键点击悬停中的卡片时，进入对应的日志详情界面（实时/历史），并带上当前滚动位置与模式以便返回时恢复。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            float scrollOff = scroll.getValue(1.0f);
            int index = getHoveredCardIndex((int) mouseX, (int) mouseY, scrollOff);
            if (index >= 0) {
                if (historyMode) {
                    if (index < historyEntries.size()) {
                        ProcessManagerHistoryEntry entry = historyEntries.get(index);
                        ScreenOpener.open(new ProcessManagerDetailScreen(pos, entry.logEntries(), scroll.getChaseTarget()));
                        return true;
                    }
                } else if (index < processes.size()) {
                    WorkWarehouseBlockEntity warehouse = processes.get(index);
                    ScreenOpener.open(new ProcessManagerDetailScreen(
                            pos, warehouse.getBlockPos(), scroll.getChaseTarget(), historyMode));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderProcessCard(GuiGraphics graphics, int x, int y, WorkWarehouseBlockEntity warehouse,
                                   float partialTicks, float scrollOff, boolean hovered) {
        ResourceLocation bgTexture = hovered ? PROGRESS_TEXTURE_SELECT : PROGRESS_TEXTURE;
        graphics.blit(bgTexture, x, y, 0, 0, PROGRESS_BG_W, PROGRESS_BG_H, PROGRESS_BG_W, PROGRESS_BG_H);

        Component label = Component.translatable("createimp.gui.process_manager.template_request");
        graphics.drawString(font, label, x + PROGRESS_LABEL_X_OFFSET, y + PROGRESS_LABEL_Y_OFFSET, PROGRESS_TEXT_COLOR, false);

        ItemStack product = warehouse.getRequestedProduct();
        int amount = warehouse.getRequestedAmount();
        if (!product.isEmpty()) {
            renderProcessItem(graphics, x + PROGRESS_ITEM_X_OFFSET, y + PROGRESS_ITEM_Y_OFFSET, product, amount);
        }

        Component elapsed = formatElapsed(warehouse.getActivationGameTime());
        int elapsedX = x + PROGRESS_BG_W - PROGRESS_TIME_RIGHT_MARGIN - font.width(elapsed);
        int elapsedY = y + PROGRESS_TIME_Y_OFFSET;
        graphics.drawString(font, elapsed, elapsedX, elapsedY, PROGRESS_TEXT_COLOR, false);

        Component logLabel = Component.translatable("createimp.gui.process_manager.latest_log");
        graphics.drawString(font, logLabel, x + PROGRESS_LOG_LABEL_X_OFFSET, y + PROGRESS_LOG_LABEL_Y_OFFSET,
                PROGRESS_TEXT_COLOR, false);

        renderLogLine(graphics, x, y, warehouse.getLatestLogMessage(), partialTicks, scrollOff);
    }

    /**
     * "历史请求日志"模式下的卡片：外观和实时卡片一样（标签、物品图标+数量、
     * 最新日志行），只有右上角的时间文字换成"XX分XX秒前"（相对归档时刻），
     * 最新日志用的是这条历史记录里最后一条日志的内容。
     */
    private void renderHistoryCard(GuiGraphics graphics, int x, int y, ProcessManagerHistoryEntry entry,
                                   float partialTicks, float scrollOff, boolean hovered) {
        ResourceLocation bgTexture = hovered ? PROGRESS_TEXTURE_SELECT : PROGRESS_TEXTURE;
        graphics.blit(bgTexture, x, y, 0, 0, PROGRESS_BG_W, PROGRESS_BG_H, PROGRESS_BG_W, PROGRESS_BG_H);

        Component label = Component.translatable("createimp.gui.process_manager.template_request");
        graphics.drawString(font, label, x + PROGRESS_LABEL_X_OFFSET, y + PROGRESS_LABEL_Y_OFFSET, PROGRESS_TEXT_COLOR, false);

        ItemStack product = entry.requestedProduct();
        int amount = entry.requestedAmount();
        if (!product.isEmpty()) {
            renderProcessItem(graphics, x + PROGRESS_ITEM_X_OFFSET, y + PROGRESS_ITEM_Y_OFFSET, product, amount);
        }

        Component timeAgo = formatTimeAgo(entry.completionGameTime());
        int timeX = x + PROGRESS_BG_W - PROGRESS_TIME_RIGHT_MARGIN - font.width(timeAgo);
        int timeY = y + PROGRESS_TIME_Y_OFFSET;
        graphics.drawString(font, timeAgo, timeX, timeY, PROGRESS_TEXT_COLOR, false);

        Component logLabel = Component.translatable("createimp.gui.process_manager.latest_log");
        graphics.drawString(font, logLabel, x + PROGRESS_LOG_LABEL_X_OFFSET, y + PROGRESS_LOG_LABEL_Y_OFFSET,
                PROGRESS_TEXT_COLOR, false);

        List<WorkWarehouseTemplateSnapshot.LogEntry> logs = entry.logEntries();
        String lastMessage = logs.isEmpty() ? "" : logs.get(logs.size() - 1).resolveMessage();
        renderLogLine(graphics, x, y, lastMessage, partialTicks, scrollOff);
    }

    /**
     * 卡片下方展示一行日志文字（实时卡片用最新日志，历史卡片用该记录最后
     * 一条日志），单行；一般文字和 {@code _高亮_} 标记的文字分别用
     * {@link #PROGRESS_LOG_TEXT_COLOR} / {@link #PROGRESS_LOG_HIGHLIGHT_COLOR}
     * 绘制。内容宽度超出 {@link #PROGRESS_LOG_WIDTH} 时改为横向滚动展示（复制
     * 一份接在后面首尾相连，滚动到头就无缝回到开头），不做截断也不换行。
     * <p>
     * {@code scrollOff} 是外层卡片列表当前的纵向滚动量：{@code drawString}
     * 本身走的是 {@code graphics.pose()} 矩阵栈，会自动叠加外层
     * {@code pose.translate(0, -scrollOff, 0)} 的效果，但这里手动调用的
     * {@code enableScissor} 用的是原始屏幕像素坐标，不受矩阵栈影响——如果
     * 不手动把 {@code scrollOff} 减掉，裁剪框的位置就不会跟着列表一起滚动，
     * 卡片刚滚出去一点点，文字实际画的位置和裁剪框就对不上，看起来像是
     * "一滚动日志就消失了"。
     */
    private void renderLogLine(GuiGraphics graphics, int cardX, int cardY, String message,
                               float partialTicks, float scrollOff) {
        if (message == null || message.isEmpty()) {
            return;
        }
        List<ProcessLogTextUtil.Segment> segments = ProcessLogTextUtil.parseHighlight(message);
        int totalWidth = ProcessLogTextUtil.width(font, segments);

        int rowX = cardX + PROGRESS_LOG_X_OFFSET;
        int rowY = cardY + PROGRESS_LOG_Y_OFFSET;
        int scissorY = rowY - Math.round(scrollOff);

        graphics.enableScissor(rowX, scissorY, rowX + PROGRESS_LOG_WIDTH, scissorY + font.lineHeight);
        if (totalWidth <= PROGRESS_LOG_WIDTH) {
            ProcessLogTextUtil.draw(graphics, font, segments, rowX, rowY,
                    PROGRESS_LOG_TEXT_COLOR, PROGRESS_LOG_HIGHLIGHT_COLOR, PROGRESS_LOG_TEXT_COLOR);
        } else {
            int cycle = totalWidth + PROGRESS_LOG_SCROLL_GAP;
            float scrollPixels = (logScrollTicks + partialTicks) * PROGRESS_LOG_SCROLL_SPEED;
            int offset = Math.floorMod((int) scrollPixels, cycle);
            int drawX = rowX - offset;
            ProcessLogTextUtil.draw(graphics, font, segments, drawX, rowY,
                    PROGRESS_LOG_TEXT_COLOR, PROGRESS_LOG_HIGHLIGHT_COLOR, PROGRESS_LOG_TEXT_COLOR);
            ProcessLogTextUtil.draw(graphics, font, segments, drawX + cycle, rowY,
                    PROGRESS_LOG_TEXT_COLOR, PROGRESS_LOG_HIGHLIGHT_COLOR, PROGRESS_LOG_TEXT_COLOR);
        }
        graphics.disableScissor();
    }

    /**
     * 物品图标渲染，仿照参考窗口（材料所需窗口）的绘制方式，但不绘制槽位背景；
     * 数量角标沿用同一套数字贴图绘制算法，固定显示在图标右下角。
     */
    private void renderProcessItem(GuiGraphics graphics, int x, int y, ItemStack product, int amount) {
        ItemStack display = product.copyWithCount(Math.max(1, amount));

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + 1.0, y + 1.0, 0.0);
        GuiGameElement.of(display).render(graphics);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.0f, 0.0f, 190.0f);
        drawItemCount(graphics, x, y, amount);
        pose.popPose();
    }

    /**
     * 完全照搬参考窗口（材料所需窗口）的数字贴图绘制算法（同一份
     * {@code AllGuiTextures.NUMBERS} 贴图、同样的大数字压缩格式 k/m/+）。
     */
    private void drawItemCount(GuiGraphics graphics, int slotX, int slotY, int count) {
        String text;
        if (count >= 1_000_000_000) {
            text = "+";
        } else if (count >= 1_000_000) {
            text = (count / 1_000_000) + "m";
        } else if (count >= 10_000) {
            text = (count / 1000) + "k";
        } else if (count >= 1000) {
            text = ((float) (count * 10 / 1000) / 10.0f) + "k";
        } else if (count >= 100) {
            text = "" + count;
        } else {
            text = " " + count;
        }
        if (text.isBlank()) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        int x = (int) Math.floor(-text.length() * 2.5);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                x += 4;
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
            } else if (c == '+') {
                spriteWidth = 9;
                xOffset = 84;
            }
            graphics.blit(AllGuiTextures.NUMBERS.location, slotX + 14 + x, slotY + 10, 0,
                    AllGuiTextures.NUMBERS.getStartX() + xOffset, AllGuiTextures.NUMBERS.getStartY(),
                    spriteWidth, AllGuiTextures.NUMBERS.getHeight(), 256, 256);
            x += spriteWidth - 1;
        }
    }

    /**
     * "经过时间 xx分xx秒"，按客户端当前世界时间与工作仓库激活时刻的世界时间
     * （{@link WorkWarehouseBlockEntity#getActivationGameTime()}，随方块实体
     * 同步）之差实时计算，每 tick 都会随游戏时间推进自然更新，不需要额外定时器。
     */
    private Component formatElapsed(long activationGameTime) {
        long currentGameTime = minecraft.level != null ? minecraft.level.getGameTime() : activationGameTime;
        long elapsedTicks = Math.max(0, currentGameTime - activationGameTime);
        long totalSeconds = elapsedTicks / 20;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return Component.translatable("createimp.gui.process_manager.elapsed", minutes, seconds);
    }

    /**
     * "XX分XX秒前"，按客户端当前世界时间与这条历史记录归档时刻的世界时间
     * （{@link ProcessManagerHistoryEntry#completionGameTime()}）之差实时
     * 计算，会随时间推进持续增大（不像实时经过时间那样对应一个还在跑的
     * 工作仓库）。
     */
    private Component formatTimeAgo(long completionGameTime) {
        long currentGameTime = minecraft.level != null ? minecraft.level.getGameTime() : completionGameTime;
        long elapsedTicks = Math.max(0, currentGameTime - completionGameTime);
        if (elapsedTicks >= 20L * 60 * 60) {
            return Component.translatable("createimp.gui.process_manager.time_ago_over_hour");
        }
        long totalSeconds = elapsedTicks / 20;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return Component.translatable("createimp.gui.process_manager.time_ago", minutes, seconds);
    }
}