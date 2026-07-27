package com.molox.createimp.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 幻翼物流可调频便携式发报机界面（{@code TunablePortableTickerScreen}）内部
 * 的分类条目类型是 {@code private static class CategoryEntry}——反编译确认
 * 它本身就是私有的（不像 Create 原版 {@code StockKeeperRequestScreen
 * .CategoryEntry} 是 public 且构造方法也是 public），因此不能在本模组代码里
 * 直接声明这个类型，只能通过访问器接口读写其 {@code y}/{@code hidden} 字段。
 * <p>
 * 构造新实例这件事本来想用 {@code @Invoker("<init>")} 做（和这两个字段访问器
 * 放在一起），但实测确认 Mixin 的构造器 Invoker 要求返回类型必须与目标类型
 * 精确匹配，不能像普通 {@code @Accessor} 那样放宽成 {@code Object}——既然
 * 目标类型本身私有到没法在源码里写出来，这条路走不通，改为在
 * {@link MixinTunablePortableTickerScreen} 里用普通反射
 * （{@code Class.forName} + {@code getDeclaredConstructor().setAccessible(true)}）
 * 构造实例，构造出来之后再强转成本接口读写字段。
 * <p>
 * 目标类使用 {@code targets} 字符串而不是 {@code value = X.class}，原因与
 * {@link MixinFluidPackagerArrivalDedup} 注释里说明的一致：幻翼物流是可选
 * 依赖，避免 Mixin 处理本类注解时就去解析这个类型。
 */
@Mixin(targets = "com.yision.phantom.item.ticker.TunablePortableTickerScreen$CategoryEntry", remap = false)
public interface PhantomCategoryEntryAccessor {

    @Accessor("y")
    int createimp$getY();

    @Accessor("y")
    void createimp$setY(int y);

    @Accessor("hidden")
    boolean createimp$isHidden();

    @Accessor("hidden")
    void createimp$setHidden(boolean hidden);
}