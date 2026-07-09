package com.molox.createimp.block.work_warehouse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * 工作仓库接收生产请求时，为它分配到的那一个模板链保存的结构快照，
 * 记录从根节点（玩家请求的模板仪表）到所有有效上游枝叶的每一个仪表的
 * 关键信息，供生产阶段使用。
 * <p>
 * 本次改动：快照的具体构建逻辑已经并入
 * {@link com.molox.createimp.block.template_panel.TemplateMaterialCalculator#calculate}
 * 的递归过程中，与材料计算共用同一份库存缓存、同一次遍历，避免两边各自
 * 独立查询网络库存导致的认领顺序不一致。本类不再提供 capture() 方法。
 * <p>
 * 快照列表的顺序约定：根节点固定是列表的最后一个元素（下标
 * {@code list.size() - 1}），因为构建过程是"先递归构建全部上游，
 * 上游构建完成后再把自己追加进列表"，而不是原来的先序遍历。
 */
public final class WorkWarehouseTemplateSnapshot {

    private WorkWarehouseTemplateSnapshot() {
    }

    /**
     * 快照仪表节点的生产状态，由工作仓库在生产阶段手动切换，不会被任何
     * "重新检查是否满足"的逻辑覆盖：
     * <p>
     * - IDLE：自己的上游还没有全部变为 COMPLETED，本节点暂时什么都不做。
     * - WAITING_MATERIALS：自己的上游已经全部 COMPLETED，本节点正在等待
     *   自己配方需要的原料实际到齐（复用需求列表 + 连接库存监控 + 网络
     *   请求这一套机制）；原料一到齐，立即扣减仓库存储、按本节点的配方
     *   规则寄出，并且在同一时刻切换为 COMPLETED，不会再等待寄出后的
     *   转换结果。
     * - COMPLETED：本节点已经把自己该寄出的原料寄出去了，是一次性的终态，
     *   之后仓库存储发生任何变化都不会让这个节点退回其他状态。
     */
    public enum PanelState {
        IDLE, WAITING_MATERIALS, COMPLETED;

        public static final Codec<PanelState> CODEC = Codec.STRING.xmap(
                PanelState::fromNameSafe, PanelState::name);

        private static PanelState fromNameSafe(String name) {
            try {
                return PanelState.valueOf(name);
            } catch (IllegalArgumentException e) {
                return IDLE;
            }
        }
    }

    /**
     * 单条上游原料需求：物品种类 + 该连接配置的单次配方消耗数量 +
     * 这份原料对应的上游节点在快照列表中的下标（{@code sourceIndex}）。
     * 有了这个下标才能在生产阶段准确判断"这个节点的全部上游是否都已
     * COMPLETED"，而不是像原来那样只能靠物品种类隐式对应。
     */
    public record IngredientEntry(ItemStack item, int amount, int sourceIndex) {
        public static final Codec<IngredientEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.CODEC.fieldOf("item").forGetter(IngredientEntry::item),
                Codec.INT.fieldOf("amount").forGetter(IngredientEntry::amount),
                Codec.INT.fieldOf("source_index").forGetter(IngredientEntry::sourceIndex)
        ).apply(instance, IngredientEntry::new));
    }

    /**
     * 需求列表中的一条记录：这份数量的这种物品，需要从这个物流网络里获取，
     * 才能凑够需求。{@code ownerNode} 标记这份需求归属于快照列表中的哪一个
     * 节点：
     * <p>
     * - {@code >= 0}：归属于生产阶段某个正在 WAITING_MATERIALS 的节点，
     *   它自己配方需要的原料。
     * - {@code -1}：原料请求阶段的一次性初始需求（即"现有材料"），与任何
     *   具体节点的生产阶段动作无关。
     * - {@code -2}：生产阶段专用的"虚拟末端需求"——根节点寄出最后一批原料
     *   进入 COMPLETED 之后，代表"等待根节点自身产出物返回仓库"这一份
     *   等待，满足后代表整次生产真正完成。
     * <p>
     * 同一物品即使被多个不同节点同时需要，也会各自生成独立的记录、不做
     * 合并——需求列表本身按记录顺序、依托仓库存储的真实数量逐条扣减，
     * 天然保证不会出现"两个分支同时以为自己的原料都够了"的重复认领。
     */
    public record DemandEntry(UUID network, ItemStack item, int amount, int ownerNode) {
        public static final int OWNER_INITIAL_GATHER = -1;
        public static final int OWNER_FINAL_PRODUCT = -2;
        /**
         * 归属为"副产物"：生产者这一批实际产出比全部下游消费者需要的总和还
         * 多出来的部分（批次颗粒度取整导致），不属于任何具体消费节点，纯粹
         * 需要被收进仓库内部存储、不触发任何节点的状态推进——否则这部分
         * 差额会因为没有对应的需求列表条目而永远滞留在仓库外部，收不进来。
         */
        public static final int OWNER_BYPRODUCT = -3;

        public static final Codec<DemandEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("network").forGetter(DemandEntry::network),
                ItemStack.CODEC.fieldOf("item").forGetter(DemandEntry::item),
                Codec.INT.fieldOf("amount").forGetter(DemandEntry::amount),
                Codec.INT.fieldOf("owner_node").forGetter(DemandEntry::ownerNode)
        ).apply(instance, DemandEntry::new));
    }

    /**
     * "请求列表"中的一条记录：这份数量的这种物品已经向网络发起了请求、
     * 还在路上，尚未实际到达仓库。字段含义与 {@link DemandEntry} 对称，
     * {@code ownerNode} 取值规则相同。
     */
    public record InTransitEntry(UUID network, ItemStack item, int amount, int ownerNode) {
        public static final Codec<InTransitEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("network").forGetter(InTransitEntry::network),
                ItemStack.CODEC.fieldOf("item").forGetter(InTransitEntry::item),
                Codec.INT.fieldOf("amount").forGetter(InTransitEntry::amount),
                Codec.INT.fieldOf("owner_node").forGetter(InTransitEntry::ownerNode)
        ).apply(instance, InTransitEntry::new));
    }

    /**
     * 单个仪表节点的快照。
     *
     * @param craftingArrangement 动力合成模式下的九宫格配方物品列表（对应
     *                            原 {@code activeCraftingArrangement}
     *                            字段）；非动力合成模式下为空列表。生产阶段
     *                            寄出带合成请求的包裹时需要这份真实配方内容，
     *                            仅存一个布尔值不足以构造
     *                            {@code PackageOrderWithCrafts.CraftingEntry}。
     * @param requiredBatches     本节点这一轮总共需要执行多少次自己的配方，
     *                            在快照生成那一刻按"覆盖下游总需求、按
     *                            recipeOutput 向上取整"算死，生产过程中不再
     *                            重新计算。为 0 表示这个节点的产出在材料
     *                            计算时已经被现有库存直接满足，不需要自己
     *                            再生产（初始状态即为 COMPLETED）。
     * @param state               生产阶段的当前状态，初始值在快照生成时
     *                            一并确定，此后只由工作仓库手动切换。
     * @param expectedOutputTotal 这个节点的产出物最终总共会有多少——包含
     *                            两部分：材料确认阶段就已经从现有网络库存
     *                            确认、并在原料请求阶段运到仓库里的部分，
     *                            加上这个节点自己按 requiredBatches 生产
     *                            出来的部分。这个节点变为 COMPLETED 时，
     *                            应该由它自己用这个数字登记下游消费者的
     *                            需求列表条目，而不是由下游按自己的批次数
     *                            反推——两者在"部分产出已经现成"的情况下
     *                            会算出不同的数字，用下游反推出来的数字会
     *                            漏算已经现成的那部分，导致需求列表迟迟凑
     *                            不满、被迫多发一次不必要的网络请求。
     */
    public record PanelSnapshot(UUID network, ItemStack filterItem, boolean templatePanel, int recipeOutput,
                                List<IngredientEntry> ingredients, boolean demandMode,
                                List<ItemStack> craftingArrangement, String address,
                                int requiredBatches, PanelState state, int expectedOutputTotal) {
        public static final Codec<PanelSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("network").forGetter(PanelSnapshot::network),
                ItemStack.CODEC.fieldOf("filter").forGetter(PanelSnapshot::filterItem),
                Codec.BOOL.fieldOf("template_panel").forGetter(PanelSnapshot::templatePanel),
                Codec.INT.fieldOf("recipe_output").forGetter(PanelSnapshot::recipeOutput),
                IngredientEntry.CODEC.listOf().fieldOf("ingredients").forGetter(PanelSnapshot::ingredients),
                Codec.BOOL.fieldOf("demand_mode").forGetter(PanelSnapshot::demandMode),
                ItemStack.CODEC.listOf().fieldOf("crafting_arrangement").forGetter(PanelSnapshot::craftingArrangement),
                Codec.STRING.fieldOf("address").forGetter(PanelSnapshot::address),
                Codec.INT.fieldOf("required_batches").forGetter(PanelSnapshot::requiredBatches),
                PanelState.CODEC.fieldOf("state").forGetter(PanelSnapshot::state),
                Codec.INT.fieldOf("expected_output_total").forGetter(PanelSnapshot::expectedOutputTotal)
        ).apply(instance, PanelSnapshot::new));

        public boolean craftingMode() {
            return !this.craftingArrangement.isEmpty();
        }

        public PanelSnapshot withState(PanelState newState) {
            return new PanelSnapshot(network, filterItem, templatePanel, recipeOutput, ingredients,
                    demandMode, craftingArrangement, address, requiredBatches, newState, expectedOutputTotal);
        }
    }
}