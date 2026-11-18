package com.molox.createimp.compat.fluidlogistics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/** Lazy bridges for helpers relocated or introduced in FluidLogistics 1.3.2. */
final class FluidLogisticsVersionBridge {
    private FluidLogisticsVersionBridge() {}

    private static Class<?> findClass(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name, false, FluidLogisticsVersionBridge.class.getClassLoader());
            } catch (ClassNotFoundException ignored) {
                // Older releases have a different package layout.
            }
        }
        throw new IllegalStateException("Unsupported FluidLogistics API: " + String.join(", ", names));
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Unsupported FluidLogistics API: " + owner.getName() + "." + name, e);
        }
    }

    private static Object invoke(Method method, Object... arguments) {
        try {
            return method.invoke(null, arguments);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access FluidLogistics API", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("FluidLogistics API invocation failed", cause);
        }
    }

    private static final class AmountApi {
        private static final Class<?> TYPE = findClass(
                "com.yision.fluidlogistics.foundation.fluid.FluidAmountHelper",
                "com.yision.fluidlogistics.util.FluidAmountHelper");
        private static final Method FORMAT = method(TYPE, "format", int.class);
        private static final Method PRECISE = method(TYPE, "formatPrecise", int.class);
        private static final Method ADJUST = method(TYPE, "adjustFluidRequestAmount", int.class,
                boolean.class, boolean.class, boolean.class, int.class, int.class);
        private static final Method STOCK_STEP = method(TYPE, "getStockKeeperFluidRequestStep", boolean.class, boolean.class);
    }

    static String format(int amount) {
        return (String) invoke(AmountApi.FORMAT, amount);
    }

    static String formatPrecise(int amount) {
        return (String) invoke(AmountApi.PRECISE, amount);
    }

    static int adjust(int amount, boolean forward, boolean shift, boolean control, int min, int max) {
        return (Integer) invoke(AmountApi.ADJUST, amount, forward, shift, control, min, max);
    }

    static int stockStep(boolean shift, boolean control) {
        return (Integer) invoke(AmountApi.STOCK_STEP, shift, control);
    }

    private static final class RendererApi {
        private static final Method RENDER = method(findClass(
                "com.yision.fluidlogistics.foundation.render.FluidSlotAmountRenderer",
                "com.yision.fluidlogistics.render.FluidSlotAmountRenderer"),
                "renderInStockKeeper", GuiGraphics.class, int.class);
    }

    static void renderAmount(GuiGraphics graphics, int amount) {
        invoke(RendererApi.RENDER, graphics, amount);
    }

    private static final class PackageApi {
        private static final Class<?> TYPE = optionalPackageHelper();
        private static final Method FLUIDS = TYPE == null ? null : method(TYPE, "getContainedFluids", ItemStack.class);
        private static final Method SINGLE = TYPE == null ? null : method(TYPE, "getSingleContainedFluid", ItemStack.class);
        private static final Method CREATE = TYPE == null ? null : method(TYPE, "createCanonicalPackage", FluidStack.class);

        private static Class<?> optionalPackageHelper() {
            try {
                Class<?> helper = Class.forName("com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageContentHelper",
                        false, FluidLogisticsVersionBridge.class.getClassLoader());
                // 1.3.0 already has this class, but only 1.3.2 exposes the plural
                // reader for component-based multi-fluid cargo.
                helper.getMethod("getContainedFluids", ItemStack.class);
                return helper;
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                return null;
            }
        }
    }

    static boolean hasComponentPackages() {
        return PackageApi.TYPE != null;
    }

    @SuppressWarnings("unchecked")
    static List<FluidStack> containedFluids(ItemStack box) {
        return (List<FluidStack>) invoke(PackageApi.FLUIDS, box);
    }

    static FluidStack singleContainedFluid(ItemStack box) {
        return (FluidStack) invoke(PackageApi.SINGLE, box);
    }

    static ItemStack createPackage(FluidStack fluid) {
        return (ItemStack) invoke(PackageApi.CREATE, fluid);
    }
}
