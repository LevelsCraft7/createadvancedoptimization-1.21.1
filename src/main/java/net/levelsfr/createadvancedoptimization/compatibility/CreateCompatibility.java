package net.levelsfr.createadvancedoptimization.compatibility;

import net.levelsfr.createadvancedoptimization.CreateAdvancedOptimization;
import net.neoforged.fml.ModList;

public final class CreateCompatibility {

    public static final String CREATE_MOD_ID = "create";
    public static final String REQUIRED_CREATE_VERSION = "6.0.10";

    private CreateCompatibility() {
    }

    public static void validateOrThrow() {
        String loadedVersion = getLoadedCreateVersion();
        if ("<missing>".equals(loadedVersion)) {
            throw new IllegalStateException("Create: Advanced Optimization requires Create " + REQUIRED_CREATE_VERSION + ", but Create is not loaded.");
        }

        if (!REQUIRED_CREATE_VERSION.equals(loadedVersion)) {
            throw new IllegalStateException("Create: Advanced Optimization requires Create " + REQUIRED_CREATE_VERSION + " exactly, but found " + loadedVersion + ".");
        }
    }

    public static String getLoadedCreateVersion() {
        return ModList.get()
            .getModContainerById(CREATE_MOD_ID)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("<missing>");
    }

    public static boolean isCompatible() {
        return REQUIRED_CREATE_VERSION.equals(getLoadedCreateVersion());
    }
}
