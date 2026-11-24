package dev.xyat.taczworkshop.server;

import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import dev.xyat.taczworkshop.TaczWorkshop;
import dev.xyat.taczworkshop.network.TaczDataNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Applies saved TACZ data. A save reloads TACZ's own data alone ({@link TaczDataReload}); saves made while one is
 * applied wait and are applied together right after it. Only when TACZ's data cannot be reloaded on its own does a
 * full /reload apply it instead.
 */
public final class TaczDataRuntime {
    private static final int RELOAD_TIMEOUT_TICKS = 20 * 60 * 5;
    // Server thread only. The same slot remains occupied through targeted reload and its automatic fallback.
    private static boolean reloading;
    private static final List<Waiter> waiting = new ArrayList<>();
    private static final List<Waiter> runningWaiters = new ArrayList<>();
    private static int reloadTicks;
    private static MinecraftServer activeServer;
    private static long serverEpoch;
    private static boolean registered;

    private TaczDataRuntime() {
    }

    /** Runs {@code done} once the data is applied, or {@code failed} if it could not be. */
    private static final class Waiter {
        private final Runnable done, failed;
        private boolean completed;

        private Waiter(Runnable done, Runnable failed) {
            this.done = done;
            this.failed = failed;
        }

        private void complete(boolean success) {
            if (completed) return;
            completed = true;
            try {
                (success ? done : failed).run();
            } catch (RuntimeException exception) {
                TaczWorkshop.LOGGER.error("TACZ save completion callback failed", exception);
            }
        }
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        KineticServerEvents.onStarted(KineticEventPriority.NORMAL, TaczDataRuntime::onServerStarted);
        KineticServerEvents.onStopping(KineticEventPriority.NORMAL, server -> {
            if (server == activeServer) clearWaiting();
        });
        KineticServerEvents.onDatapackSync(KineticEventPriority.NORMAL, TaczDataRuntime::onDatapackSync);
        KineticServerEvents.onTick(KineticEventPriority.NORMAL, KineticServerEvents.TickPhase.END, TaczDataRuntime::onServerTick);
    }

    private static void onServerStarted(MinecraftServer server) {
        clearWaiting();
        activeServer = server;
        TaczDataStore.snapshot();
    }

    private static void onDatapackSync(MinecraftServer server, ServerPlayer player) {
        if (server != activeServer) return;
        if (player != null) {
            if (player.hasPermissions(2)) TaczDataNetwork.sendDataList(player);
            return;
        }
        // A sync event is not ownership or completion of our reload future (manual reloads emit it too).
        sendDataListToOperators(server);
    }

    private static void onServerTick(MinecraftServer server) {
        if (server != activeServer || runningWaiters.isEmpty() || --reloadTicks > 0) return;
        List<Waiter> timedOut = new ArrayList<>(runningWaiters);
        runningWaiters.clear();
        TaczWorkshop.LOGGER.warn("Timed out waiting for a TACZ data save; reload remains busy until its future ends");
        timedOut.forEach(waiter -> waiter.complete(false));
    }

    /**
     * Applies the saved TACZ data, then runs {@code done} on the server thread ({@code failed} if it could not be
     * applied). Call it on the server thread.
     */
    public static void applyTaczData(MinecraftServer server, Runnable done, Runnable failed) {
        if (server == null) throw new IllegalStateException("Minecraft server is not available");
        if (activeServer != server) throw new IllegalStateException("Minecraft server is not the active save session");
        waiting.add(new Waiter(done, failed));
        if (reloading) return;
        startReload(server);
    }

