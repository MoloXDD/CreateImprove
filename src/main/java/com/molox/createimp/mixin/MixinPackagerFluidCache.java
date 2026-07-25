package com.molox.createimp.mixin;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.work_warehouse.WorkWarehouseBlockEntity;
import com.molox.createimp.compat.fluidlogistics.FluidLogisticsCompat;
import com.molox.createimp.compat.fluidlogistics.TemplateFluidDisplayHelper;
import com.molox.createimp.util.IPackagerFluidCache;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 给打包机加一份不对外暴露的"流体累计缓存"，并在流包无差别拦截含压缩罐
 * 包裹之前（{@code unwrapBox} 方法开头，本类优先级设为 500，低于流包
 * Mixin 的默认 1000，会先执行），抢先判断"这个打包机是不是属于某个正在
 * 等这份流体的工作仓库"：是的话自己接管（扣减该工作仓库需求列表 + 存进
 * 缓存），不满足条件则原样放行，交由流包和原版后续逻辑按原来的方式处理，
 * 不影响任何其它情况。
 * <p>
 * 【重要】判断"这个打包机是不是属于某个工作仓库"时只会用到
 * {@code WorkWarehouseBlockEntity} 和 {@code InvManipulationBehaviour} 这些
 * 双端通用的类；只有真正确认这个包裹含流体、且确实是我们要接管的情况下，
 * 才会调用 {@link TemplateFluidDisplayHelper} 里那些引用流体包裹具体类的
 * 方法，而且调用前已经确认 {@link FluidLogisticsCompat#isLoaded()}——没装
 * 流体包裹时，这些方法自然不会有机会被触发，不会因为缺这个可选依赖而报错。
 * <p>
 * 【关于不用 @Shadow】{@code PackagerBlockEntity} 是一个真实存在、完整编译
 * 进 classpath 的具体类，转型后调用它自己的公开成员就是普通 Java 调用，不
 * 触发 Mixin 的 shadow 校验机制。
 * <p>
 * 【连接储存可能是多方块容器】"连接储存"不一定是单方块容器（机械动力自己
 * 的保险库就是多方块的），也允许被多个工作仓库同时共用。反查归属时用的
 * 是机械动力自己判断"是不是同一份库存"的标准身份识别机制
 * （{@code InvManipulationBehaviour.getIdentifiedInventory()} +
 * {@code PackagerBlockEntity.isTargetingSameInventory(...)}），而不是自己
 * 按坐标扫描；一份连接储存背后如果同时有多个工作仓库在等这份流体，会按
 * 各自需求列表逐个尝试匹配，不会只认定其中一个。
 */
@Mixin(value = PackagerBlockEntity.class, priority = 500, remap = false)
public abstract class MixinPackagerFluidCache implements IPackagerFluidCache {

    @Unique
    private final List<FluidStack> createimp$fluidCache = new ArrayList<>();

    @Inject(method = "unwrapBox", at = @At("HEAD"), cancellable = true)
    private void createimp$acceptFluidPackage(ItemStack box, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        PackagerBlockEntity self = (PackagerBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (!FluidLogisticsCompat.isLoaded() || level == null) {
            return;
        }
        FluidStack fluid = TemplateFluidDisplayHelper.findRealFluidInPackage(box);
        if (fluid.isEmpty()) {
            return;
        }
        // 已知限制：只接管"纯流体包裹"（9格里只有这一个压缩罐，没有混着其他
        // 物品）。理包机合并出来的"固液混包"暂时不处理，原样放行交给流包/
        // 原版后续逻辑按原来的方式判断——这是为了避免把只处理了流体部分、
        // 却把混在同一个包裹里的物品部分静默丢弃这种数据丢失风险，宁可这种
        // 混包场景暂时按原样表现（大概率被判定失败、原样留在打包机里），
        // 也不引入丢东西的可能。
        if (!TemplateFluidDisplayHelper.isPureFluidPackage(box)) {
            return;
        }

        List<WorkWarehouseBlockEntity> candidates = createimp$findOwningWarehouses(self, level);
        if (candidates.isEmpty()) {
            return;
        }
        WorkWarehouseBlockEntity warehouse = null;
        for (WorkWarehouseBlockEntity candidate : candidates) {
            if (candidate.matchesFluidDemand(fluid, fluid.getAmount())) {
                warehouse = candidate;
                break;
            }
        }
        if (warehouse == null) {
            return;
        }

        if (!simulate) {
            warehouse.consumeFluidFromDemandList(fluid, fluid.getAmount());
            createimp$addCachedFluid(fluid);
            // 原版 unwrapBox 成功解包后会设置这几个字段来触发"包裹被吸入"的
            // 动画——我们这里直接 cancel 掉了整个方法、从来没走到原版那一段，
            // 补上同样的字段设置，效果跟原版处理物品包裹完全一致。
            self.previouslyUnwrapped = box;
            self.animationInward = true;
            self.animationTicks = 20;
            self.notifyUpdate();
        }
        cir.setReturnValue(true);
    }

    /**
     * 收集"我可能归属哪些工作仓库"：先看自己面朝的目标方块是不是工作仓库
     * 本身（直接贴合的情形——工作仓库自己是单方块结构，这种情形下坐标
     * 判断是可靠的，也不可能有第二个候选）；不是的话，改用
     * {@code IdentifiedInventory} 身份比对找出所有把这份连接储存当成自己
     * 的候选仓库（正确处理多方块容器，也允许多个仓库共用同一份连接储存）。
     */
    @Unique
    private List<WorkWarehouseBlockEntity> createimp$findOwningWarehouses(PackagerBlockEntity self, Level level) {
        InvManipulationBehaviour targetInventory = self.targetInventory;
        if (targetInventory == null) {
            return List.of();
        }
        BlockPos targetPos;
        try {
            targetPos = targetInventory.getTarget().getConnectedPos();
        } catch (Exception e) {
            return List.of();
        }
        if (targetPos != null && level.getBlockEntity(targetPos) instanceof WorkWarehouseBlockEntity warehouse) {
            return List.of(warehouse);
        }
        IdentifiedInventory packagerIdentified = targetInventory.getIdentifiedInventory();
        if (packagerIdentified == null) {
            return List.of();
        }
        List<WorkWarehouseBlockEntity> found = new ArrayList<>();
        for (WorkWarehouseBlockEntity candidate : WorkWarehouseBlockEntity.getAllActiveAcrossAllNetworks(false)) {
            if (self.isTargetingSameInventory(candidate.getConnectedIdentifiedInventory())) {
                found.add(candidate);
            }
        }
        return found;
    }

    @Override
    public int createimp$getCachedFluidAmount(FluidStack sample) {
        if (sample == null || sample.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (FluidStack tank : createimp$fluidCache) {
            if (FluidStack.isSameFluidSameComponents(tank, sample)) {
                total += tank.getAmount();
            }
        }
        return total;
    }

    @Override
    public FluidStack createimp$extractCachedFluid(FluidStack sample, int amount) {
        if (sample == null || sample.isEmpty() || amount <= 0) {
            return FluidStack.EMPTY;
        }
        int remaining = amount;
        FluidStack result = FluidStack.EMPTY;
        for (int i = 0; i < createimp$fluidCache.size() && remaining > 0; i++) {
            FluidStack tank = createimp$fluidCache.get(i);
            if (tank.isEmpty() || !FluidStack.isSameFluidSameComponents(tank, sample)) {
                continue;
            }
            int taken = Math.min(tank.getAmount(), remaining);
            if (result.isEmpty()) {
                result = tank.copy();
                result.setAmount(0);
            }
            result.grow(taken);
            remaining -= taken;
            int left = tank.getAmount() - taken;
            if (left <= 0) {
                createimp$fluidCache.remove(i);
                i--;
            } else {
                FluidStack newTank = tank.copy();
                newTank.setAmount(left);
                createimp$fluidCache.set(i, newTank);
            }
        }
        if (!result.isEmpty()) {
            ((PackagerBlockEntity) (Object) this).setChanged();
        }
        return result;
    }

    @Override
    public int createimp$addCachedFluid(FluidStack toAdd) {
        if (toAdd == null || toAdd.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < createimp$fluidCache.size(); i++) {
            FluidStack tank = createimp$fluidCache.get(i);
            if (FluidStack.isSameFluidSameComponents(tank, toAdd)) {
                FluidStack merged = tank.copy();
                merged.grow(toAdd.getAmount());
                createimp$fluidCache.set(i, merged);
                ((PackagerBlockEntity) (Object) this).setChanged();
                return 0;
            }
        }
        createimp$fluidCache.add(toAdd.copy());
        ((PackagerBlockEntity) (Object) this).setChanged();
        return 0;
    }

    @Override
    public List<FluidStack> createimp$nonEmptyCachedFluids() {
        List<FluidStack> result = new ArrayList<>();
        for (FluidStack tank : createimp$fluidCache) {
            if (!tank.isEmpty()) {
                result.add(tank.copy());
            }
        }
        return result;
    }

    @Override
    public boolean createimp$isCachedFluidEmpty() {
        return createimp$fluidCache.isEmpty();
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void createimp$writeFluidCache(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        ListTag list = new ListTag();
        for (FluidStack tank : createimp$fluidCache) {
            if (tank.isEmpty()) {
                continue;
            }
            FluidStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), tank)
                    .resultOrPartial(error -> CreateImp.LOGGER.error("打包机流体缓存写入存档失败：{}", error))
                    .ifPresent(list::add);
        }
        compound.put("CreateimpFluidCache", list);
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void createimp$readFluidCache(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        createimp$fluidCache.clear();
        if (!compound.contains("CreateimpFluidCache", Tag.TAG_LIST)) {
            return;
        }
        ListTag list = compound.getList("CreateimpFluidCache", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            FluidStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), list.get(i))
                    .resultOrPartial(error -> CreateImp.LOGGER.error("打包机流体缓存读取存档失败：{}", error))
                    .ifPresent(createimp$fluidCache::add);
        }
    }
}