package com.molox.createimp.block.brass_scrap_bucket;

import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class BrassScrapBucketRenderer extends SmartBlockEntityRenderer<BrassScrapBucketBlockEntity> {

    public BrassScrapBucketRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(BrassScrapBucketBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
    }
}