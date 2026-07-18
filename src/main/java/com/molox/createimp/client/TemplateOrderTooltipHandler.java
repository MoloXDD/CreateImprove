package com.molox.createimp.client;

import com.molox.createimp.item.TemplateOrderTarget;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.simibubi.create.AllTags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

public class TemplateOrderTooltipHandler {

    private static List<ItemStack> currentTemplateDisplays = List.of();

    public static void updateCurrentTemplateDisplays(List<ItemStack> displays) {
        currentTemplateDisplays = displays;
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(stack);
        if (target == null) {
            return;
        }

        Player player = event.getEntity();
        boolean cannotRequest = player != null && isEncodeRequesterItem(player.getMainHandItem());

        int duplicateCount = 0;
        for (ItemStack display : currentTemplateDisplays) {
            if (ItemStack.isSameItemSameComponents(display, target.display())) {
                duplicateCount++;
            }
        }
        boolean showRecipeLine = duplicateCount > 1;

        if (!showRecipeLine && !cannotRequest) {
            return;
        }

        List<Component> tooltip = event.getToolTip();
        if (!tooltip.isEmpty()) {
            Component nameLine = tooltip.get(0);
            tooltip.clear();
            tooltip.add(nameLine);
        }

        if (cannotRequest) {
            tooltip.add(Component.translatable("createimp.item.template_order_token.tooltip_cannot_request")
                    .withStyle(ChatFormatting.RED));
        }

        if (showRecipeLine) {
            List<ItemStack> ingredients = target.ingredients();
            MutableComponent line = Component.translatable("createimp.item.template_order_token.tooltip_prefix");
            for (ItemStack ingredient : ingredients) {
                line = line.append(" ").append(ingredient.getHoverName());
            }
            line = line.append(Component.translatable("createimp.item.template_order_token.tooltip_suffix"));
            tooltip.add(line.withStyle(ChatFormatting.GRAY));
        }
    }

    private static boolean isEncodeRequesterItem(ItemStack stack) {
        // 红石请求器现在允许携带模板，只有桌布类物品仍然不支持模板，
        // 才需要提示"当前无法请求模板"。
        return AllTags.AllItemTags.TABLE_CLOTHS.matches(stack);
    }
}