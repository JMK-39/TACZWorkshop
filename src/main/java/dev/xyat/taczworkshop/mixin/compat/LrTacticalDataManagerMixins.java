package dev.xyat.taczworkshop.mixin.compat;

import com.google.gson.JsonElement;
import dev.xyat.taczworkshop.server.TaczBaseDataCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

public final class LrTacticalDataManagerMixins {
    private LrTacticalDataManagerMixins() {
    }

    @Pseudo
    @Mixin(targets = "me.xjqsh.lrtactical.resource.manager.MeleeIndexManager", remap = false)
    public abstract static class MeleeIndexManagerMixin {
        @Inject(method = "apply", at = @At("HEAD"), require = 0)
        private void taczworkshop_tacz$captureMelee(Map<ResourceLocation, JsonElement> source, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
            TaczBaseDataCache.captureAndApply(TaczBaseDataCache.Domain.LR_MELEE_INDEX, source);
        }
    }

    @Pseudo
    @Mixin(targets = "me.xjqsh.lrtactical.resource.manager.ThrowableIndexManager", remap = false)
    public abstract static class ThrowableIndexManagerMixin {
        @Inject(method = "apply", at = @At("HEAD"), require = 0)
        private void taczworkshop_tacz$captureThrowable(Map<ResourceLocation, JsonElement> source, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
            TaczBaseDataCache.captureAndApply(TaczBaseDataCache.Domain.LR_THROWABLE_INDEX, source);
        }
    }

    @Pseudo
    @Mixin(targets = "me.xjqsh.lrtactical.resource.manager.ConsumableIndexManager", remap = false)
    public abstract static class ConsumableIndexManagerMixin {
        @Inject(method = "apply", at = @At("HEAD"), require = 0)
        private void taczworkshop_tacz$captureConsumable(Map<ResourceLocation, JsonElement> source, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
            TaczBaseDataCache.captureAndApply(TaczBaseDataCache.Domain.LR_CONSUMABLE_INDEX, source);
        }
    }
}
