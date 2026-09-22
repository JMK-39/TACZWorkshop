package dev.xyat.taczworkshop.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.taczworkshop.TaczWorkshop;
import dev.xyat.taczworkshop.data.TaczDataCodec;
import dev.xyat.taczworkshop.data.TaczDataKind;
import dev.xyat.taczworkshop.data.TaczDataOverride;
import dev.xyat.kineticcore.api.runtime.KineticPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public final class TaczDataStore {
    private static final Path DIRECTORY = KineticPaths.configDirectory().resolve("kineticcore");
    private static final Path FILE = DIRECTORY.resolve("taczdata.json");
    private static final Path BACKUP = DIRECTORY.resolve("taczdata.json.bak");
    private static Map<TaczDataKind, Map<String, TaczDataOverride>> cache = TaczDataCodec.emptyMap();
    private static long cacheStamp = Long.MIN_VALUE;
    private static boolean healthy = true;

    private TaczDataStore() {
    }

    public static Path file() {
        return FILE;
    }

    public static synchronized Map<TaczDataKind, Map<String, TaczDataOverride>> snapshot() {
        reloadIfChanged();
        Map<TaczDataKind, Map<String, TaczDataOverride>> copy = TaczDataCodec.emptyMap();
        for (TaczDataKind kind : TaczDataKind.values()) {
            cache.get(kind).forEach((id, value) -> copy.get(kind).put(id, value.copy()));
        }
        return copy;
    }

    public static synchronized TaczDataOverride get(TaczDataKind kind, String id) {
        reloadIfChanged();
        TaczDataOverride value = cache.get(kind).get(id);
        return value == null ? null : value.copy();
    }

    public static synchronized void upsert(TaczDataOverride value) throws IOException {
        if (value == null) throw new IllegalArgumentException("Missing TACZ data override");
        reloadIfChanged();
        ensureHealthy();
        String id = TaczDataCodec.normalizeId(value.id());
        String dataId = value.dataId().isBlank() ? "" : TaczDataCodec.normalizeId(value.dataId());
        JsonObject data = value.data();
        if (!value.removed() && data == null) throw new IllegalArgumentException("Missing override data");
        cache.get(value.kind()).put(id, new TaczDataOverride(value.kind(), id, dataId, value.removed(), data));
        saveCache();
    }


    public static synchronized void replaceAll(Map<TaczDataKind, Map<String, TaczDataOverride>> next) throws IOException {
        reloadIfChanged();
        ensureHealthy();
        Map<TaczDataKind, Map<String, TaczDataOverride>> normalized = TaczDataCodec.emptyMap();
        if (next != null) {
            for (TaczDataKind kind : TaczDataKind.values()) {
                Map<String, TaczDataOverride> entries = next.get(kind);
                if (entries == null) continue;
                for (Map.Entry<String, TaczDataOverride> entry : entries.entrySet()) {
                    TaczDataOverride value = entry.getValue();
                    if (value == null) continue;
                    String id = TaczDataCodec.normalizeId(value.id());
                    String dataId = value.dataId().isBlank() ? "" : TaczDataCodec.normalizeId(value.dataId());
                    JsonObject data = value.data();
                    if (!value.removed() && data == null) throw new IllegalArgumentException("Missing override data");
                    normalized.get(kind).put(id, new TaczDataOverride(kind, id, dataId, value.removed(), data));
                }
            }
        }
        cache = normalized;
        saveCache();
    }

    public static synchronized boolean reset(TaczDataKind kind, String id) throws IOException {
        reloadIfChanged();
        ensureHealthy();
        boolean removed = cache.get(kind).remove(TaczDataCodec.normalizeId(id)) != null;
        if (removed) saveCache();
        return removed;
    }

    private static void reloadIfChanged() {
        if (!Files.exists(FILE)) {
            if (cacheStamp != Long.MIN_VALUE) cache = TaczDataCodec.emptyMap();
            cacheStamp = Long.MIN_VALUE;
            healthy = true;
            return;
        }
        try {
            long stamp = Files.getLastModifiedTime(FILE).toMillis();
            if (stamp == cacheStamp) return;
            String raw = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(raw.isBlank() ? "{}" : raw);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("taczdata.json root is not an object");
            cache = TaczDataCodec.decodeRoot(parsed.getAsJsonObject());
            cacheStamp = stamp;
            healthy = true;
        } catch (Exception exception) {
            healthy = false;
            TaczWorkshop.LOGGER.error("Unable to read {}", FILE, exception);
        }
    }

    private static void saveCache() throws IOException {
        Files.createDirectories(DIRECTORY);
        Path temp = DIRECTORY.resolve("taczdata.json.tmp");
        String json = TaczDataCodec.GSON.toJson(TaczDataCodec.encodeRoot(cache));
        Files.writeString(temp, json + System.lineSeparator(), StandardCharsets.UTF_8);
        if (Files.exists(FILE)) Files.copy(FILE, BACKUP, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
        }
        cacheStamp = Files.getLastModifiedTime(FILE).toMillis();
        healthy = true;
    }

    private static void ensureHealthy() throws IOException {
        if (!healthy) throw new IOException("taczdata.json could not be read; refusing to overwrite it");
    }
}
