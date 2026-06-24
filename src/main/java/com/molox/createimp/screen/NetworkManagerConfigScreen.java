package com.molox.createimp.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.molox.createimp.network.ApplyNetworkPacket;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class NetworkManagerConfigScreen extends AbstractSimiScreen {

    // 两个选项的图标
    private static final AllIcons ICON_SINGLE  = AllIcons.I_CONFIRM;
    private static final AllIcons ICON_NETWORK = AllIcons.I_PRIORITY_VERY_HIGH;

    // 与 ValueSettingsScreen iconMode 完全一致的布局参数
    private static final int MILESTONE_SIZE = 8;       // VALUE_SETTINGS_WIDE_MILESTONE 宽 13，间距 8
    private static final int MAX_VALUE      = 1;       // 0 = 单个，1 = 全网络
    private static final int ROW_HEIGHT     = 11;

    private final InteractionHand hand;
    private final BlockPos targetPos;
    private final Vec3 clickLocation;

    private int ticksOpen = 0;
    private int currentValue = 0;          // 0 = 配置当前元件，1 = 配置全网络
    private int soundCoolDown = 0;

    // 布局缓存（init 时计算）
    private int valueBarWidth;
    private int maxLabelWidth;

    public NetworkManagerConfigScreen(InteractionHand hand, BlockPos targetPos, Vec3 clickLocation) {
        this.hand       = hand;
        this.targetPos  = targetPos;
        this.clickLocation = clickLocation;
    }

    @Override
    protected void init() {
        // iconMode：maxLabelWidth = -18（与 ValueSettingsScreen 一致）
        maxLabelWidth = -18;
        // milestoneCount = maxValue / milestoneInterval + 1 = 1/1 + 1 = 2
        int milestoneCount = MAX_VALUE + 1;
        int scale = 2; // maxValue <= 128 时 scale = 2
        // valueBarWidth = (maxValue + 1) * scale + 1 + milestoneCount * MILESTONE_SIZE
        valueBarWidth = (MAX_VALUE + 1) * scale + 1 + milestoneCount * MILESTONE_SIZE;
        // width = maxLabelWidth + 14 + (valueBarWidth + 10)
        int w = maxLabelWidth + 14 + (valueBarWidth + 10);
        // height = rows.size() * ROW_HEIGHT = 1 * 11
        int h = ROW_HEIGHT;
        setWindowSize(w, h);
        super.init();
        // 将鼠标移到初始选项（左侧）的坐标
        Vec2 initCoord = getCoordinateOfValue(0);
        setCursor(initCoord);
    }

    private void setCursor(Vec2 coord) {
        double guiScale = minecraft.getWindow().getGuiScale();
        GLFW.glfwSetCursorPos(
                minecraft.getWindow().getWindow(),
                coord.x * guiScale,
                coord.y * guiScale
        );
    }

    /** 计算 value（0 或 1）在屏幕上的坐标，对应 ValueSettingsScreen.getCoordinateOfValue */
    private Vec2 getCoordinateOfValue(int value) {
        int scale = 2;
        // 与 ValueSettingsScreen 完全一致的公式
        float xOut = (float)(guiLeft + (Math.max(1, value) - 1) / 1 * MILESTONE_SIZE + value * scale) + 1.5f;
        xOut += (float)(maxLabelWidth + 14 + 4);
        if (value % 1 == 0) {           // milestoneInterval = 1，所有点都是 milestone
            xOut += (float)(MILESTONE_SIZE / 2);
        }
        if (value > 0) {
            xOut += (float)MILESTONE_SIZE;
        }
        float yOut = (float)guiTop + 0.5f * ROW_HEIGHT - 0.5f;
        return new Vec2(xOut, yOut);
    }

    /** 从鼠标坐标反算最近的 value（0 或 1） */
    private int getClosestValue(int mouseX, int mouseY) {
        double bestDiff = Double.MAX_VALUE;
        int best = 0;
        for (int v = 0; v <= MAX_VALUE; v++) {
            Vec2 coord = getCoordinateOfValue(v);
            double diff = Math.abs(coord.x - (float) mouseX);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = v;
            }
        }
        return best;
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;

        Component title = Component.translatable("createimp.gui.network_manager.config_network");
        Component tip   = Component.translatable("createimp.gui.network_manager.config_release");

        double fadeIn = Math.pow(
                Mth.clamp((ticksOpen + partialTicks) / 4.0, 0.0, 1.0), 1.0);

        // 计算最宽标签（iconMode 下需要包含选项文字宽度）
        int fattestLabel = Math.max(font.width(tip), font.width(title));
        Component optSingle  = Component.translatable("createimp.gui.network_manager.config_single");
        Component optNetwork = Component.translatable("createimp.gui.network_manager.config_whole_network");
        fattestLabel = Math.max(fattestLabel, font.width(optSingle));
        fattestLabel = Math.max(fattestLabel, font.width(optNetwork));

        int fatTipOffset  = Math.max(0, fattestLabel + 10 - (windowWidth + 13)) / 2;
        int bgWidth       = Math.max(windowWidth + 13, fattestLabel + 10);
        int fadeInWidth   = (int)(bgWidth * fadeIn);
        int fadeInStart   = (bgWidth - fadeInWidth) / 2 - fatTipOffset;

        // iconMode 时 additionalHeight = 46
        int additionalHeight = 46;
        int zLevel = 0;

        UIRenderHelper.drawStretched(graphics,
                x - 11 + fadeInStart, y - 17,
                fadeInWidth, windowHeight + additionalHeight,
                zLevel, AllGuiTextures.VALUE_SETTINGS_OUTER_BG);
        UIRenderHelper.drawStretched(graphics,
                x - 10 + fadeInStart, y - 18,
                fadeInWidth - 2, 1,
                zLevel, AllGuiTextures.VALUE_SETTINGS_OUTER_BG);
        UIRenderHelper.drawStretched(graphics,
                x - 10 + fadeInStart, y - 17 + windowHeight + additionalHeight,
                zLevel, fadeInWidth - 2,   // 注意原码此处参数顺序是 zLevel/w，保持一致
                1, AllGuiTextures.VALUE_SETTINGS_OUTER_BG);

        if (fadeInWidth > fattestLabel) {
            int textX = x - 11 - fatTipOffset + bgWidth / 2;
            graphics.drawString(font, title,
                    textX - font.width(title) / 2, y - 14,
                    0xDDDDDD, false);
            graphics.drawString(font, tip,
                    textX - font.width(tip) / 2, y + windowHeight + additionalHeight - 27,
                    0xDDDDDD, false);
        }

        // 黄铜框（包裹滑动条区域）
        renderBrassFrame(graphics,
                x + maxLabelWidth + 14, y - 3,
                valueBarWidth + 8, ROW_HEIGHT + 5);

        // 滑动条背景
        UIRenderHelper.drawStretched(graphics,
                x + maxLabelWidth + 17, y,
                valueBarWidth + 2, ROW_HEIGHT - 1,
                zLevel, AllGuiTextures.VALUE_SETTINGS_BAR_BG);

        // 渲染两个 WIDE_MILESTONE（选项锚点）
        int valueBarX = x + maxLabelWidth + 14 + 4;
        int milestoneCount = MAX_VALUE + 1;
        int scale = 2;
        int milestoneX = valueBarX;
        for (int milestone = 0; milestone < milestoneCount; milestone++) {
            AllGuiTextures.VALUE_SETTINGS_WIDE_MILESTONE.render(graphics, milestoneX, y + 1);
            milestoneX += MILESTONE_SIZE + 1 * scale; // milestoneInterval=1
        }

        if (ticksOpen < 1) return;

        // 当前选中值
        int closest = getClosestValue(mouseX, mouseY);
        if (closest != currentValue) {
            currentValue = closest;
            if (soundCoolDown == 0) {
                minecraft.getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                com.simibubi.create.AllSoundEvents.SCROLL_VALUE.getMainEvent(),
                                Mth.lerp((float) closest / (float) MAX_VALUE, 1.15f, 1.5f),
                                0.25f));
                soundCoolDown = 1;
            }
        }

        Vec2 coordinate = getCoordinateOfValue(closest);
        AllIcons cursorIcon = (closest == 0) ? ICON_SINGLE : ICON_NETWORK;
        Component cursorText = (closest == 0) ? optSingle : optNetwork;

        // 图标光标
        int cursorWidth = 16 / 2 * 2 + 3;   // iconMode 固定 cursorWidth = 16/2*2+3 = 19
        int cursorX = (int) coordinate.x - cursorWidth / 2;
        int cursorY = (int) coordinate.y - 7;

        AllGuiTextures.VALUE_SETTINGS_CURSOR_ICON.render(graphics, cursorX - 2, cursorY - 3);
        RenderSystem.setShaderColor(0.265625f, 0.125f, 0.0f, 1.0f);
        cursorIcon.render(graphics, cursorX + 1, cursorY - 1);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        if (fadeInWidth > fattestLabel) {
            int textX = x - 11 - fatTipOffset + bgWidth / 2;
            graphics.drawString(font, cursorText,
                    textX - font.width(cursorText) / 2,
                    y + windowHeight + additionalHeight - 40,
                    0xFBB454, false);
        }
    }

    private void renderBrassFrame(GuiGraphics graphics, int x, int y, int w, int h) {
        AllGuiTextures.BRASS_FRAME_TL.render(graphics, x, y);
        AllGuiTextures.BRASS_FRAME_TR.render(graphics, x + w - 4, y);
        AllGuiTextures.BRASS_FRAME_BL.render(graphics, x, y + h - 4);
        AllGuiTextures.BRASS_FRAME_BR.render(graphics, x + w - 4, y + h - 4);
        int zLevel = 0;
        if (h > 8) {
            UIRenderHelper.drawStretched(graphics, x,         y + 4,     3,     h - 8, zLevel, AllGuiTextures.BRASS_FRAME_LEFT);
            UIRenderHelper.drawStretched(graphics, x + w - 3, y + 4,     3,     h - 8, zLevel, AllGuiTextures.BRASS_FRAME_RIGHT);
        }
        if (w > 8) {
            UIRenderHelper.drawCropped  (graphics, x + 4,     y,         w - 8, 3,     zLevel, AllGuiTextures.BRASS_FRAME_TOP);
            UIRenderHelper.drawCropped  (graphics, x + 4,     y + h - 3, w - 8, 3,     zLevel, AllGuiTextures.BRASS_FRAME_BOTTOM);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int a = (int)(80.0f * Math.min(1.0f,
                (ticksOpen + AnimationTickHolder.getPartialTicks()) / 20.0f)) << 24;
        graphics.fillGradient(0, 0, width, height, 0x101010 | a, 0x101010 | a);
    }

    @Override
    public void tick() {
        ticksOpen++;
        if (soundCoolDown > 0) soundCoolDown--;
        super.tick();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int next = Mth.clamp(currentValue - (int) Math.signum(scrollY), 0, MAX_VALUE);
        if (next == currentValue) return false;
        setCursor(getCoordinateOfValue(next));
        return true;
    }

    /** 松开右键（Use 键）时触发确认 */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (minecraft.options.keyUse.matches(keyCode, scanCode)) {
            var window = minecraft.getWindow();
            double mx = minecraft.mouseHandler.xpos()
                    * (double) window.getGuiScaledWidth() / (double) window.getScreenWidth();
            double my = minecraft.mouseHandler.ypos()
                    * (double) window.getGuiScaledHeight() / (double) window.getScreenHeight();
            saveAndClose((int) mx, (int) my);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1) {
            saveAndClose((int) mouseX, (int) mouseY);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void saveAndClose(int mouseX, int mouseY) {
        int chosen = getClosestValue(mouseX, mouseY);
        boolean wholeNetwork = (chosen == 1);
        PacketDistributor.sendToServer(
                new ApplyNetworkPacket(hand, targetPos, clickLocation, wholeNetwork));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}