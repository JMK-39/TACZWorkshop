package dev.xyat.taczworkshop.server;

import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class TaczClientSyncContextTest {
    @Test void snapshotScopeBelongsOnlyToTheReceivingThread() {
        assertFalse(active());
        run(() -> {
            assertTrue(active());
            AtomicBoolean otherThread = new AtomicBoolean(true);
            Thread other = new Thread(() -> otherThread.set(active()));
            other.start();
            try { other.join(5000L); } catch (InterruptedException error) { throw new AssertionError(error); }
            assertFalse(other.isAlive());
            assertFalse(otherThread.get(), "the server thread must continue using server assets");
        });
        assertFalse(active());
    }

    @Test void nestedSnapshotScopeKeepsTheOuterScopeActive() {
        run(() -> {
            run(() -> assertTrue(active()));
            assertTrue(active());
        });
        assertFalse(active());
    }

    @Test void failedNativeReloadCannotLeakTheSnapshotScope() {
        assertThrows(IllegalStateException.class, () -> run(() -> { throw new IllegalStateException("native reload failed"); }));
        assertFalse(active());
    }

    @Test void inWorldSyncDoesNotWaitForTheModelLoader() {
        var background = new CompletableFuture<Void>();
        try {
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () ->
                    invoke("run", new Class<?>[]{boolean.class, Runnable.class}, true, (Runnable) () -> {
                        assertTrue(active());
                        assertNull(invoke("awaitModels", new Class<?>[]{CompletableFuture.class}, background));
                        assertFalse(background.isDone(), "the renderer must let native background work finish on its own");
                    }));
        } finally {
            background.complete(null);
        }
        assertFalse(active());
        var failed = CompletableFuture.failedFuture(new IllegalStateException("preload failed"));
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> invoke("awaitModels", new Class<?>[]{CompletableFuture.class}, failed));
    }

    private static boolean active() { return (boolean) invoke("isActive", new Class<?>[0]); }
    private static void run(Runnable action) { invoke("run", new Class<?>[]{Runnable.class}, action); }
    private static Object invoke(String name, Class<?>[] arguments, Object... values) {
        try {
            return Class.forName("dev.xyat.taczworkshop.server.TaczClientSyncContext").getMethod(name, arguments).invoke(null, values);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            if (error.getCause() instanceof Error failure) throw failure;
            throw new AssertionError(error.getCause());
        } catch (ReflectiveOperationException missing) {
            throw new AssertionError("The native client sync needs an isolated snapshot scope", missing);
        }
    }
}
