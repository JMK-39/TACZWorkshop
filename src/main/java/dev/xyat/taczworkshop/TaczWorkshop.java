package dev.xyat.taczworkshop;

import com.mojang.logging.LogUtils;
import dev.xyat.kineticcore.api.config.server.KTServerConfigApi;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import dev.xyat.taczworkshop.compat.ClientAmmoTickEvent;
import dev.xyat.taczworkshop.config.TaczConfigGui;
import dev.xyat.taczworkshop.network.TaczRecipeNetwork;
import dev.xyat.taczworkshop.server.TaczDataRuntime;
import dev.xyat.taczworkshop.server.TaczRecipeRuntime;
import dev.xyat.taczworkshop.server.TaczRouteSyncEvents;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(TaczWorkshop.MODID)
public final class TaczWorkshop {
    public static final String MODID = "taczworkshop";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TaczWorkshop() {
        KTServerConfigApi.registerActionPage(TaczConfigGui.PAGE_ID);
        TaczRecipeNetwork.register();
        TaczDataRuntime.register();
        TaczRecipeRuntime.register();
        TaczRouteSyncEvents.register();
        KineticPlatform.runOnClient(() -> () -> {
            TaczConfigGui.load();
            ClientAmmoTickEvent.register();
        });
    }
}
