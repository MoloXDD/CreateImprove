package com.molox.createimp.compat.jei;

import com.molox.createimp.screen.BrassScrapBucketScreen;
import com.molox.createimp.screen.NetworkManagerLabelEditMenu;
import com.molox.createimp.screen.NetworkManagerLabelEditScreen;
import com.molox.createimp.screen.NetworkManagerLabelEditorMenu;
import com.molox.createimp.screen.NetworkManagerLabelEditorScreen;
import com.simibubi.create.content.logistics.filter.FilterItem;
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
        registration.addGhostIngredientHandler(
                NetworkManagerLabelEditScreen.class,
                new LabelEditGhostHandler()
        );
        registration.addGhostIngredientHandler(
                BrassScrapBucketScreen.class,
                new BrassScrapBucketGhostHandler()
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
            if (!ingredient.getType().equals(VanillaTypes.ITEM_STACK)) return targets;

            int iconSlotScreenX = screen.getGuiLeft() + NetworkManagerLabelEditorMenu.ICON_SLOT_X;
            int iconSlotScreenY = screen.getGuiTop() + NetworkManagerLabelEditorMenu.ICON_SLOT_Y;

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

    private static class LabelEditGhostHandler
            implements IGhostIngredientHandler<NetworkManagerLabelEditScreen> {

        @Override
        public <I> List<IGhostIngredientHandler.Target<I>> getTargetsTyped(
                NetworkManagerLabelEditScreen screen,
                ITypedIngredient<I> ingredient,
                boolean doStart) {

            List<IGhostIngredientHandler.Target<I>> targets = new ArrayList<>();
            if (!ingredient.getType().equals(VanillaTypes.ITEM_STACK)) return targets;

            int iconSlotScreenX = screen.getGuiLeft() + NetworkManagerLabelEditMenu.ICON_SLOT_X;
            int iconSlotScreenY = screen.getGuiTop() + NetworkManagerLabelEditMenu.ICON_SLOT_Y;

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

    private static class BrassScrapBucketGhostHandler
            implements IGhostIngredientHandler<BrassScrapBucketScreen> {

        @Override
        public <I> List<IGhostIngredientHandler.Target<I>> getTargetsTyped(
                BrassScrapBucketScreen screen,
                ITypedIngredient<I> ingredient,
                boolean doStart) {

            List<IGhostIngredientHandler.Target<I>> targets = new ArrayList<>();
            if (!ingredient.getType().equals(VanillaTypes.ITEM_STACK)) return targets;

            I ingredientValue = ingredient.getIngredient();
            if (ingredientValue instanceof ItemStack stack && stack.getItem() instanceof FilterItem) {
                return targets;
            }

            int iconScreenX = screen.getGuiLeft() + BrassScrapBucketScreen.FILTER_ICON_X;
            int iconScreenY = screen.getGuiTop() + BrassScrapBucketScreen.FILTER_ICON_Y;

            targets.add(new IGhostIngredientHandler.Target<I>() {
                @Override
                public Rect2i getArea() {
                    return new Rect2i(iconScreenX, iconScreenY, 16, 16);
                }

                @Override
                public void accept(I value) {
                    if (value instanceof ItemStack stack && !stack.isEmpty()) {
                        screen.setFilterIcon(stack);
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