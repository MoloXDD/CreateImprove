package com.molox.createimp.mixin;

import com.molox.createimp.CreateImp;
import com.molox.createimp.CreateImpConfig;
import com.molox.createimp.item.TemplateOrderTarget;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterMenu;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 让红石请求器自己的 9 格配置界面能正确显示已经配置好的模板：格子背景换成
 * 模板专属贴图，悬浮提示按仓管界面同一套规则追加配方说明行。红石请求器
 * 界面本身不新增任何输入方式（不支持 JEI 拖拽模板），模板只能通过仓管界面
 * 配置进来，这里只负责展示。
 */
@Mixin(value = RedstoneRequesterScreen.class, remap = false)
public abstract class MixinRedstoneRequesterScreen {

    @Unique
    private static final ResourceLocation TEMPLATE_REQUEST_SLOT_BG =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/stock_keeper_template_request_slot_bg.png");

    @Unique
    private static final ResourceLocation TEMPLATE_REQUEST_SLOT_BG_2 =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/stock_keeper_template_request_slot_bg2.png");

    @Unique
    private static boolean createimp$isStyle2() {
        return CreateImp.getConfig().templateFunctionConfig.stockKeeperTemplateDisplayStyle
                == CreateImpConfig.TemplateFunctionConfig.TemplateDisplayStyle.STYLE_2;
    }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void createimp$drawTemplateSlotBackgrounds(GuiGraphics graphics, float partialTick,
                                                       int mouseX, int mouseY, CallbackInfo ci) {
        if (createimp$isStyle2()) {
            // 样式2下贴图改成画在物品之上的前景，这里（物品渲染之前）不再画。
            return;
        }
        RedstoneRequesterScreen self = (RedstoneRequesterScreen) (Object) this;
        ItemStackHandler ghostInventory = self.getMenu().ghostInventory;
        int x = self.getGuiLeft();
        int y = self.getGuiTop();
        for (int i = 0; i < ghostInventory.getSlots(); i++) {
            if (!TemplateOrderTokenHelper.isToken(ghostInventory.getStackInSlot(i))) {
                continue;
            }
            int slotX = x + 27 + i * 20 - 1;
            int slotY = y + 28 - 1;
            graphics.blit(TEMPLATE_REQUEST_SLOT_BG, slotX, slotY, 0, 0, 18, 18, 18, 18);
        }
    }

    /**
     * 样式2：贴图画在物品图标之上。这个界面的 9 格是靠原版
     * {@code AbstractContainerScreen} 自带的物品渲染管线画出来的，本身没有
     * 仓管界面那种"悬浮时物品放大 7.5%"的效果，所以这里没有缩放可跟随，
     * 贴图始终按原始大小绘制在物品上方。
     */
    @Inject(method = "renderForeground", at = @At("TAIL"))
    private void createimp$drawTemplateForeground(GuiGraphics graphics, int mouseX, int mouseY,
                                                  float partialTicks, CallbackInfo ci) {
        if (!createimp$isStyle2()) {
            return;
        }
        RedstoneRequesterScreen self = (RedstoneRequesterScreen) (Object) this;
        ItemStackHandler ghostInventory = self.getMenu().ghostInventory;
        int x = self.getGuiLeft();
        int y = self.getGuiTop();
        for (int i = 0; i < ghostInventory.getSlots(); i++) {
            if (!TemplateOrderTokenHelper.isToken(ghostInventory.getStackInSlot(i))) {
                continue;
            }
            int slotX = x + 27 + i * 20 - 1;
            int slotY = y + 28 - 1;
            graphics.pose().pushPose();
            graphics.pose().translate(0.0, 0.0, 300.0);
            graphics.blit(TEMPLATE_REQUEST_SLOT_BG_2, slotX, slotY, 0, 0, 18, 18, 18, 18);
            graphics.pose().popPose();
        }
    }

    /**
     * 与仓管界面模板提示规则一致：只有当前请求栏（这里是红石请求器自己的
     * 9 格 ghost 库存）里，同一个展示物同时对应两个及以上不同的模板时，
     * 才追加"使用 A + B 的配方"这一行；判重范围就是这 9 格本身，不依赖
     * 任何额外的网络同步。原有的"发送 X 数量 / 滚轮调整"两行提示保留不变。
     */
    @Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), cancellable = true)
    private void createimp$appendTemplateTooltip(ItemStack pStack, CallbackInfoReturnable<List<Component>> cir) {
        TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(pStack);
        if (target == null) {
            return;
        }

        RedstoneRequesterScreen self = (RedstoneRequesterScreen) (Object) this;
        ItemStackHandler ghostInventory = self.getMenu().ghostInventory;

        Set<TemplateOrderTarget> distinctTemplates = new HashSet<>();
        for (int i = 0; i < ghostInventory.getSlots(); i++) {
            TemplateOrderTarget slotTarget = TemplateOrderTokenHelper.getTarget(ghostInventory.getStackInSlot(i));
            if (slotTarget != null && ItemStack.isSameItemSameComponents(slotTarget.display(), target.display())) {
                distinctTemplates.add(slotTarget);
            }
        }
        if (distinctTemplates.size() <= 1) {
            return;
        }

        List<Component> tooltip = new ArrayList<>(cir.getReturnValue());
        MutableComponent line = Component.translatable("createimp.item.template_order_token.tooltip_prefix");
        for (ItemStack ingredient : target.ingredients()) {
            line = line.append(" ").append(ingredient.getHoverName());
        }
        line = line.append(Component.translatable("createimp.item.template_order_token.tooltip_suffix"));
        tooltip.add(line.withStyle(ChatFormatting.GRAY));
        cir.setReturnValue(tooltip);
    }
}