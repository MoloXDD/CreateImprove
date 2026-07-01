package com.molox.createimp.block.template_panel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class TemplatePanelSlotPositioning extends ValueBoxTransform {

    public TemplatePanelBlock.PanelSlot slot;

    public TemplatePanelSlotPositioning(TemplatePanelBlock.PanelSlot slot) {
        this.slot = slot;
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        return getCenterOfSlot(state, this.slot);
    }

    public static Vec3 getCenterOfSlot(BlockState state, TemplatePanelBlock.PanelSlot slot) {
        Vec3 vec = new Vec3(0.25 + slot.xOffset * 0.5, 0.09375, 0.25 + slot.yOffset * 0.5);
        vec = VecHelper.rotateCentered(vec, 180.0, Direction.Axis.Y);
        vec = VecHelper.rotateCentered(vec, 57.295776f * TemplatePanelBlock.getXRot(state) + 90.0f, Direction.Axis.X);
        vec = VecHelper.rotateCentered(vec, 57.295776f * TemplatePanelBlock.getYRot(state), Direction.Axis.Y);
        return vec;
    }

    @Override
    public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
        Vec3 offset = this.getLocalOffset(level, pos, state);
        if (offset == null) {
            return false;
        }
        return localHit.distanceTo(offset) < this.scale / 2.0f;
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        ((PoseTransformStack) TransformStack.of(ms)
                .rotate(TemplatePanelBlock.getYRot(state) + (float) Math.PI, Direction.UP))
                .rotate(-TemplatePanelBlock.getXRot(state), Direction.EAST);
    }
}