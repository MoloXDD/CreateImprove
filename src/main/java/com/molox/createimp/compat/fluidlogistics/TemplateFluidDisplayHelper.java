package com.molox.createimp.compat.fluidlogistics;

import com.yision.fluidlogistics.compat.ghost.VirtualFluidGhostStacks;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.util.FluidAmountHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 本类集中封装所有对流体包裹（FluidLogistics）具体类的直接引用（
 * {@code CompressedTankItem}、{@code FluidStack}、{@code FluidAmountHelper}、
 * {@code VirtualFluidGhostStacks}）。
 * <p>
 * 【重要】本类里的任何方法都只允许在调用方已经确认
 * {@link FluidLogisticsCompat#isLoaded()} 为真之后才能调用——本类本身不做
 * 这个判断，是因为它需要在编译期就能正常解析这些类型（compileOnly 依赖），
 * 一旦某个方法被真正调用而流体包裹未安装，对应的类会在类加载阶段直接抛出
 * {@link NoClassDefFoundError}。把所有引用集中收敛到这一个类里，就是为了让
 * “有没有装流体包裹”这件事只需要在调用点做一次判断，不会散落进
 * {@code TemplatePanelBehaviour} 等核心类，避免核心类本身在没装流体包裹时
 * 也被牵连着尝试加载流体包裹的类。
 */
public final class TemplateFluidDisplayHelper {

    private TemplateFluidDisplayHelper() {
    }

    /**
     * 判断一个过滤物 {@link ItemStack} 是否是流体包裹用来代表“虚拟流体监测
     * 目标”的压缩罐物品（即工厂仪表 / 我们模板仪表通过 JEI 拖拽流体设置出的
     * 那种过滤物，本身不是一份真实存在的物品，只是携带了流体种类与数量的
     * 数据组件）。
     */
    public static boolean isVirtualFluidDisplay(ItemStack stack) {
        return stack.getItem() instanceof CompressedTankItem && CompressedTankItem.isVirtual(stack);
    }

    /**
     * 取出虚拟流体过滤物里携带的真实 {@link FluidStack}。调用前必须已经用
     * {@link #isVirtualFluidDisplay(ItemStack)} 确认过是虚拟流体过滤物。
     */
    public static FluidStack getFluid(ItemStack stack) {
        return CompressedTankItem.getFluid(stack);
    }

    /**
     * 按流体包裹自己工厂仪表使用的同一套格式（mB / B / KB 分段），格式化一个
     * 以 mB 为单位的库存数值，用于模板仪表表面存量数值的展示，保证和流体包裹
     * 自己的工厂仪表显示风格一致。
     */
    public static String formatStorageAmount(int amountInMillibuckets) {
        return FluidAmountHelper.format(amountInMillibuckets);
    }

    /**
     * 由一个真实的 {@link FluidStack} 构造出流体包裹使用的“虚拟流体鬼影过滤
     * 物”，用于 JEI 把流体拖进模板仪表设置界面时生成过滤物。直接复用流体
     * 包裹自己的构造方法，保证生成的过滤物与流体包裹工厂仪表那边完全一致
     * （同一个物品、同一套数据组件标记），后续所有识别、库存汇总、格式化都
     * 能天然互通。
     */
    public static ItemStack createVirtualFluidGhostStack(FluidStack fluid) {
        return VirtualFluidGhostStacks.fromFluid(fluid);
    }

    /**
     * 流体连接/流体产出在模板配方配置界面允许的最大数量（mB），与流体包裹
     * 自己各处流体数量上限保持一致的同一个常量。
     */
    public static int maxFluidAmount() {
        return com.yision.fluidlogistics.util.FluidGaugeHelper.MAX_FLUID_AMOUNT;
    }

    /**
     * 按流体包裹自己的滚轮步进规则（Ctrl=1mB、Shift=100mB、默认1000mB）调整
     * 一个以 mB 为单位的数量，用于模板配方配置界面里流体连接/流体产出的
     * 滚轮调整，保证步进手感与流体包裹一致。
     */
    public static int adjustFluidAmount(int currentAmount, boolean forward, boolean shift, boolean control, int minAmount, int maxAmount) {
        return FluidAmountHelper.adjustFluidRequestAmount(currentAmount, forward, shift, control, minAmount, maxAmount);
    }

    /**
     * 仓管界面里点击/滚轮调整流体模板下单数量的单次步进，直接复用流包自己
     * "仓管直接请求流体库存"用的同一套步进规则（默认1000mB=1B，Ctrl=1万mB，
     * Shift=2万mB），保证手感跟流包一致。
     */
    public static int stockKeeperFluidStep(boolean shift, boolean control) {
        return FluidAmountHelper.getStockKeeperFluidRequestStep(shift, control);
    }

    /**
     * 请求栏内直接调整流体模板数量的单次步进，复用流包"请求栏内调整流体
     * 请求"用的同一套步进规则（反编译确认：Ctrl=1mB，Shift=100mB(0.1B)，
     * 默认=1000mB(1B)）——跟仓库列表点击用的步进（{@link #stockKeeperFluidStep}）
     * 不是同一套，流包自己这两处本来就分别用了不同的步进函数。
     */
    public static int orderBarFluidStep(boolean shift, boolean control) {
        if (control) {
            return 1;
        }
        if (shift) {
            return 100;
        }
        return 1000;
    }

    /**
     * 精确格式化（不像 {@link #formatStorageAmount} 那样压缩取整），用于
     * 请求栏悬浮提示里"实际详细请求量"这一行，跟流包自己给真实流体库存
     * 用的同一个方法。
     */
    public static String formatPreciseAmount(int amountInMillibuckets) {
        return FluidAmountHelper.formatPrecise(amountInMillibuckets);
    }

    /**
     * 判断一个需求列表里的监测过滤物（虚拟压缩罐，{@code virtual=true}，
     * 数量恒为1）和一份真实流体包裹里的压缩罐（{@code virtual=false}，带
     * 真实数量）是不是同一种流体——只比流体种类+数据组件，不比
     * {@code virtual} 标记和数量，两者在这两个字段上必然不同，不能直接用
     * {@code ItemStack.isSameItemSameComponents} 整体比对。
     */
    public static boolean isSameFluidType(ItemStack demandItem, FluidStack realFluid) {
        if (realFluid == null || realFluid.isEmpty() || !isVirtualFluidDisplay(demandItem)) {
            return false;
        }
        FluidStack demandFluid = getFluid(demandItem);
        return FluidStack.isSameFluidSameComponents(demandFluid, realFluid);
    }

    /**
     * 扫描一个包裹（{@code PackageItem}）的9格内容，找第一个真实（非虚拟）
     * 压缩罐并取出它携带的流体；没有就返回空。包裹内容有可能是"固液混包"
     * （理包机合并出来的，物品和压缩罐混在一起），这里只关心压缩罐这一个
     * 槽位，其余物品槽位忽略。
     */
    /** 包裹本身的物品类型是不是流包的"流体包裹"（不管里面扫不扫得到压缩罐）。 */
    public static boolean isFluidPackageItemType(ItemStack box) {
        return box.getItem() instanceof com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageItem;
    }

    /**
     * 扫描一个包裹（{@code PackageItem}）的9格内容，找第一个装着流体的
     * 压缩罐并取出流体。
     * <p>
     * 【重要，之前这里猜错过一次】原来这里额外要求
     * {@code !CompressedTankItem.isVirtual(slotStack)}（只认非虚拟压缩罐），
     * 是我自己凭空加上去的限制——反编译流包自己接收包裹的逻辑
     * （{@code FluidPackagerBlockEntity.collectSinglePackageFluid}）确认过，
     * 它自己压根不检查 virtual 标记，只要 {@code CompressedTankItem.getFluid(...)}
     * 不为空就认，已经按流包自己的真实做法改过来了。
     */
    public static FluidStack findRealFluidInPackage(ItemStack box) {
        if (box == null || box.isEmpty() || !com.simibubi.create.content.logistics.box.PackageItem.isPackage(box)) {
            return FluidStack.EMPTY;
        }
        net.neoforged.neoforge.items.ItemStackHandler contents =
                com.simibubi.create.content.logistics.box.PackageItem.getContents(box);
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack slotStack = contents.getStackInSlot(i);
            if (slotStack.getItem() instanceof CompressedTankItem) {
                FluidStack fluid = CompressedTankItem.getFluid(slotStack);
                if (!fluid.isEmpty()) {
                    return fluid;
                }
            }
        }
        return FluidStack.EMPTY;
    }

    /**
     * 构造一个真实（非虚拟）压缩罐物品，装着这份流体——用于我们自己组装
     * 发出去的流体包裹，跟流包自己 {@code FluidPackagerBlockEntity.
     * createFluidPackage} 构造压缩罐内容那一步用的是同一个公开方法
     * （{@code CompressedTankItem.setFluid}）。
     */
    /**
     * 判断这个包裹是不是"纯流体包裹"——9格内容里只有一个真实压缩罐、没有
     * 混着任何其他物品。理包机合并出来的"固液混包"会被判定为 false。
     */
    public static boolean isPureFluidPackage(ItemStack box) {
        if (box == null || box.isEmpty() || !com.simibubi.create.content.logistics.box.PackageItem.isPackage(box)) {
            return false;
        }
        net.neoforged.neoforge.items.ItemStackHandler contents =
                com.simibubi.create.content.logistics.box.PackageItem.getContents(box);
        boolean foundTank = false;
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack slotStack = contents.getStackInSlot(i);
            if (slotStack.isEmpty()) {
                continue;
            }
            if (slotStack.getItem() instanceof CompressedTankItem) {
                if (foundTank) {
                    return false;
                }
                foundTank = true;
            } else {
                return false;
            }
        }
        return foundTank;
    }

    public static ItemStack createRealFluidTankStack(FluidStack fluid) {
        ItemStack stack = new ItemStack(com.yision.fluidlogistics.registry.AllItems.COMPRESSED_STORAGE_TANK.get());
        CompressedTankItem.setFluid(stack, fluid.copy());
        return stack;
    }

    /**
     * 构造一个外观上就是流包自己"流体包裹"（随机的普通/暴露/风化/氧化四种
     * 外观之一）的包裹物品，9格内容第0格放一个装着这份流体的真实压缩罐——
     * 跟流包自己 {@code FluidPackagerBlockEntity.createFluidPackage} 一模一样
     * 的构造方式（{@code AllItems.createFluidPackage()} + {@code ItemHelper.
     * containerContentsFromHandler} + {@code AllDataComponents.PACKAGE_CONTENTS}），
     * 让我们自己工作仓库发出去的流体包裹外观和流包真正的流体打包机发出来的
     * 完全一样，不再是 Create 通用的纸壳包裹外观。
     */
    public static ItemStack createFluidPackageBox(FluidStack fluid) {
        net.neoforged.neoforge.items.ItemStackHandler contents = new net.neoforged.neoforge.items.ItemStackHandler(9);
        contents.setStackInSlot(0, createRealFluidTankStack(fluid));
        ItemStack fluidPackage = com.yision.fluidlogistics.registry.AllItems.createFluidPackage();
        fluidPackage.set(com.simibubi.create.AllDataComponents.PACKAGE_CONTENTS,
                com.simibubi.create.foundation.item.ItemHelper.containerContentsFromHandler(contents));
        return fluidPackage;
    }

    /** 单个压缩罐能装的流体上限（流包自己的配置项）。 */
    public static int tankCapacity() {
        return CompressedTankItem.getCapacity();
    }

    /**
     * 在仓管界面用流包自己的像素字体格式绘制一个流体数量角标（mB/B/KB），
     * 直接复用流包自己给工厂仪表用的同一个渲染方法，保证格式、字体、颜色
     * 跟流包完全一致。
     */
    public static void renderFluidAmountBadge(net.minecraft.client.gui.GuiGraphics graphics, int amount) {
        com.yision.fluidlogistics.render.FluidSlotAmountRenderer.renderInStockKeeper(graphics, amount);
    }
}