package com.molox.createimp.screen;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.labeled_redstone_link.LabeledRedstoneLinkBlockEntity;
import com.molox.createimp.block.redstone_link_router.RedstoneLinkRouterBlockEntity;
import com.molox.createimp.block.redstone_link_router.RedstoneLinkRouterComponentData;
import com.molox.createimp.block.redstone_link_router.RedstoneLinkRouterConnectionRef;
import com.molox.createimp.network.OpenRedstoneLinkRouterGuiPacket;
import com.molox.createimp.network.OpenRedstoneLinkRouterSetItemPacket;
import com.molox.createimp.network.SaveRedstoneLinkRouterDataPacket;
import com.molox.createimp.network.SaveRedstoneLinkRouterLabelPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 无线红石信号路由器右键打开的窗口。背景复用进程面板界面的同一张背景贴图与裁切区域。
 * 窗口内部是一个可以上下左右滚动/拖拽的"地图"：地图纵向由若干"行"堆叠组成，每行高
 * 24px，行与行之间夹一条 2px 高的分隔线；每行从左到右摆放若干 39x20 的"组件"，组件
 * 间隔 17px。每行末尾（最后一个组件之后）固定跟着一个"添加组件"按钮。地图最下方固定
 * 存在一条"额外行"，里面是一个"添加行"按钮。
 * <p>
 * 每个组件左侧是输入点、右侧是输出点。点击一个可用连接点会进入"连接状态"，除了可用
 * 目标点以外的一切交互（含悬浮高亮）全部锁定；起点自己在整个连接状态期间恒亮，候选
 * 目标点保持"精确悬浮才高亮"。候选分两类：
 * <ul>
 *     <li>同行相邻候选——沿用最早实现的单行连接。</li>
 *     <li>隔行候选——地图里任意一行（不要求上下相邻）、列偏移一格的候选，具体查找/搬运规则见
 *     {@link ConnectingState#findCrossRowCandidates()} 和 {@link #shiftGroup}
 *     上的详细注释，这部分是和你反复核对多轮确认下来的算法，如果实际表现和预期不符，
 *     多半是我对某个边界条件的理解仍有偏差，需要你进一步指出。</li>
 * </ul>
 * 隔行连接用 linknode（6x6）+ linkline 的折线画法：后方（输入点）模块前方隔 1px 放
 * 一个 linknode，前方（输出点）模块所在行的相同横坐标再放一个 linknode，两个模块
 * 各自用水平 linkline 连到自己那一侧的 linknode，两个 linknode 之间用竖直（旋转90°）
 * 的 linkline 连接。
 * <p>
 * 连接线互相独立、允许重叠；鼠标下所有重叠的连接线都算命中（右键统一断开），渲染上
 * 分两遍：先画所有非高亮的连接线，再单独画一遍所有当前命中的连接线（高亮贴图），
 * 保证高亮线永远盖在非高亮线上面。
 * <p>
 * 模块之间只要有连接（同行或跨行）就被"焊死"了相对位置，这样的一串模块构成一个
 * "刚性组"，移动时必须整组同步平移，具体算法见 {@link #shiftGroup} 上的详细注释。
 * 断开连接或删除模块之后，会调用 {@link #settleAll()} 反复对全图做"能不能整组往
 * 前滑一步"的检查，直到没有任何模块再移动为止。
 * <p>
 * 这一套行/组件/连接数据目前只保存在客户端这个 Screen 对象里，关闭窗口重新打开会
 * 重置，还没有做服务端持久化。
 */
public class RedstoneLinkRouterScreen extends AbstractSimiScreen {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/process_manager_guibackg.png");
    private static final int TEXTURE_W = 256;
    private static final int TEXTURE_H = 256;

    // 贴图内背景实际绘制区域：左上角(13,0)，右下角(246,219)——这两个坐标本身是
    // "该点左侧/上侧有多少像素"的记号，即右下角那个像素本身也要画进去，所以
    // 实际绘制宽高要在坐标差的基础上各 +1，否则背景最下面一行、最右边一列的
    // 像素会被漏画，看起来像是被裁掉了一圈。窗口本身用于居中定位的宽高
    // （WINDOW_WIDTH/WINDOW_HEIGHT）不跟着变，避免因为改这个尺寸导致窗口位置跟着挪动。
    private static final int BG_SRC_X = 13;
    private static final int BG_SRC_Y = 0;
    private static final int BG_SRC_RIGHT = 246;
    private static final int BG_SRC_BOTTOM = 219;
    private static final int BG_CENTERING_RIGHT = 238;

    private static final int BG_DRAW_WIDTH = BG_SRC_RIGHT - BG_SRC_X + 1;
    private static final int BG_DRAW_HEIGHT = BG_SRC_BOTTOM - BG_SRC_Y + 1;
    private static final int WINDOW_WIDTH = BG_CENTERING_RIGHT - BG_SRC_X;
    private static final int WINDOW_HEIGHT = BG_SRC_BOTTOM - BG_SRC_Y;

    private static final int TITLE_Y_OFFSET = 3;
    private static final int TITLE_COLOR = 0x404040;

    private static final int CONFIRM_BUTTON_X_OFFSET = 201;
    private static final int CONFIRM_BUTTON_Y_OFFSET = 196;
    private static final int CONFIRM_BUTTON_SIZE = 18;

    /** 左下角"清除所有配置"按钮，位置对齐进程面板界面里"历史进程"按钮的位置。 */
    private static final int CLEAR_BUTTON_X_OFFSET = 7;
    private static final int CLEAR_BUTTON_Y_OFFSET = 196;
    private static final int CLEAR_BUTTON_SIZE = 18;

    private static final int FUNC_X = 3;
    private static final int FUNC_Y = 16;
    private static final int FUNC_WIDTH = 220;
    private static final int FUNC_HEIGHT = 173;

    private static final int ROW_HEIGHT = 24;
    private static final int SEPARATOR_HEIGHT = 2;
    private static final int ROW_ADVANCE = ROW_HEIGHT + SEPARATOR_HEIGHT;

    /** 每行最左侧的空隙，之后才是第一个组件槽位的起点；原本是5，+2=7，避免隔行连接
     * 出现在第0列时，它的左侧连接点被功能区域左边界裁掉一半。 */
    private static final int ROW_LEFT_GUTTER = 7;

    private static final int COMPONENT_WIDTH = 39;
    private static final int COMPONENT_HEIGHT = 20;
    private static final int COMPONENT_GAP = 17;
    private static final int COMPONENT_ADVANCE = COMPONENT_WIDTH + COMPONENT_GAP;
    private static final int COMPONENT_Y_IN_ROW = (ROW_HEIGHT - COMPONENT_HEIGHT) / 2;

    /** 物品终端模块贴图上两个物品图标的显示区域：(3,2)-(18,17) 和 (21,2)-(36,17)，均为 15x15。 */
    private static final int ITEM_ICON_1_X = 3;
    private static final int ITEM_ICON_1_Y = 2;
    private static final int ITEM_ICON_2_X = 21;
    private static final int ITEM_ICON_2_Y = 2;

    /** 文本终端模块贴图上文本显示区域：(3,2)-(36,17)，超出宽度横向滚动显示。 */
    private static final int LABEL_TEXT_X = 3;
    private static final int LABEL_TEXT_Y = 2;
    private static final int LABEL_TEXT_WIDTH = 33;
    private static final int LABEL_TEXT_HEIGHT = 15;
    private static final float LABEL_SCROLL_SPEED = 0.8f;
    private static final int LABEL_SCROLL_GAP = 12;
    /** 字体大小、阴影颜色/偏移直接沿用新建模块窗口（{@link ComponentPickerPopup}）的设置。 */
    private static final int LABEL_TEXT_COLOR = 0xF8F8F8;
    private static final int LABEL_TEXT_SHADOW_COLOR = 0x747474;
    private static final float LABEL_TEXT_SHADOW_OFFSET = 0.5f;

    private static final int GAP_LEFT_HALF = COMPONENT_GAP / 2;
    private static final int GAP_RIGHT_HALF = COMPONENT_GAP - GAP_LEFT_HALF;

    private static final int LINKPOINT_WIDTH = 4;
    private static final int LINKPOINT_HEIGHT = 4;
    private static final int LINKPOINT_GAP = 1;
    private static final int LINKPOINT_Y_IN_ROW = (ROW_HEIGHT - LINKPOINT_HEIGHT) / 2;

    private static final ResourceLocation LINKPOINT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/link/linkpoint.png");
    private static final ResourceLocation LINKPOINT_TEXTURE_HOVER =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/link/linkpoint_1.png");

    private static final ResourceLocation LINKLINE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/link/linkline.png");
    private static final ResourceLocation LINKLINE_TEXTURE_HOVER =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/link/linkline_1.png");

    /** 连接状态下悬浮候选点/候选连线时用的"预览"贴图：用法和 linkline/linknode 一模一样，只是画的时候整体带 0.3 透明度、且盖在其它元素上方。 */
    private static final ResourceLocation PRELINKLINE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/link/prelinkline.png");
    private static final ResourceLocation PRELINKNODE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/link/prelinknode.png");
    /** 预览用的"幽灵组件"贴图：和 itemlink/labellink 尺寸一致，不区分具体组件类型，统一用这一张表示"连接后会挪到这里"。 */
    private static final ResourceLocation PRE_COMPONENT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/module/pre.png");
    private static final float PREVIEW_ALPHA = 0.3f;

    /** 隔行连接节点（6x6）。 */
    private static final int LINKNODE_SIZE = 6;
    /** 节点放在两个模块之间那 17px 间隔的正中间：(17-6)=11 分给两侧，前方分到 5、后方分到 6（11 是奇数，多出的 1px 给后方，和其它地方"奇数间隔分两半"的处理方式保持一致）。 */
    private static final int LINKNODE_FRONT_MARGIN = (COMPONENT_GAP - LINKNODE_SIZE) / 2;
    private static final int LINKNODE_BACK_MARGIN = COMPONENT_GAP - LINKNODE_SIZE - LINKNODE_FRONT_MARGIN;
    private static final int LINKNODE_Y_IN_ROW = (ROW_HEIGHT - LINKNODE_SIZE) / 2;

    private static final ResourceLocation LINKNODE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/link/linknode.png");
    private static final ResourceLocation LINKNODE_TEXTURE_HOVER =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/link/linknode_1.png");

    private static final int ADD_BUTTON_WIDTH = 19;
    private static final int ADD_BUTTON_HEIGHT = 16;
    private static final int ADD_BUTTON_X_SHIFT = 3;
    private static final int ADD_BUTTON_Y_IN_ROW = (ROW_HEIGHT - ADD_BUTTON_HEIGHT) / 2;

    private static final ResourceLocation ADD_BUTTON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/module/add.png");
    private static final ResourceLocation ADD_BUTTON_TEXTURE_HOVER =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/module/add_1.png");

    private static final ResourceLocation ITEMLINK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/module/itemlink.png");
    private static final ResourceLocation ITEMLINK_TEXTURE_HOVER =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/module/itemlink_1.png");
    private static final ResourceLocation LABELLINK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/module/labellink.png");
    private static final ResourceLocation LABELLINK_TEXTURE_HOVER =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/module/labellink_1.png");
    private static final ResourceLocation ANDGATE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/module/and.png");
    private static final ResourceLocation ANDGATE_TEXTURE_HOVER =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/module/and_1.png");
    private static final ResourceLocation ORGATE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/module/or.png");
    private static final ResourceLocation ORGATE_TEXTURE_HOVER =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/module/or_1.png");

    /** 激活状态叠加贴图：尺寸和模块贴图完全一致，画在模块本体上层表示"当前已激活"。 */
    private static final ResourceLocation POWERED_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/module/powered.png");

    /** 非门标记：跟随输出模块本身、画在所有连接线上层的一个 3x6 小图标，紧贴输出模块右边缘。 */
    private static final int NOT_MARK_WIDTH = 3;
    private static final int NOT_MARK_HEIGHT = 6;
    private static final int NOT_MARK_Y_IN_ROW = (ROW_HEIGHT - NOT_MARK_HEIGHT) / 2;

    private static final ResourceLocation NOT_MARK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/module/not.png");
    private static final ResourceLocation NOT_MARK_TEXTURE_HOVER =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/module/not_1.png");

    private static final int LINE_WIDTH = 216;
    private static final int LINE_HEIGHT = 2;
    private static final int LINE_X_IN_FUNC = (FUNC_WIDTH - LINE_WIDTH) / 2;

    private static final ResourceLocation LINE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/link/line.png");
    private static final ResourceLocation LINE_TEXTURE_HOVER =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/link/line_1.png");

    private static final ResourceLocation WINDOW_SIDE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/window/select_window_side.png");
    private static final ResourceLocation WINDOW_INSIDE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/window/select_window_inside.png");
    private static final ResourceLocation WINDOW_INSIDE_TEXTURE_HOVER =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/redstone_link_router/window/select_window_inside_1.png");

    private static final int SCROLL_STEP = 20;
    private static final double SCROLL_CHASE_SPEED = 0.4;
    private static final double DRAG_CHASE_SPEED = 0.9;

    private final BlockPos pos;
    private final List<Row> rows = new ArrayList<>();

    private final LerpedFloat scrollX = LerpedFloat.linear().startWithValue(0);
    private final LerpedFloat scrollY = LerpedFloat.linear().startWithValue(0);

    private ModalPopup activeModal;
    private boolean draggingMap;
    private boolean loadedFromBlockEntity;
    /** 非空表示这次打开不需要重新读方块实体，直接用这份已知的最新数据初始化——用来
     *  规避"保存到服务端的包还没被处理完/同步包还没送回来，就立刻重新打开界面读到
     *  旧数据"这个时序竞态问题（本地已经知道最新值，没必要多绕一趟网络）。 */
    private final List<List<RedstoneLinkRouterComponentData>> pendingSnapshot;
    /** 文本终端超长文本横向滚动用的计时器，每 tick 递增，画面上按此计算滚动偏移。 */
    private float labelScrollTicks = 0;

    private IconButton confirmButton;
    private IconButton clearButton;

    /** 物品终端配置菜单关闭后要带回来的路由器快照——纯客户端本地的桥接变量，不经网络传输。 */
    private static List<List<RedstoneLinkRouterComponentData>> pendingItemEditReturnSnapshot;

    public RedstoneLinkRouterScreen(OpenRedstoneLinkRouterGuiPacket packet) {
        super(Component.translatable("block.createimp.redstone_link_router"));
        this.pos = packet.pos();
        this.pendingSnapshot = null;
    }

    private RedstoneLinkRouterScreen(BlockPos pos, List<List<RedstoneLinkRouterComponentData>> snapshot) {
        super(Component.translatable("block.createimp.redstone_link_router"));
        this.pos = pos;
        this.pendingSnapshot = snapshot;
    }

    public static void open(OpenRedstoneLinkRouterGuiPacket packet) {
        ScreenOpener.open(new RedstoneLinkRouterScreen(packet));
    }

    /** 用一份已经在客户端本地算好的最新快照直接重新打开界面，跳过方块实体读取。 */
    public static void openFromSnapshot(BlockPos pos, List<List<RedstoneLinkRouterComponentData>> snapshot) {
        ScreenOpener.open(new RedstoneLinkRouterScreen(pos, snapshot));
    }

    /** 供 {@code RedstoneLinkRouterSetItemScreen} 关闭时取走：取到之后立刻清空，避免被下一次编辑误用。 */
    public static List<List<RedstoneLinkRouterComponentData>> takePendingItemEditReturnSnapshot() {
        List<List<RedstoneLinkRouterComponentData>> snapshot = pendingItemEditReturnSnapshot;
        pendingItemEditReturnSnapshot = null;
        return snapshot;
    }

    /** 把某个槽位的两个物品数据位定点替换成新值（用于物品终端配置关闭后，往快照里补上刚编辑的结果）。 */
    public static void applyItemPatch(List<List<RedstoneLinkRouterComponentData>> snapshot,
                                      int rowIndex, int slotIndex, ItemStack item1, ItemStack item2) {
        if (rowIndex < 0 || rowIndex >= snapshot.size()) return;
        List<RedstoneLinkRouterComponentData> row = snapshot.get(rowIndex);
        if (slotIndex < 0 || slotIndex >= row.size()) return;
        RedstoneLinkRouterComponentData old = row.get(slotIndex);
        if (RedstoneLinkRouterComponentData.EMPTY_TYPE.equals(old.type())) return;
        row.set(slotIndex, new RedstoneLinkRouterComponentData(
                old.type(), old.notMarked(), item1.copy(), item2.copy(), old.labelText(),
                old.inputConnections(), old.outputConnections()));
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        super.init();

        if (!loadedFromBlockEntity) {
            loadedFromBlockEntity = true;
            if (pendingSnapshot != null) {
                loadRowsFromSnapshot(pendingSnapshot);
            } else {
                loadRowsFromBlockEntity();
            }
        }

        confirmButton = new IconButton(
                guiLeft + CONFIRM_BUTTON_X_OFFSET,
                guiTop + CONFIRM_BUTTON_Y_OFFSET,
                CONFIRM_BUTTON_SIZE, CONFIRM_BUTTON_SIZE,
                AllIcons.I_CONFIRM
        );
        confirmButton.withCallback(this::onClose);
        addRenderableWidget(confirmButton);

        // 复用机械动力"工厂仪表"界面里"重置所有设置"按钮同款的贴图（AllIcons.I_TRASH，
        // 对应它源码里 deleteButton 的图标，回调是把整块面板的配置清空），位置对齐
        // 进程面板界面"历史进程"按钮的位置（窗口左下角）。
        clearButton = new IconButton(
                guiLeft + CLEAR_BUTTON_X_OFFSET,
                guiTop + CLEAR_BUTTON_Y_OFFSET,
                CLEAR_BUTTON_SIZE, CLEAR_BUTTON_SIZE,
                AllIcons.I_TRASH
        );
        clearButton.withCallback(this::clearAll);
        addRenderableWidget(clearButton);
    }

    /** 清空这个路由器里的所有行、所有组件、所有连接。 */
    private void clearAll() {
        rows.clear();
        activeModal = null;
        draggingMap = false;
        scrollX.chase(0, SCROLL_CHASE_SPEED, LerpedFloat.Chaser.EXP);
        scrollY.chase(0, SCROLL_CHASE_SPEED, LerpedFloat.Chaser.EXP);
    }

    /**
     * 关闭这个界面之前，把当前编辑状态整体打包发给服务端覆盖保存。ESC 关闭走的是
     * 这里（vanilla {@code Screen.keyPressed} 在 ESC 时直接调用 {@code onClose()}），
     * 右下角确认键的回调本身就是 {@code this::onClose}，两条路径最终都会经过这里，
     * 不需要分别处理。
     */
    @Override
    public void onClose() {
        saveToServer();
        super.onClose();
    }

    private void saveToServer() {
        saveToServer(toRowData());
    }

    private void saveToServer(List<List<RedstoneLinkRouterComponentData>> snapshot) {
        HolderLookup.Provider registries = (HolderLookup.Provider) Minecraft.getInstance().level.registryAccess();
        CompoundTag data = RedstoneLinkRouterBlockEntity.encodeRowsToTag(registries, snapshot);
        PacketDistributor.sendToServer(new SaveRedstoneLinkRouterDataPacket(pos, data));
    }

    /** 打开界面时从客户端本地的方块实体读取上一次保存的行/组件/连接，初始化 {@link #rows}。 */
    private void loadRowsFromBlockEntity() {
        BlockEntity be = Minecraft.getInstance().level.getBlockEntity(pos);
        if (!(be instanceof RedstoneLinkRouterBlockEntity router)) return;
        loadRowsFromSnapshot(router.getRows());
    }

    /** 用一份已知的快照数据初始化 {@link #rows}，跳过方块实体读取（见 {@link #pendingSnapshot} 的说明）。 */
    private void loadRowsFromSnapshot(List<List<RedstoneLinkRouterComponentData>> snapshot) {
        rows.clear();
        for (List<RedstoneLinkRouterComponentData> rowData : snapshot) {
            Row row = new Row();
            for (RedstoneLinkRouterComponentData data : rowData) {
                row.components.add(componentFromData(data));
            }
            rows.add(row);
        }
    }

    /**
     * 每帧读取一次客户端本地方块实体上最近同步下来的"激活模块"坐标集合，用于渲染
     * "激活"贴图。这份数据是服务端按频率是否有电周期性算出来再同步下来的（见
     * {@code RedstoneLinkRouterBlockEntity.tick()}），不参与也不影响任何本地编辑逻辑，
     * 纯粹只用来决定要不要在某个模块上叠加画一层激活贴图。
     */
    private Set<RedstoneLinkRouterConnectionRef> currentPoweredRefs() {
        BlockEntity be = Minecraft.getInstance().level.getBlockEntity(pos);
        if (!(be instanceof RedstoneLinkRouterBlockEntity router)) return Set.of();
        return router.getPoweredRefs();
    }

    private static PlacedComponent componentFromData(RedstoneLinkRouterComponentData data) {
        if (RedstoneLinkRouterComponentData.EMPTY_TYPE.equals(data.type())) {
            return null;
        }
        PlacedComponent component = new PlacedComponent(ComponentType.valueOf(data.type()));
        component.notMarked = data.notMarked();
        component.itemSlot1 = data.itemSlot1();
        component.itemSlot2 = data.itemSlot2();
        component.labelText = data.labelText();
        for (RedstoneLinkRouterConnectionRef ref : data.inputConnections()) {
            component.inputConnections.add(new ComponentRef(ref.rowIndex(), ref.slotIndex()));
        }
        for (RedstoneLinkRouterConnectionRef ref : data.outputConnections()) {
            component.outputConnections.add(new ComponentRef(ref.rowIndex(), ref.slotIndex()));
        }
        return component;
    }

    /** 把当前 {@link #rows} 转换成可持久化的数据结构，供保存到服务端时使用。 */
    private List<List<RedstoneLinkRouterComponentData>> toRowData() {
        List<List<RedstoneLinkRouterComponentData>> result = new ArrayList<>();
        for (Row row : rows) {
            List<RedstoneLinkRouterComponentData> rowData = new ArrayList<>();
            for (PlacedComponent component : row.components) {
                rowData.add(componentToData(component));
            }
            result.add(rowData);
        }
        return result;
    }

    private static RedstoneLinkRouterComponentData componentToData(PlacedComponent component) {
        if (component == null) {
            return RedstoneLinkRouterComponentData.EMPTY;
        }
        List<RedstoneLinkRouterConnectionRef> inputs = new ArrayList<>();
        for (ComponentRef ref : component.inputConnections) {
            inputs.add(new RedstoneLinkRouterConnectionRef(ref.rowIndex(), ref.slotIndex()));
        }
        List<RedstoneLinkRouterConnectionRef> outputs = new ArrayList<>();
        for (ComponentRef ref : component.outputConnections) {
            outputs.add(new RedstoneLinkRouterConnectionRef(ref.rowIndex(), ref.slotIndex()));
        }
        return new RedstoneLinkRouterComponentData(
                component.type.name(), component.notMarked,
                component.itemSlot1, component.itemSlot2, component.labelText,
                inputs, outputs
        );
    }

    @Override
    public void tick() {
        super.tick();
        scrollX.tickChaser();
        scrollY.tickChaser();
        labelScrollTicks += 1;
    }

    /** 左键点击一个已放置的物品终端/文本终端模块，打开对应的配置界面；其它类型的模块暂时没有可配置项，点击只是单纯吞掉。 */
    private void openComponentEditor(ComponentRef ref) {
        PlacedComponent component = rows.get(ref.rowIndex()).components.get(ref.slotIndex());
        if (component == null) return;
        if (component.type == ComponentType.ITEM_LINK) {
            openItemEditor(ref, component);
        } else if (component.type == ComponentType.LABEL_LINK) {
            openLabelEditor(ref, component);
        }
    }

    /**
     * 物品终端配置：先把当前编辑状态整体存盘（保证服务端方块实体上这个槽位的数据
     * 是最新的），再请求服务端打开真正的配置菜单。这次存盘用的快照同时留一份在
     * {@link #pendingItemEditReturnSnapshot} 里，供菜单关闭后（见
     * {@link RedstoneLinkRouterSetItemScreen#removed()}）直接把刚编辑的物品结果
     * patch 进去重新打开——不能指望关闭那一刻方块实体上的同步包已经送达客户端本地，之前
     * 出现过"配置其实成功了，但界面上显示的还是旧值，再次进入编辑又把旧值当新值
     * 存回去"的问题，根源就是重新打开时重新读了一遍很可能还没同步到的方块实体。
     */
    private void openItemEditor(ComponentRef ref, PlacedComponent component) {
        List<List<RedstoneLinkRouterComponentData>> snapshot = toRowData();
        saveToServer(snapshot);
        pendingItemEditReturnSnapshot = snapshot;
        PacketDistributor.sendToServer(new OpenRedstoneLinkRouterSetItemPacket(
                pos, ref.rowIndex(), ref.slotIndex(), component.itemSlot1, component.itemSlot2));
    }

    /**
     * 文本终端配置：直接复用标码无线红石信号终端的频率设置界面（含剪贴板快速输入），
     * 保存时只把这一个模块的文本定点发给服务端，不需要走整份路由器数据的保存；
     * 关闭后用本地已知的快照（保存时顺手把新文本也 patch 进这份快照里）直接重开
     * 界面，原因同上——不依赖"服务端处理完、同步包也送回来了"这个不确定的时序。
     */
    private void openLabelEditor(ComponentRef ref, PlacedComponent component) {
        List<List<RedstoneLinkRouterComponentData>> snapshot = toRowData();
        saveToServer(snapshot);
        BlockPos routerPos = pos;
        int rowIndex = ref.rowIndex();
        int slotIndex = ref.slotIndex();
        LabeledRedstoneLinkScreen.openForRouter(
                component.labelText,
                text -> {
                    PacketDistributor.sendToServer(
                            new SaveRedstoneLinkRouterLabelPacket(routerPos, rowIndex, slotIndex, text));
                    applyLabelPatch(snapshot, rowIndex, slotIndex, text);
                },
                () -> RedstoneLinkRouterScreen.openFromSnapshot(routerPos, snapshot)
        );
    }

    /** 把某个槽位的文本数据位定点替换成新值（用于文本终端配置关闭后，往快照里补上刚编辑的结果）。 */
    private static void applyLabelPatch(List<List<RedstoneLinkRouterComponentData>> snapshot,
                                        int rowIndex, int slotIndex, String text) {
        if (rowIndex < 0 || rowIndex >= snapshot.size()) return;
        List<RedstoneLinkRouterComponentData> row = snapshot.get(rowIndex);
        if (slotIndex < 0 || slotIndex >= row.size()) return;
        RedstoneLinkRouterComponentData old = row.get(slotIndex);
        if (RedstoneLinkRouterComponentData.EMPTY_TYPE.equals(old.type())) return;
        row.set(slotIndex, new RedstoneLinkRouterComponentData(
                old.type(), old.notMarked(), old.itemSlot1(), old.itemSlot2(), text,
                old.inputConnections(), old.outputConnections()));
    }

    private int mapContentHeight() {
        return rows.size() * ROW_ADVANCE + ROW_HEIGHT;
    }

    private int maxScrollY() {
        return Math.max(0, mapContentHeight() - FUNC_HEIGHT);
    }

    /**
     * 一行里"添加组件"按钮应该对齐的槽位号：最后一个非空组件的下一个位置，而不是
     * List 的原始长度——搬运/滑落之后，列表末尾可能残留空位置，这些不应该计入。
     */
    private int effectiveComponentCount(Row row) {
        for (int i = row.components.size() - 1; i >= 0; i--) {
            if (row.components.get(i) != null) return i + 1;
        }
        return 0;
    }

    private int rowAddSlotRightEdge(int componentCount) {
        return ROW_LEFT_GUTTER + componentCount * COMPONENT_ADVANCE + COMPONENT_WIDTH;
    }

    private int mapContentWidth() {
        int width = rowAddSlotRightEdge(0);
        for (Row row : rows) {
            width = Math.max(width, rowAddSlotRightEdge(effectiveComponentCount(row)));
        }
        return width;
    }

    private int maxScrollX() {
        return Math.max(0, mapContentWidth() - FUNC_WIDTH);
    }

    private int addButtonScreenX(int funcScreenX, float offX, int slotIndex) {
        int slotStartX = ROW_LEFT_GUTTER + slotIndex * COMPONENT_ADVANCE;
        return Math.round(funcScreenX + slotStartX - ADD_BUTTON_X_SHIFT - offX);
    }

    private int addButtonScreenY(int funcScreenY, float offY, int rowTop) {
        return Math.round(funcScreenY + rowTop + ADD_BUTTON_Y_IN_ROW - offY);
    }

    private int componentScreenX(int funcScreenX, float offX, int slotIndex) {
        return Math.round(funcScreenX + ROW_LEFT_GUTTER + slotIndex * COMPONENT_ADVANCE - offX);
    }

    private int componentScreenY(int funcScreenY, float offY, int rowTop) {
        return Math.round(funcScreenY + rowTop + COMPONENT_Y_IN_ROW - offY);
    }

    private int rowTopOf(int rowIndex) {
        return rowIndex * ROW_ADVANCE;
    }

    private boolean isWithinFunctionalArea(int funcScreenX, int funcScreenY, int mouseX, int mouseY) {
        return mouseX >= funcScreenX && mouseX < funcScreenX + FUNC_WIDTH
                && mouseY >= funcScreenY && mouseY < funcScreenY + FUNC_HEIGHT;
    }

    private boolean isPointInRect(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    // ============================================================
    // 模块移动的核心引擎："刚性组"整体平移。
    //
    // 一个模块只要跟别的模块有任意一条连接（同行或跨行），就和对方被"焊死"了相对
    // 位置（同行必须紧邻、跨行必须错一列）；这种关系具有传递性，把全图按连接关系
    // 分组，同一组内任何成员的列号一旦定下来，其它成员的列号也就跟着唯一确定了。
    // 所以"能不能移动"永远是问"整个刚性组能不能沿同一方向同时挪一步"，而不是针对
    // 单个模块问"它是死是活"——不再有静态的"死模块"标签，可移动性是每次尝试时才能
    // 确定的结果。
    //
    // 整个模拟过程只在一份"工作副本"（每一行组件列表的拷贝 + 模块对象到当前行列的
    // 映射）上进行，全程不接触真实的 rows/ComponentRef 数据；分组关系则是一次性从
    // 真实数据解析出的"对象引用图"，不依赖行列坐标，所以中途行列怎么变都不会失真。
    // 只有全部步骤都验证通过，才会在最后一次性提交回真实数据、并对所有位置发生
    // 变化的模块统一重写引用。dryRun=true 时到验证成功为止就返回，不提交，用于
    // 候选查找阶段"看看这样能不能行"而不真的改动数据。
    // ============================================================

    /** 从真实数据一次性解析出的"对象引用图"：每个模块 -> 它直接连接（不分输入输出、不分同行跨行）的所有模块。 */
    private Map<PlacedComponent, List<PlacedComponent>> buildNeighborGraph() {
        Map<PlacedComponent, List<PlacedComponent>> graph = new HashMap<>();
        for (Row row : rows) {
            for (PlacedComponent c : row.components) {
                if (c == null) continue;
                List<PlacedComponent> neighbors = new ArrayList<>();
                for (ComponentRef ref : c.inputConnections) {
                    PlacedComponent n = rows.get(ref.rowIndex()).components.get(ref.slotIndex());
                    if (n != null) neighbors.add(n);
                }
                for (ComponentRef ref : c.outputConnections) {
                    PlacedComponent n = rows.get(ref.rowIndex()).components.get(ref.slotIndex());
                    if (n != null) neighbors.add(n);
                }
                graph.put(c, neighbors);
            }
        }
        return graph;
    }

    /** 从 seed 出发，沿引用图广度优先遍历，得到它所在的整个刚性组。 */
    private Set<PlacedComponent> computeRigidGroup(PlacedComponent seed, Map<PlacedComponent, List<PlacedComponent>> graph) {
        Set<PlacedComponent> group = new HashSet<>();
        Deque<PlacedComponent> queue = new ArrayDeque<>();
        group.add(seed);
        queue.add(seed);
        while (!queue.isEmpty()) {
            PlacedComponent cur = queue.poll();
            for (PlacedComponent n : graph.getOrDefault(cur, List.of())) {
                if (group.add(n)) queue.add(n);
            }
        }
        return group;
    }

    /** 供外部（候选查找等）直接使用的版本：现从真实数据临时建一次图。 */
    private Set<PlacedComponent> computeRigidGroup(PlacedComponent seed) {
        return computeRigidGroup(seed, buildNeighborGraph());
    }

    /**
     * 尝试把 seed 所在的刚性组，沿 direction（+1 或 -1）方向连续挪 steps 步；
     * immutable 集合里的模块在整个过程中绝对不能被牵动（连接时前移传"不动模块所在
     * 的整个刚性组"，前滑检测传空集）。dryRun=true 只返回是否可行，不改动真实数据；
     * dryRun=false 且可行时会真正提交这次搬运（对所有位置变化的模块统一重写引用）。
     */
    private boolean shiftGroup(PlacedComponent seed, int direction, int steps, Set<PlacedComponent> immutable, boolean dryRun) {
        if (steps <= 0) return true;

        Map<PlacedComponent, List<PlacedComponent>> graph = buildNeighborGraph();

        // seed 自己所在的刚性组一旦和 immutable 有交集，说明 seed 本来就和"不能动的
        // 那一方"焊死在同一个组里：整体平移不会改变组内成员的相对列差，所以这次要求的
        // 目标列关系永远不可能达成，必须在这里直接判定失败，不能指望后面的占用检测
        // 发现问题——组内成员之间互相不会被当成"占用者"，那条检测根本不会触发。
        if (!Collections.disjoint(computeRigidGroup(seed, graph), immutable)) {
            return false;
        }

        Map<Integer, List<PlacedComponent>> working = new HashMap<>();
        Map<PlacedComponent, int[]> positionOf = new HashMap<>();
        for (int r = 0; r < rows.size(); r++) {
            List<PlacedComponent> copy = new ArrayList<>(rows.get(r).components);
            working.put(r, copy);
            for (int c = 0; c < copy.size(); c++) {
                if (copy.get(c) != null) positionOf.put(copy.get(c), new int[]{r, c});
            }
        }
        Map<PlacedComponent, int[]> originalPositions = new HashMap<>();
        for (Map.Entry<PlacedComponent, int[]> e : positionOf.entrySet()) {
            originalPositions.put(e.getKey(), e.getValue().clone());
        }

        for (int i = 0; i < steps; i++) {
            Set<PlacedComponent> group = computeRigidGroup(seed, graph);
            Map<PlacedComponent, int[]> plan = new HashMap<>();
            if (!planGroupShift(group, direction, immutable, plan, working, positionOf, graph)) {
                return false;
            }
            applyPlanToWorking(plan, working, positionOf);
        }

        if (!dryRun) {
            commitWorkingToReal(working, originalPositions, positionOf);
        }
        return true;
    }

    /**
     * 对 group 里的每个成员各自算出挪一步后的目标格；如果目标格被别的（非本组、非
     * 已在计划中）模块占着，且那个模块不在 immutable 里，就递归地把它所在的整个
     * 刚性组也一起纳入这次验证；只要有任何一步失败（越界或撞上 immutable），整体
     * 失败，plan 里已经累积的内容作废（因为外层只在整体成功时才会真正应用 plan）。
     */
    private boolean planGroupShift(Set<PlacedComponent> group, int direction, Set<PlacedComponent> immutable,
                                   Map<PlacedComponent, int[]> plan,
                                   Map<Integer, List<PlacedComponent>> working,
                                   Map<PlacedComponent, int[]> positionOf,
                                   Map<PlacedComponent, List<PlacedComponent>> graph) {
        for (PlacedComponent member : group) {
            if (plan.containsKey(member)) continue;
            int[] pos = positionOf.get(member);
            int newCol = pos[1] + direction;
            if (newCol < 0) return false;
            plan.put(member, new int[]{pos[0], newCol});
        }
        for (PlacedComponent member : group) {
            int[] planned = plan.get(member);
            List<PlacedComponent> rowList = working.get(planned[0]);
            if (planned[1] >= rowList.size()) continue;
            PlacedComponent occupant = rowList.get(planned[1]);
            if (occupant == null || group.contains(occupant) || plan.containsKey(occupant)) continue;
            if (immutable.contains(occupant)) return false;
            Set<PlacedComponent> occupantGroup = computeRigidGroup(occupant, graph);
            if (!planGroupShift(occupantGroup, direction, immutable, plan, working, positionOf, graph)) return false;
        }
        return true;
    }

    private void applyPlanToWorking(Map<PlacedComponent, int[]> plan,
                                    Map<Integer, List<PlacedComponent>> working,
                                    Map<PlacedComponent, int[]> positionOf) {
        for (PlacedComponent member : plan.keySet()) {
            int[] oldPos = positionOf.get(member);
            working.get(oldPos[0]).set(oldPos[1], null);
        }
        for (Map.Entry<PlacedComponent, int[]> e : plan.entrySet()) {
            int newRow = e.getValue()[0];
            int newCol = e.getValue()[1];
            List<PlacedComponent> list = working.get(newRow);
            while (list.size() <= newCol) list.add(null);
            list.set(newCol, e.getKey());
            positionOf.put(e.getKey(), new int[]{newRow, newCol});
        }
    }

    /** 把工作副本提交回真实 rows，并把每个位置发生变化的模块，其旧位置的引用全部重写成新位置。 */
    /**
     * 把工作副本提交回真实数据，并重写所有受影响的连接引用。
     * <p>
     * 这里必须先把"旧坐标→新坐标"的完整映射表建好，再一次性扫描全图替换，而不能
     * 逐个模块分别扫描替换——如果分开做，级联搬运时经常会出现"模块A的旧位置恰好
     * 是模块B的新位置"，先处理完A之后，某些引用已经变成了指向A的新坐标，接着处理
     * B时会把"全图里等于这个坐标的引用"无差别地改写，反而把刚指向A的引用错误地
     * 篡改成指向B——这是之前出现连接丢失/错位的真正原因。改成"整表一次性查、一次性
     * 替换"之后，每条引用只会按它原本的坐标值被映射一次，不会被后续的替换连带误伤。
     */
    private void commitWorkingToReal(Map<Integer, List<PlacedComponent>> working,
                                     Map<PlacedComponent, int[]> originalPositions,
                                     Map<PlacedComponent, int[]> finalPositions) {
        Map<ComponentRef, ComponentRef> refRemap = new HashMap<>();
        for (Map.Entry<PlacedComponent, int[]> e : originalPositions.entrySet()) {
            int[] oldPos = e.getValue();
            int[] newPos = finalPositions.get(e.getKey());
            if (oldPos[0] == newPos[0] && oldPos[1] == newPos[1]) continue;
            refRemap.put(new ComponentRef(oldPos[0], oldPos[1]), new ComponentRef(newPos[0], newPos[1]));
        }

        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            row.components.clear();
            row.components.addAll(working.get(r));
        }

        if (refRemap.isEmpty()) return;

        for (Row row : rows) {
            for (PlacedComponent other : row.components) {
                if (other == null) continue;
                remapRefsOnce(other.inputConnections, refRemap);
                remapRefsOnce(other.outputConnections, refRemap);
            }
        }
    }

    private void remapRefsOnce(List<ComponentRef> list, Map<ComponentRef, ComponentRef> refRemap) {
        for (int i = 0; i < list.size(); i++) {
            ComponentRef mapped = refRemap.get(list.get(i));
            if (mapped != null) list.set(i, mapped);
        }
    }

    /**
     * 全图"前滑结算"：反复对地图里的每一个模块尝试"能不能沿列号变小的方向挪一步"
     * （不设 immutable，谁都可以被牵动），一轮下来只要还有任何模块动过就再来一轮，
     * 直到某一轮完全没有变化为止（加了个安全上限防止意外情况下死循环）。断开连接、
     * 删除组件之后都会调用这个方法，取代之前那种"只检查两个特定位置"的做法——现在
     * 组内成员会作为一个整体被一起考虑，不需要再单独处理"同行搭档"之类的特殊情况。
     */
    private void settleAll() {
        int guard = 0;
        boolean movedAny;
        do {
            movedAny = false;
            for (Row row : rows) {
                for (PlacedComponent c : new ArrayList<>(row.components)) {
                    if (c == null) continue;
                    if (shiftGroup(c, -1, 1, Set.of(), false)) {
                        movedAny = true;
                    }
                }
            }
        } while (movedAny && guard++ < 200);
        reclampScroll();
    }

    /** 在 insertIndex 位置插入一个空行，把地图里所有引用了"行号 >= insertIndex"的连接引用整体 +1。 */
    private void insertRowAt(int insertIndex) {
        rows.add(insertIndex, new Row());
        for (Row row : rows) {
            for (PlacedComponent c : row.components) {
                if (c == null) continue;
                remapRowIndexForInsert(c.inputConnections, insertIndex);
                remapRowIndexForInsert(c.outputConnections, insertIndex);
            }
        }
        reclampScroll();
    }

    private void remapRowIndexForInsert(List<ComponentRef> refs, int insertIndex) {
        for (int i = 0; i < refs.size(); i++) {
            ComponentRef ref = refs.get(i);
            if (ref.rowIndex() >= insertIndex) {
                refs.set(i, new ComponentRef(ref.rowIndex() + 1, ref.slotIndex()));
            }
        }
    }

    /** 删除一个（必须是空的）行，把地图里所有引用了"行号 > rowIndexToDelete"的连接引用整体 -1。 */
    private void deleteRow(int rowIndexToDelete) {
        rows.remove(rowIndexToDelete);
        for (Row row : rows) {
            for (PlacedComponent c : row.components) {
                if (c == null) continue;
                remapRowIndexForDelete(c.inputConnections, rowIndexToDelete);
                remapRowIndexForDelete(c.outputConnections, rowIndexToDelete);
            }
        }
        reclampScroll();
    }

    private void remapRowIndexForDelete(List<ComponentRef> refs, int deletedRowIndex) {
        for (int i = 0; i < refs.size(); i++) {
            ComponentRef ref = refs.get(i);
            if (ref.rowIndex() > deletedRowIndex) {
                refs.set(i, new ComponentRef(ref.rowIndex() - 1, ref.slotIndex()));
            }
        }
    }

    /** 结构性变化（增删行/组件）之后，把当前滚动位置钳制回新的合法范围内，避免视图停在已经不存在的空间里。 */
    private void reclampScroll() {
        scrollX.chase(Mth.clamp(scrollX.getChaseTarget(), 0, maxScrollX()), SCROLL_CHASE_SPEED, LerpedFloat.Chaser.EXP);
        scrollY.chase(Mth.clamp(scrollY.getChaseTarget(), 0, maxScrollY()), SCROLL_CHASE_SPEED, LerpedFloat.Chaser.EXP);
    }

    /**
     * 删除一个组件：先把它所有的连接（同行/跨行、输入/输出，数量不限）直接摘掉引用，
     * 再把它自己从行内摘掉（置空），最后调用一次全图 {@link #settleAll()}——不再需要
     * 针对"这一行后面"单独处理，全图结算本身就会正确地把因为这次删除而空出来位置
     * 的相关模块（不管是同行还是跨行牵连到的）该滑落的都滑落到位。
     */
    private void deleteComponent(int rowIndex, int slotIndex) {
        Row row = rows.get(rowIndex);
        PlacedComponent target = row.components.get(slotIndex);
        if (target == null) return;
        ComponentRef targetRef = new ComponentRef(rowIndex, slotIndex);

        for (ComponentRef other : new ArrayList<>(target.inputConnections)) {
            PlacedComponent otherComponent = rows.get(other.rowIndex()).components.get(other.slotIndex());
            otherComponent.outputConnections.remove(targetRef);
            clearNotMarkIfNoOutputs(otherComponent);
        }
        for (ComponentRef other : new ArrayList<>(target.outputConnections)) {
            rows.get(other.rowIndex()).components.get(other.slotIndex()).inputConnections.remove(targetRef);
        }

        row.components.set(slotIndex, null);
        settleAll();
    }

    /**
     * 判断当前鼠标应该让哪个组件显示连接点（悬浮触发模式，非连接状态下用）：命中组件
     * 本体则左右两侧里"存在且未被占用"的都显示；命中组件左侧间隔的右半部分只判断左侧
     * 点；命中组件右侧间隔的左半部分只判断右侧点。
     */
    private LinkpointTarget findLinkpointTarget(int funcScreenX, int funcScreenY, float offX, float offY, int mouseX, int mouseY) {
        if (!isWithinFunctionalArea(funcScreenX, funcScreenY, mouseX, mouseY)) return null;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowTop = rowTopOf(i);
            int rowScreenTop = Math.round(funcScreenY + rowTop - offY);
            if (mouseY < rowScreenTop || mouseY >= rowScreenTop + ROW_HEIGHT) continue;

            for (int j = 0; j < row.components.size(); j++) {
                PlacedComponent component = row.components.get(j);
                if (component == null) continue;
                boolean canLeft = component.inputConnections.isEmpty();
                boolean canRight = component.outputConnections.isEmpty();
                int compLeft = componentScreenX(funcScreenX, offX, j);
                int compRight = compLeft + COMPONENT_WIDTH;

                if (mouseX >= compLeft && mouseX < compRight) {
                    if (canLeft || canRight) return new LinkpointTarget(i, j, canLeft, canRight);
                    continue;
                }
                if (canLeft && mouseX >= compLeft - GAP_RIGHT_HALF && mouseX < compLeft) {
                    return new LinkpointTarget(i, j, true, false);
                }
                if (canRight && mouseX >= compRight && mouseX < compRight + GAP_LEFT_HALF) {
                    return new LinkpointTarget(i, j, false, true);
                }
            }
        }
        return null;
    }

    private LinkpointHit hitTestLinkpoint(int funcScreenX, int funcScreenY, float offX, float offY, double mouseX, double mouseY) {
        LinkpointTarget target = findLinkpointTarget(funcScreenX, funcScreenY, offX, offY, (int) mouseX, (int) mouseY);
        if (target == null) return null;
        int rowTop = rowTopOf(target.rowIndex());
        int compLeft = componentScreenX(funcScreenX, offX, target.slotIndex());
        int compRight = compLeft + COMPONENT_WIDTH;
        int linkY = Math.round(funcScreenY + rowTop + LINKPOINT_Y_IN_ROW - offY);
        if (target.showLeft() && isPointInRect(mouseX, mouseY, compLeft - LINKPOINT_GAP - LINKPOINT_WIDTH, linkY, LINKPOINT_WIDTH, LINKPOINT_HEIGHT)) {
            return new LinkpointHit(target.rowIndex(), target.slotIndex(), true);
        }
        if (target.showRight() && isPointInRect(mouseX, mouseY, compRight + LINKPOINT_GAP, linkY, LINKPOINT_WIDTH, LINKPOINT_HEIGHT)) {
            return new LinkpointHit(target.rowIndex(), target.slotIndex(), false);
        }
        return null;
    }

    private boolean isAdjacentConnected(Row row, int rowIndex, int slotIndex) {
        PlacedComponent left = row.components.get(slotIndex);
        PlacedComponent right = row.components.get(slotIndex + 1);
        if (left == null || right == null) return false;
        return left.outputConnections.contains(new ComponentRef(rowIndex, slotIndex + 1));
    }

    /** 收集地图上当前存在的所有连接线可视片段（同行的一段、隔行的五段），用于统一做悬浮/高亮/断开。 */
    private List<LineSegment> collectLineSegments(int funcScreenX, int funcScreenY, float offX, float offY) {
        List<LineSegment> segments = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            for (int j = 0; j < row.components.size(); j++) {
                PlacedComponent c = row.components.get(j);
                if (c == null) continue;
                for (ComponentRef ref : c.outputConnections) {
                    if (ref.rowIndex() == i && ref.slotIndex() == j + 1) {
                        int lineX = componentScreenX(funcScreenX, offX, j) + COMPONENT_WIDTH;
                        int lineY = Math.round(funcScreenY + rowTopOf(i) + LINKPOINT_Y_IN_ROW - offY);
                        segments.add(new LineSegment(new ConnectionHit(i, j, false), lineX, lineY, COMPONENT_GAP, LINKPOINT_HEIGHT, false));
                    } else if (ref.rowIndex() != i) {
                        addCrossRowSegments(segments, funcScreenX, funcScreenY, offX, offY, i, j, ref.rowIndex(), ref.slotIndex());
                    }
                }
            }
        }
        return segments;
    }

    private void addCrossRowSegments(List<LineSegment> segments, int funcScreenX, int funcScreenY, float offX, float offY,
                                     int frontRow, int frontSlot, int backRow, int backSlot) {
        ConnectionHit id = new ConnectionHit(frontRow, frontSlot, true);
        int frontCompLeft = componentScreenX(funcScreenX, offX, frontSlot);
        int frontCompRight = frontCompLeft + COMPONENT_WIDTH;
        int frontRowTop = rowTopOf(frontRow);
        int backRowTop = rowTopOf(backRow);
        int frontLinkY = Math.round(funcScreenY + frontRowTop + LINKPOINT_Y_IN_ROW - offY);
        int backLinkY = Math.round(funcScreenY + backRowTop + LINKPOINT_Y_IN_ROW - offY);

        int nodeX = frontCompRight + LINKNODE_FRONT_MARGIN;
        int frontNodeY = Math.round(funcScreenY + frontRowTop + LINKNODE_Y_IN_ROW - offY);
        int backNodeY = Math.round(funcScreenY + backRowTop + LINKNODE_Y_IN_ROW - offY);
        // 竖直连线（4px宽）比node（6px宽）窄2px，往右挪1px才能让竖直线在node宽度范围内居中。
        int verticalLineX = nodeX + (LINKNODE_SIZE - LINKPOINT_HEIGHT) / 2;

        // 两个 linknode 本身。原始贴图连接的是"左侧+下侧"（0°）。前方行的节点始终朝左
        // 连（前方模块在它左边），后方行的节点始终朝右连（后方模块在它右边）；竖直方向
        // 朝哪边则取决于对方行实际在上面还是下面。四种组合对应顺时针 0/90/180/270°：
        // 左+下=0°，左+上=90°，右+上=180°，右+下=270°。
        boolean backIsBelowFront = backRowTop > frontRowTop;
        float frontNodeRotation = backIsBelowFront ? 0f : 90f;
        float backNodeRotation = backIsBelowFront ? 180f : 270f;

        // 前方模块输出点 -> 前方行里的 linknode
        segments.add(new LineSegment(id, frontCompRight, frontLinkY, LINKNODE_FRONT_MARGIN, LINKPOINT_HEIGHT, false));
        // 后方模块输入点 -> 后方行里的 linknode
        segments.add(new LineSegment(id, nodeX + LINKNODE_SIZE, backLinkY, LINKNODE_BACK_MARGIN, LINKPOINT_HEIGHT, false));
        // 两个 linknode 本身
        segments.add(new LineSegment(id, nodeX, frontNodeY, LINKNODE_SIZE, LINKNODE_SIZE, true, false, frontNodeRotation));
        segments.add(new LineSegment(id, nodeX, backNodeY, LINKNODE_SIZE, LINKNODE_SIZE, true, false, backNodeRotation));
        // 两个 linknode 之间的竖直连线
        int topY = Math.min(frontNodeY, backNodeY) + LINKNODE_SIZE;
        int bottomY = Math.max(frontNodeY, backNodeY);
        segments.add(new LineSegment(id, verticalLineX, topY, LINKNODE_SIZE, bottomY - topY, false, true));
    }

    /** 鼠标位置下所有重叠的连接线（用同一个 ConnectionHit 去重），而不是只返回第一条命中的——这是修复"重叠连接线只能交互一条"的关键。 */
    private List<ConnectionHit> hitTestConnectionLines(int funcScreenX, int funcScreenY, float offX, float offY, double mouseX, double mouseY) {
        if (!isWithinFunctionalArea(funcScreenX, funcScreenY, (int) mouseX, (int) mouseY)) return List.of();
        java.util.LinkedHashSet<ConnectionHit> hits = new java.util.LinkedHashSet<>();
        for (LineSegment segment : collectLineSegments(funcScreenX, funcScreenY, offX, offY)) {
            if (isPointInRect(mouseX, mouseY, segment.x(), segment.y(), segment.w(), segment.h())) {
                hits.add(segment.id());
            }
        }
        return new ArrayList<>(hits);
    }

    private void disconnect(ConnectionHit hit) {
        if (hit.crossRow()) {
            Row frontRow = rows.get(hit.rowIndex());
            PlacedComponent front = frontRow.components.get(hit.leftSlotIndex());
            ComponentRef backRef = null;
            for (ComponentRef ref : front.outputConnections) {
                if (ref.rowIndex() != hit.rowIndex()) {
                    backRef = ref;
                    break;
                }
            }
            if (backRef == null) return;
            PlacedComponent back = rows.get(backRef.rowIndex()).components.get(backRef.slotIndex());
            front.outputConnections.remove(backRef);
            back.inputConnections.remove(new ComponentRef(hit.rowIndex(), hit.leftSlotIndex()));
            clearNotMarkIfNoOutputs(front);
        } else {
            Row row = rows.get(hit.rowIndex());
            PlacedComponent left = row.components.get(hit.leftSlotIndex());
            PlacedComponent right = row.components.get(hit.leftSlotIndex() + 1);
            left.outputConnections.remove(new ComponentRef(hit.rowIndex(), hit.leftSlotIndex() + 1));
            right.inputConnections.remove(new ComponentRef(hit.rowIndex(), hit.leftSlotIndex()));
            clearNotMarkIfNoOutputs(left);
        }
        settleAll();
    }

    /** 非门标记只在"这个模块还有至少一条输出连接"时才有意义；输出连接被清空时自动取消标记。 */
    private void clearNotMarkIfNoOutputs(PlacedComponent component) {
        if (component.outputConnections.isEmpty()) {
            component.notMarked = false;
        }
    }

    /**
     * 切换鼠标位置命中的每一条连接线，各自对应的输出模块的非门标记。ConnectionHit
     * 的 (rowIndex, leftSlotIndex) 始终就是该连接的输出侧模块坐标（同行连接里
     * leftSlotIndex 是左边那个提供输出的模块；隔行连接里 frontRow/frontSlot 同样
     * 固定是提供输出的一方，见 {@link #addCrossRowSegments}）。用 Set 按模块对象本身
     * 去重，保证鼠标同时悬浮在多条重叠连接线上、且它们指向同一个输出模块时，这个
     * 模块只被切换一次，不会出现"标记又打开又关闭"抵消的情况。
     */
    private void toggleNotMarks(List<ConnectionHit> hits) {
        java.util.Set<PlacedComponent> outputs = new java.util.LinkedHashSet<>();
        for (ConnectionHit hit : hits) {
            outputs.add(rows.get(hit.rowIndex()).components.get(hit.leftSlotIndex()));
        }
        for (PlacedComponent output : outputs) {
            output.notMarked = !output.notMarked;
        }
    }

    private boolean[] computeLinkpointVisibility(int rowIndex, int slotIndex, LinkpointTarget hoverTarget) {
        boolean showLeft = false;
        boolean showRight = false;
        if (activeModal instanceof ConnectingState cs) {
            if (cs.rowIndex() == rowIndex && cs.slotIndex() == slotIndex) {
                if (cs.fromLeft()) showLeft = true;
                else showRight = true;
            }
            for (ComponentRef ref : cs.allCandidates()) {
                if (ref.rowIndex() == rowIndex && ref.slotIndex() == slotIndex && !cs.isOccupiedOnNeededSide(ref)) {
                    if (cs.fromLeft()) showRight = true;
                    else showLeft = true;
                }
            }
        } else if (hoverTarget != null && hoverTarget.rowIndex() == rowIndex && hoverTarget.slotIndex() == slotIndex) {
            showLeft = hoverTarget.showLeft();
            showRight = hoverTarget.showRight();
        }
        return new boolean[]{showLeft, showRight};
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.blit(TEXTURE, guiLeft, guiTop, BG_SRC_X, BG_SRC_Y,
                BG_DRAW_WIDTH, BG_DRAW_HEIGHT, TEXTURE_W, TEXTURE_H);

        Component title = Component.translatable("block.createimp.redstone_link_router");
        int titleX = guiLeft + WINDOW_WIDTH / 2 - font.width(title) / 2;
        int titleY = guiTop + TITLE_Y_OFFSET;
        graphics.drawString(font, title, titleX, titleY, TITLE_COLOR, false);

        int funcScreenX = guiLeft + FUNC_X;
        int funcScreenY = guiTop + FUNC_Y;

        int mapMouseX = activeModal == null ? mouseX : Integer.MIN_VALUE;
        int mapMouseY = activeModal == null ? mouseY : Integer.MIN_VALUE;

        graphics.enableScissor(funcScreenX, funcScreenY, funcScreenX + FUNC_WIDTH, funcScreenY + FUNC_HEIGHT);

        float offX = scrollX.getValue(partialTicks);
        float offY = scrollY.getValue(partialTicks);

        LinkpointTarget hoverTarget = (activeModal instanceof ConnectingState)
                ? null : findLinkpointTarget(funcScreenX, funcScreenY, offX, offY, mapMouseX, mapMouseY);

        Set<RedstoneLinkRouterConnectionRef> poweredRefs = currentPoweredRefs();

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowTop = rowTopOf(i);

            for (int j = 0; j < row.components.size(); j++) {
                PlacedComponent component = row.components.get(j);
                if (component == null) continue;
                boolean powered = poweredRefs.contains(new RedstoneLinkRouterConnectionRef(i, j));
                drawComponent(graphics, funcScreenX, funcScreenY, offX, offY, rowTop, j, component, mapMouseX, mapMouseY, powered);

                boolean[] visibility = computeLinkpointVisibility(i, j, hoverTarget);
                boolean forceLeftHighlight = activeModal instanceof ConnectingState cs0 && cs0.rowIndex() == i && cs0.slotIndex() == j && cs0.fromLeft();
                boolean forceRightHighlight = activeModal instanceof ConnectingState cs1 && cs1.rowIndex() == i && cs1.slotIndex() == j && !cs1.fromLeft();
                if (visibility[0] || visibility[1]) {
                    int compLeft = componentScreenX(funcScreenX, offX, j);
                    int compRight = compLeft + COMPONENT_WIDTH;
                    int linkY = Math.round(funcScreenY + rowTop + LINKPOINT_Y_IN_ROW - offY);
                    if (visibility[0]) {
                        drawLinkpoint(graphics, compLeft - LINKPOINT_GAP - LINKPOINT_WIDTH, linkY, mouseX, mouseY, forceLeftHighlight);
                    }
                    if (visibility[1]) {
                        drawLinkpoint(graphics, compRight + LINKPOINT_GAP, linkY, mouseX, mouseY, forceRightHighlight);
                    }
                }
            }

            drawAddButton(graphics, funcScreenX, funcScreenY, offX, offY, rowTop, effectiveComponentCount(row), mapMouseX, mapMouseY);
        }

        for (int i = 0; i < rows.size(); i++) {
            int separatorTop = rowTopOf(i) + ROW_HEIGHT;
            drawSeparator(graphics, funcScreenX, funcScreenY, offY, separatorTop, mapMouseX, mapMouseY);
        }

        int addRowTop = rowTopOf(rows.size());
        drawAddButton(graphics, funcScreenX, funcScreenY, offX, offY, addRowTop, 0, mapMouseX, mapMouseY);

        drawConnectionLines(graphics, funcScreenX, funcScreenY, offX, offY, mouseX, mouseY);

        if (activeModal instanceof ConnectingState cs) {
            ComponentRef hoveredCandidate = cs.findHoveredCandidate(mouseX, mouseY);
            if (hoveredCandidate != null) {
                renderConnectionPreview(graphics, funcScreenX, funcScreenY, offX, offY, cs, hoveredCandidate);
            }
        }

        graphics.disableScissor();

        if (activeModal != null) {
            activeModal.render(graphics, mouseX, mouseY, partialTicks);
        }
    }

    /**
     * 连接状态下，鼠标精确悬浮在某个候选上时，用 prelinkline/prelinknode/pre.png（都带
     * {@link #PREVIEW_ALPHA} 透明度）预览"如果点击这个候选，连接会长什么样"：如果这次
     * 连接会导致起点或候选挪动位置，就在挪动后的新槽位画一个 pre.png 幽灵；连接线本身
     * 复用 {@link #addCrossRowSegments} 算出的同一套折线结构（同行的话就是一小段直线），
     * 只是换成预览贴图。整个预览画在裁剪区域内所有其它元素的最上层。
     */
    private void renderConnectionPreview(GuiGraphics graphics, int funcScreenX, int funcScreenY, float offX, float offY,
                                         ConnectingState cs, ComponentRef candidate) {
        ComponentRef origin = new ComponentRef(cs.rowIndex(), cs.slotIndex());
        boolean fromLeft = cs.fromLeft();
        MoveInfo move = cs.moveInfoFor(candidate);

        ComponentRef originDisplay = origin;
        ComponentRef candidateDisplay = candidate;
        if (move != null) {
            if (move.mover().equals(origin)) {
                originDisplay = new ComponentRef(origin.rowIndex(), move.newSlot());
            } else {
                candidateDisplay = new ComponentRef(candidate.rowIndex(), move.newSlot());
            }
        }

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, PREVIEW_ALPHA);

        if (move != null) {
            int rowTop = rowTopOf(move.mover().rowIndex());
            int drawX = componentScreenX(funcScreenX, offX, move.newSlot());
            int drawY = componentScreenY(funcScreenY, offY, rowTop);
            graphics.blit(PRE_COMPONENT_TEXTURE, drawX, drawY, 0, 0, COMPONENT_WIDTH, COMPONENT_HEIGHT,
                    COMPONENT_WIDTH, COMPONENT_HEIGHT);
        }

        ComponentRef front = fromLeft ? candidateDisplay : originDisplay;
        ComponentRef back = fromLeft ? originDisplay : candidateDisplay;

        if (front.rowIndex() == back.rowIndex()) {
            int lineX = componentScreenX(funcScreenX, offX, front.slotIndex()) + COMPONENT_WIDTH;
            int lineY = Math.round(funcScreenY + rowTopOf(front.rowIndex()) + LINKPOINT_Y_IN_ROW - offY);
            for (int k = 0; k < COMPONENT_GAP; k++) {
                graphics.blit(PRELINKLINE_TEXTURE, lineX + k, lineY, 0, 0, 1, LINKPOINT_HEIGHT, 1, LINKPOINT_HEIGHT);
            }
        } else {
            List<LineSegment> segments = new ArrayList<>();
            addCrossRowSegments(segments, funcScreenX, funcScreenY, offX, offY,
                    front.rowIndex(), front.slotIndex(), back.rowIndex(), back.slotIndex());
            for (LineSegment segment : segments) {
                if (segment.isNode()) {
                    blitRotatedSquare(graphics, PRELINKNODE_TEXTURE, segment.x(), segment.y(), LINKNODE_SIZE, segment.nodeRotation());
                } else if (segment.vertical()) {
                    for (int k = 0; k < segment.h(); k++) {
                        blitRotated90(graphics, PRELINKLINE_TEXTURE, segment.x(), segment.y() + k, LINKPOINT_HEIGHT, 1);
                    }
                } else {
                    for (int k = 0; k < segment.w(); k++) {
                        graphics.blit(PRELINKLINE_TEXTURE, segment.x() + k, segment.y(), 0, 0, 1, segment.h(), 1, segment.h());
                    }
                }
            }
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** 连接线两遍绘制：先画所有非高亮的，再单独画一遍所有命中鼠标的（高亮贴图），保证高亮永远在最上层。 */
    private void drawConnectionLines(GuiGraphics graphics, int funcScreenX, int funcScreenY, float offX, float offY, int mouseX, int mouseY) {
        List<LineSegment> segments = collectLineSegments(funcScreenX, funcScreenY, offX, offY);

        // 弹窗/连接状态打开时，大部分连线不应该再响应悬浮；连接状态下唯一的例外是
        // "已被占用、可以作为多输入/输出候选"的那些连线——它们和普通模式一样，只是
        // 鼠标真悬浮上去才高亮（重叠的全部一起亮），不是持续强制高亮。
        java.util.Set<ConnectionHit> eligibleForHover;
        if (activeModal == null) {
            eligibleForHover = null; // null 表示不限制，全部按正常悬浮处理
        } else if (activeModal instanceof ConnectingState cs) {
            eligibleForHover = cs.occupiedCandidateConnectionHits();
        } else {
            eligibleForHover = java.util.Set.of();
        }

        java.util.Set<ConnectionHit> hovered = new java.util.HashSet<>();
        for (LineSegment segment : segments) {
            if (eligibleForHover != null && !eligibleForHover.contains(segment.id())) continue;
            if (isPointInRect(mouseX, mouseY, segment.x(), segment.y(), segment.w(), segment.h())) {
                hovered.add(segment.id());
            }
        }
        for (LineSegment segment : segments) {
            if (!hovered.contains(segment.id())) drawSegment(graphics, segment, false);
        }
        for (LineSegment segment : segments) {
            if (hovered.contains(segment.id())) drawSegment(graphics, segment, true);
        }

        // 非门标记同样分两遍画，且整体压在所有连接线（含高亮的）之上：先画所有非高亮
        // 状态的标记，再单独画一遍所有"输出侧有连线正在高亮"的标记（高亮贴图），
        // 保证高亮的非门标记永远在最上层。
        drawNotMarks(graphics, funcScreenX, funcScreenY, offX, offY, hovered, false);
        drawNotMarks(graphics, funcScreenX, funcScreenY, offX, offY, hovered, true);
    }

    /**
     * 非门标记跟随输出模块本身渲染（不跟随任何一条具体连线），紧贴模块右边缘画在
     * 连接线的起始点上。一个模块只要它作为输出侧的某一条连线当前正处于高亮状态
     * （无论是同行还是隔行连接、无论具体是哪一条），它的非门标记就整体切到高亮贴图。
     */
    private void drawNotMarks(GuiGraphics graphics, int funcScreenX, int funcScreenY, float offX, float offY,
                              java.util.Set<ConnectionHit> hovered, boolean highlightedPass) {
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            for (int s = 0; s < row.components.size(); s++) {
                PlacedComponent component = row.components.get(s);
                if (component == null || !component.notMarked) continue;
                boolean highlighted = hovered.contains(new ConnectionHit(r, s, false))
                        || hovered.contains(new ConnectionHit(r, s, true));
                if (highlighted != highlightedPass) continue;
                int drawX = componentScreenX(funcScreenX, offX, s) + COMPONENT_WIDTH;
                int drawY = Math.round(funcScreenY + rowTopOf(r) + NOT_MARK_Y_IN_ROW - offY);
                ResourceLocation texture = highlighted ? NOT_MARK_TEXTURE_HOVER : NOT_MARK_TEXTURE;
                graphics.blit(texture, drawX, drawY, 0, 0, NOT_MARK_WIDTH, NOT_MARK_HEIGHT, NOT_MARK_WIDTH, NOT_MARK_HEIGHT);
            }
        }
    }

    private void drawSegment(GuiGraphics graphics, LineSegment segment, boolean highlighted) {
        if (segment.isNode()) {
            ResourceLocation texture = highlighted ? LINKNODE_TEXTURE_HOVER : LINKNODE_TEXTURE;
            blitRotatedSquare(graphics, texture, segment.x(), segment.y(), LINKNODE_SIZE, segment.nodeRotation());
            return;
        }
        ResourceLocation texture = highlighted ? LINKLINE_TEXTURE_HOVER : LINKLINE_TEXTURE;
        if (segment.vertical()) {
            for (int k = 0; k < segment.h(); k++) {
                blitRotated90(graphics, texture, segment.x(), segment.y() + k, LINKPOINT_HEIGHT, 1);
            }
        } else {
            for (int k = 0; k < segment.w(); k++) {
                graphics.blit(texture, segment.x() + k, segment.y(), 0, 0, 1, segment.h(), 1, segment.h());
            }
        }
    }

    private void drawComponent(GuiGraphics graphics, int funcScreenX, int funcScreenY, float offX, float offY,
                               int rowTop, int slotIndex, PlacedComponent component, int mouseX, int mouseY, boolean powered) {
        int drawX = componentScreenX(funcScreenX, offX, slotIndex);
        int drawY = componentScreenY(funcScreenY, offY, rowTop);
        boolean hovered = isPointInRect(mouseX, mouseY, drawX, drawY, COMPONENT_WIDTH, COMPONENT_HEIGHT)
                && isWithinFunctionalArea(funcScreenX, funcScreenY, mouseX, mouseY);
        ResourceLocation texture = hovered ? component.type.hoverTexture : component.type.texture;
        graphics.blit(texture, drawX, drawY, 0, 0, COMPONENT_WIDTH, COMPONENT_HEIGHT,
                COMPONENT_WIDTH, COMPONENT_HEIGHT);

        if (component.type == ComponentType.ITEM_LINK) {
            drawComponentItemIcon(graphics, drawX + ITEM_ICON_1_X, drawY + ITEM_ICON_1_Y, component.itemSlot1);
            drawComponentItemIcon(graphics, drawX + ITEM_ICON_2_X, drawY + ITEM_ICON_2_Y, component.itemSlot2);
        } else if (component.type == ComponentType.LABEL_LINK) {
            drawComponentLabelText(graphics, drawX + LABEL_TEXT_X, drawY + LABEL_TEXT_Y, component.labelText);
        }

        if (powered) {
            graphics.blit(POWERED_TEXTURE, drawX, drawY, 0, 0, COMPONENT_WIDTH, COMPONENT_HEIGHT,
                    COMPONENT_WIDTH, COMPONENT_HEIGHT);
        }
    }

    private void drawComponentItemIcon(GuiGraphics graphics, int x, int y, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        graphics.renderItem(stack, x, y);
    }

    /**
     * 文本终端显示区域：未超出宽度直接画一行；超出宽度改为横向滚动（复制一份接在
     * 后面首尾相连，滚完一轮无缝回到开头），不做截断也不换行，逻辑仿照进程面板
     * 进程卡片下方最后日志的滚动展示。裁剪框用的是原始屏幕像素坐标，这个区域
     * 本身不会跟着任何额外的滚动矩阵叠加，所以不需要像进程面板那样额外减去外层
     * 滚动量。
     */
    private void drawComponentLabelText(GuiGraphics graphics, int areaX, int areaY, String text) {
        if (text == null || text.isEmpty()) return;
        int totalWidth = font.width(text);
        int textY = areaY + (LABEL_TEXT_HEIGHT - font.lineHeight) / 2;

        graphics.enableScissor(areaX, areaY, areaX + LABEL_TEXT_WIDTH, areaY + LABEL_TEXT_HEIGHT);
        if (totalWidth <= LABEL_TEXT_WIDTH) {
            drawLabelText(graphics, text, areaX, textY);
        } else {
            int cycle = totalWidth + LABEL_SCROLL_GAP;
            float scrollPixels = labelScrollTicks * LABEL_SCROLL_SPEED;
            int offset = Math.floorMod((int) scrollPixels, cycle);
            int drawX = areaX - offset;
            drawLabelText(graphics, text, drawX, textY);
            drawLabelText(graphics, text, drawX + cycle, textY);
        }
        graphics.disableScissor();
    }

    private void drawLabelText(GuiGraphics graphics, String text, float x, float y) {
        graphics.drawString(font, text, x + LABEL_TEXT_SHADOW_OFFSET, y + LABEL_TEXT_SHADOW_OFFSET, LABEL_TEXT_SHADOW_COLOR, false);
        graphics.drawString(font, text, x, y, LABEL_TEXT_COLOR, false);
    }

    private void drawLinkpoint(GuiGraphics graphics, int drawX, int drawY, int mouseX, int mouseY, boolean forceHighlight) {
        boolean hovered = forceHighlight || isPointInRect(mouseX, mouseY, drawX, drawY, LINKPOINT_WIDTH, LINKPOINT_HEIGHT);
        ResourceLocation texture = hovered ? LINKPOINT_TEXTURE_HOVER : LINKPOINT_TEXTURE;
        graphics.blit(texture, drawX, drawY, 0, 0, LINKPOINT_WIDTH, LINKPOINT_HEIGHT, LINKPOINT_WIDTH, LINKPOINT_HEIGHT);
    }

    private void drawAddButton(GuiGraphics graphics, int funcScreenX, int funcScreenY,
                               float offX, float offY, int rowTop, int slotIndex, int mouseX, int mouseY) {
        int drawX = addButtonScreenX(funcScreenX, offX, slotIndex);
        int drawY = addButtonScreenY(funcScreenY, offY, rowTop);
        boolean hovered = isPointInRect(mouseX, mouseY, drawX, drawY, ADD_BUTTON_WIDTH, ADD_BUTTON_HEIGHT)
                && isWithinFunctionalArea(funcScreenX, funcScreenY, mouseX, mouseY);
        ResourceLocation texture = hovered ? ADD_BUTTON_TEXTURE_HOVER : ADD_BUTTON_TEXTURE;
        graphics.blit(texture, drawX, drawY, 0, 0, ADD_BUTTON_WIDTH, ADD_BUTTON_HEIGHT,
                ADD_BUTTON_WIDTH, ADD_BUTTON_HEIGHT);
    }

    private void drawSeparator(GuiGraphics graphics, int funcScreenX, int funcScreenY,
                               float offY, int separatorTop, int mouseX, int mouseY) {
        int drawX = funcScreenX + LINE_X_IN_FUNC;
        int drawY = Math.round(funcScreenY + separatorTop - offY);
        boolean hovered = isPointInRect(mouseX, mouseY, drawX, drawY, LINE_WIDTH, LINE_HEIGHT)
                && isWithinFunctionalArea(funcScreenX, funcScreenY, mouseX, mouseY);
        ResourceLocation texture = hovered ? LINE_TEXTURE_HOVER : LINE_TEXTURE;
        graphics.blit(texture, drawX, drawY, 0, 0, LINE_WIDTH, LINE_HEIGHT, LINE_WIDTH, LINE_HEIGHT);
    }

    /** 沿 X 轴镜像绘制一张贴图，原理见方法体内注释。 */
    private void blitMirroredX(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
        graphics.blit(texture, x, y, width, height, width, 0, -width, height, width, height);
    }

    /**
     * 把原生 nativeWidth x nativeHeight 的贴图旋转 90° 后画成 rotatedWidth x rotatedHeight
     * 的样子（这里固定用于把 1x4 的 linkline 转成 4x1，供竖直方向的连接线逐像素堆叠）。
     * 用 PoseStack 绕自身中心旋转实现，这是 Minecraft GUI 里旋转贴图的标准写法
     * （PoseStack.mulPose + Axis.ZP.rotationDegrees），方法签名已经核实过。
     */
    private void blitRotated90(GuiGraphics graphics, ResourceLocation texture, int x, int y, int rotatedWidth, int rotatedHeight) {
        int nativeWidth = rotatedHeight;
        int nativeHeight = rotatedWidth;
        graphics.pose().pushPose();
        graphics.pose().translate(x + rotatedWidth / 2.0, y + rotatedHeight / 2.0, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(90));
        graphics.pose().translate(-nativeWidth / 2.0, -nativeHeight / 2.0, 0);
        graphics.blit(texture, 0, 0, 0, 0, nativeWidth, nativeHeight, nativeWidth, nativeHeight);
        graphics.pose().popPose();
    }

    /**
     * 把一张 size x size 的正方形贴图绕自身中心顺时针旋转 angleDegrees 度画出来（linknode
     * 是 6x6 的正方形，宽高相同，旋转前后贴图尺寸不变，不需要像 {@link #blitRotated90}
     * 那样处理宽高互换）。同样用 PoseStack 绕中心旋转实现。
     */
    private void blitRotatedSquare(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size, float angleDegrees) {
        graphics.pose().pushPose();
        graphics.pose().translate(x + size / 2.0, y + size / 2.0, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(angleDegrees));
        graphics.pose().translate(-size / 2.0, -size / 2.0, 0);
        graphics.blit(texture, 0, 0, 0, 0, size, size, size, size);
        graphics.pose().popPose();
    }

    private boolean isOnConfirmButton(double mouseX, double mouseY) {
        return isPointInRect(mouseX, mouseY, guiLeft + CONFIRM_BUTTON_X_OFFSET, guiTop + CONFIRM_BUTTON_Y_OFFSET,
                CONFIRM_BUTTON_SIZE, CONFIRM_BUTTON_SIZE);
    }

    private int hitTestAddButtonRow(int funcScreenX, int funcScreenY, float offX, float offY, double mouseX, double mouseY) {
        for (int i = 0; i <= rows.size(); i++) {
            int slotIndex = (i < rows.size()) ? effectiveComponentCount(rows.get(i)) : 0;
            int rowTop = rowTopOf(i);
            int drawX = addButtonScreenX(funcScreenX, offX, slotIndex);
            int drawY = addButtonScreenY(funcScreenY, offY, rowTop);
            if (isPointInRect(mouseX, mouseY, drawX, drawY, ADD_BUTTON_WIDTH, ADD_BUTTON_HEIGHT)) {
                return i;
            }
        }
        return -1;
    }

    /** 命中了第几条分隔线（分隔线 i 在第 i 行之后），没命中返回 -1；点击命中后会在 i+1 处插入新行。 */
    private int hitTestSeparator(int funcScreenX, int funcScreenY, float offY, double mouseX, double mouseY) {
        for (int i = 0; i < rows.size(); i++) {
            int drawX = funcScreenX + LINE_X_IN_FUNC;
            int drawY = Math.round(funcScreenY + rowTopOf(i) + ROW_HEIGHT - offY);
            if (isPointInRect(mouseX, mouseY, drawX, drawY, LINE_WIDTH, LINE_HEIGHT)) {
                return i;
            }
        }
        return -1;
    }

    /** 精确命中的是哪个已放置组件（行号+槽位号），没命中返回 null。 */
    private ComponentRef hitTestPlacedComponent(int funcScreenX, int funcScreenY, float offX, float offY, double mouseX, double mouseY) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowTop = rowTopOf(i);
            for (int j = 0; j < row.components.size(); j++) {
                if (row.components.get(j) == null) continue;
                int drawX = componentScreenX(funcScreenX, offX, j);
                int drawY = componentScreenY(funcScreenY, offY, rowTop);
                if (isPointInRect(mouseX, mouseY, drawX, drawY, COMPONENT_WIDTH, COMPONENT_HEIGHT)) {
                    return new ComponentRef(i, j);
                }
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int funcScreenX = guiLeft + FUNC_X;
        int funcScreenY = guiTop + FUNC_Y;

        if (activeModal != null) {
            if (isOnConfirmButton(mouseX, mouseY)) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            if (activeModal instanceof ConnectingState && button == 1) {
                // 连接状态下，右键不退出连接状态、也不做任何连接判断，改成拖动地图。
                if (isWithinFunctionalArea(funcScreenX, funcScreenY, (int) mouseX, (int) mouseY)) {
                    draggingMap = true;
                }
                return true;
            }
            if (button == 0) {
                if (activeModal.shouldCloseOnClick(mouseX, mouseY, button)) {
                    activeModal = null;
                }
            } else {
                // 右键：其它弹窗（组件选择）无论点在哪里，都只是退出，不触发别的交互。
                activeModal = null;
            }
            return true;
        }

        if (button == 1) {
            if (isWithinFunctionalArea(funcScreenX, funcScreenY, (int) mouseX, (int) mouseY)) {
                float offX = scrollX.getValue(1.0f);
                float offY = scrollY.getValue(1.0f);

                // 重叠的连接线一次右键全部断开：每断开一条，被影响的行可能因为"滑落"
                // 重新排布，之前算好的一批 ConnectionHit 下标可能失效，所以每次都用
                // 当前鼠标位置重新检测，直到这个位置下再也测不到任何连接线为止。
                int guard = 0;
                boolean disconnectedAny = false;
                List<ConnectionHit> hits;
                while (guard++ < 32 && !(hits = hitTestConnectionLines(funcScreenX, funcScreenY, offX, offY, mouseX, mouseY)).isEmpty()) {
                    disconnect(hits.get(0));
                    disconnectedAny = true;
                }
                if (disconnectedAny) return true;

                // 右键命中一个空行的"添加组件"按钮：删除这一行（只有空行才能删）。
                int hitRow = hitTestAddButtonRow(funcScreenX, funcScreenY, offX, offY, mouseX, mouseY);
                if (hitRow >= 0 && hitRow < rows.size() && effectiveComponentCount(rows.get(hitRow)) == 0) {
                    deleteRow(hitRow);
                    return true;
                }

                // 右键命中一个已放置的组件：断开它所有连接并删除它，deleteComponent 内部会调用 settleAll 处理后续滑落。
                ComponentRef hitComponent = hitTestPlacedComponent(funcScreenX, funcScreenY, offX, offY, mouseX, mouseY);
                if (hitComponent != null) {
                    deleteComponent(hitComponent.rowIndex(), hitComponent.slotIndex());
                    return true;
                }

                // 空白处右键：和左键一样，开始拖动地图。
                draggingMap = true;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (button == 0) {
            if (isWithinFunctionalArea(funcScreenX, funcScreenY, (int) mouseX, (int) mouseY)) {
                float offX = scrollX.getValue(1.0f);
                float offY = scrollY.getValue(1.0f);

                int hitRow = hitTestAddButtonRow(funcScreenX, funcScreenY, offX, offY, mouseX, mouseY);
                if (hitRow == rows.size()) {
                    rows.add(new Row());
                    scrollY.chase(maxScrollY(), SCROLL_CHASE_SPEED, LerpedFloat.Chaser.EXP);
                    return true;
                } else if (hitRow >= 0) {
                    activeModal = new ComponentPickerPopup((int) mouseX, (int) mouseY, hitRow);
                    return true;
                }

                LinkpointHit linkHit = hitTestLinkpoint(funcScreenX, funcScreenY, offX, offY, mouseX, mouseY);
                if (linkHit != null) {
                    activeModal = new ConnectingState(linkHit.rowIndex(), linkHit.slotIndex(), linkHit.isLeftPoint());
                    return true;
                }

                int separatorHit = hitTestSeparator(funcScreenX, funcScreenY, offY, mouseX, mouseY);
                if (separatorHit >= 0) {
                    insertRowAt(separatorHit + 1);
                    return true;
                }

                ComponentRef clickedComponent = hitTestPlacedComponent(funcScreenX, funcScreenY, offX, offY, mouseX, mouseY);
                if (clickedComponent != null) {
                    openComponentEditor(clickedComponent);
                    return true;
                }

                List<ConnectionHit> lineHits = hitTestConnectionLines(funcScreenX, funcScreenY, offX, offY, mouseX, mouseY);
                if (!lineHits.isEmpty()) {
                    toggleNotMarks(lineHits);
                    return true;
                }

                draggingMap = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingMap = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingMap && (button == 0 || button == 1)) {
            int maxX = maxScrollX();
            int maxY = maxScrollY();
            float targetX = Mth.clamp(scrollX.getChaseTarget() - (float) dragX, 0, maxX);
            float targetY = Mth.clamp(scrollY.getChaseTarget() - (float) dragY, 0, maxY);
            scrollX.chase(targetX, DRAG_CHASE_SPEED, LerpedFloat.Chaser.EXP);
            scrollY.chase(targetY, DRAG_CHASE_SPEED, LerpedFloat.Chaser.EXP);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollAmountX, double scrollAmountY) {
        if (activeModal != null && !(activeModal instanceof ConnectingState)) {
            return true;
        }
        int maxY = maxScrollY();
        if (maxY > 0 && scrollAmountY != 0) {
            float newTarget = scrollY.getChaseTarget() - (float) scrollAmountY * SCROLL_STEP;
            newTarget = Mth.clamp(newTarget, 0, maxY);
            scrollY.chase(newTarget, SCROLL_CHASE_SPEED, LerpedFloat.Chaser.EXP);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollAmountX, scrollAmountY);
    }

    private enum ComponentType {
        ITEM_LINK(ITEMLINK_TEXTURE, ITEMLINK_TEXTURE_HOVER),
        LABEL_LINK(LABELLINK_TEXTURE, LABELLINK_TEXTURE_HOVER),
        AND_GATE(ANDGATE_TEXTURE, ANDGATE_TEXTURE_HOVER),
        OR_GATE(ORGATE_TEXTURE, ORGATE_TEXTURE_HOVER);

        private final ResourceLocation texture;
        private final ResourceLocation hoverTexture;

        ComponentType(ResourceLocation texture, ResourceLocation hoverTexture) {
            this.texture = texture;
            this.hoverTexture = hoverTexture;
        }
    }

    private record ComponentRef(int rowIndex, int slotIndex) {
    }

    /** 隔行"镜像"候选：fixedRef 是固定不动、被连接的那个模块；originNewSlot 是起点为了对齐它需要挪到的新槽位。 */
    private record MirrorCandidate(ComponentRef fixedRef, int originNewSlot) {
    }

    /** 预览用：谁需要挪动(mover)、挪到哪个新槽位(newSlot)。 */
    private record MoveInfo(ComponentRef mover, int newSlot) {
    }

    private static final class PlacedComponent {
        private final ComponentType type;
        private final List<ComponentRef> inputConnections = new ArrayList<>();
        private final List<ComponentRef> outputConnections = new ArrayList<>();
        /** 非门标记：跟随模块本身，不跟随任何一条具体的连接线；由输出侧连线的左键点击切换。 */
        private boolean notMarked = false;
        /** 给物品终端预留的两个带顺序的物品数据位，给文本终端预留的文本数据位；具体怎么配置这几种终端是后续工作。 */
        private ItemStack itemSlot1 = ItemStack.EMPTY;
        private ItemStack itemSlot2 = ItemStack.EMPTY;
        private String labelText = "";

        private PlacedComponent(ComponentType type) {
            this.type = type;
        }
    }

    private static final class Row {
        private final List<PlacedComponent> components = new ArrayList<>();
    }

    private record LinkpointTarget(int rowIndex, int slotIndex, boolean showLeft, boolean showRight) {
    }

    private record LinkpointHit(int rowIndex, int slotIndex, boolean isLeftPoint) {
    }

    /** rowIndex+leftSlotIndex 标识一条连接：同行连接时就是它左边那个模块的槽位；隔行连接时是"前方"（输出侧）模块的行号+槽位。 */
    private record ConnectionHit(int rowIndex, int leftSlotIndex, boolean crossRow) {
    }

    /**
     * 一段可点击/可高亮的连接线可视片段：可能是普通线段、旋转90°的竖线段、或者一个
     * linknode 方块。nodeRotation 只对 isNode=true 的片段有意义，表示这个节点的贴图
     * 要顺时针旋转多少度（原始贴图连接左侧与下侧）。
     */
    private record LineSegment(ConnectionHit id, int x, int y, int w, int h, boolean isNode, boolean vertical, float nodeRotation) {
        private LineSegment(ConnectionHit id, int x, int y, int w, int h, boolean isNode) {
            this(id, x, y, w, h, isNode, false, 0f);
        }

        private LineSegment(ConnectionHit id, int x, int y, int w, int h, boolean isNode, boolean vertical) {
            this(id, x, y, w, h, isNode, vertical, 0f);
        }
    }

    private interface ModalPopup {
        void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks);

        boolean shouldCloseOnClick(double mouseX, double mouseY, int button);
    }

    private final class ComponentPickerPopup implements ModalPopup {

        private static final int ROW_HEIGHT = 13;
        private static final int SIDE_WIDTH = 2;
        private static final int TEXT_LEFT_PADDING = 4;
        private static final int TEXT_RIGHT_PADDING = 4;
        private static final int TEXT_COLOR = 0xF8F8F8;
        private static final int TEXT_SHADOW_COLOR = 0x747474;
        private static final float TEXT_SHADOW_OFFSET = 0.5f;

        private final int x;
        private final int y;
        private final int targetRow;
        private final List<Component> labels;
        private final List<ComponentType> optionTypes;
        private final int innerWidth;
        private final int width;

        private ComponentPickerPopup(int x, int y, int targetRow) {
            this.x = x;
            this.y = y;
            this.targetRow = targetRow;
            this.optionTypes = List.of(
                    ComponentType.ITEM_LINK, ComponentType.LABEL_LINK,
                    ComponentType.AND_GATE, ComponentType.OR_GATE
            );
            this.labels = List.of(
                    Component.translatable("createimp.gui.redstone_link_router.component_picker.item_link"),
                    Component.translatable("createimp.gui.redstone_link_router.component_picker.label_link"),
                    Component.translatable("createimp.gui.redstone_link_router.component_picker.and_gate"),
                    Component.translatable("createimp.gui.redstone_link_router.component_picker.or_gate")
            );
            int maxTextWidth = 0;
            for (Component label : labels) {
                maxTextWidth = Math.max(maxTextWidth, font.width(label));
            }
            this.innerWidth = TEXT_LEFT_PADDING + maxTextWidth + TEXT_RIGHT_PADDING;
            this.width = innerWidth + SIDE_WIDTH * 2;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            for (int i = 0; i < labels.size(); i++) {
                int rowY = y + i * ROW_HEIGHT;
                boolean hovered = isPointInRect(mouseX, mouseY, x, rowY, width, ROW_HEIGHT);

                graphics.blit(WINDOW_SIDE_TEXTURE, x, rowY, 0, 0, SIDE_WIDTH, ROW_HEIGHT, SIDE_WIDTH, ROW_HEIGHT);

                ResourceLocation insideTexture = hovered ? WINDOW_INSIDE_TEXTURE_HOVER : WINDOW_INSIDE_TEXTURE;
                for (int px = 0; px < innerWidth; px++) {
                    graphics.blit(insideTexture, x + SIDE_WIDTH + px, rowY, 0, 0, 1, ROW_HEIGHT, 1, ROW_HEIGHT);
                }

                blitMirroredX(graphics, WINDOW_SIDE_TEXTURE, x + SIDE_WIDTH + innerWidth, rowY, SIDE_WIDTH, ROW_HEIGHT);

                String labelStr = labels.get(i).getString();
                float textX = x + SIDE_WIDTH + TEXT_LEFT_PADDING;
                float textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2f + 1;
                graphics.drawString(font, labelStr, textX + TEXT_SHADOW_OFFSET, textY + TEXT_SHADOW_OFFSET, TEXT_SHADOW_COLOR, false);
                graphics.drawString(font, labelStr, textX, textY, TEXT_COLOR, false);
            }
        }

        @Override
        public boolean shouldCloseOnClick(double mouseX, double mouseY, int button) {
            for (int i = 0; i < labels.size(); i++) {
                int rowY = y + i * ROW_HEIGHT;
                if (isPointInRect(mouseX, mouseY, x, rowY, width, ROW_HEIGHT)) {
                    addComponent(optionTypes.get(i));
                    break;
                }
            }
            return true;
        }

        private void addComponent(ComponentType type) {
            Row row = rows.get(targetRow);
            int idx = effectiveComponentCount(row);
            while (row.components.size() > idx) {
                row.components.remove(row.components.size() - 1);
            }
            PlacedComponent component = new PlacedComponent(type);
            if (type == ComponentType.LABEL_LINK) {
                String languageCode = Minecraft.getInstance().getLanguageManager().getSelected();
                component.labelText = LabeledRedstoneLinkBlockEntity.defaultFrequencyFor(languageCode);
            }
            row.components.add(component);

            int slotRightEdge = rowAddSlotRightEdge(row.components.size());
            int viewRight = Math.round(scrollX.getChaseTarget()) + FUNC_WIDTH;
            if (slotRightEdge > viewRight) {
                int target = Mth.clamp(slotRightEdge - FUNC_WIDTH, 0, maxScrollX());
                scrollX.chase(target, SCROLL_CHASE_SPEED, LerpedFloat.Chaser.EXP);
            }
        }
    }

    /**
     * 点击一个可用连接点之后进入的"连接状态"。候选分同行相邻（沿用最早的实现）和隔行
     * 两类，见 {@link #findCrossRowCandidates()}。
     */
    private final class ConnectingState implements ModalPopup {

        private final int rowIndex;
        private final int slotIndex;
        private final boolean fromLeft;
        private final List<ComponentRef> crossRowCandidates;
        private final List<MirrorCandidate> mirrorCandidates;

        private ConnectingState(int rowIndex, int slotIndex, boolean fromLeft) {
            this.rowIndex = rowIndex;
            this.slotIndex = slotIndex;
            this.fromLeft = fromLeft;
            this.crossRowCandidates = findCrossRowCandidates();
            this.mirrorCandidates = findMirrorCandidates();
        }

        private int rowIndex() {
            return rowIndex;
        }

        private int slotIndex() {
            return slotIndex;
        }

        private boolean fromLeft() {
            return fromLeft;
        }

        /**
         * 所有候选（"候选挪动"+"起点挪动"镜像候选），供渲染/点击统一遍历。同行相邻
         * 候选不再单独处理——它只是"候选挪动"在 steps=0 时的特例，已经被
         * {@link #findCrossRowCandidates()} 统一覆盖。
         */
        private List<ComponentRef> allCandidates() {
            List<ComponentRef> all = new ArrayList<>(crossRowCandidates);
            for (MirrorCandidate mc : mirrorCandidates) all.add(mc.fixedRef());
            return all;
        }

        private Integer mirrorNewOriginSlot(ComponentRef ref) {
            for (MirrorCandidate mc : mirrorCandidates) {
                if (mc.fixedRef().equals(ref)) return mc.originNewSlot();
            }
            return null;
        }

        /**
         * 给定一个候选，判断这次连接建立后谁需要挪动、挪到哪个新槽位；返回 null 表示
         * 两边都不需要挪动（已经正好落在锚点列、或已占用的固定候选都是这样）。是否
         * 需要挪动只取决于候选当前列号是否等于锚点列，与候选是否和起点同一行无关——
         * 同一行里隔着空格的候选同样需要先挪到锚点列才能建立连接。
         */
        private MoveInfo moveInfoFor(ComponentRef candidate) {
            Integer mirrorSlot = mirrorNewOriginSlot(candidate);
            if (mirrorSlot != null) {
                return new MoveInfo(new ComponentRef(rowIndex, slotIndex), mirrorSlot);
            }
            int anchor = fromLeft ? slotIndex - 1 : slotIndex + 1;
            if (candidate.slotIndex() != anchor) {
                return new MoveInfo(candidate, anchor);
            }
            return null;
        }

        /** 当前鼠标精确悬浮在哪个候选上（不管是候选自己的点、还是它已有的连线），用于触发预览；没有则返回 null。 */
        private ComponentRef findHoveredCandidate(double mouseX, double mouseY) {
            for (ComponentRef candidate : allCandidates()) {
                boolean hit = isOccupiedOnNeededSide(candidate)
                        ? hitsExistingConnectionLine(candidate, mouseX, mouseY)
                        : hitsTargetPoint(candidate, mouseX, mouseY);
                if (hit) return candidate;
            }
            return null;
        }

        /** 某个候选在"这次连接需要提供的那一侧"是否已经被占用（占用了也允许作为多输入/输出的候选）。 */
        private boolean isOccupiedOnNeededSide(ComponentRef ref) {
            PlacedComponent c = rows.get(ref.rowIndex()).components.get(ref.slotIndex());
            return fromLeft ? !c.outputConnections.isEmpty() : !c.inputConnections.isEmpty();
        }

        /**
         * 跨行候选查找——对地图里除起点所在行以外的每一行、每一个现存模块，直接用
         * "候选所在的刚性组能不能整体平移到锚点列（起点所在的整个刚性组作为这次操作
         * 绝对不能被牵动的墙）"来验证；已经正好在锚点列的等价于挪0步，天然通过。
         * 不再需要"情况一/情况二"的划分，也不再需要单独的"死模块检测范围"——能不能
         * 移动完全由 {@link #shiftGroup} 的递归验证结果决定，而不是一个预先贴好的
         * 静态标签。
         * <p>
         * 搜索范围覆盖地图里的每一行，包括起点自己所在的那一行——同一行内隔着若干
         * 空位的候选，同样是"能不能挪到锚点列"这同一套问题，挪动到锚点列后自然就是
         * 紧邻起点，不需要为同行单独写判断；起点自己所在的那个位置会因为它自身所在
         * 的刚性组必然和 immutable 相交，被 {@link #shiftGroup} 开头的交集校验直接拦掉。
         */
        private List<ComponentRef> findCrossRowCandidates() {
            List<ComponentRef> result = new ArrayList<>();
            PlacedComponent originComponent = rows.get(rowIndex).components.get(slotIndex);
            Set<PlacedComponent> immutable = computeRigidGroup(originComponent);
            int anchor = fromLeft ? slotIndex - 1 : slotIndex + 1;

            for (int r = 0; r < rows.size(); r++) {
                Row row = rows.get(r);
                for (int k = 0; k < row.components.size(); k++) {
                    PlacedComponent candidate = row.components.get(k);
                    if (candidate == null) continue;
                    if (r == rowIndex && k == slotIndex) continue;
                    int steps = anchor - k;
                    if (steps < 0) continue;
                    if (shiftGroup(candidate, 1, steps, immutable, true)) {
                        result.add(new ComponentRef(r, k));
                    }
                }
            }
            return result;
        }

        /**
         * "镜像"候选：上面 {@link #findCrossRowCandidates()} 处理的是"候选模块在锚点
         * 位置或者锚点前方，把候选挪过来"的情况；这里反过来处理"候选模块固定在锚点后方
         * （没法挪过来，因为我们的搬运机制只会把东西往列号变大方向推，不会往回拉）、
         * 改成把起点自己往列号变大方向挪，去对齐这个固定不动的候选"的情况。
         * <p>
         * 这次候选自己所在的刚性组才是"墙"（因为它不动），验证的是"起点所在的刚性组
         * 能不能整体平移到新槽位"。
         * <p>
         * 搜索范围同样覆盖每一行，包括起点自己所在的那一行：如果同一行里锚点更远处
         * 存在一个够不着（拉不回来）的候选，就改成把起点自己往后推去对齐它。
         */
        private List<MirrorCandidate> findMirrorCandidates() {
            List<MirrorCandidate> result = new ArrayList<>();
            PlacedComponent originComponent = rows.get(rowIndex).components.get(slotIndex);
            int anchor = fromLeft ? slotIndex - 1 : slotIndex + 1;

            for (int r = 0; r < rows.size(); r++) {
                Row row = rows.get(r);
                for (int k = anchor + 1; k < row.components.size(); k++) {
                    PlacedComponent candidate = row.components.get(k);
                    if (candidate == null) continue;
                    if (r == rowIndex && k == slotIndex) continue;
                    int newOriginSlot = fromLeft ? k + 1 : k - 1;
                    if (newOriginSlot <= slotIndex) continue;
                    Set<PlacedComponent> immutable = computeRigidGroup(candidate);
                    int steps = newOriginSlot - slotIndex;
                    if (shiftGroup(originComponent, 1, steps, immutable, true)) {
                        result.add(new MirrorCandidate(new ComponentRef(r, k), newOriginSlot));
                    }
                }
            }
            return result;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            // 起点常亮、候选点/候选连线的持续显示都在主渲染循环里统一处理，这里不需要再画东西。
        }

        @Override
        public boolean shouldCloseOnClick(double mouseX, double mouseY, int button) {
            for (ComponentRef candidate : allCandidates()) {
                boolean hit = isOccupiedOnNeededSide(candidate)
                        ? hitsExistingConnectionLine(candidate, mouseX, mouseY)
                        : hitsTargetPoint(candidate, mouseX, mouseY);
                if (!hit) continue;

                Integer mirrorSlot = mirrorNewOriginSlot(candidate);
                if (mirrorSlot != null) {
                    connectMirror(candidate, mirrorSlot);
                } else {
                    connectCrossRow(candidate);
                }
                return true;
            }
            return true;
        }

        /** 已被占用的候选，各自延伸出的连线（这次连接需要的那一侧）对应的 ConnectionHit 集合，供渲染时强制持续高亮。 */
        private java.util.Set<ConnectionHit> occupiedCandidateConnectionHits() {
            int funcScreenX = guiLeft + FUNC_X;
            int funcScreenY = guiTop + FUNC_Y;
            float offX = scrollX.getValue(1.0f);
            float offY = scrollY.getValue(1.0f);
            java.util.Set<ConnectionHit> result = new java.util.HashSet<>();
            for (ComponentRef candidate : allCandidates()) {
                if (!isOccupiedOnNeededSide(candidate)) continue;
                for (LineSegment segment : existingConnectionSegments(candidate, funcScreenX, funcScreenY, offX, offY)) {
                    result.add(segment.id());
                }
            }
            return result;
        }

        private boolean hitsTargetPoint(ComponentRef target, double mouseX, double mouseY) {
            int funcScreenX = guiLeft + FUNC_X;
            int funcScreenY = guiTop + FUNC_Y;
            float offX = scrollX.getValue(1.0f);
            float offY = scrollY.getValue(1.0f);
            int rowTop = rowTopOf(target.rowIndex());
            int compLeft = componentScreenX(funcScreenX, offX, target.slotIndex());
            int compRight = compLeft + COMPONENT_WIDTH;
            int linkY = Math.round(funcScreenY + rowTop + LINKPOINT_Y_IN_ROW - offY);
            int pointX = fromLeft ? compRight + LINKPOINT_GAP : compLeft - LINKPOINT_GAP - LINKPOINT_WIDTH;
            return isPointInRect(mouseX, mouseY, pointX, linkY, LINKPOINT_WIDTH, LINKPOINT_HEIGHT);
        }

        /** 已被占用的候选：点击它已有的、从"这次连接需要的那一侧"延伸出去的任意一条连线都算命中。 */
        private boolean hitsExistingConnectionLine(ComponentRef candidate, double mouseX, double mouseY) {
            int funcScreenX = guiLeft + FUNC_X;
            int funcScreenY = guiTop + FUNC_Y;
            float offX = scrollX.getValue(1.0f);
            float offY = scrollY.getValue(1.0f);
            for (LineSegment segment : existingConnectionSegments(candidate, funcScreenX, funcScreenY, offX, offY)) {
                if (isPointInRect(mouseX, mouseY, segment.x(), segment.y(), segment.w(), segment.h())) {
                    return true;
                }
            }
            return false;
        }

        /** candidate 在"这次连接需要提供的那一侧"上，目前已有的每一条连接各自对应的可视线段集合。 */
        private List<LineSegment> existingConnectionSegments(ComponentRef candidate, int funcScreenX, int funcScreenY, float offX, float offY) {
            List<LineSegment> segments = new ArrayList<>();
            PlacedComponent c = rows.get(candidate.rowIndex()).components.get(candidate.slotIndex());
            List<ComponentRef> existing = fromLeft ? c.outputConnections : c.inputConnections;
            for (ComponentRef otherRef : existing) {
                // candidate 提供输出(fromLeft=true) => candidate是前方(输出侧)；
                // candidate 提供输入(fromLeft=false) => candidate是后方(输入侧)。
                ComponentRef front = fromLeft ? candidate : otherRef;
                ComponentRef back = fromLeft ? otherRef : candidate;
                if (front.rowIndex() == back.rowIndex()) {
                    int lineX = componentScreenX(funcScreenX, offX, front.slotIndex()) + COMPONENT_WIDTH;
                    int lineY = Math.round(funcScreenY + rowTopOf(front.rowIndex()) + LINKPOINT_Y_IN_ROW - offY);
                    segments.add(new LineSegment(new ConnectionHit(front.rowIndex(), front.slotIndex(), false),
                            lineX, lineY, COMPONENT_GAP, LINKPOINT_HEIGHT, false));
                } else {
                    addCrossRowSegments(segments, funcScreenX, funcScreenY, offX, offY,
                            front.rowIndex(), front.slotIndex(), back.rowIndex(), back.slotIndex());
                }
            }
            return segments;
        }

        /**
         * 连接建立的通用逻辑，同行、隔行共用同一套实现：mover 是需要挪动位置的模块
         * （挪到 moverTargetSlot），fixed 是保持不动的那个（可能已被占用，无所谓，它
         * 所在的整个刚性组会被当成这次搬运绝对不能牵动的墙）。moverProvidesOutput
         * 表示 mover 在这次连接里提供的是输出点(true)还是输入点(false)。moverTargetSlot
         * 等于 mover 当前列号时（steps=0）不会触发任何搬运，直接建立连接——这正是
         * "紧邻无需移动"这一特例的自然表现，不需要单独判断是否同行。
         * <p>
         * mover 本身的同行/跨行搭档（如果有）会作为它所在刚性组的一部分被
         * {@link #shiftGroup} 自动一起搬运，不需要再像以前那样单独判断、单独处理
         * "同行搭档"——这是新算法相比旧版本的一个明确简化。
         */
        private void establishCrossRowConnection(ComponentRef mover, int moverTargetSlot, ComponentRef fixed, boolean moverProvidesOutput) {
            PlacedComponent moverComponent = rows.get(mover.rowIndex()).components.get(mover.slotIndex());
            PlacedComponent fixedComponent = rows.get(fixed.rowIndex()).components.get(fixed.slotIndex());
            Set<PlacedComponent> immutable = computeRigidGroup(fixedComponent);
            int steps = moverTargetSlot - mover.slotIndex();
            if (steps > 0 && !shiftGroup(moverComponent, 1, steps, immutable, false)) {
                return; // 理论上候选查找阶段已经验证过可行，这里失败基本不该发生。
            }

            ComponentRef newMoverRef = new ComponentRef(mover.rowIndex(), moverTargetSlot);
            PlacedComponent moverNow = rows.get(mover.rowIndex()).components.get(moverTargetSlot);
            if (moverProvidesOutput) {
                moverNow.outputConnections.add(fixed);
                fixedComponent.inputConnections.add(newMoverRef);
            } else {
                moverNow.inputConnections.add(fixed);
                fixedComponent.outputConnections.add(newMoverRef);
            }
        }

        /** 候选模块挪动到锚点位置去对齐起点（{@link #findCrossRowCandidates()} 找出来的那种候选）。 */
        private void connectCrossRow(ComponentRef candidate) {
            int anchor = fromLeft ? slotIndex - 1 : slotIndex + 1;
            ComponentRef origin = new ComponentRef(rowIndex, slotIndex);
            establishCrossRowConnection(candidate, anchor, origin, fromLeft);
        }

        /** 起点自己挪动到新槽位去对齐固定不动的候选（{@link #findMirrorCandidates()} 找出来的镜像候选）。 */
        private void connectMirror(ComponentRef fixedCandidate, int originNewSlot) {
            ComponentRef origin = new ComponentRef(rowIndex, slotIndex);
            establishCrossRowConnection(origin, originNewSlot, fixedCandidate, !fromLeft);
        }
    }
}