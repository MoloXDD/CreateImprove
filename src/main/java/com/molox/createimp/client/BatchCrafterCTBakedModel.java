package com.molox.createimp.client;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.batch_mechanical_crafter.BatchCrafterCTBehaviour;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import com.simibubi.create.foundation.block.connected.CTType;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量动力合成器专用的连接纹理 BakedModel 包装类，替代 Create 自己的 CTModel。
 *
 * 与 CTModel 的区别只在最后一步"索引 -> 最终贴图"：
 * - 邻居连接状态的判断（谁跟谁连接、要不要反转UV）完全复用 BatchCrafterCTBehaviour
 *   已有的 connectsTo/reverseUVs/buildContext/getDataType 等方法，逻辑和效果不变；
 * - 但不再调用 CTSpriteShiftEntry.getOriginal()/getTarget()（那一步依赖运行时
 *   从图集里查询贴图对象，在 ModernFix 的 faster_texture_stitching 开启时可能失效），
 *   而是直接从 BatchCrafterCTQuadLibrary 里取出模型烘焙阶段就已经准备好的候选面，
 *   用 Create 自带的 BakedQuadHelper 只替换原始面的贴图坐标，保留其原有的顶点位置、
 *   法线、光照数据不变。
 *
 * 本类通过 BatchCrafterUnbakedGeometry 在模型烘焙阶段直接构造并包装，
 * 不再依赖 Create 的 MODEL_SWAPPER 事后一次性替换事件。
 */
public class BatchCrafterCTBakedModel extends BakedModelWrapper<BakedModel> {

    public static final ModelProperty<CTFaceIndices> CT_INDEX_PROPERTY = new ModelProperty<>();

    private static final String NS = CreateImp.MODID;
    private static final ResourceLocation FRONT_BACK_UNCONNECTED =
            ResourceLocation.fromNamespaceAndPath(NS, "block/batch_mechanical_crafter/batch_brass_block");
    private static final ResourceLocation SIDE_UNCONNECTED =
            ResourceLocation.fromNamespaceAndPath(NS, "block/batch_mechanical_crafter/batch_crafter_side");

    private final BatchCrafterCTBehaviour behaviour;

    public BatchCrafterCTBakedModel(BakedModel originalModel, BatchCrafterCTBehaviour behaviour) {
        super(originalModel);
        this.behaviour = behaviour;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        ModelData base = super.getModelData(level, pos, state, modelData);

        if (!state.hasProperty(HorizontalKineticBlock.HORIZONTAL_FACING)) {
            return base;
        }

        int[] frontBack = new int[6];
        int[] side = new int[6];
        java.util.Arrays.fill(frontBack, -1);
        java.util.Arrays.fill(side, -1);

        Direction facing = state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);

        for (Direction dir : Direction.values()) {
            CTType type = behaviour.getDataType(level, pos, state, dir);
            if (type == null) {
                continue;
            }
            ConnectedTextureBehaviour.CTContext context =
                    behaviour.buildContext(level, pos, state, dir, type.getContextRequirement());
            int index = type.getTextureIndex(context);

            boolean isFrontAxis = facing.getAxis() == dir.getAxis();
            if (isFrontAxis) {
                frontBack[dir.ordinal()] = index;
            } else {
                side[dir.ordinal()] = index;
            }
        }

