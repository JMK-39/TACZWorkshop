package dev.xyat.taczworkshop.client.gui;

import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.*;
import dev.xyat.kineticcore.api.client.gui.widget.list.*;
import dev.xyat.kineticcore.api.text.KineticI18n;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.xyat.taczworkshop.client.TaczDataClientState;
import dev.xyat.taczworkshop.client.TaczDataStackUtil;
import dev.xyat.taczworkshop.client.TaczPreviewIndexContext;
import dev.xyat.taczworkshop.data.TaczDataKind;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TaczDataDetailPage extends KineticPage {
    private static final List<String> ATTACHMENT_TYPES = List.of("scope", "muzzle", "stock", "grip", "laser", "extended_mag");
    private static final int FIELDS_PER_ROW = 2;
    private static final int ROW_HEIGHT = 28;
    private static final int LEFT_X = 20;
    private static final int RIGHT_X = 318;
    private static final int LABEL_WIDTH = 112;
    private static final int INPUT_OFFSET = 118;
    private static final int INPUT_WIDTH = 158;
    private static final int FIELD_SCROLL_X = 617;
    private static final int FIELD_SCROLL_WIDTH = 4;
    private static final int FIELD_BOTTOM = 330;
    private static final int ATTACHMENT_SUMMARY_X = 20;
    private static final int ATTACHMENT_SUMMARY_Y = 82;
    private static final int ATTACHMENT_SUMMARY_WIDTH = 200;
    private static final int ATTACHMENT_SUMMARY_HEIGHT = 22;

    private final TaczDataKind kind;
    private final String id;
    private final String dataId;
    private final String nameKey;
    private final JsonObject data;
    private final boolean serverModified;
    private final boolean resourceAvailable;
    private final JsonObject previewIndex;
    private final KineticScrollController fieldScroll = new KineticScrollController();
    private final List<KineticControl> activeFieldWidgets = new ArrayList<>();
    private boolean removed;
    private boolean dirty;
    private KineticTextField search;
    private List<TaczJsonLeafModel.Leaf> leaves = List.of();
    private TaczJsonLeafModel.Leaf hoveredLeaf;
    private boolean hoveredHeaderItem;
    private boolean hoveredAttachmentSummary;
    private int fieldWidgetFirstRow = -1;

    public TaczDataDetailPage(JsonObject detail) {
        super(KineticI18n.translatable("gui.taczworkshop.data.detail.title"));
        this.kind = TaczDataKind.fromWire(read(detail, "kind"));
        this.id = read(detail, "id");
        this.dataId = read(detail, "data_id");
        JsonObject indexForName = detail.has("index") && detail.get("index").isJsonObject() ? detail.getAsJsonObject("index") : new JsonObject();
        JsonObject previewForName = detail.has("preview_index") && detail.get("preview_index").isJsonObject() ? detail.getAsJsonObject("preview_index") : new JsonObject();
        String resolvedNameKey = read(indexForName, "name");
        if (resolvedNameKey.isBlank()) resolvedNameKey = read(previewForName, "name");
        this.nameKey = resolvedNameKey;
        this.data = detail.has("data") && detail.get("data").isJsonObject() ? detail.getAsJsonObject("data").deepCopy() : new JsonObject();
        this.serverModified = bool(detail, "modified");
        this.previewIndex = detail.has("preview_index") && detail.get("preview_index").isJsonObject() ? detail.getAsJsonObject("preview_index").deepCopy() : new JsonObject();
        this.resourceAvailable = detail.has("index") && detail.get("index").isJsonObject() && !detail.getAsJsonObject("index").entrySet().isEmpty()
                || !this.previewIndex.entrySet().isEmpty();
        this.removed = bool(detail, "removed");
    }

    @Override
    protected void build(KineticUi ui) {
        closeContextMenu();
        activeFieldWidgets.clear();
        String oldSearch = search == null ? "" : search.textValue();
        search = ui().textField(20, 50, 250).label(KineticI18n.translatable("gui.taczworkshop.search")).placeholder(KineticI18n.translatable("gui.taczworkshop.data.field.search.hint")).firstShownTextAsDefault().build();
        search.limitTextLength(256);
        search.setTextValue(oldSearch);
        search.onTextChange(value -> {
            fieldScroll.reset();
            rebuildFieldWidgets();
        });

        ui().button(278, 18, 68).text(KineticI18n.translatable(removed ? "gui.taczworkshop.data.restore" : "gui.taczworkshop.data.remove")).tooltip(KineticI18n.translatable("tip.taczworkshop.data.remove")).onClick(this::toggleRemoved).build();
        ui().button(350, 18, 64).text(KineticI18n.translatable("gui.taczworkshop.data.reset")).tooltip(KineticI18n.translatable("tip.taczworkshop.data.reset")).onClick(this::confirmReset).build();
        if (hasExpandableCollections()) {
            ui().button(418, 18, 68).text(KineticI18n.translatable("gui.taczworkshop.data.collection.add")).tooltip(KineticI18n.translatable("tip.taczworkshop.data.collection.add")).layer(1).onClick(this::openAddCollectionMenu).build();
        }
        if (hasRemovableCollections()) {
            ui().button(490, 18, 68).text(KineticI18n.translatable("gui.taczworkshop.data.collection.remove")).tooltip(KineticI18n.translatable("tip.taczworkshop.data.collection.remove")).layer(1).onClick(this::openRemoveCollectionMenu).build();
        }
        ui().button(562, 18, 64).text(KineticI18n.translatable("gui.taczworkshop.back")).tooltip(KineticI18n.translatable("tip.taczworkshop.back.data_list")).onClick(this::close).build();

        rebuildFieldWidgets();
    }

    private int fieldBaseY() {
        return kind == TaczDataKind.GUN ? 116 : 84;
    }

    private int visibleFieldRows() {
        return Math.max(1, (FIELD_BOTTOM - fieldBaseY()) / ROW_HEIGHT);
    }

    private void rebuildFieldWidgets() {
        activeFieldWidgets.forEach(ui()::remove);
        activeFieldWidgets.clear();

        String query = search == null ? "" : search.textValue().trim().toLowerCase(Locale.ROOT);
        List<TaczJsonLeafModel.Leaf> all = TaczJsonLeafModel.flatten(data, "", kind);
        if (!query.isEmpty()) {
            all.removeIf(leaf -> !TaczJsonLeafModel.matches(leaf, query, fieldLabel(leaf.displayPath()).getString()));
        }
        leaves = List.copyOf(all);

        int totalRows = (leaves.size() + FIELDS_PER_ROW - 1) / FIELDS_PER_ROW;
        fieldScroll.update(totalRows, visibleFieldRows());

        fieldWidgetFirstRow = fieldScroll.smoothIndexOffset();
        int start = fieldWidgetFirstRow * FIELDS_PER_ROW;
        int end = Math.min(leaves.size(), start + (visibleFieldRows() + 1) * FIELDS_PER_ROW);
        for (int i = start; i < end; i++) {
            int local = i - start;
            int col = local % FIELDS_PER_ROW;
            int row = local / FIELDS_PER_ROW;
            int x = col == 0 ? LEFT_X : RIGHT_X;
            int y = fieldBaseY() + row * ROW_HEIGHT - fieldScroll.visualShift(ROW_HEIGHT);
            addLeafEditor(leaves.get(i), x, y);
        }
    }

    private void addLeafEditor(TaczJsonLeafModel.Leaf leaf, int x, int y) {
        int inputX = x + INPUT_OFFSET;
        Component label = fieldLabel(leaf.displayPath());
        if (leaf.isBoolean()) {
            boolean current = leaf.value().getAsBoolean();
            KineticToggle button = ui().toggle(inputX, y + 2, INPUT_WIDTH).value(current).labels(KineticI18n.translatable("gui.taczworkshop.data.boolean.true"), KineticI18n.translatable("gui.taczworkshop.data.boolean.false")).onChange(value -> {
                        TaczJsonLeafModel.set(data, leaf, Boolean.toString(value));
                        markDirty();
                    }).build();
            activeFieldWidgets.add(button);
            return;
        }

        KineticTextField box;
        if (leaf.isNumber()) {
            KineticNumberField numeric = ui().numberField(inputX, y + 2, INPUT_WIDTH, NumberType.DECIMAL).label(label).allowNegative(true).firstShownTextAsDefault().build();
            numeric.setTextValue(leaf.value().getAsString());
            numeric.onTextChange(value -> {
                try {
                    if (value.isBlank() || "-".equals(value) || ".".equals(value) || "-.".equals(value)) return;
                    new BigDecimal(value);
                    TaczJsonLeafModel.set(data, leaf, value);
                    markDirty();
                } catch (NumberFormatException ignored) {
                }
            });
            box = numeric;
        } else {
            box = ui().textField(inputX, y + 2, INPUT_WIDTH).label(label).firstShownTextAsDefault().build();
            box.setTextValue(leaf.value().getAsString());
            box.onTextChange(value -> {
                TaczJsonLeafModel.set(data, leaf, value);
                markDirty();
            });
        }
        box.limitTextLength(2048);
        activeFieldWidgets.add(box);
    }


    private void updateFieldWidgetPositions() {
        int firstRow = fieldScroll.smoothIndexOffset();
        if (firstRow != fieldWidgetFirstRow) {
            rebuildFieldWidgets();
            return;
        }
        int shift = fieldScroll.visualShift(ROW_HEIGHT);
        for (int i = 0; i < activeFieldWidgets.size(); i++) {
            KineticControl widget = activeFieldWidgets.get(i);
            int row = i / FIELDS_PER_ROW;
            widget.moveControlY(fieldBaseY() + row * ROW_HEIGHT - shift + 2);
            boolean intersects = widget.controlY() + widget.controlHeight() > fieldBaseY()
                    && widget.controlY() < fieldBaseY() + visibleFieldRows() * ROW_HEIGHT;
            widget.setControlVisible(intersects);
        }
    }

    private Set<String> attachmentTypes() {
        Set<String> result = new HashSet<>();
        if (data.has("allow_attachment_types") && data.get("allow_attachment_types").isJsonArray()) {
            for (JsonElement element : data.getAsJsonArray("allow_attachment_types")) {
                if (element.isJsonPrimitive()) result.add(element.getAsString());
            }
        }
        return result;
    }

    private void toggleAttachmentType(String type) {
        Set<String> enabled = attachmentTypes();
        if (!enabled.add(type)) enabled.remove(type);
        JsonArray array = new JsonArray();
        ATTACHMENT_TYPES.stream().filter(enabled::contains).forEach(array::add);
        data.add("allow_attachment_types", array);
        markDirty();
    }

    private void toggleRemoved() {
        removed = !removed;
        markDirty();
        rebuildWidgetsKeepSearch();
    }

    private void confirmReset() {
        openDialog(
                KineticI18n.translatable("gui.taczworkshop.data.reset.title"),
                KineticI18n.translatable("gui.taczworkshop.data.reset.message", id),
                KineticI18n.translatable("gui.yes"),
                KineticI18n.translatable("gui.no"),
                () -> {
                    TaczDataClientState.stageReset(kind, id);
                    dirty = false;
                    navigateBack();
                },
                () -> { }
        );
    }

    private void rebuildWidgetsKeepSearch() {
        String oldSearch = search == null ? "" : search.textValue();
        search = null;
        rebuild();
        if (search != null) search.setTextValue(oldSearch);
    }

    private void rebuildWidgetsKeepSearchAndReveal(String pathPrefix) {
        String oldSearch = search == null ? "" : search.textValue();
        search = null;
        rebuild();
        if (search != null) search.setTextValue(oldSearch);
        revealField(pathPrefix);
    }

    private void revealField(String pathPrefix) {
        if (pathPrefix == null || pathPrefix.isBlank()) return;
        for (int i = 0; i < leaves.size(); i++) {
            if (!leaves.get(i).displayPath().startsWith(pathPrefix)) continue;
            int row = i / FIELDS_PER_ROW;
            fieldScroll.setOffset(Math.max(0, row - visibleFieldRows() + 1));
            rebuildFieldWidgets();
            return;
        }
    }

    private boolean hasExpandableCollections() {
        if (kind == TaczDataKind.GUN) return true;
        if (kind == TaczDataKind.CONSUMABLE) return true;
        return kind == TaczDataKind.THROWABLE && objectAtPath() != null;
    }

    private boolean hasRemovableCollections() {
        if (arraySize("bullet.extra_damage.damage_adjust") > 0) return true;
        if (arraySize("extras.ammo_types") > 1) return true;
        if (arraySize("recoil.pitch") > 1 || arraySize("recoil.yaw") > 1) return true;
        if (arraySize("effects") > 0 || arraySize("remove_effects") > 0) return true;
        return arraySize("cloud.effects") > 0;
    }

    private void openAddCollectionMenu() {
        List<KineticOverlays.MenuItem> items = new ArrayList<>();
        if (kind == TaczDataKind.GUN) {
            addCollectionEntry(items, "gui.taczworkshop.data.collection.damage_curve", true, this::addDamageCurvePoint);
            if (arrayAtPath("extras.ammo_types") != null) addCollectionEntry(items, "gui.taczworkshop.data.collection.ammo_type", true, this::addAmmoType);
            if (arrayAtPath("recoil.pitch") != null) addCollectionEntry(items, "gui.taczworkshop.data.collection.recoil_pitch", true, () -> addRecoilPoint("recoil.pitch"));
            if (arrayAtPath("recoil.yaw") != null) addCollectionEntry(items, "gui.taczworkshop.data.collection.recoil_yaw", true, () -> addRecoilPoint("recoil.yaw"));
        }
        if (kind == TaczDataKind.CONSUMABLE) {
            addCollectionEntry(items, "gui.taczworkshop.data.collection.effect", true, this::addConsumableEffect);
            addCollectionEntry(items, "gui.taczworkshop.data.collection.remove_effect", true, this::addRemoveEffect);
        }
        if (kind == TaczDataKind.THROWABLE && objectAtPath() != null) {
            addCollectionEntry(items, "gui.taczworkshop.data.collection.cloud_effect", true, this::addCloudEffect);
        }
        if (!items.isEmpty()) openContextMenu(418, 40, items);
    }

    private void openRemoveCollectionMenu() {
        List<KineticOverlays.MenuItem> items = new ArrayList<>();
        if (arraySize("bullet.extra_damage.damage_adjust") > 0) addCollectionEntry(items, "gui.taczworkshop.data.collection.damage_curve", false, () -> removeLast("bullet.extra_damage.damage_adjust", 0));
        if (arraySize("extras.ammo_types") > 1) addCollectionEntry(items, "gui.taczworkshop.data.collection.ammo_type", false, () -> removeLast("extras.ammo_types", 1));
        if (arraySize("recoil.pitch") > 1) addCollectionEntry(items, "gui.taczworkshop.data.collection.recoil_pitch", false, () -> removeLast("recoil.pitch", 1));
        if (arraySize("recoil.yaw") > 1) addCollectionEntry(items, "gui.taczworkshop.data.collection.recoil_yaw", false, () -> removeLast("recoil.yaw", 1));
        if (arraySize("effects") > 0) addCollectionEntry(items, "gui.taczworkshop.data.collection.effect", false, () -> removeLast("effects", 0));
        if (arraySize("remove_effects") > 0) addCollectionEntry(items, "gui.taczworkshop.data.collection.remove_effect", false, () -> removeLast("remove_effects", 0));
        if (arraySize("cloud.effects") > 0) addCollectionEntry(items, "gui.taczworkshop.data.collection.cloud_effect", false, () -> removeLast("cloud.effects", 0));
        if (!items.isEmpty()) openContextMenu(490, 40, items);
    }

    private void addCollectionEntry(List<KineticOverlays.MenuItem> items, String labelKey, boolean add, Runnable action) {
        Component label = KineticI18n.translatable(labelKey);
        items.add(KineticOverlays.MenuItem.action(
                label,
                KineticI18n.translatable(add ? "tip.taczworkshop.data.collection.add_entry" : "tip.taczworkshop.data.collection.remove_entry", label),
                action
        ));
    }

    private void addDamageCurvePoint() {
        JsonArray array = ensureArrayAtPath("bullet.extra_damage.damage_adjust");
        if (array == null) return;
        double distance = 10.0D;
        double damage = 1.0D;
        if (!array.isEmpty() && array.get(array.size() - 1).isJsonObject()) {
            JsonObject last = array.get(array.size() - 1).getAsJsonObject();
            double lastDistance = number(last, "distance", 0.0D);
            damage = number(last, "damage", 1.0D);
            double gap = 10.0D;
            if (array.size() > 1 && array.get(array.size() - 2).isJsonObject()) {
                double previousDistance = number(array.get(array.size() - 2).getAsJsonObject(), "distance", lastDistance - 10.0D);
                if (lastDistance > previousDistance) gap = lastDistance - previousDistance;
            }
            distance = lastDistance + Math.max(1.0D, gap);
        }
        JsonObject point = new JsonObject();
        point.addProperty("distance", distance);
        point.addProperty("damage", damage);
        int index = array.size();
        array.add(point);
        markDirty();
        rebuildWidgetsKeepSearchAndReveal("bullet.extra_damage.damage_adjust[" + index + "]");
    }

    private void addAmmoType() {
        JsonArray array = arrayAtPath("extras.ammo_types");
        if (array == null || array.isEmpty() || !array.get(array.size() - 1).isJsonObject()) return;
        int index = array.size();
        array.add(array.get(array.size() - 1).getAsJsonObject().deepCopy());
        markDirty();
        rebuildWidgetsKeepSearchAndReveal("extras.ammo_types[" + index + "]");
    }

    private void addRecoilPoint(String path) {
        JsonArray array = arrayAtPath(path);
        if (array == null) return;
        JsonObject point = new JsonObject();
        double nextTime = 0.1D;
        if (!array.isEmpty() && array.get(array.size() - 1).isJsonObject()) {
            JsonObject last = array.get(array.size() - 1).getAsJsonObject();
            point = last.deepCopy();
            double lastTime = number(last, "time", 0.0D);
            double gap = 0.1D;
            if (array.size() > 1 && array.get(array.size() - 2).isJsonObject()) {
                double previousTime = number(array.get(array.size() - 2).getAsJsonObject(), "time", lastTime - 0.1D);
                if (lastTime > previousTime) gap = lastTime - previousTime;
            }
            nextTime = lastTime + Math.max(0.01D, gap);
        } else {
            JsonArray value = new JsonArray();
            value.add(0.0D);
            value.add(0.0D);
            point.add("value", value);
        }
        point.addProperty("time", nextTime);
        int index = array.size();
        array.add(point);
        markDirty();
        rebuildWidgetsKeepSearchAndReveal(path + "[" + index + "]");
    }

    private void addConsumableEffect() {
        JsonArray array = ensureArrayAtPath("effects");
        if (array == null) return;
        JsonObject effect;
        if (!array.isEmpty() && array.get(array.size() - 1).isJsonObject()) {
            effect = array.get(array.size() - 1).getAsJsonObject().deepCopy();
        } else {
            effect = new JsonObject();
            effect.addProperty("id", "minecraft:regeneration");
            effect.addProperty("duration", 100);
            effect.addProperty("amplifier", 0);
            effect.addProperty("chance", 1.0D);
            effect.addProperty("ambient", false);
            effect.addProperty("visible", true);
            effect.addProperty("show_icon", true);
        }
        int index = array.size();
        array.add(effect);
        markDirty();
        rebuildWidgetsKeepSearchAndReveal("effects[" + index + "]");
    }

    private void addRemoveEffect() {
        JsonArray array = ensureArrayAtPath("remove_effects");
        if (array == null) return;
        String value = "@harmful";
        if (!array.isEmpty() && array.get(array.size() - 1).isJsonPrimitive()) value = array.get(array.size() - 1).getAsString();
        int index = array.size();
        array.add(value);
        markDirty();
        rebuildWidgetsKeepSearchAndReveal("remove_effects[" + index + "]");
    }

    private void addCloudEffect() {
        JsonArray array = ensureArrayAtPath("cloud.effects");
        if (array == null) return;
        JsonObject effect;
        if (!array.isEmpty() && array.get(array.size() - 1).isJsonObject()) {
            effect = array.get(array.size() - 1).getAsJsonObject().deepCopy();
        } else {
            effect = new JsonObject();
            effect.addProperty("type", "minecraft:slowness");
            effect.addProperty("duration", 100);
            effect.addProperty("amplifier", 0);
            effect.addProperty("visible", true);
            effect.addProperty("show_icon", true);
        }
        int index = array.size();
        array.add(effect);
        markDirty();
        rebuildWidgetsKeepSearchAndReveal("cloud.effects[" + index + "]");
    }

    private void removeLast(String path, int minimumSize) {
        JsonArray array = arrayAtPath(path);
        if (array == null || array.size() <= minimumSize) return;
        array.remove(array.size() - 1);
        if (array.isEmpty()) removePath(path);
        markDirty();
        rebuildWidgetsKeepSearchAndReveal(path);
    }

    private int arraySize(String path) {
        JsonArray array = arrayAtPath(path);
        return array == null ? 0 : array.size();
    }

    private JsonArray arrayAtPath(String path) {
        JsonElement element = elementAtPath(path);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private JsonObject objectAtPath() {
        JsonElement element = elementAtPath("cloud");
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private JsonElement elementAtPath(String path) {
        if (path == null || path.isBlank()) return data;
        JsonElement cursor = data;
        for (String key : path.split("\\.")) {
            if (!cursor.isJsonObject()) return null;
            cursor = cursor.getAsJsonObject().get(key);
            if (cursor == null || cursor.isJsonNull()) return null;
        }
        return cursor;
    }

    private JsonArray ensureArrayAtPath(String path) {
        if (path == null || path.isBlank()) return null;
        String[] parts = path.split("\\.");
        JsonObject cursor = data;
        for (int i = 0; i < parts.length - 1; i++) {
            String key = parts[i];
            JsonElement child = cursor.get(key);
            if (child == null || child.isJsonNull()) {
                JsonObject next = new JsonObject();
                cursor.add(key, next);
                cursor = next;
            } else if (child.isJsonObject()) {
                cursor = child.getAsJsonObject();
            } else {
                return null;
            }
        }
        String key = parts[parts.length - 1];
        JsonElement child = cursor.get(key);
        if (child == null || child.isJsonNull()) {
            JsonArray array = new JsonArray();
            cursor.add(key, array);
            return array;
        }
        return child.isJsonArray() ? child.getAsJsonArray() : null;
    }

    private void removePath(String path) {
        if (path == null || path.isBlank()) return;
        String[] parts = path.split("\\.");
        List<JsonObject> parents = new ArrayList<>();
        JsonObject cursor = data;
        parents.add(cursor);
        for (int i = 0; i < parts.length - 1; i++) {
            JsonElement child = cursor.get(parts[i]);
            if (child == null || !child.isJsonObject()) return;
            cursor = child.getAsJsonObject();
            parents.add(cursor);
        }
        cursor.remove(parts[parts.length - 1]);
        for (int i = parents.size() - 1; i > 0; i--) {
            JsonObject child = parents.get(i);
            if (!child.entrySet().isEmpty()) break;
            parents.get(i - 1).remove(parts[i - 1]);
        }
    }

    private static double number(JsonObject object, String key, double fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive() || !object.get(key).getAsJsonPrimitive().isNumber()) return fallback;
        try {
            return object.get(key).getAsDouble();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panel(graphics, 6, 6, 628, 348);
        updateFieldWidgetPositions();
        int top = kind == TaczDataKind.GUN ? 110 : 78;
        KineticTheme.panelAlt(graphics, 14, top, 612, 218 - (top - 110));
        int scrollHeight = visibleFieldRows() * ROW_HEIGHT - 4;
        fieldScroll.render(graphics, mouseX, mouseY, FIELD_SCROLL_X, fieldBaseY(), FIELD_SCROLL_WIDTH, scrollHeight, 18);
    }

    @Override
    protected void renderForeground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ItemStack stack = TaczDataStackUtil.build(kind, id);
        hoveredHeaderItem = KineticTheme.hovering(mouseX, mouseY, 20, 14, 18, 18);
        KineticTheme.itemSlot(graphics, 20, 14, hoveredHeaderItem);
        if (resourceAvailable && !stack.isEmpty()) {
            TaczPreviewIndexContext.with(kind, id, previewIndex, () -> KineticTheme.item(graphics, stack, 20, 14, 18, 1.0F, false));
        } else {
            graphics.centeredText(KineticI18n.translatable("gui.taczworkshop.data.missing_mark"), 29, 19, 0xFFFFCC55, true);
        }
        if (serverModified || dirty) KineticTheme.indicatorOutline(graphics, 20, 14, 18, 18, KineticTheme.Indicator.SUCCESS);
        if (removed) KineticTheme.indicatorOutline(graphics, 20, 14, 18, 18, KineticTheme.Indicator.DANGER);

        graphics.scrollingText(displayName(stack), 44, 14, 280, 0xFFFFFFFF, true);
        graphics.scrollingText(Component.literal(id), 44, 27, 280, 0xFFCCCCCC, false);
        graphics.scrollingText(KineticI18n.translatable("gui.taczworkshop.data.detail.data_id", dataId), 44, 40, 290, 0xFFAAAAAA, false);
        graphics.text(KineticI18n.translatable(removed ? "gui.taczworkshop.data.status.removed" : serverModified || dirty ? "gui.taczworkshop.data.status.modified" : "gui.taczworkshop.data.status.active"), 280, 56, 0xFFFFFFFF, true);

        hoveredAttachmentSummary = false;
        if (kind == TaczDataKind.GUN) renderAttachmentSummary(graphics, mouseX, mouseY);

        hoveredLeaf = null;
        int firstRow = fieldScroll.smoothIndexOffset();
        int shift = fieldScroll.visualShift(ROW_HEIGHT);
        int start = firstRow * FIELDS_PER_ROW;
        int end = Math.min(leaves.size(), start + (visibleFieldRows() + 1) * FIELDS_PER_ROW);
        graphics.scissor(14, fieldBaseY(), 617, fieldBaseY() + visibleFieldRows() * ROW_HEIGHT);
        for (int i = start; i < end; i++) {
            int local = i - start;
            int col = local % FIELDS_PER_ROW;
            int row = local / FIELDS_PER_ROW;
            int x = col == 0 ? LEFT_X : RIGHT_X;
            int y = fieldBaseY() + row * ROW_HEIGHT - shift;
            TaczJsonLeafModel.Leaf leaf = leaves.get(i);
            Component label = fieldLabel(leaf.displayPath());
            graphics.scrollingText(label, x, y + 8, LABEL_WIDTH, 0xFFFFFFFF, false);
            if (KineticTheme.hovering(mouseX, mouseY, x, y + 2, INPUT_OFFSET + INPUT_WIDTH, 20)) hoveredLeaf = leaf;
        }
        graphics.endScissor();

        graphics.text(KineticI18n.translatable("gui.taczworkshop.data.fields", leaves.size()), 20, 338, 0xFFCCCCCC, false);
    }

    @Override
    protected boolean onMouseClick(MouseInput input) {
        double mouseX = input.x(), mouseY = input.y(); int button = input.rawButton();
        if (KineticMouseButtons.isSecondary(button) && kind == TaczDataKind.GUN && KineticTheme.hovering(mouseX, mouseY, ATTACHMENT_SUMMARY_X, ATTACHMENT_SUMMARY_Y, ATTACHMENT_SUMMARY_WIDTH, ATTACHMENT_SUMMARY_HEIGHT)) {
            openAttachmentContextMenu((int) mouseX, (int) mouseY);
            return true;
        }
        boolean widget = false;
        int scrollHeight = visibleFieldRows() * ROW_HEIGHT - 4;
        if (fieldScroll.beginDrag(mouseX, mouseY, input.button(), FIELD_SCROLL_X, fieldBaseY(), FIELD_SCROLL_WIDTH, scrollHeight, 18, 2)) {
            return true;
        }
        return widget;
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        double mouseX = input.x(), mouseY = input.y(); int button = input.rawButton(); double dragX = input.deltaX(), dragY = input.deltaY();
        int scrollHeight = visibleFieldRows() * ROW_HEIGHT - 4;
        if (fieldScroll.drag(mouseY, fieldBaseY(), scrollHeight, 18)) {
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        double mouseX = input.x(), mouseY = input.y(); int button = input.rawButton();
        return fieldScroll.release(input.button())
                || false;
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        double mouseX = input.x(), mouseY = input.y(), delta = input.deltaY();
        int scrollHeight = visibleFieldRows() * ROW_HEIGHT - 4;
        if (KineticTheme.hovering(mouseX, mouseY, 14, fieldBaseY(), 612, scrollHeight) && fieldScroll.scroll(delta)) {
            return true;
        }
        return false;
    }

    @Override
    protected void renderTooltips(int scaledMouseX, int scaledMouseY) {
        if (overlayBlocksInput()) return;
        if (hoveredLeaf != null) {
            Component label = fieldLabel(hoveredLeaf.displayPath());
            List<FormattedCharSequence> lines = new ArrayList<>(KineticText.wrap(label, 320));
            if (!label.getString().equals(hoveredLeaf.displayPath())) {
                lines.addAll(KineticText.wrap(KineticI18n.translatable("gui.taczworkshop.data.tooltip.field_path", hoveredLeaf.displayPath()), 320));
            }
            showFormattedTooltip(lines);
            return;
        }
        if (hoveredAttachmentSummary && kind == TaczDataKind.GUN) {
            showFormattedTooltip(attachmentSummaryTooltip());
            return;
        }
        if (hoveredHeaderItem) {
            ItemStack stack = TaczDataStackUtil.build(kind, id);
            List<FormattedCharSequence> lines = new ArrayList<>();
            if (resourceAvailable && !stack.isEmpty()) {
                lines.addAll(KineticText.wrap(displayName(stack), 320));
            } else lines.addAll(KineticText.wrap(KineticI18n.translatable("gui.taczworkshop.data.resource_missing"), 320));
            lines.addAll(KineticText.wrap(KineticI18n.translatable("gui.taczworkshop.data.tooltip.item_id", id), 320));
            if (!dataId.isBlank()) lines.addAll(KineticText.wrap(KineticI18n.translatable("gui.taczworkshop.data.tooltip.data_id", dataId), 320));
            showFormattedTooltip(lines);
        }
    }


    private Component displayName(ItemStack stack) {
        if (!nameKey.isBlank() && KineticI18n.hasTranslation(nameKey)) return KineticI18n.translatable(nameKey);
        if (resourceAvailable && !stack.isEmpty()) {
            Component name = TaczPreviewIndexContext.withResult(kind, id, previewIndex, stack::getHoverName);
            String text = name.getString();
            if (!text.isBlank() && !text.equals(stack.getDescriptionId()) && !text.startsWith("item.")) return name;
        }
        return Component.literal(id);
    }


    private void renderAttachmentSummary(KineticGraphics graphics, int mouseX, int mouseY) {
        Set<String> enabled = attachmentTypes();
        hoveredAttachmentSummary = KineticTheme.hovering(mouseX, mouseY, ATTACHMENT_SUMMARY_X, ATTACHMENT_SUMMARY_Y, ATTACHMENT_SUMMARY_WIDTH, ATTACHMENT_SUMMARY_HEIGHT);
        KineticTheme.panel(
                graphics,
                ATTACHMENT_SUMMARY_X,
                ATTACHMENT_SUMMARY_Y,
                ATTACHMENT_SUMMARY_WIDTH,
                ATTACHMENT_SUMMARY_HEIGHT
        );
        if (hoveredAttachmentSummary) KineticTheme.stateOutline(graphics, ATTACHMENT_SUMMARY_X, ATTACHMENT_SUMMARY_Y, ATTACHMENT_SUMMARY_WIDTH, ATTACHMENT_SUMMARY_HEIGHT, false, true, false);
        graphics.text(KineticI18n.translatable("gui.taczworkshop.data.slot.summary", enabled.size(), ATTACHMENT_TYPES.size()), ATTACHMENT_SUMMARY_X + 8, ATTACHMENT_SUMMARY_Y + 7, 0xFFFFFFFF, false);
    }

    private List<FormattedCharSequence> attachmentSummaryTooltip() {
        List<FormattedCharSequence> lines = new ArrayList<>(KineticText.wrap(KineticI18n.translatable("gui.taczworkshop.data.slot.summary.title"), 320));
        Set<String> enabled = attachmentTypes();
        for (String type : ATTACHMENT_TYPES) {
            lines.addAll(KineticText.wrap(KineticI18n.translatable(
                    "gui.taczworkshop.data.slot.summary.line",
                    KineticI18n.translatable("gui.taczworkshop.attachment." + type),
                    KineticI18n.translatable(enabled.contains(type) ? "gui.taczworkshop.data.boolean.true" : "gui.taczworkshop.data.boolean.false")
            ), 320));
        }
        lines.addAll(KineticText.wrap(KineticI18n.translatable("tip.taczworkshop.data.slot.context"), 320));
        return lines;
    }

    private void openAttachmentContextMenu(int mouseX, int mouseY) {
        Set<String> enabled = attachmentTypes();
        List<KineticOverlays.MenuItem> items = new ArrayList<>();
        for (String type : ATTACHMENT_TYPES) {
            boolean on = enabled.contains(type);
            items.add(KineticOverlays.MenuItem.toggle(
                    KineticI18n.translatable("gui.taczworkshop.attachment." + type),
                    KineticI18n.translatable(on ? "tip.taczworkshop.data.slot.enabled" : "tip.taczworkshop.data.slot.disabled"),
                    on,
                    () -> {
                        toggleAttachmentType(type);
                        openAttachmentContextMenu(mouseX, mouseY);
                    }
            ));
        }
        openContextMenu(mouseX, mouseY, items);
    }


    @Override
    protected boolean onCloseRequested() {
        stageCurrentEdit();
        return false;
    }

    private void stageCurrentEdit() {
        if (!dirty) return;
        TaczDataClientState.stageOverride(kind, id, dataId, removed, data.deepCopy());
        dirty = false;
    }

    private void markDirty() {
        dirty = true;
        TaczDataClientState.stageOverride(kind, id, dataId, removed, data.deepCopy());
    }

    private Component fieldLabel(String path) {
        if (path == null) return Component.empty();
        Component ammoType = ammoTypeFieldLabel(path);
        if (ammoType != null) return ammoType;
        Component fireModeScoped = fireModeScopedFieldLabel(path);
        if (fireModeScoped != null) return fireModeScoped;
        if (path.startsWith("extended_mag_ammo_amount[") && path.endsWith("]")) {
            int index = arrayIndex(path);
            if (index >= 0) return KineticI18n.translatable("gui.taczworkshop.data.field.extended_mag_ammo_amount", index + 1);
        }
        if (path.startsWith("fire_mode[") && path.endsWith("]")) {
            int index = arrayIndex(path);
            if (index >= 0) return KineticI18n.translatable("gui.taczworkshop.data.field.fire_mode", index + 1);
        }
        Component lrTactical = lrTacticalLabel(path);
        if (lrTactical != null) return lrTactical;
        Component recoil = recoilLabel(path);
        if (recoil != null) return recoil;
        Component damageAdjust = damageAdjustLabel(path);
        if (damageAdjust != null) return damageAdjust;

        return switch (path) {
            case "ammo" -> KineticI18n.translatable("gui.taczworkshop.data.field.ammo_id");
            case "bolt" -> KineticI18n.translatable("gui.taczworkshop.data.field.bolt");
            case "bullet.damage" -> KineticI18n.translatable("gui.taczworkshop.data.field.damage");
            case "bullet.explosion.explode" -> KineticI18n.translatable("gui.taczworkshop.data.field.explosion_enabled");
            case "bullet.explosion.damage" -> KineticI18n.translatable("gui.taczworkshop.data.field.explosion_damage");
            case "bullet.explosion.radius" -> KineticI18n.translatable("gui.taczworkshop.data.field.explosion_radius");
            case "bullet.explosion.knockback" -> KineticI18n.translatable("gui.taczworkshop.data.field.explosion_knockback");
            case "bullet.explosion.destroy_block" -> KineticI18n.translatable("gui.taczworkshop.data.field.explosion_destroy_block");
            case "bullet.explosion.delay" -> KineticI18n.translatable("gui.taczworkshop.data.field.explosion_delay");
            case "bullet.bullet_amount" -> KineticI18n.translatable("gui.taczworkshop.data.field.bullet_amount");
            case "rpm" -> KineticI18n.translatable("gui.taczworkshop.data.field.rpm");
            case "can_crawl" -> KineticI18n.translatable("gui.taczworkshop.data.field.can_crawl");
            case "can_slide" -> KineticI18n.translatable("gui.taczworkshop.data.field.can_slide");
            case "bolt_action_time" -> KineticI18n.translatable("gui.taczworkshop.data.field.bolt_action_time");
            case "bolt_feed_time" -> KineticI18n.translatable("gui.taczworkshop.data.field.bolt_feed_time");
            case "crawl_recoil_multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.crawl_recoil_multiplier");
            case "hurt_bob_tweak_multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.hurt_bob_tweak_multiplier");
            case "ammo_amount" -> KineticI18n.translatable("gui.taczworkshop.data.field.ammo_amount");
            case "bullet.speed" -> KineticI18n.translatable("gui.taczworkshop.data.field.bullet_speed");
            case "bullet.life" -> KineticI18n.translatable("gui.taczworkshop.data.field.bullet_life");
            case "bullet.gravity" -> KineticI18n.translatable("gui.taczworkshop.data.field.gravity");
            case "bullet.knockback" -> KineticI18n.translatable("gui.taczworkshop.data.field.knockback");
            case "bullet.friction" -> KineticI18n.translatable("gui.taczworkshop.data.field.friction");
            case "bullet.pierce" -> KineticI18n.translatable("gui.taczworkshop.data.field.pierce");
            case "bullet.ignite" -> KineticI18n.translatable("gui.taczworkshop.data.field.ignite");
            case "bullet.ignite.entity" -> KineticI18n.translatable("gui.taczworkshop.data.field.ignite_entity");
            case "bullet.ignite.block" -> KineticI18n.translatable("gui.taczworkshop.data.field.ignite_block");
            case "bullet.ignite_entity_time" -> KineticI18n.translatable("gui.taczworkshop.data.field.ignite_entity_time");
            case "bullet.tracer_count_interval" -> KineticI18n.translatable("gui.taczworkshop.data.field.tracer_interval");
            case "bullet.extra_damage.armor_ignore" -> KineticI18n.translatable("gui.taczworkshop.data.field.armor_ignore");
            case "bullet.extra_damage.head_shot_multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.headshot");
            case "inaccuracy.stand" -> KineticI18n.translatable("gui.taczworkshop.data.field.spread_stand");
            case "inaccuracy.move" -> KineticI18n.translatable("gui.taczworkshop.data.field.spread_move");
            case "inaccuracy.sneak" -> KineticI18n.translatable("gui.taczworkshop.data.field.spread_sneak");
            case "inaccuracy.lie" -> KineticI18n.translatable("gui.taczworkshop.data.field.spread_lie");
            case "inaccuracy.aim" -> KineticI18n.translatable("gui.taczworkshop.data.field.spread_aim");
            case "movement_speed.base" -> KineticI18n.translatable("gui.taczworkshop.data.field.move_speed_base");
            case "movement_speed.aim" -> KineticI18n.translatable("gui.taczworkshop.data.field.move_speed_aim");
            case "movement_speed.reload" -> KineticI18n.translatable("gui.taczworkshop.data.field.move_speed_reload");
            case "weight" -> KineticI18n.translatable("gui.taczworkshop.data.field.weight");
            case "draw_time" -> KineticI18n.translatable("gui.taczworkshop.data.field.draw_time");
            case "put_away_time" -> KineticI18n.translatable("gui.taczworkshop.data.field.put_away_time");
            case "aim_time" -> KineticI18n.translatable("gui.taczworkshop.data.field.aim_time");
            case "sprint_time" -> KineticI18n.translatable("gui.taczworkshop.data.field.sprint_time");
            case "reload.type" -> KineticI18n.translatable("gui.taczworkshop.data.field.reload_type");
            case "reload.infinite" -> KineticI18n.translatable("gui.taczworkshop.data.field.reload_infinite");
            case "reload.feed.empty" -> KineticI18n.translatable("gui.taczworkshop.data.field.reload_feed_empty");
            case "reload.feed.tactical" -> KineticI18n.translatable("gui.taczworkshop.data.field.reload_feed_tactical");
            case "reload.cooldown.empty" -> KineticI18n.translatable("gui.taczworkshop.data.field.reload_cooldown_empty");
            case "reload.cooldown.tactical" -> KineticI18n.translatable("gui.taczworkshop.data.field.reload_cooldown_tactical");
            case "fire_sound.fire_multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.fire_sound_multiplier");
            case "fire_sound.silence_multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.silence_sound_multiplier");
            case "burst_data.continuous_shoot" -> KineticI18n.translatable("gui.taczworkshop.data.field.burst_continuous");
            case "burst_data.count" -> KineticI18n.translatable("gui.taczworkshop.data.field.burst_count");
            case "burst_data.bpm" -> KineticI18n.translatable("gui.taczworkshop.data.field.burst_bpm");
            case "burst_data.min_interval" -> KineticI18n.translatable("gui.taczworkshop.data.field.burst_min_interval");
            case "melee.distance" -> KineticI18n.translatable("gui.taczworkshop.data.field.gun_melee_distance");
            case "melee.cooldown" -> KineticI18n.translatable("gui.taczworkshop.data.field.gun_melee_cooldown");
            case "melee.default.animation_type" -> KineticI18n.translatable("gui.taczworkshop.data.field.gun_melee_animation");
            case "melee.default.distance" -> KineticI18n.translatable("gui.taczworkshop.data.field.gun_melee_default_distance");
            case "melee.default.range_angle" -> KineticI18n.translatable("gui.taczworkshop.data.field.gun_melee_range_angle");
            case "melee.default.cooldown" -> KineticI18n.translatable("gui.taczworkshop.data.field.gun_melee_default_cooldown");
            case "melee.default.damage" -> KineticI18n.translatable("gui.taczworkshop.data.field.gun_melee_damage");
            case "melee.default.knockback" -> KineticI18n.translatable("gui.taczworkshop.data.field.gun_melee_knockback");
            case "melee.default.prep" -> KineticI18n.translatable("gui.taczworkshop.data.field.gun_melee_prep");
            case "heat.max" -> KineticI18n.translatable("gui.taczworkshop.data.field.heat_max");
            case "heat.per_shot" -> KineticI18n.translatable("gui.taczworkshop.data.field.heat_per_shot");
            case "heat.cooling_multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.heat_cooling_multiplier");
            case "heat.cooling_delay" -> KineticI18n.translatable("gui.taczworkshop.data.field.heat_cooling_delay");
            case "heat.over_heat_time" -> KineticI18n.translatable("gui.taczworkshop.data.field.heat_overheat_time");
            case "heat.min_inaccuracy" -> KineticI18n.translatable("gui.taczworkshop.data.field.heat_min_inaccuracy");
            case "heat.max_inaccuracy" -> KineticI18n.translatable("gui.taczworkshop.data.field.heat_max_inaccuracy");
            case "heat.min_rpm_mod" -> KineticI18n.translatable("gui.taczworkshop.data.field.heat_min_rpm_mod");
            case "heat.max_rpm_mod" -> KineticI18n.translatable("gui.taczworkshop.data.field.heat_max_rpm_mod");
            case "extended_mag_level" -> KineticI18n.translatable("gui.taczworkshop.data.field.extended_mag_level");
            case "stack_size" -> KineticI18n.translatable("gui.taczworkshop.data.field.stack_size");
            case "sort" -> KineticI18n.translatable("gui.taczworkshop.data.field.sort");
            case "damage.multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.damage_multiplier");
            case "damage.addend" -> KineticI18n.translatable("gui.taczworkshop.data.field.damage_addend");
            case "damage.percent" -> KineticI18n.translatable("gui.taczworkshop.data.field.damage_percent");
            case "ads.addend", "ads_addend" -> KineticI18n.translatable("gui.taczworkshop.data.field.ads_addend");
            case "ads.multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.ads_multiplier");
            case "ads.percent" -> KineticI18n.translatable("gui.taczworkshop.data.field.ads_percent");
            case "inaccuracy.addend", "inaccuracy_addend" -> KineticI18n.translatable("gui.taczworkshop.data.field.inaccuracy_addend");
            case "inaccuracy.multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.inaccuracy_multiplier");
            case "inaccuracy.percent" -> KineticI18n.translatable("gui.taczworkshop.data.field.inaccuracy_percent");
            case "aim_inaccuracy.addend" -> KineticI18n.translatable("gui.taczworkshop.data.field.aim_inaccuracy_addend");
            case "aim_inaccuracy.multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.aim_inaccuracy_multiplier");
            case "aim_inaccuracy.percent" -> KineticI18n.translatable("gui.taczworkshop.data.field.aim_inaccuracy_percent");
            case "recoil_modifier.pitch" -> KineticI18n.translatable("gui.taczworkshop.data.field.recoil_pitch_modifier");
            case "recoil_modifier.yaw" -> KineticI18n.translatable("gui.taczworkshop.data.field.recoil_yaw_modifier");
            case "recoil.pitch.multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.recoil_pitch_multiplier");
            case "recoil.yaw.multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.recoil_yaw_multiplier");
            case "rpm.multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.rpm_multiplier");
            case "rpm.addend" -> KineticI18n.translatable("gui.taczworkshop.data.field.rpm_addend");
            case "rpm.percent" -> KineticI18n.translatable("gui.taczworkshop.data.field.rpm_percent");
            case "armor_ignore.addend" -> KineticI18n.translatable("gui.taczworkshop.data.field.armor_ignore_addend");
            case "armor_ignore.multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.armor_ignore_multiplier");
            case "armor_ignore.percent" -> KineticI18n.translatable("gui.taczworkshop.data.field.armor_ignore_percent");
            case "head_shot.addend" -> KineticI18n.translatable("gui.taczworkshop.data.field.headshot_addend");
            case "head_shot.multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.headshot_multiplier");
            case "pierce.addend" -> KineticI18n.translatable("gui.taczworkshop.data.field.pierce_addend");
            case "pierce.multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.pierce_multiplier");
            case "ammo_speed.addend" -> KineticI18n.translatable("gui.taczworkshop.data.field.ammo_speed_addend");
            case "ammo_speed.multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.ammo_speed_multiplier");
            case "ammo_speed.percent" -> KineticI18n.translatable("gui.taczworkshop.data.field.ammo_speed_percent");
            default -> genericFieldLabel(path);
        };
    }

    private Component ammoTypeFieldLabel(String path) {
        String prefix = "extras.ammo_types[";
        if (path == null || !path.startsWith(prefix)) return null;
        int close = path.indexOf(']', prefix.length());
        if (close <= prefix.length()) return null;
        int index;
        try {
            index = Integer.parseInt(path.substring(prefix.length(), close)) + 1;
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (close + 1 >= path.length()) {
            return KineticI18n.translatable("gui.taczworkshop.data.field.ammo_type", index);
        }
        String relative = path.charAt(close + 1) == '.' ? path.substring(close + 2) : path.substring(close + 1);
        if (relative.isBlank()) return KineticI18n.translatable("gui.taczworkshop.data.field.ammo_type", index);
        return KineticI18n.translatable("gui.taczworkshop.data.field.ammo_type.wrap", index, fieldLabel(relative));
    }

    private Component fireModeScopedFieldLabel(String path) {
        if (path == null || path.isBlank()) return null;
        String prefix;
        String wrapKey;
        if (path.startsWith("fire_mode_adjust.")) {
            prefix = "fire_mode_adjust.";
            wrapKey = "gui.taczworkshop.data.field.fire_mode_adjust.wrap";
        } else if (path.startsWith("charging.")) {
            prefix = "charging.";
            wrapKey = "gui.taczworkshop.data.field.charging.wrap";
        } else {
            return null;
        }
        String remainder = path.substring(prefix.length());
        int dot = remainder.indexOf('.');
        if (dot <= 0 || dot + 1 >= remainder.length()) return null;
        String mode = remainder.substring(0, dot);
        String relative = remainder.substring(dot + 1);
        return KineticI18n.translatable(wrapKey, genericSegmentLabel(mode), fieldLabel(relative));
    }

    private Component genericFieldLabel(String path) {
        if (path == null || path.isBlank()) return Component.empty();
        String normalized = path.startsWith("extras.") ? path.substring("extras.".length()) : path;
        int dot = normalized.lastIndexOf('.');
        String segment = dot >= 0 ? normalized.substring(dot + 1) : normalized;
        int open = segment.lastIndexOf('[');
        int close = segment.endsWith("]") ? segment.length() - 1 : -1;
        if (open > 0 && close > open + 1) {
            try {
                int index = Integer.parseInt(segment.substring(open + 1, close)) + 1;
                return KineticI18n.translatable("gui.taczworkshop.data.field.indexed", genericSegmentLabel(segment.substring(0, open)), index);
            } catch (NumberFormatException ignored) {
            }
        }
        return genericSegmentLabel(segment);
    }

    private Component genericSegmentLabel(String segment) {
        if (segment == null || segment.isBlank()) return Component.empty();
        String safe = segment.toLowerCase(Locale.ROOT).replace(':', '_').replace('-', '_');
        String key = "gui.taczworkshop.data.field.generic." + safe;
        if (KineticI18n.hasTranslation(key)) return KineticI18n.translatable(key);
        String[] parts = safe.split("_+");
        if (parts.length > 1) {
            Component result = genericSimpleSegmentLabel(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                result = KineticI18n.translatable("gui.taczworkshop.data.field.compound", result, genericSimpleSegmentLabel(parts[i]));
            }
            return result;
        }
        return KineticI18n.translatable("gui.taczworkshop.data.field.custom", segment);
    }

    private Component genericSimpleSegmentLabel(String segment) {
        String key = "gui.taczworkshop.data.field.generic." + segment;
        if (KineticI18n.hasTranslation(key)) return KineticI18n.translatable(key);
        return KineticI18n.translatable("gui.taczworkshop.data.field.custom", segment);
    }

    private Component lrTacticalLabel(String path) {
        if (path == null || path.isBlank()) return null;

        if (path.startsWith("attack.attack_left.")) {
            Component label = lrMeleeAttackLabel(path.substring("attack.attack_left.".length()));
            return label == null ? null : KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.attack.wrap", KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.attack.left"), label);
        }
        if (path.startsWith("attack.attack_right.")) {
            Component label = lrMeleeAttackLabel(path.substring("attack.attack_right.".length()));
            return label == null ? null : KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.attack.wrap", KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.attack.right"), label);
        }

        if (path.startsWith("effects[") && path.contains("].")) {
            int close = path.indexOf(']');
            int index = arrayIndex(path.substring(0, close + 1));
            if (index >= 0) {
                String key = getKey(path, close);
                if (key != null) return KineticI18n.translatable(key, index + 1);
            }
        }

        if (path.startsWith("cloud.effects[") && path.contains("].")) {
            int open = path.indexOf('[');
            int close = path.indexOf(']', open + 1);
            if (open >= 0 && close > open + 1) {
                int index;
                try {
                    index = Integer.parseInt(path.substring(open + 1, close));
                } catch (NumberFormatException ignored) {
                    index = -1;
                }
                if (index >= 0) {
                    String key = getString(path, close);
                    if (key != null) return KineticI18n.translatable(key, index + 1);
                }
            }
        }

        if (path.startsWith("remove_effects[") && path.endsWith("]")) {
            int index = arrayIndex(path);
            if (index >= 0) return KineticI18n.translatable("gui.taczworkshop.data.field.lr.remove_effect", index + 1);
        }

        return switch (path) {
            case "attributes.generic.attack_damage" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.attack_damage");
            case "attributes.minecraft:generic.movement_speed.amount" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.movement_speed_amount");
            case "attributes.minecraft:generic.movement_speed.operation" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.movement_speed_operation");
            case "enchantment_value" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.enchantment_value");
            case "max_durability" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.max_durability");
            case "cooldown" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.cooldown");
            case "cooldown_category" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.cooldown_category");
            case "durability_damage" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.durability_damage");
            case "food" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.food");
            case "heal" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.heal");
            case "saturation" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.saturation");
            case "use_duration" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.use_duration");
            case "use_mode" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.use_mode");
            case "prepare_time" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.prepare_time");
            case "cookable" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.cookable");
            case "initial_speed" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.initial_speed");
            case "entity.life_time" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.entity.life_time");
            case "entity.gravity" -> KineticI18n.translatable("gui.taczworkshop.data.field.gravity");
            case "entity.hit_damage" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.entity.hit_damage");
            case "entity.should_bounce" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.entity.should_bounce");
            case "entity.broke_on_ground" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.entity.broke_on_ground");
            case "entity.bounce_factor" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.entity.bounce_factor");
            case "entity.tail_particles" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.entity.tail_particles");
            case "explode.radius" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.explode.radius");
            case "explode.damage" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.explode.damage");
            case "explode.destroy_blocks" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.explode.destroy_blocks");
            case "explode.destroy_multiplier" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.explode.destroy_multiplier");
            case "explode.screen_shake_time" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.explode.screen_shake_time");
            case "explode.screen_shake_amplitude" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.explode.screen_shake_amplitude");
            case "explode.trigger_on_explode" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.explode.trigger_on_explode");
            case "explode.remote_detonation" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.explode.remote_detonation");
            case "stun.radius" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.stun.radius");
            case "stun.blind.max_angle" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.stun.blind.max_angle");
            case "stun.blind.max_duration" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.stun.blind.max_duration");
            case "stun.blind.min_duration" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.stun.blind.min_duration");
            case "stun.blind.view_angle_factor" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.stun.blind.view_angle_factor");
            case "stun.deafened.max_duration" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.stun.deafened.max_duration");
            case "stun.deafened.min_duration" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.stun.deafened.min_duration");
            case "cloud.area_cloud" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.cloud.area_cloud");
            case "cloud.duration" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.cloud.duration");
            case "cloud.extinguish_by_smoke" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.cloud.extinguish_by_smoke");
            case "cloud.ignite" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.cloud.ignite");
            case "cloud.ignite_time" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.cloud.ignite_time");
            case "cloud.particles" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.cloud.particles");
            case "cloud.radius" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.cloud.radius");
            case "cloud.radius_per_tick" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.cloud.radius_per_tick");
            case "cloud.wait_time" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.cloud.wait_time");
            default -> null;
        };
    }

    private static @Nullable String getKey(String path, int close) {
        String suffix = path.substring(close + 2);
        return switch (suffix) {
            case "amplifier" -> "gui.taczworkshop.data.field.lr.effect.amplifier";
            case "chance" -> "gui.taczworkshop.data.field.lr.effect.chance";
            case "duration" -> "gui.taczworkshop.data.field.lr.effect.duration";
            case "id" -> "gui.taczworkshop.data.field.lr.effect.id";
            case "ambient" -> "gui.taczworkshop.data.field.lr.effect.ambient";
            case "visible" -> "gui.taczworkshop.data.field.lr.effect.visible";
            case "show_icon" -> "gui.taczworkshop.data.field.lr.effect.show_icon";
            default -> null;
        };
    }

    private static @Nullable String getString(String path, int close) {
        String suffix = path.substring(close + 2);
        return switch (suffix) {
            case "amplifier" -> "gui.taczworkshop.data.field.lr.cloud_effect.amplifier";
            case "duration" -> "gui.taczworkshop.data.field.lr.cloud_effect.duration";
            case "type" -> "gui.taczworkshop.data.field.lr.cloud_effect.type";
            case "visible" -> "gui.taczworkshop.data.field.lr.cloud_effect.visible";
            case "show_icon" -> "gui.taczworkshop.data.field.lr.cloud_effect.show_icon";
            default -> null;
        };
    }

    private Component lrMeleeAttackLabel(String suffix) {
        return switch (suffix) {
            case "factor" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.attack.factor");
            case "knockback" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.attack.knockback");
            case "cooldown" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.attack.cooldown");
            case "delay" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.attack.delay");
            case "durability_damage" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.attack.durability_damage");
            case "hitbox.type" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.hitbox.type");
            case "hitbox.max_range" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.hitbox.max_range");
            case "hitbox.max_angle" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.hitbox.max_angle");
            case "hitbox.exclude_self" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.hitbox.exclude_self");
            case "hitbox.penetration" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.hitbox.penetration");
            case "hitbox.half_width" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.hitbox.half_width");
            case "hitbox.half_height" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.hitbox.half_height");
            case "hitbox.roll" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.hitbox.roll");
            case "movement.delay" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.movement.delay");
            case "movement.speed" -> KineticI18n.translatable("gui.taczworkshop.data.field.lr.melee.movement.speed");
            default -> null;
        };
    }

    private Component recoilLabel(String path) {
        if (!path.startsWith("recoil.")) return null;
        String axis;
        String remainder;
        if (path.startsWith("recoil.pitch[")) {
            axis = "pitch";
            remainder = path.substring("recoil.pitch[".length());
        } else if (path.startsWith("recoil.yaw[")) {
            axis = "yaw";
            remainder = path.substring("recoil.yaw[".length());
        } else {
            return null;
        }
        int close = remainder.indexOf(']');
        if (close < 1) return null;
        int index;
        try {
            index = Integer.parseInt(remainder.substring(0, close)) + 1;
        } catch (NumberFormatException ignored) {
            return null;
        }
        String suffix = remainder.substring(close + 1);
        String key;
        switch (suffix) {
            case ".time" -> key = "gui.taczworkshop.data.field.recoil." + axis + ".time";
            case ".value[0]" -> key = "gui.taczworkshop.data.field.recoil." + axis + ".min";
            case ".value[1]" -> key = "gui.taczworkshop.data.field.recoil." + axis + ".max";
            default -> {
                return null;
            }
        }
        return KineticI18n.translatable(key, index);
    }

    private Component damageAdjustLabel(String path) {
        String prefix = "bullet.extra_damage.damage_adjust[";
        if (!path.startsWith(prefix)) return null;
        String remainder = path.substring(prefix.length());
        int close = remainder.indexOf(']');
        if (close < 1) return null;
        int index;
        try {
            index = Integer.parseInt(remainder.substring(0, close)) + 1;
        } catch (NumberFormatException ignored) {
            return null;
        }
        String suffix = remainder.substring(close + 1);
        if (".distance".equals(suffix)) return KineticI18n.translatable("gui.taczworkshop.data.field.damage_curve.distance", index);
        if (".damage".equals(suffix)) return KineticI18n.translatable("gui.taczworkshop.data.field.damage_curve.damage", index);
        return null;
    }

    private static int arrayIndex(String path) {
        int open = path.lastIndexOf('[');
        int close = path.lastIndexOf(']');
        if (open < 0 || close <= open + 1) return -1;
        try {
            return Integer.parseInt(path.substring(open + 1, close));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String read(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return "";
        return object.get(key).getAsString();
    }

    private static boolean bool(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean();
    }
}
