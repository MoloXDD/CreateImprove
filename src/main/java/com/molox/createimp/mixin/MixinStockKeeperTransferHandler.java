package com.molox.createimp.mixin;

import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.simibubi.create.compat.jei.StockKeeperTransferHandler;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

/**
 * 上一次修复（{@code MixinStockKeeperRequestScreen} 里对 {@code maxCraftable()}
 * 内 {@code Ingredient.test()} 的拦截）解决的是"请求栏被错误填入模板物品"这一
 * 结果，但 JEI 配方界面"+"号自己判断"材料够不够"这一步，走的是另一个完全独立
 * 的方法——{@code StockKeeperTransferHandler.transferRecipeOnClient()}，它是
 * 直接拿 {@code InventorySummary.getStacksByCount()} 的结果去和配方输入槽比对
 * （通过 JEI 自己的 {@code RecipeTransferUtil.getRecipeTransferOperations}），
 * 完全不经过 {@code maxCraftable}/{@code Ingredient.test}，所以之前那一处修复
 * 完全覆盖不到这里。
 * <p>
 * 而这份 {@code InventorySummary} 本身就是 {@code MixinLogisticalStockRequestPacket}
 * 用 {@code TemplateOrderSummaryHelper.augment()} 掺入过模板令牌（巨大占位数量）
 * 的那一份，令牌物品种类和真实材料完全一样，于是 JEI 这一步会误判"材料充足"，
 * 先把配方登记进 {@code recipesToOrder}；等真正调用 {@code requestCraftable}→
 * {@code maxCraftable} 算实际能凑几份时（这一步已经被上一次修复过滤掉了模板
 * 令牌，只看真实库存），如果真实库存确实是 0（现存模板本来就是因为这个材料
 * 没有库存才建的），算出来的可合成份数就是 0，最终表现为请求栏里出现一条
 * 卡在"请求 0 组"的幽灵配方订单，既没有真正请求材料，也没有提示材料不足。
 * <p>
 * 修复：只在这一个方法内，把 {@code getStacksByCount()} 返回的列表先剔除掉
 * 模板令牌条目再交给 JEI 判断，让"材料够不够"这一步也只看真实库存，模板令牌
 * 不再参与这里的匹配。
 */
@Mixin(value = StockKeeperTransferHandler.class, remap = false)
public abstract class MixinStockKeeperTransferHandler {

    @Redirect(method = "transferRecipeOnClient", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packager/InventorySummary;getStacksByCount()Ljava/util/List;"))
    private List<BigItemStack> createimp$excludeTemplateTokensFromStockCheck(InventorySummary instance) {
        List<BigItemStack> original = instance.getStacksByCount();
        List<BigItemStack> filtered = new ArrayList<>(original.size());
        for (BigItemStack entry : original) {
            if (!TemplateOrderTokenHelper.isToken(entry.stack)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }
}