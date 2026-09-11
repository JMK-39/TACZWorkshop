package dev.xyat.taczworkshop.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.taczworkshop.data.TaczDataKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TaczDataClientState {
    private enum PendingType {
        OVERRIDE,
        RESET,
        REMOVED
    }

    private record PendingEdit(
            PendingType type,
            TaczDataKind kind,
            String id,
            String dataId,
            boolean removed,
            JsonObject data,
            long sequence
    ) {
        PendingEdit copy() {
            return new PendingEdit(type, kind, id, dataId, removed, data == null ? null : data.deepCopy(), sequence);
        }
    }

    private static final Map<TaczDataKind, List<TaczDataListEntry>> ENTRIES = new EnumMap<>(TaczDataKind.class);
    private static final Map<TaczDataKind, Map<String, PendingEdit>> PENDING = new EnumMap<>(TaczDataKind.class);
    private static long revision;
    private static long pendingSequence;

    static {
        for (TaczDataKind kind : TaczDataKind.values()) {
            ENTRIES.put(kind, List.of());
            PENDING.put(kind, new LinkedHashMap<>());
        }
    }

    private TaczDataClientState() {
    }

    public static synchronized void replaceList(String json) {
        JsonElement parsed = JsonParser.parseString(json == null || json.isBlank() ? "{}" : json);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Invalid TACZ data list");
        JsonObject root = parsed.getAsJsonObject();
        for (TaczDataKind kind : TaczDataKind.values()) {
            List<TaczDataListEntry> list = new ArrayList<>();
            if (root.has(kind.jsonKey()) && root.get(kind.jsonKey()).isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject(kind.jsonKey()).entrySet()) {
                    if (entry.getValue().isJsonObject()) list.add(TaczDataListEntry.fromJson(entry.getValue().getAsJsonObject()));
                }
            }
            list.sort(Comparator.comparing(TaczDataListEntry::id));
            ENTRIES.put(kind, List.copyOf(list));
        }
        revision++;
    }

    public static synchronized List<TaczDataListEntry> snapshot(TaczDataKind kind) {
        List<TaczDataListEntry> result = new ArrayList<>();
        for (TaczDataListEntry entry : ENTRIES.getOrDefault(kind, List.of())) {
            PendingEdit pending = PENDING.get(kind).get(entry.id());
            if (pending == null) {
                result.add(entry);
                continue;
            }
            boolean removed = pending.type == PendingType.RESET ? false : pending.removed;
            String dataId = pending.type == PendingType.OVERRIDE && !pending.dataId.isBlank() ? pending.dataId : entry.dataId();
            result.add(new TaczDataListEntry(
                    entry.kind(),
                    entry.id(),
                    dataId,
                    entry.nameKey(),
                    entry.type(),
                    true,
                    removed,
                    entry.previewIndex()
            ));
        }
        return result;
    }

    public static synchronized void stageOverride(TaczDataKind kind, String id, String dataId, boolean removed, JsonObject data) {
        if (kind == null || id == null || id.isBlank() || data == null) return;
        long sequence = ++pendingSequence;
        PENDING.get(kind).put(id, new PendingEdit(PendingType.OVERRIDE, kind, id, dataId == null ? "" : dataId, removed, data.deepCopy(), sequence));
        revision++;
    }

    public static synchronized void stageReset(TaczDataKind kind, String id) {
        if (kind == null || id == null || id.isBlank()) return;
        long sequence = ++pendingSequence;
        PENDING.get(kind).put(id, new PendingEdit(PendingType.RESET, kind, id, "", false, null, sequence));
        revision++;
    }

    public static synchronized void stageRemoved(TaczDataKind kind, String id, boolean removed) {
        if (kind == null || id == null || id.isBlank()) return;
        PendingEdit existing = PENDING.get(kind).get(id);
        long sequence = ++pendingSequence;
        if (existing != null && existing.type == PendingType.OVERRIDE) {
            PENDING.get(kind).put(id, new PendingEdit(PendingType.OVERRIDE, kind, id, existing.dataId, removed, existing.data, sequence));
        } else {
            PENDING.get(kind).put(id, new PendingEdit(PendingType.REMOVED, kind, id, "", removed, null, sequence));
        }
        revision++;
    }

    public static synchronized boolean isPending(TaczDataKind kind, String id) {
        return kind != null && id != null && PENDING.get(kind).containsKey(id);
    }

    public static synchronized int pendingCount() {
        int count = 0;
        for (Map<String, PendingEdit> entries : PENDING.values()) count += entries.size();
        return count;
    }

    public static synchronized boolean hasPending() {
        return pendingCount() > 0;
    }

    public static synchronized JsonObject pendingPayload() {
        JsonObject root = new JsonObject();
        JsonArray edits = new JsonArray();
        long batchId = 0L;
        for (TaczDataKind kind : TaczDataKind.values()) {
            for (PendingEdit pending : PENDING.get(kind).values()) {
                batchId = Math.max(batchId, pending.sequence);
                JsonObject item = new JsonObject();
                item.addProperty("kind", pending.kind.wireName());
                item.addProperty("id", pending.id);
                item.addProperty("op", pending.type.name().toLowerCase());
                if (pending.type == PendingType.OVERRIDE) {
                    item.addProperty("data_id", pending.dataId);
                    item.addProperty("removed", pending.removed);
                    item.add("data", pending.data == null ? new JsonObject() : pending.data.deepCopy());
                } else if (pending.type == PendingType.REMOVED) {
                    item.addProperty("removed", pending.removed);
                }
                edits.add(item);
            }
        }
        root.addProperty("batch_id", batchId);
        root.add("edits", edits);
        return root;
    }

    public static synchronized void acknowledgeThrough(long batchId) {
        if (batchId <= 0L) return;
        boolean changed = false;
        for (Map<String, PendingEdit> entries : PENDING.values()) {
            var iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                PendingEdit pending = iterator.next().getValue();
                if (pending.sequence <= batchId) {
                    iterator.remove();
                    changed = true;
                }
            }
        }
        if (changed) revision++;
    }

    public static synchronized JsonObject applyPendingToDetail(JsonObject source) {
        JsonObject detail = source == null ? new JsonObject() : source.deepCopy();
        TaczDataKind kind;
        try {
            kind = TaczDataKind.fromWire(read(detail, "kind"));
        } catch (Exception ignored) {
            return detail;
        }
        String id = read(detail, "id");
        PendingEdit pending = PENDING.get(kind).get(id);
        if (pending == null) return detail;

        detail.addProperty("pending", true);
        detail.addProperty("modified", true);
        if (pending.type == PendingType.RESET) {
            JsonObject base = detail.has("base_data") && detail.get("base_data").isJsonObject()
                    ? detail.getAsJsonObject("base_data").deepCopy()
                    : new JsonObject();
            detail.add("data", base);
            detail.addProperty("removed", false);
            return detail;
        }
        if (pending.type == PendingType.REMOVED) {
            detail.addProperty("removed", pending.removed);
            return detail;
        }
        detail.add("data", pending.data == null ? new JsonObject() : pending.data.deepCopy());
        detail.addProperty("data_id", pending.dataId);
        detail.addProperty("removed", pending.removed);
        return detail;
    }

    public static final class PendingSnapshot {
        private final String json;

        public PendingSnapshot(String json) {
            this.json = json == null ? "" : json;
        }

        public String json() {
            return json;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof PendingSnapshot other && json.equals(other.json);
        }

        @Override
        public int hashCode() {
            return json.hashCode();
        }
    }

    public static synchronized PendingSnapshot capturePendingSnapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("pending_sequence", pendingSequence);
        JsonArray edits = new JsonArray();
        for (TaczDataKind kind : TaczDataKind.values()) {
            for (PendingEdit pending : PENDING.get(kind).values()) {
                JsonObject item = new JsonObject();
                item.addProperty("type", pending.type().name());
                item.addProperty("kind", pending.kind().wireName());
                item.addProperty("id", pending.id());
                item.addProperty("data_id", pending.dataId());
                item.addProperty("removed", pending.removed());
                item.addProperty("sequence", pending.sequence());
                if (pending.data() != null) item.add("data", pending.data().deepCopy());
                edits.add(item);
            }
        }
        root.add("edits", edits);
        return new PendingSnapshot(root.toString());
    }

    public static synchronized void restorePendingSnapshot(PendingSnapshot snapshot) {
        for (Map<String, PendingEdit> entries : PENDING.values()) entries.clear();
        pendingSequence = 0L;
        if (snapshot == null || snapshot.json() == null || snapshot.json().isBlank()) {
            revision++;
            return;
        }
        JsonElement parsed = JsonParser.parseString(snapshot.json());
        if (!parsed.isJsonObject()) {
            revision++;
            return;
        }
        JsonObject root = parsed.getAsJsonObject();
        if (root.has("pending_sequence")) pendingSequence = Math.max(0L, root.get("pending_sequence").getAsLong());
        if (root.has("edits") && root.get("edits").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("edits")) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                try {
                    PendingType type = PendingType.valueOf(read(item, "type"));
                    TaczDataKind kind = TaczDataKind.fromWire(read(item, "kind"));
                    String id = read(item, "id");
                    String dataId = read(item, "data_id");
                    boolean removed = item.has("removed") && item.get("removed").getAsBoolean();
                    long sequence = item.has("sequence") ? item.get("sequence").getAsLong() : ++pendingSequence;
                    JsonObject data = item.has("data") && item.get("data").isJsonObject()
                            ? item.getAsJsonObject("data").deepCopy()
                            : null;
                    if (!id.isBlank()) {
                        PENDING.get(kind).put(id, new PendingEdit(type, kind, id, dataId, removed, data, sequence));
                        pendingSequence = Math.max(pendingSequence, sequence);
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }
        revision++;
    }

    public static synchronized void clearPending() {
        for (Map<String, PendingEdit> entries : PENDING.values()) entries.clear();
        revision++;
    }

    public static synchronized long revision() {
        return revision;
    }

    private static String read(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return "";
        return object.get(key).getAsString();
    }
}
