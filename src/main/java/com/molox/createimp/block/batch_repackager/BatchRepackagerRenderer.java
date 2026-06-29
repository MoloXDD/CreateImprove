package com.molox.createimp.block.batch_repackager;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

public class BatchRepackagerRenderer extends SmartBlockEntityRenderer<BatchRepackagerBlockEntity> {

    public BatchRepackagerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(BatchRepackagerBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        ItemStack renderedBox = be.getRenderedBox();
        float trayOffset = be.getTrayOffset(partialTicks);
        BlockState blockState = be.getBlockState();
        Direction facing = ((Direction) blockState.getValue((Property) PackagerBlock.FACING)).getOpposite();
        if (!VisualizationManager.supportsVisualization((LevelAccessor) be.getLevel())) {
            PartialModel hatchModel = PackagerRenderer.getHatchModel(be);
            SuperByteBuffer sbb = CachedBuffers.partial(hatchModel, blockState);
            ((SuperByteBuffer) ((SuperByteBuffer) ((SuperByteBuffer) sbb
                    .translate(Vec3.atLowerCornerOf((Vec3i) facing.getNormal()).scale(0.49999f)))
                    .rotateYCenteredDegrees(AngleHelper.horizontalAngle(facing)))
                    .rotateXCenteredDegrees(AngleHelper.verticalAngle(facing)))
                    .light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
            sbb = CachedBuffers.partial(AllPartialModels.PACKAGER_TRAY_DEFRAG, blockState);
            ((SuperByteBuffer) ((SuperByteBuffer) sbb
                    .translate(Vec3.atLowerCornerOf((Vec3i) facing.getNormal()).scale(trayOffset)))
                    .rotateYCenteredDegrees(facing.toYRot()))
                    .light(light).renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
        }
        if (!renderedBox.isEmpty()) {
            ms.pushPose();
            PoseTransformStack msr = TransformStack.of(ms);
            ((PoseTransformStack) ((PoseTransformStack) msr
                    .translate(Vec3.atLowerCornerOf((Vec3i) facing.getNormal()).scale(trayOffset)))
                    .translate(0.5f, 0.5f, 0.5f).rotateYDegrees(facing.toYRot()))
                    .translate(0.0f, 0.125f, 0.0f).scale(1.49f, 1.49f, 1.49f);
            Minecraft.getInstance().getItemRenderer().renderStatic(null, renderedBox,
                    ItemDisplayContext.FIXED, false, ms, buffer, be.getLevel(), light, overlay, 0);
            ms.popPose();
        }
    }
}