package com.molox.createimp.block.work_warehouse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
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
        /**
         * 和 {@link LogArg.ItemCount} 同样的原因：{@code item} 只是类型标记，
         * 真实数量由 {@code amount} 单独承载，强制归一化避免调用方不小心把
         * 真实数量（可能超过 99）带进 {@code item} 自身导致编码失败。
         */
        public IngredientEntry {
            item = item.copyWithCount(1);
        }

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
    public record DemandEntry(UUID network, ItemStack item, int amount, int ownerNode, int sourceProducerIndex) {
        /** 同 {@link IngredientEntry}：item 只是类型标记，强制归一化数量为 1。 */
        public DemandEntry {
            item = item.copyWithCount(1);
        }

        public static final int OWNER_INITIAL_GATHER = -1;
        public static final int OWNER_FINAL_PRODUCT = -2;
        /**
         * 归属为"副产物"：生产者这一批实际产出比全部下游消费者需要的总和还
         * 多出来的部分（批次颗粒度取整导致），不属于任何具体消费节点，纯粹
         * 需要被收进仓库内部存储、不触发任何节点的状态推进——否则这部分
         * 差额会因为没有对应的需求列表条目而永远滞留在仓库外部，收不进来。
         */
        public static final int OWNER_BYPRODUCT = -3;

        /**
         * {@code sourceProducerIndex} 取值规则：
         * <p>
         * - {@code >= 0}：这份需求是快照列表里下标为该值的节点，在自己变为
         *   COMPLETED 时（{@link com.molox.createimp.block.work_warehouse.WorkWarehouseBlockEntity#registerOutputDemand}
         *   / {@code registerFinalDemand}）登记出来的产出需求，用于判断
         *   "这个生产者自己的产出物是否已经全部到达仓库"。
         * - {@code NO_PRODUCER}（-1）：这份需求不对应快照列表里的任何一个
         *   生产节点，即原料请求阶段的一次性初始需求（{@link #OWNER_INITIAL_GATHER}）。
         */
        public static final int NO_PRODUCER = -1;

        public static final Codec<DemandEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("network").forGetter(DemandEntry::network),
                ItemStack.CODEC.fieldOf("item").forGetter(DemandEntry::item),
                Codec.INT.fieldOf("amount").forGetter(DemandEntry::amount),
                Codec.INT.fieldOf("owner_node").forGetter(DemandEntry::ownerNode),
                Codec.INT.optionalFieldOf("source_producer_index", NO_PRODUCER).forGetter(DemandEntry::sourceProducerIndex)
        ).apply(instance, DemandEntry::new));
    }

    /**
     * "请求列表"中的一条记录：这份数量的这种物品已经向网络发起了请求、
     * 还在路上，尚未实际到达仓库。字段含义与 {@link DemandEntry} 对称，
     * {@code ownerNode} 取值规则相同。
     */
    public record InTransitEntry(UUID network, ItemStack item, int amount, int ownerNode) {
        /** 同 {@link IngredientEntry}：item 只是类型标记，强制归一化数量为 1。 */
        public InTransitEntry {
            item = item.copyWithCount(1);
        }

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
        /**
         * 同 {@link IngredientEntry}：{@code filterItem} 只是类型标记，真实
         * 数量由 {@code expectedOutputTotal}/{@code requiredBatches} 单独
         * 承载，强制归一化避免数量意外超过 99 导致这个节点自身编码失败，
         * 拖累整份快照列表都存不进存档。
         */
        public PanelSnapshot {
            filterItem = filterItem.copyWithCount(1);
        }

        public static final Codec<PanelSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("network").forGetter(PanelSnapshot::network),
                ItemStack.CODEC.fieldOf("filter").forGetter(PanelSnapshot::filterItem),
                Codec.BOOL.fieldOf("template_panel").forGetter(PanelSnapshot::templatePanel),
                Codec.INT.fieldOf("recipe_output").forGetter(PanelSnapshot::recipeOutput),
                IngredientEntry.CODEC.listOf().fieldOf("ingredients").forGetter(PanelSnapshot::ingredients),
                Codec.BOOL.fieldOf("demand_mode").forGetter(PanelSnapshot::demandMode),
                // 【问题修复】此前用 ItemStack.CODEC.listOf()，而原版 ItemStack.CODEC
                // 的 id 字段校验明确拒绝编码 minecraft:air（用于表示"这一格是空的"
                // 占位物），只要九宫格里有一个空格，整份 PanelSnapshot 列表就会
                // 编码失败、CatnipCodecUtils.encode 静默返回空，导致 TemplateSnapshot
                // 整体不写入存档、下次读取变成空列表——这正是生产会永久卡死的根因。
                // 改用 ItemStack.OPTIONAL_CODEC.listOf()：该 Codec 会把空气编码成
                // "什么都不写"、解码还原成 ItemStack.EMPTY，和原版容器槽位处理
                // "可能为空的格子"的标准写法完全一致。
                ItemStack.OPTIONAL_CODEC.listOf().fieldOf("crafting_arrangement").forGetter(PanelSnapshot::craftingArrangement),
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

    /**
     * 日志条目的展示分类：{@code NORMAL} 是正常颜色，{@code CANCEL} 是"请求
     * 中断"相关的几条日志专用——整条消息里除了 {@code _高亮_} 部分依然保持
     * 高亮色之外，其余文字在三处展示（进程卡片最新日志行、历史请求日志
     * 卡片预览行、详情界面完整换行展示）里都要统一变成红色。这个分类是在
     * 日志产生的那一刻就打好标签的，不是靠解析文本内容去猜。
     */
    public enum LogCategory {
        NORMAL, CANCEL;

        public static final Codec<LogCategory> CODEC = Codec.STRING.xmap(
                LogCategory::fromNameSafe, LogCategory::name);

        private static LogCategory fromNameSafe(String name) {
            try {
                return LogCategory.valueOf(name);
            } catch (IllegalArgumentException e) {
                return NORMAL;
            }
        }
    }

    /**
     * 日志消息里的一个 {@code %s} 参数，三选一：
     * <p>
     * - {@code text}：不需要翻译的原样文本（比如玩家自己设置的地址）。
     * - {@code items}：一组物品+数量，解析时会拼成"物品名×数量 物品名×数量"
     *   这样的字符串，物品名字用 {@link ItemStack#getHoverName()}——这是个
     *   可翻译的 {@code Component}，在谁的客户端上调用 {@code getString()}
     *   就会按谁的语言解析，天然支持多语言。
     * - {@code translationKey}：另一个不带参数的翻译键（比如"连接储存"这种
     *   UI 文字本身也需要翻译，而不是原样文本），解析时递归翻译。
     * <p>
     * 三个字段同一时间只会有一个非空，靠 {@link #resolve()} 按优先级
     * （translationKey > items > text）解析成最终代入 {@code %s} 的文本。
     */
    public record LogArg(String text, List<ItemCount> items, String translationKey) {
        public static final Codec<LogArg> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("text", "").forGetter(LogArg::text),
                ItemCount.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(LogArg::items),
                Codec.STRING.optionalFieldOf("translation_key", "").forGetter(LogArg::translationKey)
        ).apply(instance, LogArg::new));

        public static LogArg text(String value) {
            return new LogArg(value, List.of(), "");
        }

        public static LogArg items(List<ItemCount> items) {
            return new LogArg("", items, "");
        }

        public static LogArg key(String translationKey) {
            return new LogArg("", List.of(), translationKey);
        }

        /** 按调用方（渲染时是客户端）自己的语言，把这一个参数解析成最终文本。 */
        public String resolve() {
            if (!translationKey.isEmpty()) {
                return Component.translatable(translationKey).getString();
            }
            if (!items.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (ItemCount ic : items) {
                    if (!sb.isEmpty()) {
                        sb.append(" ");
                    }
                    sb.append(ic.item().getHoverName().getString()).append("×").append(formatCount(ic));
                }
                return sb.toString();
            }
            return text;
        }

        /**
         * 数量部分的文本：流体展示物（流包已安装且这个物品是它的虚拟流体
         * 过滤物）改用流体自己的 mB/B/KB 格式，跟仓管、材料检查界面用的是
         * 同一套格式化方法；其余普通物品保持原样显示整数个数。
         */
        private static String formatCount(ItemCount ic) {
            if (com.molox.createimp.compat.fluidlogistics.FluidLogisticsCompat.isLoaded()
                    && com.molox.createimp.compat.fluidlogistics.TemplateFluidDisplayHelper.isVirtualFluidDisplay(ic.item())) {
                return com.molox.createimp.compat.fluidlogistics.TemplateFluidDisplayHelper.formatStorageAmount(ic.count());
            }
            return String.valueOf(ic.count());
        }

        /** 一种物品 + 数量，用于拼成日志里"物品名×数量"这一段。 */
        public record ItemCount(ItemStack item, int count) {
            /**
             * 强制把 {@code item} 内嵌的数量归一化成 1——真实数量只由 {@code count}
             * 这个独立字段承载。原版 {@code ItemStack.CODEC} 对内部 count 字段做了
             * {@code intRange(1, 99)} 校验，如果不归一化，调用方一旦把"物品×实际
             * 数量"直接拼进 {@code item} 里（比如数量超过 99 的批量物流请求），
             * 这一条记录就会编码失败，进而拖累整个日志列表都编码失败、静默丢失。
             */
            public ItemCount {
                item = item.copyWithCount(1);
            }

            public static final Codec<ItemCount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ItemStack.CODEC.fieldOf("item").forGetter(ItemCount::item),
                    Codec.INT.fieldOf("count").forGetter(ItemCount::count)
            ).apply(instance, ItemCount::new));
        }
    }

    /**
     * 工作仓库自己的事件日志条目：{@code elapsedTicks} 是记录这条日志那一刻，
     * 距离本次工作被激活（{@code activate()} 记录的世界时间）经过的 tick 数，
     * 与进程面板界面展示"经过时间"用的是同一个时间基准，只是这里存的是原始
     * tick 数，格式化成"XX分XX秒"的展示逻辑留给界面层处理。
     * <p>
     * 消息本身不再存成拼好的字符串，而是存"翻译键 + 参数列表"——
     * {@link #resolveMessage()} 会用 {@code Component.translatable(key, 参数...)}
     * 现场解析，在谁的客户端上调用就按谁当前选择的语言解析，随时切换语言
     * 都能立刻看到对应语言的日志，不需要重新生成。翻译键对应的语言文件
     * 模板字符串里保留 {@code _高亮内容_} 这种单下划线标记，解析完之后仍然
     * 是界面层负责按这个标记解析成两种颜色。
     */
    public record LogEntry(long elapsedTicks, String key, List<LogArg> args, LogCategory category) {
        public static final Codec<LogEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("elapsed_ticks").forGetter(LogEntry::elapsedTicks),
                Codec.STRING.fieldOf("key").forGetter(LogEntry::key),
                LogArg.CODEC.listOf().optionalFieldOf("args", List.of()).forGetter(LogEntry::args),
                LogCategory.CODEC.optionalFieldOf("category", LogCategory.NORMAL).forGetter(LogEntry::category)
        ).apply(instance, LogEntry::new));

        /** 按调用方（渲染时是客户端）自己的语言，把 key+参数解析成最终要显示的文字。 */
        public String resolveMessage() {
            Object[] resolvedArgs = new Object[args.size()];
            for (int i = 0; i < args.size(); i++) {
                resolvedArgs[i] = args.get(i).resolve();
            }
            return Component.translatable(key, resolvedArgs).getString();
        }
    }
}