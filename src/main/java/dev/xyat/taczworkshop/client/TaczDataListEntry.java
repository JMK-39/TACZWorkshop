package dev.xyat.taczworkshop.client;

import com.google.gson.JsonObject;
import dev.xyat.taczworkshop.data.TaczDataKind;

public record TaczDataListEntry(
        TaczDataKind kind,
        String id,
        String dataId,
        String nameKey,
        String type,
        boolean modified,
        boolean removed,
        JsonObject previewIndex
) {
    public static TaczDataListEntry fromJson(JsonObject object) {
        JsonObject preview = object != null && object.has("preview_index") && object.get("preview_index").isJsonObject()
                ? object.getAsJsonObject("preview_index").deepCopy()
                : new JsonObject();
        return new TaczDataListEntry(
                TaczDataKind.fromWire(read(object, "kind")),
                read(object, "id"),
                read(object, "data_id"),
                read(object, "name"),
                read(object, "type"),
                bool(object, "modified"),
                bool(object, "removed"),
                preview
        );
    }

    public boolean hasPreviewIndex() {
        return previewIndex != null && !previewIndex.entrySet().isEmpty();
    }

    private static String read(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return "";
        return object.get(key).getAsString();
    }

    private static boolean bool(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean();
    }
}
