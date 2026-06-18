package com.molox.createimp.block.labeled_redstone_link;

import com.mojang.blaze3d.vertex.PoseStack;
import com.molox.createimp.CreateImpConfig;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;

public class LabeledRedstoneLinkRenderer extends SmartBlockEntityRenderer<LabeledRedstoneLinkBlockEntity> {

    public LabeledRedstoneLinkRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(LabeledRedstoneLinkBlockEntity be, float partialTicks, PoseStack poseStack,
                              MultiBufferSource buffer, int light, int overlay) {
        CreateImpConfig config = AutoConfig.getConfigHolder(CreateImpConfig.class).getConfig();
        if (!config.labeledRedstoneLinkConfig.showFrequencyLabel) return;
        String freq = be.getFrequencyText();
        renderNameplateOnHover(be, Component.literal(freq), 0.25f, poseStack, buffer, light);
    }
}