    private static void startReload(MinecraftServer server) {
        reloading = true;
        long epoch = serverEpoch;
        List<Waiter> applying = new ArrayList<>(waiting);
        waiting.clear();
        runningWaiters.addAll(applying);
        reloadTicks = RELOAD_TIMEOUT_TICKS;
        long started = System.nanoTime();
        CompletableFuture<Void> running;
        try {
            running = TaczDataReload.reload(server);
        } catch (RuntimeException | LinkageError exception) {
            running = CompletableFuture.failedFuture(exception);
        }
        running.whenCompleteAsync((ignored, error) -> {
            if (!currentSession(server, epoch)) return;
            Throwable problem = error;
            if (problem == null) {
                try {
                    TaczDataReload.sync(server);
                } catch (RuntimeException | LinkageError exception) {
                    problem = exception;
                }
            }
            if (problem == null) {
                TaczWorkshop.LOGGER.info("Applied TACZ data in {} ms without /reload ({} saves)",
                        (System.nanoTime() - started) / 1_000_000L, applying.size());
                finishReload(server, epoch, applying, true);
            } else {
                TaczWorkshop.LOGGER.warn("Cannot reload TACZ data on its own; running /reload instead", problem);
                fullReload(server, epoch, applying);
            }
        }, server);
    }

    private static void fullReload(MinecraftServer server, long epoch, List<Waiter> waiters) {
        CompletableFuture<Void> running;
        try {
            // Keep the selected packs in their existing priority order; do not rediscover/reorder packs.
            running = server.reloadResources(new ArrayList<>(server.getPackRepository().getSelectedIds()));
        } catch (RuntimeException | LinkageError exception) {
            running = CompletableFuture.failedFuture(exception);
        }
        running.whenCompleteAsync((ignored, error) -> {
            if (!currentSession(server, epoch)) return;
            if (error != null) TaczWorkshop.LOGGER.error("Cannot complete fallback reload for TACZ data", error);
            finishReload(server, epoch, waiters, error == null);
        }, server);
    }

    private static boolean currentSession(MinecraftServer server, long epoch) {
        return server == activeServer && epoch == serverEpoch;
    }

    private static void finishReload(MinecraftServer server, long epoch, List<Waiter> waiters, boolean success) {
        if (!currentSession(server, epoch)) return;
        runningWaiters.clear();
        for (Waiter waiter : waiters) {
            if (!currentSession(server, epoch)) return;
            waiter.complete(success);
        }
        if (!currentSession(server, epoch)) return;
        if (success) {
            try {
                sendDataListToOperators(server);
            } catch (RuntimeException exception) {
                TaczWorkshop.LOGGER.error("Cannot refresh TACZ editor lists after reload", exception);
            }
        }
        if (!currentSession(server, epoch)) return;
        reloading = false;
        if (!waiting.isEmpty()) startReload(server);
    }

    /** A full /reload, as players run it; for checks that the overrides survive one. */
    public static void reloadManagedData(MinecraftServer server, boolean includeExternalPacks) {
        executeReloadCommand(server);
    }

    /** Whether no TACZ data is being applied. */
    public static boolean canStartBatchReload() {
        return !reloading && waiting.isEmpty();
    }

    /** Applies a saved batch, then tells the player it is committed (or that it failed). */
    public static void requestDataBatchReload(ServerPlayer player, long batchId, int editCount) {
        if (player == null || player.getServer() == null) {
            throw new IllegalStateException("Minecraft server is not available");
        }
        if (batchId <= 0L) {
            throw new IllegalArgumentException("Invalid data batch id");
        }
        MinecraftServer server = player.getServer();
        UUID playerId = player.getUUID();
        int edits = Math.max(0, editCount);
        applyTaczData(server, () -> {
            ServerPlayer target = server.getPlayerList().getPlayer(playerId);
            if (target != null) TaczDataNetwork.completeDataBatchReload(target, batchId, edits);
            TaczWorkshop.LOGGER.info("TACZ data batch applied: batchId={}, edits={}", batchId, edits);
        }, () -> {
            ServerPlayer target = server.getPlayerList().getPlayer(playerId);
            if (target != null) TaczDataNetwork.failDataBatchReload(target);
        });
    }

    private static void sendDataListToOperators(MinecraftServer server) {
        server.getPlayerList().getPlayers().stream()
                .filter(target -> target.hasPermissions(2))
                .forEach(TaczDataNetwork::sendDataList);
    }

    private static void executeReloadCommand(MinecraftServer server) {
        if (server == null) throw new IllegalStateException("Minecraft server is not available");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
    }

    private static void clearWaiting() {
        serverEpoch++;
        activeServer = null;
        reloading = false;
        waiting.clear();
        runningWaiters.clear();
        reloadTicks = 0;
    }
}
