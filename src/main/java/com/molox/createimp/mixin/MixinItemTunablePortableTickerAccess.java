package com.molox.createimp.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.molox.createimp.item.TemplateOrderSummaryHelper;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

/**
 * 幻翼物流（CreatePhantom）可调频便携式发报机是可选依赖，
 * {@code ItemTunablePortableTickerAccess} 只有装了幻翼物流才存在——用
 * {@code targets} 字符串而不是 {@code value = X.class} 是为了避免 Mixin
 * 处理本类注解时就去解析这个类型，做法与 {@link MixinFluidPackagerArrivalDedup}
 * 对流体包裹可选依赖类的处理方式一致。
 * <p>
 * {@code fetchSummary()} 是幻翼便携发报机查询库存快照的唯一出口（反编译
 * 确认：无论是玩家打开界面后的常规轮询，还是切换频道后的重新查询，最终
 * 都会调用到这一个方法），在这里把当前网络下所有可下单模板的令牌混入
 * 返回结果，效果与 {@link MixinLogisticalStockRequestPacket} 对 Create
 * 原版仓储管理员做的事情完全一致，只是这里只有一个统一的注入点，不需要
 * 像原版那样再额外处理"打开菜单瞬间单独推送一次"的旁路。
 */
@Mixin(targets = "com.yision.phantom.item.ticker.access.ItemTunablePortableTickerAccess", remap = false)
public abstract class MixinItemTunablePortableTickerAccess {

    @Shadow
    private UUID network;

    @ModifyReturnValue(method = "fetchSummary", at = @At("RETURN"))
    private InventorySummary createimp$augmentWithTemplates(InventorySummary original) {
        return TemplateOrderSummaryHelper.augment(original, this.network);
    }
}
