package com.molox.createimp.item;

import com.molox.createimp.registry.ModDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

/**
 * 模板令牌在"监测目标是流体"时使用的专属展示物品。
 * <p>
 * 【为什么不直接用流体包裹的压缩罐物品】流体包裹对仓储发报机请求界面
 * （{@code StockKeeperRequestScreen}）打了一整套无差别的客户端 Mixin：只要
 * 一个展示条目的物品是它自己的压缩罐物品且处于"虚拟"状态，就会被当成一次
 * 真实的流体库存来处理——包括角标数字格式化、点击/滚轮直接调整流体请求量。
 * 这些判断完全不看物品身上有没有挂我们自己的模板标记数据组件。如果模板
 * 令牌继续用这个物品本身，就会被流包这一整套逻辑连带命中，导致仓管界面
 * 数值错乱、点击行为失控（这些问题都是通过反编译流包源码实际确认的，不是
 * 猜测）。
 * <p>
 * 所以模板令牌改用我们自己注册的这个物品：图标渲染直接复用 Create/Catnip
 * 自带的流体渲染工具（见 {@link TemplateFluidTokenItemRenderer}，客户端渲染器
 * 的注册在 {@code TemplateFluidTokenClientRenderer} 里，跟随项目已有的
 * 仅客户端安全注册方式），效果与流体包裹自己压缩罐物品的图标一致（同样
 * 贴着流体真实材质+染色），但完全不引用流体包裹的任何类。真正的监测目标
 * （流包的虚拟压缩罐过滤物）仍然完整保存在 {@code TemplateOrderTarget} 数据
 * 组件里，材料计算、下单等所有需要知道"真正监测的是什么"的逻辑都还是读
 * 那个组件，不受影响。
 */
public class TemplateFluidTokenItem extends Item {

    public TemplateFluidTokenItem(Properties properties) {
        super(properties);
    }

    public static FluidStack getFluid(ItemStack stack) {
        TemplateFluidContent content = stack.get(ModDataComponents.TEMPLATE_FLUID_CONTENT.get());
        return content == null ? FluidStack.EMPTY : content.fluid();
    }

    public static ItemStack create(FluidStack fluid) {
        ItemStack stack = new ItemStack(com.molox.createimp.registry.ModItems.TEMPLATE_FLUID_TOKEN.get());
        stack.set(ModDataComponents.TEMPLATE_FLUID_CONTENT.get(), new TemplateFluidContent(fluid.copy()));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        FluidStack fluid = getFluid(stack);
        if (!fluid.isEmpty()) {
            return fluid.getHoverName().plainCopy();
        }
        return super.getName(stack);
    }

    /**
     * 高级提示（F3+H）里"由哪个模组提供"这一行，默认会按物品自己的注册
     * 命名空间（也就是我们自己的模组）显示。流体包裹自己压缩罐物品的这行
     * 是按流体真正所属的模组显示的（比如水显示 Minecraft）——它是靠自己
     * 完整重新拼装整个提示内容做到的，仅限仓管界面。这里用 NeoForge 专门
     * 为"这个物品其实代表另一个来源"这种情况设计的
     * {@code getCreatorModId}（文档原话："比如 Forge 的万能桶给每种流体建的
     * 子物品，这里就返回那个流体真正所属的模组ID"，正是我们这个场景），
     * 一处覆盖即可对任意界面（不止仓管）生效，不需要另外重新拼装提示内容。
     */
    @Nullable
    @Override
    public String getCreatorModId(ItemStack itemStack) {
        FluidStack fluid = getFluid(itemStack);
        if (!fluid.isEmpty()) {
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
            if (fluidId != null) {
                return fluidId.getNamespace();
            }
        }
        return CommonHooks.getDefaultCreatorModId(itemStack);
    }
}