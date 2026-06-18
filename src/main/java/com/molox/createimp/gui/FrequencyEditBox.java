package com.molox.createimp.gui;

import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.trains.schedule.DestinationSuggestions;
import net.createmod.catnip.data.IntAttached;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FrequencyEditBox extends EditBox {

    private final DestinationSuggestions destinationSuggestions;
    private Runnable onDefocus;

    private static final Field previousField;
    private static final Field activeField;
    static {
        try {
            previousField = DestinationSuggestions.class.getDeclaredField("previous");
            previousField.setAccessible(true);
            activeField = DestinationSuggestions.class.getDeclaredField("active");
            activeField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public FrequencyEditBox(Screen screen, Font font, int x, int y, int w, int h,
                            String localFrequency, int suggestionsYOffset) {
        super(font, x, y, w, h, Component.empty());

        Minecraft mc = Minecraft.getInstance();
        List<IntAttached<String>> options = new ArrayList<>();
        Set<String> alreadyAdded = new HashSet<>();

        // 注意：localFrequency 不加入 alreadyAdded，让它也出现在备选项中
        if (mc.player != null) {
            for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                appendFrequencies(options, alreadyAdded, mc.player.getInventory().getItem(i));
            }
        }

        destinationSuggestions = new DestinationSuggestions(
                mc, screen, this, mc.font, options, true, suggestionsYOffset
        );
        destinationSuggestions.setAllowSuggestions(true);
        destinationSuggestions.updateCommandInfo();
    }

    private static void appendFrequencies(List<IntAttached<String>> options, Set<String> alreadyAdded,
                                          ItemStack stack) {
        if (stack.isEmpty()) return;
        List<List<ClipboardEntry>> pages = ClipboardEntry.readAll(stack);
        if (pages == null) return;
        for (List<ClipboardEntry> page : pages) {
            for (ClipboardEntry entry : page) {
                String string = entry.text.getString();
                if (!string.startsWith("@") || string.length() <= 1) continue;
                String frequency = string.substring(1).trim();
                if (frequency.isBlank() || alreadyAdded.contains(frequency)) continue;
                alreadyAdded.add(frequency);
                options.add(IntAttached.withZero(frequency));
            }
        }
    }

    public void setOnDefocus(Runnable callback) {
        this.onDefocus = callback;
    }

    private void setPrevious(String value) {
        try { previousField.set(destinationSuggestions, value); } catch (IllegalAccessException ignored) {}
    }

    private void setActive(boolean value) {
        try { activeField.set(destinationSuggestions, value); } catch (IllegalAccessException ignored) {}
    }

    public void enterEditMode() {
        setFocused(true);
        setHighlightPos(0);
        setCursorPosition(getValue().length());
        // 强制以全选状态（input 被 replace 为 ""）重新过滤，显示全部备选项
        setPrevious("\u0000");
        setActive(true);
        destinationSuggestions.updateCommandInfo();
    }

    public void exitEditMode(String defaultFrequency) {
        moveCursorToEnd(false);
        setSuggestion("");
        if (getValue().isBlank()) setValue(defaultFrequency);
        setFocused(false);
        // 同步 active=false，让 updateCommandInfo 立即清空 suggestions
        setActive(false);
        setPrevious("\u0000");
        destinationSuggestions.updateCommandInfo();
        if (onDefocus != null) onDefocus.run();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused()) return false;

        if (keyCode == 257 || keyCode == 335 || keyCode == 256) {
            exitEditMode(getDefaultFrequency());
            return true;
        }

        if (keyCode == 258) {
            if (destinationSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
                exitEditMode(getDefaultFrequency());
                return true;
            }
            return false;
        }

        if (keyCode == 264 || keyCode == 265) {
            return destinationSuggestions.keyPressed(keyCode, scanCode, modifiers);
        }

        boolean result = super.keyPressed(keyCode, scanCode, modifiers);
        if (result) {
            setPrevious("\u0000");
            destinationSuggestions.updateCommandInfo();
        }
        return result;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (!isFocused()) return false;
        boolean result = super.charTyped(c, modifiers);
        if (result) {
            setPrevious("\u0000");
            destinationSuggestions.updateCommandInfo();
        }
        return result;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        if (!isFocused()) {
            if (isMouseOver(mouseX, mouseY)) {
                super.mouseClicked(mouseX, mouseY, button);
                enterEditMode();
                return true;
            }
            return false;
        }

        if (destinationSuggestions.mouseClicked((int) mouseX, (int) mouseY, button)) {
            exitEditMode(getDefaultFrequency());
            return true;
        }

        if (isMouseOver(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        exitEditMode(getDefaultFrequency());
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return destinationSuggestions.mouseScrolled(net.minecraft.util.Mth.clamp(scrollY, -1.0, 1.0));
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        destinationSuggestions.render(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    public void tick() {
        destinationSuggestions.tick();
    }

    private String getDefaultFrequency() {
        return com.molox.createimp.block.labeled_redstone_link.LabeledRedstoneLinkBlockEntity.DEFAULT_FREQUENCY;
    }
}