        return base.derive()
                .with(CT_INDEX_PROPERTY, new CTFaceIndices(facing, frontBack, side))
                .build();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction direction, RandomSource rand, ModelData extraData, RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, direction, rand, extraData, renderType);

        if (state == null || quads.isEmpty()) {
            return quads;
        }
        if (!extraData.has(CT_INDEX_PROPERTY) || !BatchCrafterCTQuadLibrary.isReady()) {
            return quads;
        }

        CTFaceIndices indices = extraData.get(CT_INDEX_PROPERTY);

        List<BakedQuad> result = null;

        for (int i = 0; i < quads.size(); i++) {
            BakedQuad quad = quads.get(i);
            Direction quadDir = quad.getDirection();
            int frontBackIndex = indices.frontBack()[quadDir.ordinal()];
            int sideIndex = indices.side()[quadDir.ordinal()];
            if (frontBackIndex < 0 && sideIndex < 0) {
                continue;
            }

            BakedQuad substitute = null;
            boolean alreadyFinal = false;
            ResourceLocation spriteName = quad.getSprite().contents().name();

            if (frontBackIndex >= 0 && FRONT_BACK_UNCONNECTED.equals(spriteName)) {
                boolean isFront = quadDir == indices.facing();
                substitute = isFront
                        ? BatchCrafterCTQuadLibrary.getFront(frontBackIndex)
                        : BatchCrafterCTQuadLibrary.getBack(frontBackIndex);
            } else if (sideIndex >= 0 && SIDE_UNCONNECTED.equals(spriteName)) {
                substitute = computeSideSubstitute(quad, sideIndex);
                alreadyFinal = true;
            }

            if (substitute != null) {
                if (result == null) {
                    result = new ArrayList<>(quads);
                }
                result.set(i, alreadyFinal ? substitute : spliceUV(quad, substitute));
            }
        }

        return result != null ? result : quads;
    }

    /**
     * 对侧面的连接替换采用"解析换算"而不是"从预烘焙候选面搬运顶点UV"，
     * 因为侧面很多声明（比如 east/west 面）带了 90/180/270 度的 rotation，
     * 而按顶点编号直接搬运 UV 要求两个面的顶点顺序（也就是旋转朝向）完全一致，
     * 一旦真实面带了旋转、候选面没有，顶点对应关系就会错位，表现为贴图拉伸/错位。
     *
     * 这里改成直接对真实面自己已有的顶点 UV 做换算：先把顶点 UV 换算回"在原始
     * 16x16 贴图内的 0-1 比例位置"，再按连接状态对应的格子偏移映射到连接贴图
     * (32x32) 里对应象限的同一比例位置，公式和 Create 自己 CTSpriteShiftEntry
     * 的换算方式一致，只是换算对象换成了我们自己直接持有的贴图引用。
     * 由于是在真实面自己的顶点数组上原地换算，天然保留了原有的旋转/朝向，
     * 不存在顶点错位的问题。
     */
    private static BakedQuad computeSideSubstitute(BakedQuad quad, int connectionIndex) {
        TextureAtlasSprite original = quad.getSprite();
        TextureAtlasSprite target = BatchCrafterCTQuadLibrary.getSideConnectedSprite();
        if (target == null) {
            return null;
        }

        int sheetSize = 2;
        int tileX = connectionIndex % sheetSize;
        int tileY = connectionIndex / sheetSize;

        int[] vertexData = BakedQuadHelper.clone(quad).getVertices();
        float originalU0 = original.getU0(), originalU1 = original.getU1();
        float originalV0 = original.getV0(), originalV1 = original.getV1();

        for (int v = 0; v < 4; v++) {
            float u = BakedQuadHelper.getU(vertexData, v);
            float vv = BakedQuadHelper.getV(vertexData, v);

            float localU = (u - originalU0) / (originalU1 - originalU0);
            float localV = (vv - originalV0) / (originalV1 - originalV0);

            float targetU = target.getU((localU + tileX) / sheetSize);
            float targetV = target.getV((localV + tileY) / sheetSize);

            BakedQuadHelper.setU(vertexData, v, targetU);
            BakedQuadHelper.setV(vertexData, v, targetV);
        }

        return new BakedQuad(vertexData, quad.getTintIndex(), quad.getDirection(), target, quad.isShade());
    }

    /**
     * 保留原始面的顶点位置/法线/光照/朝向数据，只把贴图坐标和贴图引用
     * 替换成预烘焙候选面里的结果。
     */
    private static BakedQuad spliceUV(BakedQuad original, BakedQuad substituteSource) {
        int[] vertexData = BakedQuadHelper.clone(original).getVertices();
        int[] sourceVertexData = substituteSource.getVertices();
        for (int v = 0; v < 4; v++) {
            BakedQuadHelper.setU(vertexData, v, BakedQuadHelper.getU(sourceVertexData, v));
            BakedQuadHelper.setV(vertexData, v, BakedQuadHelper.getV(sourceVertexData, v));
        }
        return new BakedQuad(vertexData, original.getTintIndex(), original.getDirection(),
                substituteSource.getSprite(), original.isShade());
    }

    public record CTFaceIndices(Direction facing, int[] frontBack, int[] side) {
    }
}