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
import dev.xyat.kineticcore.api.client.gui.widget.*;
import dev.xyat.kineticcore.api.client.gui.widget.list.*;
import dev.xyat.kineticcore.api.text.KineticI18n;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.taczworkshop.client.TaczDataClientState;
import dev.xyat.taczworkshop.client.TaczDataListEntry;
import dev.xyat.taczworkshop.client.TaczDataStackUtil;
import dev.xyat.taczworkshop.client.TaczPreviewIndexContext;
import dev.xyat.taczworkshop.data.TaczDataKind;
import dev.xyat.taczworkshop.network.TaczRecipeNetwork;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TaczDataManagerPage extends KineticPage {
    private static final int SEARCH_X = 14;
    private static final int SEARCH_Y = 10;
    private static final int SEARCH_W = 224;
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
        ACTIVE("gui.taczworkshop.data.filter.active"),
        MODIFIED("gui.taczworkshop.data.filter.modified"),
        REMOVED("gui.taczworkshop.data.filter.removed");

        private final String key;

        StatusFilter(String key) {
            this.key = key;
        }
    }

    private final List<TaczDataListEntry> source = new ArrayList<>();
    private final List<TaczDataListEntry> filtered = new ArrayList<>();
    private final Map<String, ItemStack> stackCache = new HashMap<>();
    private final KineticScrollController gridScroll = new KineticScrollController();
    private TaczDataKind kind = TaczDataKind.GUN;
    private StatusFilter statusFilter = StatusFilter.ACTIVE;
    private KineticTextField search;
    private KineticButton categoryButton;
    private KineticButton saveAllButton;
    private KineticButton activeFilterButton;
    private KineticButton modifiedFilterButton;
    private KineticButton removedFilterButton;
    private TaczDataListEntry hoveredEntry;

    public TaczDataManagerPage() {
        super(KineticI18n.translatable("gui.taczworkshop.data.title"));
        configureStandaloneDraft(TaczDataClientState::capturePendingSnapshot, TaczDataClientState::restorePendingSnapshot);
    }

    @Override
    protected void build(KineticUi ui) {
        closeContextMenu();
        String oldSearch = search == null ? "" : search.textValue();
        search = ui().textField(SEARCH_X, SEARCH_Y, 180).label(KineticI18n.translatable("gui.taczworkshop.search")).placeholder(KineticI18n.translatable("gui.taczworkshop.data.search.hint")).firstShownTextAsDefault().build();
        search.limitTextLength(256);
        search.setTextValue(oldSearch);
        search.onTextChange(value -> {
            gridScroll.reset();
            rebuildFiltered();
        });

        categoryButton = ui().button(198, 10, 180).text(KineticI18n.translatable("gui.taczworkshop.data.category", KineticI18n.translatable(tabKey(kind)))).tooltip(KineticI18n.translatable("tip.taczworkshop.data.category")).layer(1).onClick(this::openKindMenu).build();

        activeFilterButton = ui().button(14, 34, 80).text(KineticI18n.translatable(StatusFilter.ACTIVE.key)).tooltip(KineticI18n.translatable("tip.taczworkshop.data.filter.active")).onClick(() -> setStatusFilter(StatusFilter.ACTIVE)).build();
        activeFilterButton.setEnabled(statusFilter != StatusFilter.ACTIVE);
        modifiedFilterButton = ui().button(96, 34, 80).text(KineticI18n.translatable(StatusFilter.MODIFIED.key)).tooltip(KineticI18n.translatable("tip.taczworkshop.data.filter.modified")).onClick(() -> setStatusFilter(StatusFilter.MODIFIED)).build();
        modifiedFilterButton.setEnabled(statusFilter != StatusFilter.MODIFIED);
        removedFilterButton = ui().button(178, 34, 80).text(KineticI18n.translatable(StatusFilter.REMOVED.key)).tooltip(KineticI18n.translatable("tip.taczworkshop.data.filter.removed")).onClick(() -> setStatusFilter(StatusFilter.REMOVED)).build();
        removedFilterButton.setEnabled(statusFilter != StatusFilter.REMOVED);
        saveAllButton = ui().button(340, 34, 92).text(KineticI18n.translatable("gui.taczworkshop.data.save_all", TaczDataClientState.pendingCount())).tooltip(KineticI18n.translatable("tip.taczworkshop.data.save_all")).onClick(this::saveAll).build();
        saveAllButton.setEnabled(TaczDataClientState.hasPending());
        ui().button(436, 34, 90).text(KineticI18n.translatable("gui.taczworkshop.data.refresh")).tooltip(KineticI18n.translatable("tip.taczworkshop.data.refresh")).onClick(TaczRecipeNetwork::requestDataList).build();
        ui().button(530, 34, 96).text(KineticI18n.translatable("gui.taczworkshop.back")).tooltip(KineticI18n.translatable("tip.taczworkshop.back.management")).onClick(this::close).build();

        refreshFromState();
    }

    public void refreshFromState() {
        source.clear();
        source.addAll(TaczDataClientState.snapshot(kind));
        stackCache.clear();
        rebuildFiltered();
        updateHeaderControls();
    }


    private void openKindMenu() {
        List<KineticOverlays.MenuItem> items = new ArrayList<>();
        for (TaczDataKind option : TaczDataKind.values()) {
            items.add(KineticOverlays.MenuItem.toggle(
                    KineticI18n.translatable(tabKey(option)),
                    KineticI18n.translatable("tip.taczworkshop.data.category.entry", KineticI18n.translatable(tabKey(option))),
                    option == kind,
                    () -> setKind(option)
            ));
        }
        int menuX = categoryButton == null ? 198 : categoryButton.controlX();
        int menuY = categoryButton == null ? 32 : categoryButton.controlY() + categoryButton.controlHeight() + 2;
        openContextMenu(menuX, menuY, items);
    }

    private void setKind(TaczDataKind next) {
        if (next == null) return;
        closeContextMenu();
        if (next == kind) {
            updateHeaderControls();
            return;
        }
        kind = next;
        statusFilter = StatusFilter.ACTIVE;
        gridScroll.reset();
        refreshFromState();
        updateHeaderControls();
    }

    private void setStatusFilter(StatusFilter next) {
        if (next == null || next == statusFilter) return;
        statusFilter = next;
        gridScroll.reset();
        closeContextMenu();
        rebuildFiltered();
        updateHeaderControls();
    }

    private void rebuildHeaderWidgets() {
        refreshFromState();
        updateHeaderControls();
    }

    private void updateHeaderControls() {
        if (categoryButton != null) {
            categoryButton.setText(KineticI18n.translatable("gui.taczworkshop.data.category", KineticI18n.translatable(tabKey(kind))));
        }
        if (activeFilterButton != null) activeFilterButton.setEnabled(statusFilter != StatusFilter.ACTIVE);
        if (modifiedFilterButton != null) modifiedFilterButton.setEnabled(statusFilter != StatusFilter.MODIFIED);
        if (removedFilterButton != null) removedFilterButton.setEnabled(statusFilter != StatusFilter.REMOVED);
        if (saveAllButton != null) {
            saveAllButton.setText(KineticI18n.translatable("gui.taczworkshop.data.save_all", TaczDataClientState.pendingCount()));
            saveAllButton.setEnabled(TaczDataClientState.hasPending());
        }
    }

    private void rebuildFiltered() {
        String query = search == null ? "" : search.textValue().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (TaczDataListEntry entry : source) {
            if (failsStatusFilter(entry)) continue;
            String translated = localizedNameText(entry);
            String searchable = (entry.id() + " " + entry.dataId() + " " + translated + " " + entry.type()).toLowerCase(Locale.ROOT);
            if (query.isEmpty() || searchable.contains(query)) filtered.add(entry);
        }
        filtered.sort(Comparator
                .comparing(TaczDataListEntry::modified).reversed()
                .thenComparing(TaczDataListEntry::id));
        int totalRows = (filtered.size() + COLUMNS - 1) / COLUMNS;
        gridScroll.update(totalRows, ROWS_VISIBLE);
    }

    private boolean failsStatusFilter(TaczDataListEntry entry) {
        return switch (statusFilter) {
            case ACTIVE -> entry.removed();
            case MODIFIED -> (!entry.modified()) || (entry.removed());
            case REMOVED -> !entry.removed();
        };
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panel(graphics, 6, 6, 628, 348);
        KineticTheme.panelAlt(graphics, 10, 56, 620, 293);
        KineticTheme.panelAlt(graphics, INFO_X, INFO_Y, INFO_WIDTH, INFO_HEIGHT);
        renderEntries(graphics, mouseX, mouseY);
        gridScroll.render(graphics, mouseX, mouseY, SCROLL_X, GRID_Y, SCROLL_WIDTH, GRID_HEIGHT, 18);
    }

    @Override
    protected void renderForeground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.scrollingText(KineticI18n.translatable("gui.taczworkshop.data.count", filtered.size()), 386, 16, 626 - 386, 0xFFFFFFFF, true);
        renderInfoPanel(graphics);
        if (filtered.isEmpty()) {
            graphics.scrollingTextCentered(KineticI18n.translatable("gui.taczworkshop.data.empty"), GRID_X + GRID_WIDTH / 2, GRID_Y + GRID_HEIGHT / 2 - KineticText.lineHeight() / 2, GRID_WIDTH - 8, 0xFFAAAAAA, true);
        }
    }

    private void renderInfoPanel(KineticGraphics graphics) {
        int activeCount = 0;
        int modifiedCount = 0;
        int removedCount = 0;
        for (TaczDataListEntry entry : source) {
            if (entry.removed()) removedCount++;
            else activeCount++;
            if (entry.modified() && !entry.removed()) modifiedCount++;
        }

        int x = INFO_X + 8;
        int y = INFO_Y + 8;
        graphics.scrollingText(KineticI18n.translatable("gui.taczworkshop.data.overview"), x, y, INFO_WIDTH - 16, 0xFFFFFFFF, true);
        y += 18;
        graphics.scrollingText(KineticI18n.translatable("gui.taczworkshop.data.overview.active", activeCount), x, y, INFO_WIDTH - 16, 0xFFCCCCCC, false);
        y += 13;
        graphics.scrollingText(KineticI18n.translatable("gui.taczworkshop.data.overview.modified", modifiedCount), x, y, INFO_WIDTH - 16, 0xFFCCCCCC, false);
        y += 13;
        graphics.scrollingText(KineticI18n.translatable("gui.taczworkshop.data.overview.removed", removedCount), x, y, INFO_WIDTH - 16, 0xFFCCCCCC, false);
        y += 22;

        if (hoveredEntry != null) {
            ItemStack stack = stackFor(hoveredEntry);
            Component name = hoverName(hoveredEntry, stack);
            graphics.scrollingText(name, x, y, INFO_WIDTH - 16, 0xFFFFFFFF, true);
            y += 14;
            graphics.scrollingText(Component.literal(hoveredEntry.id()), x, y, INFO_WIDTH - 16, 0xFFCCCCCC, false);
            y += 13;
            if (!hoveredEntry.dataId().isBlank()) {
                graphics.scrollingText(Component.literal(hoveredEntry.dataId()), x, y, INFO_WIDTH - 16, 0xFFAAAAAA, false);
                y += 13;
            }
            graphics.scrollingText(statusLine(hoveredEntry), x, y, INFO_WIDTH - 16, 0xFFFFFFFF, false);
        } else {
            for (FormattedCharSequence line : KineticText.wrap(KineticI18n.translatable("gui.taczworkshop.data.overview.guide"), INFO_WIDTH - 16)) {
                graphics.text(line, x, y, 0xFFCCCCCC, false);
                y += KineticText.lineHeight() + 2;
            }
        }
    }

    private void renderEntries(KineticGraphics graphics, int mouseX, int mouseY) {
        hoveredEntry = null;
        int hoveredIndex = indexAt(mouseX, mouseY);
        int baseRow = gridScroll.smoothIndexOffset();
        int visualShift = gridScroll.visualShift(CELL_SIZE);
        int first = baseRow * COLUMNS;
        int last = Math.min(filtered.size(), first + (ROWS_VISIBLE + 2) * COLUMNS);
        graphics.scissor(GRID_X, GRID_Y, GRID_X + GRID_WIDTH, GRID_Y + GRID_HEIGHT);
        for (int index = first; index < last; index++) {
            int visible = index - first;
            int col = visible % COLUMNS;
            int row = visible / COLUMNS;
            int x = GRID_X + col * CELL_SIZE;
            int y = GRID_Y + row * CELL_SIZE - visualShift;
            TaczDataListEntry entry = filtered.get(index);
            boolean hovered = index == hoveredIndex && !overlayBlocksInput();

            KineticTheme.itemSlot(graphics, x, y, hovered);
            ItemStack stack = stackFor(entry);
            if (!stack.isEmpty()) {
                TaczPreviewIndexContext.with(entry, () -> KineticTheme.item(graphics, stack, x, y, SLOT_SIZE, 1.0F, false));
            } else {
                graphics.centeredText(KineticI18n.translatable("gui.taczworkshop.data.missing_mark"), x + 9, y + 5, 0xFFFFCC55, true);
            }

            if (entry.removed()) KineticTheme.indicatorOutline(graphics, x, y, SLOT_SIZE, SLOT_SIZE, KineticTheme.Indicator.DANGER);
            else if (entry.modified()) KineticTheme.indicatorOutline(graphics, x, y, SLOT_SIZE, SLOT_SIZE, KineticTheme.Indicator.SUCCESS);
            if (hovered) hoveredEntry = entry;
        }
        graphics.endScissor();
    }

    private ItemStack stackFor(TaczDataListEntry entry) {
        return stackCache.computeIfAbsent(entry.id(), ignored -> TaczDataStackUtil.build(entry.kind(), entry.id(), entry.previewIndex()));
    }

    private Component hoverName(TaczDataListEntry entry, ItemStack stack) {
        if (!entry.nameKey().isBlank() && KineticI18n.hasTranslation(entry.nameKey())) {
            return KineticI18n.translatable(entry.nameKey());
        }
        if (!stack.isEmpty()) {
            Component stackName = TaczPreviewIndexContext.withResult(entry.kind(), entry.id(), entry.previewIndex(), stack::getHoverName);
            String text = stackName.getString();
            if (!text.isBlank() && !text.equals(stack.getDescriptionId()) && !text.startsWith("item.")) return stackName;
        }
        if (!entry.id().isBlank()) return Component.literal(entry.id());
        return KineticI18n.translatable("gui.taczworkshop.data.resource_missing");
    }

    private String localizedNameText(TaczDataListEntry entry) {
        if (entry == null) return "";
        if (!entry.nameKey().isBlank() && KineticI18n.hasTranslation(entry.nameKey())) return KineticI18n.translatable(entry.nameKey()).getString();
        return entry.id();
    }

    private int indexAt(double mouseX, double mouseY) {
        if (!KineticTheme.hovering(mouseX, mouseY, GRID_X, GRID_Y, GRID_WIDTH, GRID_HEIGHT)) return -1;
        int localX = (int) (mouseX - GRID_X);
        int visualShift = gridScroll.visualShift(CELL_SIZE);
        int localY = (int) Math.floor(mouseY - GRID_Y + visualShift);
        int col = localX / CELL_SIZE;
        int row = localY / CELL_SIZE;
        if (col < 0 || col >= COLUMNS || row < 0 || row > ROWS_VISIBLE) return -1;
        if (localX % CELL_SIZE == SLOT_SIZE || localY % CELL_SIZE == SLOT_SIZE) return -1;
        int index = (gridScroll.smoothIndexOffset() + row) * COLUMNS + col;
        return index >= 0 && index < filtered.size() ? index : -1;
    }

    @Override
    protected boolean onMouseClick(MouseInput input) {
        double mouseX = input.x(), mouseY = input.y(); int button = input.rawButton();
        boolean widget = false;
        if (gridScroll.beginDrag(mouseX, mouseY, input.button(), SCROLL_X, GRID_Y, SCROLL_WIDTH, GRID_HEIGHT, 18, 2)) return true;

        int index = indexAt(mouseX, mouseY);
        if (index < 0) return widget;
        TaczDataListEntry entry = filtered.get(index);
        if (KineticMouseButtons.isPrimary(button)) {
            TaczRecipeNetwork.requestDataDetail(entry.kind(), entry.id());
            return true;
        }
        if (KineticMouseButtons.isSecondary(button)) {
            openEntryContextMenu(entry, (int) mouseX, (int) mouseY);
            return true;
        }
        return widget;
    }

    private void openEntryContextMenu(TaczDataListEntry entry, int mouseX, int mouseY) {
        List<KineticOverlays.MenuItem> items = new ArrayList<>();
        items.add(KineticOverlays.MenuItem.action(
                KineticI18n.translatable("gui.taczworkshop.context.edit"),
                KineticI18n.translatable("tip.taczworkshop.context.edit"),
                () -> TaczRecipeNetwork.requestDataDetail(entry.kind(), entry.id())
        ));
        items.add(KineticOverlays.MenuItem.action(
                KineticI18n.translatable(entry.removed() ? "gui.taczworkshop.context.enable" : "gui.taczworkshop.context.disable"),
                KineticI18n.translatable(entry.removed() ? "tip.taczworkshop.context.enable" : "tip.taczworkshop.context.disable"),
                () -> stageRemovedEntry(entry, !entry.removed())
        ));
        if (entry.modified()) {
            items.add(KineticOverlays.MenuItem.separator());
            items.add(KineticOverlays.MenuItem.danger(
                    KineticI18n.translatable("gui.taczworkshop.context.reset"),
                    KineticI18n.translatable("tip.taczworkshop.context.reset"),
                    () -> confirmResetEntry(entry)
            ));
        }
        openContextMenu(mouseX, mouseY, items);
    }

    private void confirmResetEntry(TaczDataListEntry entry) {
        if (entry == null) return;
        openDialog(
                KineticI18n.translatable("gui.taczworkshop.data.reset.title"),
                KineticI18n.translatable("gui.taczworkshop.data.reset.message", entry.id()),
                KineticI18n.translatable("gui.yes"),
                KineticI18n.translatable("gui.no"),
                () -> {
                    TaczDataClientState.stageReset(entry.kind(), entry.id());
                    rebuildHeaderWidgets();
                },
                () -> { }
        );
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        double mouseX = input.x(), mouseY = input.y(); int button = input.rawButton(); double dragX = input.deltaX(), dragY = input.deltaY();
        return gridScroll.drag(mouseY, GRID_Y, GRID_HEIGHT, 18) || false;
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        double mouseX = input.x(), mouseY = input.y(); int button = input.rawButton();
        return gridScroll.release(input.button()) || false;
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        double mouseX = input.x(), mouseY = input.y(), delta = input.deltaY();
        if (KineticTheme.hovering(mouseX, mouseY, GRID_X, GRID_Y, GRID_WIDTH + 12, GRID_HEIGHT) && gridScroll.scroll(delta)) return true;
        return false;
    }

    @Override
    protected void renderTooltips(int scaledMouseX, int scaledMouseY) {
        if (overlayBlocksInput()) return;
        if (hoveredEntry == null) return;
        ItemStack stack = stackFor(hoveredEntry);
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.addAll(KineticText.wrap(hoverName(hoveredEntry, stack), 320));
        lines.addAll(KineticText.wrap(KineticI18n.translatable("gui.taczworkshop.data.tooltip.item_id", hoveredEntry.id()), 320));
        if (!hoveredEntry.dataId().isBlank()) lines.addAll(KineticText.wrap(KineticI18n.translatable("gui.taczworkshop.data.tooltip.data_id", hoveredEntry.dataId()), 320));
        lines.addAll(KineticText.wrap(statusLine(hoveredEntry), 320));
        lines.addAll(KineticText.wrap(KineticI18n.translatable("tip.taczworkshop.data.open_entry"), 320));
        lines.addAll(KineticText.wrap(KineticI18n.translatable("tip.taczworkshop.data.context_entry"), 320));
        showFormattedTooltip(lines);
    }

    private void stageRemovedEntry(TaczDataListEntry entry, boolean removed) {
        TaczDataClientState.stageRemoved(entry.kind(), entry.id(), removed);
        rebuildHeaderWidgets();
    }

    private void saveAll() {
        if (!TaczDataClientState.hasPending()) return;
        TaczRecipeNetwork.saveDataBatch(TaczDataClientState.pendingPayload());
    }

    public void handleDataBatchCommit(long batchId) {
        commitDraft();
        refreshFromState();
    }

    private Component statusLine(TaczDataListEntry entry) {
        if (entry.removed()) return KineticI18n.translatable("gui.taczworkshop.data.tooltip.status.removed");
        if (entry.modified()) return KineticI18n.translatable("gui.taczworkshop.data.tooltip.status.modified");
        return KineticI18n.translatable("gui.taczworkshop.data.tooltip.status.active");
    }


    private static String tabKey(TaczDataKind kind) {
        return "gui.taczworkshop.data.tab." + kind.wireName();
    }

}
