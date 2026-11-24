package dev.xyat.taczworkshop.mixin;

import com.tacz.guns.client.resource.ClientIndexManager;
import dev.xyat.taczworkshop.server.TaczClientSyncContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;

/** Native warmup continues on its asset worker while the active client renders. */
@Mixin(value = ClientIndexManager.class, remap = false)
public abstract class TaczClientModelPreloadMixin {
    @Redirect(method = "preloadExternalModels", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"))
    private static Object workshop$waitForModels(CompletableFuture<?> completion) {
        return TaczClientSyncContext.awaitModels(completion);
    }
}
