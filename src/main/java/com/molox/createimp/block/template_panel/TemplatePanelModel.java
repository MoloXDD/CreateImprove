package com.molox.createimp.block.template_panel;

import com.molox.createimp.CreateImp;
import com.simibubi.create.foundation.model.BakedModelWrapperWithData;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TemplatePanelModel extends BakedModelWrapperWithData {

    public static final PartialModel TEMPLATE_PANEL = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "block/template_panel/panel"));

    private static final ModelProperty<TemplatePanelModelData> PANEL_PROPERTY = new ModelProperty<>();

    public static void init() {
    }

    public TemplatePanelModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    protected ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter world, BlockPos pos, BlockState state, ModelData blockEntityData) {
        TemplatePanelModelData data = new TemplatePanelModelData();
        for (TemplatePanelBlock.PanelSlot slot : TemplatePanelBlock.PanelSlot.values()) {
            TemplatePanelBehaviour behaviour = TemplatePanelBehaviour.at(world, new TemplatePanelPosition(pos, slot));
            if (behaviour == null) continue;
            data.activeSlots.add(slot);
        }
        return builder.with(PANEL_PROPERTY, data);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
        if (side != null || !data.has(PANEL_PROPERTY)) {
            return Collections.emptyList();
        }
        TemplatePanelModelData modelData = data.get(PANEL_PROPERTY);
        ArrayList<BakedQuad> quads = new ArrayList<>(super.getQuads(state, null, rand, data, renderType));
        for (TemplatePanelBlock.PanelSlot slot : TemplatePanelBlock.PanelSlot.values()) {
            if (!modelData.activeSlots.contains(slot)) continue;
            this.addPanel(quads, state, slot, rand, data, renderType);
        }
        return quads;
    }

    public void addPanel(List<BakedQuad> quads, BlockState state, TemplatePanelBlock.PanelSlot slot, RandomSource rand, ModelData data, RenderType renderType) {
        List<BakedQuad> quadsToAdd = TEMPLATE_PANEL.get().getQuads(state, null, rand, data, RenderType.solid());
        float xRot = 57.295776f * TemplatePanelBlock.getXRot(state);
        float yRot = 57.295776f * TemplatePanelBlock.getYRot(state);
        for (BakedQuad bakedQuad : quadsToAdd) {
            int[] vertices = bakedQuad.getVertices();
            int[] transformedVertices = Arrays.copyOf(vertices, vertices.length);
            Vec3 quadNormal = Vec3.atLowerCornerOf(bakedQuad.getDirection().getNormal());
            quadNormal = VecHelper.rotate(quadNormal, 180.0, Direction.Axis.Y);
            quadNormal = VecHelper.rotate(quadNormal, xRot + 90.0f, Direction.Axis.X);
            quadNormal = VecHelper.rotate(quadNormal, yRot, Direction.Axis.Y);
            for (int i = 0; i < vertices.length / BakedQuadHelper.VERTEX_STRIDE; ++i) {
                Vec3 vertex = BakedQuadHelper.getXYZ(vertices, i);
                Vec3 normal = BakedQuadHelper.getNormalXYZ(vertices, i);
                vertex = vertex.add(slot.xOffset * 0.5, 0.0, slot.yOffset * 0.5);
                vertex = VecHelper.rotateCentered(vertex, 180.0, Direction.Axis.Y);
                vertex = VecHelper.rotateCentered(vertex, xRot + 90.0f, Direction.Axis.X);
                vertex = VecHelper.rotateCentered(vertex, yRot, Direction.Axis.Y);
                normal = VecHelper.rotate(normal, 180.0, Direction.Axis.Y);
                normal = VecHelper.rotate(normal, xRot + 90.0f, Direction.Axis.X);
                normal = VecHelper.rotate(normal, yRot, Direction.Axis.Y);
                BakedQuadHelper.setXYZ(transformedVertices, i, vertex);
                BakedQuadHelper.setNormalXYZ(transformedVertices, i, new Vec3(0.0, 1.0, 0.0));
            }
            Direction newNormal = Direction.fromDelta(
                    (int) Math.round(quadNormal.x), (int) Math.round(quadNormal.y), (int) Math.round(quadNormal.z));
            quads.add(new BakedQuad(transformedVertices, bakedQuad.getTintIndex(), newNormal, bakedQuad.getSprite(), bakedQuad.isShade()));
        }
    }

    private static class TemplatePanelModelData {
        java.util.EnumSet<TemplatePanelBlock.PanelSlot> activeSlots = java.util.EnumSet.noneOf(TemplatePanelBlock.PanelSlot.class);
    }
}