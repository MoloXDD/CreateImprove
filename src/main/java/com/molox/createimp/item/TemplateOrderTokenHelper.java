package com.molox.createimp.item;

import com.molox.createimp.registry.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class TemplateOrderTokenHelper {

    private TemplateOrderTokenHelper() {
    }

    public static boolean isToken(ItemStack stack) {
        return stack.has(ModDataComponents.TEMPLATE_ORDER_TARGET.get());
    }

    @Nullable
    public static TemplateOrderTarget getTarget(ItemStack stack) {
        return stack.get(ModDataComponents.TEMPLATE_ORDER_TARGET.get());
    }

    public static ItemStack of(TemplateOrderTarget target) {
        ItemStack stack = target.display().copyWithCount(1);
        Component baseName = stack.getHoverName();
        stack.set(DataComponents.CUSTOM_NAME,
                Component.translatable("createimp.item.template_order_token.name", baseName));
        stack.set(ModDataComponents.TEMPLATE_ORDER_TARGET.get(), target);
        return stack;
    }
}