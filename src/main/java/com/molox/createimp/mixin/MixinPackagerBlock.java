package com.molox.createimp.mixin;

import com.molox.createimp.block.work_warehouse.WorkWarehouseBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PackagerBlock.class, remap = false)
public abstract class MixinPackagerBlock {

    @Unique
    private static final Object createimp$WORK_WAREHOUSE_CAPABILITY_PLACEHOLDER = new Object();

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(method = "getStateForPlacement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getCapability(Lnet/neoforged/neoforge/capabilities/BlockCapability;Lnet/minecraft/core/BlockPos;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object createimp$redirectCapabilityForWorkWarehouse(Level level, BlockCapability capability, BlockPos pos, Object context) {
        Object result = level.getCapability(capability, pos, context);
        if (result != null) {
            return result;
        }
        if (level.getBlockEntity(pos) instanceof WorkWarehouseBlockEntity) {
            return createimp$WORK_WAREHOUSE_CAPABILITY_PLACEHOLDER;
        }
        return null;
    }
}