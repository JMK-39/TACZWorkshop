package dev.xyat.taczworkshop.client.gui;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.text.KineticText;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.taczworkshop.client.TaczClientState;
import dev.xyat.taczworkshop.client.TaczDataClientState;
import dev.xyat.taczworkshop.client.TaczDataListEntry;
import dev.xyat.taczworkshop.client.TaczPreviewIndexContext;
import dev.xyat.taczworkshop.data.TaczDataKind;
import dev.xyat.taczworkshop.data.TaczRecipeCodec;
import dev.xyat.taczworkshop.data.TaczRecipeRecord;
import dev.xyat.taczworkshop.network.TaczRecipeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.HighZButton;
import dev.xyat.kineticcore.api.client.widget.button.KineticButtons.StateButton;
import dev.xyat.kineticcore.api.client.widget.input.KineticTextFields.KineticEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TaczRecipeListScreen extends KineticScreen {
    private static final List<String> CREATE_TYPES = List.of("gun", "attachment", "ammo", "melee", "throwable", "consumable", "custom");
    private static final int SEARCH_X = 14;
    private static final int SEARCH_Y = 10;
    private static final int SEARCH_W = 210;
    private static final int GRID_X = 14;
    private static final int GRID_Y = 60;
    private static final int SLOT_SIZE = 18;
    private static final int CELL_SIZE = 19;
    private static final int COLUMNS = 25;
    private static final int ROWS_VISIBLE = 15;
    private static final int GRID_WIDTH = COLUMNS * CELL_SIZE;
    private static final int GRID_HEIGHT = ROWS_VISIBLE * CELL_SIZE;
    private static final int SCROLL_X = GRID_X + GRID_WIDTH + 4;
    private static final int SCROLL_WIDTH = 4;
    private static final int INFO_X = SCROLL_X + SCROLL_WIDTH + 4;
    private static final int INFO_Y = GRID_Y;
    private static final int INFO_WIDTH = 123;
    private static final int INFO_HEIGHT = GRID_HEIGHT;

    private enum StatusFilter {
        ALL("gui.taczworkshop.recipe.filter.all"),
        ORIGINAL("gui.taczworkshop.recipe.filter.original"),
        CREATED("gui.taczworkshop.recipe.filter.created"),
        REMOVED("gui.taczworkshop.recipe.filter.removed");

        private final String key;

        StatusFilter(String key) {
            this.key = key;
        }
    }

    private enum CategoryFilter {
        ALL("all"),
        GUN("gun"),
        ATTACHMENT("attachment"),
        AMMO("ammo"),
        MELEE("melee"),
        THROWABLE("throwable"),
        CONSUMABLE("consumable"),
        OTHER("other");

        private final String key;

        CategoryFilter(String key) {
            this.key = key;
        }
    }

    private final Screen parent;
    private final String initialSearch;
    private final List<TaczRecipeRecord> source = new ArrayList<>();
    private final List<TaczRecipeRecord> filtered = new ArrayList<>();
    private final Map<String, ItemStack> previewCache = new HashMap<>();
    private final Map<String, Boolean> pendingOriginalDisabled = new LinkedHashMap<>();
    private final Map<String, TaczRecipeRecord> pendingRecordSaves = new LinkedHashMap<>();
    private final Set<String> pendingDeletedUuids = new LinkedHashSet<>();
    private final GridScrollController gridScroll = new GridScrollController();
    private StatusFilter statusFilter = StatusFilter.ALL;
    private CategoryFilter categoryFilter = CategoryFilter.ALL;
    private KineticEditBox search;
    private HighZButton categoryButton;
    private HighZButton createButton;
    private TaczRecipeRecord hoveredRecord;

    public TaczRecipeListScreen(Screen parent) {
        this(parent, "");
    }

    public TaczRecipeListScreen(Screen parent, String initialSearch) {
        super(Component.translatable("gui.taczworkshop.list.title"));
        setParentScreen(parent);
        this.parent = parent;
        this.initialSearch = initialSearch == null ? "" : initialSearch;
        source.addAll(copyRecords(TaczClientState.snapshot()));
        configureStandaloneDraft(this::captureListSnapshot, this::restoreListSnapshot);
    }

    private record ListSnapshot(
            List<TaczRecipeRecord> source,
            Map<String, Boolean> originalDisabled,
            Map<String, TaczRecipeRecord> recordSaves,
            Set<String> deletedUuids
    ) {
    }

    private ListSnapshot captureListSnapshot() {
        return new ListSnapshot(
                copyRecords(source),
                new LinkedHashMap<>(pendingOriginalDisabled),
                copyRecordMap(pendingRecordSaves),
                new LinkedHashSet<>(pendingDeletedUuids)
        );
    }

    private void restoreListSnapshot(ListSnapshot snapshot) {
        if (snapshot == null) return;
        source.clear();
        source.addAll(copyRecords(snapshot.source()));
        pendingOriginalDisabled.clear();
        pendingOriginalDisabled.putAll(snapshot.originalDisabled());
        pendingRecordSaves.clear();
        pendingRecordSaves.putAll(copyRecordMap(snapshot.recordSaves()));
        pendingDeletedUuids.clear();
        pendingDeletedUuids.addAll(snapshot.deletedUuids());
        previewCache.clear();
        rebuildFiltered();
    }

    private static List<TaczRecipeRecord> copyRecords(List<TaczRecipeRecord> records) {
        List<TaczRecipeRecord> copies = new ArrayList<>();
        if (records == null) return copies;
        for (TaczRecipeRecord record : records) {
            if (record != null) copies.add(record.copy());
        }
        return copies;
    }

    private static Map<String, TaczRecipeRecord> copyRecordMap(Map<String, TaczRecipeRecord> records) {
        Map<String, TaczRecipeRecord> copies = new LinkedHashMap<>();
        if (records == null) return copies;
        for (Map.Entry<String, TaczRecipeRecord> entry : records.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) copies.put(entry.getKey(), entry.getValue().copy());
        }
        return copies;
    }

    @Override
    protected void buildUi() {
        closeContextMenu();
        String oldSearch = search == null ? initialSearch : search.getValue();
        search = addTextField(SEARCH_X, SEARCH_Y, SEARCH_W, Component.translatable("gui.taczworkshop.search"), Component.translatable("gui.taczworkshop.search.hint"), null, null);
        search.setMaxLength(256);
        search.setValue(oldSearch);
        search.setResponder(value -> {
            gridScroll.reset();
            rebuildFiltered();
        });

        categoryButton = addHighZButton(
                230, 10, 110,
                categoryComponent(),
                null,
                40,
                this::openCategoryMenu
        );

        createButton = addHighZButton(
                344, 10, 92,
                Component.translatable("gui.taczworkshop.recipe.create"),
                null,
                40,
                this::openCreateMenu
        );

        addButton(440, 10, 58, Component.translatable("gui.taczworkshop.refresh"), Component.translatable("tip.taczworkshop.refresh"), TaczRecipeNetwork::requestSnapshot);
        addButton(502, 10, 58, Component.translatable("gui.taczworkshop.save"), Component.translatable("tip.taczworkshop.save"), this::savePendingRecipeListChanges);
        addButton(564, 10, 62, Component.translatable("gui.taczworkshop.back"), Component.translatable("tip.taczworkshop.back.management"), this::onClose);

        StateButton allFilter = addButton(14, 34, 76, Component.translatable(StatusFilter.ALL.key), Component.translatable("tip.taczworkshop.recipe.filter.all"), () -> setStatusFilter(StatusFilter.ALL));
        allFilter.setEnabled(statusFilter != StatusFilter.ALL);
        StateButton originalFilter = addButton(92, 34, 76, Component.translatable(StatusFilter.ORIGINAL.key), Component.translatable("tip.taczworkshop.recipe.filter.original"), () -> setStatusFilter(StatusFilter.ORIGINAL));
        originalFilter.setEnabled(statusFilter != StatusFilter.ORIGINAL);
        StateButton createdFilter = addButton(170, 34, 76, Component.translatable(StatusFilter.CREATED.key), Component.translatable("tip.taczworkshop.recipe.filter.created"), () -> setStatusFilter(StatusFilter.CREATED));
        createdFilter.setEnabled(statusFilter != StatusFilter.CREATED);
        StateButton removedFilter = addButton(248, 34, 76, Component.translatable(StatusFilter.REMOVED.key), Component.translatable("tip.taczworkshop.recipe.filter.removed"), () -> setStatusFilter(StatusFilter.REMOVED));
        removedFilter.setEnabled(statusFilter != StatusFilter.REMOVED);
        refreshFromState();
    }

    public void refreshFromState() {
        source.clear();
        source.addAll(copyRecords(TaczClientState.snapshot()));
        applyPendingChangesToSource();
        previewCache.clear();
        rebuildFiltered();
    }

    private void applyPendingChangesToSource() {
        for (TaczRecipeRecord record : source) {
            if (record.isOriginal()) {
                Boolean disabled = pendingOriginalDisabled.get(record.id());
                if (disabled != null) record.setEnabled(!disabled);
            }
        }
        for (Map.Entry<String, TaczRecipeRecord> entry : pendingRecordSaves.entrySet()) {
            replaceSourceRecord(entry.getKey(), entry.getValue());
        }
        if (!pendingDeletedUuids.isEmpty()) {
            source.removeIf(record -> pendingDeletedUuids.contains(record.uuid()));
        }
    }

    private void replaceSourceRecord(String uuid, TaczRecipeRecord replacement) {
        if (uuid == null || replacement == null) return;
        for (int i = 0; i < source.size(); i++) {
            if (uuid.equals(source.get(i).uuid())) {
                source.set(i, replacement.copy());
                return;
            }
        }
        source.add(replacement.copy());
    }

    private void setStatusFilter(StatusFilter next) {
        if (statusFilter == next) return;
        statusFilter = next;
        gridScroll.reset();
        rebuildHeaderWidgets();
    }

    private void rebuildHeaderWidgets() {
        String oldSearch = search == null ? "" : search.getValue();
        search = null;
        rebuildUi();
        if (search != null) search.setValue(oldSearch);
    }

    private void rebuildFiltered() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (TaczRecipeRecord record : source) {
            if (failsStatusFilter(record) || !matchesCategory(record)) continue;
            TaczDataListEntry entry = dataEntry(record);
            String localizedName = localizedResultName(record, entry, null).getString().toLowerCase(Locale.ROOT);
            String dataId = entry == null ? "" : entry.dataId().toLowerCase(Locale.ROOT);
            if (query.isEmpty() || record.searchableText().contains(query) || localizedName.contains(query) || dataId.contains(query)) filtered.add(record);
        }
        filtered.sort(Comparator
                .comparing(TaczRecipeRecord::hasIssue).reversed()
                .thenComparing(Comparator.comparing(TaczRecipeRecord::isOriginal).reversed())
                .thenComparing(TaczRecipeRecord::id));
        int totalRows = (filtered.size() + COLUMNS - 1) / COLUMNS;
        gridScroll.update(totalRows, ROWS_VISIBLE);
    }

    private boolean failsStatusFilter(TaczRecipeRecord record) {
        return switch (statusFilter) {
            case ALL -> !record.enabled();
            case ORIGINAL -> (!record.isOriginal()) || (!record.enabled());
            case CREATED -> (record.isOriginal()) || (!record.enabled());
            case REMOVED -> record.enabled();
        };
    }

    private boolean matchesCategory(TaczRecipeRecord record) {
        if (categoryFilter == CategoryFilter.ALL) return true;
        return switch (record.resultType()) {
            case "gun" -> categoryFilter == CategoryFilter.GUN;
            case "attachment" -> categoryFilter == CategoryFilter.ATTACHMENT;
            case "ammo" -> categoryFilter == CategoryFilter.AMMO;
            case "melee" -> categoryFilter == CategoryFilter.MELEE;
            case "throwable" -> categoryFilter == CategoryFilter.THROWABLE;
            case "consumable" -> categoryFilter == CategoryFilter.CONSUMABLE;
            default -> categoryFilter == CategoryFilter.OTHER;
        };
    }

    private Component categoryComponent() {
        return Component.translatable(
                "gui.taczworkshop.recipe.category",
                Component.translatable("gui.taczworkshop.recipe.category." + categoryFilter.key)
        );
    }

    private void openCategoryMenu() {
        List<KineticOverlays.MenuItem> entries = new ArrayList<>();
        for (CategoryFilter option : CategoryFilter.values()) {
            entries.add(KineticOverlays.MenuItem.toggle(
                    Component.translatable("gui.taczworkshop.recipe.category." + option.key),
                    Component.empty(),
                    option == categoryFilter,
                    () -> setCategoryFilter(option)
            ));
        }
        int x = categoryButton == null ? 230 : categoryButton.getX();
        int y = categoryButton == null ? 32 : categoryButton.getY() + categoryButton.getHeight() + 2;
        openContextMenu(x, y, entries);
    }

    private void setCategoryFilter(CategoryFilter next) {
        if (next == null) return;
        closeContextMenu();
        if (categoryFilter == next) {
            if (categoryButton != null) categoryButton.setText(categoryComponent());
            return;
        }
        categoryFilter = next;
        gridScroll.reset();
        rebuildFiltered();
        if (categoryButton != null) categoryButton.setText(categoryComponent());
    }

    private void openCreateMenu() {
        List<KineticOverlays.MenuItem> entries = new ArrayList<>();
        for (String type : CREATE_TYPES) {
            entries.add(KineticOverlays.MenuItem.action(
                    Component.translatable("gui.taczworkshop.type.short." + type),
                    Component.empty(),
                    () -> create(type)
            ));
        }
        int x = createButton == null ? 344 : createButton.getX();
        int y = createButton == null ? 32 : createButton.getY() + createButton.getHeight() + 2;
        openContextMenu(x, y, entries);
    }

    private void create(String type) {
        KineticClientRuntime.openScreen(new TaczRecipeEditorScreen(this, TaczRecipeRecord.blank(type)));
    }

    private void edit(TaczRecipeRecord record) {
        if (record != null) KineticClientRuntime.openScreen(new TaczRecipeEditorScreen(this, record.copy()));
    }

    private void copy(TaczRecipeRecord record) {
        if (record == null) return;
        TaczRecipeRecord copy = record.duplicate();
        String base = copy.id();
        String candidate = base;
        int suffix = 2;
        while (containsRecipeId(candidate)) candidate = base + suffix++;
        copy.setId(candidate);
        KineticClientRuntime.openScreen(new TaczRecipeEditorScreen(this, copy));
    }

    private boolean containsRecipeId(String id) {
        return source.stream().anyMatch(record -> record.id().equals(id));
    }

    private void toggleEnabled(TaczRecipeRecord record) {
        if (record == null) return;
        if (record.isOriginal()) {
            stageOriginalDisabled(record, record.enabled());
            return;
        }
        record.setEnabled(!record.enabled());
        pendingRecordSaves.put(record.uuid(), record.copy());
        previewCache.remove(record.uuid());
        rebuildFiltered();
    }

    private void stageOriginalDisabled(TaczRecipeRecord record, boolean disabled) {
        if (record == null || !record.isOriginal()) return;
        record.setEnabled(!disabled);
        pendingOriginalDisabled.put(record.id(), disabled);
        previewCache.remove(record.uuid());
        rebuildFiltered();
    }

    private void restoreOriginal(TaczRecipeRecord record) {
        if (record == null || !record.isOriginal() || record.enabled()) return;
        stageOriginalDisabled(record, false);
    }

    private void confirmRestoreOriginal(TaczRecipeRecord record) {
        if (record == null) return;
        openDialog(
                Component.translatable("gui.taczworkshop.recipe.restore.title"),
                Component.translatable("gui.taczworkshop.recipe.restore.message", record.id()),
                Component.translatable("gui.yes"),
                Component.translatable("gui.no"),
                () -> restoreOriginal(record),
                () -> { }
        );
    }

    private void confirmDelete(TaczRecipeRecord record) {
        if (record == null) return;
        openDialog(
                Component.translatable("gui.taczworkshop.delete.title"),
                Component.translatable("gui.taczworkshop.delete.message", record.id()),
                Component.translatable("gui.yes"),
                Component.translatable("gui.no"),
                () -> stageDelete(record),
                () -> { }
        );
    }

    private void stageDelete(TaczRecipeRecord record) {
        if (record == null || record.isOriginal()) return;
        pendingDeletedUuids.add(record.uuid());
        pendingRecordSaves.remove(record.uuid());
        source.removeIf(entry -> record.uuid().equals(entry.uuid()));
        previewCache.remove(record.uuid());
        rebuildFiltered();
    }

    private void savePendingRecipeListChanges() {
        if (pendingOriginalDisabled.isEmpty() && pendingRecordSaves.isEmpty() && pendingDeletedUuids.isEmpty()) {
            commitDraft();
            return;
        }

        for (Map.Entry<String, Boolean> entry : new LinkedHashMap<>(pendingOriginalDisabled).entrySet()) {
            TaczRecipeNetwork.setOriginalRecipeDisabled(entry.getKey(), entry.getValue());
        }
        for (TaczRecipeRecord record : copyRecordMap(pendingRecordSaves).values()) {
            TaczRecipeNetwork.saveRecord(record);
        }
        for (String uuid : new LinkedHashSet<>(pendingDeletedUuids)) {
            TaczRecipeNetwork.deleteRecord(uuid);
        }

        pendingOriginalDisabled.clear();
        pendingRecordSaves.clear();
        pendingDeletedUuids.clear();
        commitDraft();
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.panel(graphics, 6, 6, 628, 348);
        GuiTheme.panelAlt(graphics, 10, 56, 620, 293);
        GuiTheme.panelAlt(graphics, INFO_X, INFO_Y, INFO_WIDTH, INFO_HEIGHT);
        renderRecipes(graphics, mouseX, mouseY);
        GuiTheme.scrollbar(gridScroll, graphics, mouseX, mouseY, SCROLL_X, GRID_Y, SCROLL_WIDTH, GRID_HEIGHT, 18);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(font, Component.translatable("gui.taczworkshop.list.count", filtered.size()), 390, 41, 0xFFFFFFFF, true);
        renderInfoPanel(graphics);
        if (filtered.isEmpty()) graphics.drawCenteredString(font, Component.translatable("gui.taczworkshop.data.empty"), GRID_X + GRID_WIDTH / 2, GRID_Y + GRID_HEIGHT / 2, 0xFFAAAAAA);
    }

    private void renderRecipes(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredRecord = null;
        int hoveredIndex = indexAt(mouseX, mouseY);
        int baseRow = gridScroll.smoothIndexOffset();
        int visualShift = gridScroll.visualShift(CELL_SIZE);
        int first = baseRow * COLUMNS;
        int last = Math.min(filtered.size(), first + (ROWS_VISIBLE + 2) * COLUMNS);
        enableUiScissor(graphics, GRID_X, GRID_Y, GRID_X + GRID_WIDTH, GRID_Y + GRID_HEIGHT);
        for (int index = first; index < last; index++) {
            int visible = index - first;
            int x = GRID_X + (visible % COLUMNS) * CELL_SIZE;
            int y = GRID_Y + (visible / COLUMNS) * CELL_SIZE - visualShift;
            TaczRecipeRecord record = filtered.get(index);
            boolean hovered = index == hoveredIndex && !overlayBlocksInput();

            GuiTheme.itemSlot(graphics, x, y, hovered);
            TaczDataListEntry previewEntry = dataEntry(record);
            ItemStack stack = previewCache.computeIfAbsent(record.uuid(), ignored -> recipePreview(record, previewEntry));
            if (!stack.isEmpty()) {
                if (previewEntry == null) GuiTheme.item(graphics, font, stack, x, y, SLOT_SIZE, 1.0F, false);
                else TaczPreviewIndexContext.with(previewEntry, () -> GuiTheme.item(graphics, font, stack, x, y, SLOT_SIZE, 1.0F, false));
            }
            if (record.hasIssue()) GuiTheme.indicatorOutline(graphics, x, y, SLOT_SIZE, SLOT_SIZE, GuiTheme.Indicator.DANGER);
            else if (!record.enabled()) GuiTheme.indicatorOutline(graphics, x, y, SLOT_SIZE, SLOT_SIZE, GuiTheme.Indicator.DANGER);
            else if (!record.isOriginal()) GuiTheme.indicatorOutline(graphics, x, y, SLOT_SIZE, SLOT_SIZE, GuiTheme.Indicator.SUCCESS);
            if (hovered) hoveredRecord = record;
        }
        disableUiScissor(graphics);
    }

    private void renderInfoPanel(GuiGraphics graphics) {
        int x = INFO_X + 8;
        int y = INFO_Y + 8;
        if (hoveredRecord == null) {
            graphics.drawString(font, Component.translatable("gui.taczworkshop.list.title"), x, y, 0xFFFFFFFF, true);
            y += 18;
            for (FormattedCharSequence line : font.split(Component.translatable("gui.taczworkshop.recipe.grid.guide"), INFO_WIDTH - 16)) {
                graphics.drawString(font, line, x, y, 0xFFCCCCCC, false);
                y += font.lineHeight + 2;
            }
            return;
        }
        if (hoveredRecord.hasIssue()) {
            graphics.drawString(font, Component.translatable("gui.taczworkshop.recipe.error.badge"), x, y, 0xFFFF5656, true);
            y += 14;
            KineticText.drawScrollingLeft(graphics, font, hoveredRecord.id(), x, y, INFO_WIDTH - 16, 0xFFFFFFFF, false);
            y += 13;
            for (FormattedCharSequence line : font.split(issueComponent(hoveredRecord), INFO_WIDTH - 16)) {
                graphics.drawString(font, line, x, y, 0xFFFF7777, false);
                y += font.lineHeight + 1;
                if (y > INFO_Y + INFO_HEIGHT - 60) break;
            }
            if (!hoveredRecord.issueDetail().isBlank() && y <= INFO_Y + INFO_HEIGHT - 45) {
                for (FormattedCharSequence line : font.split(Component.literal(hoveredRecord.issueDetail()), INFO_WIDTH - 16)) {
                    graphics.drawString(font, line, x, y, 0xFFCCAAAA, false);
                    y += font.lineHeight + 1;
                    if (y > INFO_Y + INFO_HEIGHT - 30) break;
                }
            }
            return;
        }
        TaczDataListEntry entry = dataEntry(hoveredRecord);
        ItemStack stack = previewCache.computeIfAbsent(hoveredRecord.uuid(), ignored -> recipePreview(hoveredRecord, entry));
        KineticText.drawScrollingLeft(graphics, font, localizedResultName(hoveredRecord, entry, stack), x, y, INFO_WIDTH - 16, 0xFFFFFFFF, true);
        y += 14;
        KineticText.drawScrollingLeft(graphics, font, hoveredRecord.resultId(), x, y, INFO_WIDTH - 16, 0xFFCCCCCC, false);
        y += 13;
        graphics.drawString(font, Component.translatable(originKey(hoveredRecord)), x, y, 0xFFFFFFFF, false);
        y += 13;
        graphics.drawString(font, Component.translatable(hoveredRecord.enabled() ? "gui.taczworkshop.enabled" : "gui.taczworkshop.disabled"), x, y, 0xFFFFFFFF, false);
        y += 13;
        graphics.drawString(font, Component.translatable("gui.taczworkshop.materials", hoveredRecord.materials().size()), x, y, 0xFFCCCCCC, false);
        y += 13;
        graphics.drawString(font, Component.translatable("gui.taczworkshop.workbench.summary", hoveredRecord.workbenches().size()), x, y, 0xFFCCCCCC, false);
    }

    private String originKey(TaczRecipeRecord record) {
        if (record.hasIssue()) return "gui.taczworkshop.recipe.origin.invalid";
        if (record.isOriginal()) return record.enabled()
                ? "gui.taczworkshop.recipe.origin.original"
                : "gui.taczworkshop.recipe.origin.removed";
        return "gui.taczworkshop.recipe.origin.created";
    }

    private Component localizedResultName(TaczRecipeRecord record, TaczDataListEntry entry, ItemStack cachedStack) {
        if (entry != null && !entry.nameKey().isBlank() && KineticText.hasTranslation(entry.nameKey())) return Component.translatable(entry.nameKey());
        ItemStack stack = cachedStack == null ? recipePreview(record, entry) : cachedStack;
        if (!stack.isEmpty()) {
            Component name = entry == null ? stack.getHoverName() : TaczPreviewIndexContext.withResult(entry.kind(), entry.id(), entry.previewIndex(), stack::getHoverName);
            String text = name.getString();
            if (!text.isBlank() && !text.equals(stack.getDescriptionId()) && !text.startsWith("item.")) return name;
        }
        return Component.literal(record.resultId());
    }

    private ItemStack recipePreview(TaczRecipeRecord record, TaczDataListEntry entry) {
        if (entry == null) return TaczRecipeCodec.resultPreview(record);
        return TaczPreviewIndexContext.withResult(entry.kind(), entry.id(), entry.previewIndex(), () -> TaczRecipeCodec.resultPreview(record));
    }

    private TaczDataListEntry dataEntry(TaczRecipeRecord record) {
        TaczDataKind kind = switch (record.resultType()) {
            case "gun" -> TaczDataKind.GUN;
            case "attachment" -> TaczDataKind.ATTACHMENT;
            case "ammo" -> TaczDataKind.AMMO;
            case "melee" -> TaczDataKind.MELEE;
            case "throwable" -> TaczDataKind.THROWABLE;
            case "consumable" -> TaczDataKind.CONSUMABLE;
            default -> null;
        };
        if (kind == null) return null;
        return TaczDataClientState.snapshot(kind).stream().filter(entry -> entry.id().equals(record.resultId())).findFirst().orElse(null);
    }

    private int indexAt(double mouseX, double mouseY) {
        if (!GuiTheme.hovering(mouseX, mouseY, GRID_X, GRID_Y, GRID_WIDTH, GRID_HEIGHT)) return -1;
        int localX = (int) mouseX - GRID_X;
        int visualShift = gridScroll.visualShift(CELL_SIZE);
        int localY = (int) Math.floor(mouseY - GRID_Y + visualShift);
        int col = localX / CELL_SIZE;
        int row = localY / CELL_SIZE;
        if (col < 0 || col >= COLUMNS || row < 0 || row > ROWS_VISIBLE || localX % CELL_SIZE >= SLOT_SIZE || localY % CELL_SIZE >= SLOT_SIZE) return -1;
        int index = (gridScroll.smoothIndexOffset() + row) * COLUMNS + col;
        return index < filtered.size() ? index : -1;
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        boolean widget = super.canvasMouseClicked(mouseX, mouseY, button);
        if (KineticMouseButtons.isPrimary(button) && gridScroll.beginDrag(mouseX, mouseY, SCROLL_X, GRID_Y, SCROLL_WIDTH, GRID_HEIGHT, 18, 2)) return true;
        int index = indexAt(mouseX, mouseY);
        if (index < 0) return widget;
        TaczRecipeRecord record = filtered.get(index);
        if (KineticMouseButtons.isPrimary(button)) {
            edit(record);
            return true;
        }
        if (KineticMouseButtons.isSecondary(button)) {
            openRecipeContextMenu(record, (int) mouseX, (int) mouseY);
            return true;
        }
        return widget;
    }

    private void openRecipeContextMenu(TaczRecipeRecord record, int mouseX, int mouseY) {
        List<KineticOverlays.MenuItem> entries = new ArrayList<>();
        if (record.hasIssue()) {
            entries.add(KineticOverlays.MenuItem.action(Component.translatable("gui.taczworkshop.context.edit"), Component.translatable("tip.taczworkshop.recipe.error.edit"), () -> edit(record)));
            entries.add(KineticOverlays.MenuItem.action(Component.translatable("gui.taczworkshop.copy"), Component.translatable("tip.taczworkshop.copy"), () -> copy(record)));
        } else if (record.isOriginal() && !record.enabled()) {
            entries.add(KineticOverlays.MenuItem.action(
                    Component.translatable("gui.taczworkshop.recipe.restore_original"),
                    Component.translatable("tip.taczworkshop.recipe.restore_original_keep_created"),
                    () -> confirmRestoreOriginal(record)
            ));
        } else {
            entries.add(KineticOverlays.MenuItem.action(Component.translatable("gui.taczworkshop.context.edit"), Component.translatable("tip.taczworkshop.context.edit"), () -> edit(record)));
            entries.add(KineticOverlays.MenuItem.action(Component.translatable("gui.taczworkshop.copy"), Component.translatable("tip.taczworkshop.copy"), () -> copy(record)));
            if (record.isOriginal()) {
                entries.add(KineticOverlays.MenuItem.danger(
                        Component.translatable("gui.taczworkshop.recipe.remove_original"),
                        Component.translatable("tip.taczworkshop.recipe.remove_original"),
                        () -> stageOriginalDisabled(record, true)
                ));
            } else {
                entries.add(KineticOverlays.MenuItem.action(
                        Component.translatable(record.enabled() ? "gui.taczworkshop.context.disable" : "gui.taczworkshop.context.enable"),
                        Component.translatable(record.enabled() ? "tip.taczworkshop.recipe.disable" : "tip.taczworkshop.recipe.enable"),
                        () -> toggleEnabled(record)
                ));
                entries.add(KineticOverlays.MenuItem.separator());
                entries.add(KineticOverlays.MenuItem.danger(Component.translatable("gui.taczworkshop.remove"), Component.translatable("tip.taczworkshop.recipe.delete"), () -> confirmDelete(record)));
            }
        }
        openContextMenu(mouseX, mouseY, entries);
    }

    @Override
    protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return gridScroll.drag(mouseY, GRID_Y, GRID_HEIGHT, 18) || super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return gridScroll.release(button) || super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (GuiTheme.hovering(mouseX, mouseY, GRID_X, GRID_Y, GRID_WIDTH + 12, GRID_HEIGHT) && gridScroll.scroll(delta)) return true;
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderTooltips(GuiGraphics graphics, int scaledMouseX, int scaledMouseY, int mouseX, int mouseY) {
        if (overlayBlocksInput()) return;
        if (hoveredRecord == null) return;
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (hoveredRecord.hasIssue()) {
            lines.addAll(font.split(Component.translatable("gui.taczworkshop.recipe.error.badge"), 320));
            lines.addAll(font.split(Component.literal(hoveredRecord.id()), 320));
            lines.addAll(font.split(issueComponent(hoveredRecord), 320));
            if (!hoveredRecord.issueDetail().isBlank()) lines.addAll(font.split(Component.literal(hoveredRecord.issueDetail()), 320));
        } else {
            TaczDataListEntry entry = dataEntry(hoveredRecord);
            ItemStack stack = previewCache.computeIfAbsent(hoveredRecord.uuid(), ignored -> recipePreview(hoveredRecord, entry));
            lines.addAll(font.split(localizedResultName(hoveredRecord, entry, stack), 320));
            lines.addAll(font.split(Component.literal(hoveredRecord.resultId()), 320));
            lines.addAll(font.split(Component.literal(hoveredRecord.id()), 320));
            lines.addAll(font.split(Component.translatable(originKey(hoveredRecord)), 320));
            lines.addAll(font.split(Component.translatable(hoveredRecord.enabled() ? "gui.taczworkshop.enabled" : "gui.taczworkshop.disabled"), 320));
            if (!hoveredRecord.workbenches().isEmpty()) lines.addAll(font.split(Component.translatable("gui.taczworkshop.workbench.tooltip", hoveredRecord.workbenches().size()), 320));
        }
        lines.addAll(font.split(Component.translatable("gui.taczworkshop.recipe.grid.tooltip"), 320));
        showFormattedTooltip(lines);
    }



    private Component issueComponent(TaczRecipeRecord record) {
        if (record == null || !record.hasIssue()) return Component.empty();
        String code = record.issueCode().isBlank() ? "tacz_deserialize_failed" : record.issueCode();
        return Component.translatable("gui.taczworkshop.recipe.error." + code);
    }

    @Override
    protected boolean handleCloseRequest() {
        discardDraft();
        return false;
    }
}
