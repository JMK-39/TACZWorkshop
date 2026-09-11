package dev.xyat.taczworkshop.server;

import dev.xyat.taczworkshop.TaczWorkshop;
import dev.xyat.taczworkshop.network.TaczRecipeNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TaczWorkshop.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TaczRouteSyncEvents {
    private TaczRouteSyncEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TaczRecipeNetwork.sendRoutes(player);
    }
}
