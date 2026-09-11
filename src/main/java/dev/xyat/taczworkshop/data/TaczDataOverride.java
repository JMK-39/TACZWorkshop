package dev.xyat.taczworkshop.data;

import com.google.gson.JsonObject;

public final class TaczDataOverride {
    private final TaczDataKind kind;
    private final String id;
    private final String dataId;
    private final boolean removed;
    private final JsonObject data;

    public TaczDataOverride(TaczDataKind kind, String id, String dataId, boolean removed, JsonObject data) {
        this.kind = kind;
        this.id = id == null ? "" : id.trim();
        this.dataId = dataId == null ? "" : dataId.trim();
        this.removed = removed;
        this.data = data == null ? null : data.deepCopy();
    }

    public TaczDataKind kind() {
        return kind;
    }

    public String id() {
        return id;
    }

    public String dataId() {
        return dataId;
    }

    public boolean removed() {
        return removed;
    }

    public JsonObject data() {
        return data == null ? null : data.deepCopy();
    }

    public TaczDataOverride copy() {
        return new TaczDataOverride(kind, id, dataId, removed, data);
    }
}
