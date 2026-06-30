package com.molox.createimp.block.batch_repackager;

import com.molox.createimp.registry.ModBlockEntityTypes;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.compat.computercraft.events.ComputerEvent;
import com.simibubi.create.compat.computercraft.events.PackageEvent;
import com.simibubi.create.compat.computercraft.events.RepackageEvent;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.crate.BottomlessItemHandler;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerItemHandler;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BatchRepackagerBlockEntity extends PackagerBlockEntity {

    public BatchPackageRepackageHelper repackageHelper = new BatchPackageRepackageHelper();
    protected Map<Integer, List<ItemStack>> collectedFragments = new HashMap<>();

    public BatchRepackagerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModBlockEntityTypes.BATCH_REPACKAGER.get(),
                (be, context) -> be.inventory);
    }

    @Override
    public boolean unwrapBox(ItemStack box, boolean simulate) {
        if (this.animationTicks > 0) {
            return false;
        }
        IItemHandler targetInv = this.targetInventory.getInventory();
        if (targetInv == null || targetInv instanceof PackagerItemHandler) {
            return false;
        }
        boolean targetIsCreativeCrate = targetInv instanceof BottomlessItemHandler;
        boolean anySpace = false;
        for (int slot = 0; slot < targetInv.getSlots(); ++slot) {
            ItemStack remainder = targetInv.insertItem(slot, box, simulate);
            if (!remainder.isEmpty()) continue;
            anySpace = true;
            break;
        }
        if (!targetIsCreativeCrate && !anySpace) {
            return false;
        }
        if (simulate) {
            return true;
        }
        this.computerBehaviour.prepareComputerEvent((ComputerEvent) new PackageEvent(box, "package_received"));
        this.previouslyUnwrapped = box;
        this.animationInward = true;
        this.animationTicks = 20;
        this.notifyUpdate();
        return true;
    }

    @Override
    public void recheckIfLinksPresent() {
    }

    @Override
    public boolean redstoneModeActive() {
        return true;
    }

    @Override
    public void attemptToSend(List<PackagingRequest> queuedRequests) {
        if (!this.heldBox.isEmpty() || this.animationTicks != 0 || this.buttonCooldown > 0) {
            return;
        }
        if (!this.queuedExitingPackages.isEmpty()) {
            return;
        }
        IItemHandler targetInv = this.targetInventory.getInventory();
        if (targetInv == null || targetInv instanceof PackagerItemHandler) {
            return;
        }
        this.attemptToRepackage(targetInv);
        if (this.heldBox.isEmpty()) {
            return;
        }
        this.updateSignAddress();
        if (!this.signBasedAddress.isBlank()) {
            PackageItem.addAddress(this.heldBox, this.signBasedAddress);
        }
    }

    protected boolean isFragmented(ItemStack box) {
        return box.has(AllDataComponents.PACKAGE_ORDER_DATA);
    }

    protected int addPackageFragment(ItemStack box) {
        int collectedOrderId = PackageItem.getOrderId(box);
        if (collectedOrderId == -1) {
            return -1;
        }
        List<ItemStack> collectedOrder = this.collectedFragments.computeIfAbsent(
                collectedOrderId, k -> new ArrayList<>());
        collectedOrder.add(box);
        if (!this.isOrderComplete(collectedOrderId)) {
            return -1;
        }
        return collectedOrderId;
    }

    private boolean isOrderComplete(int orderId) {
        boolean finalLinkReached = false;
        int linkCounter = 0;
        while (linkCounter < 1000 && !finalLinkReached) {
            int packageCounter = 0;
            while (packageCounter < 1000) {
                ItemStack matched = null;
                for (ItemStack box : this.collectedFragments.get(orderId)) {
                    PackageItem.PackageOrderData data = box.get(AllDataComponents.PACKAGE_ORDER_DATA);
                    if (linkCounter != data.linkIndex() || packageCounter != data.fragmentIndex()) continue;
                    matched = box;
                    break;
                }
                if (matched == null) {
                    return false;
                }
                PackageItem.PackageOrderData matchedData = matched.get(AllDataComponents.PACKAGE_ORDER_DATA);
                finalLinkReached = matchedData.isFinalLink();
                if (matchedData.isFinal()) {
                    break;
                }
                packageCounter++;
            }
            linkCounter++;
        }
        return true;
    }

    protected void attemptToRepackage(IItemHandler targetInv) {
        this.collectedFragments.clear();
        int completedOrderId = -1;
        for (int slot = 0; slot < targetInv.getSlots(); ++slot) {
            ItemStack extracted = targetInv.extractItem(slot, 1, true);
            if (extracted.isEmpty() || !PackageItem.isPackage(extracted)) continue;
            if (!this.isFragmented(extracted)) {
                targetInv.extractItem(slot, 1, false);
                this.heldBox = extracted.copy();
                this.animationInward = false;
                this.animationTicks = 20;
                this.notifyUpdate();
                return;
            }
            completedOrderId = this.addPackageFragment(extracted);
            if (completedOrderId != -1) break;
        }
        if (completedOrderId == -1) {
            return;
        }

        String address = "";
        PackageOrderWithCrafts orderContext = null;
        InventorySummary summary = new InventorySummary();
        for (ItemStack box : this.collectedFragments.get(completedOrderId)) {
            address = PackageItem.getAddress(box);
            if (box.has(AllDataComponents.PACKAGE_ORDER_DATA)) {
                PackageOrderWithCrafts context = box.get(AllDataComponents.PACKAGE_ORDER_DATA).orderContext();
                if (context != null && !context.isEmpty()) {
                    orderContext = context;
                }
            }
            ItemStackHandler contents = PackageItem.getContents(box);
            for (int slot = 0; slot < contents.getSlots(); ++slot) {
                summary.add(contents.getStackInSlot(slot));
            }
        }

        List<BigItemStack> boxesToExport;
        if (orderContext != null && !orderContext.orderedCrafts().isEmpty()) {
            boxesToExport = this.repackageHelper.repackBasedOnRecipes(
                    summary, orderContext, address, completedOrderId, this.level.getRandom());
        } else {
            // 没有配方信息的普通分片订单（不带合成表的包裹）：不走配方重打逻辑，
            // 直接把汇总到的所有材料按原版方式紧凑打包成新包裹输出，避免材料凭空消失。
            boxesToExport = this.repackageHelper.repackPlainMaterials(summary, address, completedOrderId);
        }

        for (int slot = 0; slot < targetInv.getSlots(); ++slot) {
            ItemStack extracted = targetInv.extractItem(slot, 1, true);
            if (extracted.isEmpty() || !PackageItem.isPackage(extracted)
                    || PackageItem.getOrderId(extracted) != completedOrderId) continue;
            targetInv.extractItem(slot, 1, false);
        }
        if (boxesToExport.isEmpty()) {
            return;
        }
        if (this.computerBehaviour.hasAttachedComputer()) {
            for (BigItemStack box : boxesToExport) {
                this.computerBehaviour.prepareComputerEvent((ComputerEvent) new RepackageEvent(box.stack, box.count));
            }
        }
        this.queuedExitingPackages.addAll(boxesToExport);
        this.notifyUpdate();
    }
}