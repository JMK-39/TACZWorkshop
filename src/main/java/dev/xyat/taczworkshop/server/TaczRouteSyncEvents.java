package dev.xyat.taczworkshop.server;

import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import dev.xyat.taczworkshop.network.TaczRecipeNetwork;

public final class TaczRouteSyncEvents {
    private static boolean registered;

    private TaczRouteSyncEvents() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        KineticServerEvents.onPlayerLogin(KineticEventPriority.NORMAL, TaczRecipeNetwork::sendRoutes);
    }
}
