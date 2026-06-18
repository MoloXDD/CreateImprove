package com.molox.createimp.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.molox.createimp.CreateImp;
import com.simibubi.create.content.equipment.clipboard.ClipboardScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClipboardScreen.class)
public class MixinClipboardScreen {

    private static final ResourceLocation CLIPBOARD_FREQUENCY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/clipboard_frequency.png");

    @ModifyVariable(
            method = "renderWindow(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(value = "STORE"),
            index = 13
    )
    private boolean createimp$modifyIsAddress(boolean isAddress,
                                              @Local(index = 12) String string) {
        if (isAddress) return true;
        return string.startsWith("@") && string.length() > 1 && !string.substring(1).isBlank();
    }

    @Redirect(
            method = "renderWindow(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V",
                    ordinal = 1
            )
    )
    private void createimp$redirectIconRender(AllGuiTextures instance,
                                              GuiGraphics graphics, int x, int y,
                                              @Local(index = 12) String string) {
        if (string.startsWith("@")) {
            graphics.blit(CLIPBOARD_FREQUENCY_TEXTURE, x, y, 0, 0, 8, 8, 8, 8);
        } else {
            instance.render(graphics, x, y);
        }
    }

    @Redirect(
            method = "rebuildDisplayCache()Lcom/simibubi/create/content/equipment/clipboard/ClipboardScreen$DisplayCache;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z",
                    ordinal = 0
            )
    )
    private boolean createimp$redirectRebuildStartsWith(String string, String prefix) {
        return string.startsWith(prefix) || (string.startsWith("@") && string.length() > 1 && !string.substring(1).isBlank());
    }
}