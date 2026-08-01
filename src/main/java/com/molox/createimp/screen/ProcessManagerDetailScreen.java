package com.molox.createimp.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.molox.createimp.CreateImp;
import com.molox.createimp.block.work_warehouse.WorkWarehouseBlockEntity;
import com.molox.createimp.block.work_warehouse.WorkWarehouseTemplateSnapshot;
import com.molox.createimp.network.RequestWorkWarehouseInterruptPacket;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 从进程面板主界面左键某张进程卡片进入的详情界面：从上到下、从前到后
 * 展示这个工作仓库本次工作的完整日志历史，每条日志前面带 "[XX分XX秒]"
 * 时间戳，超出宽度换行而不是滚动。刚进入时处于"置底"状态，跟随最新日志
 * 自动滚到底部；玩家往上滚一下就会退出置底，自己滚到底部或者点击右下角
 * 的置底按钮可以重新进入置底状态。
 */
public class ProcessManagerDetailScreen extends AbstractSimiScreen {

    // 以下这几个背景/标题/确认键相关常量和 ProcessManagerScreen 保持一致，
    // 两个界面共用同一套背景贴图和窗口尺寸。
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/process_manager_guibackg.png");
    private static final int TEXTURE_W = 256;
    private static final int TEXTURE_H = 256;

    private static final int BG_SRC_X = 13;
    private static final int BG_SRC_Y = 0;
    private static final int BG_SRC_RIGHT = 246;
    private static final int BG_SRC_BOTTOM = 219;
    private static final int BG_CENTERING_RIGHT = 238;

    private static final int BG_DRAW_WIDTH = BG_SRC_RIGHT - BG_SRC_X;
    private static final int BG_DRAW_HEIGHT = BG_SRC_BOTTOM - BG_SRC_Y;
    private static final int WINDOW_WIDTH = BG_CENTERING_RIGHT - BG_SRC_X;
    private static final int WINDOW_HEIGHT = BG_DRAW_HEIGHT;

    private static final int TITLE_Y_OFFSET = 3;
    private static final int TITLE_X_ADJUST = 0;
    private static final int TITLE_COLOR = 0x404040;

    private static final int CONFIRM_BUTTON_X_OFFSET = 201;
    private static final int CONFIRM_BUTTON_Y_OFFSET = 196;
    private static final int CONFIRM_BUTTON_SIZE = 18;

    private static final int DISPLAY_X = 0;
    private static final int DISPLAY_Y = 16;
    private static final int DISPLAY_RIGHT = 235;
    private static final int DISPLAY_BOTTOM = 188;
    private static final int DISPLAY_WIDTH = DISPLAY_RIGHT - DISPLAY_X;
    private static final int DISPLAY_HEIGHT = DISPLAY_BOTTOM - DISPLAY_Y;

    /** 列表滚动的平滑过渡速度（过渡时长，单位秒，越小追得越快），和主界面保持一致。 */
    private static final double SCROLL_CHASE_SPEED = 0.4;
    /** 每次滚轮滚动的像素步进。 */
    private static final int SCROLL_STEP = 20;

    // ============================================================
    // ↓↓↓ 以下是窗口内所有【非背景】组件的位置/颜色，均可自行修改 ↓↓↓
    // ============================================================

    /** 置底按钮：位置、贴图，18x18。写法和参考窗口的取消键一致——贴图整张
     *  盖在原版按钮底色上面，{@code x-1,y-1} 是为了抵消 IconButton 内部
     *  渲染图标时自带的 +1,+1 偏移，让贴图正好铺满整个 18x18 按钮范围。 */
    private static final ResourceLocation DOWN_BUTTON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/process_manager_progress_down.png");
    private static final ScreenElement DOWN_ICON = (graphics, x, y) ->
            graphics.blit(DOWN_BUTTON_TEXTURE, x - 1, y - 1, 0, 0, 18, 18, 18, 18);
    private static final int DOWN_BUTTON_X_OFFSET = 172;
    private static final int DOWN_BUTTON_Y_OFFSET = 196;
    private static final int DOWN_BUTTON_SIZE = 18;

