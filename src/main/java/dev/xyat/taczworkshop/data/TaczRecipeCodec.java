package dev.xyat.taczworkshop.data;

import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AmmoItemDataAccessor;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class TaczRecipeCodec {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final Set<String> RESULT_TYPES = Set.of("gun", "attachment", "ammo", "melee", "throwable", "consumable", "custom");

    private TaczRecipeCodec() {
    }

    public static JsonObject toRoot(List<TaczRecipeRecord> records) {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        if (records != null) {
            for (TaczRecipeRecord record : records) {
                if (record != null) array.add(record.toJson());
            }
        }
        root.add("recipes", array);
        return root;
    }

    public static List<TaczRecipeRecord> readRoot(JsonObject root) {
        List<TaczRecipeRecord> records = new ArrayList<>();
        if (root == null || !root.has("recipes") || !root.get("recipes").isJsonArray()) return records;
        for (JsonElement element : root.getAsJsonArray("recipes")) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("recipe entry must be an object");
            records.add(TaczRecipeRecord.fromJson(element.getAsJsonObject()));
        }
        return records;
    }

    public static String encodeRecord(TaczRecipeRecord record) {
        return GSON.toJson(record.toJson());
    }

    public static TaczRecipeRecord decodeRecord(String json) {
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) throw new IllegalArgumentException("recipe payload must be an object");
        return TaczRecipeRecord.fromJson(element.getAsJsonObject());
    }

    public static String encodeRoot(List<TaczRecipeRecord> records) {
        return GSON.toJson(toRoot(records));
    }

    public static List<TaczRecipeRecord> decodeRoot(String json) {
        JsonElement element = JsonParser.parseString(json == null || json.isBlank() ? "{}" : json);
        if (!element.isJsonObject()) throw new IllegalArgumentException("recipe root must be an object");
        return readRoot(element.getAsJsonObject());
    }

    public static JsonObject nativeRecipeJson(TaczRecipeRecord record) {
        JsonObject nativeJson = new JsonObject();
        JsonArray materials = new JsonArray();
        for (TaczMaterial material : record.materials()) materials.add(material.toJson());
        nativeJson.add("materials", materials);
        nativeJson.add("result", nativeResult(record));
        return nativeJson;
    }

    public static String defaultExternalBaseItem(String type) {
        return switch (type == null ? "" : type) {
            case "melee" -> "lrtactical:melee";
            case "throwable" -> "lrtactical:throwable";
            case "consumable" -> "lrtactical:consumable";
            default -> "";
        };
    }

    private static boolean isExternalResultType(String type) {
        return "melee".equals(type) || "throwable".equals(type) || "consumable".equals(type);
    }

    private static String externalIdTag(String type) {
        return switch (type == null ? "" : type) {
            case "melee" -> "MeleeWeaponId";
            case "throwable" -> "ThrowableId";
            case "consumable" -> "ConsumableId";
            default -> "";
        };
    }

    private static JsonObject nativeResult(TaczRecipeRecord record) {
        JsonObject configured = record.result();
        String type = record.resultType();
        if (!isExternalResultType(type)) return configured.deepCopy();

        JsonObject result = new JsonObject();
        result.addProperty("type", "custom");
        JsonObject item = new JsonObject();
        String baseItem = configured.has("base_item") && configured.get("base_item").isJsonPrimitive()
                ? configured.get("base_item").getAsString()
                : defaultExternalBaseItem(type);
        item.addProperty("item", baseItem);
        item.addProperty("count", configured.has("count") ? Math.max(1, configured.get("count").getAsInt()) : 1);

        CompoundTag tag = new CompoundTag();
        if (configured.has("nbt")) {
            try {
                tag = TagParser.parseTag(nbtString(configured.get("nbt")));
            } catch (Exception exception) {
                throw new IllegalArgumentException("invalid NBT", exception);
            }
        }
        String idTag = externalIdTag(type);
        String id = configured.has("id") ? configured.get("id").getAsString() : "";
        if (!idTag.isBlank() && !id.isBlank()) tag.putString(idTag, id);
        if (!tag.isEmpty()) item.addProperty("nbt", tag.toString());
        result.add("item", item);
        if (configured.has("group") && configured.get("group").isJsonPrimitive()) {
            String group = configured.get("group").getAsString();
            if (!group.isBlank()) result.addProperty("group", group);
        }
        return result;
    }

    public static TaczRecipeRecord fromNativeRecipe(GunSmithTableRecipe recipe, List<String> workbenches, boolean enabled) {
        if (recipe == null || recipe.getId() == null) throw new IllegalArgumentException("recipe is missing id");
        List<TaczMaterial> materials = new ArrayList<>();
        for (GunSmithTableIngredient input : recipe.getInputs()) {
            materials.add(new TaczMaterial(input.getIngredient().toJson(), Math.max(1, input.getCount())));
        }
        JsonObject result = resultFromNative(recipe);
        return TaczRecipeRecord.original(recipe.getId().toString(), enabled, materials, result, workbenches);
    }

    public static GunSmithTableRecipe validateAndBuild(TaczRecipeRecord record) {
        validateRecordShape(record);
        ResourceLocation recipeId = KineticResourceIds.tryParse(record.id());
        if (recipeId == null) throw new IllegalArgumentException("invalid recipe id: " + record.id());

        TableRecipe tableRecipe = CommonAssetsManager.GSON.fromJson(nativeRecipeJson(record), TableRecipe.class);
        if (tableRecipe == null || tableRecipe.getMaterials() == null || tableRecipe.getResult() == null) {
            throw new IllegalArgumentException("TACZ rejected recipe payload");
        }

        GunSmithTableRecipe recipe = new GunSmithTableRecipe(recipeId, tableRecipe);
        recipe.init();
        if (recipe.getOutput() == null || recipe.getOutput().isEmpty()) {
            throw new IllegalArgumentException("TACZ result resolved to an empty item");
        }
        return recipe;
    }

    public static void validateAll(List<TaczRecipeRecord> records) {
        validateCollection(records);
        for (TaczRecipeRecord record : records) {
            if (record.enabled()) validateAndBuild(record);
        }
    }

    public static void validateCollection(List<TaczRecipeRecord> records) {
        Set<String> uuids = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (TaczRecipeRecord record : records) {
            validateIdentity(record);
            if (!uuids.add(record.uuid())) throw new IllegalArgumentException("duplicate uuid: " + record.uuid());
            if (!ids.add(record.id())) throw new IllegalArgumentException("duplicate recipe id: " + record.id());
        }
    }

    public static JsonObject resultFromStack(ItemStack source, int count, String group) {
        ItemStack stack = source == null ? ItemStack.EMPTY : source.copy();
        int safeCount = Math.max(1, count);
        JsonObject result = new JsonObject();

        if (!stack.isEmpty() && stack.getItem() instanceof IGun gun) {
            ResourceLocation id = gun.getGunId(stack);
            if (id != null && !DefaultAssets.EMPTY_GUN_ID.equals(id)) {
                result.addProperty("type", "gun");
                result.addProperty("id", id.toString());
                result.addProperty("count", safeCount);
                result.addProperty("ammo_count", Math.max(0, gun.getCurrentAmmoCount(stack)));

                JsonObject attachments = new JsonObject();
                addAttachment(attachments, "scope", gun.getAttachmentId(stack, AttachmentType.SCOPE));
                addAttachment(attachments, "muzzle", gun.getAttachmentId(stack, AttachmentType.MUZZLE));
                addAttachment(attachments, "stock", gun.getAttachmentId(stack, AttachmentType.STOCK));
                addAttachment(attachments, "grip", gun.getAttachmentId(stack, AttachmentType.GRIP));
                addAttachment(attachments, "laser", gun.getAttachmentId(stack, AttachmentType.LASER));
                addAttachment(attachments, "extended_mag", gun.getAttachmentId(stack, AttachmentType.EXTENDED_MAG));
                if (!attachments.entrySet().isEmpty()) result.add("attachments", attachments);

                CompoundTag extra = cleanGunNbt(stack.getTag());
                if (!extra.isEmpty()) result.addProperty("nbt", extra.toString());
                setGroup(result, group);
                return result;
            }
        }

        if (!stack.isEmpty() && stack.getItem() instanceof IAttachment attachment) {
            ResourceLocation id = attachment.getAttachmentId(stack);
            if (id != null && !DefaultAssets.EMPTY_ATTACHMENT_ID.equals(id)) {
                result.addProperty("type", "attachment");
                result.addProperty("id", id.toString());
                result.addProperty("count", safeCount);
                CompoundTag extra = stack.hasTag() && stack.getTag() != null ? stack.getTag().copy() : new CompoundTag();
                extra.remove(AttachmentItemDataAccessor.ATTACHMENT_ID_TAG);
                if (!extra.isEmpty()) result.addProperty("nbt", extra.toString());
                setGroup(result, group);
                return result;
            }
        }

        if (!stack.isEmpty() && stack.getItem() instanceof IAmmo ammo) {
            ResourceLocation id = ammo.getAmmoId(stack);
            if (id != null && !DefaultAssets.EMPTY_AMMO_ID.equals(id)) {
                result.addProperty("type", "ammo");
                result.addProperty("id", id.toString());
                result.addProperty("count", safeCount);
                CompoundTag extra = stack.hasTag() && stack.getTag() != null ? stack.getTag().copy() : new CompoundTag();
                extra.remove(AmmoItemDataAccessor.AMMO_ID_TAG);
                if (!extra.isEmpty()) result.addProperty("nbt", extra.toString());
                setGroup(result, group);
                return result;
            }
        }

        JsonObject external = externalResultFromStack(stack, safeCount, group);
        if (external != null) return external;

        result.addProperty("type", "custom");
        JsonObject item = new JsonObject();
        ResourceLocation itemId = stack.isEmpty() ? null : KineticRegistries.items().id(stack.getItem());
        item.addProperty("item", itemId == null ? "minecraft:air" : itemId.toString());
        item.addProperty("count", safeCount);
        if (!stack.isEmpty() && stack.hasTag() && stack.getTag() != null && !stack.getTag().isEmpty()) {
            item.addProperty("nbt", stack.getTag().toString());
        }
        result.add("item", item);
        setGroup(result, group);
        return result;
    }

    private static void setGroup(JsonObject result, String group) {
        if (group != null && !group.isBlank()) result.addProperty("group", group.trim());
    }

    public static ItemStack resultPreview(TaczRecipeRecord record) {
        try {
            GunSmithTableResult result = CommonAssetsManager.GSON.fromJson(nativeResult(record), GunSmithTableResult.class);
            if (result == null) return ItemStack.EMPTY;
            result.init();
            return result.getResult().copy();
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static ItemStack materialPreview(TaczMaterial material) {
        try {
            Ingredient ingredient = Ingredient.fromJson(material.ingredient());
            ItemStack[] items = ingredient.getItems();
            if (items.length == 0) return ItemStack.EMPTY;
            ItemStack result = items[0].copy();
            result.setCount(Math.max(1, material.count()));
            return result;
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static void validateNbt(String snbt) {
        if (snbt == null || snbt.isBlank()) return;
        try {
            TagParser.parseTag(snbt);
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid NBT", exception);
        }
    }

    private static JsonObject resultFromNative(GunSmithTableRecipe recipe) {
        ItemStack stack = recipe.getOutput().copy();
        JsonObject result = new JsonObject();
        result.addProperty("count", Math.max(1, stack.getCount()));
        if (recipe.getTab() != null) result.addProperty("group", recipe.getTab().toString());

        if (stack.getItem() instanceof IGun gun) {
            result.addProperty("type", "gun");
            result.addProperty("id", gun.getGunId(stack).toString());
            result.addProperty("ammo_count", Math.max(0, gun.getCurrentAmmoCount(stack)));
            JsonObject attachments = new JsonObject();
            addAttachment(attachments, "scope", gun.getAttachmentId(stack, AttachmentType.SCOPE));
            addAttachment(attachments, "muzzle", gun.getAttachmentId(stack, AttachmentType.MUZZLE));
            addAttachment(attachments, "stock", gun.getAttachmentId(stack, AttachmentType.STOCK));
            addAttachment(attachments, "grip", gun.getAttachmentId(stack, AttachmentType.GRIP));
            addAttachment(attachments, "laser", gun.getAttachmentId(stack, AttachmentType.LASER));
            addAttachment(attachments, "extended_mag", gun.getAttachmentId(stack, AttachmentType.EXTENDED_MAG));
            if (!attachments.entrySet().isEmpty()) result.add("attachments", attachments);
            CompoundTag extra = cleanGunNbt(stack.getTag());
            if (!extra.isEmpty()) result.addProperty("nbt", extra.toString());
            return result;
        }

        if (stack.getItem() instanceof IAttachment attachment) {
            result.addProperty("type", "attachment");
            result.addProperty("id", attachment.getAttachmentId(stack).toString());
            CompoundTag extra = stack.hasTag() && stack.getTag() != null ? stack.getTag().copy() : new CompoundTag();
            extra.remove(AttachmentItemDataAccessor.ATTACHMENT_ID_TAG);
            if (!extra.isEmpty()) result.addProperty("nbt", extra.toString());
            return result;
        }

        if (stack.getItem() instanceof IAmmo ammo) {
            result.addProperty("type", "ammo");
            result.addProperty("id", ammo.getAmmoId(stack).toString());
            CompoundTag extra = stack.hasTag() && stack.getTag() != null ? stack.getTag().copy() : new CompoundTag();
            extra.remove(AmmoItemDataAccessor.AMMO_ID_TAG);
            if (!extra.isEmpty()) result.addProperty("nbt", extra.toString());
            return result;
        }

        JsonObject external = externalResultFromStack(stack, Math.max(1, stack.getCount()), recipe.getTab() == null ? "" : recipe.getTab().toString());
        if (external != null) return external;

        result.addProperty("type", "custom");
        JsonObject item = new JsonObject();
        ResourceLocation itemId = KineticRegistries.items().id(stack.getItem());
        item.addProperty("item", itemId == null ? "minecraft:air" : itemId.toString());
        item.addProperty("count", Math.max(1, stack.getCount()));
        if (stack.hasTag() && stack.getTag() != null && !stack.getTag().isEmpty()) item.addProperty("nbt", stack.getTag().toString());
        result.add("item", item);
        return result;
    }

    private static JsonObject externalResultFromStack(ItemStack stack, int count, String group) {
        if (stack == null || stack.isEmpty() || stack.getTag() == null) return null;
        for (String type : List.of("melee", "throwable", "consumable")) {
            String key = externalIdTag(type);
            String id = stack.getTag().getString(key);
            if (KineticResourceIds.tryParse(id) == null) continue;

            JsonObject result = new JsonObject();
            result.addProperty("type", type);
            result.addProperty("id", id);
            result.addProperty("count", Math.max(1, count));
            ResourceLocation baseId = KineticRegistries.items().id(stack.getItem());
            result.addProperty("base_item", baseId == null ? defaultExternalBaseItem(type) : baseId.toString());
            CompoundTag extra = stack.getTag().copy();
            extra.remove(key);
            if (!extra.isEmpty()) result.addProperty("nbt", extra.toString());
            setGroup(result, group);
            return result;
        }
        return null;
    }

    private static CompoundTag cleanGunNbt(CompoundTag source) {
        CompoundTag extra = source == null ? new CompoundTag() : source.copy();
        extra.remove(GunItemDataAccessor.GUN_ID_TAG);
        extra.remove(GunItemDataAccessor.GUN_CURRENT_AMMO_COUNT_TAG);
        for (AttachmentType type : AttachmentType.values()) {
            if (type != AttachmentType.NONE) extra.remove(GunItemDataAccessor.GUN_ATTACHMENT_BASE + type.name());
        }
        return extra;
    }

    private static void addAttachment(JsonObject object, String key, ResourceLocation id) {
        if (id == null || DefaultAssets.EMPTY_ATTACHMENT_ID.equals(id)) return;
        object.addProperty(key, id.toString());
    }

    private static void validateRecordShape(TaczRecipeRecord record) {
        validateIdentity(record);
        if (record.materials().isEmpty() || record.materials().size() > 64) {
            throw new IllegalArgumentException("materials must contain 1 to 64 entries");
        }
        for (TaczMaterial material : record.materials()) {
            if (material.count() < 1 || material.count() > 999999) throw new IllegalArgumentException("invalid material count");
            Ingredient ingredient = Ingredient.fromJson(material.ingredient());
            if (ingredient == null) throw new IllegalArgumentException("invalid ingredient");
            validateIngredientNbt(material.ingredient());
        }
        String type = record.resultType();
        if (!RESULT_TYPES.contains(type)) throw new IllegalArgumentException("invalid result type");
        if ("custom".equals(type)) {
            if (!record.result().has("item") || !record.result().get("item").isJsonObject()) {
                throw new IllegalArgumentException("custom result is missing item");
            }
            JsonObject customItem = record.result().getAsJsonObject("item");
            if (customItem.has("nbt")) validateNbt(nbtString(customItem.get("nbt")));
        } else {
            if (!record.result().has("id") || KineticResourceIds.tryParse(record.result().get("id").getAsString()) == null) {
                throw new IllegalArgumentException("result id is invalid");
            }
            if (isExternalResultType(type) && record.result().has("base_item")) {
                String baseItem = record.result().get("base_item").getAsString();
                if (KineticResourceIds.tryParse(baseItem) == null) throw new IllegalArgumentException("external result base item is invalid");
            }
        }
        if (record.result().has("count")) {
            int count = record.result().get("count").getAsInt();
            if (count < 1 || count > 999999) throw new IllegalArgumentException("invalid result count");
        }
        if (record.result().has("nbt")) validateNbt(nbtString(record.result().get("nbt")));
        for (String workbench : record.workbenches()) {
            if (KineticResourceIds.tryParse(workbench) == null) throw new IllegalArgumentException("invalid workbench id: " + workbench);
        }
    }

    private static void validateIdentity(TaczRecipeRecord record) {
        if (record == null) throw new IllegalArgumentException("recipe is null");
        try {
            UUID.fromString(record.uuid());
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid uuid");
        }
        if (KineticResourceIds.tryParse(record.id()) == null) throw new IllegalArgumentException("invalid recipe id");
        if (record.isReplacement() && KineticResourceIds.tryParse(record.originalId()) == null) {
            throw new IllegalArgumentException("replacement original id is invalid");
        }
    }

    private static void validateIngredientNbt(JsonElement ingredient) {
        if (ingredient == null || ingredient.isJsonNull()) return;
        if (ingredient.isJsonArray()) {
            for (JsonElement child : ingredient.getAsJsonArray()) validateIngredientNbt(child);
            return;
        }
        if (!ingredient.isJsonObject()) return;
        JsonObject object = ingredient.getAsJsonObject();
        if (object.has("nbt")) validateNbt(nbtString(object.get("nbt")));
    }

    public static String nbtString(JsonElement element) {
        if (element == null || element.isJsonNull()) return "";
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) return element.getAsString();
        return element.toString();
    }
}
