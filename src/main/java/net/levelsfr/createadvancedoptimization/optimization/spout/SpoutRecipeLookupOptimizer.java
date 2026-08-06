package net.levelsfr.createadvancedoptimization.optimization.spout;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import java.util.Map;
import java.util.Optional;
import net.levelsfr.createadvancedoptimization.config.CAOServerConfig;
import net.levelsfr.createadvancedoptimization.diagnostics.OptimizationStats;
import net.levelsfr.createadvancedoptimization.diagnostics.profiler.CreateProfilerManager;
import net.levelsfr.createadvancedoptimization.diagnostics.profiler.ProfiledSection;
import net.levelsfr.createadvancedoptimization.util.SignatureUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

public final class SpoutRecipeLookupOptimizer {

    private static final int NO_OFFICIAL_REQUIRED_AMOUNT = Integer.MIN_VALUE;

    private SpoutRecipeLookupOptimizer() {
    }

    public static boolean canItemBeFilled(Object spout, Level level, ItemStack stack) {
        if (!isCacheEnabled()) {
            return originalCanItemBeFilled(level, stack);
        }

        OptimizationStats.recordSpoutLookup();
        Map<SpoutRecipeLookupKey, SpoutRecipeLookupResult> cache = ((SpoutRecipeCacheHolder) spout).cao$getSpoutRecipeCache();
        SpoutRecipeLookupKey key = new SpoutRecipeLookupKey(level.dimension(), stack, null, SignatureUtil.recipeEpoch(level));

        SpoutRecipeLookupResult cached = cache.get(key);
        if (cached != null) {
            OptimizationStats.recordSpoutHit();
            if (!cached.officialFillable()) {
                OptimizationStats.recordSpoutNegativeResult();
            }
            return cached.officialFillable() || GenericItemFilling.canItemBeFilled(level, stack);
        }

        OptimizationStats.recordSpoutMiss();
        boolean officialFillable = lookupOfficialCanFill(level, stack);
        if (!officialFillable) {
            OptimizationStats.recordSpoutNegativeResult();
        }
        cache.put(key, new SpoutRecipeLookupResult(officialFillable, null));
        return officialFillable || GenericItemFilling.canItemBeFilled(level, stack);
    }

    public static int getRequiredAmountForItem(Object spout, Level level, ItemStack stack, FluidStack fluidStack) {
        if (!isCacheEnabled()) {
            return originalGetRequiredAmount(level, stack, fluidStack);
        }

        OptimizationStats.recordSpoutLookup();
        Map<SpoutRecipeLookupKey, SpoutRecipeLookupResult> cache = ((SpoutRecipeCacheHolder) spout).cao$getSpoutRecipeCache();
        SpoutRecipeLookupKey key = new SpoutRecipeLookupKey(level.dimension(), stack, fluidStack, SignatureUtil.recipeEpoch(level));

        SpoutRecipeLookupResult cached = cache.get(key);
        if (cached != null && cached.officialRequiredAmount() != null) {
            OptimizationStats.recordSpoutHit();
            if (cached.officialRequiredAmount() == NO_OFFICIAL_REQUIRED_AMOUNT) {
                OptimizationStats.recordSpoutNegativeResult();
                return GenericItemFilling.getRequiredAmountForItem(level, stack, fluidStack);
            }
            return cached.officialRequiredAmount();
        }

        OptimizationStats.recordSpoutMiss();
        Integer officialAmount = lookupOfficialRequiredAmount(level, stack, fluidStack);
        if (officialAmount == null) {
            OptimizationStats.recordSpoutNegativeResult();
        }
        cache.put(key, new SpoutRecipeLookupResult(officialAmount != null, officialAmount == null ? NO_OFFICIAL_REQUIRED_AMOUNT : officialAmount));
        return officialAmount != null ? officialAmount : GenericItemFilling.getRequiredAmountForItem(level, stack, fluidStack);
    }

    private static boolean isCacheEnabled() {
        return CAOServerConfig.GENERAL_ENABLED.get() && CAOServerConfig.SPOUT_RECIPE_CACHE_ENABLED.get();
    }

    private static boolean originalCanItemBeFilled(Level level, ItemStack stack) {
        return FillingBySpout.canItemBeFilled(level, stack);
    }

    private static int originalGetRequiredAmount(Level level, ItemStack stack, FluidStack fluidStack) {
        return FillingBySpout.getRequiredAmountForItem(level, stack, fluidStack);
    }

    private static boolean lookupOfficialCanFill(Level level, ItemStack stack) {
        long start = CreateProfilerManager.begin(ProfiledSection.FILLING_CAN_ITEM_BE_FILLED);
        try {
            SingleRecipeInput input = new SingleRecipeInput(stack);
            Optional<RecipeHolder<FillingRecipe>> sequenced = SequencedAssemblyRecipe.getRecipe(level, input, AllRecipeTypes.FILLING.getType(), FillingRecipe.class);
            if (sequenced.isPresent()) {
                return true;
            }
            return AllRecipeTypes.FILLING.find(input, level).isPresent();
        } finally {
            CreateProfilerManager.end(ProfiledSection.FILLING_CAN_ITEM_BE_FILLED, start);
        }
    }

    private static Integer lookupOfficialRequiredAmount(Level level, ItemStack stack, FluidStack fluidStack) {
        long start = CreateProfilerManager.begin(ProfiledSection.FILLING_GET_REQUIRED_AMOUNT);
        try {
            SingleRecipeInput input = new SingleRecipeInput(stack);
            Optional<RecipeHolder<FillingRecipe>> sequenced = SequencedAssemblyRecipe.getRecipe(
                level,
                input,
                AllRecipeTypes.FILLING.getType(),
                FillingRecipe.class,
                recipe -> recipe.value().matches(input, level) && recipe.value().getRequiredFluid().test(fluidStack)
            );
            if (sequenced.isPresent()) {
                SizedFluidIngredient ingredient = sequenced.get().value().getRequiredFluid();
                if (ingredient.ingredient().test(fluidStack)) {
                    return ingredient.amount();
                }
            }

            for (RecipeHolder<Recipe<SingleRecipeInput>> recipeHolder : level.getRecipeManager().getRecipesFor(AllRecipeTypes.FILLING.getType(), input, level)) {
                FillingRecipe fillingRecipe = (FillingRecipe) recipeHolder.value();
                SizedFluidIngredient requiredFluid = fillingRecipe.getRequiredFluid();
                if (requiredFluid.ingredient().test(fluidStack)) {
                    return requiredFluid.amount();
                }
            }

            return null;
        } finally {
            CreateProfilerManager.end(ProfiledSection.FILLING_GET_REQUIRED_AMOUNT, start);
        }
    }
}
