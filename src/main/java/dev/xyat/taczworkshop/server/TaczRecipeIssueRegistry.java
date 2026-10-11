package dev.xyat.taczworkshop.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Diagnostics retained for data repair; no editor or recipe mutation state. */
public final class TaczRecipeIssueRegistry {
    private static final Map<ResourceLocation, Issue> ISSUES = new LinkedHashMap<>();
    private TaczRecipeIssueRegistry() { }
    public record Issue(ResourceLocation id, String code, String detail, JsonElement raw) {
        public Issue copy() { return new Issue(id, code, detail, raw.deepCopy()); }
    }
    public static synchronized void beginReload() { ISSUES.clear(); }
    public static synchronized void record(ResourceLocation id, String code, String detail, JsonElement raw) {
        if (id != null) ISSUES.put(id, new Issue(id, code == null ? "" : code.trim(),
                detail == null ? "" : detail.trim(), raw == null ? JsonNull.INSTANCE : raw.deepCopy()));
    }
    public static synchronized void remove(ResourceLocation id) { ISSUES.remove(id); }
    public static synchronized int size() { return ISSUES.size(); }
    public static synchronized List<Issue> snapshot() { return ISSUES.values().stream().map(Issue::copy).toList(); }
}