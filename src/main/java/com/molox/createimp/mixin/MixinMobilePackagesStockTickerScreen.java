package com.molox.createimp.mixin;

import com.molox.createimp.CreateImp;
import com.molox.createimp.CreateImpConfig;
import com.molox.createimp.client.ClientWorkWarehouseAvailabilityCache;
import com.molox.createimp.client.TemplateOrderTooltipHandler;
import com.molox.createimp.compat.fluidlogistics.FluidLogisticsCompat;
import com.molox.createimp.compat.fluidlogistics.TemplateFluidDisplayHelper;
import com.molox.createimp.item.TemplateOrderTarget;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.molox.createimp.network.RequestTemplateMaterialsPacket;
import com.molox.createimp.network.RequestWorkWarehouseAvailabilityPacket;
import com.molox.createimp.util.StockKeeperRequestScreenInvoker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.utility.CreateLang;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.ClientScreenStorage;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.LogisticallyLinkedItem;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTicker;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTickerScreen;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zznty.create_factory_abstractions.api.generic.search.GenericSearch;
import ru.zznty.create_factory_abstractions.api.generic.stack.GenericStack;
import ru.zznty.create_factory_abstractions.generic.key.item.ItemKey;
import ru.zznty.create_factory_abstractions.generic.support.BigGenericStack;
import ru.zznty.create_factory_abstractions.generic.support.CraftableGenericStack;
import ru.zznty.create_factory_abstractions.generic.support.GenericInventorySummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Mobile Packages 0.7.8 / Factory Abstractions 1.6.0 screen adapter.
 * All CMP/CFA references deliberately live in this optional, plugin-gated mixin.
 * A token is always an ItemKey, including fluid tokens; actual inventory entries keep
 * their original GenericKey. Never round-trip a real fluid through GenericStack.wrap.
 */
@Mixin(targets = "de.theidler.create_mobile_packages.items.portable_stock_ticker.PortableStockTickerScreen", remap = false)
public abstract class MixinMobilePackagesStockTickerScreen implements StockKeeperRequestScreenInvoker {
    @Shadow public List<List<BigGenericStack>> displayedItems;
    @Shadow public List<GenericSearch.CategoryEntry> categories;
    @Shadow public List<BigGenericStack> itemsToOrder;
    @Shadow public List<CraftableGenericStack> recipesToOrder;
    @Shadow @Final private Set<Integer> hiddenCategories;
    @Shadow Inventory playerInventory;
    @Shadow private native Couple<Integer> getHoveredSlot(int x, int y);
    @Shadow private native boolean isConfirmHovered(int x, int y);
    @Shadow private native void sendIt();

    @Unique private int createimp$pollCooldown;
    @Unique private boolean createimp$suppressNextInitRefresh;
    @Unique private static final int CREATEIMP_TEMPLATE_CATEGORY = -2;
    @Unique private static final float CREATEIMP_HOVER_SCALE = 1.075f;
    @Unique private static final float CREATEIMP_FLUID_HOVER_GROWTH = 3.0f;
    @Unique private static final ResourceLocation CREATEIMP_TEMPLATE_BG = ResourceLocation.fromNamespaceAndPath(
            CreateImp.MODID, "textures/gui/stock_keeper_template_slot_bg.png");
    @Unique private static final ResourceLocation CREATEIMP_ORDER_BG = ResourceLocation.fromNamespaceAndPath(
            CreateImp.MODID, "textures/gui/stock_keeper_template_request_slot_bg.png");
    @Unique private static final ResourceLocation CREATEIMP_TEMPLATE_FG = ResourceLocation.fromNamespaceAndPath(
            CreateImp.MODID, "textures/gui/stock_keeper_template_slot_bg2.png");
    @Unique private static final ResourceLocation CREATEIMP_ORDER_FG = ResourceLocation.fromNamespaceAndPath(
            CreateImp.MODID, "textures/gui/stock_keeper_template_request_slot_bg2.png");

