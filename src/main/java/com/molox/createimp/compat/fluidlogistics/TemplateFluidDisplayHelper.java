package com.molox.createimp.compat.fluidlogistics;

import com.yision.fluidlogistics.compat.ghost.FluidGhostStacks;
import com.yision.fluidlogistics.content.logistics.fluidPackage.CompressedTankItem;
import com.yision.fluidlogistics.util.FluidAmountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 本类集中封装所有对流体包裹（FluidLogistics）具体类的直接引用（
 * {@code CompressedTankItem}、{@code FluidStack}、{@code FluidAmountHelper}、
 * {@code FluidGhostStacks}）。
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
     * <p>
     * 【1.2.5 变更，反编译确认】流体包裹自 1.2.5 起彻底去掉了“虚拟/真实”
     * 这个区分标记（{@code FluidTankContent} 从 {@code record(FluidStack,
     * boolean virtual)} 改成了 {@code record(FluidStack)}，{@code
     * CompressedTankItem.isVirtual}/{@code setFluidVirtual} 两个方法都已
     * 不存在），压缩罐这个物品层面现在只剩“是不是装着流体”这一个判断维度
     * （对应新增的 {@code CompressedTankItem.isFluidStack}）。本模组自己
     * 从来不会把“包裹里真实压缩罐”这种 {@link ItemStack} 放进模板过滤物/
     * 需求列表条目这个字段——这个字段的取值永远只来自
     * {@link #createVirtualFluidGhostStack} 构造出的过滤物，所以这里直接
     * 判断“是不是装着流体的压缩罐”跟原来的语义完全等价，不会因为流体
     * 包裹去掉了虚拟标记而误认成真实压缩罐。
     */
    public static boolean isVirtualFluidDisplay(ItemStack stack) {
        return CompressedTankItem.isFluidStack(stack);
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
        return FluidGhostStacks.fromFluid(fluid);
    }

    /**
     * 流体连接/流体产出在模板配方配置界面允许的最大数量（mB），与流体包裹
     * 工厂仪表面板设置项的上限保持一致。
     * <p>
     * 【1.2.5 变更，反编译确认】原来直接读取的公开常量 {@code
     * FluidGaugeHelper.MAX_FLUID_AMOUNT} 所在的类已被拆分/移除，且没有留下
     * 任何公开常量替代——这个数值（反编译确认仍是 100000）现在只以
     * {@code private} 常量 {@code MAX_FACTORY_PANEL_AMOUNT} 的形式，藏在
     * 流体包裹自己标记为 {@code @ApiStatus.Internal} 的
     * {@code FluidPackageResourceType} 内部类里。这里改成通过流体包裹
     * 公开在 {@code api.packager} 包下的 {@code PackageResourceType
     * .display().factoryPanelRestockPolicy(...).maxSettingAmount()} 这条
     * 链路间接取值——反编译确认流体这一侧的实现不会用到传入的
     * {@code normalizedKey} 参数内容，只依赖自己的配置项计算，所以传入
     * 任意一份有效流体压缩罐即可。好处是这个值会跟随流体包裹自己的配置
     * 变化自动同步；代价是链路末端的 {@code FluidPackageResourceType}
     * 本身是内部类，流体包裹后续版本随时可能改变这条调用链，每次跟进新
     * 版本时都需要重新反编译确认这一处依然可用。
     */
    public static int maxFluidAmount() {
        ItemStack sampleKey = createRealFluidTankStack(new FluidStack(Fluids.WATER, 1));
        return com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageResourceType.fluid()
                .display()
                .factoryPanelRestockPolicy(sampleKey)
                .maxSettingAmount();
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
     * 判断一个需求列表里的监测过滤物（{@link #createVirtualFluidGhostStack}
     * 构造出来的压缩罐，数量恒为1）和一份真实流体包裹里的压缩罐（带真实
     * 数量）是不是同一种流体——只比流体种类+数据组件，不比数量，两者的
     * 数量按本模组自己的构造约定必然不同，不能直接用
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
     * 只要求 {@code CompressedTankItem.getFluid(...)} 非空即可，不额外区分
     * 任何标记——1.2.5 起流体包裹自己也是同样的判断口径（新增的
     * {@code CompressedTankItem.isFluidStack} 同样只看流体是否非空，压缩罐
     * 已经没有“虚拟/真实”这个区分维度了，见 {@link #isVirtualFluidDisplay}
     * 的说明）。
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
     * 判断一个打包机是不是流体包裹自己的流体打包机（{@code
     * FluidPackagerBlockEntity}）。供出库地址过滤等需要区分"这是流体打包机
     * 还是普通打包机"、从而分别套用各自配置开关的场景使用——调用前必须先
     * 确认 {@link FluidLogisticsCompat#isLoaded()} 为真。
     */
    public static boolean isFluidPackager(com.simibubi.create.content.logistics.packager.PackagerBlockEntity packager) {
        return packager instanceof com.yision.fluidlogistics.content.logistics.fluidPackager.FluidPackagerBlockEntity;
    }

    /**
     * 计算流体打包机目标（它面朝贴合的流体容器）的物理仓库身份标识，用法与
     * {@code InvManipulationBehaviour#getIdentifiedInventory()} 里
     * {@code InventoryIdentifier.get(level, face)} 完全一致，只是流体打包机
     * 用的是 {@code TankManipulationBehaviour} 而不是
     * {@code InvManipulationBehaviour}——两者共同的父类
     * {@code CapManipulationBehaviourBase} 并未提供这个方法，需要在这里单独
     * 按同样的算法算一次。不是流体打包机、目标未连接、所在区块未加载等
     * 任何拿不到有效目标的情况都返回 null。调用前必须先确认
     * {@link FluidLogisticsCompat#isLoaded()} 为真。
     */
    public static com.simibubi.create.api.packager.InventoryIdentifier identifyFluidPackagerTarget(
            com.simibubi.create.content.logistics.packager.PackagerBlockEntity packager) {
        if (!(packager instanceof com.yision.fluidlogistics.content.logistics.fluidPackager.FluidPackagerBlockEntity fluidPackager)) {
            return null;
        }
        com.simibubi.create.foundation.blockEntity.behaviour.inventory.TankManipulationBehaviour fluidTarget =
                fluidPackager.fluidTarget;
        if (fluidTarget == null) {
            return null;
        }
        net.minecraft.world.level.Level level = fluidPackager.getLevel();
        if (level == null) {
            return null;
        }
        net.createmod.catnip.math.BlockFace face;
        try {
            face = fluidTarget.getTarget().getOpposite();
        } catch (Exception e) {
            return null;
        }
        return com.simibubi.create.api.packager.InventoryIdentifier.get(level, face);
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