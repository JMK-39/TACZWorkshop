package dev.xyat.taczworkshop.client.gui;

import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.init.ModItems;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.search.ItemSearchIndex;
import dev.xyat.kineticcore.api.client.selector.ItemSelectorScreen;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.GridScrollController;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.HighZButton;
import dev.xyat.kineticcore.api.client.selector.NbtEditorScreen;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.NumericEditBox;
import dev.xyat.taczworkshop.client.TaczDataClientState;
import dev.xyat.taczworkshop.client.TaczDataListEntry;
import dev.xyat.taczworkshop.client.TaczDataStackUtil;
import dev.xyat.taczworkshop.client.TaczPreviewIndexContext;
import dev.xyat.taczworkshop.client.TaczStackUtil;
import dev.xyat.taczworkshop.data.TaczDataKind;
import dev.xyat.taczworkshop.data.TaczMaterial;
import dev.xyat.taczworkshop.data.TaczRecipeCodec;
import dev.xyat.taczworkshop.data.TaczRecipeRecord;
import dev.xyat.taczworkshop.network.TaczRecipeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class TaczRecipeEditorScreen extends KineticScreen {
    private static final List<String> RESULT_TYPES = List.of("gun", "attachment", "ammo", "melee", "throwable", "consumable", "custom");
    private static final List<String> ATTACHMENT_TYPES = List.of("scope", "muzzle", "stock", "grip", "laser", "extended_mag");

    private static final int MATERIAL_GRID_X = 22;
    private static final int MATERIAL_GRID_Y = 103;
    private static final int MATERIAL_COLS = 10;
    private static final int MATERIAL_ROWS = 5;
    private static final int SLOT_SIZE = 18;
    private static final int CELL_SIZE = 19;
    private static final int MATERIAL_GRID_W = MATERIAL_COLS * CELL_SIZE;
    private static final int MATERIAL_GRID_H = MATERIAL_ROWS * CELL_SIZE;
    private static final int MATERIAL_SCROLL_X = MATERIAL_GRID_X + MATERIAL_GRID_W + 5;
    private static final int MATERIAL_SCROLL_W = 4;

    private static final int RESULT_SLOT_X = 418;
    private static final int RESULT_SLOT_Y = 101;
    private static final int WORKBENCH_BUTTON_X = 180;
    private static final int WORKBENCH_BUTTON_Y = 42;
    private static final int WORKBENCH_BUTTON_W = 76;
    private static final int WORKBENCH_SLOT_X = 262;
    private static final int WORKBENCH_SLOT_Y = 43;
    private static final int ATTACHMENT_SUMMARY_X = 418;
    private static final int ATTACHMENT_SUMMARY_Y = 316;
    private static final int ATTACHMENT_SUMMARY_W = 198;
    private static final int ATTACHMENT_SUMMARY_H = 22;

    private final Screen parent;
    private final TaczRecipeRecord record;
    private final GridScrollController materialScroll = new GridScrollController();
    private final TaczContextMenu contextMenu = new TaczContextMenu();
    private int selectedMaterial = -1;
    private EditBox idBox;
    private EditBox commentBox;
    private EditBox groupBox;
    private Button resultTypeButton;
    private boolean hoveredResult;
    private int hoveredMaterialIndex = -1;
    private boolean hoveredAttachmentSummary;
    private boolean hoveredWorkbench;

    public TaczRecipeEditorScreen(Screen parent, TaczRecipeRecord record) {
        super(Component.translatable("gui.taczworkshop.editor.title"));
        this.parent = parent;
        this.record = record.copy();
        useCanvas(640, 360, 6);
        maxScale = 1.0F;
        configureStandaloneDraft(this::captureRecipeSnapshot, this::restoreRecipeSnapshot);
    }

    private record RecipeSnapshot(String json) {
    }

    private RecipeSnapshot captureRecipeSnapshot() {
        return new RecipeSnapshot(record.toJson().toString());
    }

    private void restoreRecipeSnapshot(RecipeSnapshot snapshot) {
        if (snapshot == null || snapshot.json() == null || snapshot.json().isBlank()) return;
        JsonElement parsed = JsonParser.parseString(snapshot.json());
        if (!parsed.isJsonObject()) return;
        TaczRecipeRecord restored = TaczRecipeRecord.fromJson(parsed.getAsJsonObject());
        record.setId(restored.id());
        record.setEnabled(restored.enabled());
        record.setComment(restored.comment());
        record.materials().clear();
        for (TaczMaterial material : restored.materials()) record.materials().add(material.copy());
        record.setResult(restored.result());
        record.setOrigin(restored.origin());
        record.setOriginalId(restored.originalId());
        record.setWorkbenches(restored.workbenches());
        selectedMaterial = record.materials().isEmpty() ? -1 : Math.min(selectedMaterial, record.materials().size() - 1);
        updateMaterialScroll();
    }

    @Override
    protected void buildUi() {
        contextMenu.close();
        updateMaterialScroll();

        idBox = new EditBox(font, 72, 14, 244, 20, Component.translatable("gui.taczworkshop.recipe_id"));
        idBox.setValue(record.id());
        idBox.active = !record.isOriginal();
        idBox.setResponder(record::setId);
        addRenderableWidget(idBox);

        commentBox = new EditBox(font, 324, 14, 302, 20, Component.translatable("gui.taczworkshop.comment"));
        commentBox.setValue(record.comment());
        commentBox.setResponder(record::setComment);
        addRenderableWidget(commentBox);

        Button enabled = Button.builder(
                Component.translatable(record.enabled() ? "gui.taczworkshop.enabled" : "gui.taczworkshop.disabled"),
                button -> {
                    record.setEnabled(!record.enabled());
                    button.setMessage(Component.translatable(record.enabled() ? "gui.taczworkshop.enabled" : "gui.taczworkshop.disabled"));
                }
        ).bounds(14, 42, 72, 20).build();
        enabled.setTooltip(Tooltip.create(Component.translatable("tip.taczworkshop.enabled")));
        addRenderableWidget(enabled);

        resultTypeButton = new HighZButton(90, 42, 86, 20, resultTypeComponent(), ignored -> openResultTypeMenu(), null, 40);
        addRenderableWidget(resultTypeButton);
        addButton(WORKBENCH_BUTTON_X, WORKBENCH_BUTTON_Y, WORKBENCH_BUTTON_W, 20, Component.translatable("gui.taczworkshop.workbench.button"), "tip.taczworkshop.workbench.select", this::openWorkbenchSelector);
        addButton(524, 42, 48, 20, Component.translatable("gui.taczworkshop.save"), "tip.taczworkshop.save", this::save);
        addButton(578, 42, 48, 20, Component.translatable("gui.taczworkshop.back"), "tip.taczworkshop.back.recipe_list", this::onClose);

        createSelectedMaterialWidgets();
        createResultWidgets();
    }

    private void createSelectedMaterialWidgets() {
        if (selectedMaterial < 0 || selectedMaterial >= record.materials().size()) return;
        TaczMaterial material = record.materials().get(selectedMaterial);

        NumericEditBox count = NumericEditBox.integer(
                font,
                325,
                139,
                74,
                20,
                Component.translatable("gui.taczworkshop.count"),
                true,
                1,
                9999
        );
        count.setValue(Integer.toString(material.count()));
        count.setResponder(value -> material.setCount(parseCount(value, material.count())));
        addRenderableWidget(count);

        addButton(238, 251, 76, 20, Component.translatable("gui.taczworkshop.material.replace"), "tip.taczworkshop.material.select", () -> selectMaterial(selectedMaterial));
        Button nbt = Button.builder(Component.translatable("gui.taczworkshop.nbt"), ignored -> editMaterialNbt(selectedMaterial)).bounds(322, 251, 76, 20).build();
        JsonObject ingredientObject = ingredientObject(material);
        nbt.active = ingredientObject != null && ingredientObject.has("item");
        nbt.setTooltip(Tooltip.create(Component.translatable(nbt.active ? "tip.taczworkshop.material.nbt" : "tip.taczworkshop.material.nbt_tag")));
        addRenderableWidget(nbt);
        addButton(238, 277, 160, 20, Component.translatable("gui.taczworkshop.remove"), "tip.taczworkshop.material.remove", () -> removeMaterial(selectedMaterial));
    }

    private void createResultWidgets() {
        NumericEditBox resultCount = NumericEditBox.integer(
                font,
                516,
                137,
                100,
                20,
                Component.translatable("gui.taczworkshop.count"),
                true,
                1,
                9999
        );
        resultCount.setValue(Integer.toString(resultCount()));
        resultCount.setResponder(value -> setResultCount(parseCount(value, resultCount())));
        addRenderableWidget(resultCount);

        addButton(548, 101, 68, 20, Component.translatable("gui.taczworkshop.nbt"), "tip.taczworkshop.result_nbt", this::editResultNbt);

        groupBox = new EditBox(font, 500, 162, 116, 20, Component.translatable("gui.taczworkshop.group"));
        groupBox.setValue(getString(record.result(), "group"));
        groupBox.setResponder(value -> setOptionalString(record.result(), "group", value));
        addRenderableWidget(groupBox);

        if ("gun".equals(record.resultType())) {
            NumericEditBox ammoCount = NumericEditBox.integer(
                    font,
                    530,
                    187,
                    86,
                    20,
                    Component.translatable("gui.taczworkshop.ammo_count"),
                    true,
                    0,
                    9999
            );
            ammoCount.setValue(Integer.toString(getInt(record.result(), "ammo_count", 0)));
            ammoCount.setResponder(value -> record.result().addProperty("ammo_count", Math.max(0, parseInt(value, 0))));
            addRenderableWidget(ammoCount);
        }
    }

    private void updateMaterialScroll() {
        int visible = MATERIAL_COLS * MATERIAL_ROWS;
        int total = record.materials().size() + (record.materials().size() < 64 ? 1 : 0);
        int totalRows = (total + MATERIAL_COLS - 1) / MATERIAL_COLS;
        materialScroll.update(totalRows, MATERIAL_ROWS);
        if (selectedMaterial >= record.materials().size()) selectedMaterial = record.materials().isEmpty() ? -1 : record.materials().size() - 1;
    }

    @Override
    protected void renderCanvasBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.panel(graphics, 6, 6, 628, 348);
        renderWorkbenchSlot(graphics, mouseX, mouseY);
        GuiTheme.panelAlt(graphics, 12, 72, 390, 276);
        GuiTheme.panelAlt(graphics, 410, 72, 216, 276);
        renderMaterials(graphics, mouseX, mouseY);
        GuiTheme.scrollbar(materialScroll, graphics, mouseX, mouseY, MATERIAL_SCROLL_X, MATERIAL_GRID_Y, MATERIAL_SCROLL_W, MATERIAL_GRID_H, 18);
        renderResultSlot(graphics, mouseX, mouseY);
        if ("gun".equals(record.resultType())) renderAttachmentSummary(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderCanvasForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(font, Component.translatable("gui.taczworkshop.recipe_id"), 14, 20, 0xFFFFFFFF, true);
        renderSearchPlaceholder(graphics, commentBox, "gui.taczworkshop.comment.hint");
        renderSearchPlaceholder(graphics, groupBox, "gui.taczworkshop.group.hint");

        graphics.drawString(font, Component.translatable("gui.taczworkshop.materials", record.materials().size()), 20, 82, 0xFFFFFFFF, true);
        graphics.drawString(font, Component.translatable("gui.taczworkshop.result"), 418, 82, 0xFFFFFFFF, true);

        if (selectedMaterial >= 0 && selectedMaterial < record.materials().size()) {
            TaczMaterial material = record.materials().get(selectedMaterial);
            ItemStack preview = TaczRecipeCodec.materialPreview(material);
            graphics.drawString(font, Component.translatable("gui.taczworkshop.material.selected"), 238, 103, 0xFFFFFFFF, true);
            graphics.drawString(font, GuiTheme.trim(font, materialDisplayName(material, preview).getString(), 160), 238, 116, 0xFFFFFFFF, false);
            graphics.drawString(font, GuiTheme.trim(font, materialLabel(material), 160), 238, 129, 0xFFAAAAAA, false);
            graphics.drawString(font, Component.translatable("gui.taczworkshop.material.count_label"), 238, 145, 0xFFCCCCCC, false);
            graphics.drawString(font, Component.translatable("gui.taczworkshop.item_nbt"), 238, 173, 0xFFCCCCCC, false);
            renderNbtBlock(graphics, 238, 186, 160, 5, materialNbtText(material, preview));
        } else {
            graphics.drawString(font, Component.translatable("gui.taczworkshop.material.select_hint"), 238, 107, 0xFFAAAAAA, false);
        }

        ItemStack resultStack = resultPreviewStack();
        graphics.drawString(font, GuiTheme.trim(font, resultDisplayName(resultStack).getString(), 96), 442, 101, 0xFFFFFFFF, true);
        graphics.drawString(font, GuiTheme.trim(font, record.resultId(), 96), 442, 114, 0xFFAAAAAA, false);
        graphics.drawString(font, Component.translatable("gui.taczworkshop.type." + record.resultType()), 418, 126, 0xFFCCCCCC, false);
        graphics.drawString(font, Component.translatable("gui.taczworkshop.result.count_label"), 418, 143, 0xFFCCCCCC, false);
        graphics.drawString(font, Component.translatable("gui.taczworkshop.group"), 418, 168, 0xFFCCCCCC, false);
        if ("gun".equals(record.resultType())) {
            graphics.drawString(font, Component.translatable("gui.taczworkshop.ammo_count"), 418, 193, 0xFFCCCCCC, false);
        }
        int nbtLabelY = "gun".equals(record.resultType()) ? 216 : 193;
        graphics.drawString(font, Component.translatable("gui.taczworkshop.item_nbt"), 418, nbtLabelY, 0xFFCCCCCC, false);
        renderNbtBlock(graphics, 418, nbtLabelY + 13, 198, "gun".equals(record.resultType()) ? 8 : 11, resultNbtText(resultStack));
        contextMenu.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSearchPlaceholder(GuiGraphics graphics, EditBox box, String key) {
        if (box == null || !box.visible || !box.getValue().isEmpty() || box.isFocused()) return;
        String text = font.plainSubstrByWidth(Component.translatable(key).getString(), Math.max(0, box.getWidth() - 10));
        graphics.drawString(font, text, box.getX() + 5, box.getY() + (box.getHeight() - font.lineHeight) / 2, 0xFFAAAAAA, false);
    }

    private void renderMaterials(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredMaterialIndex = -1;
        int firstRow = materialScroll.smoothIndexOffset();
        int shift = materialScroll.visualShift(CELL_SIZE);
        int first = firstRow * MATERIAL_COLS;
        int visibleSlots = MATERIAL_COLS * (MATERIAL_ROWS + 1);
        int total = record.materials().size() + (record.materials().size() < 64 ? 1 : 0);
        int last = Math.min(total, first + visibleSlots);
        enableCanvasScissor(graphics, MATERIAL_GRID_X, MATERIAL_GRID_Y, MATERIAL_GRID_X + MATERIAL_GRID_W, MATERIAL_GRID_Y + MATERIAL_GRID_H);
        for (int index = first; index < last; index++) {
            int visible = index - first;
            int col = visible % MATERIAL_COLS;
            int row = visible / MATERIAL_COLS;
            int x = MATERIAL_GRID_X + col * CELL_SIZE;
            int y = MATERIAL_GRID_Y + row * CELL_SIZE - shift;
            boolean addSlot = index == record.materials().size();
            boolean hovered = GuiTheme.hovering(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE) && !contextMenu.isOpen();
            GuiTheme.itemSlot(graphics, x, y, hovered);
            if (addSlot) {
                graphics.drawCenteredString(font, "+", x + 9, y + 5, 0xFFFFFFFF);
            } else {
                TaczMaterial material = record.materials().get(index);
                ItemStack preview = TaczRecipeCodec.materialPreview(material);
                if (!preview.isEmpty()) renderItemWithPreviewContext(graphics, preview, x, y);
                if (index == selectedMaterial) graphics.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, 0xFFFFAA00);
                if (hovered) hoveredMaterialIndex = index;
            }
        }
        graphics.disableScissor();
    }

    private void renderResultSlot(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredResult = GuiTheme.hovering(mouseX, mouseY, RESULT_SLOT_X, RESULT_SLOT_Y, SLOT_SIZE, SLOT_SIZE);
        GuiTheme.itemSlot(graphics, RESULT_SLOT_X, RESULT_SLOT_Y, hoveredResult);
        ItemStack result = resultPreviewStack();
        if (!result.isEmpty()) renderItemWithPreviewContext(graphics, result, RESULT_SLOT_X, RESULT_SLOT_Y);
    }

    private void renderItemWithPreviewContext(GuiGraphics graphics, ItemStack stack, int x, int y) {
        TaczDataListEntry entry = dataEntryForStack(stack);
        if (entry == null) {
            GuiTheme.item(graphics, font, stack, x, y, SLOT_SIZE, 1.0F, true);
        } else {
            TaczPreviewIndexContext.with(entry, () -> GuiTheme.item(graphics, font, stack, x, y, SLOT_SIZE, 1.0F, true));
        }
    }

    private ItemStack resultPreviewStack() {
        TaczDataListEntry entry = resultDataEntry();
        return entry == null
                ? TaczRecipeCodec.resultPreview(record)
                : TaczPreviewIndexContext.withResult(entry.kind(), entry.id(), entry.previewIndex(), () -> TaczRecipeCodec.resultPreview(record));
    }

    private Component materialDisplayName(TaczMaterial material, ItemStack preview) {
        JsonObject ingredient = ingredientObject(material);
        if (ingredient != null && ingredient.has("tag")) return Component.literal("#" + ingredient.get("tag").getAsString());
        if (!preview.isEmpty()) {
            TaczDataListEntry entry = dataEntryForStack(preview);
            if (entry != null && !entry.nameKey().isBlank() && I18n.exists(entry.nameKey())) return Component.translatable(entry.nameKey());
            Component name = entry == null ? preview.getHoverName() : TaczPreviewIndexContext.withResult(entry.kind(), entry.id(), entry.previewIndex(), preview::getHoverName);
            String text = name.getString();
            if (!text.isBlank() && !text.equals(preview.getDescriptionId()) && !text.startsWith("item.")) return name;
        }
        return Component.literal(materialLabel(material));
    }

    private Component resultDisplayName(ItemStack resultStack) {
        TaczDataListEntry entry = resultDataEntry();
        if (entry != null && !entry.nameKey().isBlank() && I18n.exists(entry.nameKey())) return Component.translatable(entry.nameKey());
        if (!resultStack.isEmpty()) {
            Component name = entry == null ? resultStack.getHoverName() : TaczPreviewIndexContext.withResult(entry.kind(), entry.id(), entry.previewIndex(), resultStack::getHoverName);
            String text = name.getString();
            if (!text.isBlank() && !text.equals(resultStack.getDescriptionId()) && !text.startsWith("item.")) return name;
        }
        return Component.literal(record.resultId());
    }

    private String materialNbtText(TaczMaterial material, ItemStack preview) {
        JsonObject ingredient = ingredientObject(material);
        if (ingredient != null && ingredient.has("nbt")) {
            String value = TaczRecipeCodec.nbtString(ingredient.get("nbt"));
            if (!value.isBlank()) return value;
        }
        if (!preview.isEmpty() && preview.hasTag() && preview.getTag() != null) return preview.getTag().toString();
        return Component.translatable("gui.taczworkshop.nbt.none").getString();
    }

    private String resultNbtText(ItemStack resultStack) {
        if (!resultStack.isEmpty() && resultStack.hasTag() && resultStack.getTag() != null) return resultStack.getTag().toString();
        String configured = currentResultNbt();
        if (!configured.isBlank()) return configured;
        return Component.translatable("gui.taczworkshop.nbt.none").getString();
    }

    private void renderNbtBlock(GuiGraphics graphics, int x, int y, int width, int maxLines, String nbt) {
        List<FormattedCharSequence> lines = font.split(Component.literal(nbt), width);
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) {
            String suffix = i == maxLines - 1 && lines.size() > maxLines ? "..." : "";
            graphics.drawString(font, lines.get(i), x, y + i * (font.lineHeight + 1), 0xFFAAAAAA, false);
            if (!suffix.isEmpty()) {
                graphics.drawString(font, suffix, x + Math.max(0, width - font.width(suffix)), y + i * (font.lineHeight + 1), 0xFFAAAAAA, false);
            }
        }
    }

    private TaczDataListEntry resultDataEntry() {
        TaczDataKind kind = switch (record.resultType()) {
            case "gun" -> TaczDataKind.GUN;
            case "attachment" -> TaczDataKind.ATTACHMENT;
            case "ammo" -> TaczDataKind.AMMO;
            case "melee" -> TaczDataKind.MELEE;
            case "throwable" -> TaczDataKind.THROWABLE;
            case "consumable" -> TaczDataKind.CONSUMABLE;
            default -> null;
        };
        if (kind != null) return findDataEntry(kind, record.resultId());
        if ("custom".equals(record.resultType())) return dataEntryForStack(TaczRecipeCodec.resultPreview(record));
        return null;
    }

    private TaczDataListEntry dataEntryForStack(ItemStack stack) {
        String gun = TaczStackUtil.specialId(stack, "gun");
        if (!gun.isBlank()) return findDataEntry(TaczDataKind.GUN, gun);
        String attachment = TaczStackUtil.specialId(stack, "attachment");
        if (!attachment.isBlank()) return findDataEntry(TaczDataKind.ATTACHMENT, attachment);
        String ammo = TaczStackUtil.specialId(stack, "ammo");
        if (!ammo.isBlank()) return findDataEntry(TaczDataKind.AMMO, ammo);
        String melee = TaczDataStackUtil.externalId(stack, TaczDataKind.MELEE);
        if (!melee.isBlank()) return findDataEntry(TaczDataKind.MELEE, melee);
        String throwable = TaczDataStackUtil.externalId(stack, TaczDataKind.THROWABLE);
        if (!throwable.isBlank()) return findDataEntry(TaczDataKind.THROWABLE, throwable);
        String consumable = TaczDataStackUtil.externalId(stack, TaczDataKind.CONSUMABLE);
        if (!consumable.isBlank()) return findDataEntry(TaczDataKind.CONSUMABLE, consumable);
        return null;
    }

    private TaczDataListEntry findDataEntry(TaczDataKind kind, String id) {
        return TaczDataClientState.snapshot(kind).stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
    }

    private void renderAttachmentSummary(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredAttachmentSummary = GuiTheme.hovering(mouseX, mouseY, ATTACHMENT_SUMMARY_X, ATTACHMENT_SUMMARY_Y, ATTACHMENT_SUMMARY_W, ATTACHMENT_SUMMARY_H);
        GuiTheme.panel(graphics, ATTACHMENT_SUMMARY_X, ATTACHMENT_SUMMARY_Y, ATTACHMENT_SUMMARY_W, ATTACHMENT_SUMMARY_H);
        int count = 0;
        JsonObject object = attachments();
        for (String type : ATTACHMENT_TYPES) if (!getString(object, type).isBlank()) count++;
        graphics.drawString(font, Component.translatable("gui.taczworkshop.recipe.attachments.summary", count, ATTACHMENT_TYPES.size()), ATTACHMENT_SUMMARY_X + 7, ATTACHMENT_SUMMARY_Y + 7, 0xFFFFFFFF, false);
        if (hoveredAttachmentSummary) graphics.renderOutline(ATTACHMENT_SUMMARY_X, ATTACHMENT_SUMMARY_Y, ATTACHMENT_SUMMARY_W, ATTACHMENT_SUMMARY_H, 0xFFFFFFFF);
    }

    @Override
    protected boolean canvasMouseClicked(double mouseX, double mouseY, int button) {
        if (contextMenu.isOpen()) {
            if (contextMenu.mouseClicked(mouseX, mouseY, button)) return true;
            if (contextMenu.contains(mouseX, mouseY)) return true;
            contextMenu.close();
            if (button != 1) return true;
        }

        boolean widget = super.canvasMouseClicked(mouseX, mouseY, button);
        if (button == 0 && materialScroll.beginDrag(mouseX, mouseY, MATERIAL_SCROLL_X, MATERIAL_GRID_Y, MATERIAL_SCROLL_W, MATERIAL_GRID_H, 18, 2)) return true;

        int materialIndex = materialIndexAt(mouseX, mouseY);
        if (materialIndex >= 0) {
            if (materialIndex == record.materials().size() && record.materials().size() < 64) {
                if (button == 0) selectMaterial(-1);
                return true;
            }
            if (materialIndex < record.materials().size()) {
                if (button == 0) {
                    selectedMaterial = materialIndex;
                    refreshEditorWidgets();
                    return true;
                }
                if (button == 1) {
                    openMaterialContext(materialIndex, (int) mouseX, (int) mouseY);
                    return true;
                }
            }
        }

        if (GuiTheme.hovering(mouseX, mouseY, RESULT_SLOT_X, RESULT_SLOT_Y, SLOT_SIZE, SLOT_SIZE)) {
            if (button == 0) {
                selectResult();
                return true;
            }
            if (button == 1) {
                openResultContext((int) mouseX, (int) mouseY);
                return true;
            }
        }

        if (button == 0 && GuiTheme.hovering(mouseX, mouseY, WORKBENCH_SLOT_X, WORKBENCH_SLOT_Y, SLOT_SIZE, SLOT_SIZE)) {
            openWorkbenchSelector();
            return true;
        }

        if ("gun".equals(record.resultType()) && GuiTheme.hovering(mouseX, mouseY, ATTACHMENT_SUMMARY_X, ATTACHMENT_SUMMARY_Y, ATTACHMENT_SUMMARY_W, ATTACHMENT_SUMMARY_H) && button == 1) {
            openAttachmentContext((int) mouseX, (int) mouseY);
            return true;
        }
        return widget;
    }

    private int materialIndexAt(double mouseX, double mouseY) {
        if (!GuiTheme.hovering(mouseX, mouseY, MATERIAL_GRID_X, MATERIAL_GRID_Y, MATERIAL_GRID_W, MATERIAL_GRID_H)) return -1;
        int localX = (int) (mouseX - MATERIAL_GRID_X);
        int localY = (int) (mouseY - MATERIAL_GRID_Y + materialScroll.visualShift(CELL_SIZE));
        int col = localX / CELL_SIZE;
        int row = localY / CELL_SIZE;
        if (col < 0 || col >= MATERIAL_COLS || row < 0 || row > MATERIAL_ROWS) return -1;
        if (localX % CELL_SIZE >= SLOT_SIZE || localY % CELL_SIZE >= SLOT_SIZE) return -1;
        int index = (materialScroll.smoothIndexOffset() + row) * MATERIAL_COLS + col;
        int total = record.materials().size() + (record.materials().size() < 64 ? 1 : 0);
        return index < total ? index : -1;
    }

    private void openMaterialContext(int index, int mouseX, int mouseY) {
        List<TaczContextMenu.Entry> entries = new ArrayList<>();
        entries.add(TaczContextMenu.Entry.action(Component.translatable("gui.taczworkshop.material.replace"), Component.translatable("tip.taczworkshop.material.select"), () -> selectMaterial(index)));
        JsonObject ingredientObject = ingredientObject(record.materials().get(index));
        if (ingredientObject != null && ingredientObject.has("item")) {
            entries.add(TaczContextMenu.Entry.action(Component.translatable("gui.taczworkshop.nbt"), Component.translatable("tip.taczworkshop.material.nbt"), () -> editMaterialNbt(index)));
        }
        entries.add(TaczContextMenu.Entry.separator());
        entries.add(TaczContextMenu.Entry.danger(Component.translatable("gui.taczworkshop.remove"), Component.translatable("tip.taczworkshop.material.remove"), () -> removeMaterial(index)));
        contextMenu.open(font, mouseX, mouseY, canvasWidth, canvasHeight, entries);
    }

    private void openResultContext(int mouseX, int mouseY) {
        List<TaczContextMenu.Entry> entries = new ArrayList<>();
        entries.add(TaczContextMenu.Entry.action(Component.translatable("gui.taczworkshop.result.select"), Component.translatable("tip.taczworkshop.result_select"), this::selectResult));
        entries.add(TaczContextMenu.Entry.action(Component.translatable("gui.taczworkshop.nbt"), Component.translatable("tip.taczworkshop.result_nbt"), this::editResultNbt));
        contextMenu.open(font, mouseX, mouseY, canvasWidth, canvasHeight, entries);
    }

    private void openAttachmentContext(int mouseX, int mouseY) {
        List<TaczContextMenu.Entry> entries = new ArrayList<>();
        JsonObject object = attachments();
        for (String type : ATTACHMENT_TYPES) {
            String current = getString(object, type);
            entries.add(TaczContextMenu.Entry.action(
                    attachmentComponent(type, object),
                    Component.translatable("tip.taczworkshop.attachment.select"),
                    () -> selectAttachment(type)
            ));
            if (!current.isBlank()) {
                entries.add(TaczContextMenu.Entry.danger(
                        Component.translatable("gui.taczworkshop.attachment.clear_one", Component.translatable("gui.taczworkshop.attachment." + type)),
                        Component.translatable("tip.taczworkshop.attachment.clear"),
                        () -> clearAttachment(type)
                ));
            }
        }
        contextMenu.open(font, mouseX, mouseY, canvasWidth, canvasHeight, entries);
    }

    @Override
    protected boolean canvasMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (contextMenu.isOpen()) return true;
        return materialScroll.drag(mouseY, MATERIAL_GRID_Y, MATERIAL_GRID_H, 18) || super.canvasMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean canvasMouseReleased(double mouseX, double mouseY, int button) {
        return materialScroll.release(button) || super.canvasMouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean canvasMouseScrolled(double mouseX, double mouseY, double delta) {
        if (contextMenu.isOpen()) return true;
        if (GuiTheme.hovering(mouseX, mouseY, MATERIAL_GRID_X, MATERIAL_GRID_Y, MATERIAL_GRID_W + 12, MATERIAL_GRID_H) && materialScroll.scroll(delta)) {
            refreshEditorWidgets();
            return true;
        }
        return super.canvasMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderTooltips(GuiGraphics graphics, int scaledMouseX, int scaledMouseY, int mouseX, int mouseY) {
        if (contextMenu.isOpen()) {
            contextMenu.requestTooltip(graphics, font, scaledMouseX, scaledMouseY, mouseX, mouseY);
            return;
        }
        if (hoveredMaterialIndex >= 0 && hoveredMaterialIndex < record.materials().size()) {
            TaczMaterial material = record.materials().get(hoveredMaterialIndex);
            ItemStack preview = TaczRecipeCodec.materialPreview(material);
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.addAll(font.split(materialDisplayName(material, preview), 320));
            lines.addAll(font.split(Component.literal(materialLabel(material)), 320));
            lines.addAll(font.split(Component.translatable("gui.taczworkshop.material.count", material.count()), 320));
            lines.addAll(font.split(Component.translatable("gui.taczworkshop.item_nbt.value", materialNbtText(material, preview)), 320));
            lines.addAll(font.split(Component.translatable("gui.taczworkshop.material.click_hint"), 320));
            GuiOverlay.requestFormattedTooltip(lines, mouseX, mouseY);
            return;
        }
        if (hoveredWorkbench) {
            ItemStack stack = workbenchPreviewStack();
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.addAll(font.split(Component.translatable("gui.taczworkshop.workbench.button"), 280));
            if (!stack.isEmpty()) lines.addAll(font.split(stack.getHoverName(), 280));
            if (!record.workbenches().isEmpty()) {
                lines.addAll(font.split(Component.literal(record.workbenches().get(0)), 280));
                if (record.workbenches().size() > 1) lines.addAll(font.split(Component.translatable("gui.taczworkshop.workbench.summary", record.workbenches().size()), 280));
            } else {
                lines.addAll(font.split(Component.translatable("gui.taczworkshop.workbench.native"), 280));
            }
            GuiOverlay.requestFormattedTooltip(lines, mouseX, mouseY);
            return;
        }

        if (hoveredResult) {
            ItemStack resultStack = resultPreviewStack();
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.addAll(font.split(resultDisplayName(resultStack), 320));
            lines.addAll(font.split(Component.literal(record.resultId()), 320));
            lines.addAll(font.split(Component.translatable("gui.taczworkshop.item_nbt.value", resultNbtText(resultStack)), 320));
            lines.addAll(font.split(Component.translatable("gui.taczworkshop.result.click_hint"), 320));
            GuiOverlay.requestFormattedTooltip(lines, mouseX, mouseY);
        }
    }

    private void openResultTypeMenu() {
        List<TaczContextMenu.Entry> entries = new ArrayList<>();
        String current = record.resultType();
        for (String type : RESULT_TYPES) {
            entries.add(TaczContextMenu.Entry.toggle(
                    Component.translatable("gui.taczworkshop.type.short." + type),
                    Component.empty(),
                    type.equals(current),
                    () -> setResultType(type)
            ));
        }
        int x = resultTypeButton == null ? 90 : resultTypeButton.getX();
        int y = resultTypeButton == null ? 64 : resultTypeButton.getY() + resultTypeButton.getHeight() + 2;
        contextMenu.open(font, x, y, canvasWidth, canvasHeight, entries);
    }

    private void setResultType(String next) {
        if (next == null || !RESULT_TYPES.contains(next)) return;
        contextMenu.close();
        String current = record.resultType();
        if (next.equals(current)) return;
        int count = resultCount();
        String group = getString(record.result(), "group");
        String nbt = currentResultNbt();

        JsonObject result = new JsonObject();
        result.addProperty("type", next);
        if ("custom".equals(next)) {
            JsonObject item = new JsonObject();
            item.addProperty("item", "minecraft:air");
            item.addProperty("count", count);
            if (!nbt.isBlank()) item.addProperty("nbt", nbt);
            result.add("item", item);
        } else {
            result.addProperty("id", "");
            result.addProperty("count", count);
            String baseItem = TaczRecipeCodec.defaultExternalBaseItem(next);
            if (!baseItem.isBlank()) result.addProperty("base_item", baseItem);
        }
        setOptionalString(result, "group", group);
        if (!"custom".equals(next) && !nbt.isBlank()) result.addProperty("nbt", nbt);
        record.setResult(result);
        refreshEditorWidgets();
    }

    private void selectResult() {
        if (minecraft == null) return;
        ItemSearchIndex.prepareCache(() -> {
            if (minecraft == null) return;
            primeResultSelectorForTacz();
            minecraft.setScreen(new ItemSelectorScreen(this, selection -> {
                if (selection == null || !selection.isItem()) {
                    clientMessage("msg.taczworkshop.result_requires_item");
                    return;
                }
                if (applySelectedResult(selection.stack()) && minecraft != null) {
                    minecraft.execute(() -> {
                        if (minecraft.screen == this) refreshEditorWidgets();
                    });
                }
            }));
        });
    }

    private boolean applySelectedResult(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        int count = resultCount();
        String group = getString(record.result(), "group");
        record.setResult(TaczRecipeCodec.resultFromStack(stack, count, group));
        return !record.resultId().isBlank();
    }

    private static void primeResultSelectorForTacz() {
        setItemSelectorStaticField("rememberedMode", 0);
        setItemSelectorStaticField("rememberedFilterValue", null);
        setItemSelectorStaticField("rememberedFilterType", 0);
        boolean categoryReady = setItemSelectorStaticField("rememberedCategoryKey", "mod:tacz");
        setItemSelectorStaticField("rememberedSearch", categoryReady ? "" : "@tacz");
    }

    private static boolean setItemSelectorStaticField(String name, Object value) {
        try {
            Field field = ItemSelectorScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private void selectMaterial(int index) {
        if (minecraft == null) return;
        minecraft.setScreen(new ItemSelectorScreen(this, selection -> {
            TaczMaterial material;
            if (selection.isItem()) material = new TaczMaterial(TaczStackUtil.ingredientFromStack(selection.stack()), 1);
            else if (selection.isTag()) material = new TaczMaterial(TaczStackUtil.ingredientFromTag(selection.value()), 1);
            else {
                clientMessage("msg.taczworkshop.material_requires_item_or_tag");
                return;
            }

            if (index >= 0 && index < record.materials().size()) {
                material.setCount(record.materials().get(index).count());
                record.materials().set(index, material);
                selectedMaterial = index;
            } else if (record.materials().size() < 64) {
                record.materials().add(material);
                selectedMaterial = record.materials().size() - 1;
            }
            updateMaterialScroll();
            int selectedRow = selectedMaterial < 0 ? 0 : selectedMaterial / MATERIAL_COLS;
            if (selectedRow >= materialScroll.offset() + MATERIAL_ROWS) materialScroll.setOffset(selectedRow - MATERIAL_ROWS + 1);
            refreshEditorWidgets();
        }));
    }

    private void editMaterialNbt(int index) {
        if (index < 0 || index >= record.materials().size() || minecraft == null) return;
        TaczMaterial material = record.materials().get(index);
        JsonObject ingredient = ingredientObject(material);
        if (ingredient == null || !ingredient.has("item")) return;
        String initial = ingredient.has("nbt") ? TaczRecipeCodec.nbtString(ingredient.get("nbt")) : "";
        minecraft.setScreen(new NbtEditorScreen(initial, value -> {
            if (value == null || value.isBlank()) {
                ingredient.remove("nbt");
                if ("forge:partial_nbt".equals(getString(ingredient, "type"))) ingredient.remove("type");
            } else {
                ingredient.addProperty("type", "forge:partial_nbt");
                ingredient.addProperty("nbt", value);
            }
        }, this));
    }

    private void editResultNbt() {
        if (minecraft == null) return;
        JsonObject target = record.result();
        if ("custom".equals(record.resultType())) {
            if (!record.result().has("item") || !record.result().get("item").isJsonObject()) record.result().add("item", new JsonObject());
            target = record.result().getAsJsonObject("item");
        }
        JsonObject finalTarget = target;
        String initial = finalTarget.has("nbt") ? TaczRecipeCodec.nbtString(finalTarget.get("nbt")) : "";
        minecraft.setScreen(new NbtEditorScreen(initial, value -> {
            if (value == null || value.isBlank()) finalTarget.remove("nbt");
            else finalTarget.addProperty("nbt", value);
        }, this));
    }

    private void selectAttachment(String type) {
        if (minecraft == null) return;
        minecraft.setScreen(new ItemSelectorScreen(this, selection -> {
            if (!selection.isItem()) {
                clientMessage("msg.taczworkshop.attachment_requires_item");
                return;
            }
            String id = TaczStackUtil.attachmentId(selection.stack());
            if (id.isBlank()) {
                clientMessage("msg.taczworkshop.attachment_wrong_type");
                return;
            }
            attachments().addProperty(type, id);
            refreshEditorWidgets();
        }));
    }

    private void clearAttachment(String type) {
        attachments().remove(type);
        refreshEditorWidgets();
    }

    private JsonObject attachments() {
        if (!record.result().has("attachments") || !record.result().get("attachments").isJsonObject()) record.result().add("attachments", new JsonObject());
        return record.result().getAsJsonObject("attachments");
    }

    private void removeMaterial(int index) {
        if (index < 0 || index >= record.materials().size()) return;
        record.materials().remove(index);
        selectedMaterial = record.materials().isEmpty() ? -1 : Math.min(index, record.materials().size() - 1);
        updateMaterialScroll();
        refreshEditorWidgets();
    }

    private void save() {
        TaczRecipeNetwork.saveRecord(record.copy());
        commitDraft();
        if (record.isOriginal() && minecraft != null) minecraft.setScreen(parent);
    }

    private void refreshEditorWidgets() {
        int offset = materialScroll.offset();
        clearWidgets();
        buildUi();
        materialScroll.setOffset(offset);
    }

    private String currentResultNbt() {
        if ("custom".equals(record.resultType())) {
            if (record.result().has("item") && record.result().get("item").isJsonObject()) {
                JsonObject item = record.result().getAsJsonObject("item");
                return item.has("nbt") ? TaczRecipeCodec.nbtString(item.get("nbt")) : "";
            }
            return "";
        }
        return record.result().has("nbt") ? TaczRecipeCodec.nbtString(record.result().get("nbt")) : "";
    }

    private int resultCount() {
        if ("custom".equals(record.resultType())) {
            if (record.result().has("item") && record.result().get("item").isJsonObject()) return getInt(record.result().getAsJsonObject("item"), "count", 1);
            return 1;
        }
        return getInt(record.result(), "count", 1);
    }

    private void setResultCount(int count) {
        int safe = Math.max(1, count);
        if ("custom".equals(record.resultType())) {
            if (!record.result().has("item") || !record.result().get("item").isJsonObject()) record.result().add("item", new JsonObject());
            record.result().getAsJsonObject("item").addProperty("count", safe);
        } else record.result().addProperty("count", safe);
    }

    private Component resultTypeComponent() {
        return Component.translatable("gui.taczworkshop.type.short." + record.resultType());
    }

    private Component attachmentComponent(String type, JsonObject attachments) {
        String id = getString(attachments, type);
        Component name = Component.translatable("gui.taczworkshop.attachment." + type);
        if (id.isBlank()) return name;
        return Component.literal(shorten(name.getString() + ": " + id, 24));
    }

    private String materialLabel(TaczMaterial material) {
        JsonElement ingredient = material.ingredient();
        if (ingredient != null && ingredient.isJsonObject()) {
            JsonObject object = ingredient.getAsJsonObject();
            if (object.has("tag")) return "#" + object.get("tag").getAsString();
            if (object.has("item")) return object.get("item").getAsString();
        }
        return shorten(ingredient == null ? "" : ingredient.toString(), 34);
    }

    private static JsonObject ingredientObject(TaczMaterial material) {
        if (material == null || material.ingredient() == null || !material.ingredient().isJsonObject()) return null;
        return material.ingredient().getAsJsonObject();
    }

    private void renderWorkbenchSlot(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredWorkbench = GuiTheme.hovering(mouseX, mouseY, WORKBENCH_SLOT_X, WORKBENCH_SLOT_Y, SLOT_SIZE, SLOT_SIZE);
        GuiTheme.itemSlot(graphics, WORKBENCH_SLOT_X, WORKBENCH_SLOT_Y, hoveredWorkbench);
        ItemStack stack = workbenchPreviewStack();
        if (!stack.isEmpty()) GuiTheme.item(graphics, font, stack, WORKBENCH_SLOT_X, WORKBENCH_SLOT_Y, SLOT_SIZE, 1.0F, false);
        if (record.workbenches().size() > 1) {
            graphics.drawString(font, Integer.toString(record.workbenches().size()), WORKBENCH_SLOT_X + 13, WORKBENCH_SLOT_Y + 10, 0xFFFFFFFF, true);
        }
    }

    private ItemStack workbenchPreviewStack() {
        if (record.workbenches().isEmpty()) return ModItems.GUN_SMITH_TABLE.get().getDefaultInstance();
        ResourceLocation id = ResourceLocation.tryParse(record.workbenches().get(0));
        if (id == null) return ModItems.GUN_SMITH_TABLE.get().getDefaultInstance();
        return TimelessAPI.getCommonBlockIndex(id).map(index -> {
            try {
                return BlockItemBuilder.create(index.getBlock()).setId(id).build();
            } catch (Exception ignored) {
                return ItemStack.EMPTY;
            }
        }).orElseGet(() -> ModItems.GUN_SMITH_TABLE.get().getDefaultInstance());
    }

    private void openWorkbenchSelector() {
        if (minecraft == null) return;
        minecraft.setScreen(new TaczWorkbenchSelectorScreen(this, record.workbenches(), selected -> {
            record.setWorkbenches(selected);
            refreshEditorWidgets();
        }));
    }

    private void addButton(int x, int y, int w, int h, Component text, String tooltipKey, Runnable action) {
        Button button = Button.builder(text, ignored -> action.run()).bounds(x, y, w, h).build();
        button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        addRenderableWidget(button);
    }


    private void clientMessage(String key) {
        if (minecraft != null && minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable(key), false);
    }

    @Override
    public void onClose() {
        if (parent instanceof TaczRecipeListScreen list) list.refreshFromState();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private static int parseCount(String value, int fallback) {
        return Math.max(1, parseInt(value, fallback));
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key)) return fallback;
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || !object.has(key)) return "";
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void setOptionalString(JsonObject object, String key, String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank()) object.remove(key);
        else object.addProperty(key, clean);
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(1, max - 2)) + "..";
    }
}
