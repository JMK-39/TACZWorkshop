package dev.xyat.taczworkshop.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacyTaczRecipeMigrationTest {
    @TempDir Path directory;
    private static final String LEGACY = """
            {"recipes":[
              {"id":"test:gun","materials":[{"item":{"tag":"forge:ingots/iron"},"count":3}],
               "result":{"type":"gun","id":"tacz:ak47","count":1}},
              {"id":"test:existing","materials":[],"result":{"type":"ammo","id":"tacz:762x39","count":5}},
              {"id":"test:disabled","enabled":false,"materials":[],"result":{"type":"gun","id":"tacz:ak47"}}
            ],"disabled_originals":["test:removed","test:gun"]}
            """;

    @Test void importsNativeRecipesAndRemovalsWithoutReplacingNewEditorChanges() throws Exception {
        write("taczrecipes.json", LEGACY);
        write("datapack/data/contentstudio/recipe_overrides.json", "{\"test:existing\":{\"type\":\"test:newer\"}}");
        write("datapack/data/contentstudio/recipe/recipe_bundle.json", "{\"version\":1,\"recipes\":[],\"removals\":[],\"custom\":7}");
        LegacyTaczRecipeMigration.migrate(directory);
        JsonObject nativeRecipes = read("datapack/data/contentstudio/recipe_overrides.json");
        assertEquals("tacz:gun_smith_table_crafting", nativeRecipes.getAsJsonObject("test:gun").get("type").getAsString());
        assertEquals(3, nativeRecipes.getAsJsonObject("test:gun").getAsJsonArray("materials").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals("test:newer", nativeRecipes.getAsJsonObject("test:existing").get("type").getAsString());
        assertFalse(nativeRecipes.has("test:disabled"));
        JsonObject bundle = read("datapack/data/contentstudio/recipe/recipe_bundle.json");
        assertEquals(7, bundle.get("custom").getAsInt());
        assertEquals(2, bundle.getAsJsonArray("removals").size());
        assertEquals("RECIPE_ID", bundle.getAsJsonArray("removals").get(0).getAsJsonObject().get("mode").getAsString());
        assertEquals("test:removed", bundle.getAsJsonArray("removals").get(0).getAsJsonObject().get("value").getAsString());
        assertEquals(LEGACY, Files.readString(directory.resolve("taczrecipes.json")));
        assertEquals(LEGACY, Files.readString(directory.resolve("taczrecipes.json.pre-contentstudio.bak")));
        assertTrue(Files.exists(directory.resolve("taczrecipes-migrated.json")));
    }

    @Test void completedMigrationDoesNotResurrectRecipesDeletedInNewEditor() throws Exception {
        write("taczrecipes.json", LEGACY);
        LegacyTaczRecipeMigration.migrate(directory);
        write("datapack/data/contentstudio/recipe_overrides.json", "{}");
        LegacyTaczRecipeMigration.migrate(directory);
        assertEquals(0, read("datapack/data/contentstudio/recipe_overrides.json").size());
    }

    @Test void corruptDestinationIsPreservedAndCanBeRetriedAfterRepair() throws Exception {
        write("taczrecipes.json", LEGACY);
        write("datapack/data/contentstudio/recipe_overrides.json", "{broken");
        assertThrows(IOException.class, () -> LegacyTaczRecipeMigration.migrate(directory));
        assertEquals("{broken", Files.readString(directory.resolve("datapack/data/contentstudio/recipe_overrides.json")));
        assertFalse(Files.exists(directory.resolve("taczrecipes-migrated.json")));
        write("datapack/data/contentstudio/recipe_overrides.json", "{}");
        LegacyTaczRecipeMigration.migrate(directory);
        assertTrue(read("datapack/data/contentstudio/recipe_overrides.json").has("test:gun"));
    }

    @Test void badRecordIsRetainedWhileValidRecipesStillImport() throws Exception {
        write("taczrecipes.json", LEGACY.replace("\"recipes\":[", "\"recipes\":[{\"id\":\"INVALID ID\"},"));
        LegacyTaczRecipeMigration.migrate(directory);
        assertTrue(read("datapack/data/contentstudio/recipe_overrides.json").has("test:gun"));
        assertEquals(1, read("taczrecipes-migrated.json").getAsJsonArray("unusable_records").size());
        assertTrue(Files.readString(directory.resolve("taczrecipes.json")).contains("INVALID ID"));
    }

    @Test void unsupportedWorkbenchRoutingIsNotSilentlyDiscarded() throws Exception {
        write("taczrecipes.json", LEGACY.replace("\"id\":\"test:gun\"", "\"id\":\"test:gun\",\"workbenches\":[\"test:table\"]"));
        assertThrows(IOException.class, () -> LegacyTaczRecipeMigration.migrate(directory));
        assertFalse(Files.exists(directory.resolve("taczrecipes-migrated.json")));
        assertFalse(Files.exists(directory.resolve("datapack/data/contentstudio/recipe_overrides.json")));
    }

    @Test void malformedSourceDoesNotCreateCompletionMarker() throws Exception {
        write("taczrecipes.json", "{\"recipes\":{}}");
        assertThrows(IOException.class, () -> LegacyTaczRecipeMigration.migrate(directory));
        assertFalse(Files.exists(directory.resolve("taczrecipes-migrated.json")));
    }

    @Test void oversizedDestinationNeverReplacesOtherFiles() throws Exception {
        write("taczrecipes.json", LEGACY);
        write("datapack/data/contentstudio/recipe/recipe_bundle.json", "{\"recipes\":[],\"removals\":[],\"note\":\"" + "x".repeat(8_000_000) + "\"}");
        assertThrows(IOException.class, () -> LegacyTaczRecipeMigration.migrate(directory));
        assertFalse(Files.exists(directory.resolve("datapack/data/contentstudio/recipe_overrides.json")));
        assertFalse(Files.exists(directory.resolve("taczrecipes-migrated.json")));
    }

    @Test void unusableReplacementDoesNotRestoreExplicitlyRemovedOriginal() throws Exception {
        write("taczrecipes.json", "{\"recipes\":[{\"id\":\"test:broken\",\"materials\":[]}],\"disabled_originals\":[\"test:broken\"]}");
        LegacyTaczRecipeMigration.migrate(directory);
        assertEquals(1, read("datapack/data/contentstudio/recipe/recipe_bundle.json").getAsJsonArray("removals").size());
    }

    @Test void recipesOutsideTargetEditorLimitsAreNotMarkedAsSuccessfullyMigrated() throws Exception {
        JsonObject legacy = JsonParser.parseString(LEGACY).getAsJsonObject();
        legacy.getAsJsonArray("recipes").get(0).getAsJsonObject().getAsJsonObject("result").addProperty("nbt", "x".repeat(12_000));
        write("taczrecipes.json", legacy.toString());
        assertThrows(IOException.class, () -> LegacyTaczRecipeMigration.migrate(directory));
        assertFalse(Files.exists(directory.resolve("taczrecipes-migrated.json")));
        assertFalse(Files.exists(directory.resolve("datapack/data/contentstudio/recipe_overrides.json")));
    }

    private void write(String relative, String value) throws IOException {
        Path file = directory.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, value);
    }
    private JsonObject read(String relative) throws IOException {
        return JsonParser.parseString(Files.readString(directory.resolve(relative))).getAsJsonObject();
    }
}
