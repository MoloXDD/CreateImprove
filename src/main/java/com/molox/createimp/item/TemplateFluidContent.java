package com.molox.createimp.item;

import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Objects;

/**
 * 数据组件要求它的值类型必须自己实现 {@code equals}/{@code hashCode}（而且
 * 不可变），{@link FluidStack} 本身不满足这个要求，不能直接当数据组件的值
 * 类型用——这一点是通过实机崩溃日志确认的（{@code NeoForge CommonHooks.
 * validateComponent} 会在 {@code stack.set(...)} 时校验并抛出
 * {@code IllegalArgumentException}），不是猜测。这里照抄流体包裹自己
 * （{@code FluidTankContent}）的做法，用一个手写 {@code equals}/{@code hashCode}
 * 的包装类型把 {@link FluidStack} 包一层再挂数据组件。
 */
public record TemplateFluidContent(FluidStack fluid) {

    public static final com.mojang.serialization.Codec<TemplateFluidContent> CODEC =
            FluidStack.OPTIONAL_CODEC.xmap(TemplateFluidContent::new, TemplateFluidContent::fluid);

    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, TemplateFluidContent> STREAM_CODEC =
            FluidStack.OPTIONAL_STREAM_CODEC.map(TemplateFluidContent::new, TemplateFluidContent::fluid);

    public static TemplateFluidContent empty() {
        return new TemplateFluidContent(FluidStack.EMPTY);
    }

    public boolean isEmpty() {
        return this.fluid.isEmpty();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TemplateFluidContent other)) {
            return false;
        }
        if (this.fluid.isEmpty() || other.fluid.isEmpty()) {
            return this.fluid.isEmpty() == other.fluid.isEmpty();
        }
        return FluidStack.isSameFluidSameComponents(this.fluid, other.fluid) && this.fluid.getAmount() == other.fluid.getAmount();
    }

    @Override
    public int hashCode() {
        if (this.fluid.isEmpty()) {
            return 0;
        }
        return Objects.hash(this.fluid.getFluid(), this.fluid.getComponentsPatch(), this.fluid.getAmount());
    }
}