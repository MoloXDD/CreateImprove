package com.molox.createimp.compat.fluidlogistics;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.batch_repackager.BatchPackageRepackageHelper;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Splits resource cargo with the upstream API, retaining our large-pattern batch packing for items. */
public final class FluidResourceRepackCompat {
    public record Result(boolean handled, List<BigItemStack> packages) {}

    private static final Result NOT_HANDLED = new Result(false, List.of());
    private static final Result REJECTED = new Result(true, List.of());
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();

    private FluidResourceRepackCompat() {}

    /** All preparation uses copies. A rejected resource order must leave its source inventory untouched. */
    public static Result prepare(List<ItemStack> fragments, int orderId, RandomSource random,
                                 BatchPackageRepackageHelper batchHelper) {
        if (!FluidLogisticsCompat.isLoaded() || ApiHolder.API == null) return NOT_HANDLED;
        Bridge api = ApiHolder.API;
        boolean resourceOrder = false;
        for (ItemStack fragment : fragments) {
            if (FluidLogisticsCompat.blocksItemOnlyUnpack(fragment)) {
                resourceOrder = true;
                break;
            }
        }
        if (!resourceOrder) return NOT_HANDLED;
        try {
            boolean hasResources = false;
            List<ItemStack> sorted = new ArrayList<>(fragments);
            sorted.sort(Comparator.comparingInt((ItemStack box) -> orderData(box).linkIndex())
                    .thenComparingInt(box -> orderData(box).fragmentIndex()));
            Map<DataComponentPatch, List<ItemStack>> ordinaryGroups = new LinkedHashMap<>();
            List<BigItemStack> remainderOutput = new ArrayList<>();
            Contents expectedOrder = new Contents();
            PackageOrderWithCrafts context = null;
            boolean conflictingContext = false;
            for (ItemStack source : sorted) {
                Contents expectedSource = new Contents();
                expectedSource.add(api, inspect(api, source), 1);
                expectedOrder.add(api, inspect(api, source), 1);
                Object split = api.splitPackage.invoke(null, source.copyWithCount(1));
                if (!(split instanceof List<?> packages) || packages.isEmpty()) {
                    throw new IllegalStateException("Resource split produced no packages");
                }
                Contents actualSource = new Contents();
                for (Object value : packages) {
                    if (!(value instanceof ItemStack box) || !PackageItem.isPackage(box) || box.getCount() != 1) {
                        throw new IllegalStateException("Resource split produced an invalid package");
                    }
                    Inspection inspection = inspect(api, box);
                    actualSource.add(api, inspection, 1);
                    PackageItem.PackageOrderData data = box.get(AllDataComponents.PACKAGE_ORDER_DATA);
                    PackageOrderWithCrafts sourceContext = data == null ? null : data.orderContext();
                    if (sourceContext != null) {
                        if (context != null && !context.equals(sourceContext)) conflictingContext = true;
                        context = sourceContext;
                    }
                    if (inspection.hasResources()) {
                        hasResources = true;
                        if (!inspection.canonical || !inspection.items.isEmpty()) {
                            throw new IllegalStateException("Resource split left mixed or noncanonical cargo");
                        }
                        // Do not reconstruct resource payloads or copy item contents over resource components.
                        remainderOutput.add(new BigItemStack(box.copyWithCount(1), 1));
                    } else if (!inspection.items.isEmpty()) {
                        ordinaryGroups.computeIfAbsent(metadataOf(box), ignored -> new ArrayList<>())
                                .add(box.copyWithCount(1));
                    }
                }
                expectedSource.requireMatches(actualSource);
            }

            // Even a conflicting resource payload with an empty safe read must first pass the
            // upstream split validator. Only validated, truly item-only orders use the old path.
            if (!hasResources) return NOT_HANDLED;
            if (conflictingContext) throw new IllegalStateException("Conflicting resource order contexts");
            List<PackageOrderWithCrafts.CraftingEntry> crafts = context == null ? List.of() : context.orderedCrafts();
            int[] remainingCrafts = new int[crafts.size()];
            for (int i = 0; i < crafts.size(); i++) {
                if (crafts.get(i).count() < 0) throw new IllegalStateException("Negative crafting count");
                remainingCrafts[i] = crafts.get(i).count();
            }
            List<BigItemStack> recipeOutput = new ArrayList<>();
            for (Map.Entry<DataComponentPatch, List<ItemStack>> group : ordinaryGroups.entrySet()) {
                Contents expected = new Contents();
                InventorySummary available = new InventorySummary();
                for (ItemStack box : group.getValue()) {
                    Inspection inspection = inspect(api, box);
                    expected.add(api, inspection, 1);
                    for (ItemStack item : inspection.items) available.add(item.copy());
                }
                // Allocate the original request budget across metadata groups, never once per group.
                InventorySummary allocation = new InventorySummary();
                for (BigItemStack entry : available.getStacks()) allocation.add(entry.stack.copy(), entry.count);
                List<PackageOrderWithCrafts.CraftingEntry> groupCrafts = new ArrayList<>();
                for (int i = 0; i < crafts.size(); i++) {
                    PackageOrderWithCrafts.CraftingEntry craft = crafts.get(i);
                    InventorySummary needed = new InventorySummary();
                    for (BigItemStack ingredient : craft.pattern().stacks()) {
                        if (ingredient.stack.isEmpty() || ingredient.count <= 0) continue;
                        if (((Optional<?>) api.findType.invoke(null, ingredient.stack)).isPresent()) {
                            throw new IllegalStateException("Resource ingredients are not ordinary crafting items");
                        }
                        needed.add(ingredient.stack.copy(), ingredient.count);
                    }
                    if (needed.getStacks().isEmpty()) continue;
                    int count = remainingCrafts[i];
                    for (BigItemStack ingredient : needed.getStacks()) {
                        count = Math.min(count, allocation.getCountOf(ingredient.stack) / ingredient.count);
                    }
                    if (count <= 0) continue;
                    groupCrafts.add(new PackageOrderWithCrafts.CraftingEntry(craft.pattern(), count));
                    remainingCrafts[i] -= count;
                    for (BigItemStack ingredient : needed.getStacks()) {
                        allocation.add(ingredient.stack, -Math.multiplyExact(ingredient.count, count));
                    }
                }
                String address = PackageItem.getAddress(group.getValue().getFirst());
                List<BigItemStack> repacked = new ArrayList<>(groupCrafts.isEmpty()
                        ? batchHelper.repackPlainMaterials(available, address, orderId)
                        : batchHelper.repackBasedOnRecipes(available,
                                new PackageOrderWithCrafts(new PackageOrder(List.of()), groupCrafts),
                                address, orderId, random));
                // The batch helper's recipe path tracks recipe ingredients only. Preserve any unrelated
                // ordinary cargo too, rather than changing that helper's established item-only behavior.
                Contents actual = contentsOf(api, repacked);
                InventorySummary omitted = expected.items.missingFrom(actual.items);
                repacked.addAll(batchHelper.repackPlainMaterials(omitted, address, orderId));
                for (BigItemStack entry : repacked) entry.stack.applyComponents(group.getKey());
                expected.requireMatches(contentsOf(api, repacked));
                for (BigItemStack entry : repacked) {
                    PackageItem.PackageOrderData data = entry.stack.get(AllDataComponents.PACKAGE_ORDER_DATA);
                    if (data != null && data.orderContext() != null && !data.orderContext().orderedCrafts().isEmpty()) {
                        recipeOutput.add(entry);
                    } else {
                        remainderOutput.add(entry);
                    }
                }
            }
            // splitPackage assigns a new fragment chain per source package. Item portions no longer
            // occupy those slots after batching, so resequence the resource/remainder chain together.
            // Recipe parcels retain the batch helper's independent full-pattern/count contexts.
            for (int i = 0; i < remainderOutput.size(); i++) {
                PackageItem.setOrder(remainderOutput.get(i).stack, orderId, 0, true, i,
                        i == remainderOutput.size() - 1, i == remainderOutput.size() - 1 ? context : null);
            }
            recipeOutput.addAll(remainderOutput);
            if (recipeOutput.isEmpty()) throw new IllegalStateException("Resource order produced no output");
            expectedOrder.requireMatches(contentsOf(api, recipeOutput));
            return new Result(true, List.copyOf(recipeOutput));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (FAILURE_LOGGED.compareAndSet(false, true)) {
                CreateImp.LOGGER.warn("Resource repackage preparation failed; original packages were retained", failure);
            }
            return REJECTED;
        }
    }

