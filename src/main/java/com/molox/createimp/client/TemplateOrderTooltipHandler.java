package com.molox.createimp.client;

import com.molox.createimp.item.TemplateOrderTarget;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

public class TemplateOrderTooltipHandler {

    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(stack);
        if (target == null) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        if (!tooltip.isEmpty()) {
            Component nameLine = tooltip.get(0);
            tooltip.clear();
            tooltip.add(nameLine);
        }
        List<ItemStack> ingredients = target.ingredients();
        MutableComponent line = Component.translatable("createimp.item.template_order_token.tooltip_prefix");
        for (ItemStack ingredient : ingredients) {
            line = line.append(" ").append(ingredient.getHoverName());
        }
        line = line.append(Component.translatable("createimp.item.template_order_token.tooltip_suffix"));
        tooltip.add(line.withStyle(ChatFormatting.GRAY));
    }
}