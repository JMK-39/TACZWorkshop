package dev.xyat.taczworkshop.mixin;

import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.network.message.ServerMessageSyncGunPack;
import com.tacz.guns.resource.network.CommonNetworkCache;
import com.tacz.guns.resource.network.DataType;
import dev.xyat.taczworkshop.server.TaczClientSyncContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.Map;
import java.lang.ref.WeakReference;

/** The native sync must use its own received snapshot while another server-side save can already be reloading. */
@Mixin(value = ServerMessageSyncGunPack.class, remap = false)
public abstract class TaczClientSyncMixin {
    @Unique private static WeakReference<Connection> workshop$lastSyncedConnection = new WeakReference<>(null);

    @Redirect(method = "doSync", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/resource/network/CommonNetworkCache;fromNetwork(Ljava/util/Map;)V"))
    private static void workshop$parseSnapshot(CommonNetworkCache cache, Map<DataType, Map<ResourceLocation, String>> data) {
        TaczClientSyncContext.run(() -> cache.fromNetwork(data));
    }

    @Redirect(method = "doSync", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/resource/ClientIndexManager;reload()V"))
    private static void workshop$reloadSnapshotIndices(ServerMessageSyncGunPack message, Connection connection) {
        boolean inWorldUpdate = workshop$lastSyncedConnection.get() == connection && Minecraft.getInstance().player != null;
        TaczClientSyncContext.run(inWorldUpdate, ClientIndexManager::reload);
        if (workshop$lastSyncedConnection.get() != connection) workshop$lastSyncedConnection = new WeakReference<>(connection);
    }
}
