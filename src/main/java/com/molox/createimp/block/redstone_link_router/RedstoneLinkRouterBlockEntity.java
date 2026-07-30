package com.molox.createimp.block.redstone_link_router;

import com.mojang.serialization.Codec;
import com.molox.createimp.block.labeled_redstone_link.LabeledRedstoneLinkNetworkHandler;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.codecs.CatnipCodecUtils;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 路由器界面（地图/行/组件/连接）的服务端持久化载体。这个方块实体本身不参与任何
 * tick 逻辑，纯粹是一份数据存储：客户端界面关闭时（无论是 ESC 还是右下角确认键，
 * 两者最终都会走 {@code onClose()}）把当前编辑状态整体打包发给服务端覆盖保存；
 * 打开界面时客户端直接读取这里的数据初始化。注意本类完全不引用任何客户端专属类型，
 * 双端都能正常加载。
 */
public class RedstoneLinkRouterBlockEntity extends SmartBlockEntity {

    private static final Codec<List<List<RedstoneLinkRouterComponentData>>> ROWS_CODEC =
            RedstoneLinkRouterComponentData.CODEC.listOf().listOf();

    /** 每隔多少 tick 检查一次激活状态。改成每 tick 都查，让信号传播的延迟感更接近原版终端的"近乎即时"，而不是原来 10 tick（约0.5秒）才反应一次。 */
    private static final int POWER_CHECK_INTERVAL = 1;

    private final List<List<RedstoneLinkRouterComponentData>> rows = new ArrayList<>();
    /** 当前判定为"信号非0"的模块坐标集合，只在客户端同步包里携带，不落盘（每次加载后会自然重新算出来）。 */
    private final Set<RedstoneLinkRouterConnectionRef> poweredRefs = new HashSet<>();
    /** 上一轮算出来的每个模块的信号强度（0-15），本轮计算与门/或门的输入时用这份"上一轮"的值——
     *  这个"用上一轮结果做这一轮输入"的模型，是自环（模块接回自己）能够触发一次后永久锁死的
     *  关键，不需要另外写检测逻辑，是这套模型自然产生的效果。不落盘，服务器重启后从全 0 开始。 */
    private Map<RedstoneLinkRouterConnectionRef, Integer> previousSignal = new HashMap<>();
    /** 当前正在向原版无线红石信号终端网络虚拟广播的发送端，按物品频率分类维护。 */
    private final Map<Couple<RedstoneLinkNetworkHandler.Frequency>, RedstoneLinkRouterVirtualLinkable> activeItemLinkables = new HashMap<>();
    /** 当前正在向我们自己的标码无线红石信号终端网络虚拟广播的发送端，按文本频率分类维护。 */
    private final Map<String, RedstoneLinkRouterVirtualLabelLinkable> activeLabelLinkables = new HashMap<>();
    private int tickCounter = 0;

