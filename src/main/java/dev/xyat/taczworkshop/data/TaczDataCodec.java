package dev.xyat.taczworkshop.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TaczDataCodec {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private TaczDataCodec() {
    }

    public static JsonObject encodeRoot(Map<TaczDataKind, Map<String, TaczDataOverride>> source) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        for (TaczDataKind kind : TaczDataKind.values()) {
            JsonObject section = new JsonObject();
            Map<String, TaczDataOverride> entries = source == null ? null : source.get(kind);
            if (entries != null) {
                entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    TaczDataOverride override = entry.getValue();
                    if (override == null) return;
                    JsonObject value = new JsonObject();
                    if (!override.dataId().isBlank()) value.addProperty("data_id", override.dataId());
                    if (override.removed()) value.addProperty("removed", true);
                    JsonObject data = override.data();
                    if (data != null) value.add("data", data);
                    section.add(entry.getKey(), value);
                });
            }
            root.add(kind.jsonKey(), section);
        }
        return root;
    }

    public static Map<TaczDataKind, Map<String, TaczDataOverride>> decodeRoot(JsonObject root) {
        Map<TaczDataKind, Map<String, TaczDataOverride>> result = emptyMap();
        if (root == null) return result;
        for (TaczDataKind kind : TaczDataKind.values()) {
            JsonElement sectionElement = root.get(kind.jsonKey());
            if (sectionElement == null || !sectionElement.isJsonObject()) continue;
            JsonObject section = sectionElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                String id = normalizeId(entry.getKey());
                JsonObject value = entry.getValue().getAsJsonObject();
                String dataId = readString(value, "data_id");
                boolean removed = value.has("removed") && value.get("removed").isJsonPrimitive() && value.get("removed").getAsBoolean();
                JsonObject data = value.has("data") && value.get("data").isJsonObject() ? value.getAsJsonObject("data") : null;
                result.get(kind).put(id, new TaczDataOverride(kind, id, dataId, removed, data));
            }
        }
        return result;
    }

    public static Map<TaczDataKind, Map<String, TaczDataOverride>> emptyMap() {
        Map<TaczDataKind, Map<String, TaczDataOverride>> result = new EnumMap<>(TaczDataKind.class);
        for (TaczDataKind kind : TaczDataKind.values()) result.put(kind, new LinkedHashMap<>());
        return result;
    }

    public static String normalizeId(String value) {
        String id = value == null ? "" : value.trim();
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) throw new IllegalArgumentException("Invalid resource id: " + id);
        return parsed.toString();
    }

    private static String readString(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return "";
        return object.get(key).getAsString().trim();
    }
}