    private static PackageItem.PackageOrderData orderData(ItemStack box) {
        PackageItem.PackageOrderData data = box.get(AllDataComponents.PACKAGE_ORDER_DATA);
        if (data == null) throw new IllegalStateException("Missing resource fragment order data");
        return data;
    }

    private static DataComponentPatch metadataOf(ItemStack box) {
        boolean emptyAddress = PackageItem.getAddress(box).isEmpty();
        return box.getComponentsPatch().forget(component -> component == AllDataComponents.PACKAGE_CONTENTS
                || component == AllDataComponents.PACKAGE_ORDER_DATA
                || component == AllDataComponents.PACKAGE_ADDRESS && emptyAddress);
    }

    private record Inspection(List<?> resources, List<ItemStack> items, boolean canonical) {
        boolean hasResources() { return !resources.isEmpty(); }
    }

    private static Inspection inspect(Bridge api, ItemStack box) throws ReflectiveOperationException {
        Object inspection = api.inspectPackage.invoke(null, box.copyWithCount(1));
        List<?> resources = (List<?>) api.resources.invoke(inspection);
        List<?> items = (List<?>) api.ordinaryItems.invoke(inspection);
        List<ItemStack> copies = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof ItemStack stack)) throw new IllegalStateException("Invalid ordinary item");
            copies.add(stack.copy());
        }
        return new Inspection(resources, copies, Boolean.TRUE.equals(api.canonical.invoke(inspection)));
    }

    private static Contents contentsOf(Bridge api, List<BigItemStack> packages) throws ReflectiveOperationException {
        Contents contents = new Contents();
        for (BigItemStack entry : packages) {
            if (entry.count <= 0 || !PackageItem.isPackage(entry.stack) || entry.stack.getCount() != 1) {
                throw new IllegalStateException("Invalid repacked package");
            }
            contents.add(api, inspect(api, entry.stack), entry.count);
        }
        return contents;
    }

    /** Exact component-sensitive item counts, independent of resource-key normalization in InventorySummary. */
    private static final class ItemTotals {
        private final List<ItemStack> keys = new ArrayList<>();
        private final List<Long> amounts = new ArrayList<>();

        private void add(ItemStack item, long amount) {
            if (item.isEmpty() || amount == 0) return;
            for (int i = 0; i < keys.size(); i++) {
                if (!ItemStack.isSameItemSameComponents(keys.get(i), item)) continue;
                amounts.set(i, Math.addExact(amounts.get(i), amount));
                return;
            }
            keys.add(item.copyWithCount(1));
            amounts.add(amount);
        }

        private long count(ItemStack item) {
            for (int i = 0; i < keys.size(); i++) {
                if (ItemStack.isSameItemSameComponents(keys.get(i), item)) return amounts.get(i);
            }
            return 0;
        }

        private InventorySummary missingFrom(ItemTotals actual) {
            InventorySummary missing = new InventorySummary();
            for (int i = 0; i < actual.keys.size(); i++) {
                if (actual.amounts.get(i) > count(actual.keys.get(i))) {
                    throw new IllegalStateException("Repack created ordinary items");
                }
            }
            for (int i = 0; i < keys.size(); i++) {
                long difference = amounts.get(i) - actual.count(keys.get(i));
                if (difference > 0) missing.add(keys.get(i).copy(), Math.toIntExact(difference));
            }
            return missing;
        }

        private boolean matches(ItemTotals other) {
            if (keys.size() != other.keys.size()) return false;
            for (int i = 0; i < keys.size(); i++) if (amounts.get(i) != other.count(keys.get(i))) return false;
            return true;
        }
    }

    private static final class Contents {
        private final Map<Object, Long> resources = new HashMap<>();
        private final ItemTotals items = new ItemTotals();

        private void add(Bridge api, Inspection inspection, int copies) throws ReflectiveOperationException {
            for (ItemStack item : inspection.items) items.add(item, Math.multiplyExact((long) item.getCount(), copies));
            for (Object resource : inspection.resources) {
                ItemStack key = (ItemStack) api.resourceKey.invoke(resource);
                Object identity = ((Optional<?>) api.keyOf.invoke(null, key)).orElseThrow();
                long amount = Math.multiplyExact(((Number) api.resourceAmount.invoke(resource)).longValue(), copies);
                resources.merge(identity, amount, Math::addExact);
            }
        }

        private void requireMatches(Contents actual) {
            if (!resources.equals(actual.resources) || !items.matches(actual.items)) {
                throw new IllegalStateException("Resource repackage changed cargo contents");
            }
        }
    }

    private record Bridge(Method splitPackage, Method inspectPackage, Method findType, Method keyOf,
                          Method resources, Method ordinaryItems, Method canonical,
                          Method resourceKey, Method resourceAmount) {}

    private static final class ApiHolder {
        private static final Bridge API = load();

        private static Bridge load() {
            if (!FluidLogisticsVersionBridge.hasComponentPackages()) return null;
            try {
                Class<?> resources = Class.forName("com.yision.fluidlogistics.api.packager.PackageResources", false, FluidResourceRepackCompat.class.getClassLoader());
                Class<?> inspection = Class.forName("com.yision.fluidlogistics.api.packager.PackageInspection", false, FluidResourceRepackCompat.class.getClassLoader());
                Class<?> resource = Class.forName("com.yision.fluidlogistics.api.packager.PackageResource", false, FluidResourceRepackCompat.class.getClassLoader());
                return new Bridge(resources.getMethod("splitPackage", ItemStack.class),
                        resources.getMethod("inspectPackage", ItemStack.class),
                        resources.getMethod("findType", ItemStack.class), resources.getMethod("keyOf", ItemStack.class),
                        inspection.getMethod("resources"), inspection.getMethod("ordinaryItems"),
                        inspection.getMethod("canonical"), resource.getMethod("key"), resource.getMethod("amount"));
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
    }
}
