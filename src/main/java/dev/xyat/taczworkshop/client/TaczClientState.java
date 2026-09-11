package dev.xyat.taczworkshop.client;

import dev.xyat.taczworkshop.data.TaczRecipeCodec;
import dev.xyat.taczworkshop.data.TaczRecipeRecord;

import java.util.ArrayList;
import java.util.List;

public final class TaczClientState {
    private static List<TaczRecipeRecord> recipes = List.of();
    private static long revision;

    private TaczClientState() {
    }

    public static synchronized void replaceFromJson(String json) {
        List<TaczRecipeRecord> parsed = TaczRecipeCodec.decodeRoot(json);
        List<TaczRecipeRecord> copies = new ArrayList<>();
        for (TaczRecipeRecord record : parsed) copies.add(record.copy());
        recipes = List.copyOf(copies);
        revision++;
    }

    public static synchronized List<TaczRecipeRecord> snapshot() {
        List<TaczRecipeRecord> copies = new ArrayList<>();
        for (TaczRecipeRecord record : recipes) copies.add(record.copy());
        return copies;
    }

    public static synchronized long revision() {
        return revision;
    }
}
