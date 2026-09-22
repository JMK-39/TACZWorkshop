package dev.xyat.taczworkshop.client.gui;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import dev.xyat.kineticcore.api.client.text.KineticText;
import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.scroll.KineticScroll.GridScrollController;
import dev.xyat.taczworkshop.client.TaczDataClientState;
import dev.xyat.taczworkshop.client.TaczDataListEntry;
import dev.xyat.taczworkshop.client.TaczDataStackUtil;
import dev.xyat.taczworkshop.client.TaczPreviewIndexContext;
import dev.xyat.taczworkshop.data.TaczDataKind;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TaczDataManagerScreen extends KineticScreen {
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
    private final GridScrollController gridScroll = new GridScrollController();
    private TaczDataKind kind = TaczDataKind.GUN;
    private StatusFilter statusFilter = StatusFilter.ACTIVE;
    private KineticEditBox search;
    private HighZButton categoryButton;
    private StateButton saveAllButton;
    private StateButton activeFilterButton;
    private StateButton modifiedFilterButton;
    private StateButton removedFilterButton;
    private TaczDataListEntry hoveredEntry;

    public TaczDataManagerScreen(Screen parent) {
        super(Component.translatable("gui.taczworkshop.data.title"));
        setParentScreen(parent);
        configureStandaloneDraft(TaczDataClientState::capturePendingSnapshot, TaczDataClientState::restorePendingSnapshot);
    }

    @Override
    protected void buildUi() {
        closeContextMenu();
        String oldSearch = search == null ? "" : search.getValue();
        search = addTextField(SEARCH_X, SEARCH_Y, 180, Component.translatable("gui.taczworkshop.search"), Component.translatable("gui.taczworkshop.data.search.hint"), null, null);
        search.setMaxLength(256);
        search.setValue(oldSearch);
        search.setResponder(value -> {
            gridScroll.reset();
            rebuildFiltered();
        });

        categoryButton = addHighZButton(
                198, 10, 180,
                Component.translatable("gui.taczworkshop.data.category", Component.translatable(tabKey(kind))),
                Component.translatable("tip.taczworkshop.data.category"),
                40,
                this::openKindMenu
        );

        activeFilterButton = addButton(14, 34, 80, Component.translatable(StatusFilter.ACTIVE.key), Component.translatable("tip.taczworkshop.data.filter.active"), () -> setStatusFilter(StatusFilter.ACTIVE));
        activeFilterButton.setEnabled(statusFilter != StatusFilter.ACTIVE);
        modifiedFilterButton = addButton(96, 34, 80, Component.translatable(StatusFilter.MODIFIED.key), Component.translatable("tip.taczworkshop.data.filter.modified"), () -> setStatusFilter(StatusFilter.MODIFIED));
        modifiedFilterButton.setEnabled(statusFilter != StatusFilter.MODIFIED);
        removedFilterButton = addButton(178, 34, 80, Component.translatable(StatusFilter.REMOVED.key), Component.translatable("tip.taczworkshop.data.filter.removed"), () -> setStatusFilter(StatusFilter.REMOVED));
        removedFilterButton.setEnabled(statusFilter != StatusFilter.REMOVED);
        saveAllButton = addButton(
                340, 34, 92,
                Component.translatable("gui.taczworkshop.data.save_all", TaczDataClientState.pendingCount()),
                Component.translatable("tip.taczworkshop.data.save_all"),
                this::saveAll
        );
        saveAllButton.setEnabled(TaczDataClientState.hasPending());
        addButton(436, 34, 90, Component.translatable("gui.taczworkshop.data.refresh"), Component.translatable("tip.taczworkshop.data.refresh"), TaczRecipeNetwork::requestDataList);
        addButton(530, 34, 96, Component.translatable("gui.taczworkshop.back"), Component.translatable("tip.taczworkshop.back.management"), this::onClose);

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
                    Component.translatable(tabKey(option)),
                    Component.translatable("tip.taczworkshop.data.category.entry", Component.translatable(tabKey(option))),
                    option == kind,
                    () -> setKind(option)
            ));
        }
        int menuX = categoryButton == null ? 198 : categoryButton.getX();
        int menuY = categoryButton == null ? 32 : categoryButton.getY() + categoryButton.getHeight() + 2;
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
            categoryButton.setText(Component.translatable("gui.taczworkshop.data.category", Component.translatable(tabKey(kind))));
        }
        if (activeFilterButton != null) activeFilterButton.setEnabled(statusFilter != StatusFilter.ACTIVE);
        if (modifiedFilterButton != null) modifiedFilterButton.setEnabled(statusFilter != StatusFilter.MODIFIED);
        if (removedFilterButton != null) removedFilterButton.setEnabled(statusFilter != StatusFilter.REMOVED);
        if (saveAllButton != null) {
            saveAllButton.setText(Component.translatable("gui.taczworkshop.data.save_all", TaczDataClientState.pendingCount()));
            saveAllButton.setEnabled(TaczDataClientState.hasPending());
        }
    }

    private void rebuildFiltered() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
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
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.panel(graphics, 6, 6, 628, 348);
        GuiTheme.panelAlt(graphics, 10, 56, 620, 293);
        GuiTheme.panelAlt(graphics, INFO_X, INFO_Y, INFO_WIDTH, INFO_HEIGHT);
        renderEntries(graphics, mouseX, mouseY);
        GuiTheme.scrollbar(gridScroll, graphics, mouseX, mouseY, SCROLL_X, GRID_Y, SCROLL_WIDTH, GRID_HEIGHT, 18);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(font, Component.translatable("gui.taczworkshop.data.count", filtered.size()), 386, 16, 0xFFFFFFFF, true);
        renderInfoPanel(graphics);
        if (filtered.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.taczworkshop.data.empty"), GRID_X + GRID_WIDTH / 2, GRID_Y + GRID_HEIGHT / 2 - font.lineHeight / 2, 0xFFAAAAAA);
        }
    }

    private void renderInfoPanel(GuiGraphics graphics) {
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
        graphics.drawString(font, Component.translatable("gui.taczworkshop.data.overview"), x, y, 0xFFFFFFFF, true);
        y += 18;
        graphics.drawString(font, Component.translatable("gui.taczworkshop.data.overview.active", activeCount), x, y, 0xFFCCCCCC, false);
        y += 13;
        graphics.drawString(font, Component.translatable("gui.taczworkshop.data.overview.modified", modifiedCount), x, y, 0xFFCCCCCC, false);
        y += 13;
        graphics.drawString(font, Component.translatable("gui.taczworkshop.data.overview.removed", removedCount), x, y, 0xFFCCCCCC, false);
        y += 22;

        if (hoveredEntry != null) {
            ItemStack stack = stackFor(hoveredEntry);
            Component name = hoverName(hoveredEntry, stack);
            KineticText.drawScrollingLeft(graphics, font, name, x, y, INFO_WIDTH - 16, 0xFFFFFFFF, true);
            y += 14;
            KineticText.drawScrollingLeft(graphics, font, hoveredEntry.id(), x, y, INFO_WIDTH - 16, 0xFFCCCCCC, false);
            y += 13;
            if (!hoveredEntry.dataId().isBlank()) {
                KineticText.drawScrollingLeft(graphics, font, hoveredEntry.dataId(), x, y, INFO_WIDTH - 16, 0xFFAAAAAA, false);
                y += 13;
            }
            graphics.drawString(font, statusLine(hoveredEntry), x, y, 0xFFFFFFFF, false);
        } else {
            for (FormattedCharSequence line : font.split(Component.translatable("gui.taczworkshop.data.overview.guide"), INFO_WIDTH - 16)) {
                graphics.drawString(font, line, x, y, 0xFFCCCCCC, false);
                y += font.lineHeight + 2;
            }
        }
    }

    private void renderEntries(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredEntry = null;
        int hoveredIndex = indexAt(mouseX, mouseY);
        int baseRow = gridScroll.smoothIndexOffset();
        int visualShift = gridScroll.visualShift(CELL_SIZE);
        int first = baseRow * COLUMNS;
        int last = Math.min(filtered.size(), first + (ROWS_VISIBLE + 2) * COLUMNS);
        enableUiScissor(graphics, GRID_X, GRID_Y, GRID_X + GRID_WIDTH, GRID_Y + GRID_HEIGHT);
        for (int index = first; index < last; index++) {
            int visible = index - first;
            int col = visible % COLUMNS;
            int row = visible / COLUMNS;
            int x = GRID_X + col * CELL_SIZE;
            int y = GRID_Y + row * CELL_SIZE - visualShift;
            TaczDataListEntry entry = filtered.get(index);
            boolean hovered = index == hoveredIndex && !overlayBlocksInput();

            GuiTheme.itemSlot(graphics, x, y, hovered);
            ItemStack stack = stackFor(entry);
            if (!stack.isEmpty()) {
                TaczPreviewIndexContext.with(entry, () -> GuiTheme.item(graphics, font, stack, x, y, SLOT_SIZE, 1.0F, false));
            } else {
                graphics.drawCenteredString(font, Component.translatable("gui.taczworkshop.data.missing_mark"), x + 9, y + 5, 0xFFFFCC55);
            }

            if (entry.removed()) GuiTheme.indicatorOutline(graphics, x, y, SLOT_SIZE, SLOT_SIZE, GuiTheme.Indicator.DANGER);
            else if (entry.modified()) GuiTheme.indicatorOutline(graphics, x, y, SLOT_SIZE, SLOT_SIZE, GuiTheme.Indicator.SUCCESS);
            if (hovered) hoveredEntry = entry;
        }
        disableUiScissor(graphics);
    }

    private ItemStack stackFor(TaczDataListEntry entry) {
        return stackCache.computeIfAbsent(entry.id(), ignored -> TaczDataStackUtil.build(entry.kind(), entry.id(), entry.previewIndex()));
    }

    private Component hoverName(TaczDataListEntry entry, ItemStack stack) {
        if (!entry.nameKey().isBlank() && KineticText.hasTranslation(entry.nameKey())) {
            return Component.translatable(entry.nameKey());
        }
        if (!stack.isEmpty()) {
            Component stackName = TaczPreviewIndexContext.withResult(entry.kind(), entry.id(), entry.previewIndex(), stack::getHoverName);
            String text = stackName.getString();
            if (!text.isBlank() && !text.equals(stack.getDescriptionId()) && !text.startsWith("item.")) return stackName;
        }
        if (!entry.id().isBlank()) return Component.literal(entry.id());
        return Component.translatable("gui.taczworkshop.data.resource_missing");
    }

    private String localizedNameText(TaczDataListEntry entry) {
        if (entry == null) return "";
        if (!entry.nameKey().isBlank() && KineticText.hasTranslation(entry.nameKey())) return Component.translatable(entry.nameKey()).getString();
        return entry.id();
    }

    private int indexAt(double mouseX, double mouseY) {
        if (!GuiTheme.hovering(mouseX, mouseY, GRID_X, GRID_Y, GRID_WIDTH, GRID_HEIGHT)) return -1;
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
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        boolean widget = super.canvasMouseClicked(mouseX, mouseY, button);
        if (KineticMouseButtons.isPrimary(button) && gridScroll.beginDrag(mouseX, mouseY, SCROLL_X, GRID_Y, SCROLL_WIDTH, GRID_HEIGHT, 18, 2)) return true;

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
                Component.translatable("gui.taczworkshop.context.edit"),
                Component.translatable("tip.taczworkshop.context.edit"),
                () -> TaczRecipeNetwork.requestDataDetail(entry.kind(), entry.id())
        ));
        items.add(KineticOverlays.MenuItem.action(
                Component.translatable(entry.removed() ? "gui.taczworkshop.context.enable" : "gui.taczworkshop.context.disable"),
                Component.translatable(entry.removed() ? "tip.taczworkshop.context.enable" : "tip.taczworkshop.context.disable"),
                () -> stageRemovedEntry(entry, !entry.removed())
        ));
        if (entry.modified()) {
            items.add(KineticOverlays.MenuItem.separator());
            items.add(KineticOverlays.MenuItem.danger(
                    Component.translatable("gui.taczworkshop.context.reset"),
                    Component.translatable("tip.taczworkshop.context.reset"),
                    () -> confirmResetEntry(entry)
            ));
        }
        openContextMenu(mouseX, mouseY, items);
    }

    private void confirmResetEntry(TaczDataListEntry entry) {
        if (entry == null) return;
        openDialog(
                Component.translatable("gui.taczworkshop.data.reset.title"),
                Component.translatable("gui.taczworkshop.data.reset.message", entry.id()),
                Component.translatable("gui.yes"),
                Component.translatable("gui.no"),
                () -> {
                    TaczDataClientState.stageReset(entry.kind(), entry.id());
                    rebuildHeaderWidgets();
                },
                () -> { }
        );
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
        if (hoveredEntry == null) return;
        ItemStack stack = stackFor(hoveredEntry);
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.addAll(font.split(hoverName(hoveredEntry, stack), 320));
        lines.addAll(font.split(Component.translatable("gui.taczworkshop.data.tooltip.item_id", hoveredEntry.id()), 320));
        if (!hoveredEntry.dataId().isBlank()) lines.addAll(font.split(Component.translatable("gui.taczworkshop.data.tooltip.data_id", hoveredEntry.dataId()), 320));
        lines.addAll(font.split(statusLine(hoveredEntry), 320));
        lines.addAll(font.split(Component.translatable("tip.taczworkshop.data.open_entry"), 320));
        lines.addAll(font.split(Component.translatable("tip.taczworkshop.data.context_entry"), 320));
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
        if (entry.removed()) return Component.translatable("gui.taczworkshop.data.tooltip.status.removed");
        if (entry.modified()) return Component.translatable("gui.taczworkshop.data.tooltip.status.modified");
        return Component.translatable("gui.taczworkshop.data.tooltip.status.active");
    }


    private static String tabKey(TaczDataKind kind) {
        return "gui.taczworkshop.data.tab." + kind.wireName();
    }

}
