package com.molox.createimp.compat.jei;

import com.molox.createimp.screen.NetworkManagerLabelEditorMenu;
import com.molox.createimp.screen.NetworkManagerLabelEditorScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class CreateImpJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("createimp", "jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(
                NetworkManagerLabelEditorScreen.class,
                new LabelEditorGhostHandler()
        );
    }

    private static class LabelEditorGhostHandler
            implements IGhostIngredientHandler<NetworkManagerLabelEditorScreen> {

        @Override
        public <I> List<IGhostIngredientHandler.Target<I>> getTargetsTyped(
                NetworkManagerLabelEditorScreen screen,
                ITypedIngredient<I> ingredient,
                boolean doStart) {

            List<IGhostIngredientHandler.Target<I>> targets = new ArrayList<>();

            if (!ingredient.getType().equals(VanillaTypes.ITEM_STACK)) {
                return targets;
            }

            // 图标槽是第 36 个槽（索引 36），在玩家背包 36 个槽之后
            int iconSlotScreenX = screen.getGuiLeft() + NetworkManagerLabelEditorMenu.ICON_SLOT_X;
            int iconSlotScreenY = screen.getGuiTop()  + NetworkManagerLabelEditorMenu.ICON_SLOT_Y;

            targets.add(new IGhostIngredientHandler.Target<I>() {
                @Override
                public Rect2i getArea() {
                    return new Rect2i(iconSlotScreenX, iconSlotScreenY, 16, 16);
                }

                @Override
                public void accept(I value) {
                    if (value instanceof ItemStack stack && !stack.isEmpty()) {
                        ItemStack copy = stack.copy();
                        copy.setCount(1);
                        screen.getMenu().ghostInventory.setStackInSlot(0, copy);
                    }
                }
            });

            return targets;
        }

        @Override
        public void onComplete() {
        }
    }
}