package com.molox.createimp.mixin;

import com.molox.createimp.block.batch_mechanical_crafter.BatchCrafterUnpackingHandler;
import com.molox.createimp.block.batch_mechanical_crafter.BatchMechanicalCrafterBlockEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PackagerBlockEntity.class, remap = false)
public abstract class MixinPackagerItemHandler {

    @Inject(method = "unwrapBox", at = @At("HEAD"), cancellable = true)
    private void createimp$unwrapBox(ItemStack box, boolean simulate,
                                     CallbackInfoReturnable<Boolean> ci) {
        if (!PackageItem.isPackage(box)) return;

        PackageOrderWithCrafts orderContext = PackageItem.getOrderContext(box);
        if (orderContext == null || orderContext.orderedCrafts().isEmpty()) return;

        PackagerBlockEntity packager = (PackagerBlockEntity) (Object) this;
        var level = packager.getLevel();
        if (level == null) return;

        var facing = packager.getBlockState()
                .getOptionalValue(com.simibubi.create.content.logistics.packager.PackagerBlock.FACING)
                .orElse(net.minecraft.core.Direction.UP);
        var targetPos = packager.getBlockPos().relative(facing.getOpposite());
        BlockEntity be = level.getBlockEntity(targetPos);
        if (!(be instanceof BatchMechanicalCrafterBlockEntity crafter)) return;

        if (simulate) {
            // insertItem的测试阶段，返回true表示可以接收
            ci.setReturnValue(true);
            return;
        }

        // insertItem的真正执行阶段：把包裹存入heldBox，设置显示状态，处理第一批次
        packager.heldBox = box.copy();
        packager.animationInward = false;
        packager.animationTicks = 0;
        packager.notifyUpdate();
        BatchCrafterUnpackingHandler.processBatchPackage(packager, crafter);
        ci.setReturnValue(true);
    }
}