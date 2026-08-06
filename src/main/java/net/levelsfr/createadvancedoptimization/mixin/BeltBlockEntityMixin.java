package net.levelsfr.createadvancedoptimization.mixin;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import net.levelsfr.createadvancedoptimization.diagnostics.profiler.CreateProfilerManager;
import net.levelsfr.createadvancedoptimization.diagnostics.profiler.ProfiledSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Profiles controller belt ticks separately from the broad SmartBlockEntity dispatcher.
 */
@Mixin(BeltBlockEntity.class)
public abstract class BeltBlockEntityMixin {

    @Unique
    private long cao$startedAt;

    @Inject(method = "tick", at = @At("HEAD"))
    private void cao$profileStart(CallbackInfo ci) {
        if (CreateProfilerManager.isProfilingEnabled()) {
            cao$startedAt = CreateProfilerManager.begin(ProfiledSection.BELT_TICK);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void cao$profileEnd(CallbackInfo ci) {
        if (cao$startedAt != 0L) {
            CreateProfilerManager.end(ProfiledSection.BELT_TICK, cao$startedAt);
            cao$startedAt = 0L;
        }
    }
}
