package dev.xyat.taczworkshop.server;

import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.runtime.KineticServerRuntime;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import dev.xyat.taczworkshop.TaczWorkshop;
import dev.xyat.taczworkshop.network.TaczRecipeNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class TaczDataRuntime {
    private static final int RELOAD_TIMEOUT_TICKS = 20 * 60 * 5;
    private static PendingBatchReload pendingBatchReload;
    private static int reloadTimeoutTicks;
    private static boolean registered;

    private TaczDataRuntime() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        KineticServerEvents.onStarted(KineticEventPriority.NORMAL, TaczDataRuntime::onServerStarted);
        KineticServerEvents.onStopping(KineticEventPriority.NORMAL, server -> onServerStopping());
        KineticServerEvents.onDatapackSync(KineticEventPriority.NORMAL, TaczDataRuntime::onDatapackSync);
        KineticServerEvents.onTick(KineticEventPriority.NORMAL, KineticServerEvents.TickPhase.END, TaczDataRuntime::onServerTick);
    }

    private static void onServerStarted(MinecraftServer server) {
        clearPendingReload();
        TaczDataStore.snapshot();
    }

    private static void onServerStopping() {
        clearPendingReload();
    }

    private static void onDatapackSync(MinecraftServer server, ServerPlayer player) {
        if (player != null) {
            if (player.hasPermissions(2)) TaczRecipeNetwork.sendDataList(player);
            return;
        }

        PendingBatchReload completed = takePendingReload();
        if (completed != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(completed.playerId());
            if (target != null) {
                TaczRecipeNetwork.completeDataBatchReload(target, completed.batchId(), completed.editCount());
            }
            TaczWorkshop.LOGGER.info("TACZ data batch reload completed: batchId={}, edits={}", completed.batchId(), completed.editCount());
        }

        server.getPlayerList().getPlayers().stream()
                .filter(target -> target.hasPermissions(2))
                .forEach(TaczRecipeNetwork::sendDataList);
    }

    private static void onServerTick(MinecraftServer server) {
        PendingBatchReload timedOut = tickPendingReload();
        if (timedOut == null) return;

        ServerPlayer player = server.getPlayerList().getPlayer(timedOut.playerId());
        if (player != null) TaczRecipeNetwork.failDataBatchReload(player);
        TaczWorkshop.LOGGER.warn("Timed out waiting for server /reload after TACZ data save: batchId={}, edits={}", timedOut.batchId(), timedOut.editCount());
    }

    public static void reloadTaczData() {
        executeReloadCommand(KineticServerRuntime.currentServer());
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
