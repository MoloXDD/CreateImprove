package com.molox.createimp.block.batch_repackager;

import com.molox.createimp.CreateImp;
import com.molox.createimp.registry.ModBlocks;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 让批量理包机也能被机械动力的动力臂识别为存放/取出物品的目标，完全对标原版
 * 动力臂对理包机/回收理包机（{@code AllArmInteractionPointTypes.PackagerType}）
 * 的识别方式：原版这一判定只认 {@code AllBlocks.PACKAGER}/{@code AllBlocks.REPACKAGER}
 * 这两个具体方块实例，不认子类也不认标签，所以我们自己的批量理包机需要单独补一条
 * 注册；识别之后直接复用动力臂基类最通用的 {@code ArmInteractionPoint}（不做任何子类化），
 * 和原版理包机走的是完全相同的机制——{@code ArmInteractionPoint} 本身只按方块正上方的
 * {@code IItemHandler} 能力槽位读写，不关心具体是什么方块；而
 * {@code BatchRepackagerBlockEntity.registerCapabilities()} 里已经把持有包裹用的
 * {@code inventory} 注册成了任意方向都能取到的能力，因此不需要像批量动力合成器那样
 * 额外写子类处理特殊的槽位锁定问题。
 */
public class BatchRepackagerArmInteraction {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(BatchRepackagerArmInteraction::registerType);
    }

    private static void registerType(RegisterEvent event) {
        event.register(CreateRegistries.ARM_INTERACTION_POINT_TYPE, helper ->
                helper.register(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "batch_repackager"),
                        new BatchRepackagerType()));
    }

    public static class BatchRepackagerType extends ArmInteractionPointType {
        @Override
        public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
            return ModBlocks.BATCH_REPACKAGER.get() == state.getBlock();
        }

        @Override
        public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
            return new ArmInteractionPoint(this, level, pos, state);
        }
    }
}