    public RedstoneLinkRouterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) return;
        if (tickCounter++ % POWER_CHECK_INTERVAL != 0) return;
        recomputeSignals();
    }

    /**
     * 关闭方块实体时（方块被破坏、区块卸载等）把所有还在虚拟广播的发送端都从对应的
     * 红石网络里摘除，不能让它们变成"幽灵"一直挂在网络里影响其它玩家的红石装置。
     */
    @Override
    public void remove() {
        clearAllVirtualLinkables();
        super.remove();
    }

    private void clearAllVirtualLinkables() {
        if (level != null) {
            for (RedstoneLinkRouterVirtualLinkable linkable : activeItemLinkables.values()) {
                linkable.markRemoved();
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, linkable);
            }
            LabeledRedstoneLinkNetworkHandler labelHandler = LabeledRedstoneLinkNetworkHandler.get(level);
            if (labelHandler != null) {
                for (RedstoneLinkRouterVirtualLabelLinkable linkable : activeLabelLinkables.values()) {
                    labelHandler.removeFromNetwork(linkable);
                }
            }
        }
        activeItemLinkables.clear();
        activeLabelLinkables.clear();
    }

    /**
     * 整套激活传播的核心：
     * <ol>
     *   <li>物品终端/文本终端的"原始信号"直接查它自己配置的频率当前的真实强度
     *       （这个强度本身就已经包含了世界里其它真实方块、以及路由器自己上一轮
     *       广播出去的虚拟信号——不需要额外去看它的输入端连接列表）；</li>
     *   <li>与门/或门没有自己的频率，"原始信号"完全按输入端直连的模块上一轮
     *       广播出去的信号算：与门要求全部非 0，取其中最大值，只要有一个是 0 就
     *       整体输出 0，没有任何输入连接时固定输出 0；或门只要任意一个非 0 就
     *       取最大值，没有输入同样是 0；</li>
     *   <li>非门标记只影响这个模块"广播给下游的信号"（原始信号是 0 则固定广播
     *       15，原始信号是 1-15 中任意值则固定广播 0），不影响模块自己的"激活"
     *       外观——地图上要不要显示激活贴图，看的是原始信号本身，和有没有打
     *       非门标记无关；</li>
     *   <li>最后，把每个模块广播出去的信号（打了非门标记的已经翻转过），按它
     *       输出端连接的下游模块的类型分情况：连的是物品终端/文本终端，就把这个
     *       信号真实广播到下游终端配置的那个频率上（多个模块广播到同一个频率时
     *       取最大值）；连的是逻辑门，则什么都不用做——逻辑门下一轮自己会通过
     *       输入端连接列表读到这个信号。</li>
     * </ol>
     */
    private void recomputeSignals() {
        Map<RedstoneLinkRouterConnectionRef, Integer> rawSignal = new HashMap<>();
        Map<RedstoneLinkRouterConnectionRef, Integer> broadcastSignal = new HashMap<>();
        for (int r = 0; r < rows.size(); r++) {
            List<RedstoneLinkRouterComponentData> row = rows.get(r);
            for (int s = 0; s < row.size(); s++) {
                RedstoneLinkRouterComponentData data = row.get(s);
                if (RedstoneLinkRouterComponentData.EMPTY_TYPE.equals(data.type())) continue;
                int raw = computeRawSignal(data);
                int broadcast = data.notMarked() ? (raw == 0 ? 15 : 0) : raw;
                RedstoneLinkRouterConnectionRef ref = new RedstoneLinkRouterConnectionRef(r, s);
                rawSignal.put(ref, raw);
                broadcastSignal.put(ref, broadcast);
            }
        }

        Set<RedstoneLinkRouterConnectionRef> newPowered = new HashSet<>();
        Map<Couple<RedstoneLinkNetworkHandler.Frequency>, Integer> desiredItemFrequencies = new HashMap<>();
        Map<String, Integer> desiredLabelFrequencies = new HashMap<>();

        for (int r = 0; r < rows.size(); r++) {
            List<RedstoneLinkRouterComponentData> row = rows.get(r);
            for (int s = 0; s < row.size(); s++) {
                RedstoneLinkRouterComponentData data = row.get(s);
                if (RedstoneLinkRouterComponentData.EMPTY_TYPE.equals(data.type())) continue;
                RedstoneLinkRouterConnectionRef selfRef = new RedstoneLinkRouterConnectionRef(r, s);
                if (rawSignal.get(selfRef) > 0) newPowered.add(selfRef);

                int broadcast = broadcastSignal.get(selfRef);
                if (broadcast <= 0) continue;

                for (RedstoneLinkRouterConnectionRef outRef : data.outputConnections()) {
                    RedstoneLinkRouterComponentData target = getComponent(outRef.rowIndex(), outRef.slotIndex());
                    if ("ITEM_LINK".equals(target.type())) {
                        if (target.itemSlot1().isEmpty() && target.itemSlot2().isEmpty()) continue;
                        Couple<RedstoneLinkNetworkHandler.Frequency> key = Couple.create(
                                RedstoneLinkNetworkHandler.Frequency.of(target.itemSlot1()),
                                RedstoneLinkNetworkHandler.Frequency.of(target.itemSlot2()));
                        desiredItemFrequencies.merge(key, broadcast, Math::max);
                    } else if ("LABEL_LINK".equals(target.type())) {
                        if (target.labelText() == null || target.labelText().isBlank()) continue;
                        desiredLabelFrequencies.merge(target.labelText(), broadcast, Math::max);
                    }
                    // 逻辑门：不需要广播，下一轮它自己会通过输入端连接列表读取这个信号。
                }
            }
        }

        reconcileItemLinkables(desiredItemFrequencies);
        reconcileLabelLinkables(desiredLabelFrequencies);

        // 与门/或门下一轮读取上游信号时，用的是"广播出去的信号"（已经过非门翻转），
        // 因为这才是真正会经过连接线传过去的值；模块自己的激活外观只在上面用
        // rawSignal 判断过一次，不会受这里影响。
        previousSignal = broadcastSignal;

        if (!newPowered.equals(poweredRefs)) {
            poweredRefs.clear();
            poweredRefs.addAll(newPowered);
            notifyUpdate();
        }
    }

    private int computeRawSignal(RedstoneLinkRouterComponentData data) {
        // 这里用字符串字面量而不是引用客户端那边的 ComponentType 枚举，是因为
        // ComponentType 是 RedstoneLinkRouterScreen（纯客户端类）里的私有嵌套枚举，
        // 这个方块实体类是双端都要加载的通用类，不能引用任何客户端专属类型。
        if ("ITEM_LINK".equals(data.type())) {
            return queryItemFrequencyStrength(data.itemSlot1(), data.itemSlot2());
        }
        if ("LABEL_LINK".equals(data.type())) {
            return queryLabelFrequencyStrength(data.labelText());
        }
        if ("AND_GATE".equals(data.type())) {
            List<RedstoneLinkRouterConnectionRef> inputs = data.inputConnections();
            if (inputs.isEmpty()) return 0;
            int max = 0;
            for (RedstoneLinkRouterConnectionRef inRef : inputs) {
                int inSignal = previousSignal.getOrDefault(inRef, 0);
                if (inSignal <= 0) return 0;
                max = Math.max(max, inSignal);
            }
            return max;
        }
        if ("OR_GATE".equals(data.type())) {
            int max = 0;
            for (RedstoneLinkRouterConnectionRef inRef : data.inputConnections()) {
                max = Math.max(max, previousSignal.getOrDefault(inRef, 0));
            }
            return max;
        }
        return 0;
    }

    /** 查询一个物品频率当前的真实强度（0-15），和原版 {@code updateNetworkOf} 一样按传输距离过滤，取网络内最大值。 */
    private int queryItemFrequencyStrength(ItemStack item1, ItemStack item2) {
        if (item1.isEmpty() && item2.isEmpty()) return 0;
        Couple<RedstoneLinkNetworkHandler.Frequency> key = Couple.create(
                RedstoneLinkNetworkHandler.Frequency.of(item1),
                RedstoneLinkNetworkHandler.Frequency.of(item2));
        Set<IRedstoneLinkable> network = Create.REDSTONE_LINK_NETWORK_HANDLER.networksIn(level).get(key);
        if (network == null || network.isEmpty()) return 0;
        RedstoneLinkRouterVirtualLinkable probe = new RedstoneLinkRouterVirtualLinkable(key, getBlockPos(), 0);
        int power = 0;
        for (IRedstoneLinkable other : network) {
            if (!other.isAlive()) continue;
            if (!RedstoneLinkNetworkHandler.withinRange(probe, other)) continue;
            power = Math.max(power, other.getTransmittedStrength());
            if (power >= 15) break;
        }
        return power;
    }

    /** 查询一个文本频率当前的真实强度（0-15）；我们自己的标码终端网络没有传输距离限制。 */
    private int queryLabelFrequencyStrength(String labelText) {
        if (labelText == null || labelText.isBlank()) return 0;
        LabeledRedstoneLinkNetworkHandler handler = LabeledRedstoneLinkNetworkHandler.get(level);
        return handler == null ? 0 : handler.getMaxTransmittedStrength(labelText);
    }

    private void reconcileItemLinkables(Map<Couple<RedstoneLinkNetworkHandler.Frequency>, Integer> desired) {
        activeItemLinkables.entrySet().removeIf(entry -> {
            if (desired.containsKey(entry.getKey())) return false;
            RedstoneLinkRouterVirtualLinkable linkable = entry.getValue();
            linkable.markRemoved();
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, linkable);
            return true;
        });
        for (Map.Entry<Couple<RedstoneLinkNetworkHandler.Frequency>, Integer> entry : desired.entrySet()) {
            RedstoneLinkRouterVirtualLinkable existing = activeItemLinkables.get(entry.getKey());
            if (existing == null) {
                RedstoneLinkRouterVirtualLinkable linkable =
                        new RedstoneLinkRouterVirtualLinkable(entry.getKey(), getBlockPos(), entry.getValue());
                activeItemLinkables.put(entry.getKey(), linkable);
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, linkable);
            } else if (existing.getTransmittedStrength() != entry.getValue()) {
                existing.setStrength(entry.getValue());
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, existing);
            }
        }
    }

    private void reconcileLabelLinkables(Map<String, Integer> desired) {
        LabeledRedstoneLinkNetworkHandler handler = LabeledRedstoneLinkNetworkHandler.get(level);
        if (handler == null) return;
        activeLabelLinkables.entrySet().removeIf(entry -> {
            if (desired.containsKey(entry.getKey())) return false;
            handler.removeFromNetwork(entry.getValue());
            return true;
        });
        for (Map.Entry<String, Integer> entry : desired.entrySet()) {
            RedstoneLinkRouterVirtualLabelLinkable existing = activeLabelLinkables.get(entry.getKey());
            if (existing == null) {
                RedstoneLinkRouterVirtualLabelLinkable linkable =
                        new RedstoneLinkRouterVirtualLabelLinkable(entry.getKey(), entry.getValue());
                activeLabelLinkables.put(entry.getKey(), linkable);
                handler.addToNetwork(linkable);
                handler.updateNetworkOf(linkable);
            } else if (existing.getTransmittedSignal() != entry.getValue()) {
                existing.setStrength(entry.getValue());
                handler.updateNetworkOf(existing);
            }
        }
    }

    public List<List<RedstoneLinkRouterComponentData>> getRows() {
        return rows;
    }

    /** 客户端渲染用：读取最近一次同步下来的、信号非 0 的模块坐标集合。 */
    public Set<RedstoneLinkRouterConnectionRef> getPoweredRefs() {
        return poweredRefs;
    }

    /** 读取某个具体槽位当前的数据；行号/列号越界或者该槽位当前是空位时返回 {@link RedstoneLinkRouterComponentData#EMPTY}。 */
    public RedstoneLinkRouterComponentData getComponent(int rowIndex, int slotIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) return RedstoneLinkRouterComponentData.EMPTY;
        List<RedstoneLinkRouterComponentData> row = rows.get(rowIndex);
        if (slotIndex < 0 || slotIndex >= row.size()) return RedstoneLinkRouterComponentData.EMPTY;
        return row.get(slotIndex);
    }

    /** 用客户端发来的完整数据整体覆盖当前保存的行/组件/连接，随后同步给客户端。每一行都防御性地拷贝成可变列表，保证后续按下标定点修改（见 {@link #setComponentItemSlots}/{@link #setComponentLabelText}）不会因为 Codec 解出来的列表不可变而抛异常。 */
    public void setRows(List<List<RedstoneLinkRouterComponentData>> newRows) {
        rows.clear();
        for (List<RedstoneLinkRouterComponentData> row : newRows) {
            rows.add(new ArrayList<>(row));
        }
        notifyUpdate();
    }

    /**
     * 物品终端配置菜单（{@code RedstoneLinkRouterSetItemMenu}）每次点击槽位后立即调用，
     * 把这个模块的两个物品数据位定点更新掉，而不是整份路由器数据重新提交一次。
     * 行号/列号越界、或者该位置当前不是一个真实模块（已被删除/本来就是空位）时忽略。
     */
    public void setComponentItemSlots(int rowIndex, int slotIndex, ItemStack item1, ItemStack item2) {
        if (rowIndex < 0 || rowIndex >= rows.size()) return;
        List<RedstoneLinkRouterComponentData> row = rows.get(rowIndex);
        if (slotIndex < 0 || slotIndex >= row.size()) return;
        RedstoneLinkRouterComponentData old = row.get(slotIndex);
        if (RedstoneLinkRouterComponentData.EMPTY_TYPE.equals(old.type())) return;
        row.set(slotIndex, new RedstoneLinkRouterComponentData(
                old.type(), old.notMarked(), item1.copy(), item2.copy(), old.labelText(),
                old.inputConnections(), old.outputConnections()));
        notifyUpdate();
    }

    /** 文本终端配置界面保存频率文本时调用，只定点更新这一个模块的文本数据位。 */
    public void setComponentLabelText(int rowIndex, int slotIndex, String text) {
        if (rowIndex < 0 || rowIndex >= rows.size()) return;
        List<RedstoneLinkRouterComponentData> row = rows.get(rowIndex);
        if (slotIndex < 0 || slotIndex >= row.size()) return;
        RedstoneLinkRouterComponentData old = row.get(slotIndex);
        if (RedstoneLinkRouterComponentData.EMPTY_TYPE.equals(old.type())) return;
        row.set(slotIndex, new RedstoneLinkRouterComponentData(
                old.type(), old.notMarked(), old.itemSlot1(), old.itemSlot2(), text,
                old.inputConnections(), old.outputConnections()));
        notifyUpdate();
    }

    /**
     * 供网络包处理器调用：{@code tag} 是客户端按 {@link #ROWS_CODEC} 编码出来的、
     * 套着"Rows"键名的容器（和 {@link #write} 写盘时的格式完全一致），解码后整体
     * 覆盖当前数据并同步给客户端。
     */
    public void loadRowsFromTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("Rows")) {
            setRows(List.of());
            return;
        }
        CatnipCodecUtils.decode(ROWS_CODEC, registries, tag.get("Rows"))
                .ifPresentOrElse(this::setRows, () -> setRows(List.of()));
    }

    /** 按存盘/网络传输统一的格式，把一份行数据编码成套着"Rows"键名的容器；客户端打包 C2S 数据时复用同一份逻辑，保证两边格式完全一致。 */
    public static CompoundTag encodeRowsToTag(HolderLookup.Provider registries, List<List<RedstoneLinkRouterComponentData>> rows) {
        CompoundTag tag = new CompoundTag();
        CatnipCodecUtils.encode(ROWS_CODEC, registries, rows).ifPresent(encoded -> tag.put("Rows", encoded));
        return tag;
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        rows.clear();
        if (tag.contains("Rows")) {
            CatnipCodecUtils.decode(ROWS_CODEC, registries, tag.get("Rows"))
                    .ifPresent(decoded -> {
                        for (List<RedstoneLinkRouterComponentData> row : decoded) {
                            rows.add(new ArrayList<>(row));
                        }
                    });
        }
        if (clientPacket) {
            poweredRefs.clear();
            if (tag.contains("Powered")) {
                CatnipCodecUtils.decode(RedstoneLinkRouterConnectionRef.CODEC.listOf(), registries, tag.get("Powered"))
                        .ifPresent(poweredRefs::addAll);
            }
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        CatnipCodecUtils.encode(ROWS_CODEC, registries, rows).ifPresent(encoded -> tag.put("Rows", encoded));
        if (clientPacket) {
            CatnipCodecUtils.encode(RedstoneLinkRouterConnectionRef.CODEC.listOf(), registries, new ArrayList<>(poweredRefs))
                    .ifPresent(encoded -> tag.put("Powered", encoded));
        }
    }
}