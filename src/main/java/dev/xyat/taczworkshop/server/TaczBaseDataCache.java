package dev.xyat.taczworkshop.server;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.xyat.taczworkshop.data.TaczDataKind;
import dev.xyat.taczworkshop.data.TaczDataOverride;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TaczBaseDataCache {
    public enum Domain {
        GUN_DATA,
        ATTACHMENT_DATA,
        GUN_INDEX,
        ATTACHMENT_INDEX,
        AMMO_INDEX,
        LR_MELEE_INDEX,
        LR_THROWABLE_INDEX,
        LR_CONSUMABLE_INDEX
    }

    private static final Map<Domain, Map<ResourceLocation, JsonObject>> BASE = new EnumMap<>(Domain.class);

    static {
        for (Domain domain : Domain.values()) BASE.put(domain, new LinkedHashMap<>());
    }

    private TaczBaseDataCache() {
    }

    public static Domain domainForClass(Class<?> dataClass) {
        if (dataClass == null) return null;
        return switch (dataClass.getSimpleName()) {
            case "GunData" -> Domain.GUN_DATA;
            case "AttachmentData" -> Domain.ATTACHMENT_DATA;
            case "CommonGunIndex" -> Domain.GUN_INDEX;
            case "CommonAttachmentIndex" -> Domain.ATTACHMENT_INDEX;
            case "CommonAmmoIndex" -> Domain.AMMO_INDEX;
            case "MeleeWeaponIndex" -> Domain.LR_MELEE_INDEX;
            case "ThrowableIndex" -> Domain.LR_THROWABLE_INDEX;
            case "ConsumableIndex" -> Domain.LR_CONSUMABLE_INDEX;
            default -> null;
        };
    }

    public static synchronized void captureAndApply(Domain domain, Map<ResourceLocation, JsonElement> source) {
        if (domain == null || source == null) return;
        Map<ResourceLocation, JsonObject> base = BASE.get(domain);
        base.clear();
        source.forEach((id, element) -> {
            if (id != null && element != null && element.isJsonObject()) base.put(id, element.getAsJsonObject().deepCopy());
        });
        applyOverrides(domain, source, TaczDataStore.snapshot());
    }

    public static synchronized JsonObject listSnapshot() {
        Map<TaczDataKind, Map<String, TaczDataOverride>> overrides = TaczDataStore.snapshot();
        JsonObject root = new JsonObject();
        for (TaczDataKind kind : TaczDataKind.values()) {
            JsonObject section = new JsonObject();
            Domain indexDomain = indexDomain(kind);
            for (Map.Entry<ResourceLocation, JsonObject> entry : BASE.get(indexDomain).entrySet()) {
                String id = entry.getKey().toString();
                JsonObject index = entry.getValue();
                TaczDataOverride override = overrides.get(kind).get(id);
                JsonObject item = new JsonObject();
                item.addProperty("id", id);
                item.addProperty("kind", kind.wireName());
                item.addProperty("name", stringValue(index, "name"));
                item.addProperty("type", stringValue(index, "type"));
                String dataId = kind.usesInlineData() || kind == TaczDataKind.AMMO ? id : stringValue(index, "data");
                item.addProperty("data_id", dataId);
                item.addProperty("modified", override != null);
                item.addProperty("removed", override != null && override.removed());
                if (kind.usesInlineData() || override != null && override.removed()) item.add("preview_index", index.deepCopy());
                section.add(id, item);
            }
            Map<String, TaczDataOverride> kindOverrides = overrides.get(kind);
            kindOverrides.forEach((id, override) -> {
                if (section.has(id)) return;
                JsonObject item = new JsonObject();
                item.addProperty("id", id);
                item.addProperty("kind", kind.wireName());
                item.addProperty("name", "");
                item.addProperty("type", "");
                item.addProperty("data_id", override.dataId());
                item.addProperty("modified", true);
                item.addProperty("removed", override.removed());
                section.add(id, item);
            });
            root.add(kind.jsonKey(), section);
        }
        return root;
    }

    public static synchronized JsonObject detailSnapshot(TaczDataKind kind, String logicalId) {
        ResourceLocation id = KineticResourceIds.tryParse(logicalId);
        if (id == null) throw new IllegalArgumentException("Invalid TACZ id");
        JsonObject index = copy(BASE.get(indexDomain(kind)).get(id));
        String dataIdString;
        JsonObject baseData;
        if (kind.usesInlineData()) {
            dataIdString = id.toString();
            baseData = index.has("data") && index.get("data").isJsonObject() ? index.getAsJsonObject("data").deepCopy() : new JsonObject();
        } else if (kind == TaczDataKind.AMMO) {
            dataIdString = id.toString();
            baseData = copy(index);
        } else {
            dataIdString = stringValue(index, "data");
            ResourceLocation dataId = KineticResourceIds.tryParse(dataIdString);
            baseData = copy(dataId == null ? null : BASE.get(dataDomain(kind)).get(dataId));
        }

        TaczDataOverride override = TaczDataStore.get(kind, id.toString());
        JsonObject effective = override != null && override.data() != null ? override.data() : copy(baseData);

        JsonObject root = new JsonObject();
        root.addProperty("kind", kind.wireName());
        root.addProperty("id", id.toString());
        root.addProperty("data_id", override != null && !override.dataId().isBlank() ? override.dataId() : dataIdString);
        root.addProperty("modified", override != null);
        root.addProperty("removed", override != null && override.removed());
        if (kind.usesInlineData() || override != null && override.removed() && !index.entrySet().isEmpty()) root.add("preview_index", index.deepCopy());
        root.add("index", index);
        root.add("base_data", baseData);
        root.add("data", effective);
        return root;
    }

    private static void applyOverrides(Domain domain, Map<ResourceLocation, JsonElement> source, Map<TaczDataKind, Map<String, TaczDataOverride>> overrides) {
        if (domain == Domain.GUN_DATA) {
            applyDataOverrides(source, overrides.get(TaczDataKind.GUN));
            return;
        }
        if (domain == Domain.ATTACHMENT_DATA) {
            applyDataOverrides(source, overrides.get(TaczDataKind.ATTACHMENT));
            return;
        }
        if (domain == Domain.GUN_INDEX) {
            applyIndexOverrides(source, overrides.get(TaczDataKind.GUN), false);
            return;
        }
        if (domain == Domain.ATTACHMENT_INDEX) {
            applyIndexOverrides(source, overrides.get(TaczDataKind.ATTACHMENT), false);
            return;
        }
        if (domain == Domain.AMMO_INDEX) {
            applyIndexOverrides(source, overrides.get(TaczDataKind.AMMO), true);
            return;
        }
        if (domain == Domain.LR_MELEE_INDEX) {
            applyInlineIndexOverrides(source, overrides.get(TaczDataKind.MELEE));
            return;
        }
        if (domain == Domain.LR_THROWABLE_INDEX) {
            applyInlineIndexOverrides(source, overrides.get(TaczDataKind.THROWABLE));
            return;
        }
        if (domain == Domain.LR_CONSUMABLE_INDEX) applyInlineIndexOverrides(source, overrides.get(TaczDataKind.CONSUMABLE));
    }

    private static void applyDataOverrides(Map<ResourceLocation, JsonElement> source, Map<String, TaczDataOverride> overrides) {
        overrides.values().forEach(override -> {
            if (override.dataId().isBlank() || override.data() == null) return;
            ResourceLocation dataId = KineticResourceIds.tryParse(override.dataId());
            if (dataId != null) source.put(dataId, override.data());
        });
    }

    private static void applyIndexOverrides(Map<ResourceLocation, JsonElement> source, Map<String, TaczDataOverride> overrides, boolean replaceData) {
        overrides.forEach((idString, override) -> {
            ResourceLocation id = KineticResourceIds.tryParse(idString);
            if (id == null) return;
            if (override.removed()) {
                source.remove(id);
            } else if (replaceData && override.data() != null) {
                source.put(id, override.data());
            }
        });
    }

    private static void applyInlineIndexOverrides(Map<ResourceLocation, JsonElement> source, Map<String, TaczDataOverride> overrides) {
        overrides.forEach((idString, override) -> {
            ResourceLocation id = KineticResourceIds.tryParse(idString);
            if (id == null) return;
            if (override.removed()) {
                source.remove(id);
                return;
            }
            if (override.data() == null) return;
            JsonElement existing = source.get(id);
            if (existing == null || !existing.isJsonObject()) return;
            JsonObject next = existing.getAsJsonObject().deepCopy();
            next.add("data", override.data());
            source.put(id, next);
        });
    }

    private static Domain indexDomain(TaczDataKind kind) {
        return switch (kind) {
            case GUN -> Domain.GUN_INDEX;
            case ATTACHMENT -> Domain.ATTACHMENT_INDEX;
            case AMMO -> Domain.AMMO_INDEX;
            case MELEE -> Domain.LR_MELEE_INDEX;
            case THROWABLE -> Domain.LR_THROWABLE_INDEX;
            case CONSUMABLE -> Domain.LR_CONSUMABLE_INDEX;
        };
    }

    private static Domain dataDomain(TaczDataKind kind) {
        return switch (kind) {
            case GUN -> Domain.GUN_DATA;
            case ATTACHMENT -> Domain.ATTACHMENT_DATA;
            case AMMO -> Domain.AMMO_INDEX;
            case MELEE -> Domain.LR_MELEE_INDEX;
            case THROWABLE -> Domain.LR_THROWABLE_INDEX;
            case CONSUMABLE -> Domain.LR_CONSUMABLE_INDEX;
        };
    }

    private static String stringValue(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return "";
        return object.get(key).getAsString();
    }

    private static JsonObject copy(JsonObject object) {
        return object == null ? new JsonObject() : object.deepCopy();
    }
}
