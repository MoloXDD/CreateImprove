package com.molox.createimp.compat.fluidlogistics;

import net.neoforged.fml.ModList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流体包裹（FluidLogistics）模组是否已加载的判断入口。
 * <p>
 * 本类是本模组与流体包裹之间“只兼容、不依赖”的唯一开关：所有涉及流体包裹
 * 具体类（{@code CompressedTankItem}、{@code FluidStack} 等）的代码都必须
 * 封装在 {@code com.molox.createimp.compat.fluidlogistics} 包下的类里，并且
 * 必须先经过 {@link #isLoaded()} 判断为真才能调用——这样未安装流体包裹时，
 * 这些类永远不会被触发加载，不会因为缺少这个可选依赖而报错。
 */
public final class FluidLogisticsCompat {

    public static final String MOD_ID = "fluidlogistics";
    private static final Pattern SEMANTIC_VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    private FluidLogisticsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 供 Cloth Config 界面决定是否展示旧修复开关。流体包裹 1.2.7 起已经由
     * 上游负责重复承诺去重，因此只在已安装版本达到 1.2.7 时隐藏该开关。
     * 未安装或低于该版本时保持原有界面行为；旧 Redirect 是否真正可注入则由
     * 其目标调用的实际存在性决定。
     */
    public static boolean shouldShowLegacyDuplicatePromiseFixOption() {
        return !loadedVersionAtLeast(1, 2, 7);
    }

    private static boolean loadedVersionAtLeast(int requiredMajor, int requiredMinor, int requiredPatch) {
        ModList modList = ModList.get();
        if (modList == null) {
            return false;
        }
        return modList.getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .map(FluidLogisticsCompat::parseVersion)
                .map(version -> version.isAtLeast(requiredMajor, requiredMinor, requiredPatch))
                .orElse(false);
    }

    private static SemanticVersion parseVersion(String value) {
        Matcher matcher = SEMANTIC_VERSION.matcher(value);
        if (!matcher.find()) {
            return SemanticVersion.UNKNOWN;
        }
        try {
            return new SemanticVersion(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)));
        } catch (NumberFormatException ignored) {
            return SemanticVersion.UNKNOWN;
        }
    }

    private record SemanticVersion(int major, int minor, int patch) {
        private static final SemanticVersion UNKNOWN = new SemanticVersion(-1, -1, -1);

        private boolean isAtLeast(int requiredMajor, int requiredMinor, int requiredPatch) {
            if (major != requiredMajor) {
                return major > requiredMajor;
            }
            if (minor != requiredMinor) {
                return minor > requiredMinor;
            }
            return patch >= requiredPatch;
        }
    }
}
