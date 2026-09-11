package dev.xyat.taczworkshop.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class TaczRouteClientState {
    private static Map<ResourceLocation, Set<ResourceLocation>> routes = Map.of();

    private TaczRouteClientState() {
    }

    public static synchronized void replaceFromJson(String json) {
        JsonElement parsed = JsonParser.parseString(json == null || json.isBlank() ? "{}" : json);
        if (!parsed.isJsonObject()) return;
        Map<ResourceLocation, Set<ResourceLocation>> next = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
            ResourceLocation recipeId = ResourceLocation.tryParse(entry.getKey());
            if (recipeId == null || !entry.getValue().isJsonArray()) continue;
            Set<ResourceLocation> workbenches = new HashSet<>();
            for (JsonElement value : entry.getValue().getAsJsonArray()) {
                if (!value.isJsonPrimitive()) continue;
                ResourceLocation blockId = ResourceLocation.tryParse(value.getAsString());
                if (blockId != null) workbenches.add(blockId);
            }
            if (!workbenches.isEmpty()) next.put(recipeId, Set.copyOf(workbenches));
        }
        routes = Map.copyOf(next);
    }

    public static synchronized boolean hasRoute(ResourceLocation recipeId) {
        return recipeId != null && routes.containsKey(recipeId);
    }

    public static synchronized boolean allows(ResourceLocation recipeId, ResourceLocation blockId) {
        Set<ResourceLocation> allowed = routes.get(recipeId);
        return allowed != null && blockId != null && allowed.contains(blockId);
    }

    public static synchronized Set<ResourceLocation> routedRecipes(ResourceLocation blockId) {
        Set<ResourceLocation> result = new HashSet<>();
        if (blockId == null) return result;
        routes.forEach((recipeId, workbenches) -> {
            if (workbenches.contains(blockId)) result.add(recipeId);
        });
        return result;
    }
}
