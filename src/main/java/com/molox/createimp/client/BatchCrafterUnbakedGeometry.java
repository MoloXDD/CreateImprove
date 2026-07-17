package com.molox.createimp.client;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.molox.createimp.CreateImp;
import com.molox.createimp.block.batch_mechanical_crafter.BatchCrafterCTBehaviour;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import java.util.function.Function;

/**
 * 批量动力合成器专用的自定义几何体加载器。
 *
 * 背景：之前借用 Create 自己的 MODEL_SWAPPER（监听 ModelEvent.ModifyBakingResult，
 * 在原版一次性烘焙完所有模型之后，事后把结果调包一次）来套上连接纹理包装。
 * 但这套机制只对"那一次性事件发生时确实存在于烘焙结果里"的模型生效。
 * 在 ModernFix 的 mixin.perf.dynamic_resources 与 mixin.perf.faster_texture_stitching
 * 同时开启的特定组合下，批量动力合成器的模型会绕开那次一次性事件，导致包装从未
 * 生效，方块直接显示为未连接状态的原始贴图。
 *
 * 换成自定义几何体加载器后，BatchCrafterCTBakedModel 的包装改为直接发生在模型
 * 被烘焙(bake)的那一刻本身——不管这次烘焙是提前烘焙还是懒加载烘焙、发生在游戏
 * 生命周期的什么时间点，只要模型真的被烘焙了，就一定会经过这里，不再依赖任何
 * "烘焙完之后再补一刀"的事件。
 *
 * 内部实际的方块朝向/元素/贴图解析完全复用原版 BlockModel 自己的解析与烘焙逻辑
 * （通过 context.deserialize 把同一份 JSON 按标准格式解析成 BlockModel，再调用
 * 它自己的 bake 方法），我们只是在拿到烘焙结果后包一层 BatchCrafterCTBakedModel，
 * 方块模型 JSON 里的 elements/textures/parent 等字段完全不用改。
 */
public class BatchCrafterUnbakedGeometry implements IUnbakedGeometry<BatchCrafterUnbakedGeometry> {

    private final BlockModel innerModel;

    public BatchCrafterUnbakedGeometry(BlockModel innerModel) {
        this.innerModel = innerModel;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, ItemOverrides overrides) {
        BakedModel baseModel = innerModel.bake(baker, innerModel, spriteGetter, modelState, true);
        return new BatchCrafterCTBakedModel(baseModel, new BatchCrafterCTBehaviour());
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
        innerModel.resolveParents(modelGetter);
    }

    public static class Loader implements IGeometryLoader<BatchCrafterUnbakedGeometry> {

        public static final Loader INSTANCE = new Loader();
        public static final ResourceLocation ID =
                ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "batch_crafter_ct");

        private Loader() {
        }

        @Override
        public BatchCrafterUnbakedGeometry read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) throws JsonParseException {

            JsonObject withoutLoader = jsonObject.deepCopy();
            withoutLoader.remove("loader");
            BlockModel innerModel = deserializationContext.deserialize(withoutLoader, BlockModel.class);
            return new BatchCrafterUnbakedGeometry(innerModel);
        }
    }
}