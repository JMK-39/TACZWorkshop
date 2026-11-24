package dev.xyat.taczworkshop.server;

import java.util.concurrent.CompletableFuture;

/** Selects the received native snapshot while the receiving thread parses it and rebuilds client indices. */
public final class TaczClientSyncContext {
    private static final ThreadLocal<Boolean> DEFER_MODELS = new ThreadLocal<>();

    private TaczClientSyncContext() { }

    /** Whether this thread is currently consuming a received snapshot, rather than reading live server assets. */
    public static boolean isActive() { return DEFER_MODELS.get() != null; }

    /** Runs one native sync operation in a nestable scope and restores it even when native parsing fails. */
    public static void run(Runnable action) {
        run(false, action);
    }

    public static void run(boolean deferModels, Runnable action) {
        Boolean previous = DEFER_MODELS.get();
        DEFER_MODELS.set(deferModels);
        try { action.run(); }
        finally {
            if (previous == null) DEFER_MODELS.remove();
            else DEFER_MODELS.set(previous);
        }
    }

    public static Object awaitModels(CompletableFuture<?> completion) {
        // The native loader remains responsible for queuing/cancelling model work by resource generation.
        return Boolean.TRUE.equals(DEFER_MODELS.get()) ? null : completion.join();
    }
}
