package com.molox.createimp.block.batch_mechanical_crafter;

import com.molox.createimp.CreateImp;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 让批量动力合成器也能被机械动力的动力臂识别为存放/取出物品的目标，完全
 * 对标原版动力臂对普通机械合成器（{@code AllArmInteractionPointTypes.CrafterType}/
 * {@code CrafterPoint}）的实现方式。
 * <p>
 * 存入端：调用 {@link BatchConnectedInputHandler#insertEvenly} 把手中物品
 * 尽可能均分插入连接链里当前为空的槽位，除不尽的余数从左上角的合成器开始
 * 往后依次多分1个——和手持物品右键槽位的分配规则完全一致。这是因为原版
 * 动力臂对接原版动力合成器时"看起来均分给每一台"，其实只是原版单槽容量
 * 恰好为1、配合顺序填充的副作用，并不存在真正的均分算法；我们的槽位容量
 * 是一整组，所以需要显式实现均分，而不能像原版那样依赖顺序填充自然凑出
 * 均分效果。
 * <p>
 * 取出端：和原版一样，只针对"还没开始合成、原料原样躺在输入槽里"这一种
 * 情况——一旦触发合成，{@code begin()} 会立刻把输入槽清空（原料转存进内部
 * 的产物暂存结构），从此不管是合成中还是合成完毕后因为传送带堵住而滞留，
 * 输入槽本身都是空的，动力臂自然拿不到；和原版一样也没有能力越过这一点去
 * 抓取半路的产物。这里只是照抄原版 {@code CrafterPoint.extract} 那种"临时
 * 解锁槽位限制、走通用槽位逻辑"的写法。
 */
public class BatchCrafterArmInteraction {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(BatchCrafterArmInteraction::registerType);
    }

    private static void registerType(RegisterEvent event) {
        event.register(CreateRegistries.ARM_INTERACTION_POINT_TYPE, helper ->
                helper.register(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "batch_mechanical_crafter"),
                        new BatchCrafterType()));
    }

    public static class BatchCrafterType extends ArmInteractionPointType {
        @Override
        public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
            return BatchCrafterHelper.isBatchCrafter(state);
        }

        @Override
        public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
            return new BatchCrafterPoint(this, level, pos, state);
        }
    }

    public static class BatchCrafterPoint extends ArmInteractionPoint {

        public BatchCrafterPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
            super(type, level, pos, state);
        }

        @Override
        protected Direction getInteractionDirection() {
            return this.cachedState.getOptionalValue(BatchMechanicalCrafterBlock.HORIZONTAL_FACING)
                    .orElse(Direction.SOUTH).getOpposite();
        }

        @Override
        protected Vec3 getInteractionPositionVector() {
            return super.getInteractionPositionVector()
                    .add(Vec3.atLowerCornerOf((Vec3i) this.getInteractionDirection().getNormal()).scale(0.5));
        }

        @Override
        public void updateCachedState() {
            BlockState oldState = this.cachedState;
            super.updateCachedState();
            if (oldState != this.cachedState) {
                this.cachedAngles = null;
            }
        }

        @Override
        public ItemStack insert(ArmBlockEntity armBlockEntity, ItemStack stack, boolean simulate) {
            return BatchConnectedInputHandler.insertEvenly(this.level, this.pos, stack, simulate);
        }

        @Override
        public ItemStack extract(ArmBlockEntity armBlockEntity, int slot, int amount, boolean simulate) {
            BatchMechanicalCrafterBlockEntity crafter = BatchCrafterHelper.getCrafter(this.level, this.pos);
            if (crafter == null) {
                return ItemStack.EMPTY;
            }
            BatchMechanicalCrafterBlockEntity.Inventory inventory = crafter.getInventory();
            inventory.allowExtraction();
            ItemStack extract = super.extract(armBlockEntity, slot, amount, simulate);
            inventory.forbidExtraction();
            return extract;
        }
    }
}