    /** 日志文字左边距（相对显示区域左边缘）。 */
    private static final int LOG_X_OFFSET = 6;
    /** 单行日志最多多宽（像素）就换行；默认按显示区域宽度减去左右边距算，也可以直接改成固定数值。 */
    private static final int LOG_LINE_MAX_WIDTH = DISPLAY_WIDTH - LOG_X_OFFSET * 2 - 5;
    /** 不同日志条目之间的纵向间隔，同时也用作最顶部/最底部的留白。 */
    private static final int LOG_ENTRY_GAP = 6;
    /** 同一条日志换行后，行与行之间的纵向间隔。 */
    private static final int LOG_LINE_GAP = 2;
    /** 一般文字 / 高亮文字（"_xxx_"标记）颜色。 */
    private static final int LOG_TEXT_COLOR = 0xC0C0C0;
    private static final int LOG_HIGHLIGHT_COLOR = 0xFFD700;
    /** "请求中断"分类日志的一般文字颜色（红色），高亮部分用 LOG_CANCEL_HIGHLIGHT_COLOR。 */
    private static final int LOG_CANCEL_COLOR = 0xFF5555;
    /** "请求中断"分类日志里 "_高亮_" 部分专用的颜色（比 LOG_CANCEL_COLOR 更亮/更淡的红）。 */
    private static final int LOG_CANCEL_HIGHLIGHT_COLOR = 0xFF9999;
    /** "[XX分XX秒]" 时间戳颜色（亮棕色）。 */
    private static final int LOG_TIMESTAMP_COLOR = 0xC68642;

    /** 取消（中断请求）按钮：只在【当前进程】详情界面、且工作仓库处于
     *  原料请求/生产阶段时才显示，位置和主界面"历史请求日志"按钮一致。
     *  写法和置底按钮一样，贴图整张盖在原版按钮底色上面。 */
    private static final ResourceLocation CANCEL1_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/process_manager_progress_cancel1.png");
    private static final ResourceLocation CANCEL2_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/process_manager_progress_cancel2.png");
    private static final ScreenElement CANCEL1_ICON = (graphics, x, y) ->
            graphics.blit(CANCEL1_TEXTURE, x - 1, y - 1, 0, 0, 18, 18, 18, 18);
    private static final ScreenElement CANCEL2_ICON = (graphics, x, y) ->
            graphics.blit(CANCEL2_TEXTURE, x - 1, y - 1, 0, 0, 18, 18, 18, 18);
    private static final int CANCEL_BUTTON_X_OFFSET = 7;
    private static final int CANCEL_BUTTON_Y_OFFSET = 196;
    private static final int CANCEL_BUTTON_SIZE = 18;

    // ============================================================
    // ↑↑↑ 以上是窗口内所有【非背景】组件的位置/颜色，均可自行修改 ↑↑↑
    // ============================================================

    private record RenderedLine(List<ProcessLogTextUtil.Segment> segments, boolean firstOfEntry,
                                WorkWarehouseTemplateSnapshot.LogCategory category) {
    }

    private final BlockPos managerPos;
    /** 实时模式：轮询这个工作仓库；历史模式（{@link #staticEntries} 非空）下为 null，不使用。 */
    private final BlockPos warehousePos;
    /** 历史模式专用：日志内容已经固定不会再变，不需要每 tick 轮询。实时模式下为 null。 */
    private final List<WorkWarehouseTemplateSnapshot.LogEntry> staticEntries;
    private final float returnScroll;
    /** 关闭时应该回到主界面的"历史请求日志"模式还是当前进程模式。 */
    private final boolean returnToHistoryMode;
    private final LerpedFloat scroll = LerpedFloat.linear().startWithValue(0);

