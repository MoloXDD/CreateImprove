package com.molox.createimp.block.batch_mechanical_crafter;

import com.google.common.base.Predicates;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingInput;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.Pointing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.FireworkRocketRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class BatchRecipeGridHandler {

    public static List<BatchMechanicalCrafterBlockEntity> getAllCraftersOfChain(
            BatchMechanicalCrafterBlockEntity root) {
        return getAllCraftersOfChainIf(root, Predicates.alwaysTrue());
    }

    public static List<BatchMechanicalCrafterBlockEntity> getAllCraftersOfChainIf(
            BatchMechanicalCrafterBlockEntity root,
            Predicate<BatchMechanicalCrafterBlockEntity> test) {
        return getAllCraftersOfChainIf(root, test, false);
    }

    public static List<BatchMechanicalCrafterBlockEntity> getAllCraftersOfChainIf(
            BatchMechanicalCrafterBlockEntity root,
            Predicate<BatchMechanicalCrafterBlockEntity> test,
            boolean poweredStart) {
        ArrayList<BatchMechanicalCrafterBlockEntity> crafters = new ArrayList<>();
        ArrayList<Pair<BatchMechanicalCrafterBlockEntity, BatchMechanicalCrafterBlockEntity>> frontier = new ArrayList<>();
        HashSet<BatchMechanicalCrafterBlockEntity> visited = new HashSet<>();
        frontier.add(Pair.of(root, null));
        boolean empty = false;
        boolean allEmpty = true;
        while (!frontier.isEmpty()) {
            Pair<BatchMechanicalCrafterBlockEntity, BatchMechanicalCrafterBlockEntity> pair = frontier.remove(0);
            BatchMechanicalCrafterBlockEntity current = pair.getKey();
            BatchMechanicalCrafterBlockEntity last = pair.getValue();
            if (visited.contains(current))
                return null;
            if (!test.test(current))
                empty = true;
            else
                allEmpty = false;
            crafters.add(current);
            visited.add(current);
            BatchMechanicalCrafterBlockEntity target = getTargetingCrafter(current);
            if (target != last && target != null)
                frontier.add(Pair.of(target, current));
            for (BatchMechanicalCrafterBlockEntity preceding : getPrecedingCrafters(current)) {
                if (preceding == last)
                    continue;
                frontier.add(Pair.of(preceding, current));
            }
        }
        return (empty && !poweredStart) || allEmpty ? null : crafters;
    }

    public static BatchMechanicalCrafterBlockEntity getTargetingCrafter(
            BatchMechanicalCrafterBlockEntity crafter) {
        var state = crafter.getBlockState();
        if (!BatchCrafterHelper.isBatchCrafter(state))
            return null;
        BlockPos targetPos = crafter.getBlockPos()
                .relative(BatchMechanicalCrafterBlock.getTargetDirection(state));
        BatchMechanicalCrafterBlockEntity targetBE = BatchCrafterHelper.getCrafter(crafter.getLevel(), targetPos);
        if (targetBE == null)
            return null;
        var targetState = targetBE.getBlockState();
        if (!BatchCrafterHelper.isBatchCrafter(targetState))
            return null;
        if (state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING) !=
                targetState.getValue(HorizontalKineticBlock.HORIZONTAL_FACING))
            return null;
        return targetBE;
    }

    public static List<BatchMechanicalCrafterBlockEntity> getPrecedingCrafters(
            BatchMechanicalCrafterBlockEntity crafter) {
        BlockPos pos = crafter.getBlockPos();
        Level world = crafter.getLevel();
        ArrayList<BatchMechanicalCrafterBlockEntity> crafters = new ArrayList<>();
        var blockState = crafter.getBlockState();
        if (!BatchCrafterHelper.isBatchCrafter(blockState))
            return crafters;
        Direction blockFacing = blockState.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
        Direction blockPointing = BatchMechanicalCrafterBlock.getTargetDirection(blockState);
        for (Direction facing : Iterate.directions) {
            BlockPos neighbourPos = pos.relative(facing);
            var neighbourState = world.getBlockState(neighbourPos);
            if (blockFacing.getAxis() == facing.getAxis()) continue;
            if (blockPointing == facing) continue;
            if (!BatchCrafterHelper.isBatchCrafter(neighbourState)) continue;
            if (BatchMechanicalCrafterBlock.getTargetDirection(neighbourState) != facing.getOpposite()) continue;
            if (blockFacing != neighbourState.getValue(HorizontalKineticBlock.HORIZONTAL_FACING)) continue;
            BatchMechanicalCrafterBlockEntity be = BatchCrafterHelper.getCrafter(world, neighbourPos);
            if (be == null) continue;
            crafters.add(be);
        }
        return crafters;
    }

    public static ItemStack tryToApplyRecipe(Level world, GroupedItems items) {
        items.calcStats();
        CraftingInput craftingInput = buildCraftingInput(items);
        ItemStack result = null;
        RegistryAccess registryAccess = world.registryAccess();
        if (((Boolean) AllConfigs.server().recipes.allowRegularCraftingInCrafter.get())) {
            result = world.getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, craftingInput, world)
                    .filter(r -> isRecipeAllowed(r, craftingInput))
                    .map(r -> ((CraftingRecipe) r.value()).assemble(craftingInput, registryAccess))
                    .orElse(null);
        }
        if (result == null) {
            result = AllRecipeTypes.MECHANICAL_CRAFTING.find(craftingInput, world)
                    .map(r -> r.value().assemble(craftingInput, registryAccess))
                    .orElse(null);
        }
        return result;
    }

    private static CraftingInput buildCraftingInput(GroupedItems items) {
        ArrayList<ItemStack> list = new ArrayList<>(items.width * items.height);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int y = 0; y < items.height; y++) {
            for (int x = 0; x < items.width; x++) {
                int xp = x + items.minX;
                int yp = y + items.minY;
                ItemStack stack = items.grid.get(Pair.of(xp, yp));
                if (stack == null || stack.isEmpty()) continue;
                minX = Math.min(minX, xp);
                maxX = Math.max(maxX, xp);
                minY = Math.min(minY, yp);
                maxY = Math.max(maxY, yp);
            }
        }
        if (minX == Integer.MAX_VALUE)
            return CraftingInput.of(1, 1, List.of(ItemStack.EMPTY));
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                ItemStack stack = items.grid.get(Pair.of(x + minX, maxY - y));
                list.add(stack == null ? ItemStack.EMPTY : stack.copy());
            }
        }
        return CraftingInput.of(w, h, list);
    }

    public static boolean isRecipeAllowed(RecipeHolder<CraftingRecipe> recipe, CraftingInput craftingInput) {
        if (recipe.value() instanceof FireworkRocketRecipe) {
            int numItems = 0;
            for (int i = 0; i < craftingInput.size(); i++) {
                if (!craftingInput.getItem(i).isEmpty()) numItems++;
            }
            if (numItems > (Integer) AllConfigs.server().recipes.maxFireworkIngredientsInCrafter.get())
                return false;
        }
        return !AllRecipeTypes.shouldIgnoreInAutomation(recipe);
    }

    public static class GroupedItems {
        public Map<Pair<Integer, Integer>, ItemStack> grid = new HashMap<>();
        public int minX, minY, maxX, maxY, width, height;
        boolean statsReady;

        public GroupedItems() {}

        public GroupedItems(ItemStack stack) {
            this.grid.put(Pair.of(0, 0), stack);
        }

        public void mergeOnto(GroupedItems other, Pointing pointing) {
            int xOffset = pointing == Pointing.LEFT ? 1 : (pointing == Pointing.RIGHT ? -1 : 0);
            int yOffset = pointing == Pointing.DOWN ? 1 : (pointing == Pointing.UP ? -1 : 0);
            this.grid.forEach((pair, stack) ->
                    other.grid.put(Pair.of(pair.getKey() + xOffset, pair.getValue() + yOffset), stack));
            other.statsReady = false;
        }

        public void write(CompoundTag nbt, HolderLookup.Provider registries) {
            ListTag gridNBT = new ListTag();
            this.grid.forEach((pair, stack) -> {
                CompoundTag entry = new CompoundTag();
                entry.putInt("x", pair.getKey());
                entry.putInt("y", pair.getValue());
                entry.put("item", stack.saveOptional(registries));
                gridNBT.add(entry);
            });
            nbt.put("Grid", (Tag) gridNBT);
        }

        public static GroupedItems read(CompoundTag nbt, HolderLookup.Provider registries) {
            GroupedItems items = new GroupedItems();
            ListTag gridNBT = nbt.getList("Grid", 10);
            gridNBT.forEach(inbt -> {
                CompoundTag entry = (CompoundTag) inbt;
                int x = entry.getInt("x");
                int y = entry.getInt("y");
                ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("item"));
                items.grid.put(Pair.of(x, y), stack);
            });
            return items;
        }

        public void calcStats() {
            if (this.statsReady) return;
            this.statsReady = true;
            this.minX = 0; this.minY = 0; this.maxX = 0; this.maxY = 0;
            for (Pair<Integer, Integer> pair : this.grid.keySet()) {
                int x = pair.getKey(), y = pair.getValue();
                this.minX = Math.min(this.minX, x);
                this.minY = Math.min(this.minY, y);
                this.maxX = Math.max(this.maxX, x);
                this.maxY = Math.max(this.maxY, y);
            }
            this.width = this.maxX - this.minX + 1;
            this.height = this.maxY - this.minY + 1;
        }

        public boolean onlyEmptyItems() {
            for (ItemStack stack : this.grid.values())
                if (!stack.isEmpty()) return false;
            return true;
        }
    }
}