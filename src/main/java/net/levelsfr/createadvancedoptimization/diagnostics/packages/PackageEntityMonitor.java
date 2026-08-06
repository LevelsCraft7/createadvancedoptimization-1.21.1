package net.levelsfr.createadvancedoptimization.diagnostics.packages;

import com.simibubi.create.content.logistics.box.PackageEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.levelsfr.createadvancedoptimization.CreateAdvancedOptimization;
import net.levelsfr.createadvancedoptimization.config.CAOServerConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class PackageEntityMonitor {

    private static final PackageEntityMonitor INSTANCE = new PackageEntityMonitor();

    private final Map<ResourceKey<Level>, Integer> activeByDimension = new LinkedHashMap<>();
    private int activeTotal;
    private int peakActiveTotal;
    private long spawnedSinceReset;
    private boolean warningSent;
    private long lastWarningGameTime;

    private static final int STATIONARY_AGE_TICKS = 100;
    private static final double STATIONARY_SPEED_SQR = 0.000001D;

    private PackageEntityMonitor() {
    }

    public static PackageEntityMonitor getInstance() {
        return INSTANCE;
    }

    public synchronized void onEntityJoin(Entity entity, Level level, boolean loadedFromDisk) {
        if (!isEnabled() || !(entity instanceof PackageEntity) || level.isClientSide) {
            return;
        }

        activeTotal++;
        activeByDimension.merge(level.dimension(), 1, Integer::sum);
        peakActiveTotal = Math.max(peakActiveTotal, activeTotal);
        if (!loadedFromDisk) {
            spawnedSinceReset++;
        }

        long gameTime = level.getGameTime();
        if (activeTotal >= CAOServerConfig.PACKAGE_ENTITY_WARNING_THRESHOLD.get() && shouldWarn(gameTime, 200L)) {
            warningSent = true;
            lastWarningGameTime = gameTime;
            CreateAdvancedOptimization.LOGGER.warn("Active Create PackageEntity count reached {} on {}.", activeTotal, level.dimension().location());
        }
    }

    public synchronized void onEntityLeave(Entity entity, Level level) {
        if (!isEnabled() || !(entity instanceof PackageEntity) || level.isClientSide) {
            return;
        }

        activeTotal = Math.max(0, activeTotal - 1);
        activeByDimension.compute(level.dimension(), (dimension, count) -> {
            if (count == null || count <= 1) {
                return null;
            }
            return count - 1;
        });
    }

    public synchronized int getActiveTotal() {
        return activeTotal;
    }

    public synchronized int getPeakActiveTotal() {
        return peakActiveTotal;
    }

    public synchronized long getSpawnedSinceReset() {
        return spawnedSinceReset;
    }

    public synchronized Map<ResourceKey<Level>, Integer> getActiveByDimension() {
        return new LinkedHashMap<>(activeByDimension);
    }

    public synchronized void reset(MinecraftServer server) {
        activeByDimension.clear();
        activeTotal = 0;
        peakActiveTotal = 0;
        spawnedSinceReset = 0L;
        warningSent = false;
        lastWarningGameTime = 0L;
        if (server != null && isEnabled()) {
            rescan(server);
        }
    }

    public synchronized void rescan(MinecraftServer server) {
        activeByDimension.clear();
        activeTotal = 0;
        spawnedSinceReset = 0L;
        if (!isEnabled()) {
            peakActiveTotal = 0;
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof PackageEntity) {
                    activeTotal++;
                    activeByDimension.merge(level.dimension(), 1, Integer::sum);
                }
            }
        }
        peakActiveTotal = Math.max(peakActiveTotal, activeTotal);
    }

    public synchronized List<ChunkPackageCount> collectTopChunks(MinecraftServer server, int limit) {
        if (!isEnabled()) {
            return List.of();
        }

        Map<ChunkKey, Integer> counts = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof PackageEntity)) {
                    continue;
                }
                ChunkPos chunkPos = entity.chunkPosition();
                ChunkKey key = new ChunkKey(level.dimension(), chunkPos.x, chunkPos.z);
                counts.merge(key, 1, Integer::sum);
            }
        }

        List<ChunkPackageCount> result = new ArrayList<>();
        counts.entrySet().stream()
            .sorted(Map.Entry.<ChunkKey, Integer>comparingByValue(Comparator.reverseOrder()))
            .limit(limit)
            .forEach(entry -> result.add(new ChunkPackageCount(entry.getKey(), entry.getValue())));
        return result;
    }

    public synchronized PackageDiagnostics collectDiagnostics(MinecraftServer server, int stalledLimit) {
        if (!isEnabled()) {
            return PackageDiagnostics.empty();
        }

        int active = 0;
        long totalAgeTicks = 0L;
        int maxAgeTicks = 0;
        int stationaryCandidates = 0;
        int oldPackages = 0;
        List<PackageSnapshot> stalledPackages = new ArrayList<>();

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof PackageEntity packageEntity)) {
                    continue;
                }

                active++;
                int ageTicks = packageEntity.tickCount;
                totalAgeTicks += ageTicks;
                maxAgeTicks = Math.max(maxAgeTicks, ageTicks);
                if (ageTicks >= 20 * 60) {
                    oldPackages++;
                }

                Vec3 movement = packageEntity.getDeltaMovement();
                boolean stationary = ageTicks >= STATIONARY_AGE_TICKS && movement.lengthSqr() <= STATIONARY_SPEED_SQR;
                if (stationary) {
                    stationaryCandidates++;
                    stalledPackages.add(new PackageSnapshot(
                        level.dimension(),
                        packageEntity.getX(),
                        packageEntity.getY(),
                        packageEntity.getZ(),
                        packageEntity.chunkPosition().x,
                        packageEntity.chunkPosition().z,
                        ageTicks,
                        packageEntity.insertionDelay,
                        movement.length()
                    ));
                }
            }
        }

        stalledPackages.sort(Comparator.comparingInt(PackageSnapshot::ageTicks).reversed());
        if (stalledPackages.size() > stalledLimit) {
            stalledPackages = new ArrayList<>(stalledPackages.subList(0, stalledLimit));
        }

        double averageAgeTicks = active == 0 ? 0.0D : totalAgeTicks / (double) active;
        return new PackageDiagnostics(active, averageAgeTicks, maxAgeTicks, stationaryCandidates, oldPackages, stalledPackages);
    }

    public record ChunkPackageCount(ChunkKey key, int count) {
    }

    public record ChunkKey(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        public int minBlockX() {
            return chunkX << 4;
        }

        public int minBlockZ() {
            return chunkZ << 4;
        }

        public int maxBlockX() {
            return minBlockX() + 15;
        }

        public int maxBlockZ() {
            return minBlockZ() + 15;
        }
    }

    public record PackageDiagnostics(
        int active,
        double averageAgeTicks,
        int maxAgeTicks,
        int stationaryCandidates,
        int oldPackages,
        List<PackageSnapshot> stalledPackages
    ) {
        public double averageAgeSeconds() {
            return averageAgeTicks / 20.0D;
        }

        public double maxAgeSeconds() {
            return maxAgeTicks / 20.0D;
        }

        private static PackageDiagnostics empty() {
            return new PackageDiagnostics(0, 0.0D, 0, 0, 0, List.of());
        }
    }

    public record PackageSnapshot(
        ResourceKey<Level> dimension,
        double x,
        double y,
        double z,
        int chunkX,
        int chunkZ,
        int ageTicks,
        int insertionDelay,
        double speed
    ) {
        public double ageSeconds() {
            return ageTicks / 20.0D;
        }
    }

    private static boolean isEnabled() {
        return CAOServerConfig.GENERAL_ENABLED.get() && CAOServerConfig.DIAGNOSTICS_ENABLED.get();
    }

    private boolean shouldWarn(long gameTime, long cooldownTicks) {
        return !warningSent || (gameTime >= lastWarningGameTime && gameTime - lastWarningGameTime >= cooldownTicks);
    }
}
