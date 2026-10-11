package dev.xyat.taczworkshop.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import dev.xyat.taczworkshop.TaczWorkshop;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** One-time bridge; subsequent recipe editing belongs to ContentStudio. */
public final class LegacyTaczRecipeMigration {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int MAX_BYTES = 8_000_000;
    private static final Set<String> RESULT_TYPES = Set.of("gun", "attachment", "ammo", "custom", "melee", "throwable", "consumable");

    private LegacyTaczRecipeMigration() {}

    public static void migrateInstalled() {
        if (!KineticPlatform.isModLoaded("contentstudio")) return;
        try {
            Path directory = KineticPlatform.configDirectory().resolve("kineticcore");
            Class<?> store = Class.forName("dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeStore");
            // Mod constructors run in parallel. Share the native store's static
            // synchronization monitor so relocation cannot overwrite a migration.
            synchronized (store) {
                Path installedFile = (Path) store.getMethod("file").invoke(null);
                if (!installedFile.equals(directory.resolve("datapack/data/contentstudio/recipe/recipe_overrides.json"))) {
                    throw new IOException("Update ContentStudio to the version using data/contentstudio/recipe before migrating TACZ recipes");
                }
                var document = Class.forName("dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeDocument").getConstructor(JsonObject.class);
                migrate(directory, recipe -> {
                    try { document.newInstance(recipe); }
                    catch (ReflectiveOperationException exception) {
                        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                        throw new IllegalArgumentException(cause.getMessage(), cause);
                    }
                });
            }
        } catch (Exception exception) {
            TaczWorkshop.LOGGER.error("Legacy TACZ recipe migration or cleanup failed. Repair the reported file and restart to retry; completed migration data remains in ContentStudio", exception);
        }
    }

    public static void migrate(Path directory) throws IOException {
        migrate(directory, recipe -> {
            validateStructure(recipe, 0, new int[]{0});
            if (recipe.toString().length() > 12_000) throw new IllegalArgumentException("Recipe exceeds editor size limit");
        });
    }

    private static void migrate(Path directory, Consumer<JsonObject> validateTarget) throws IOException {
        Path source = directory.resolve("taczrecipes.json");
        Path marker = directory.resolve("taczrecipes-migrated.json");
        if (!Files.isRegularFile(source) && !Files.exists(marker)) return;
        Path oldOverridesFile = directory.resolve("datapack/data/contentstudio/recipe_overrides.json");
        Path overridesFile = directory.resolve("datapack/data/contentstudio/recipe/recipe_overrides.json");
        Path bundleFile = directory.resolve("datapack/data/contentstudio/recipe/recipe_bundle.json");
        JsonObject overrides = readObject(oldOverridesFile);
        readObject(overridesFile).entrySet().forEach(entry -> overrides.add(entry.getKey(), entry.getValue().deepCopy()));
        if (Files.exists(marker)) {
            JsonObject completion = readObject(marker);
            if (!completion.has("version") || completion.get("version").getAsInt() != 1) {
                throw new IOException(marker + ": unknown migration status");
            }
            array(completion, "unusable_records", true);
            readObject(bundleFile);
            if (Files.exists(oldOverridesFile)) writeAtomic(overridesFile, encode(overrides, overridesFile));
            cleanupMigratedSource(source, oldOverridesFile, overridesFile, bundleFile);
            return;
        }
        JsonObject legacy = readObject(source);
        JsonArray records;
        try { records = array(legacy, "recipes", true); }
        catch (IOException exception) { throw new IOException(source + ": " + exception.getMessage(), exception); }
        JsonObject bundle = readObject(bundleFile);
        JsonArray removals = array(bundle, "removals", false);
        array(bundle, "recipes", false);

        // Legacy per-workbench routing cannot be expressed by a native recipe alone.
        // Refuse the conversion instead of broadening a restricted recipe silently.
        for (JsonElement element : records) {
            if (!element.isJsonObject()) continue;
            JsonObject record = element.getAsJsonObject();
            if (record.has("enabled") && !record.get("enabled").getAsBoolean()) continue;
            JsonElement routes = record.get("workbenches");
            if (routes != null && (!routes.isJsonArray() || !routes.getAsJsonArray().isEmpty())) {
                throw new IOException(source + ": per-workbench routing is not supported by ContentStudio native recipes: " + record.get("id"));
            }
        }

        Set<String> seen = new HashSet<>();
        JsonArray unusable = new JsonArray();
        int imported = 0;
        for (JsonElement element : records) {
            try {
                JsonObject record = element.getAsJsonObject();
                if (record.has("enabled") && !record.get("enabled").getAsBoolean()) continue;
                if ("original".equals(text(record, "origin"))) continue;
                String id = resourceId(text(record, "id"));
                if (!seen.add(id)) throw new IllegalArgumentException("Duplicate recipe ID: " + id);
                if (overrides.has(id)) continue;
                JsonArray materials = array(record, "materials", true);
                for (JsonElement material : materials) {
                    JsonObject value = material.getAsJsonObject();
                    if (!value.has("item") || value.get("item").isJsonNull() || !value.has("count") || value.get("count").getAsInt() < 1) {
                        throw new IllegalArgumentException("Invalid material or count");
                    }
                }
                JsonObject recipe = new JsonObject();
                recipe.addProperty("type", TaczRecipePreflight.TACZ_RECIPE_TYPE);
                recipe.add("materials", materials.deepCopy());
                recipe.add("result", nativeResult(record.getAsJsonObject("result")));
                try { validateTarget.accept(recipe); }
                catch (RuntimeException exception) { throw new TargetFormatException(source + ": recipe " + id + " cannot be loaded by ContentStudio: " + exception.getMessage(), exception); }
                overrides.add(id, recipe);
                imported++;
            } catch (TargetFormatException exception) {
                throw exception;
            } catch (Exception exception) {
                JsonObject issue = new JsonObject();
                issue.add("record", element.deepCopy());
                issue.addProperty("reason", exception.getMessage());
                unusable.add(issue);
            }
        }

        Set<String> removedIds = new HashSet<>();
        for (JsonElement element : removals) {
            if (!element.isJsonObject()) continue;
            JsonObject removal = element.getAsJsonObject();
            if ("RECIPE_ID".equals(text(removal, "mode"))) removedIds.add(text(removal, "value"));
        }
        int importedRemovals = 0;
        for (JsonElement element : array(legacy, "disabled_originals", false)) {
            String id;
            try { id = resourceId(element.getAsString()); }
            catch (Exception exception) { throw new IOException(source + ": invalid disabled original recipe ID", exception); }
            if (!removedIds.add(id)) continue;
            JsonObject removal = new JsonObject();
            removal.addProperty("mode", "RECIPE_ID");
            removal.addProperty("value", id);
            removals.add(removal);
            importedRemovals++;
        }
        if (!bundle.has("version")) bundle.addProperty("version", 1);
        JsonObject completion = new JsonObject();
        completion.addProperty("version", 1);
        completion.addProperty("imported_recipes", imported);
        completion.addProperty("imported_removals", importedRemovals);
        completion.add("unusable_records", unusable);

        // Check all output sizes before replacing either destination.
        byte[] encodedRecipes = encode(overrides, overridesFile);
        byte[] encodedBundle = encode(bundle, bundleFile);
        byte[] encodedMarker = encode(completion, marker);
        writeAtomic(overridesFile, encodedRecipes);
        writeAtomic(bundleFile, encodedBundle);
        writeAtomic(marker, encodedMarker);
        cleanupMigratedSource(source, oldOverridesFile, overridesFile, bundleFile);
        TaczWorkshop.LOGGER.info("Migrated {} legacy TACZ recipes and {} removals to ContentStudio; {} unusable records retained in {}",
                imported, importedRemovals, unusable.size(), marker);
    }

