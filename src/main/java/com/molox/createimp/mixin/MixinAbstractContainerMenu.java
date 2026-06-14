package com.molox.createimp.mixin;

import com.molox.createimp.CreateImp;
import com.molox.createimp.util.PackageUnpackHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
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
        if (!CreateImp.getConfig().quickUnpack.enabled) {
            return;
        }
        if (clickType != ClickType.PICKUP || button != 1) {
            return;
        }
        if (slotId < 0) {
            return;
        }
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
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
}