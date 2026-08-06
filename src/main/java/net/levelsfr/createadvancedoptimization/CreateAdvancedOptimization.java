package net.levelsfr.createadvancedoptimization;

import com.mojang.logging.LogUtils;
import net.levelsfr.createadvancedoptimization.compatibility.CreateCompatibility;
import net.levelsfr.createadvancedoptimization.config.CAOServerConfig;
import net.levelsfr.createadvancedoptimization.diagnostics.OptimizationStats;
import net.levelsfr.createadvancedoptimization.diagnostics.packages.PackageEntityMonitor;
import net.levelsfr.createadvancedoptimization.diagnostics.profiler.CreateProfilerManager;
import net.levelsfr.createadvancedoptimization.event.ServerEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(CreateAdvancedOptimization.MODID)
public final class CreateAdvancedOptimization {

    public static final String MODID = "createadvancedoptimization";
    public static final String MOD_NAME = "Create: Advanced Optimization";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static String modVersion = "unknown";

    public CreateAdvancedOptimization(IEventBus modEventBus, ModContainer modContainer) {
        modVersion = modContainer.getModInfo().getVersion().toString();
        modContainer.registerConfig(ModConfig.Type.SERVER, CAOServerConfig.SPEC);
        modEventBus.addListener(this::onCommonSetup);

        CreateCompatibility.validateOrThrow();
        OptimizationStats.reset();
        CreateProfilerManager.resetAll();
        PackageEntityMonitor.getInstance().reset(null);

        NeoForge.EVENT_BUS.register(new ServerEvents());
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("{} v{} initialized for Create {}", MOD_NAME, modVersion, CreateCompatibility.REQUIRED_CREATE_VERSION);
    }

    public static String getModVersion() {
        return modVersion;
    }
}
