package com.molox.createimp.item;

import com.molox.createimp.block.template_panel.TemplatePanelPosition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public record TemplateOrderTarget(UUID network, TemplatePanelPosition position, ItemStack display, List<ItemStack> ingredients) {

    public static final Codec<TemplateOrderTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("network").forGetter(TemplateOrderTarget::network),
            TemplatePanelPosition.CODEC.fieldOf("position").forGetter(TemplateOrderTarget::position),
            ItemStack.CODEC.fieldOf("display").forGetter(TemplateOrderTarget::display),
            ItemStack.CODEC.listOf().fieldOf("ingredients").forGetter(TemplateOrderTarget::ingredients)
    ).apply(instance, TemplateOrderTarget::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TemplateOrderTarget> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, TemplateOrderTarget::network,
            TemplatePanelPosition.STREAM_CODEC, TemplateOrderTarget::position,
            ItemStack.STREAM_CODEC, TemplateOrderTarget::display,
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), TemplateOrderTarget::ingredients,
            TemplateOrderTarget::new
    );

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TemplateOrderTarget other)) {
            return false;
        }
        if (!this.network.equals(other.network) || !this.position.equals(other.position)) {
            return false;
        }
        if (!ItemStack.isSameItemSameComponents(this.display, other.display)) {
            return false;
        }
        if (this.ingredients.size() != other.ingredients.size()) {
            return false;
        }
        for (int i = 0; i < this.ingredients.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(this.ingredients.get(i), other.ingredients.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = this.network.hashCode();
        result = 31 * result + this.position.hashCode();
        result = 31 * result + this.display.getItem().hashCode();
        return result;
    }
}