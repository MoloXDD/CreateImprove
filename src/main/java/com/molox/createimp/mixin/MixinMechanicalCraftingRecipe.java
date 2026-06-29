package com.molox.createimp.mixin;

import com.molox.createimp.block.batch_mechanical_crafter.BatchCraftingInput;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MechanicalCraftingRecipe.class, remap = false)
public abstract class MixinMechanicalCraftingRecipe {

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void createimp$matches(CraftingInput input, Level world, CallbackInfoReturnable<Boolean> ci) {
        if (!(input instanceof BatchCraftingInput)) return;

        ShapedRecipe self = (ShapedRecipe) (Object) this;
        MechanicalCraftingRecipe selfMcr = (MechanicalCraftingRecipe) (Object) this;

        int recipeWidth = self.getWidth();
        int recipeHeight = self.getHeight();

        if (input.width() < recipeWidth || input.height() < recipeHeight) {
            ci.setReturnValue(false);
            return;
        }

        if (selfMcr.acceptsMirrored()) {
            ci.setReturnValue(
                    createimp$matchesAt(input, self, false) ||
                            createimp$matchesAt(input, self, true)
            );
        } else {
            ci.setReturnValue(createimp$matchesAt(input, self, false));
        }
    }

    private boolean createimp$matchesAt(CraftingInput input, ShapedRecipe recipe, boolean mirrored) {
        for (int offsetX = 0; offsetX <= input.width() - recipe.getWidth(); offsetX++) {
            for (int offsetY = 0; offsetY <= input.height() - recipe.getHeight(); offsetY++) {
                if (createimp$matchesSpecific(input, recipe, offsetX, offsetY, mirrored))
                    return true;
            }
        }
        return false;
    }

    private boolean createimp$matchesSpecific(CraftingInput input, ShapedRecipe recipe,
                                              int offsetX, int offsetY, boolean mirrored) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        int recipeWidth = recipe.getWidth();
        int recipeHeight = recipe.getHeight();
        for (int i = 0; i < input.width(); i++) {
            for (int j = 0; j < input.height(); j++) {
                int k = i - offsetX;
                int l = j - offsetY;
                Ingredient ingredient = Ingredient.EMPTY;
                if (k >= 0 && l >= 0 && k < recipeWidth && l < recipeHeight) {
                    int index = mirrored
                            ? (recipeWidth - 1 - k) + l * recipeWidth
                            : k + l * recipeWidth;
                    ingredient = ingredients.get(index);
                }
                if (!ingredient.test(input.getItem(i + j * input.width())))
                    return false;
            }
        }
        return true;
    }
}