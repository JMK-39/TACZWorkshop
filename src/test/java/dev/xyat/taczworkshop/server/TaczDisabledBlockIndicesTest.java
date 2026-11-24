package dev.xyat.taczworkshop.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TaczDisabledBlockIndicesTest {
    @Test void emptyOverrideDisablesOnlyThatBlockIndex() throws Exception {
        Map<String, JsonElement> indices = new LinkedHashMap<>();
        indices.put("pgp:printer", JsonParser.parseString("{}"));
        var enabled = JsonParser.parseString("{\"data\":\"pack:table\",\"name\":\"Custom table\"}");
        indices.put("pack:table", enabled);
        apply(indices);
        assertFalse(indices.containsKey("pgp:printer"));
        assertSame(enabled, indices.get("pack:table"));
    }

    @Test void invalidNonEmptyInputsRemainForNativeValidation() throws Exception {
        Map<String, JsonElement> indices = new LinkedHashMap<>();
        indices.put("pack:null", JsonNull.INSTANCE);
        indices.put("pack:scalar", JsonParser.parseString("17"));
        indices.put("pack:incomplete", JsonParser.parseString("{\"name\":\"Missing data\"}"));
        var original = new LinkedHashMap<>(indices);
        apply(indices);
        assertEquals(original, indices);
    }

    private static void apply(Map<String, JsonElement> indices) throws Exception {
        try {
            Class.forName("dev.xyat.taczworkshop.server.TaczDisabledBlockIndices")
                    .getMethod("removeEmptyOverrides", Map.class).invoke(null, indices);
        } catch (ClassNotFoundException absent) {
            throw new AssertionError("Empty block-index overrides must disable entries before native parsing", absent);
        }
    }
}
