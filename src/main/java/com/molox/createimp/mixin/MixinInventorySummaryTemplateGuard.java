package com.molox.createimp.mixin;

import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 背景（与流体包裹的真实冲突，非猜测）：
 * <p>
 * 模板令牌（{@link TemplateOrderTokenHelper#of}）本质上是把模板监测目标
 * （{@code target.display()}）复制一份、扣上自定义名字和一个标记数据组件，
 * 再塞进 {@link InventorySummary}，数量填一个没有实际意义的巨大占位值。当
 * 模板监测的是流体时，{@code target.display()} 是流体包裹自己的虚拟流体
 * 压缩罐物品（{@code CompressedTankItem}）——流体包裹反编译确认过，它自己
 * 对 {@code InventorySummary.add(ItemStack, int)} 打了 Mixin，专门识别这个
 * 物品并按“流体种类是否相同”合并条目，完全不看我们额外挂的模板标记数据
 * 组件。这会导致模板令牌和网络里这种流体的真实库存条目被错误合并，占位
 * 巨量数字污染真实库存数字（反之亦然），仓管界面库存显示彻底错乱。
 * <p>
 * 修复方式：在流体包裹的 Mixin 之前（本类 {@code priority} 设为低于默认值
 * 1000），先把“是模板令牌”这一种情况原样按原版 {@code isSameItemSameComponents}
 * 全量比对（天然会把我们额外挂的数据组件也算进去，与真实库存条目/其他模板
 * 令牌都能正确区分）处理完并 {@code cancel()}，流体包裹的 Mixin 与原版逻辑
 * 都不会再执行。不是模板令牌的调用完全不受影响，原样交给后续（原版或流体
 * 包裹）处理。
 */
@Mixin(value = InventorySummary.class, priority = 500, remap = false)
public abstract class MixinInventorySummaryTemplateGuard {

    @Shadow
    private Map<Item, List<BigItemStack>> items;

    @Shadow
    private int totalCount;

    @Unique
    private static final int MAX_COUNT = 1_000_000_000;

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;I)V", at = @At("HEAD"), cancellable = true)
    private void createimp$addTemplateTokenExact(ItemStack stack, int count, CallbackInfo ci) {
        if (count == 0 || stack.isEmpty()) {
            return;
        }
        if (!TemplateOrderTokenHelper.isToken(stack)) {
            return;
        }
        if (this.totalCount < MAX_COUNT) {
            this.totalCount += count;
        }
        List<BigItemStack> stacks = this.items.computeIfAbsent(stack.getItem(), k -> new ArrayList<>());
        for (BigItemStack existing : stacks) {
            if (ItemStack.isSameItemSameComponents(existing.stack, stack)) {
                if (existing.count < MAX_COUNT) {
                    existing.count += count;
                }
                ci.cancel();
                return;
            }
        }
        ItemStack toAdd = stack.getCount() > stack.getMaxStackSize() ? stack.copyWithCount(1) : stack;
        stacks.add(new BigItemStack(toAdd, count));
        ci.cancel();
    }
}