package dev.xyat.taczworkshop.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class TaczRecipePreflight {
    public static final String TACZ_RECIPE_TYPE = "tacz:gun_smith_table_crafting";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> STRONG_TACZ_RESULT_TYPES = Set.of("gun", "attachment", "ammo", "melee", "throwable", "consumable");

    private TaczRecipePreflight() {
    }

    public static Map<ResourceLocation, JsonElement> filter(Map<ResourceLocation, JsonElement> source) {
        TaczRecipeIssueRegistry.beginReload();
        Map<ResourceLocation, JsonElement> filtered = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) return filtered;

        int isolated = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : source.entrySet()) {
            ResourceLocation id = entry.getKey();
            JsonElement raw = entry.getValue();
            Failure failure = inspectEntry(id, raw);
            if (failure != null) {
                TaczRecipeIssueRegistry.record(id, failure.code(), failure.detail(), raw);
                isolated++;
                continue;
            }
            filtered.put(id, raw);
        }

        if (isolated > 0) {
            LOGGER.warn("TaczWorkshop isolated {} malformed TACZ recipe candidate(s) before recipe deserialization", isolated);
        }
        return filtered;
    }

    public static boolean inspectRecipeObject(ResourceLocation id, JsonObject object) {
        Failure failure = inspectEntry(id, object);
        if (failure == null) {
            TaczRecipeIssueRegistry.remove(id);
            return true;
        }
        TaczRecipeIssueRegistry.record(id, failure.code(), failure.detail(), object);
        LOGGER.warn("TaczWorkshop isolated TACZ recipe candidate {}: {}", id, failure.detail());
        return false;
    }

    private static Failure inspectEntry(ResourceLocation id, JsonElement raw) {
        if (raw == null || raw.isJsonNull()) {
            return looksTaczRelated(id, raw) ? new Failure("invalid_root", "Recipe JSON is null") : null;
        }
        if (!raw.isJsonObject()) {
            return looksTaczRelated(id, raw)
                    ? new Failure("invalid_root", "Recipe root must be a JSON object")
                    : null;
        }

        JsonObject object = raw.getAsJsonObject();
        String type = primitiveString(object, "type");
        if (TACZ_RECIPE_TYPE.equals(type)) return validateTaczObject(object);

        if (type.isBlank() && looksLikeTaczRecipeWithoutType(object)) {
            return new Failure("missing_type", "Missing recipe type; expected " + TACZ_RECIPE_TYPE);
        }
        return null;
    }

    private static Failure validateTaczObject(JsonObject object) {
        if (object == null) return new Failure("invalid_root", "Recipe root must be a JSON object");
        String type = primitiveString(object, "type");
        if (type.isBlank()) return new Failure("missing_type", "Missing recipe type; expected " + TACZ_RECIPE_TYPE);
        if (!TACZ_RECIPE_TYPE.equals(type)) return null;
        if (!object.has("materials") || !object.get("materials").isJsonArray()) {
            return new Failure("invalid_materials", "TACZ recipe materials must be a JSON array");
        }
        if (!object.has("result") || !object.get("result").isJsonObject()) {
            return new Failure("invalid_result", "TACZ recipe result must be a JSON object");
        }

        try {
            TableRecipe parsed = CommonAssetsManager.GSON.fromJson(object, TableRecipe.class);
            if (parsed == null) return new Failure("tacz_deserialize_failed", "TACZ returned a null recipe payload");
            if (parsed.getMaterials() == null) return new Failure("invalid_materials", "TACZ recipe materials resolved to null");
            if (parsed.getResult() == null) return new Failure("invalid_result", "TACZ recipe result resolved to null");
            return null;
        } catch (Throwable throwable) {
            Throwable root = rootCause(throwable);
            String detail = safeMessage(root);
            return new Failure(classify(root, detail), detail);
        }
    }

    private static boolean looksLikeTaczRecipeWithoutType(JsonObject object) {
        if (object == null || !object.has("materials") || !object.get("materials").isJsonArray()) return false;
        if (!object.has("result") || !object.get("result").isJsonObject()) return false;
        JsonObject result = object.getAsJsonObject("result");
        String resultType = primitiveString(result, "type");
        if (STRONG_TACZ_RESULT_TYPES.contains(resultType)) return true;
        return containsTaczReference(object);
    }

    private static boolean looksTaczRelated(ResourceLocation id, JsonElement raw) {
        if (id != null) {
            String path = id.getPath();
            if (path.contains("tacz_tag") || path.startsWith("tacz/") || path.contains("gun_smith")) return true;
        }
        return containsTaczReference(raw);
    }

    private static boolean containsTaczReference(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonPrimitive()) {
            try {
                if (!element.getAsJsonPrimitive().isString()) return false;
                String value = element.getAsString().toLowerCase();
                return value.startsWith("tacz:") || value.startsWith("#tacz:") || value.contains("lrtactical:");
            } catch (Exception ignored) {
                return false;
            }
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (containsTaczReference(child)) return true;
            }
            return false;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (containsTaczReference(entry.getValue())) return true;
            }
        }
        return false;
    }

    private static String classify(Throwable throwable, String detail) {
        String message = detail == null ? "" : detail.toLowerCase();
        if (message.contains("non [a-z0-9_.-] character") || message.contains("invalid resource location")) {
            return "invalid_resource_location";
        }
        if (message.contains("unknown item")) return "unknown_item";
        if (message.contains("unknown tag")) return "unknown_tag";
        if (message.contains("missing type")) return "missing_type";
        if (message.contains("missing item")) return "missing_item";
        if (message.contains("expected top element to be a jsonobject")) return "invalid_root";
        if (hasStackFrame(throwable, "GunSmithTableIngredientSerializer")) return "invalid_ingredient";
        return "tacz_deserialize_failed";
    }

    private static boolean hasStackFrame(Throwable throwable, String simpleName) {
        Throwable current = throwable;
        while (current != null) {
            for (StackTraceElement element : current.getStackTrace()) {
                if (element.getClassName().contains(simpleName)) return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable == null ? new IllegalStateException("Unknown TACZ recipe error") : throwable;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) return "Unknown TACZ recipe error";
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) message = throwable.getClass().getSimpleName();
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 512 ? message.substring(0, 509) + "..." : message;
    }

    private static String primitiveString(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return "";
        try {
            return object.get(key).getAsString().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private record Failure(String code, String detail) {
    }
}