    private final List<RenderedLine> renderedLines = new ArrayList<>();
    private int knownEntryCount = 0;
    private int totalContentHeight = 0;
    /** 是否处于"置底"状态：为 true 时每 tick 都会自动追到最新日志。 */
    private boolean stickToBottom = true;
    /**
     * 上一次 {@link #refreshLog} 时，日志列表最前面那一条的轻量指纹
     * （{@code elapsedTicks + key}），用于判断最前面的条目是否已经被
     * 硬性字节上限截断逻辑丢弃换掉——不能直接拿 {@link WorkWarehouseTemplateSnapshot.LogEntry}
     * 整体做 {@code equals()} 比较，因为它内部经由 {@code LogArg.ItemCount}
     * 携带的 {@code ItemStack} 字段本身没有重写 {@code equals()}（反编译确认
     * 只会退化成对象引用比较），客户端每次收到方块实体同步包解码出来的都是
     * 全新实例，会导致哪怕内容完全没变也一直被判定为"变了"。改用这两个
     * 不涉及 {@code ItemStack} 的普通值字段拼指纹，能可靠判断内容是否真的
     * 变化。列表为空时为 {@code null}。
     */
    private record EntryFingerprint(long elapsedTicks, String key) {
    }
    private EntryFingerprint knownFirstEntryFingerprint = null;
    /**
     * 这次记录的日志是否曾经因为超过硬性字节上限被丢弃过最旧的条目。
     * 历史模式下从构造时固定传入；实时模式下每 tick 跟随
     * {@link WorkWarehouseBlockEntity#isLogTruncated()} 更新，因为这个状态
     * 可能是玩家正停留在详情界面时才变为 true 的。
     */
    private boolean logTruncated;
    /** {@link #logTruncated} 对应的警告行是否已经插入过 {@link #renderedLines}，
     *  避免重复插入。 */
    private boolean truncationWarningInserted = false;

    private IconButton confirmButton;
    private IconButton downButton;
    /** 取消（中断请求）按钮，历史模式下始终为 null（不创建）。 */
    private IconButton cancelButton;
    /** 是否处于"已点击一次、等待二次确认"的状态（cancel2 + 激活外观）。 */
    private boolean cancelArmed = false;

    /** 实时模式：日志会随着工作仓库继续工作而增长，每 tick 轮询；工作仓库回到空闲会自动退出。 */
    public ProcessManagerDetailScreen(BlockPos managerPos, BlockPos warehousePos, float returnScroll,
                                      boolean returnToHistoryMode) {
        super(Component.translatable("block.createimp.process_manager"));
        this.managerPos = managerPos;
        this.warehousePos = warehousePos;
        this.staticEntries = null;
        this.returnScroll = returnScroll;
        this.returnToHistoryMode = returnToHistoryMode;
        this.logTruncated = false;
    }

    /** 历史模式：日志内容是"历史请求日志"里的一条固定记录，不会再变化，也不需要自动退出。 */
    public ProcessManagerDetailScreen(BlockPos managerPos, List<WorkWarehouseTemplateSnapshot.LogEntry> staticEntries,
                                      boolean logTruncated, float returnScroll) {
        super(Component.translatable("block.createimp.process_manager"));
        this.managerPos = managerPos;
        this.warehousePos = null;
        this.staticEntries = new ArrayList<>(staticEntries);
        this.returnScroll = returnScroll;
        this.returnToHistoryMode = true;
        this.logTruncated = logTruncated;
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

        downButton = new IconButton(
                guiLeft + DOWN_BUTTON_X_OFFSET,
                guiTop + DOWN_BUTTON_Y_OFFSET,
                DOWN_BUTTON_SIZE, DOWN_BUTTON_SIZE,
                DOWN_ICON
        );
        downButton.withCallback(this::scrollToBottom);
        downButton.setToolTip(Component.translatable("createimp.gui.process_manager.scroll_to_bottom"));
        addRenderableWidget(downButton);

        if (staticEntries == null) {
            cancelButton = new IconButton(
                    guiLeft + CANCEL_BUTTON_X_OFFSET,
                    guiTop + CANCEL_BUTTON_Y_OFFSET,
                    CANCEL_BUTTON_SIZE, CANCEL_BUTTON_SIZE,
                    CANCEL1_ICON
            );
            cancelButton.withCallback(this::onCancelButtonClicked);
            cancelButton.visible = isWarehouseInterruptible();
            addRenderableWidget(cancelButton);
        }

        refreshLog();
        scroll.startWithValue(getMaxScroll());
        downButton.visible = !isAtBottom();
    }

