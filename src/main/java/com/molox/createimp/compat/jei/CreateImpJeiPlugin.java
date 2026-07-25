package com.molox.createimp.compat.jei;

import com.molox.createimp.screen.BrassScrapBucketScreen;
import com.molox.createimp.screen.NetworkManagerLabelEditMenu;
import com.molox.createimp.screen.NetworkManagerLabelEditScreen;
import com.molox.createimp.screen.NetworkManagerLabelEditorMenu;
import com.molox.createimp.screen.NetworkManagerLabelEditorScreen;
import com.molox.createimp.network.SubmitBrassScrapBucketFilterPacket;
import com.molox.createimp.compat.fluidlogistics.FluidLogisticsCompat;
import com.molox.createimp.compat.fluidlogistics.TemplateFluidDisplayHelper;
import com.molox.createimp.screen.TemplatePanelSetItemMenu;
import com.molox.createimp.screen.TemplatePanelSetItemScreen;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

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
        registration.addGhostIngredientHandler(
                TemplatePanelSetItemScreen.class,
                new TemplatePanelSetItemGhostHandler()
        );
        // 流体监测：只在实际装了流体包裹时才注册这个处理器，未安装时 JEI 里
        // 拖流体到模板仪表设置界面不会有任何反应，不影响上面物品鬼影处理器
        // 的正常工作——两者是分别注册给同一个界面类的两个独立处理器，JEI 会
        // 按拖拽物的类型（物品/流体）分别派发给对应的那一个。
        if (FluidLogisticsCompat.isLoaded()) {
            registration.addGhostIngredientHandler(
                    TemplatePanelSetItemScreen.class,
                    new TemplatePanelSetItemFluidGhostHandler()
            );
        }
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
                        ItemStack copy = stack.copy();
                        copy.setCount(1);
                        PacketDistributor.sendToServer(
                                new SubmitBrassScrapBucketFilterPacket(screen.getMenu().pos, copy));
                    }
                }
            });

            return targets;
        }

        @Override
        public void onComplete() {
        }
    }

    private static class TemplatePanelSetItemGhostHandler
            implements IGhostIngredientHandler<TemplatePanelSetItemScreen> {

        @Override
        public <I> List<IGhostIngredientHandler.Target<I>> getTargetsTyped(
                TemplatePanelSetItemScreen screen,
                ITypedIngredient<I> ingredient,
                boolean doStart) {

            List<IGhostIngredientHandler.Target<I>> targets = new ArrayList<>();
            if (!ingredient.getType().equals(VanillaTypes.ITEM_STACK)) return targets;

            int slotScreenX = screen.getGuiLeft() + TemplatePanelSetItemMenu.ITEM_SLOT_X;
            int slotScreenY = screen.getGuiTop() + TemplatePanelSetItemMenu.ITEM_SLOT_Y;

            targets.add(new IGhostIngredientHandler.Target<I>() {
                @Override
                public Rect2i getArea() {
                    return new Rect2i(slotScreenX, slotScreenY, 16, 16);
                }

                @Override
                public void accept(I value) {
                    if (value instanceof ItemStack stack && !stack.isEmpty()) {
                        ItemStack copy = stack.copy();
                        copy.setCount(1);
                        screen.getMenu().ghostInventory.setStackInSlot(0, copy);
                        PacketDistributor.sendToServer(new GhostItemSubmitPacket(copy, 0));
                    }
                }
            });

            return targets;
        }

        @Override
        public void onComplete() {
        }
    }

    /**
     * 只处理把流体拖进模板仪表设置界面这一种情况：命中区域与现有物品鬼影
     * 处理器完全一样（同一个槽位），落地时不是把流体本身的物品形态放进去，
     * 而是转换成流体包裹自己那套“虚拟流体过滤物”（本质仍是一个
     * {@code ItemStack}），直接复用流体包裹给工厂仪表用的同一个转换方法，
     * 保证生成的过滤物与流体包裹工厂仪表那边完全一致。
     */
    private static class TemplatePanelSetItemFluidGhostHandler
            implements IGhostIngredientHandler<TemplatePanelSetItemScreen> {

        @Override
        public <I> List<IGhostIngredientHandler.Target<I>> getTargetsTyped(
                TemplatePanelSetItemScreen screen,
                ITypedIngredient<I> ingredient,
                boolean doStart) {

            List<IGhostIngredientHandler.Target<I>> targets = new ArrayList<>();
            if (ingredient.getType() != NeoForgeTypes.FLUID_STACK) return targets;

            int slotScreenX = screen.getGuiLeft() + TemplatePanelSetItemMenu.ITEM_SLOT_X;
            int slotScreenY = screen.getGuiTop() + TemplatePanelSetItemMenu.ITEM_SLOT_Y;

            targets.add(new IGhostIngredientHandler.Target<I>() {
                @Override
                public Rect2i getArea() {
                    return new Rect2i(slotScreenX, slotScreenY, 16, 16);
                }

                @Override
                public void accept(I value) {
                    if (!(value instanceof FluidStack fluidStack) || fluidStack.isEmpty()) return;
                    ItemStack ghostStack = TemplateFluidDisplayHelper.createVirtualFluidGhostStack(fluidStack);
                    screen.getMenu().ghostInventory.setStackInSlot(0, ghostStack);
                    PacketDistributor.sendToServer(new GhostItemSubmitPacket(ghostStack, 0));
                }
            });

            return targets;
        }

        @Override
        public void onComplete() {
        }
    }
}