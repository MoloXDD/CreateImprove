package com.molox.createimp.block.template_panel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

public record TemplatePanelPosition(BlockPos pos, TemplatePanelBlock.PanelSlot slot) {

    public static final Codec<TemplatePanelPosition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(TemplatePanelPosition::pos),
            TemplatePanelBlock.PanelSlot.CODEC.fieldOf("slot").forGetter(TemplatePanelPosition::slot)
    ).apply(instance, TemplatePanelPosition::new));

    public static final StreamCodec<ByteBuf, TemplatePanelPosition> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TemplatePanelPosition::pos,
            TemplatePanelBlock.PanelSlot.STREAM_CODEC, TemplatePanelPosition::slot,
            TemplatePanelPosition::new
    );
}