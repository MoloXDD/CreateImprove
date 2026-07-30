package com.molox.createimp.screen;

import com.molox.createimp.CreateImp;
import com.molox.createimp.network.OpenRedstoneLinkRouterGuiPacket;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Collections;
import java.util.List;

/**
 * 路由器物品终端的配置界面。背景是我们自己单独的一张贴图（在 requester.png 里
 * "配置物品"那部分区域的基础上改的，裁切参数——起点 (16,160)、尺寸 184x88、
 * 贴图整体按 256x256 取 UV——和原图完全一致，只是换成了我们自己命名空间下的
 * 独立文件），不会影响 Create 原版工厂仪表配置物品界面（那边仍然引用
 * {@code AllGuiTextures.FACTORY_GAUGE_SET_ITEM}，没有被改动）。
 * <p>
 * 具体的两个物品槽位由 {@link RedstoneLinkRouterSetItemMenu} 摆放（原来居中的
 * 那一个槽位被拆成了以它为轴、上下各一个）。
 * <p>
 * 右下角确认键调用的是 {@code closeContainer()}，这条路径不会触发
 * {@code Screen.onClose()}（vanilla {@code AbstractContainerScreen.onClose()}
 * 才会调用 closeContainer，而确认键这里是反过来直接调用它），但无论走哪条关闭路径
 * 最终都会经过 {@code Minecraft.setScreen(...)} 从而调用到 {@code removed()}——
 * 所以"关闭后回到路由器界面"这个逻辑必须挂在 {@code removed()} 上，不能挂
 * {@code onClose()}。
 */
public class RedstoneLinkRouterSetItemScreen extends AbstractSimiContainerScreen<RedstoneLinkRouterSetItemMenu> {

    private static final ResourceLocation BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateImp.MODID, "textures/gui/redstone_link_router/router_item_set.png");
    private static final int BACKGROUND_U = 16;
    private static final int BACKGROUND_V = 160;
    private static final int BACKGROUND_WIDTH = 184;
    private static final int BACKGROUND_HEIGHT = 88;

    private IconButton confirmButton;
    private List<Rect2i> extraAreas = Collections.emptyList();

    public RedstoneLinkRouterSetItemScreen(RedstoneLinkRouterSetItemMenu container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    @Override
    protected void init() {
        int bgHeight = BACKGROUND_HEIGHT;
        int bgWidth = BACKGROUND_WIDTH;
        setWindowSize(bgWidth, bgHeight + AllGuiTextures.PLAYER_INVENTORY.getHeight());
        super.init();
        clearWidgets();
        int x = getGuiLeft();
        int y = getGuiTop();
        confirmButton = new IconButton(x + bgWidth - 40, y + bgHeight - 25, AllIcons.I_CONFIRM);
        confirmButton.withCallback(() -> minecraft.player.closeContainer());
        addRenderableWidget(confirmButton);
        extraAreas = List.of(new Rect2i(x + bgWidth, y + bgHeight - 30, 40, 20));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = getGuiLeft();
        int y = getGuiTop();
        graphics.blit(BACKGROUND_TEXTURE, x - 5, y, BACKGROUND_U, BACKGROUND_V, BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
        renderPlayerInventory(graphics, x + 5, y + 94);
        Component title = Component.translatable("createimp.gui.redstone_link_router.set_item.title");
        graphics.drawString(font, title,
                x + imageWidth / 2 - font.width(title) / 2 - 5, y + 4, 4013128, false);
    }

    @Override
    public List<Rect2i> getExtraAreas() {
        return extraAreas;
    }

    @Override
    public void removed() {
        super.removed();
        RedstoneLinkRouterScreen.open(new OpenRedstoneLinkRouterGuiPacket(getMenu().pos));
    }
}