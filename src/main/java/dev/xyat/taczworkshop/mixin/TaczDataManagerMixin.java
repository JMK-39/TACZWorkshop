package dev.xyat.taczworkshop.mixin;

import com.google.gson.JsonElement;
import com.tacz.guns.resource.manager.JsonDataManager;
import dev.xyat.taczworkshop.server.TaczBaseDataCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = JsonDataManager.class, remap = false)
public abstract class TaczDataManagerMixin {
    @Inject(method = "apply", at = @At("HEAD"))
    private void taczworkshop_tacz$captureAndApply(Map<ResourceLocation, JsonElement> source, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        JsonDataManager<?> manager = (JsonDataManager<?>) (Object) this;
        TaczBaseDataCache.Domain domain = TaczBaseDataCache.domainForClass(manager.getDataClass());
        if (domain != null) TaczBaseDataCache.captureAndApply(domain, source);
    }
}
