package dev.xyat.taczworkshop.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public final class TaczMaterial {
    private JsonElement ingredient;
    private int count;

    public TaczMaterial(JsonElement ingredient, int count) {
        this.ingredient = ingredient == null || ingredient.isJsonNull() ? new JsonObject() : ingredient.deepCopy();
        this.count = Math.max(1, count);
    }

    public JsonElement ingredient() {
        return ingredient;
    }

    public void setIngredient(JsonElement ingredient) {
        this.ingredient = ingredient == null || ingredient instanceof JsonNull ? new JsonObject() : ingredient.deepCopy();
    }

    public int count() {
        return count;
    }

    public void setCount(int count) {
        this.count = Math.max(1, count);
    }

    public TaczMaterial copy() {
        return new TaczMaterial(ingredient, count);
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.add("item", ingredient.deepCopy());
        object.addProperty("count", count);
        return object;
    }

    public static TaczMaterial fromJson(JsonObject object) {
        JsonElement element = object.get("item");
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("material item is missing");
        }
        int count = object.has("count") ? object.get("count").getAsInt() : 1;
        return new TaczMaterial(element, count);
    }
}
