package dev.xyat.taczworkshop.server;

import com.google.gson.JsonElement;
import java.util.Map;

/** Empty block-index override files disable the index instead of defining an incomplete native workbench. */
public final class TaczDisabledBlockIndices {
    private TaczDisabledBlockIndices() { }

    /** Removes only explicit empty-object overrides; malformed nonempty definitions retain native diagnostics. */
    public static void removeEmptyOverrides(Map<?, JsonElement> indices) {
        indices.values().removeIf(value -> value != null && value.isJsonObject() && value.getAsJsonObject().entrySet().isEmpty());
    }
}
