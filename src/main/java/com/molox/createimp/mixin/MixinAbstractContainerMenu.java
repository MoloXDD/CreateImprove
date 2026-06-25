package com.molox.createimp.mixin;

import com.molox.createimp.CreateImp;
import com.molox.createimp.item.NetworkManagerItem;
import com.molox.createimp.item.NetworkSelectedState;
import com.molox.createimp.registry.ModDataComponents;
import com.molox.createimp.util.PackageUnpackHelper;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public class MixinAbstractContainerMenu {

    @Inject(
            method = "clicked",
            at = @At("HEAD"),
            cancellable = true
    )
    private void createimp$onClicked(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;

        if (clickType == ClickType.PICKUP && button == 1 && slotId >= 0 && slotId < menu.slots.size()) {
            ItemStack carried = menu.getCarried();
            if (carried.getItem() instanceof NetworkManagerItem
                    && carried.has(ModDataComponents.NETWORK_SELECTED_STATE.get())) {
                Slot targetSlot = menu.slots.get(slotId);
                ItemStack target = targetSlot.getItem();
                if (!target.isEmpty() && target.getItem() instanceof LogisticallyLinkedBlockItem) {
                    NetworkSelectedState state = carried.get(ModDataComponents.NETWORK_SELECTED_STATE.get());
                    if (player.level().isClientSide()) {
                        if (player.isCreative()) {
                            // resolveInventoryMenuSlot 返回的是 inventoryMenu 的槽位序号（1-45）
                            int inventoryMenuSlot = resolveInventoryMenuSlot(targetSlot, player.getInventory());
                            if (inventoryMenuSlot >= 0) {
                                LogisticallyLinkedBlockItem.assignFrequency(target, player, state.networkId());
                                Minecraft.getInstance().gameMode.handleCreativeModeItemAdd(target, inventoryMenuSlot);
                            }
                        }
                    } else {
                        LogisticallyLinkedBlockItem.assignFrequency(target, player, state.networkId());
                        targetSlot.setChanged();
                    }
                    ci.cancel();
                    return;
                }
            }
        }

        if (!CreateImp.getConfig().quickUnpack.enabled) {
            return;
        }
        if (clickType != ClickType.PICKUP || button != 1) {
            return;
        }
        if (slotId < 0) {
            return;
        }
        if (slotId >= menu.slots.size()) {
            return;
        }
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        boolean handled = PackageUnpackHelper.tryUnpack(menu, slotId, player);
        if (handled) {
            ci.cancel();
        }
    }

    /**
     * 将槽位转换为 inventoryMenu 的槽位序号（服务端 handleSetCreativeModeSlot 要求 1-45）。
     * inventoryMenu 槽位布局：
     *   0        = 合成结果（只读）
     *   1-4      = 合成格
     *   5-8      = 护甲
     *   9-35     = 主物品栏（对应背包 slot 9-35）
     *   36-44    = 快捷栏（对应背包 slot 0-8）
     *   45       = 副手
     */
    private static int resolveInventoryMenuSlot(Slot slot, Inventory playerInv) {
        if (slot.container != playerInv) {
            return -1;
        }
        int bagSlot = slot.getContainerSlot();
        if (bagSlot >= 9 && bagSlot <= 35) {
            // 主物品栏：inventoryMenu slot == bagSlot
            return bagSlot;
        } else if (bagSlot >= 0 && bagSlot <= 8) {
            // 快捷栏：inventoryMenu slot = bagSlot + 36
            return bagSlot + 36;
        }
        return -1;
    }
}