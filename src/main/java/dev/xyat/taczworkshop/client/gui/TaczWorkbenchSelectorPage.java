package dev.xyat.taczworkshop.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.input.ScrollInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseDragInput;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.*;
import dev.xyat.kineticcore.api.client.gui.widget.list.*;

import dev.xyat.kineticcore.api.client.input.KineticMouseButtons;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.resource.index.CommonBlockIndex;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class TaczWorkbenchSelectorPage extends KineticPage {
    private static final int GRID_X = 14;
    private static final int GRID_Y = 54;
    private static final int SLOT_SIZE = 18;
    private static final int CELL_SIZE = 19;
    private static final int COLUMNS = 25;
    private static final int ROWS = 14;
    private static final int GRID_W = COLUMNS * CELL_SIZE;
    private static final int GRID_H = ROWS * CELL_SIZE;
    private static final int SCROLL_X = GRID_X + GRID_W + 5;
    private static final int SCROLL_W = 4;
    private static final int INFO_X = SCROLL_X + SCROLL_W + 7;
    private static final int INFO_W = 116;

    private record Entry(ResourceLocation id, ItemStack stack, String name) {
    }

    private final Consumer<List<String>> onSave;
    private final List<String> originalSelection;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<Entry> source = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private final KineticScrollController scroll = new KineticScrollController();
    private KineticTextField search;
    private Entry hovered;

    public TaczWorkbenchSelectorPage(List<String> selected, Consumer<List<String>> onSave) {
        super(KineticI18n.translatable("gui.taczworkshop.workbench.title"));
        this.onSave = onSave;
        this.originalSelection = selected == null ? List.of() : List.copyOf(selected);
        if (selected != null) this.selected.addAll(selected);
        loadEntries();
    }

    private void loadEntries() {
        source.clear();
        for (Map.Entry<ResourceLocation, CommonBlockIndex> raw : TimelessAPI.getAllCommonBlockIndex()) {
            ResourceLocation id = raw.getKey();
            CommonBlockIndex index = raw.getValue();
            if (id == null || index == null) continue;
            ItemStack stack;
            try {
                stack = BlockItemBuilder.create(index.getBlock()).setId(id).build();
            } catch (Exception ignored) {
                stack = ItemStack.EMPTY;
            }
            String name = stack.isEmpty() ? id.toString() : stack.getHoverName().getString();
            if (name.isBlank()) name = id.toString();
            source.add(new Entry(id, stack, name));
        }
        source.sort(Comparator.comparing((Entry entry) -> entry.name().toLowerCase(Locale.ROOT)));
    }

    @Override
    protected void build(KineticUi ui) {
        String oldSearch = search == null ? "" : search.textValue();
        search = ui().textField(14, 12, 224).label(KineticI18n.translatable("gui.taczworkshop.search")).placeholder(KineticI18n.translatable("gui.taczworkshop.workbench.search.hint")).firstShownTextAsDefault().build();
        search.limitTextLength(256);
        search.setTextValue(oldSearch);
        search.onTextChange(value -> {
            scroll.reset();
            rebuildFiltered();
        });

        ui().button(244, 12, 118).text(KineticI18n.translatable("gui.taczworkshop.workbench.restore_original")).tooltip(KineticI18n.translatable("tip.taczworkshop.workbench.restore_original")).onClick(this::restoreOriginal).build();
        ui().button(492, 328, 62).text(KineticI18n.translatable("gui.taczworkshop.save")).tooltip(KineticI18n.translatable("tip.taczworkshop.save")).onClick(this::saveSelection).build();
        ui().button(560, 328, 66).text(KineticI18n.translatable("gui.taczworkshop.back")).tooltip(KineticI18n.translatable("tip.taczworkshop.back.recipe_editor")).onClick(this::close).build();
        rebuildFiltered();
    }

    private void restoreOriginal() {
        selected.clear();
        selected.addAll(originalSelection);
    }

    private void rebuildFiltered() {
        String query = search == null ? "" : search.textValue().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (Entry entry : source) {
            String searchable = (entry.name() + " " + entry.id()).toLowerCase(Locale.ROOT);
            if (query.isEmpty() || searchable.contains(query)) filtered.add(entry);
        }
        int rows = (filtered.size() + COLUMNS - 1) / COLUMNS;
        scroll.update(rows, ROWS);
    }

    @Override
    protected void renderBackground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        KineticTheme.panel(graphics, 6, 6, 628, 348);
        KineticTheme.panelAlt(graphics, 10, 46, 620, 274);
        KineticTheme.panelAlt(graphics, INFO_X, GRID_Y, INFO_W, GRID_H);
        renderEntries(graphics, mouseX, mouseY);
        scroll.render(graphics, mouseX, mouseY, SCROLL_X, GRID_Y, SCROLL_W, GRID_H, 18);
    }

    @Override
    protected void renderForeground(KineticGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.text(KineticI18n.translatable("gui.taczworkshop.workbench.selected_count", selected.size()), 374, 18, 0xFFFFFFFF, true);
        int x = INFO_X + 8;
        int y = GRID_Y + 8;
        if (hovered == null) {
            graphics.text(KineticI18n.translatable("gui.taczworkshop.workbench.title"), x, y, 0xFFFFFFFF, true);
            y += 18;
            for (var line : KineticText.wrap(KineticI18n.translatable("gui.taczworkshop.workbench.guide"), INFO_W - 16)) {
                graphics.text(line, x, y, 0xFFCCCCCC, false);
                y += KineticText.lineHeight() + 2;
            }
        } else {
            graphics.scrollingText(Component.literal(hovered.name()), x, y, INFO_W - 16, 0xFFFFFFFF, true);
            y += 15;
            for (var line : KineticText.wrap(Component.literal(hovered.id().toString()), INFO_W - 16)) {
                graphics.text(line, x, y, 0xFFCCCCCC, false);
                y += KineticText.lineHeight() + 1;
            }
            y += 6;
            graphics.text(KineticI18n.translatable(selected.contains(hovered.id().toString()) ? "gui.taczworkshop.workbench.selected" : "gui.taczworkshop.workbench.not_selected"), x, y, 0xFFFFFFFF, false);
        }
    }

    private void renderEntries(KineticGraphics graphics, int mouseX, int mouseY) {
        hovered = null;
        int baseRow = scroll.smoothIndexOffset();
        int visualShift = scroll.visualShift(CELL_SIZE);
        int first = baseRow * COLUMNS;
        int last = Math.min(filtered.size(), first + (ROWS + 2) * COLUMNS);
        graphics.scissor(GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H);
        for (int index = first; index < last; index++) {
            int visible = index - first;
            int x = GRID_X + (visible % COLUMNS) * CELL_SIZE;
            int y = GRID_Y + (visible / COLUMNS) * CELL_SIZE - visualShift;
            Entry entry = filtered.get(index);
            boolean hover = KineticTheme.hovering(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE);
            KineticTheme.itemSlot(graphics, x, y, hover);
            if (!entry.stack().isEmpty()) KineticTheme.item(graphics, entry.stack(), x, y, SLOT_SIZE, 1.0F, false);
            if (selected.contains(entry.id().toString())) KineticTheme.stateOutline(graphics, x, y, SLOT_SIZE, SLOT_SIZE, true, false, false);
            if (hover) hovered = entry;
        }
        graphics.endScissor();
    }

    @Override
    protected boolean onMouseClick(MouseInput input) {
        double mouseX = input.x(), mouseY = input.y(); int button = input.rawButton();
        boolean widget = false;
        if (scroll.beginDrag(mouseX, mouseY, input.button(), SCROLL_X, GRID_Y, SCROLL_W, GRID_H, 18, 2)) return true;
        int index = indexAt(mouseX, mouseY);
        if (KineticMouseButtons.isPrimary(button) && index >= 0) {
            String id = filtered.get(index).id().toString();
            if (!selected.add(id)) selected.remove(id);
            return true;
        }
        return widget;
    }

    private int indexAt(double mouseX, double mouseY) {
        if (!KineticTheme.hovering(mouseX, mouseY, GRID_X, GRID_Y, GRID_W, GRID_H)) return -1;
        int lx = (int) mouseX - GRID_X;
        int visualShift = scroll.visualShift(CELL_SIZE);
        int ly = (int) Math.floor(mouseY - GRID_Y + visualShift);
        int col = lx / CELL_SIZE;
        int row = ly / CELL_SIZE;
        if (col < 0 || col >= COLUMNS || row < 0 || row > ROWS || lx % CELL_SIZE == SLOT_SIZE || ly % CELL_SIZE == SLOT_SIZE) return -1;
        int index = (scroll.smoothIndexOffset() + row) * COLUMNS + col;
        return index < filtered.size() ? index : -1;
    }

    @Override
    protected boolean onMouseDrag(MouseDragInput input) {
        double mouseX = input.x(), mouseY = input.y(); int button = input.rawButton(); double dragX = input.deltaX(), dragY = input.deltaY();
        return scroll.drag(mouseY, GRID_Y, GRID_H, 18) || false;
    }

    @Override
    protected boolean onMouseRelease(MouseInput input) {
        double mouseX = input.x(), mouseY = input.y(); int button = input.rawButton();
        return scroll.release(input.button()) || false;
    }

    @Override
    protected boolean onMouseScroll(ScrollInput input) {
        double mouseX = input.x(), mouseY = input.y(), delta = input.deltaY();
        if (KineticTheme.hovering(mouseX, mouseY, GRID_X, GRID_Y, GRID_W + 12, GRID_H) && scroll.scroll(delta)) return true;
        return false;
    }

    private void saveSelection() {
        if (onSave != null) onSave.accept(new ArrayList<>(selected));
    }


}
