package com.molox.createimp.block.work_warehouse;

import com.molox.createimp.block.template_panel.TemplatePanelBehaviour;
import com.molox.createimp.block.template_panel.TemplatePanelBlockEntity;
import com.molox.createimp.block.template_panel.TemplatePanelConnection;
import com.molox.createimp.block.template_panel.TemplatePanelPosition;
import com.molox.createimp.util.IFactoryPanelBehaviourDemandMode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 工作仓库接收生产请求时，为它分配到的那一个模板链保存的结构快照，
 * 记录从根节点（玩家请求的模板仪表）到所有有效上游枝叶的每一个仪表的
 * 关键信息，供之后的生产环节使用，避免生产过程中仪表配置被玩家改动
 * 导致的不一致。
 * <p>
 * 仅应在服务端调用（依赖 {@link TemplatePanelBehaviour#getFilter()} 等
 * 服务端权威数据）。
 */
public final class WorkWarehouseTemplateSnapshot {

    private WorkWarehouseTemplateSnapshot() {
    }

    /**
     * 单条上游原料需求：物品种类 + 该连接配置的单次配方消耗数量。
     */
    public record IngredientEntry(ItemStack item, int amount) {
        public static final Codec<IngredientEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.CODEC.fieldOf("item").forGetter(IngredientEntry::item),
                Codec.INT.fieldOf("amount").forGetter(IngredientEntry::amount)
        ).apply(instance, IngredientEntry::new));
    }

    /**
     * 单个仪表节点的快照。
     */
    public record PanelSnapshot(UUID network, ItemStack filterItem, boolean templatePanel, int recipeOutput,
                                List<IngredientEntry> ingredients, boolean demandMode, boolean craftingMode,
                                String address) {
        public static final Codec<PanelSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("network").forGetter(PanelSnapshot::network),
                ItemStack.CODEC.fieldOf("filter").forGetter(PanelSnapshot::filterItem),
                Codec.BOOL.fieldOf("template_panel").forGetter(PanelSnapshot::templatePanel),
                Codec.INT.fieldOf("recipe_output").forGetter(PanelSnapshot::recipeOutput),
                IngredientEntry.CODEC.listOf().fieldOf("ingredients").forGetter(PanelSnapshot::ingredients),
                Codec.BOOL.fieldOf("demand_mode").forGetter(PanelSnapshot::demandMode),
                Codec.BOOL.fieldOf("crafting_mode").forGetter(PanelSnapshot::craftingMode),
                Codec.STRING.fieldOf("address").forGetter(PanelSnapshot::address)
        ).apply(instance, PanelSnapshot::new));
    }

    /**
     * 从请求的根节点开始，沿着有效模板链向上游遍历，展开成一份扁平列表。
     * 只有在根节点自身有效（{@code validTemplateChain}）的前提下调用才有意义，
     * 但这里依然做了防御性判断，遇到无法解析的节点就在那条分支停止，
     * 不会因为一次异常状态导致服务端报错。
     */
    public static List<PanelSnapshot> capture(Level level, TemplatePanelPosition rootPos) {
        List<PanelSnapshot> result = new ArrayList<>();
        TemplatePanelBehaviour root = TemplatePanelBehaviour.at(level, rootPos);
        if (root == null) {
            return result;
        }
        result.add(fromTemplatePanel(root));
        for (TemplatePanelConnection connection : root.targetedBy.values()) {
            captureUpstream(level, connection.from, result);
        }
        return result;
    }

    private static void captureUpstream(Level level, TemplatePanelPosition pos, List<PanelSnapshot> result) {
        if (!level.isLoaded(pos.pos())) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos.pos());
        if (be instanceof TemplatePanelBlockEntity tpbe) {
            TemplatePanelBehaviour node = tpbe.panels.get(pos.slot());
            if (node == null || !node.isActive() || node.getFilter().isEmpty()) {
                return;
            }
            result.add(fromTemplatePanel(node));
            for (TemplatePanelConnection connection : node.targetedBy.values()) {
                captureUpstream(level, connection.from, result);
            }
        } else if (be instanceof FactoryPanelBlockEntity fpbe) {
            FactoryPanelBlock.PanelSlot vanillaSlot = FactoryPanelBlock.PanelSlot.valueOf(pos.slot().name());
            FactoryPanelBehaviour node = fpbe.panels.get(vanillaSlot);
            if (node == null || !node.isActive() || node.getFilter().isEmpty()) {
                return;
            }
            result.add(fromFactoryPanel(node));
            // 普通仪表是叶子节点，不再向上遍历。
        }
    }

    private static PanelSnapshot fromTemplatePanel(TemplatePanelBehaviour node) {
        List<IngredientEntry> ingredients = new ArrayList<>();
        for (TemplatePanelConnection connection : node.targetedBy.values()) {
            ItemStack ingredientItem = TemplatePanelBehaviour.getExternalFilter(
                    node.getWorld(), connection.from.pos(), connection.from.slot());
            ingredients.add(new IngredientEntry(ingredientItem.copy(), connection.amount));
        }
        return new PanelSnapshot(node.network, node.getFilter().copy(), true, node.recipeOutput,
                ingredients, node.demandMode, !node.activeCraftingArrangement.isEmpty(), node.recipeAddress);
    }

    private static PanelSnapshot fromFactoryPanel(FactoryPanelBehaviour node) {
        // 普通仪表在本系统中始终是叶子节点，它自身在原版体系里的上游配料
        // 不属于模板链的一部分，这里不记录，只记录后续生产环节需要用到的
        // 关键状态。
        boolean demandMode = ((IFactoryPanelBehaviourDemandMode) node).createimp$isDemandMode();
        return new PanelSnapshot(node.network, node.getFilter().copy(), false, node.recipeOutput,
                List.of(), demandMode, !node.activeCraftingArrangement.isEmpty(), node.recipeAddress);
    }
}