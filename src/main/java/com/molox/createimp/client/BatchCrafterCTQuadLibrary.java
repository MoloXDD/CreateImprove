package com.molox.createimp.client;

import com.molox.createimp.CreateImp;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 批量动力合成器连接纹理的预烘焙候选面查询表。
 *
 * 这些候选面来自一个专门的辅助模型（ct_library.json），该模型不会被直接展示，
 * 仅用于让"正面/背面在各种邻居连接状态下"的贴图裁剪，走一遍标准的原版模型
 * 烘焙流程提前烘焙好；侧面则只需要从这个辅助模型里取出连接贴图表的贴图对象，
 * 具体换算在 BatchCrafterCTBakedModel 里按顶点解析完成。
 *
 * 这样渲染时不再需要经过 Create 那套"运行时按图集坐标现算 UV"的机制
 * （CTSpriteShiftEntry/StitchedSprite），从根源上避免与 ModernFix 的
 * faster_texture_stitching 冲突，同时保持视觉效果与之前完全一致。
 */
public final class BatchCrafterCTQuadLibrary {

    public static final ResourceLocation LIBRARY_MODEL =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "block/batch_mechanical_crafter/ct_library");

    /**
     * 正面/背面实际可能出现的贴图表格子索引，按升序排列。
     * 这个顺序必须和生成 ct_library.json 时使用的顺序完全一致，
     * 因为查询时是按"数组下标"直接对应烘焙结果里的第几个面。
     */
    private static final int[] FRONT_BACK_VALID_INDICES = {
            0, 1, 2, 3, 8, 9, 10, 11, 12, 13, 16, 17, 18, 19, 20, 21, 24, 25, 26, 27, 28, 29, 30,
            32, 33, 34, 35, 36, 37, 38, 40, 41, 42, 43, 44, 45, 46, 48, 49, 50, 51, 52, 53, 54, 56, 57, 58
    };

    private static final int SIDE_CANDIDATE_COUNT = 12;

    private static volatile BakedQuad[] frontQuads;
    private static volatile BakedQuad[] backQuads;
    private static volatile BakedQuad sideReferenceQuad;

    private static volatile boolean ready = false;

    private BatchCrafterCTQuadLibrary() {
    }

    /**
     * 在 ModelEvent.ModifyBakingResult 里，拿到辅助模型烘焙结果后调用一次。
     *
     * 注意：ct_library.json 里的面都没有声明 "cullface"，烘焙后会全部进入
     * SimpleBakedModel 内部的 unculledFaces 列表，只有传 direction=null
     * 才能查到（分方向查询 culledFaces.get(direction) 只对声明了 cullface 的
     * 面有效）。所以这里改成一次性按 direction=null 取出全部候选面，再按
     * ct_library.json 里声明的顺序（前47个正面、接着47个背面、最后12个侧面）
     * 手动切片，不依赖方向分桶。
     */
    public static void init(BakedModel libraryModel) {
        RandomSource rand = RandomSource.create(0L);

        List<BakedQuad> all = libraryModel.getQuads(null, null, rand, ModelData.EMPTY, null);

        int expectedFrontBack = FRONT_BACK_VALID_INDICES.length;
        int expectedTotal = expectedFrontBack * 2 + SIDE_CANDIDATE_COUNT;

        if (all.size() != expectedTotal) {
            CreateImp.LOGGER.error(
                    "批量动力合成器连接纹理候选面总数不符合预期(实际={}, 期望={})，"
                            + "本次连接纹理替换将被跳过，方块会显示为未连接状态，请检查 ct_library.json 是否被意外改动",
                    all.size(), expectedTotal);
            ready = false;
            return;
        }

        frontQuads = all.subList(0, expectedFrontBack).toArray(new BakedQuad[0]);
        backQuads = all.subList(expectedFrontBack, expectedFrontBack * 2).toArray(new BakedQuad[0]);
        sideReferenceQuad = all.get(expectedFrontBack * 2);

        ready = true;
    }

    public static boolean isReady() {
        return ready;
    }

    @Nullable
    public static BakedQuad getFront(int textureIndex) {
        return lookupFrontBack(frontQuads, textureIndex);
    }

    @Nullable
    public static BakedQuad getBack(int textureIndex) {
        return lookupFrontBack(backQuads, textureIndex);
    }

    @Nullable
    private static BakedQuad lookupFrontBack(BakedQuad[] quads, int textureIndex) {
        if (!ready || quads == null || textureIndex < 0) return null;
        for (int i = 0; i < FRONT_BACK_VALID_INDICES.length; i++) {
            if (FRONT_BACK_VALID_INDICES[i] == textureIndex) {
                return quads[i];
            }
        }
        return null;
    }

    /**
     * 供侧面连接纹理的解析换算使用，返回连接贴图表（batch_crafter_side_connected）
     * 的贴图对象引用。
     */
    @Nullable
    public static TextureAtlasSprite getSideConnectedSprite() {
        if (!ready || sideReferenceQuad == null) return null;
        return sideReferenceQuad.getSprite();
    }
}