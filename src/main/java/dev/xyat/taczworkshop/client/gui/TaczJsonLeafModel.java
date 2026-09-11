package dev.xyat.taczworkshop.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.xyat.taczworkshop.data.TaczDataKind;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TaczJsonLeafModel {
    record Leaf(List<Object> path, String displayPath, JsonPrimitive value) {
        boolean isBoolean() {
            return value.isBoolean();
        }

        boolean isNumber() {
            return value.isNumber();
        }

        boolean isString() {
            return value.isString();
        }
    }

    private record Template(List<Object> path, String displayPath, JsonPrimitive value) {
    }

    private static final List<String> PREFERRED = List.of(
            "bullet.damage",
            "bullet.explosion.explode",
            "bullet.explosion.damage",
            "bullet.explosion.radius",
            "bullet.explosion.knockback",
            "bullet.explosion.destroy_block",
            "bullet.explosion.delay",
            "bullet.bullet_amount",
            "rpm",
            "ammo_amount",
            "extended_mag_ammo_amount[0]",
            "extended_mag_ammo_amount[1]",
            "extended_mag_ammo_amount[2]",
            "bullet.speed",
            "bullet.life",
            "bullet.gravity",
            "bullet.knockback",
            "bullet.friction",
            "bullet.pierce",
            "bullet.extra_damage.armor_ignore",
            "bullet.extra_damage.head_shot_multiplier",
            "inaccuracy.stand",
            "inaccuracy.move",
            "inaccuracy.sneak",
            "inaccuracy.lie",
            "inaccuracy.aim",
            "weight",
            "draw_time",
            "put_away_time",
            "aim_time",
            "sprint_time",
            "extended_mag_level",
            "stack_size",
            "sort"
    );

    private TaczJsonLeafModel() {
    }

    static List<Leaf> flatten(JsonObject root, String query) {
        return flatten(root, query, null);
    }

    static List<Leaf> flatten(JsonObject root, String query, TaczDataKind kind) {
        List<Leaf> leaves = new ArrayList<>();
        walk(root, List.of(), "", leaves);
        if (kind == TaczDataKind.GUN) addGunVirtualFields(root, leaves);
        String normalized = query == null ? "" : query.trim().toLowerCase();
        if (!normalized.isEmpty()) leaves.removeIf(leaf -> !leaf.displayPath().toLowerCase().contains(normalized));
        leaves.sort(Comparator.comparingInt((Leaf leaf) -> rank(leaf.displayPath())).thenComparing(Leaf::displayPath));
        return leaves;
    }

    static boolean matches(Leaf leaf, String query, String localizedLabel) {
        if (leaf == null) return false;
        String normalized = query == null ? "" : query.trim().toLowerCase();
        if (normalized.isEmpty()) return true;
        String raw = leaf.displayPath() == null ? "" : leaf.displayPath().toLowerCase();
        String label = localizedLabel == null ? "" : localizedLabel.toLowerCase();
        return raw.contains(normalized) || label.contains(normalized);
    }

    static void set(JsonObject root, Leaf leaf, String value) {
        if (root == null || leaf == null) return;
        JsonElement replacement;
        if (leaf.isBoolean()) {
            replacement = new JsonPrimitive(Boolean.parseBoolean(value));
        } else if (leaf.isNumber()) {
            replacement = new JsonPrimitive(new BigDecimal(value));
        } else {
            replacement = new JsonPrimitive(value == null ? "" : value);
        }
        setAt(root, leaf.path(), replacement);
    }

    private static void addGunVirtualFields(JsonObject root, List<Leaf> leaves) {
        addVirtual(root, leaves, "ammo_amount", new JsonPrimitive(30));
        addVirtual(root, leaves, "can_crawl", new JsonPrimitive(true));
        addVirtual(root, leaves, "can_slide", new JsonPrimitive(true));
        addVirtual(root, leaves, "bolt", new JsonPrimitive("open_bolt"));
        addVirtual(root, leaves, "rpm", new JsonPrimitive(300));
        addVirtual(root, leaves, "draw_time", new JsonPrimitive(0.4F));
        addVirtual(root, leaves, "put_away_time", new JsonPrimitive(0.4F));
        addVirtual(root, leaves, "sprint_time", new JsonPrimitive(0.2F));
        addVirtual(root, leaves, "aim_time", new JsonPrimitive(0.2F));
        addVirtual(root, leaves, "bolt_action_time", new JsonPrimitive(0.0F));
        addVirtual(root, leaves, "bolt_feed_time", new JsonPrimitive(-1.0F));
        addVirtual(root, leaves, "crawl_recoil_multiplier", new JsonPrimitive(0.5F));
        addVirtual(root, leaves, "hurt_bob_tweak_multiplier", new JsonPrimitive(0.05F));
        addVirtual(root, leaves, "weight", new JsonPrimitive(0.0F));

        addVirtual(root, leaves, "bullet.life", new JsonPrimitive(10.0F));
        addVirtual(root, leaves, "bullet.bullet_amount", new JsonPrimitive(1));
        addVirtual(root, leaves, "bullet.damage", new JsonPrimitive(5.0F));
        addVirtual(root, leaves, "bullet.speed", new JsonPrimitive(5.0F));
        addVirtual(root, leaves, "bullet.gravity", new JsonPrimitive(0.0F));
        addVirtual(root, leaves, "bullet.knockback", new JsonPrimitive(0.0F));
        addVirtual(root, leaves, "bullet.friction", new JsonPrimitive(0.01F));
        addVirtual(root, leaves, "bullet.pierce", new JsonPrimitive(1));
        addVirtual(root, leaves, "bullet.ignite.entity", new JsonPrimitive(false));
        addVirtual(root, leaves, "bullet.ignite.block", new JsonPrimitive(false));
        addVirtual(root, leaves, "bullet.ignite_entity_time", new JsonPrimitive(2));
        addVirtual(root, leaves, "bullet.tracer_count_interval", new JsonPrimitive(-1));
        addVirtual(root, leaves, "bullet.extra_damage.armor_ignore", new JsonPrimitive(0.0F));
        addVirtual(root, leaves, "bullet.extra_damage.head_shot_multiplier", new JsonPrimitive(1.0F));

        addVirtual(root, leaves, "bullet.explosion.explode", primitiveAt(root, "bullet.explosion.explode", new JsonPrimitive(false)));
        addVirtual(root, leaves, "bullet.explosion.damage", primitiveAt(root, "bullet.explosion.damage", new JsonPrimitive(2.0F)));
        addVirtual(root, leaves, "bullet.explosion.radius", primitiveAt(root, "bullet.explosion.radius", new JsonPrimitive(0.5F)));
        addVirtual(root, leaves, "bullet.explosion.knockback", primitiveAt(root, "bullet.explosion.knockback", new JsonPrimitive(false)));
        addVirtual(root, leaves, "bullet.explosion.destroy_block", primitiveAt(root, "bullet.explosion.destroy_block", new JsonPrimitive(false)));
        addVirtual(root, leaves, "bullet.explosion.delay", primitiveAt(root, "bullet.explosion.delay", new JsonPrimitive(30.0F)));

        addVirtual(root, leaves, "fire_sound.fire_multiplier", new JsonPrimitive(1.0F));
        addVirtual(root, leaves, "fire_sound.silence_multiplier", new JsonPrimitive(1.0F));
        addVirtual(root, leaves, "reload.type", new JsonPrimitive("magazine"));
        addVirtual(root, leaves, "reload.infinite", new JsonPrimitive(false));
        addVirtual(root, leaves, "reload.feed.empty", new JsonPrimitive(2.5F));
        addVirtual(root, leaves, "reload.feed.tactical", new JsonPrimitive(2.0F));
        addVirtual(root, leaves, "reload.cooldown.empty", new JsonPrimitive(2.5F));
        addVirtual(root, leaves, "reload.cooldown.tactical", new JsonPrimitive(2.0F));
        addVirtual(root, leaves, "burst_data.continuous_shoot", new JsonPrimitive(false));
        addVirtual(root, leaves, "burst_data.count", new JsonPrimitive(3));
        addVirtual(root, leaves, "burst_data.bpm", new JsonPrimitive(200));
        addVirtual(root, leaves, "burst_data.min_interval", new JsonPrimitive(1.0F));

        addVirtual(root, leaves, "movement_speed.base", new JsonPrimitive(0.0F));
        addVirtual(root, leaves, "movement_speed.aim", new JsonPrimitive(0.0F));
        addVirtual(root, leaves, "movement_speed.reload", new JsonPrimitive(0.0F));
        addVirtual(root, leaves, "melee.distance", new JsonPrimitive(1.0F));
        addVirtual(root, leaves, "melee.cooldown", new JsonPrimitive(1.0F));
        addVirtual(root, leaves, "melee.default.animation_type", new JsonPrimitive("melee_push"));
        addVirtual(root, leaves, "melee.default.distance", new JsonPrimitive(1.0F));
        addVirtual(root, leaves, "melee.default.range_angle", new JsonPrimitive(30.0F));
        addVirtual(root, leaves, "melee.default.cooldown", new JsonPrimitive(0.0F));
        addVirtual(root, leaves, "melee.default.damage", new JsonPrimitive(0.0F));
        addVirtual(root, leaves, "melee.default.knockback", new JsonPrimitive(0.2F));
        addVirtual(root, leaves, "melee.default.prep", new JsonPrimitive(0.1F));

        addVirtual(root, leaves, "heat.max", new JsonPrimitive(100.0F));
        addVirtual(root, leaves, "heat.per_shot", new JsonPrimitive(3.0F));
        addVirtual(root, leaves, "heat.cooling_multiplier", new JsonPrimitive(1.0F));
        addVirtual(root, leaves, "heat.cooling_delay", new JsonPrimitive(1000));
        addVirtual(root, leaves, "heat.over_heat_time", new JsonPrimitive(3000));
        addVirtual(root, leaves, "heat.min_inaccuracy", new JsonPrimitive(1.0F));
        addVirtual(root, leaves, "heat.max_inaccuracy", new JsonPrimitive(1.0F));
        addVirtual(root, leaves, "heat.min_rpm_mod", new JsonPrimitive(1.0F));
        addVirtual(root, leaves, "heat.max_rpm_mod", new JsonPrimitive(1.0F));

        addFireModeVirtualFields(root, leaves);
        addAmmoTypeVirtualFields(root, leaves);
    }

    private static void addFireModeVirtualFields(JsonObject root, List<Leaf> leaves) {
        if (root == null || !root.has("fire_mode") || !root.get("fire_mode").isJsonArray()) return;
        JsonArray modes = root.getAsJsonArray("fire_mode");
        for (JsonElement element : modes) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) continue;
            String mode = element.getAsString();
            if (mode.isBlank() || mode.indexOf('.') >= 0 || mode.indexOf('[') >= 0 || mode.indexOf(']') >= 0) continue;
            String adjust = "fire_mode_adjust." + mode + ".";
            addVirtual(root, leaves, adjust + "damage", new JsonPrimitive(0.0F));
            addVirtual(root, leaves, adjust + "rpm", new JsonPrimitive(0));
            addVirtual(root, leaves, adjust + "speed", new JsonPrimitive(0.0F));
            addVirtual(root, leaves, adjust + "knockback", new JsonPrimitive(0.0F));
            addVirtual(root, leaves, adjust + "armor_ignore", new JsonPrimitive(0.0F));
            addVirtual(root, leaves, adjust + "head_shot_multiplier", new JsonPrimitive(0.0F));
            addVirtual(root, leaves, adjust + "aim_inaccuracy", new JsonPrimitive(0.0F));
            addVirtual(root, leaves, adjust + "other_inaccuracy", new JsonPrimitive(0.0F));

            String charging = "charging." + mode + ".";
            addVirtual(root, leaves, charging + "type", new JsonPrimitive("auto"));
            addVirtual(root, leaves, charging + "increase_per_tick", new JsonPrimitive(0.2F));
            addVirtual(root, leaves, charging + "decrease_per_tick", new JsonPrimitive(0.5F));
            addVirtual(root, leaves, charging + "decrease_on_fire", new JsonPrimitive(0.0F));
            addVirtual(root, leaves, charging + "max_charge", new JsonPrimitive(1.0F));
            addVirtual(root, leaves, charging + "fire_threshold", new JsonPrimitive(0.6F));
            addVirtual(root, leaves, charging + "charge_during_cooldown", new JsonPrimitive(true));
        }
    }

    private static void addAmmoTypeVirtualFields(JsonObject root, List<Leaf> leaves) {
        if (root == null || !root.has("extras") || !root.get("extras").isJsonObject()) return;
        JsonObject extras = root.getAsJsonObject("extras");
        if (!extras.has("ammo_types") || !extras.get("ammo_types").isJsonArray()) return;
        JsonArray ammoTypes = extras.getAsJsonArray("ammo_types");
        if (ammoTypes.size() == 0) return;

        Map<String, Template> templates = new LinkedHashMap<>();
        for (JsonElement element : ammoTypes) {
            if (!element.isJsonObject()) continue;
            collectTemplates(element, List.of(), "", templates);
        }

        putTemplate(templates, "bullet.explosion.explode", primitiveAt(root, "bullet.explosion.explode", new JsonPrimitive(false)));
        putTemplate(templates, "bullet.explosion.damage", primitiveAt(root, "bullet.explosion.damage", new JsonPrimitive(2.0F)));
        putTemplate(templates, "bullet.explosion.radius", primitiveAt(root, "bullet.explosion.radius", new JsonPrimitive(0.5F)));
        putTemplate(templates, "bullet.explosion.knockback", primitiveAt(root, "bullet.explosion.knockback", new JsonPrimitive(false)));
        putTemplate(templates, "bullet.explosion.destroy_block", primitiveAt(root, "bullet.explosion.destroy_block", new JsonPrimitive(false)));
        putTemplate(templates, "bullet.explosion.delay", primitiveAt(root, "bullet.explosion.delay", new JsonPrimitive(30.0F)));

        for (int i = 0; i < ammoTypes.size(); i++) {
            if (!ammoTypes.get(i).isJsonObject()) continue;
            for (Template template : templates.values()) {
                List<Object> fullPath = new ArrayList<>();
                fullPath.add("extras");
                fullPath.add("ammo_types");
                fullPath.add(i);
                fullPath.addAll(template.path());
                if (getAt(root, fullPath) != null) continue;
                String display = "extras.ammo_types[" + i + "]." + template.displayPath();
                leaves.add(new Leaf(List.copyOf(fullPath), display, template.value().deepCopy()));
            }
        }
    }

    private static void collectTemplates(JsonElement element, List<Object> path, String display, Map<String, Template> out) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonPrimitive()) {
            out.putIfAbsent(display, new Template(List.copyOf(path), display, element.getAsJsonPrimitive().deepCopy()));
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                List<Object> nextPath = append(path, entry.getKey());
                String nextDisplay = display.isEmpty() ? entry.getKey() : display + "." + entry.getKey();
                collectTemplates(entry.getValue(), nextPath, nextDisplay, out);
            }
            return;
        }
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            List<Object> nextPath = append(path, i);
            collectTemplates(array.get(i), nextPath, display + "[" + i + "]", out);
        }
    }

    private static void putTemplate(Map<String, Template> templates, String displayPath, JsonPrimitive value) {
        templates.putIfAbsent(displayPath, new Template(parseDisplayPath(displayPath), displayPath, value.deepCopy()));
    }

    private static JsonPrimitive primitiveAt(JsonObject root, String displayPath, JsonPrimitive fallback) {
        JsonElement element = getAt(root, parseDisplayPath(displayPath));
        return element != null && element.isJsonPrimitive() ? element.getAsJsonPrimitive().deepCopy() : fallback.deepCopy();
    }

    private static void addVirtual(JsonObject root, List<Leaf> leaves, String displayPath, JsonPrimitive defaultValue) {
        List<Object> path = parseDisplayPath(displayPath);
        if (getAt(root, path) != null) return;
        leaves.add(new Leaf(path, displayPath, defaultValue.deepCopy()));
    }

    private static List<Object> parseDisplayPath(String displayPath) {
        List<Object> path = new ArrayList<>();
        if (displayPath == null || displayPath.isBlank()) return path;
        for (String part : displayPath.split("\\.")) {
            int cursor = 0;
            int open = part.indexOf('[');
            String key = open < 0 ? part : part.substring(0, open);
            if (!key.isEmpty()) path.add(key);
            while (open >= 0) {
                int close = part.indexOf(']', open + 1);
                if (close < 0) break;
                try {
                    path.add(Integer.parseInt(part.substring(open + 1, close)));
                } catch (NumberFormatException ignored) {
                    break;
                }
                cursor = close + 1;
                open = part.indexOf('[', cursor);
            }
        }
        return List.copyOf(path);
    }

    private static JsonElement getAt(JsonObject root, List<Object> path) {
        JsonElement cursor = root;
        for (Object segment : path) {
            if (segment instanceof String key) {
                if (!cursor.isJsonObject()) return null;
                cursor = cursor.getAsJsonObject().get(key);
            } else if (segment instanceof Integer index) {
                if (!cursor.isJsonArray() || index < 0 || index >= cursor.getAsJsonArray().size()) return null;
                cursor = cursor.getAsJsonArray().get(index);
            }
            if (cursor == null || cursor.isJsonNull()) return null;
        }
        return cursor;
    }

    private static void walk(JsonElement element, List<Object> path, String display, List<Leaf> out) {
        if (element == null || element.isJsonNull() || out.size() >= 4096) return;
        if (element.isJsonPrimitive()) {
            out.add(new Leaf(List.copyOf(path), display, element.getAsJsonPrimitive().deepCopy()));
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (path.isEmpty() && "allow_attachment_types".equals(entry.getKey())) continue;
                List<Object> nextPath = append(path, entry.getKey());
                String nextDisplay = display.isEmpty() ? entry.getKey() : display + "." + entry.getKey();
                walk(entry.getValue(), nextPath, nextDisplay, out);
            }
            return;
        }
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            List<Object> nextPath = append(path, i);
            walk(array.get(i), nextPath, display + "[" + i + "]", out);
        }
    }

    private static List<Object> append(List<Object> path, Object value) {
        List<Object> copy = new ArrayList<>(path);
        copy.add(value);
        return copy;
    }

    private static void setAt(JsonObject root, List<Object> path, JsonElement value) {
        if (path.isEmpty()) return;
        JsonElement cursor = root;
        for (int i = 0; i < path.size() - 1; i++) {
            Object segment = path.get(i);
            Object next = path.get(i + 1);
            if (segment instanceof String key) {
                if (!cursor.isJsonObject()) return;
                JsonObject object = cursor.getAsJsonObject();
                JsonElement child = object.get(key);
                if (child == null || child.isJsonNull()) {
                    child = next instanceof Integer ? new JsonArray() : new JsonObject();
                    object.add(key, child);
                }
                cursor = child;
            } else if (segment instanceof Integer index) {
                if (!cursor.isJsonArray() || index < 0) return;
                JsonArray array = cursor.getAsJsonArray();
                while (array.size() <= index) array.add(JsonNull.INSTANCE);
                JsonElement child = array.get(index);
                if (child == null || child.isJsonNull()) {
                    child = next instanceof Integer ? new JsonArray() : new JsonObject();
                    array.set(index, child);
                }
                cursor = child;
            }
        }
        Object last = path.get(path.size() - 1);
        if (last instanceof String key && cursor.isJsonObject()) {
            cursor.getAsJsonObject().add(key, value);
        } else if (last instanceof Integer index && cursor.isJsonArray() && index >= 0) {
            JsonArray array = cursor.getAsJsonArray();
            while (array.size() <= index) array.add(JsonNull.INSTANCE);
            array.set(index, value);
        }
    }

    private static int rank(String path) {
        int index = PREFERRED.indexOf(path);
        if (index >= 0) return index;
        String prefix = "extras.ammo_types[";
        if (path != null && path.startsWith(prefix)) {
            int close = path.indexOf(']', prefix.length());
            if (close > prefix.length()) {
                try {
                    int ammoIndex = Integer.parseInt(path.substring(prefix.length(), close));
                    String relative = close + 2 <= path.length() ? path.substring(Math.min(path.length(), close + 2)) : "";
                    int relativeRank = PREFERRED.indexOf(relative);
                    return 100 + ammoIndex * 256 + (relativeRank >= 0 ? relativeRank : 128);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 10000;
    }
}