    /**
     * 只有【当前进程】详情界面（不是历史模式）、且工作仓库处于原料请求
     * 或生产阶段时，取消按钮才显示；其余时候（比如已经在请求中断阶段）
     * 直接不显示这个按钮。
     */
    private boolean isWarehouseInterruptible() {
        if (staticEntries != null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        if (!(mc.level.getBlockEntity(warehousePos) instanceof WorkWarehouseBlockEntity warehouse)) {
            return false;
        }
        WorkWarehouseBlockEntity.WorkStage stage = warehouse.getStage();
        return stage == WorkWarehouseBlockEntity.WorkStage.REQUESTING_MATERIALS
                || stage == WorkWarehouseBlockEntity.WorkStage.PRODUCTION;
    }

    /**
     * 取消按钮的点击回调：第一次点击只是"武装"状态（换成 cancel2 贴图 +
     * 激活外观），不做任何实际操作；已经是武装状态时再点一次才真正确认，
     * 发包给服务端触发中断，同时立刻把按钮恢复成未武装的外观（不需要等
     * 服务端响应——工作仓库很快会切到请求中断阶段，按钮下一个 tick 就会
     * 因为 {@link #isWarehouseInterruptible()} 返回 false 而自动隐藏）。
     */
    private void onCancelButtonClicked() {
        if (!cancelArmed) {
            cancelArmed = true;
            cancelButton.setIcon(CANCEL2_ICON);
            cancelButton.green = true;
            return;
        }
        PacketDistributor.sendToServer(new RequestWorkWarehouseInterruptPacket(warehousePos));
        resetCancelState();
    }

    private void resetCancelState() {
        cancelArmed = false;
        if (cancelButton != null) {
            cancelButton.setIcon(CANCEL1_ICON);
            cancelButton.green = false;
        }
    }

    /** 置底按钮的点击回调：立刻回到底部，并重新进入置底状态。 */
    private void scrollToBottom() {
        stickToBottom = true;
        scroll.chase(getMaxScroll(), SCROLL_CHASE_SPEED, LerpedFloat.Chaser.EXP);
    }

    /** ESC 或确认键都会触发这个默认关闭流程，回到进程面板主界面并保留滚动位置与所在模式。 */
    @Override
    public void onClose() {
        ScreenOpener.open(new ProcessManagerScreen(managerPos, returnScroll, returnToHistoryMode));
    }

    @Override
    public void tick() {
        super.tick();
        scroll.tickChaser();
        refreshLog();
        if (stickToBottom) {
            scroll.chase(getMaxScroll(), SCROLL_CHASE_SPEED, LerpedFloat.Chaser.EXP);
        }
        if (downButton != null) {
            downButton.visible = !isAtBottom();
        }
        if (cancelButton != null) {
            boolean shouldShow = isWarehouseInterruptible();
            cancelButton.visible = shouldShow;
            if (!shouldShow && cancelArmed) {
                resetCancelState();
            }
        }
        closeIfWarehouseIdle();
    }

    /** 已经滚到最底部（或者压根没有可滚动的内容）时不需要"置底"按钮。 */
    private boolean isAtBottom() {
        int max = getMaxScroll();
        return max <= 0 || scroll.getChaseTarget() >= max;
    }

    /**
     * 这个工作仓库一旦完成本次工作、回到空闲状态（{@code resetToIdle()} 会
     * 同时清空日志、把状态切回 IDLE），详情界面就没有继续停留的意义了，
     * 自动退出回到进程面板主界面（等价于玩家自己按了 ESC / 确认键，同样
     * 会保留主界面的滚动位置）。
     */
    private void closeIfWarehouseIdle() {
        if (staticEntries != null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (mc.level.getBlockEntity(warehousePos) instanceof WorkWarehouseBlockEntity warehouse
                && warehouse.getStage() == WorkWarehouseBlockEntity.WorkStage.IDLE) {
            onClose();
        }
    }

    /**
     * 每 tick 检查一次日志是否有新增。历史模式下日志内容是固定的，只需要
     * 处理一次；实时模式下正常情况下只会变多，除非仓库本身回到空闲把
     * 日志清空了（那种情况整体重建）。只对新增的条目做换行计算，不会
     * 每 tick 把已经处理过的日志重新拆一遍。
     */
    private void refreshLog() {
        List<WorkWarehouseTemplateSnapshot.LogEntry> entries;
        if (staticEntries != null) {
            entries = staticEntries;
            if (knownEntryCount > 0) {
                insertTruncationWarningIfNeeded();
                return;
            }
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            if (!(mc.level.getBlockEntity(warehousePos) instanceof WorkWarehouseBlockEntity warehouse)) {
                return;
            }
            entries = warehouse.getLogEntries();
            logTruncated = warehouse.isLogTruncated();
        }

        EntryFingerprint currentFirstFingerprint = entries.isEmpty() ? null
                : new EntryFingerprint(entries.get(0).elapsedTicks(), entries.get(0).key());
        boolean frontChanged = !java.util.Objects.equals(currentFirstFingerprint, knownFirstEntryFingerprint);

        if (entries.size() == knownEntryCount && !frontChanged) {
            insertTruncationWarningIfNeeded();
            return;
        }

        if (entries.size() < knownEntryCount || frontChanged) {
            // 最前面的条目已经不是原来那条（被硬性字节上限截断丢弃了），或者
            // 仓库本身回到空闲把日志整体清空了——两种情况都没法只靠"追加新增
            // 的几条"来更新，必须整体重建，保证客户端显示的内容和服务端
            // 当前实际持有的日志列表完全一致。
            renderedLines.clear();
            knownEntryCount = 0;
            truncationWarningInserted = false;
        }

        int maxWidth = LOG_LINE_MAX_WIDTH;
        for (int i = knownEntryCount; i < entries.size(); i++) {
            WorkWarehouseTemplateSnapshot.LogEntry entry = entries.get(i);
            List<ProcessLogTextUtil.Segment> combined = new ArrayList<>();
            combined.add(new ProcessLogTextUtil.Segment(formatTimestamp(entry.elapsedTicks()) + " ",
                    ProcessLogTextUtil.TIMESTAMP));
            combined.addAll(ProcessLogTextUtil.parseHighlight(entry.resolveMessage()));
            List<List<ProcessLogTextUtil.Segment>> lines = ProcessLogTextUtil.wrap(font, combined, maxWidth);
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                renderedLines.add(new RenderedLine(lines.get(lineIndex), lineIndex == 0, entry.category()));
            }
        }
        knownEntryCount = entries.size();
        knownFirstEntryFingerprint = currentFirstFingerprint;
        insertTruncationWarningIfNeeded();
        recomputeTotalHeight();
    }

    /**
     * 日志曾经被截断过时，在 {@link #renderedLines} 最前方插入一条红色警告行
     * （复用 {@code CANCEL} 分类的配色，和"请求中断"日志的红色完全一致）。
     * 只会真正插入一次：{@link #truncationWarningInserted} 保证重复调用不会
     * 重复插入；{@link #logTruncated} 在实时模式下可能是玩家正停留在详情
     * 界面时才从 false 变为 true 的，所以每次 {@link #refreshLog} 都要检查，
     * 不能只在条目数量变化时才检查。
     */
    private void insertTruncationWarningIfNeeded() {
        if (!logTruncated || truncationWarningInserted) {
            return;
        }
        truncationWarningInserted = true;
        List<ProcessLogTextUtil.Segment> combined = ProcessLogTextUtil.parseHighlight(
                Component.translatable("createimp.log.truncated_warning").getString());
        List<List<ProcessLogTextUtil.Segment>> lines = ProcessLogTextUtil.wrap(font, combined, LOG_LINE_MAX_WIDTH);
        List<RenderedLine> warningLines = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            warningLines.add(new RenderedLine(lines.get(lineIndex), lineIndex == 0,
                    WorkWarehouseTemplateSnapshot.LogCategory.CANCEL));
        }
        renderedLines.addAll(0, warningLines);
        recomputeTotalHeight();
    }


