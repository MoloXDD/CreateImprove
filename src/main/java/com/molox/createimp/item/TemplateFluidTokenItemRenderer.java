package com.molox.createimp.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 流体模板令牌的图标渲染：直接复用 Create 自己的自定义物品渲染框架
 * （{@code CustomRenderedItemModelRenderer}）加 Catnip 自带的通用流体渲染
 * 工具（{@code CatnipServices.FLUID_RENDERER}），画一个贴着流体真实材质、
 * 带正确染色的小方块——跟流体包裹自己给压缩罐物品做的图标是同一套技术
 * （反编译流体包裹的 {@code CompressedTankItemRenderer} 确认过），只是
 * 这里完全走 Create/Catnip 自己的公开基础设施，不引用流体包裹的任何类。
 * <p>
 * 数值上（半宽 0.5、深度 0.03125）跟流体包裹保持一致，视觉大小与它的压缩罐
 * 图标一致。
 */
public class TemplateFluidTokenItemRenderer extends CustomRenderedItemModelRenderer {

    private static final float HALF_SIZE = 0.5f;
    private static final float DEPTH = 0.03125f;

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext displayContext, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        FluidStack fluid = TemplateFluidTokenItem.getFluid(stack);
        if (fluid.isEmpty()) {
            renderer.render(model.getOriginalModel(), light);
            return;
        }
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid,
                -HALF_SIZE, -HALF_SIZE, -DEPTH, HALF_SIZE, HALF_SIZE, 0.0f,
                buffer, ms, light, true, false);
    }
}