package dev.xyat.taczworkshop.server;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.xyat.taczworkshop.data.TaczRecipeCodec;
import dev.xyat.taczworkshop.data.TaczRecipeRecord;
import net.minecraft.resources.ResourceLocation;
import dev.xyat.kineticcore.api.runtime.KineticPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TaczRecipeStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path DIRECTORY = KineticPaths.configDirectory().resolve("kineticcore");
    private static final Path FILE = DIRECTORY.resolve("taczrecipes.json");
    private static final Path BACKUP = DIRECTORY.resolve("taczrecipes.json.bak");
    private static boolean lastLoadHealthy = true;

    private TaczRecipeStore() {
    }

    public record State(List<TaczRecipeRecord> recipes, Set<String> disabledOriginals) {
        public State copy() {
            List<TaczRecipeRecord> recipeCopies = new ArrayList<>();
            for (TaczRecipeRecord recipe : recipes) recipeCopies.add(recipe.copy());
            return new State(recipeCopies, new LinkedHashSet<>(disabledOriginals));
        }
    }

    public static Path file() {
        return FILE;
    }

    public static synchronized State loadState() {
        if (!Files.exists(FILE)) {
            lastLoadHealthy = true;
            return new State(new ArrayList<>(), new LinkedHashSet<>());
        }
        try {
            String raw = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(raw.isBlank() ? "{}" : raw);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("root is not an object");
            JsonObject root = parsed.getAsJsonObject();
            List<TaczRecipeRecord> records = TaczRecipeCodec.readRoot(root);
            Set<String> disabled = new LinkedHashSet<>();
            if (root.has("disabled_originals") && root.get("disabled_originals").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("disabled_originals")) {
                    if (!element.isJsonPrimitive()) continue;
                    String id = element.getAsString().trim();
                    if (KineticResourceIds.tryParse(id) != null) disabled.add(id);
                }
            }
            lastLoadHealthy = true;
            return new State(records, disabled);
        } catch (Exception exception) {
            lastLoadHealthy = false;
            LOGGER.error("Unable to read {}", FILE, exception);
            return new State(new ArrayList<>(), new LinkedHashSet<>());
        }
    }

    public static synchronized List<TaczRecipeRecord> load() {
        return loadState().copy().recipes();
    }

    public static synchronized void saveState(State state) throws IOException {
        State copies = state == null ? new State(new ArrayList<>(), new LinkedHashSet<>()) : state.copy();
        TaczRecipeCodec.validateCollection(copies.recipes());
        for (String id : copies.disabledOriginals()) {
            if (KineticResourceIds.tryParse(id) == null) throw new IllegalArgumentException("invalid disabled original id: " + id);
        }
        Files.createDirectories(DIRECTORY);
        Path temp = DIRECTORY.resolve("taczrecipes.json.tmp");
        JsonObject root = TaczRecipeCodec.toRoot(copies.recipes());
        JsonArray disabled = new JsonArray();
        copies.disabledOriginals().forEach(disabled::add);
        root.add("disabled_originals", disabled);
        Files.writeString(temp, TaczRecipeCodec.GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
        if (Files.exists(FILE)) Files.copy(FILE, BACKUP, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unsupportedAtomicMove) {
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static synchronized TaczRecipeRecord saveEdited(TaczRecipeRecord record) throws IOException {
        if (record == null) throw new IllegalArgumentException("recipe is null");
        State state = loadState();
        requireHealthyLoad();
        List<TaczRecipeRecord> records = state.recipes();
        Set<String> disabled = state.disabledOriginals();
        TaczRecipeRecord stored;

        if (record.hasIssue()) {
            stored = record.resolvedCopy();
            TaczRecipeCodec.validateAndBuild(stored);
            boolean replaced = false;
            for (int i = 0; i < records.size(); i++) {
                TaczRecipeRecord existing = records.get(i);
                if (existing.uuid().equals(stored.uuid()) || existing.id().equals(stored.id())) {
                    records.set(i, stored.copy());
                    replaced = true;
                    break;
                }
            }
            if (!replaced) records.add(stored.copy());
        } else if (record.isOriginal()) {
            String originalId = record.id();
            stored = record.asCreatedFromOriginal(createdId(originalId, records));
            disabled.add(originalId);
            if (stored.enabled()) TaczRecipeCodec.validateAndBuild(stored);
            records.add(stored.copy());
        } else {
            stored = record.copy();
            if (stored.isReplacement()) stored.setOrigin(TaczRecipeRecord.ORIGIN_CUSTOM);
            if (stored.enabled()) TaczRecipeCodec.validateAndBuild(stored);
            boolean replaced = false;
            for (int i = 0; i < records.size(); i++) {
                if (records.get(i).uuid().equals(stored.uuid())) {
                    records.set(i, stored.copy());
                    replaced = true;
                    break;
                }
            }
            if (!replaced) records.add(stored.copy());
        }
        saveState(new State(records, disabled));
        return stored.copy();
    }

    public static synchronized void setOriginalDisabled(String originalId, boolean disabled) throws IOException {
        if (KineticResourceIds.tryParse(originalId) == null) throw new IllegalArgumentException("invalid original recipe id");
        State state = loadState();
        requireHealthyLoad();
        if (disabled) state.disabledOriginals().add(originalId);
        else state.disabledOriginals().remove(originalId);
        saveState(state);
    }

    public static synchronized boolean restoreOriginal(String originalId) throws IOException {
        return restoreOriginal(originalId, "");
    }

    public static synchronized boolean restoreOriginal(String originalId, String replacementUuid) throws IOException {
        if (KineticResourceIds.tryParse(originalId) == null) throw new IllegalArgumentException("invalid original recipe id");
        State state = loadState();
        requireHealthyLoad();
        boolean changed = state.disabledOriginals().remove(originalId);
        if (changed) saveState(state);
        return changed;
    }

    public static synchronized boolean delete(String uuid) throws IOException {
        State state = loadState();
        requireHealthyLoad();
        boolean removed = state.recipes().removeIf(record -> record.uuid().equals(uuid));
        if (removed) saveState(state);
        return removed;
    }

    private static String createdId(String originalId, List<TaczRecipeRecord> records) {
        ResourceLocation source = KineticResourceIds.tryParse(originalId);
        if (source == null) throw new IllegalArgumentException("invalid original recipe id");
        String base = "taczworkshop:created/" + source.getNamespace() + "/" + source.getPath();
        String candidate = base;
        int suffix = 2;
        while (containsRecipeId(records, candidate)) candidate = base + "_" + suffix++;
        ResourceLocation created = KineticResourceIds.tryParse(candidate);
        if (created == null) throw new IllegalArgumentException("unable to create recipe id");
        return created.toString();
    }

    private static boolean containsRecipeId(List<TaczRecipeRecord> records, String id) {
        for (TaczRecipeRecord record : records) {
            if (record != null && id.equals(record.id())) return true;
        }
        return false;
    }

    private static void requireHealthyLoad() throws IOException {
        if (!lastLoadHealthy) throw new IOException("taczrecipes.json could not be read; refusing to overwrite it");
    }
}