    private void recomputeTotalHeight() {
        int height = LOG_ENTRY_GAP; // 最顶部留白
        for (int i = 0; i < renderedLines.size(); i++) {
            if (i > 0) {
                height += renderedLines.get(i).firstOfEntry() ? LOG_ENTRY_GAP : LOG_LINE_GAP;
            }
            height += font.lineHeight;
        }
        height += LOG_ENTRY_GAP; // 最底部留白
        totalContentHeight = height;
    }

    private int getMaxScroll() {
        return Math.max(0, totalContentHeight - DISPLAY_HEIGHT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = getMaxScroll();
        float newTarget = Mth.clamp(scroll.getChaseTarget() - (float) scrollY * SCROLL_STEP, 0, max);
        scroll.chase(newTarget, SCROLL_CHASE_SPEED, LerpedFloat.Chaser.EXP);
        if (scrollY > 0) {
            // 向上滚（远离底部）就退出置底状态
            stickToBottom = false;
        }
        if (newTarget >= max) {
            // 滚到了最底部，重新视为置底状态
            stickToBottom = true;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (cancelArmed && cancelButton != null && !isWithinCancelButton(mouseX, mouseY)) {
            resetCancelState();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isWithinCancelButton(double mouseX, double mouseY) {
        return cancelButton != null && cancelButton.visible
                && mouseX >= cancelButton.getX() && mouseX < cancelButton.getX() + CANCEL_BUTTON_SIZE
                && mouseY >= cancelButton.getY() && mouseY < cancelButton.getY() + CANCEL_BUTTON_SIZE;
    }

    private static String formatTimestamp(long elapsedTicks) {
        long totalSeconds = Math.max(0, elapsedTicks) / 20;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return Component.translatable("createimp.gui.process_manager.log_timestamp", minutes, seconds).getString();
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

        int x = guiLeft + DISPLAY_X + LOG_X_OFFSET;
        int y = guiTop + DISPLAY_Y + LOG_ENTRY_GAP;
        for (int i = 0; i < renderedLines.size(); i++) {
            RenderedLine line = renderedLines.get(i);
            if (i > 0) {
                y += line.firstOfEntry() ? LOG_ENTRY_GAP : LOG_LINE_GAP;
            }
            int normalColor = line.category() == WorkWarehouseTemplateSnapshot.LogCategory.CANCEL
                    ? LOG_CANCEL_COLOR : LOG_TEXT_COLOR;
            int highlightColor = line.category() == WorkWarehouseTemplateSnapshot.LogCategory.CANCEL
                    ? LOG_CANCEL_HIGHLIGHT_COLOR : LOG_HIGHLIGHT_COLOR;
            ProcessLogTextUtil.draw(graphics, font, line.segments(), x, y,
                    normalColor, highlightColor, LOG_TIMESTAMP_COLOR);
            y += font.lineHeight;
        }

        pose.popPose();
        graphics.disableScissor();
    }

    /**
     * 取消按钮的 tooltip 之前是在 {@link #renderWindow} 里画的，但那是在
     * 按钮之类的部件渲染之前调用的，会导致 tooltip 被按钮盖住。改成重写
     * 标准的 {@code render}，先调用 {@code super.render(...)}（背景+自定义
     * 内容+全部部件都画完），再在最后画 tooltip，保证永远盖在最上层。
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);

        if (isWithinCancelButton(mouseX, mouseY)) {
            List<Component> tooltip;
            if (cancelArmed) {
                tooltip = List.of(
                        Component.translatable("createimp.gui.process_manager.cancel_confirm_line1")
                                .withStyle(ChatFormatting.RED),
                        Component.translatable("createimp.gui.process_manager.cancel_confirm_line2"),
                        Component.translatable("createimp.gui.process_manager.cancel_confirm_line3")
                );
            } else {
                tooltip = List.of(Component.translatable("createimp.gui.process_manager.cancel_request"));
            }
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }
}