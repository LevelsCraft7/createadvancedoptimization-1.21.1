package net.levelsfr.createadvancedoptimization.optimization.spout;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

public final class SpoutRecipeLookupKey {

    private final ResourceKey<Level> dimension;
    private final ItemStack itemSnapshot;
    private final FluidStack fluidSnapshot;
    private final int recipeEpoch;
    private final int hashCode;

    SpoutRecipeLookupKey(ResourceKey<Level> dimension, ItemStack stack, FluidStack fluidStack, int recipeEpoch) {
        this.dimension = dimension;
        this.itemSnapshot = stack.copy();
        this.fluidSnapshot = fluidStack == null ? null : fluidStack.copy();
        this.recipeEpoch = recipeEpoch;

        int hash = 31 * dimension.hashCode() + recipeEpoch;
        hash = 31 * hash + ItemStack.hashItemAndComponents(itemSnapshot) + itemSnapshot.getCount();
        if (fluidSnapshot != null) {
            hash = 31 * hash + FluidStack.hashFluidAndComponents(fluidSnapshot) + fluidSnapshot.getAmount();
        }
        this.hashCode = hash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SpoutRecipeLookupKey key)) {
            return false;
        }
        if (recipeEpoch != key.recipeEpoch || !dimension.equals(key.dimension)) {
            return false;
        }
        if (itemSnapshot.getCount() != key.itemSnapshot.getCount() || !ItemStack.isSameItemSameComponents(itemSnapshot, key.itemSnapshot)) {
            return false;
        }
        if (fluidSnapshot == null || key.fluidSnapshot == null) {
            return fluidSnapshot == null && key.fluidSnapshot == null;
        }
        return fluidSnapshot.getAmount() == key.fluidSnapshot.getAmount()
            && FluidStack.isSameFluidSameComponents(fluidSnapshot, key.fluidSnapshot);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
