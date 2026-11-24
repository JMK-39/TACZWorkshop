package dev.xyat.taczworkshop.mixin;

import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.network.message.ServerMessageSyncGunPack;
import com.tacz.guns.resource.manager.INetworkCacheReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** TACZ's data managers, so saved data can be applied by reloading them alone instead of every datapack. */
@Mixin(value = CommonAssetsManager.class, remap = false)
public interface TaczAssetsManagerAccessor {
    @Accessor("listeners")
    List<INetworkCacheReloadListener> taczworkshop$listeners();

    /** The native prepared snapshot must also be refreshed for players who connect after a targeted save. */
    @Accessor("networkSyncMessages")
    List<ServerMessageSyncGunPack> taczworkshop$networkSyncMessages();

    @Accessor("networkSyncMessages")
    void taczworkshop$networkSyncMessages(List<ServerMessageSyncGunPack> messages);
}
