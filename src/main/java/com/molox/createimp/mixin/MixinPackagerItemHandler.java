package com.molox.createimp.mixin;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.batch_mechanical_crafter.BatchCrafterUnpackingHandler;
import com.molox.createimp.block.batch_mechanical_crafter.BatchMechanicalCrafterBlockEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PackagerBlockEntity.class, remap = false)
public abstract class MixinPackagerItemHandler {

    @Unique
    private BatchMechanicalCrafterBlockEntity createimp$pendingCrafter;

    @Unique
    private boolean createimp$awaitingAnimationFinish;

    @Inject(method = "unwrapBox", at = @At("HEAD"), cancellable = true)
    private void createimp$unwrapBox(ItemStack box, boolean simulate,
                                     CallbackInfoReturnable<Boolean> ci) {
        if (!PackageItem.isPackage(box)) return;

        PackageOrderWithCrafts orderContext = PackageItem.getOrderContext(box);
        if (orderContext == null || orderContext.orderedCrafts().isEmpty()) return;

        PackagerBlockEntity packager = (PackagerBlockEntity) (Object) this;

        // 持有状态完全交给heldBox管理（和PackagerItemHandler.insertItem头部的检查一致），
        // 不依赖previouslyUnwrapped——该字段只在动画播放期间(animationTicks>0)有意义，
        // 一旦原版tick()里animationTicks归零，每个tick都会把previouslyUnwrapped清空，
        // 不能用它持有"还没处理完、等待下次重试"的长期状态，否则材料会随之被静默丢弃。
        if (packager.animationTicks > 0 || !packager.heldBox.isEmpty()) {
            ci.setReturnValue(false);
            return;
        }

        var level = packager.getLevel();
        if (level == null) return;

        var facing = packager.getBlockState()
                .getOptionalValue(com.simibubi.create.content.logistics.packager.PackagerBlock.FACING)
                .orElse(net.minecraft.core.Direction.UP);
        var targetPos = packager.getBlockPos().relative(facing.getOpposite());
        BlockEntity be = level.getBlockEntity(targetPos);
        if (!(be instanceof BatchMechanicalCrafterBlockEntity crafter)) return;

        if (simulate) {
            ci.setReturnValue(true);
            return;
        }

        // 真正执行阶段：包裹长期持有在heldBox。
        // 不播放animationInward=true(吸入消失)的动画——那套视觉效果意味着包裹被
        // 一次性吃掉、彻底消失，适合原版"立刻处理完"的场景；但我们的批量合成需要
        // 持续多个tick逐批消耗材料，这段时间包裹应该在视觉上保持"停留可见"，
        // 不能让它看起来已经消失。这里只是让heldBox非空，复用原版attemptToSend
        // 输出动画同款的静止显示效果(animationTicks=0时直接显示heldBox)，
        // 不启动任何动画倒计时。
        packager.heldBox = box.copy();
        packager.animationInward = false;
        packager.notifyUpdate();

        // 不需要等待动画播完，立刻尝试触发首次处理(animationTicks本来就是0，
        // 不存在"动画进行中"的等待窗口)。
        BatchCrafterUnpackingHandler.processBatchPackage(packager, crafter);

        ci.setReturnValue(true);
    }
}