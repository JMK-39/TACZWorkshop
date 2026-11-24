package dev.xyat.taczworkshop.mixin;

import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.ICommonResourceProvider;
import com.tacz.guns.resource.network.CommonNetworkCache;
import dev.xyat.taczworkshop.server.TaczClientSyncContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps single-player sync reads off the integrated server's mutable data managers. */
@Mixin(value = CommonAssetsManager.class, remap = false)
public abstract class TaczCommonSnapshotMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private static void workshop$receivedSnapshot(CallbackInfoReturnable<ICommonResourceProvider> callback) {
        if (TaczClientSyncContext.isActive()) callback.setReturnValue(CommonNetworkCache.INSTANCE);
    }
}
