package com.molox.createimp.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * 进程面板日志文本的高亮解析、绘制、按字符换行逻辑，主界面（单行滚动
 * 展示最新日志）和详情界面（换行展示完整历史日志）共用同一套解析规则，
 * 只是各自的渲染方式（单行滚动 / 多行换行）不同。
 */
public final class ProcessLogTextUtil {

    private ProcessLogTextUtil() {
    }

    public static final int NORMAL = 0;
    public static final int HIGHLIGHT = 1;
    /** 详情界面日志前面 "[XX分XX秒]" 时间戳专用的第三种颜色类别。 */
    public static final int TIMESTAMP = 2;

    public record Segment(String text, int colorType) {
    }

    /**
     * 解析工作仓库日志里 "_xxx_" 单下划线标记的高亮片段：按 "_" 切分，
     * 下标为奇数的片段视为高亮，偶数（含 0）视为普通文字。
     */
    public static List<Segment> parseHighlight(String message) {
        List<Segment> segments = new ArrayList<>();
        String[] parts = message.split("_", -1);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            segments.add(new Segment(parts[i], i % 2 == 1 ? HIGHLIGHT : NORMAL));
        }
        return segments;
    }

    public static int width(Font font, List<Segment> segments) {
        int total = 0;
        for (Segment segment : segments) {
            total += font.width(segment.text());
        }
        return total;
    }

    /** 从左到右依次绘制各段，返回实际绘制的总宽度。 */
    public static int draw(GuiGraphics graphics, Font font, List<Segment> segments, int x, int y,
                           int normalColor, int highlightColor, int timestampColor) {
        int cursor = x;
        for (Segment segment : segments) {
            int color = switch (segment.colorType()) {
                case HIGHLIGHT -> highlightColor;
                case TIMESTAMP -> timestampColor;
                default -> normalColor;
            };
            graphics.drawString(font, segment.text(), cursor, y, color, false);
            cursor += font.width(segment.text());
        }
        return cursor - x;
    }

    /** 展平后的单个字符 + 它所属的颜色类别，wrap() 内部用来做统一的换行计算。 */
    private record CharUnit(char ch, int colorType) {
    }

    /**
     * 把一串 Segment 拆成若干行，每行宽度不超过 {@code maxWidth}。
     * <p>
     * 只有当前客户端语言**不是**简体中文（zh_cn）时才按"单词"换行（英文
     * 那种优先在空格处断开，不会把一个单词从中间拆开）；简体中文本身没有
     * 空格分词的习惯，即使消息里偶尔出现空格（比如多个物品之间用空格
     * 隔开），也统一按字符硬拆，行为和之前完全一样，不受这次改动影响。
     */
    public static List<List<Segment>> wrap(Font font, List<Segment> segments, int maxWidth) {
        boolean useWordWrap = !"zh_cn".equals(Minecraft.getInstance().getLanguageManager().getSelected());
        return useWordWrap ? wrapByWord(font, segments, maxWidth) : wrapByChar(font, segments, maxWidth);
    }

    /**
     * 优先在"空格"处断开（英文按单词换行，不会把一个单词从中间拆开）；
     * 只有当从当前行开头到某个字符为止，中间完全没有出现过空格（比如
     * 英文里单独一个词本身就超出整行宽度的极端情况）时，才退回到按字符
     * 硬拆。普通/高亮/时间戳的颜色边界会被保留，同一行内可能同时含有
     * 不同颜色类别的片段。
     */
    private static List<List<Segment>> wrapByWord(Font font, List<Segment> segments, int maxWidth) {
        List<CharUnit> units = flatten(segments);

        List<List<Segment>> lines = new ArrayList<>();
        int lineStart = 0;
        int i = 0;
        int width = 0;
        // 当前行内、最近一个空格的下标（绝对下标）；没遇到过空格时为 -1，
        // 表示这一行目前没有可以断开的位置。
        int lastSpaceIndex = -1;

        while (i < units.size()) {
            char c = units.get(i).ch();
            int charWidth = font.width(String.valueOf(c));

            if (width + charWidth > maxWidth && i > lineStart) {
                if (lastSpaceIndex >= lineStart) {
                    lines.add(buildSegments(units, lineStart, lastSpaceIndex));
                    lineStart = lastSpaceIndex + 1;
                } else {
                    lines.add(buildSegments(units, lineStart, i));
                    lineStart = i;
                }
                i = lineStart;
                width = 0;
                lastSpaceIndex = -1;
                continue;
            }

            if (c == ' ') {
                lastSpaceIndex = i;
            }
            width += charWidth;
            i++;
        }
        if (lineStart < units.size()) {
            lines.add(buildSegments(units, lineStart, units.size()));
        }
        if (lines.isEmpty()) {
            lines.add(new ArrayList<>());
        }
        return lines;
    }

    /**
     * 纯按字符累计宽度换行，不考虑空格——简体中文用这个，和这次改动之前
     * 的行为完全一样。
     */
    private static List<List<Segment>> wrapByChar(Font font, List<Segment> segments, int maxWidth) {
        List<CharUnit> units = flatten(segments);

        List<List<Segment>> lines = new ArrayList<>();
        int lineStart = 0;
        int i = 0;
        int width = 0;

        while (i < units.size()) {
            char c = units.get(i).ch();
            int charWidth = font.width(String.valueOf(c));

            if (width + charWidth > maxWidth && i > lineStart) {
                lines.add(buildSegments(units, lineStart, i));
                lineStart = i;
                width = 0;
                continue;
            }

            width += charWidth;
            i++;
        }
        if (lineStart < units.size()) {
            lines.add(buildSegments(units, lineStart, units.size()));
        }
        if (lines.isEmpty()) {
            lines.add(new ArrayList<>());
        }
        return lines;
    }

    private static List<CharUnit> flatten(List<Segment> segments) {
        List<CharUnit> units = new ArrayList<>();
        for (Segment segment : segments) {
            for (int i = 0; i < segment.text().length(); i++) {
                units.add(new CharUnit(segment.text().charAt(i), segment.colorType()));
            }
        }
        return units;
    }

    /** 把 [start, end) 范围内的字符按颜色类别重新合并成若干 Segment。 */
    private static List<Segment> buildSegments(List<CharUnit> units, int start, int end) {
        List<Segment> result = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        int currentColorType = NORMAL;
        boolean hasContent = false;
        for (int i = start; i < end; i++) {
            CharUnit unit = units.get(i);
            if (hasContent && unit.colorType() != currentColorType) {
                result.add(new Segment(currentText.toString(), currentColorType));
                currentText = new StringBuilder();
            }
            currentColorType = unit.colorType();
            currentText.append(unit.ch());
            hasContent = true;
        }
        if (!currentText.isEmpty()) {
            result.add(new Segment(currentText.toString(), currentColorType));
        }
        return result;
    }
}