package dev.xyat.taczworkshop.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.xyat.taczworkshop.data.TaczMaterial;
import dev.xyat.taczworkshop.data.TaczRecipeCodec;
import dev.xyat.taczworkshop.data.TaczRecipeRecord;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TaczRecipeIssueRegistry {
    private static final Map<ResourceLocation, Issue> ISSUES = new LinkedHashMap<>();

    private TaczRecipeIssueRegistry() {
    }

    public record Issue(ResourceLocation id, String code, String detail, JsonElement raw) {
        public Issue copy() {
            return new Issue(id, clean(code), clean(detail), raw == null ? JsonNull.INSTANCE : raw.deepCopy());
        }
    }

    public static synchronized void beginReload() {
        ISSUES.clear();
    }

    public static synchronized void clear() {
        ISSUES.clear();
    }

    public static synchronized void record(ResourceLocation id, String code, String detail, JsonElement raw) {
        if (id == null) return;
        ISSUES.put(id, new Issue(id, clean(code), clean(detail), raw == null ? JsonNull.INSTANCE : raw.deepCopy()));
    }

    public static synchronized void remove(ResourceLocation id) {
        if (id != null) ISSUES.remove(id);
    }

    public static synchronized int size() {
        return ISSUES.size();
    }

    public static synchronized List<TaczRecipeRecord> snapshotRecords(Set<String> suppressedIds) {
        List<TaczRecipeRecord> result = new ArrayList<>();
        for (Issue issue : ISSUES.values()) {
            if (issue == null || issue.id() == null) continue;
            if (suppressedIds != null && suppressedIds.contains(issue.id().toString())) continue;
            result.add(toRecord(issue));
        }
        return result;
    }

    private static TaczRecipeRecord toRecord(Issue issue) {
        JsonElement raw = issue.raw() == null ? JsonNull.INSTANCE : issue.raw().deepCopy();
        List<TaczMaterial> materials = new ArrayList<>();
        JsonObject result = placeholderResult();

        if (raw.isJsonObject()) {
            JsonObject object = raw.getAsJsonObject();
            if (object.has("materials") && object.get("materials").isJsonArray()) {
                for (JsonElement element : object.getAsJsonArray("materials")) {
                    if (!element.isJsonObject()) {
                        materials.add(new TaczMaterial(element, 1));
                        continue;
                    }
                    JsonObject materialObject = element.getAsJsonObject();
                    JsonElement ingredient = materialObject.has("item") ? materialObject.get("item") : new JsonObject();
                    int count = safePositiveInt(materialObject.get("count"), 1);
                    materials.add(new TaczMaterial(ingredient, count));
                }
            }
            if (object.has("result") && object.get("result").isJsonObject()) {
                result = normalizeResult(object.getAsJsonObject("result"));
            }
        }

        return TaczRecipeRecord.invalid(
                issue.id().toString(),
                materials,
                result,
                issue.code(),
                issue.detail(),
                raw
        );
    }

    private static JsonObject normalizeResult(JsonObject source) {
        if (source == null) return placeholderResult();
        JsonObject result = source.deepCopy();
        String type = primitiveString(result, "type");
        if (!TaczRecipeCodec.RESULT_TYPES.contains(type)) return placeholderResult();

        if ("custom".equals(type)) {
            if (!result.has("item") || !result.get("item").isJsonObject()) {
                JsonObject item = new JsonObject();
                item.addProperty("item", "minecraft:air");
                item.addProperty("count", 1);
                result.add("item", item);
            }
        } else if (!result.has("id") || !result.get("id").isJsonPrimitive()) {
            result.addProperty("id", "");
        }
        if (!result.has("count")) result.addProperty("count", 1);
        return result;
    }

    private static JsonObject placeholderResult() {
        JsonObject result = new JsonObject();
        result.addProperty("type", "custom");
        JsonObject item = new JsonObject();
        item.addProperty("item", "minecraft:air");
        item.addProperty("count", 1);
        result.add("item", item);
        return result;
    }

    private static int safePositiveInt(JsonElement element, int fallback) {
        try {
            return element == null ? fallback : Math.max(1, element.getAsInt());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String primitiveString(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return "";
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