    private static JsonObject nativeResult(JsonObject configured) throws Exception {
        if (configured == null) throw new IllegalArgumentException("Missing result");
        String type = text(configured, "type");
        if (!RESULT_TYPES.contains(type)) throw new IllegalArgumentException("Unknown result type: " + type);
        if (!Set.of("melee", "throwable", "consumable").contains(type)) return configured.deepCopy();
        JsonObject result = new JsonObject();
        result.addProperty("type", "custom");
        JsonObject item = new JsonObject();
        String baseItem = text(configured, "base_item");
        item.addProperty("item", resourceId(baseItem.isBlank() ? "lrtactical:" + type : baseItem));
        item.addProperty("count", configured.has("count") ? Math.max(1, configured.get("count").getAsInt()) : 1);
        CompoundTag tag = new CompoundTag();
        if (configured.has("nbt")) {
            JsonElement nbt = configured.get("nbt");
            tag = TagParser.parseTag(nbt.isJsonPrimitive() ? nbt.getAsString() : nbt.toString());
        }
        String tagKey = switch (type) {
            case "melee" -> "MeleeWeaponId";
            case "throwable" -> "ThrowableId";
            default -> "ConsumableId";
        };
        tag.putString(tagKey, resourceId(text(configured, "id")));
        item.addProperty("nbt", tag.toString());
        result.add("item", item);
        if (configured.has("group")) result.add("group", configured.get("group").deepCopy());
        return result;
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString().trim() : "";
    }

    // Standalone migration checks use the current structural contract; installed
    // migrations validate through the actual ContentStudio version instead.
    private static void validateStructure(JsonElement value, int depth, int[] count) {
        if (depth > 32 || ++count[0] > 2048) throw new IllegalArgumentException("Recipe structure exceeds editor limits");
        if (value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(entry -> validateStructure(entry.getValue(), depth + 1, count));
        else if (value.isJsonArray()) value.getAsJsonArray().forEach(element -> validateStructure(element, depth + 1, count));
    }

    private static final class TargetFormatException extends IOException {
        private TargetFormatException(String message, Throwable cause) { super(message, cause); }
    }

    private static String resourceId(String value) {
        String id = value.contains(":") ? value : "minecraft:" + value;
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("Invalid resource ID: " + value);
        return id;
    }

    private static JsonArray array(JsonObject object, String key, boolean required) throws IOException {
        if (!object.has(key)) {
            if (required) throw new IOException("Missing array: " + key);
            object.add(key, new JsonArray());
        }
        if (!object.get(key).isJsonArray()) throw new IOException("Expected array: " + key);
        return object.getAsJsonArray(key);
    }

    private static JsonObject readObject(Path file) throws IOException {
        if (!Files.exists(file)) return new JsonObject();
        if (Files.size(file) > MAX_BYTES) throw new IOException(file + ": exceeds 8 MB");
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("Expected object root");
            return parsed.getAsJsonObject();
        } catch (Exception exception) {
            throw new IOException("Unable to migrate " + file + ": " + exception.getMessage(), exception);
        }
    }

    private static byte[] encode(JsonObject object, Path file) throws IOException {
        byte[] bytes = (GSON.toJson(object) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException(file + ": migration would exceed 8 MB");
        return bytes;
    }

    private static void cleanupMigratedSource(Path source, Path oldOverrides, Path overrides, Path bundle) throws IOException {
        Files.deleteIfExists(source);
        Files.deleteIfExists(oldOverrides);
        Files.deleteIfExists(source.resolveSibling(source.getFileName() + ".pre-contentstudio.bak"));
        for (Path file : new Path[]{oldOverrides, overrides, bundle}) {
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".pre-taczworkshop.bak"));
        }
    }

    private static void writeAtomic(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
