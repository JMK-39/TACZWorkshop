package dev.xyat.taczworkshop.server;

import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageSyncGunPack;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import dev.xyat.taczworkshop.mixin.TaczAssetsManagerAccessor;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Applies saved TACZ data without /reload: TACZ's own data managers, and LR Tactical's when it is installed, read their
 * files again from the server's current resources (the capture mixins apply the Workshop overrides as on any reload),
 * then every player gets TACZ's and LR Tactical's own data packets, as after a datapack sync. A /reload also rebuilds
 * every other mod's recipes, tags, loot tables and advancements on the server and JEI on every client, which takes
 * minutes in a large pack, and it did so for every save.
 */
final class TaczDataReload {
    private static final String LR_ASSETS = "me.xjqsh.lrtactical.resource.CommonAssetsManager";
    private static final String LR_SYNC = "me.xjqsh.lrtactical.network.message.SPackSyncMessage";
    private static final String LR_NETWORK = "me.xjqsh.lrtactical.network.NetworkHandler";
    private static final String[] LR_MANAGERS = {"meleeIndexManager", "throwableIndexManager", "consumableIndexManager"};
    // These maps are immutable native snapshots. The packet list identity also detects a native /reload.
    private static Map<DataType, Map<ResourceLocation, String>> lastSentCache;
    private static List<ServerMessageSyncGunPack> lastSentMessages;
    private TaczDataReload() {
    }

    /** Reloads the data managers; the returned future completes on the server thread. */
    static CompletableFuture<Void> reload(MinecraftServer server) {
        CommonAssetsManager tacz = CommonAssetsManager.getInstance();
        if (tacz == null) return CompletableFuture.failedFuture(new IllegalStateException("TACZ data is not loaded"));
        List<PreparableReloadListener> listeners = new ArrayList<>(((TaczAssetsManagerAccessor) tacz).taczworkshop$listeners());
        listeners.addAll(lrManagers());
        ResourceManager resources = server.getResourceManager();
        // As in a datapack reload: every manager reads its files on a background thread at once, and they are applied
        // on the server thread one after another in TACZ's order, since an index looks up the data applied before it.
        CompletableFuture<?> applied = CompletableFuture.completedFuture(null);
        for (PreparableReloadListener listener : listeners) {
            CompletableFuture<?> before = applied;
            applied = listener.reload(new PreparableReloadListener.PreparationBarrier() {
                @Override
                public <T> CompletableFuture<T> wait(T value) {
                    return before.thenApply(ignored -> value);
                }
            }, resources, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, Util.backgroundExecutor(), server);
        }
        // TACZ resets this cache after its own reload too.
        return applied.thenRunAsync(AllowAttachmentTagMatcher::resetCache, server);
    }

    /** Sends the reloaded data to every player, as TACZ and LR Tactical do after a datapack sync. */
    static void sync(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        CommonAssetsManager tacz = CommonAssetsManager.getInstance();
        if (tacz != null) {
            TaczAssetsManagerAccessor assets = (TaczAssetsManagerAccessor) tacz;
            Map<DataType, Map<ResourceLocation, String>> cache = tacz.getNetworkCache();
            if (assets.taczworkshop$networkSyncMessages() != lastSentMessages || !cache.equals(lastSentCache)) {
                List<ServerMessageSyncGunPack> messages = ServerMessageSyncGunPack.createMessages(cache);
                assets.taczworkshop$networkSyncMessages(messages);
                players.forEach(player -> messages.forEach(message -> NetworkHandler.sendToClientPlayer(message, player)));
                lastSentCache = cache;
                lastSentMessages = messages;
            }
        }
        Object lr = lrAssets();
        if (lr == null) return;
        try {
            Object message = Class.forName(LR_SYNC).getConstructor(Map.class).newInstance(lr.getClass().getMethod("toNetwork").invoke(lr));
            Method send = Class.forName(LR_NETWORK).getMethod("sendToClientPlayer", Object.class, Player.class);
            for (ServerPlayer player : players) send.invoke(null, message, player);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Cannot send LR Tactical data", exception);
        }
    }

    private static List<PreparableReloadListener> lrManagers() {
        Object lr = lrAssets();
        List<PreparableReloadListener> managers = new ArrayList<>();
        if (lr == null) return managers;
        try {
            for (String name : LR_MANAGERS) {
                if (lr.getClass().getField(name).get(lr) instanceof PreparableReloadListener manager) managers.add(manager);
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Cannot reach LR Tactical's data managers", exception);
        }
        return managers;
    }

    /** LR Tactical's loaded assets, or {@code null} when it is not installed or has not loaded. */
    @Nullable
    private static Object lrAssets() {
        try {
            return Class.forName(LR_ASSETS).getField("INSTANCE").get(null);
        } catch (ClassNotFoundException exception) {
            return null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Cannot reach LR Tactical's assets", exception);
        }
    }
}
