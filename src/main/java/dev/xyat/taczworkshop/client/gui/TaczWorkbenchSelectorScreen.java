package dev.xyat.taczworkshop.client.gui;

import dev.xyat.kineticcore.api.client.text.KineticText;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.resource.index.CommonBlockIndex;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class TaczWorkbenchSelectorScreen extends KineticScreen {
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

    private final TaczRecipeEditorScreen parent;
    private final Consumer<List<String>> onSave;
    private final List<String> originalSelection;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<Entry> source = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private final GridScrollController scroll = new GridScrollController();
    private EditBox search;
    private Entry hovered;

    public TaczWorkbenchSelectorScreen(TaczRecipeEditorScreen parent, List<String> selected, Consumer<List<String>> onSave) {
        super(Component.translatable("gui.taczworkshop.workbench.title"));
        this.parent = parent;
        this.onSave = onSave;
        this.originalSelection = selected == null ? List.of() : List.copyOf(selected);
        if (selected != null) this.selected.addAll(selected);
        useStandardCanvas();
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
    protected void buildUi() {
        String oldSearch = search == null ? "" : search.getValue();
        search = addTextField(14, 12, 224, Component.translatable("gui.taczworkshop.search"));
        search.setMaxLength(256);
        search.setValue(oldSearch);
        search.setResponder(value -> {
            scroll.reset();
            rebuildFiltered();
        });

        addButton(
                244, 12, 118,
                Component.translatable("gui.taczworkshop.workbench.restore_original"),
                Component.translatable("tip.taczworkshop.workbench.restore_original"),
                this::restoreOriginal
        );
        addButton(492, 328, 62, Component.translatable("gui.taczworkshop.save"), Component.translatable("tip.taczworkshop.save"), this::saveAndClose);
        addButton(560, 328, 66, Component.translatable("gui.taczworkshop.back"), Component.translatable("tip.taczworkshop.back.recipe_editor"), this::onClose);
        rebuildFiltered();
    }

    private void restoreOriginal() {
        selected.clear();
        selected.addAll(originalSelection);
    }

    private void rebuildFiltered() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (Entry entry : source) {
            String searchable = (entry.name() + " " + entry.id()).toLowerCase(Locale.ROOT);
            if (query.isEmpty() || searchable.contains(query)) filtered.add(entry);
        }
        int rows = (filtered.size() + COLUMNS - 1) / COLUMNS;
        scroll.update(rows, ROWS);
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.panel(graphics, 6, 6, 628, 348);
        GuiTheme.panelAlt(graphics, 10, 46, 620, 274);
        GuiTheme.panelAlt(graphics, INFO_X, GRID_Y, INFO_W, GRID_H);
        renderEntries(graphics, mouseX, mouseY);
        GuiTheme.scrollbar(scroll, graphics, mouseX, mouseY, SCROLL_X, GRID_Y, SCROLL_W, GRID_H, 18);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTextFieldPlaceholder(graphics, search, Component.translatable("gui.taczworkshop.workbench.search.hint"));
        graphics.drawString(font, Component.translatable("gui.taczworkshop.workbench.selected_count", selected.size()), 374, 18, 0xFFFFFFFF, true);
        int x = INFO_X + 8;
        int y = GRID_Y + 8;
        if (hovered == null) {
            graphics.drawString(font, Component.translatable("gui.taczworkshop.workbench.title"), x, y, 0xFFFFFFFF, true);
            y += 18;
            for (var line : font.split(Component.translatable("gui.taczworkshop.workbench.guide"), INFO_W - 16)) {
                graphics.drawString(font, line, x, y, 0xFFCCCCCC, false);
                y += font.lineHeight + 2;
            }
        } else {
            KineticText.drawScrollingLeft(graphics, font, hovered.name(), x, y, INFO_W - 16, 0xFFFFFFFF, true);
            y += 15;
            for (var line : font.split(Component.literal(hovered.id().toString()), INFO_W - 16)) {
                graphics.drawString(font, line, x, y, 0xFFCCCCCC, false);
                y += font.lineHeight + 1;
            }
            y += 6;
            graphics.drawString(font, Component.translatable(selected.contains(hovered.id().toString()) ? "gui.taczworkshop.workbench.selected" : "gui.taczworkshop.workbench.not_selected"), x, y, 0xFFFFFFFF, false);
        }
    }

    private void renderEntries(GuiGraphics graphics, int mouseX, int mouseY) {
        hovered = null;
        int baseRow = scroll.smoothIndexOffset();
        int visualShift = scroll.visualShift(CELL_SIZE);
        int first = baseRow * COLUMNS;
        int last = Math.min(filtered.size(), first + (ROWS + 2) * COLUMNS);
        enableCanvasScissor(graphics, GRID_X, GRID_Y, GRID_X + GRID_W, GRID_Y + GRID_H);
        for (int index = first; index < last; index++) {
            int visible = index - first;
            int x = GRID_X + (visible % COLUMNS) * CELL_SIZE;
            int y = GRID_Y + (visible / COLUMNS) * CELL_SIZE - visualShift;
            Entry entry = filtered.get(index);
            boolean hover = GuiTheme.hovering(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE);
            GuiTheme.itemSlot(graphics, x, y, hover);
            if (!entry.stack().isEmpty()) GuiTheme.item(graphics, font, entry.stack(), x, y, SLOT_SIZE, 1.0F, false);
            if (selected.contains(entry.id().toString())) graphics.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, 0xFF35D06F);
            if (hover) hovered = entry;
        }
        disableCanvasScissor(graphics);
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        boolean widget = super.canvasMouseClicked(mouseX, mouseY, button);
        if (button == 0 && scroll.beginDrag(mouseX, mouseY, SCROLL_X, GRID_Y, SCROLL_W, GRID_H, 18, 2)) return true;
        int index = indexAt(mouseX, mouseY);
        if (button == 0 && index >= 0) {
            String id = filtered.get(index).id().toString();
            if (!selected.add(id)) selected.remove(id);
            return true;
        }
        return widget;
    }

    private int indexAt(double mouseX, double mouseY) {
        if (!GuiTheme.hovering(mouseX, mouseY, GRID_X, GRID_Y, GRID_W, GRID_H)) return -1;
        int lx = (int) mouseX - GRID_X;
        int visualShift = scroll.visualShift(CELL_SIZE);
        int ly = (int) Math.floor(mouseY - GRID_Y + visualShift);
        int col = lx / CELL_SIZE;
        int row = ly / CELL_SIZE;
        if (col < 0 || col >= COLUMNS || row < 0 || row > ROWS || lx % CELL_SIZE >= SLOT_SIZE || ly % CELL_SIZE >= SLOT_SIZE) return -1;
        int index = (scroll.smoothIndexOffset() + row) * COLUMNS + col;
        return index < filtered.size() ? index : -1;
    }

    @Override
    protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return scroll.drag(mouseY, GRID_Y, GRID_H, 18) || super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return scroll.release(button) || super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (GuiTheme.hovering(mouseX, mouseY, GRID_X, GRID_Y, GRID_W + 12, GRID_H) && scroll.scroll(delta)) return true;
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    private void saveAndClose() {
        if (onSave != null) onSave.accept(new ArrayList<>(selected));
        if (minecraft != null) navigateBack();
    }


    @Override
    public void onClose() {
        if (minecraft != null) navigateBack();
    }
}
