package dev.xyat.taczworkshop.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class TaczRecipeRecord {
    public static final String ORIGIN_CUSTOM = "custom";
    public static final String ORIGIN_ORIGINAL = "original";
    public static final String ORIGIN_REPLACEMENT = "replacement";

    private String uuid;
    private String id;
    private boolean enabled;
    private String comment;
    private final List<TaczMaterial> materials;
    private JsonObject result;
    private String origin;
    private String originalId;
    private final List<String> workbenches;

    public TaczRecipeRecord(String uuid, String id, boolean enabled, String comment, List<TaczMaterial> materials, JsonObject result) {
        this(uuid, id, enabled, comment, materials, result, ORIGIN_CUSTOM, "", List.of());
    }

    public TaczRecipeRecord(String uuid, String id, boolean enabled, String comment, List<TaczMaterial> materials, JsonObject result,
                            String origin, String originalId, List<String> workbenches) {
        this.uuid = normalizeUuid(uuid);
        this.id = clean(id);
        this.enabled = enabled;
        this.comment = comment == null ? "" : comment;
        this.materials = new ArrayList<>();
        if (materials != null) {
            for (TaczMaterial material : materials) {
                if (material != null) this.materials.add(material.copy());
            }
        }
        this.result = result == null ? new JsonObject() : result.deepCopy();
        this.origin = normalizeOrigin(origin);
        this.originalId = clean(originalId);
        this.workbenches = new ArrayList<>();
        setWorkbenches(workbenches);
    }

    public static TaczRecipeRecord blank(String resultType) {
        String uuid = UUID.randomUUID().toString();
        String suffix = uuid.substring(0, 8).toLowerCase(Locale.ROOT);
        JsonObject result = new JsonObject();
        result.addProperty("type", resultType);
        result.addProperty("count", 1);
        if ("custom".equals(resultType)) {
            JsonObject item = new JsonObject();
            item.addProperty("item", "minecraft:air");
            item.addProperty("count", 1);
            result.add("item", item);
        } else {
            result.addProperty("id", "");
            String baseItem = TaczRecipeCodec.defaultExternalBaseItem(resultType);
            if (!baseItem.isBlank()) result.addProperty("base_item", baseItem);
        }
        return new TaczRecipeRecord(
                uuid,
                "taczworkshop:gun_smith_table/new_" + suffix,
                true,
                "",
                List.of(),
                result,
                ORIGIN_CUSTOM,
                "",
                List.of()
        );
    }

    public static TaczRecipeRecord original(String id, boolean enabled, List<TaczMaterial> materials, JsonObject result, List<String> workbenches) {
        String uuid = UUID.nameUUIDFromBytes(("taczworkshop:original:" + id).getBytes(StandardCharsets.UTF_8)).toString();
        return new TaczRecipeRecord(uuid, id, enabled, "", materials, result, ORIGIN_ORIGINAL, "", workbenches);
    }

    public String uuid() {
        return uuid;
    }

    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = clean(id);
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String comment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment == null ? "" : comment;
    }

    public List<TaczMaterial> materials() {
        return materials;
    }

    public JsonObject result() {
        return result;
    }

    public void setResult(JsonObject result) {
        this.result = result == null ? new JsonObject() : result.deepCopy();
    }

    public String origin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = normalizeOrigin(origin);
    }

    public String originalId() {
        return originalId;
    }

    public void setOriginalId(String originalId) {
        this.originalId = clean(originalId);
    }

    public List<String> workbenches() {
        return workbenches;
    }

    public void setWorkbenches(List<String> values) {
        workbenches.clear();
        Set<String> unique = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String clean = clean(value);
                if (!clean.isBlank()) unique.add(clean);
            }
        }
        workbenches.addAll(unique);
    }

    public boolean isOriginal() {
        return ORIGIN_ORIGINAL.equals(origin);
    }

    public boolean isReplacement() {
        return ORIGIN_REPLACEMENT.equals(origin);
    }

    public boolean isCustom() {
        return ORIGIN_CUSTOM.equals(origin);
    }

    public String sourceRecipeId() {
        return isReplacement() && !originalId.isBlank() ? originalId : id;
    }

    public TaczRecipeRecord copy() {
        return new TaczRecipeRecord(uuid, id, enabled, comment, materials, result, origin, originalId, workbenches);
    }

    public TaczRecipeRecord duplicate() {
        TaczRecipeRecord copy = copy();
        copy.uuid = UUID.randomUUID().toString();
        String base = copy.id == null || copy.id.isBlank()
                ? "taczworkshop:gun_smith_table/copied_recipe"
                : copy.id;
        copy.id = base + "_copy";
        copy.origin = ORIGIN_CUSTOM;
        copy.originalId = "";
        return copy;
    }

    public TaczRecipeRecord asReplacement(String replacementId) {
        TaczRecipeRecord copy = copy();
        copy.uuid = UUID.randomUUID().toString();
        copy.originalId = id;
        copy.id = replacementId;
        copy.origin = ORIGIN_REPLACEMENT;
        return copy;
    }

    public TaczRecipeRecord asCreatedFromOriginal(String createdId) {
        TaczRecipeRecord copy = copy();
        copy.uuid = UUID.randomUUID().toString();
        copy.originalId = id;
        copy.id = createdId;
        copy.origin = ORIGIN_CUSTOM;
        copy.enabled = true;
        return copy;
    }

    public String resultType() {
        return result.has("type") ? result.get("type").getAsString() : "";
    }

    public String resultId() {
        if ("custom".equals(resultType())) {
            if (result.has("item") && result.get("item").isJsonObject()) {
                JsonObject item = result.getAsJsonObject("item");
                return item.has("item") ? item.get("item").getAsString() : "";
            }
            return "";
        }
        return result.has("id") ? result.get("id").getAsString() : "";
    }

    public String searchableText() {
        return (id + " " + originalId + " " + resultType() + " " + resultId() + " " + comment + " " + String.join(" ", workbenches)).toLowerCase(Locale.ROOT);
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("uuid", uuid);
        object.addProperty("id", id);
        object.addProperty("enabled", enabled);
        if (!comment.isBlank()) object.addProperty("comment", comment);
        if (!ORIGIN_CUSTOM.equals(origin)) object.addProperty("origin", origin);
        if (!originalId.isBlank()) object.addProperty("original_id", originalId);
        if (!workbenches.isEmpty()) {
            JsonArray workbenchArray = new JsonArray();
            workbenches.forEach(workbenchArray::add);
            object.add("workbenches", workbenchArray);
        }
        JsonArray array = new JsonArray();
        for (TaczMaterial material : materials) array.add(material.toJson());
        object.add("materials", array);
        object.add("result", result.deepCopy());
        return object;
    }

    public static TaczRecipeRecord fromJson(JsonObject object) {
        String uuid = object.has("uuid") ? object.get("uuid").getAsString() : "";
        if (!uuid.isBlank()) UUID.fromString(uuid);
        String id = object.has("id") ? object.get("id").getAsString() : "";
        boolean enabled = !object.has("enabled") || object.get("enabled").getAsBoolean();
        String comment = object.has("comment") ? object.get("comment").getAsString() : "";
        String origin = object.has("origin") ? object.get("origin").getAsString() : ORIGIN_CUSTOM;
        String originalId = object.has("original_id") ? object.get("original_id").getAsString() : "";
        List<String> workbenches = new ArrayList<>();
        if (object.has("workbenches") && object.get("workbenches").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("workbenches")) {
                if (element.isJsonPrimitive()) workbenches.add(element.getAsString());
            }
        }
        List<TaczMaterial> materials = new ArrayList<>();
        if (object.has("materials") && object.get("materials").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("materials")) {
                if (element.isJsonObject()) materials.add(TaczMaterial.fromJson(element.getAsJsonObject()));
            }
        }
        JsonObject result = object.has("result") && object.get("result").isJsonObject()
                ? object.getAsJsonObject("result")
                : new JsonObject();
        return new TaczRecipeRecord(uuid, id, enabled, comment, materials, result, origin, originalId, workbenches);
    }

    private static String normalizeUuid(String value) {
        if (value != null && !value.isBlank()) {
            try {
                return UUID.fromString(value).toString();
            } catch (IllegalArgumentException ignored) {
            }
        }
        return UUID.randomUUID().toString();
    }

    private static String normalizeOrigin(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase(Locale.ROOT)) {
            case ORIGIN_ORIGINAL -> ORIGIN_ORIGINAL;
            case ORIGIN_REPLACEMENT -> ORIGIN_REPLACEMENT;
            default -> ORIGIN_CUSTOM;
        };
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
