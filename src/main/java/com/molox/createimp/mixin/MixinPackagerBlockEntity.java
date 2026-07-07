package com.molox.createimp.mixin;

import com.molox.createimp.block.work_warehouse.WorkWarehouseBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PackagerBlockEntity.class, remap = false)
public abstract class MixinPackagerBlockEntity {

    @Shadow
    private native BlockPos getLinkPos();

    @Redirect(method = "recheckIfLinksPresent", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packager/PackagerBlockEntity;getLinkPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos createimp$redirectLinkPos(PackagerBlockEntity instance) {
        BlockPos original = this.getLinkPos();
        if (original != null) {
            return original;
        }
        Level level = instance.getLevel();
        if (level == null) {
            return null;
        }
        Direction facing = instance.getBlockState().getOptionalValue(PackagerBlock.FACING).orElse(Direction.UP);
        BlockPos targetPos = instance.getBlockPos().relative(facing.getOpposite());
        if (level.getBlockEntity(targetPos) instanceof WorkWarehouseBlockEntity) {
            return targetPos;
        }
        return null;
    }
}