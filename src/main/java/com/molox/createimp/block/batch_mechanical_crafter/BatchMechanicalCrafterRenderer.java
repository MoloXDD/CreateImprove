package com.molox.createimp.block.batch_mechanical_crafter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.network.chat.FormattedText;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;

public class BatchMechanicalCrafterRenderer extends SafeBlockEntityRenderer<BatchMechanicalCrafterBlockEntity> {

    public BatchMechanicalCrafterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(BatchMechanicalCrafterBlockEntity be, float partialTicks,
                              PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        ms.pushPose();
        Direction facing = be.getBlockState().getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
        Vec3 vec = Vec3.atLowerCornerOf((Vec3i) facing.getNormal()).scale(0.58).add(0.5, 0.5, 0.5);
        if (be.phase == BatchMechanicalCrafterBlockEntity.Phase.EXPORTING) {
            Direction targetDirection = BatchMechanicalCrafterBlock.getTargetDirection(be.getBlockState());
            float progress = Mth.clamp(
                    ((float) (1000 - be.countDown) + (float) be.getCountDownSpeed() * partialTicks) / 1000f,
                    0f, 1f);
            vec = vec.add(Vec3.atLowerCornerOf((Vec3i) targetDirection.getNormal()).scale(progress * 0.75f));
        }
        ms.translate(vec.x, vec.y, vec.z);
        ms.scale(0.5f, 0.5f, 0.5f);
        float yRot = AngleHelper.horizontalAngle(facing);
        ms.mulPose(Axis.YP.rotationDegrees(yRot));
        this.renderItems(be, partialTicks, ms, buffer, light, overlay);
        ms.popPose();
        this.renderFast(be, partialTicks, ms, buffer, light);
    }

