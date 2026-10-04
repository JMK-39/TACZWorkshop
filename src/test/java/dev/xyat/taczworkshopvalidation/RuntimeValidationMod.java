package dev.xyat.taczworkshopvalidation;

import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;

/** Explicitly enabled client fixture, packaged separately from the production mod. */
@Mod("taczworkshop_validation")
public final class RuntimeValidationMod {
    public RuntimeValidationMod() {
        if (Boolean.getBoolean("taczworkshop.guiValidation")) {
            MinecraftForge.EVENT_BUS.addListener(this::serverStarted);
        }
    }

    private void serverStarted(ServerStartedEvent event) {
        KineticClientRuntime.execute(GuiLongTextValidation::install);
    }
}
