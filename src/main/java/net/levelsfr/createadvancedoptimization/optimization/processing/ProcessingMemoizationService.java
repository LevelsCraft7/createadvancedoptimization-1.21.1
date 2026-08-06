package net.levelsfr.createadvancedoptimization.optimization.processing;

import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.levelsfr.createadvancedoptimization.config.CAOServerConfig;
import net.levelsfr.createadvancedoptimization.diagnostics.OptimizationStats;
import net.levelsfr.createadvancedoptimization.mixin.accessor.GroupedItemsAccessor;
import net.levelsfr.createadvancedoptimization.util.BoundedLruMap;
import net.levelsfr.createadvancedoptimization.util.SignatureUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.apache.commons.lang3.tuple.Pair;

public final class ProcessingMemoizationService {

    private static final Map<BasinMemoizationKey, BasinMemoizationEntry> BASIN_CACHE =
        new BoundedLruMap<>(64, ignored -> OptimizationStats.recordBasinEviction());
    private static final Map<CrafterMemoizationKey, CrafterMemoizationEntry> CRAFTER_CACHE =
        new BoundedLruMap<>(64, ignored -> OptimizationStats.recordCrafterEviction());

    private ProcessingMemoizationService() {
    }

    public static boolean isEnabled(Level level) {
        return CAOServerConfig.GENERAL_ENABLED.get()
            && CAOServerConfig.PROCESSING_RECIPE_MEMOIZATION_ENABLED.get()
            && level != null
            && level.getServer() != null;
    }

