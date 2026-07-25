package com.molox.createimp.item;

import com.molox.createimp.compat.fluidlogistics.FluidLogisticsCompat;
import com.molox.createimp.compat.fluidlogistics.TemplateFluidDisplayHelper;
import com.molox.createimp.registry.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
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

    /**
     * 监测目标是流体包裹的虚拟流体过滤物时，令牌不直接复制这个过滤物本身
     * （原因见 {@link TemplateFluidTokenItem} 类注释：会被流包一整套无差别
     * 的仓管界面 Mixin 连带命中），改用我们自己的
     * {@link TemplateFluidTokenItem} 承载同一份流体信息；其余情况（监测目标
     * 是普通物品）行为不变，仍然直接复制展示物本身。两种情况下
     * {@link TemplateOrderTarget}（包含真正的监测目标）都完整保留在数据
     * 组件里，不受影响。
     */
    public static ItemStack of(TemplateOrderTarget target) {
        ItemStack display = target.display();
        ItemStack stack;
        if (FluidLogisticsCompat.isLoaded() && TemplateFluidDisplayHelper.isVirtualFluidDisplay(display)) {
            FluidStack fluid = TemplateFluidDisplayHelper.getFluid(display);
            stack = TemplateFluidTokenItem.create(fluid);
        } else {
            stack = display.copyWithCount(1);
        }
        Component baseName = stack.getHoverName();
        stack.set(DataComponents.CUSTOM_NAME,
                Component.translatable("createimp.item.template_order_token.name", baseName));
        stack.set(ModDataComponents.TEMPLATE_ORDER_TARGET.get(), target);
        return stack;
    }
}