    public void renderItems(BatchMechanicalCrafterBlockEntity be, float partialTicks,
                            PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.phase == BatchMechanicalCrafterBlockEntity.Phase.IDLE) {
            ItemStack stack = be.getInventory().getItem(0);
            if (!stack.isEmpty()) {
                ms.pushPose();
                ms.translate(0f, 0f, -0.00390625f);
                ms.mulPose(Axis.YP.rotationDegrees(180f));
                Minecraft.getInstance().getItemRenderer().renderStatic(
                        stack, ItemDisplayContext.FIXED, light, overlay, ms, buffer, be.getLevel(), 0);
                if (stack.getCount() > 0 && com.molox.createimp.CreateImp.getConfig().batchMechanicalCrafterConfig.showItemCount)
                    renderCountLabel(ms, buffer, stack.getCount());
                ms.popPose();
            }
        } else {
            BatchRecipeGridHandler.GroupedItems items = be.groupedItems;
            float distance = 0.5f;
            ms.pushPose();
            if (be.phase == BatchMechanicalCrafterBlockEntity.Phase.CRAFTING) {
                items = be.groupedItemsBeforeCraft;
                items.calcStats();
                float progress = Mth.clamp(
                        ((float) (2000 - be.countDown) + (float) be.getCountDownSpeed() * partialTicks) / 1000f,
                        0f, 1f);
                float earlyProgress = Mth.clamp(progress * 2f, 0f, 1f);
                float lateProgress = Mth.clamp(progress * 2f - 1f, 0f, 1f);
                ms.scale(1f - lateProgress, 1f - lateProgress, 1f - lateProgress);
                Vec3 centering = new Vec3(
                        (float) (-items.minX) + (float) (-items.width + 1) / 2f,
                        (float) (-items.minY) + (float) (-items.height + 1) / 2f,
                        0.0).scale(earlyProgress);
                ms.translate(centering.x * 0.5, centering.y * 0.5, 0.0);
                distance += (-4f * (progress - 0.5f) * (progress - 0.5f) + 1f) * 0.25f;
            }
            boolean onlyRenderFirst = be.phase == BatchMechanicalCrafterBlockEntity.Phase.INSERTING
                    || be.phase == BatchMechanicalCrafterBlockEntity.Phase.CRAFTING && be.countDown < 1000;
            float spacing = distance;
            items.grid.forEach((pair, stack) -> {
                if (onlyRenderFirst && ((Integer) pair.getLeft() != 0 || (Integer) pair.getRight() != 0))
                    return;
                ms.pushPose();
                int x = pair.getKey();
                int y = pair.getValue();
                ms.translate((float) x * spacing, (float) y * spacing, 0f);
                int offset = 0;
                if (be.phase == BatchMechanicalCrafterBlockEntity.Phase.EXPORTING
                        && be.getBlockState().hasProperty(BatchMechanicalCrafterBlock.POINTING)) {
                    Pointing value = be.getBlockState().getValue(BatchMechanicalCrafterBlock.POINTING);
                    offset = value == Pointing.UP ? -1
                            : (value == Pointing.LEFT ? 2 : (value == Pointing.RIGHT ? -2 : 1));
                }
                ((PoseTransformStack) TransformStack.of(ms).rotateYDegrees(180f))
                        .translate(0f, 0f, (float) (x + y * 3 + offset * 9) / 1024f);
                Minecraft.getInstance().getItemRenderer().renderStatic(
                        stack, ItemDisplayContext.FIXED, light, overlay, ms, buffer, be.getLevel(), 0);
                if (!stack.isEmpty() && stack.getCount() > 0 && com.molox.createimp.CreateImp.getConfig().batchMechanicalCrafterConfig.showItemCount)
                    renderCountLabel(ms, buffer, stack.getCount());
                ms.popPose();
            });
            ms.popPose();
            if (be.phase == BatchMechanicalCrafterBlockEntity.Phase.CRAFTING) {
                items = be.groupedItems;
                float progress = Mth.clamp(
                        ((float) (1000 - be.countDown) + (float) be.getCountDownSpeed() * partialTicks) / 1000f,
                        0f, 1f);
                float earlyProgress = Mth.clamp(progress * 2f, 0f, 1f);
                float lateProgress = Mth.clamp(progress * 2f - 1f, 0f, 1f);
                ms.mulPose(Axis.ZP.rotationDegrees(earlyProgress * 2f * 360f));
                float upScaling = earlyProgress * 1.125f;
                float downScaling = 1f + (1f - lateProgress) * 0.125f;
                ms.scale(upScaling, upScaling, upScaling);
                ms.scale(downScaling, downScaling, downScaling);
                items.grid.forEach((pair, stack) -> {
                    if ((Integer) pair.getLeft() != 0 || (Integer) pair.getRight() != 0) return;
                    ms.pushPose();
                    ms.mulPose(Axis.YP.rotationDegrees(180f));
                    Minecraft.getInstance().getItemRenderer().renderStatic(
                            stack, ItemDisplayContext.FIXED, light, overlay, ms, buffer, be.getLevel(), 0);
                    ms.popPose();
                });
            }
        }
    }

    private void renderCountLabel(PoseStack ms, MultiBufferSource buffer, int count) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Component text = Component.literal(String.valueOf(count));

        ms.pushPose();
        ms.translate(0.0f, -0.4f, -0.1f);
        ms.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
        float fontScale = 0.05f;
        ms.scale(fontScale, -fontScale, fontScale);
        String str = String.valueOf(count);
        float x = -font.width(str) / 2f;
        font.drawInBatch(text, x, 0f, 0xFFFFFF, true, ms.last().pose(), buffer,
                Font.DisplayMode.NORMAL, 0, 0xF000F0);
        ms.popPose();
    }

    public void renderFast(BatchMechanicalCrafterBlockEntity be, float partialTicks,
                           PoseStack ms, MultiBufferSource buffer, int light) {
        BlockState blockState = be.getBlockState();
        VertexConsumer vb = buffer.getBuffer(RenderType.solid());
        if (!VisualizationManager.supportsVisualization((LevelAccessor) be.getLevel())) {
            SuperByteBuffer superBuffer = CachedBuffers.partial(
                    AllPartialModels.SHAFTLESS_COGWHEEL, blockState);
            KineticBlockEntityRenderer.standardKineticRotationTransform(
                    superBuffer, (KineticBlockEntity) be, light);
            superBuffer.rotateCentered(
                    (float) (blockState.getValue(HorizontalKineticBlock.HORIZONTAL_FACING).getAxis()
                            != Direction.Axis.X ? 0.0 : Math.PI / 2), Direction.UP);
            superBuffer.rotateCentered((float) (Math.PI / 2), Direction.EAST);
            superBuffer.renderInto(ms, vb);
        }
        Direction targetDirection = BatchMechanicalCrafterBlock.getTargetDirection(blockState);
        BlockPos pos = be.getBlockPos();
        if ((be.covered || be.phase != BatchMechanicalCrafterBlockEntity.Phase.IDLE)
                && be.phase != BatchMechanicalCrafterBlockEntity.Phase.CRAFTING
                && be.phase != BatchMechanicalCrafterBlockEntity.Phase.INSERTING) {
            SuperByteBuffer lidBuffer = this.renderAndTransform(AllPartialModels.MECHANICAL_CRAFTER_LID, blockState);
            lidBuffer.light(light).renderInto(ms, vb);
        }
        if (BatchMechanicalCrafterBlock.isValidTarget((Level) be.getLevel(),
                pos.relative(targetDirection), blockState)) {
            SuperByteBuffer beltBuffer = this.renderAndTransform(
                    AllPartialModels.MECHANICAL_CRAFTER_BELT, blockState);
            SuperByteBuffer beltFrameBuffer = this.renderAndTransform(
                    AllPartialModels.MECHANICAL_CRAFTER_BELT_FRAME, blockState);
            if (be.phase == BatchMechanicalCrafterBlockEntity.Phase.EXPORTING) {
                int textureIndex = (int) ((float) be.getCountDownSpeed() / 128f
                        * (float) AnimationTickHolder.getTicks());
                beltBuffer.shiftUVtoSheet(AllSpriteShifts.CRAFTER_THINGIES,
                        (float) (textureIndex % 4) / 4f, 0f, 1);
            }
            beltBuffer.light(light).renderInto(ms, vb);
            beltFrameBuffer.light(light).renderInto(ms, vb);
        } else {
            SuperByteBuffer arrowBuffer = this.renderAndTransform(
                    AllPartialModels.MECHANICAL_CRAFTER_ARROW, blockState);
            arrowBuffer.light(light).renderInto(ms, vb);
        }
    }

    private SuperByteBuffer renderAndTransform(PartialModel renderBlock, BlockState crafterState) {
        SuperByteBuffer buffer = CachedBuffers.partial(renderBlock, crafterState);
        float xRot = crafterState.getValue(BatchMechanicalCrafterBlock.POINTING).getXRotation();
        float yRot = AngleHelper.horizontalAngle(
                crafterState.getValue(HorizontalKineticBlock.HORIZONTAL_FACING));
        buffer.rotateCentered((float) ((yRot + 90f) / 180f * Math.PI), Direction.UP);
        buffer.rotateCentered((float) (xRot / 180f * Math.PI), Direction.EAST);
        return buffer;
    }
}