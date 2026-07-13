package com.molox.createimp.mixin;

import com.molox.createimp.block.work_warehouse.WorkWarehouseBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = PackagerBlock.class, remap = false)
public abstract class MixinPackagerBlock {

    @ModifyVariable(method = "getStateForPlacement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/context/BlockPlaceContext;getPlayer()Lnet/minecraft/world/entity/player/Player;"), ordinal = 0)
    private Direction createimp$checkWorkWarehouseFacing(Direction preferredFacing, BlockPlaceContext context) {
        if (preferredFacing != null) {
            return preferredFacing;
        }
        for (Direction face : context.getNearestLookingDirections()) {
            BlockEntity be = context.getLevel().getBlockEntity(context.getClickedPos().relative(face));
            if (be instanceof WorkWarehouseBlockEntity) {
                return face.getOpposite();
            }
        }
        return null;
    }
}