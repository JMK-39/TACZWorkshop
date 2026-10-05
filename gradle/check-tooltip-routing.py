"""Execute actual page tooltip methods against a recording host, without Minecraft.

The renderer bodies are read from production on each run. Only their model,
translation and page-host boundaries are substituted. This checks addon routing
and intact logical styled lines; Core screen/cursor fitting is a runtime check.
Requires Python 3 and a JDK (JAVA_HOME or --java-home). Writes only build/.
"""
from pathlib import Path
import argparse
import os
import subprocess

root = Path(__file__).resolve().parents[1]
pages = root / 'src/main/java/dev/xyat/taczworkshop/client/gui'


def method(page, name):
    source = (pages / (page + '.java')).read_text(encoding='utf-8')
    import re
    match = re.search(r'(?:protected|private)\s+[^\n]+\b' + name + r'\([^\n]*\)\s*\{', source)
    if not match:
        raise AssertionError('Production method missing: ' + page + '.' + name)
    start, cursor, depth = match.start(), match.end(), 1
    while depth:
        char = source[cursor]
        depth += (char == '{') - (char == '}')
        cursor += 1
    return source[start:cursor]


fixture = r'''
import java.util.*;
public class TooltipRoutingCheck {
    record Component(String text, String color) {
        static Component literal(String text) { return new Component(text, "plain"); }
        String getString() { return text; }
    }
    record FormattedCharSequence(Component component) { }
    static class KineticText {
        static List<FormattedCharSequence> wrap(Component c, int width) {
            return List.of(new FormattedCharSequence(c));
        }
    }
    static class KineticI18n {
        static Component translatable(String key, Object... args) {
            return Component.literal(key + (args.length == 0 ? "" : Arrays.toString(args)));
        }
    }
    record ItemStack(boolean empty) {
        boolean isEmpty() { return empty; }
        Component getHoverName() { return new Component("Workbench name", "gold"); }
    }
    record TaczMaterial(int count) { }
    static class TaczRecipeCodec {
        static ItemStack materialPreview(TaczMaterial m) { return new ItemStack(false); }
    }
    static class Recipe {
        boolean issue;
        boolean enabled = true;
        String issueDetail = "BrokenDetailWithoutSpaces";
        List<String> benches = List.of("sample:bench", "sample:other");
        List<TaczMaterial> materials() { return List.of(new TaczMaterial(5)); }
        List<String> workbenches() { return benches; }
        String resultId() { return "sample:result"; }
        String id() { return "sample:recipe"; }
        String uuid() { return "recipe-uuid"; }
        boolean hasIssue() { return issue; }
        String issueDetail() { return issueDetail; }
        boolean enabled() { return enabled; }
    }
    record TaczDataListEntry(String id, String dataId) { }
    record Leaf(String displayPath) { }
    enum TaczDataKind { GUN, ATTACHMENT }
    static class TaczDataStackUtil {
        static ItemStack build(TaczDataKind kind, String id) { return new ItemStack(false); }
    }
    static class Host {
        boolean blocked;
        List<Component> captured;
        boolean fitting;
        int width;
        Recipe record = new Recipe(), hoveredRecord;
        Map<String, ItemStack> previewCache = new HashMap<>();
        TaczDataListEntry hoveredEntry;
        int hoveredMaterialIndex = -1;
        boolean hoveredWorkbench, hoveredResult, hoveredAttachmentSummary, hoveredHeaderItem;
        Leaf hoveredLeaf;
        TaczDataKind kind = TaczDataKind.GUN;
        String id = "sample:gun", dataId = "sample:gun_data";
        boolean resourceAvailable = true, emptyWorkbench;
        static final List<String> ATTACHMENT_TYPES = List.of("scope", "muzzle", "stock", "grip", "laser", "extended_mag");
        boolean overlayBlocksInput() { return blocked; }
        void showTooltip(List<Component> lines, int width) {
            captured = List.copyOf(lines); fitting = true; this.width = width;
        }
        void showFormattedTooltip(List<FormattedCharSequence> lines) {
            captured = lines.stream().map(FormattedCharSequence::component).toList();
            fitting = false;
        }
        Component materialDisplayName(TaczMaterial m, ItemStack s) { return new Component("Material name", "gold"); }
        String materialLabel(TaczMaterial m) { return "sample:material"; }
        String materialNbtText(TaczMaterial m, ItemStack s) { return "MaterialNBTWithoutSpaces"; }
        ItemStack workbenchPreviewStack() { return new ItemStack(emptyWorkbench); }
        ItemStack resultPreviewStack() { return new ItemStack(false); }
        Component resultDisplayName(ItemStack s) { return new Component("Result name", "aqua"); }
        String resultNbtText(ItemStack s) { return "ResultNBTWithoutSpaces"; }
        Component issueComponent(Recipe r) { return new Component("Broken recipe", "red"); }
        TaczDataListEntry dataEntry(Recipe r) { return new TaczDataListEntry("sample:result", "sample:data"); }
        ItemStack recipePreview(Recipe r, TaczDataListEntry e) { return new ItemStack(false); }
        Component localizedResultName(Recipe r, TaczDataListEntry e, ItemStack s) { return new Component("Recipe result", "gold"); }
        String originKey(Recipe r) { return "gui.taczworkshop.origin.fixture"; }
        ItemStack stackFor(TaczDataListEntry e) { return new ItemStack(false); }
        Component hoverName(TaczDataListEntry e, ItemStack s) { return new Component("Data item", "gold"); }
        Component statusLine(TaczDataListEntry e) { return new Component("Changed status", "green"); }
        Component fieldLabel(String path) { return new Component("Field label", "aqua"); }
        Component displayName(ItemStack s) { return new Component("Header item", "gold"); }
        Set<String> attachmentTypes() { return Set.of("scope", "laser"); }
        protected void renderTooltips(int x, int y) { }
    }
    static class Editor extends Host { __EDITOR__ }
    static class Recipes extends Host { __RECIPES__ }
    static class Manager extends Host { __MANAGER__ }
    static class Detail extends Host { __DETAIL__ __ATTACHMENTS__ }
    static Component p(String text) { return Component.literal(text); }
    static Component s(String text, String color) { return new Component(text, color); }
    static List<String> failures = new ArrayList<>();
    static int checks;
    static void check(String name, Host page, int width, Component... expected) {
        page.renderTooltips(0, 0); checks++;
        if (!Objects.equals(Arrays.asList(expected), page.captured)) {
            failures.add(name + ": logical lines/styles/order changed: " + page.captured); return;
        }
        if (!page.fitting || page.width != width) {
            failures.add(name + ": bypasses screen-fitting host (expected raw Components, preferred " + width + ")");
        }
    }
    static void absent(String name, Host page) {
        page.renderTooltips(0, 0); checks++;
        if (page.captured != null) failures.add(name + ": tooltip unexpectedly shown");
    }
    public static void main(String[] args) {
        Editor material = new Editor(); material.hoveredMaterialIndex = 0;
        check("editor/material", material, 320, s("Material name", "gold"), p("sample:material"),
            p("gui.taczworkshop.material.count[5]"), p("gui.taczworkshop.item_nbt.value[MaterialNBTWithoutSpaces]"),
            p("gui.taczworkshop.material.click_hint"));
        Editor bench = new Editor(); bench.hoveredWorkbench = true;
        check("editor/workbench", bench, 280, p("gui.taczworkshop.workbench.button"), s("Workbench name", "gold"),
            p("sample:bench"), p("gui.taczworkshop.workbench.summary[2]"));
        Editor nativeBench = new Editor(); nativeBench.hoveredWorkbench = true; nativeBench.emptyWorkbench = true;
        nativeBench.record.benches = List.of();
        check("editor/native-workbench", nativeBench, 280, p("gui.taczworkshop.workbench.button"), p("gui.taczworkshop.workbench.native"));
        Editor result = new Editor(); result.hoveredResult = true;
        check("editor/result", result, 320, s("Result name", "aqua"), p("sample:result"),
            p("gui.taczworkshop.item_nbt.value[ResultNBTWithoutSpaces]"), p("gui.taczworkshop.result.click_hint"));
        Recipes normal = new Recipes(); normal.hoveredRecord = new Recipe();
        check("recipes/normal", normal, 320, s("Recipe result", "gold"), p("sample:result"), p("sample:recipe"),
            p("gui.taczworkshop.origin.fixture"), p("gui.taczworkshop.enabled"), p("gui.taczworkshop.workbench.tooltip[2]"),
            p("gui.taczworkshop.recipe.grid.tooltip"));
        Recipes error = new Recipes(); error.hoveredRecord = new Recipe(); error.hoveredRecord.issue = true;
        check("recipes/error", error, 320, p("gui.taczworkshop.recipe.error.badge"), p("sample:recipe"), s("Broken recipe", "red"),
            p("BrokenDetailWithoutSpaces"), p("gui.taczworkshop.recipe.grid.tooltip"));
        Manager manager = new Manager(); manager.hoveredEntry = new TaczDataListEntry("sample:gun", "sample:gun_data");
        check("manager/item", manager, 320, s("Data item", "gold"), p("gui.taczworkshop.data.tooltip.item_id[sample:gun]"),
            p("gui.taczworkshop.data.tooltip.data_id[sample:gun_data]"), s("Changed status", "green"),
            p("tip.taczworkshop.data.open_entry"), p("tip.taczworkshop.data.context_entry"));
        Detail field = new Detail(); field.hoveredLeaf = new Leaf("gun.longFieldPath");
        check("detail/field", field, 320, s("Field label", "aqua"), p("gui.taczworkshop.data.tooltip.field_path[gun.longFieldPath]"));
        Detail attachments = new Detail(); attachments.hoveredAttachmentSummary = true;
        List<Component> attachmentLines = new ArrayList<>();
        attachmentLines.add(p("gui.taczworkshop.data.slot.summary.title"));
        for (String type : List.of("scope", "muzzle", "stock", "grip", "laser", "extended_mag")) {
            attachmentLines.add(p("gui.taczworkshop.data.slot.summary.line[" + p("gui.taczworkshop.attachment." + type) + ", "
                + p("gui.taczworkshop.data.boolean." + (type.equals("scope") || type.equals("laser") ? "true" : "false")) + "]"));
        }
        attachmentLines.add(p("tip.taczworkshop.data.slot.context"));
        check("detail/attachments", attachments, 320, attachmentLines.toArray(Component[]::new));
        Detail header = new Detail(); header.hoveredHeaderItem = true;
        check("detail/header", header, 320, s("Header item", "gold"), p("gui.taczworkshop.data.tooltip.item_id[sample:gun]"),
            p("gui.taczworkshop.data.tooltip.data_id[sample:gun_data]"));
        Detail missing = new Detail(); missing.hoveredHeaderItem = true; missing.resourceAvailable = false; missing.dataId = "";
        check("detail/missing-header", missing, 320, p("gui.taczworkshop.data.resource_missing"), p("gui.taczworkshop.data.tooltip.item_id[sample:gun]"));
        for (Host page : List.of(new Editor(), new Recipes(), new Manager(), new Detail())) {
            page.blocked = true; page.hoveredMaterialIndex = 0; page.hoveredRecord = new Recipe();
            page.hoveredEntry = new TaczDataListEntry("sample:gun", "data"); page.hoveredHeaderItem = true;
            absent("overlay/" + page.getClass().getSimpleName(), page);
        }
        absent("empty/editor", new Editor()); absent("empty/recipes", new Recipes());
        absent("empty/manager", new Manager()); absent("empty/detail", new Detail());
        failures.forEach(System.err::println);
        if (!failures.isEmpty()) throw new AssertionError(failures.size() + " tooltip routing regression(s)");
        System.out.println("PASS: " + checks + " actual renderer cases; all 8 host paths use raw styled Components, original line order and 280/320 preferences; overlay/empty suppression retained");
    }
}
'''
for token, page in [('EDITOR', 'TaczRecipeEditorPage'), ('RECIPES', 'TaczRecipeListPage'),
                    ('MANAGER', 'TaczDataManagerPage'), ('DETAIL', 'TaczDataDetailPage')]:
    fixture = fixture.replace('__' + token + '__', method(page, 'renderTooltips'))
fixture = fixture.replace('__ATTACHMENTS__', method('TaczDataDetailPage', 'attachmentSummaryTooltip'))

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
args = parser.parse_args()
java_bin = Path(args.java_home) / 'bin' if args.java_home else Path('')
out = root / 'build/tooltip-routing-check'
out.mkdir(parents=True, exist_ok=True)
target = out / 'TooltipRoutingCheck.java'
target.write_text(fixture, encoding='utf-8')
subprocess.run([str(java_bin / 'javac'), '-encoding', 'UTF-8', str(target)], check=True)
subprocess.run([str(java_bin / 'java'), '-cp', str(out), 'TooltipRoutingCheck'], check=True)
