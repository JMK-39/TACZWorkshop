package dev.xyat.taczworkshopvalidation;

import com.google.gson.JsonObject;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.network.CommonNetworkCache;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.taczworkshop.client.TaczDataClientState;
import dev.xyat.taczworkshop.client.gui.TaczDataManagerPage;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import dev.xyat.taczworkshop.data.TaczDataKind;
import dev.xyat.taczworkshop.server.TaczBaseDataCache;
import dev.xyat.taczworkshop.server.TaczDataRuntime;
import dev.xyat.taczworkshop.server.TaczDataStore;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single-player fixture for saving TACZ data: first a full /reload is timed, as every save used to run one, then a
 * save is applied the way saves are now (TACZ's data alone), checked on the server and the client, and undone. Logs
 * how long each took and the longest pause of the client in between.
 */
public final class TaczDataApplyValidation {
    private static final Logger LOG = LoggerFactory.getLogger(TaczDataApplyValidation.class);
    private static final String GUN = "tacz:ak47";
    // The store's files as they were, put back byte for byte at the end (null: the file did not exist).
    private static byte[] fileBefore, backupBefore;
    private static volatile boolean filesRead;
    private static long settleFrom;
    private static int phase, ticks, wait, syncs, syncsAtApply, applied;
    private static int oldAmmo, oldRpm;
    private static float oldDamage;
    private static long started, fullReloadMs, applyMs, restoreMs;
    private static volatile long appliedAt;
    private static String dataId;
    private static long restoreRevision;
    private static volatile long clientUpdatedAt, sentAt;
    private static volatile boolean watchingSave, watchingRestore;
    private static volatile boolean finished;
    private static boolean failedApply, clientJoined;
    // The client's longest pause between two ticks since the mark, in milliseconds.
    private static volatile long lastClientTick, clientPause, fullReloadPause, applyPause;

    private TaczDataApplyValidation() {
    }

    public static void serverStarted(ServerStartedEvent event) {
        KineticServerEvents.onTick(KineticEventPriority.NORMAL, KineticServerEvents.TickPhase.END, TaczDataApplyValidation::tick);
        KineticServerEvents.onDatapackSync(KineticEventPriority.LOWEST, (server, player) -> {
            if (player == null) syncs++;
        });
        KineticClientRuntime.execute(() -> KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, () -> {
            long now = System.nanoTime();
            if (lastClientTick != 0L) clientPause = Math.max(clientPause, (now - lastClientTick) / 1_000_000L);
            lastClientTick = now;
            if (finished) return;
            if (watchingSave && clientUpdatedAt == 0L && !TaczDataClientState.hasPending() && clientMatches(50, 720, 37F)) clientUpdatedAt = now;
            if (watchingRestore && clientUpdatedAt == 0L && clientMatches(oldAmmo, oldRpm, oldDamage)) clientUpdatedAt = now;
            if (net.minecraft.client.Minecraft.getInstance().player != null) clientJoined = true;
            else if (clientJoined) {
                finished = true;
                try { if (filesRead) restoreFiles(); }
                catch (java.io.IOException error) { LOG.error("TACZ_APPLY could not restore files after disconnect", error); }
                LOG.error("TACZ_APPLY_FAIL phase={} the client disconnected before validation finished", phase);
                KineticClientRuntime.stopClient();
            }
        }));
    }

    private static void tick(MinecraftServer server) {
        if (finished) return;
        try {
            if (++ticks > 20 * 60 * 20) throw new AssertionError("TACZ data apply validation timed out in phase " + phase);
            if (server.getPlayerList().getPlayers().isEmpty() || ++wait < 100) return;
            switch (phase) {
                case 0 -> {
                    fileBefore = read(TaczDataStore.file());
                    backupBefore = read(backupFile());
                    filesRead = true;
                    inspectBlockIndex(server);
                    GunData gun = gunData();
                    oldAmmo = gun.getAmmoAmount();
                    oldRpm = gun.getRoundsPerMinute();
                    oldDamage = gun.getBulletData().getDamageAmount();
                    // What every save used to do.
                    clientPause = 0L;
                    started = System.nanoTime();
                    syncsAtApply = syncs;
                    TaczDataRuntime.reloadManagedData(server, false);
                    next();
                }
                case 1 -> {
                    if (syncs == syncsAtApply) {
                        wait = 100;
                        return;
                    }
                    if (fullReloadMs == 0L) fullReloadMs = (System.nanoTime() - started) / 1_000_000L;
                    // The client takes the recipes and tags in (and restarts JEI) before its pause is read.
                    if (!settled()) {
                        wait = 100;
                        return;
                    }
                    fullReloadPause = clientPause;
                    LOG.info("TACZ_APPLY_STEP full /reload took {} ms on the server; the client paused {} ms", fullReloadMs, fullReloadPause);
                    JsonObject detail = TaczBaseDataCache.detailSnapshot(TaczDataKind.GUN, GUN);
                    JsonObject data = detail.getAsJsonObject("data").deepCopy();
                    data.addProperty("ammo_amount", 50);
                    data.addProperty("rpm", 720);
                    data.getAsJsonObject("bullet").addProperty("damage", 37);
                    dataId = detail.get("data_id").getAsString();
                    syncsAtApply = syncs;
                    KineticClientRuntime.execute(() -> saveFromEditor(data));
                    next();
                    wait = 100;
                }
                case 2 -> {
                    require(!failedApply, "the saved data could be applied");
                    require(sentAt == 0 || System.nanoTime() - sentAt < 30_000_000_000L || clientUpdatedAt != 0,
                            "editor save did not reach the client within 30 seconds");
                    GunData gun = gunData();
                    if (sentAt == 0 || !matches(gun, 50, 720, 37F) || !TaczDataRuntime.canStartBatchReload()) {
                        wait = 100;
                        return;
                    }
                    if (applyMs == 0) applyMs = (System.nanoTime() - sentAt) / 1_000_000L;
                    if (clientUpdatedAt == 0 || !settled()) {
                        wait = 100;
                        return;
                    }
                    applyPause = clientPause;
                    require(syncs == syncsAtApply, "no datapack reload ran for the save: " + (syncs - syncsAtApply));
                    require(TaczDataStore.get(TaczDataKind.GUN, GUN).data().get("ammo_amount").getAsInt() == 50, "the override is stored");
                    checkLoginSnapshot(50, 720, 37F);
                    require(TaczDataRuntime.canStartBatchReload(), "nothing is left waiting");
                    LOG.info("TACZ_APPLY_EDITOR serverObservedMs={} clientVisibleMs={} clientPauseMs={}",
                            applyMs, (clientUpdatedAt - sentAt) / 1_000_000L, applyPause);
                    restoreFiles();
                    applied = 0;
                    restoreRevision = ClientIndexManager.resourceRevision();
                    watchingSave = false;
                    clientUpdatedAt = 0;
                    watchingRestore = true;
                    clientPause = 0;
                    started = System.nanoTime();
                    // Two saves in a row, as two quick clicks make them: the second waits for the first.
                    TaczDataRuntime.applyTaczData(server, TaczDataApplyValidation::applied, () -> failedApply = true);
                    TaczDataRuntime.applyTaczData(server, TaczDataApplyValidation::applied, () -> failedApply = true);
                    next();
                    wait = 100;
                }
                default -> {
                    require(!failedApply, "the original data could be applied");
                    if (applied < 2 || clientUpdatedAt == 0 || !settled()) {
                        wait = 100;
                        return;
                    }
                    restoreMs = (appliedAt - started) / 1_000_000L;
                    GunData gun = gunData();
                    require(gun.getAmmoAmount() == oldAmmo && gun.getRoundsPerMinute() == oldRpm
                            && Math.abs(gun.getBulletData().getDamageAmount() - oldDamage) < 0.001, "the original gun data is back");
                    checkLoginSnapshot(oldAmmo, oldRpm, oldDamage);
                    long rebuilds = (ClientIndexManager.resourceRevision() - restoreRevision) / 2;
                    LOG.info("TACZ_APPLY_RESTORE clientVisibleMs={} clientPauseMs={} indexRebuilds={}",
                            (clientUpdatedAt - started) / 1_000_000L, clientPause, rebuilds);
                    require(!Boolean.getBoolean("taczworkshopapply.expectDedup") || rebuilds == 1,
                            "two identical restores must rebuild client indices only once, saw " + rebuilds);
                    require(java.util.Arrays.equals(read(TaczDataStore.file()), fileBefore)
                            && java.util.Arrays.equals(read(backupFile()), backupBefore), "the store's files are as they were");
                    finished = true;
                    LOG.info("TACZ_APPLY_PASS fullReloadMs={} fullReloadClientPauseMs={} applyMs={} applyClientPauseMs={} twoRestoresMs={}",
                            fullReloadMs, fullReloadPause, applyMs, applyPause, restoreMs);
                    KineticClientRuntime.execute(KineticClientRuntime::stopClient);
                }
            }
        } catch (Throwable error) {
            finished = true;
            try {
                if (filesRead) restoreFiles();
            } catch (Throwable restore) {
                error.addSuppressed(restore);
            }
            LOG.error("TACZ_APPLY_FAIL phase=" + phase, error);
            KineticClientRuntime.execute(KineticClientRuntime::stopClient);
        }
    }

    private static void saveFromEditor(JsonObject data) {
        try {
            require(!TaczDataClientState.hasPending(), "fixture starts without unsaved editor changes");
            var page = new TaczDataManagerPage();
            KineticGui.open(page);
            require(!net.minecraft.client.Minecraft.getInstance().screen.isPauseScreen(), "workshop editor keeps the integrated server ticking");
            TaczDataClientState.stageOverride(TaczDataKind.GUN, GUN, dataId, false, data);
            var saveAll = TaczDataManagerPage.class.getDeclaredMethod("saveAll");
            saveAll.setAccessible(true);
            clientPause = 0;
            watchingSave = true;
            sentAt = System.nanoTime();
            lastClientTick = sentAt;
            saveAll.invoke(page);
        } catch (Throwable failure) {
            LOG.error("TACZ_APPLY_FAIL invoking editor save", failure);
            finished = true;
            KineticClientRuntime.stopClient();
        }
    }

    private static boolean clientMatches(int ammo, int rpm, float damage) {
        return matches(CommonNetworkCache.INSTANCE.getGunData(KineticResourceIds.parse(dataId)), ammo, rpm, damage);
    }

    private static boolean matches(GunData gun, int ammo, int rpm, float damage) {
        return gun != null && gun.getAmmoAmount() == ammo && gun.getRoundsPerMinute() == rpm
                && Math.abs(gun.getBulletData().getDamageAmount() - damage) < 0.001;
    }

    /** Compares the actual highest-priority block resource with its parsed/native index during compatibility QA. */
    private static void inspectBlockIndex(MinecraftServer server) throws Exception {
        var id = KineticResourceIds.parse("pgp:printer");
        var resource = server.getResourceManager().getResource(KineticResourceIds.parse("pgp:index/blocks/printer.json"));
        if (resource.isEmpty()) return;
        try (var reader = resource.get().openAsReader()) {
            var raw = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            var parsed = com.tacz.guns.resource.CommonAssetsManager.GSON.fromJson(raw, com.tacz.guns.resource.pojo.BlockIndexPOJO.class);
            LOG.info("TACZ_APPLY_BLOCK_INDEX source={} rawData={} parsedData={} loaded={}", resource.get().sourcePackId(),
                    raw.get("data"), parsed.getData(), com.tacz.guns.resource.CommonAssetsManager.get().getBlockIndex(id) != null);
        }
    }

    /** Decodes the real prepared packets sent to players who join after a targeted save, including all chunks. */
    private static void checkLoginSnapshot(int ammo, int rpm, float damage) throws Exception {
        var assets = com.tacz.guns.resource.CommonAssetsManager.getInstance();
        var field = assets.getClass().getDeclaredField("networkSyncMessages");
        field.setAccessible(true);
        var messages = (java.util.List<?>) field.get(assets);
        require(!messages.isEmpty(), "joining players have a prepared gun pack snapshot");
        var partField = com.tacz.guns.network.message.ServerMessageSyncGunPack.class.getDeclaredField("part");
        partField.setAccessible(true);
        var receiver = new com.tacz.guns.resource.network.GunPackSync.Receiver();
        byte[] snapshot = null;
        for (Object message : messages) {
            byte[] assembled = receiver.accept((com.tacz.guns.resource.network.GunPackSync.Part) partField.get(message));
            if (assembled != null) snapshot = assembled;
        }
        require(snapshot != null, "the prepared gun pack chunks assemble completely");
        var decode = com.tacz.guns.network.message.ServerMessageSyncGunPack.class.getDeclaredMethod("readCache", byte[].class);
        decode.setAccessible(true);
        var cache = (java.util.Map<?, ?>) decode.invoke(null, (Object) snapshot);
        var guns = (java.util.Map<?, ?>) cache.get(com.tacz.guns.resource.network.DataType.GUN_DATA);
        String dataId = TaczBaseDataCache.detailSnapshot(TaczDataKind.GUN, GUN).get("data_id").getAsString();
        String json = (String) guns.get(KineticResourceIds.parse(dataId));
        require(json != null, "the login snapshot includes the edited gun data");
        JsonObject gun = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        require(gun.get("ammo_amount").getAsInt() == ammo && gun.get("rpm").getAsInt() == rpm
                        && Math.abs(gun.getAsJsonObject("bullet").get("damage").getAsFloat() - damage) < 0.001,
                "players joining after the save get the latest gun data");
    }

    private static void applied() {
        applied++;
        appliedAt = System.nanoTime();
    }

    /** Whether five seconds have passed since first asked and the client is ticking again. */
    private static boolean settled() {
        long now = System.nanoTime();
        if (settleFrom == 0L) {
            settleFrom = now;
            return false;
        }
        if (now - settleFrom < 5_000_000_000L || now - lastClientTick > 250_000_000L) return false;
        settleFrom = 0L;
        return true;
    }

    private static java.nio.file.Path backupFile() {
        return TaczDataStore.file().resolveSibling(TaczDataStore.file().getFileName() + ".bak");
    }

    private static byte[] read(java.nio.file.Path file) throws java.io.IOException {
        return java.nio.file.Files.exists(file) ? java.nio.file.Files.readAllBytes(file) : null;
    }

    private static void restoreFiles() throws java.io.IOException {
        restore(TaczDataStore.file(), fileBefore);
        restore(backupFile(), backupBefore);
    }

    private static void restore(java.nio.file.Path file, byte[] bytes) throws java.io.IOException {
        if (bytes == null) java.nio.file.Files.deleteIfExists(file);
        else java.nio.file.Files.write(file, bytes);
    }

    private static void next() {
        phase++;
        wait = 0;
    }

    private static GunData gunData() {
        return TimelessAPI.getCommonGunIndex(KineticResourceIds.parse(GUN)).orElseThrow().getGunData();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
