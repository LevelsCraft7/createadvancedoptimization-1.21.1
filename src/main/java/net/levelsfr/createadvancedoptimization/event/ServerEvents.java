package net.levelsfr.createadvancedoptimization.event;

import net.levelsfr.createadvancedoptimization.command.CAOCommands;
import net.levelsfr.createadvancedoptimization.compatibility.CreateCompatibility;
import net.levelsfr.createadvancedoptimization.config.CAOServerConfig;
import net.levelsfr.createadvancedoptimization.diagnostics.OptimizationStats;
import net.levelsfr.createadvancedoptimization.diagnostics.packages.PackageEntityMonitor;
import net.levelsfr.createadvancedoptimization.diagnostics.profiler.CreateProfilerManager;
import net.levelsfr.createadvancedoptimization.optimization.processing.ProcessingMemoizationService;
import net.levelsfr.createadvancedoptimization.util.SignatureUtil;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ServerEvents {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CAOCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        CAOServerConfig.refreshCachedState();
        CreateCompatibility.validateOrThrow();
        SignatureUtil.invalidateRecipeCaches();
        ProcessingMemoizationService.invalidateAll();
        OptimizationStats.reset();
        PackageEntityMonitor.getInstance().reset(event.getServer());
        CreateProfilerManager.resetAll();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        ProcessingMemoizationService.invalidateAll();
        OptimizationStats.reset();
        PackageEntityMonitor.getInstance().reset(null);
        CreateProfilerManager.resetAll();
    }

    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        SignatureUtil.invalidateRecipeCaches();
        ProcessingMemoizationService.invalidateAll();
    }

    @SubscribeEvent
    public void onServerTickPre(ServerTickEvent.Pre event) {
        CreateProfilerManager.onServerTickStart(event.getServer());
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        CreateProfilerManager.onServerTickEnd(event.getServer());
        if (CreateProfilerManager.shouldAutoStop(event.getServer())) {
            CreateProfilerManager.ProfileSession session = CreateProfilerManager.stop();
            if (session != null) {
                CAOCommands.exportCompletedSession(event.getServer(), session);
            }
        }
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        PackageEntityMonitor.getInstance().onEntityJoin(event.getEntity(), event.getLevel(), event.loadedFromDisk());
    }

    @SubscribeEvent
    public void onEntityLeave(EntityLeaveLevelEvent event) {
        PackageEntityMonitor.getInstance().onEntityLeave(event.getEntity(), event.getLevel());
    }
}