    @Unique private static boolean createimp$merge() {
        return CreateImp.getConfig().templateFunctionConfig.mergeTemplateWithStock;
    }

    @Unique private static boolean createimp$style2() {
        return CreateImp.getConfig().templateFunctionConfig.stockKeeperTemplateDisplayStyle
                == CreateImpConfig.TemplateFunctionConfig.TemplateDisplayStyle.STYLE_2;
    }

    /** Inspect only ItemKeys; converting a genuine FluidKey just to test for a token is unnecessary. */
    @Unique private static ItemStack createimp$token(BigGenericStack entry) {
        if (entry.get().key() instanceof ItemKey key && TemplateOrderTokenHelper.isToken(key.stack())) {
            return key.stack();
        }
        return ItemStack.EMPTY;
    }

    @Unique private UUID createimp$network() {
        ItemStack ticker = PortableStockTicker.find(this.playerInventory);
        return ticker == null ? null : LogisticallyLinkedItem.networkFromStack(ticker);
    }

    @Unique private int createimp$templateCount() {
        int count = 0;
        for (BigGenericStack entry : this.itemsToOrder) {
            if (!createimp$token(entry).isEmpty() && entry.get().amount() > 0) count++;
        }
        return count;
    }

    @Unique private boolean createimp$blocked() {
        int required = createimp$templateCount();
        if (required == 0) return false;
        UUID network = createimp$network();
        return network == null || ClientWorkWarehouseAvailabilityCache.get(network) < required;
    }

    @Override public void createimp$pollWorkWarehouseAvailability() {
        if (createimp$templateCount() == 0) return;
        UUID network = createimp$network();
        if (network == null || this.createimp$pollCooldown-- > 0) return;
        this.createimp$pollCooldown = 15;
        PacketDistributor.sendToServer(new RequestWorkWarehouseAvailabilityPacket(network));
    }

    @Override public void createimp$invokeSendIt() {
        this.sendIt();
        this.createimp$suppressNextInitRefresh = true;
    }

    @Override public void createimp$clearRequestBar() {
        this.itemsToOrder = new ArrayList<>();
        this.recipesToOrder = new ArrayList<>();
    }

    @Redirect(method = "init", at = @At(value = "INVOKE", target =
            "Lde/theidler/create_mobile_packages/items/portable_stock_ticker/ClientScreenStorage;manualUpdate()V"))
    private void createimp$skipDuplicateRefresh() {
        if (this.createimp$suppressNextInitRefresh) {
            this.createimp$suppressNextInitRefresh = false;
        } else {
            ClientScreenStorage.manualUpdate();
        }
    }

    /** Cover BOTH the confirm button and Shift+Enter, without intercepting the material dialog callback. */
    @Redirect(method = {"mouseClicked", "keyPressed"}, at = @At(value = "INVOKE", target =
            "Lde/theidler/create_mobile_packages/items/portable_stock_ticker/PortableStockTickerScreen;sendIt()V"))
    private void createimp$checkMaterials(PortableStockTickerScreen self) {
        if (createimp$templateCount() == 0) {
            this.sendIt();
            return;
        }
        if (createimp$blocked()) return;
        UUID network = createimp$network();
        if (network == null) return;
        List<BigItemStack> request = new ArrayList<>();
        for (BigGenericStack entry : this.itemsToOrder) {
            BigItemStack stack = entry.asStack();
            if (entry.get().amount() > 0) {
                request.add(new BigItemStack(stack.stack.copy(), entry.get().amount()));
            }
        }
        if (!request.isEmpty()) PacketDistributor.sendToServer(new RequestTemplateMaterialsPacket(network, request));
    }

    @Redirect(method = "renderBg", at = @At(value = "INVOKE", target =
            "Lde/theidler/create_mobile_packages/items/portable_stock_ticker/PortableStockTickerScreen;isConfirmHovered(II)Z"))
    private boolean createimp$confirmHighlight(PortableStockTickerScreen self, int x, int y) {
        return this.isConfirmHovered(x, y) && !createimp$blocked();
    }

