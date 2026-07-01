package com.molox.createimp.block.template_panel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class TemplatePanelRenderer extends SmartBlockEntityRenderer<TemplatePanelBlockEntity> {

    public TemplatePanelRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(TemplatePanelBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        for (TemplatePanelBehaviour behaviour : be.panels.values()) {
            if (!behaviour.isActive()) continue;
            for (TemplatePanelConnection connection : behaviour.targetedBy.values()) {
                renderPath(behaviour, connection, partialTicks, ms, buffer, light, overlay);
            }
        }
    }

    public static void renderPath(TemplatePanelBehaviour behaviour, TemplatePanelConnection connection, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState blockState = behaviour.blockEntity.getBlockState();
        List<Direction> path = connection.getPath(behaviour.getWorld(), blockState, behaviour.getPanelPosition());
        float xRot = TemplatePanelBlock.getXRot(blockState) + 1.5707964f;
        float yRot = TemplatePanelBlock.getYRot(blockState);
        int color = behaviour.getIngredientStatusColor();
        float yOffset = 1.0f;

        float currentX = 0.0f;
        float currentZ = 0.0f;
        for (int i = 0; i < path.size(); ++i) {
            Direction direction = path.get(i);
            currentX = (float) (currentX + direction.getStepX() * 0.5);
            currentZ = (float) (currentZ + direction.getStepZ() * 0.5);
            boolean isArrowSegment = i == 0;
            PartialModel partial = (isArrowSegment ? AllPartialModels.FACTORY_PANEL_ARROWS : AllPartialModels.FACTORY_PANEL_LINES)
                    .get(direction.getOpposite());
            SuperByteBuffer connectionSprite = CachedBuffers.partial(partial, blockState)
                    .rotateCentered(yRot, Direction.UP)
                    .rotateCentered(xRot, Direction.EAST)
                    .rotateCentered((float) Math.PI, Direction.UP)
                    .translate(behaviour.slot.xOffset * 0.5 + 0.25, 0.0, behaviour.slot.yOffset * 0.5 + 0.25)
                    .translate(currentX, (yOffset + (direction.get2DDataValue() % 2) * 0.125f) / 512.0f, currentZ);
            connectionSprite.color(color).light(light).overlay(overlay).renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
        }
    }
}