    public static BasinMemoizationKey buildBasinKey(BlockEntity operator, BlockEntity basin, Object createRecipeCacheKey) {
        Level level = basin.getLevel();
        if (!isEnabled(level)) {
            return null;
        }

        long startedAt = diagnosticsEnabled() ? System.nanoTime() : 0L;
        IItemHandler itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, basin.getBlockPos(), null);
        IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, basin.getBlockPos(), null);
        if (itemHandler == null && fluidHandler == null) {
            return null;
        }

        try {
            return new BasinMemoizationKey(
                level.dimension(),
                SignatureUtil.recipeEpoch(level),
                operator.getType(),
                createRecipeCacheKey,
                operator.getBlockPos().immutable(),
                basin.getBlockPos().immutable(),
                heatProxy(level, basin.getBlockPos()),
                snapshotItems(itemHandler),
                snapshotFluids(fluidHandler)
            );
        } finally {
            if (startedAt != 0L) {
                OptimizationStats.recordBasinKeyBuildNanos(System.nanoTime() - startedAt);
            }
        }
    }

    public static List<Recipe<?>> getCachedBasinRecipes(Level level, BasinMemoizationKey key) {
        if (!isEnabled(level) || key == null) {
            return null;
        }

        OptimizationStats.recordBasinLookup();
        BasinMemoizationEntry cached = BASIN_CACHE.get(key);
        if (cached != null && cached.serverTick == level.getServer().getTickCount()) {
            OptimizationStats.recordBasinHit();
            if (cached.recipes.isEmpty()) {
                OptimizationStats.recordBasinNegativeResult();
            }
            return new ArrayList<>(cached.recipes);
        }

        OptimizationStats.recordBasinMiss();
        return null;
    }

    public static void storeBasinRecipes(Level level, BasinMemoizationKey key, List<Recipe<?>> recipes) {
        if (!isEnabled(level) || key == null || recipes == null) {
            return;
        }

        if (recipes.isEmpty()) {
            OptimizationStats.recordBasinNegativeResult();
        }
        BASIN_CACHE.put(key, new BasinMemoizationEntry(level.getServer().getTickCount(), new ArrayList<>(recipes)));
        OptimizationStats.recordBasinMaxSize(BASIN_CACHE.size());
    }

    public static CrafterMemoizationKey buildCrafterKey(Level level, RecipeGridHandler.GroupedItems groupedItems) {
        if (!isEnabled(level)) {
            return null;
        }

        long startedAt = diagnosticsEnabled() ? System.nanoTime() : 0L;
        try {
            return new CrafterMemoizationKey(
                level.dimension(),
                SignatureUtil.recipeEpoch(level),
                snapshotGrid(((GroupedItemsAccessor) groupedItems).cao$getGrid())
            );
        } finally {
            if (startedAt != 0L) {
                OptimizationStats.recordCrafterKeyBuildNanos(System.nanoTime() - startedAt);
            }
        }
    }

    public static CachedCrafterResult getCachedCrafterResult(Level level, CrafterMemoizationKey key) {
        if (!isEnabled(level) || key == null) {
            return CachedCrafterResult.absent();
        }

        OptimizationStats.recordCrafterLookup();
        CrafterMemoizationEntry cached = CRAFTER_CACHE.get(key);
        if (cached != null && cached.serverTick == level.getServer().getTickCount()) {
            OptimizationStats.recordCrafterHit();
            if (cached.result == null) {
                OptimizationStats.recordCrafterNegativeResult();
            }
            return CachedCrafterResult.present(cached.result == null ? null : cached.result.copy());
        }

        OptimizationStats.recordCrafterMiss();
        return CachedCrafterResult.absent();
    }

    public static void storeCrafterResult(Level level, CrafterMemoizationKey key, ItemStack result) {
        if (!isEnabled(level) || key == null) {
            return;
        }

        if (result == null) {
            OptimizationStats.recordCrafterNegativeResult();
            CRAFTER_CACHE.put(key, new CrafterMemoizationEntry(level.getServer().getTickCount(), null));
            OptimizationStats.recordCrafterMaxSize(CRAFTER_CACHE.size());
            return;
        }

        CRAFTER_CACHE.put(key, new CrafterMemoizationEntry(level.getServer().getTickCount(), result.copy()));
        OptimizationStats.recordCrafterMaxSize(CRAFTER_CACHE.size());
    }

    public static void invalidateAll() {
        if (!BASIN_CACHE.isEmpty()) {
            BASIN_CACHE.clear();
        }
        if (!CRAFTER_CACHE.isEmpty()) {
            CRAFTER_CACHE.clear();
        }
    }

    private static BlockState heatProxy(Level level, BlockPos basinPos) {
        return level.getBlockState(basinPos.below());
    }

    private static boolean diagnosticsEnabled() {
        return CAOServerConfig.diagnosticsEnabledFast();
    }

    private static List<ItemStackFingerprint> snapshotItems(IItemHandler itemHandler) {
        if (itemHandler == null) {
            return List.of();
        }

        List<ItemStackFingerprint> items = new ArrayList<>(itemHandler.getSlots());
        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            items.add(new ItemStackFingerprint(slot, itemHandler.getStackInSlot(slot)));
        }
        return List.copyOf(items);
    }

    private static List<FluidStackFingerprint> snapshotFluids(IFluidHandler fluidHandler) {
        if (fluidHandler == null) {
            return List.of();
        }

        List<FluidStackFingerprint> fluids = new ArrayList<>(fluidHandler.getTanks());
        for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
            fluids.add(new FluidStackFingerprint(tank, fluidHandler.getFluidInTank(tank)));
        }
        return List.copyOf(fluids);
    }

    private static List<GridCellFingerprint> snapshotGrid(Map<Pair<Integer, Integer>, ItemStack> grid) {
        List<GridCellFingerprint> cells = new ArrayList<>(grid.size());
        for (Map.Entry<Pair<Integer, Integer>, ItemStack> entry : grid.entrySet()) {
            cells.add(new GridCellFingerprint(entry.getKey().getLeft(), entry.getKey().getRight(), entry.getValue()));
        }
        cells.sort(Comparator.comparingInt(GridCellFingerprint::x).thenComparingInt(GridCellFingerprint::y));
        return List.copyOf(cells);
    }

    public record CachedCrafterResult(boolean cached, ItemStack result) {
        private static CachedCrafterResult absent() {
            return new CachedCrafterResult(false, null);
        }

        private static CachedCrafterResult present(ItemStack result) {
            return new CachedCrafterResult(true, result);
        }
    }

    public record BasinMemoizationKey(
        ResourceKey<Level> dimension,
        int recipeEpoch,
        BlockEntityType<?> operatorType,
        Object createRecipeCacheKey,
        BlockPos operatorPos,
        BlockPos basinPos,
        BlockState heatState,
        List<ItemStackFingerprint> items,
        List<FluidStackFingerprint> fluids
    ) {
    }

    private record BasinMemoizationEntry(int serverTick, List<Recipe<?>> recipes) {
    }

    public record CrafterMemoizationKey(
        ResourceKey<Level> dimension,
        int recipeEpoch,
        List<GridCellFingerprint> grid
    ) {
    }

    private record CrafterMemoizationEntry(int serverTick, ItemStack result) {
    }

    public static final class ItemStackFingerprint {

        private final int slot;
        private final ItemStack stack;
        private final int hashCode;

        private ItemStackFingerprint(int slot, ItemStack stack) {
            this.slot = slot;
            this.stack = stack.copy();
            this.hashCode = 31 * slot + 31 * this.stack.getCount() + ItemStack.hashItemAndComponents(this.stack);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ItemStackFingerprint key)) {
                return false;
            }
            return slot == key.slot
                && stack.getCount() == key.stack.getCount()
                && ItemStack.isSameItemSameComponents(stack, key.stack);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    public static final class FluidStackFingerprint {

        private final int tank;
        private final FluidStack stack;
        private final int hashCode;

        private FluidStackFingerprint(int tank, FluidStack stack) {
            this.tank = tank;
            this.stack = stack.copy();
            this.hashCode = 31 * tank + 31 * this.stack.getAmount() + FluidStack.hashFluidAndComponents(this.stack);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FluidStackFingerprint key)) {
                return false;
            }
            return tank == key.tank
                && stack.getAmount() == key.stack.getAmount()
                && FluidStack.isSameFluidSameComponents(stack, key.stack);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    public static final class GridCellFingerprint {

        private final int x;
        private final int y;
        private final ItemStack stack;
        private final int hashCode;

        private GridCellFingerprint(int x, int y, ItemStack stack) {
            this.x = x;
            this.y = y;
            this.stack = stack.copy();
            int hash = 31 * x + y;
            hash = 31 * hash + 31 * this.stack.getCount() + ItemStack.hashItemAndComponents(this.stack);
            this.hashCode = hash;
        }

        private int x() {
            return x;
        }

        private int y() {
            return y;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GridCellFingerprint key)) {
                return false;
            }
            return x == key.x
                && y == key.y
                && stack.getCount() == key.stack.getCount()
                && ItemStack.isSameItemSameComponents(stack, key.stack);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
