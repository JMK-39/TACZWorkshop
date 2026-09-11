package dev.xyat.taczworkshop.server;

import dev.xyat.taczworkshop.TaczWorkshop;
import dev.xyat.taczworkshop.network.TaczRecipeNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = TaczWorkshop.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TaczDataRuntime {
    private static final int RELOAD_TIMEOUT_TICKS = 20 * 60 * 5;
    private static PendingBatchReload pendingBatchReload;
    private static int reloadTimeoutTicks;

    private TaczDataRuntime() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        clearPendingReload();
        TaczDataStore.snapshot();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        clearPendingReload();
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            if (event.getPlayer().hasPermissions(2)) TaczRecipeNetwork.sendDataList(event.getPlayer());
            return;
        }

        MinecraftServer server = event.getPlayerList().getServer();
        PendingBatchReload completed = takePendingReload();
        if (completed != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(completed.playerId());
            if (player != null) {
                TaczRecipeNetwork.completeDataBatchReload(player, completed.batchId(), completed.editCount());
            }
            TaczWorkshop.LOGGER.info("TACZ data batch reload completed: batchId={}, edits={}", completed.batchId(), completed.editCount());
        }

        event.getPlayerList().getPlayers().stream()
                .filter(player -> player.hasPermissions(2))
                .forEach(TaczRecipeNetwork::sendDataList);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        PendingBatchReload timedOut = tickPendingReload();
        if (timedOut == null) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(timedOut.playerId());
            if (player != null) TaczRecipeNetwork.failDataBatchReload(player);
        }
        TaczWorkshop.LOGGER.warn("Timed out waiting for server /reload after TACZ data save: batchId={}, edits={}", timedOut.batchId(), timedOut.editCount());
    }

    public static void reloadTaczData() {
        executeReloadCommand(ServerLifecycleHooks.getCurrentServer());
    }

    public static void reloadManagedData(MinecraftServer server, boolean includeExternalPacks) {
        executeReloadCommand(server);
    }

    public static synchronized boolean canStartBatchReload() {
        return pendingBatchReload == null;
    }

    public static void requestDataBatchReload(ServerPlayer player, long batchId, int editCount) {
        if (player == null || player.getServer() == null) {
            throw new IllegalStateException("Minecraft server is not available");
        }
        if (batchId <= 0L) {
            throw new IllegalArgumentException("Invalid data batch id");
        }

        synchronized (TaczDataRuntime.class) {
            if (pendingBatchReload != null) {
                throw new IllegalStateException("A server reload is already pending");
            }
            pendingBatchReload = new PendingBatchReload(player.getUUID(), batchId, Math.max(0, editCount));
            reloadTimeoutTicks = RELOAD_TIMEOUT_TICKS;
        }

        try {
            TaczWorkshop.LOGGER.info("Starting server /reload after TACZ data save: batchId={}, edits={}", batchId, editCount);
            executeReloadCommand(player.getServer());
        } catch (RuntimeException exception) {
            clearPendingReload();
            throw exception;
        }
    }

    private static void executeReloadCommand(MinecraftServer server) {
        if (server == null) throw new IllegalStateException("Minecraft server is not available");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
    }

    private static synchronized PendingBatchReload takePendingReload() {
        PendingBatchReload result = pendingBatchReload;
        pendingBatchReload = null;
        reloadTimeoutTicks = 0;
        return result;
    }

    private static synchronized PendingBatchReload tickPendingReload() {
        if (pendingBatchReload == null) return null;
        if (--reloadTimeoutTicks > 0) return null;
        PendingBatchReload result = pendingBatchReload;
        pendingBatchReload = null;
        reloadTimeoutTicks = 0;
        return result;
    }

    private static synchronized void clearPendingReload() {
        pendingBatchReload = null;
        reloadTimeoutTicks = 0;
    }

    private record PendingBatchReload(UUID playerId, long batchId, int editCount) {
    }
}