    @Inject(method = "renderForeground", at = @At("TAIL"))
    private void createimp$warehouseTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!this.isConfirmHovered(mouseX, mouseY) || createimp$templateCount() == 0) return;
        UUID network = createimp$network();
        int available = network == null ? 0 : ClientWorkWarehouseAvailabilityCache.get(network);
        List<Component> lines = new ArrayList<>();
        if (available < createimp$templateCount()) {
            lines.add(Component.translatable("createimp.gui.stock_keeper.not_enough_work_warehouse"));
        }
        lines.add(Component.translatable("createimp.gui.stock_keeper.work_warehouse_available", Math.max(0, available)));
        graphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
    }

    @Inject(method = "refreshSearchResults", at = @At("RETURN"))
    private void createimp$templateCategories(boolean scrollBackUp, CallbackInfo ci) {
        List<ItemStack> displays = new ArrayList<>();
        List<BigGenericStack> allTokens = new ArrayList<>();
        for (List<BigGenericStack> bucket : this.displayedItems) {
            for (BigGenericStack entry : bucket) {
                ItemStack token = createimp$token(entry);
                if (token.isEmpty()) continue;
                allTokens.add(entry);
                TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(token);
                if (target != null) displays.add(target.display());
            }
        }
        TemplateOrderTooltipHandler.updateCurrentTemplateDisplays(displays);
        if (allTokens.isEmpty()) return;
        List<List<BigGenericStack>> buckets = new ArrayList<>();
        for (List<BigGenericStack> bucket : this.displayedItems) {
            List<BigGenericStack> result = new ArrayList<>();
            if (createimp$merge()) {
                for (BigGenericStack entry : bucket) if (!createimp$token(entry).isEmpty()) result.add(entry);
            }
            for (BigGenericStack entry : bucket) {
                if (!createimp$token(entry).isEmpty()) continue;
                boolean replaced = false;
                if (createimp$merge()) {
                    for (BigGenericStack token : allTokens) {
                        TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(createimp$token(token));
                        if (target != null && createimp$matchesDisplay(entry.get(), target.display())) {
                            // Only hide stock if its matching template is visible in THIS category.
                            replaced = bucket.contains(token);
                            if (replaced) break;
                        }
                    }
                }
                if (!replaced) result.add(entry);
            }
            buckets.add(result);
        }
        if (!createimp$merge()) {
            List<GenericSearch.CategoryEntry> newCategories = new ArrayList<>();
            newCategories.add(createimp$category(CREATEIMP_TEMPLATE_CATEGORY,
                    Component.translatable("createimp.gui.stock_keeper.template_category").getString()));
            if (this.categories.isEmpty()) {
                List<BigGenericStack> unsorted = new ArrayList<>();
                for (List<BigGenericStack> bucket : buckets) unsorted.addAll(bucket);
                buckets = new ArrayList<>();
                if (!unsorted.isEmpty()) {
                    newCategories.add(createimp$category(-1, CreateLang.translate("gui.stock_keeper.unsorted_category").string()));
                    buckets.add(unsorted);
                }
            } else {
                newCategories.addAll(this.categories);
            }
            buckets.add(0, allTokens);
            this.categories = newCategories;
        }
        this.displayedItems = buckets;
        int y = 0;
        for (int i = 0; i < this.categories.size(); i++) {
            GenericSearch.CategoryEntry category = this.categories.get(i);
            category.y().setValue(y);
            if (buckets.get(i).isEmpty()) continue;
            y += 20;
            if (category.hidden().isFalse()) y += ((buckets.get(i).size() + 8) / 9) * 20;
        }
    }

    @Unique private GenericSearch.CategoryEntry createimp$category(int id, String name) {
        return new GenericSearch.CategoryEntry(id, name, new MutableInt(), new MutableBoolean(this.hiddenCategories.contains(id)));
    }

    @Unique private static boolean createimp$matchesDisplay(GenericStack stack, ItemStack display) {
        // CFA's own conversion is used ONLY for comparison, never to replace the original key.
        ItemStack candidate = BigGenericStack.of(stack.withAmount(1)).asStack().stack;
        if (FluidLogisticsCompat.isLoaded() && TemplateFluidDisplayHelper.isVirtualFluidDisplay(display)
                && TemplateFluidDisplayHelper.isVirtualFluidDisplay(candidate)) {
            return TemplateFluidDisplayHelper.isSameFluidType(display, TemplateFluidDisplayHelper.getFluid(candidate));
        }
        return ItemStack.isSameItemSameComponents(candidate, display);
    }

    /** Resolve the real stock with its original GenericKey, even if the merged UI hides its row. */
    @Unique private GenericStack createimp$realStock(ItemStack display) {
        if (ClientScreenStorage.stacks != null) {
            for (GenericStack stack : ClientScreenStorage.stacks) {
                if (stack.key() instanceof ItemKey key && TemplateOrderTokenHelper.isToken(key.stack())) continue;
                if (createimp$matchesDisplay(stack, display)) return stack;
            }
        }
        return GenericStack.EMPTY;
    }

    @Unique private BigGenericStack createimp$order(GenericStack stack) {
        for (BigGenericStack order : this.itemsToOrder) if (order.get().canStack(stack)) return order;
        return null;
    }

    @Unique private int createimp$remaining(GenericStack stock) {
        BigGenericStack order = createimp$order(stock);
        return Math.max(0, stock.amount() - (order == null ? 0 : order.get().amount()));
    }

    @Unique private void createimp$adjust(GenericStack stack, int delta) {
        if (delta == 0 || stack.key() == GenericStack.EMPTY.key()) return;
        BigGenericStack order = createimp$order(stack);
        if (order == null) {
            if (delta < 0 || this.itemsToOrder.size() >= 9) return;
            order = BigGenericStack.of(stack.withAmount(0));
            this.itemsToOrder.add(order);
        }
        long amount = (long)order.get().amount() + delta;
        if (amount <= 0) this.itemsToOrder.remove(order);
        else order.setAmount((int)Math.min(Integer.MAX_VALUE, amount));
    }

    @Unique private BigGenericStack createimp$hovered(int x, int y) {
        Couple<Integer> slot = this.getHoveredSlot(x, y);
        int category = slot.getFirst();
        int index = slot.getSecond();
        if (index < 0 || category == -2) return null;
        List<BigGenericStack> bucket;
        if (category == -1) bucket = this.itemsToOrder;
        else if (category >= 0 && category < this.displayedItems.size()) bucket = this.displayedItems.get(category);
        else return null;
        return index < bucket.size() ? bucket.get(index) : null;
    }

    @Unique private static boolean createimp$fluid(ItemStack token) {
        TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(token);
        return target != null && FluidLogisticsCompat.isLoaded()
                && TemplateFluidDisplayHelper.isVirtualFluidDisplay(target.display());
    }

    @Unique private static int createimp$step(ItemStack token, boolean orderBar) {
        if (createimp$fluid(token)) return orderBar
                ? TemplateFluidDisplayHelper.orderBarFluidStep(Screen.hasShiftDown(), Screen.hasControlDown())
                : TemplateFluidDisplayHelper.stockKeeperFluidStep(Screen.hasShiftDown(), Screen.hasControlDown());
        return Screen.hasShiftDown() ? token.getMaxStackSize() : Screen.hasControlDown() ? 10 : 1;
    }

    /** All template interactions precede CFLBridge; non-template resources are left entirely native. */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void createimp$templateClick(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button < 0 || button > 2) return;
        BigGenericStack entry = createimp$hovered((int)x, (int)y);
        if (entry == null || createimp$token(entry).isEmpty()) return;
        boolean orderBar = this.getHoveredSlot((int)x, (int)y).getFirst() == -1;
        if (button == 2 && (orderBar || !createimp$merge())) return;
        cir.setReturnValue(true);
        createimp$transfer(entry, orderBar, button == 1 || orderBar, button == 2);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void createimp$templateScroll(double x, double y, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        BigGenericStack entry = createimp$hovered((int)x, (int)y);
        if (entry == null || createimp$token(entry).isEmpty() || scrollY == 0) return;
        cir.setReturnValue(true);
        createimp$transfer(entry, this.getHoveredSlot((int)x, (int)y).getFirst() == -1, scrollY < 0, false);
    }

    @Unique private void createimp$transfer(BigGenericStack entry, boolean orderBar, boolean remove, boolean forceTemplate) {
        ItemStack token = createimp$token(entry);
        GenericStack tokenStack = GenericStack.wrap(token); // explicit ItemKey: retain ALL token components
        int step = createimp$step(token, orderBar);
        if (!orderBar && createimp$merge() && !forceTemplate) {
            TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(token);
            if (target == null) return;
            GenericStack stock = createimp$realStock(target.display());
            if (remove) {
                BigGenericStack templateOrder = createimp$order(tokenStack);
                createimp$adjust(templateOrder != null ? tokenStack : stock, -step);
            } else {
                int remaining = createimp$remaining(stock);
                if (remaining > 0) createimp$adjust(stock, Math.min(step, remaining));
                else createimp$adjust(tokenStack, step);
            }
        } else {
            createimp$adjust(tokenStack, remove ? -step : step);
        }
    }

    /** Re-render just template rows; CFA and CFL amount rendering remain untouched for every real resource. */
    @Inject(method = "renderItemEntry", at = @At("HEAD"), cancellable = true)
    private void createimp$renderTemplate(GuiGraphics graphics, float scale, BigGenericStack entry,
                                         boolean hovered, boolean orderBar, CallbackInfo ci) {
        ItemStack token = createimp$token(entry);
        if (token.isEmpty()) return;
        ci.cancel();
        boolean style2 = createimp$style2();
        if (!orderBar) {
            if (style2) AllGuiTextures.STOCK_KEEPER_REQUEST_SLOT.render(graphics, 0, 0);
            else graphics.blit(CREATEIMP_TEMPLATE_BG, 0, 0, 0, 0, 18, 18, 18, 18);
        } else if (!style2) graphics.blit(CREATEIMP_ORDER_BG, 0, 0, 0, 0, 18, 18, 18, 18);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(1, 1, 0);
        pose.translate(9, 9, 0);
        float zoom = scale * (hovered ? CREATEIMP_HOVER_SCALE : 1.0f);
        pose.scale(zoom, zoom, zoom);
        pose.translate(-9, -9, 0);
        boolean clip = !style2 && createimp$fluid(token);
        if (clip) {
            Matrix4f matrix = new Matrix4f(pose.last().pose());
            Vector4f pivot = matrix.transform(new Vector4f(9, 9, 0, 1));
            Vector4f top = matrix.transform(new Vector4f(1, 1, 0, 1));
            Vector4f bottom = matrix.transform(new Vector4f(15, 15, 0, 1));
            // Match the stock keeper: remove only the hover transform (retain the
            // ambient scale), then grow the resting clip symmetrically about its center.
            float hoverScale = hovered ? CREATEIMP_HOVER_SCALE : 1.0f;
            float left = pivot.x() + (top.x() - pivot.x()) / hoverScale;
            float upper = pivot.y() + (top.y() - pivot.y()) / hoverScale;
            float right = pivot.x() + (bottom.x() - pivot.x()) / hoverScale;
            float lower = pivot.y() + (bottom.y() - pivot.y()) / hoverScale;
            if (hovered) {
                float centerX = (left + right) / 2.0f;
                float centerY = (upper + lower) / 2.0f;
                float halfWidth = (right - left + CREATEIMP_FLUID_HOVER_GROWTH / 2.0f) / 2.0f;
                float halfHeight = (lower - upper + CREATEIMP_FLUID_HOVER_GROWTH / 2.0f) / 2.0f;
                left = centerX - halfWidth;
                upper = centerY - halfHeight;
                right = centerX + halfWidth;
                lower = centerY + halfHeight;
            }
            graphics.enableScissor(Math.round(Math.min(left, right)), Math.round(Math.min(upper, lower)),
                    Math.round(Math.max(left, right)), Math.round(Math.max(upper, lower)));
        }
        try {
            GuiGameElement.of(token).render(graphics);
        } finally {
            if (clip) graphics.disableScissor();
        }
        if (style2) {
            pose.translate(0, 0, 150);
            graphics.blit(orderBar ? CREATEIMP_ORDER_FG : CREATEIMP_TEMPLATE_FG, -1, -1, 0, 0, 18, 18, 18, 18);
        }
        pose.popPose();
        int count = orderBar ? entry.get().amount() : 0;
        if (!orderBar && createimp$merge()) {
            TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(token);
            if (target != null) count = createimp$remaining(createimp$realStock(target.display()));
        }
        if (count <= 0) return;
        pose.pushPose();
        pose.translate(0, 0, 250);
        if (createimp$fluid(token)) TemplateFluidDisplayHelper.renderFluidAmountBadge(graphics, count);
        else graphics.renderItemDecorations(Minecraft.getInstance().font, token.copyWithCount(1), 1, 1, count == 1 ? "" : String.valueOf(count));
        pose.popPose();
    }

    /** Generic tooltip builders may bypass ItemTooltipEvent. Route token tooltips through the shared event pipeline. */
    @Redirect(method = "renderForeground", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;renderComponentTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"))
    private void createimp$templateTooltip(GuiGraphics graphics, Font font, List<Component> original, int x, int y) {
        BigGenericStack entry = createimp$hovered(x, y);
        if (entry == null || createimp$token(entry).isEmpty()) {
            graphics.renderComponentTooltip(font, original, x, y);
            return;
        }
        ItemStack token = createimp$token(entry);
        List<Component> lines;
        if (createimp$fluid(token) && this.getHoveredSlot(x, y).getFirst() == -1) {
            lines = List.of(token.getHoverName(), CreateLang.text("x" + TemplateFluidDisplayHelper.formatPreciseAmount(entry.get().amount()))
                    .style(ChatFormatting.DARK_GRAY).component());
        } else lines = Screen.getTooltipFromItem(Minecraft.getInstance(), token);
        graphics.renderComponentTooltip(font, lines, x, y);
    }

    /** Keep a fluid template a token when JEI/EMI asks what is under the cursor. */
    @Redirect(method = "getHoveredIngredient", at = @At(value = "INVOKE", target =
            "Lde/theidler/create_mobile_packages/compat/fluidlogistics/CFLBridge;isFluidResource(Lru/zznty/create_factory_abstractions/generic/support/BigGenericStack;)Z"))
    private boolean createimp$tokenIsNotRealFluid(BigGenericStack entry) {
        return createimp$token(entry).isEmpty()
                && de.theidler.create_mobile_packages.compat.fluidlogistics.CFLBridge.isFluidResource(entry);
    }

    /** Recipe helper snapshots must never treat a template token as a real ingredient. */
    @Inject(method = "stockSnapshot", at = @At("RETURN"), cancellable = true)
    private void createimp$excludeTemplatesFromRecipes(CallbackInfoReturnable<GenericInventorySummary> cir) {
        GenericInventorySummary original = cir.getReturnValue();
        GenericInventorySummary filtered = GenericInventorySummary.empty();
        for (GenericStack stack : original.get()) {
            if (stack.key() instanceof ItemKey key && TemplateOrderTokenHelper.isToken(key.stack())) continue;
            filtered.add(stack);
        }
        cir.setReturnValue(filtered);
    }
}
