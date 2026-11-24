package dev.xyat.taczworkshopvalidation;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.taczworkshop.crafting.WorkbenchMaterialView;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.Arrays;

/** Read-only original TaCZ screen receiving the real production clientbound packet. */
public final class WorkbenchClientValidation {
    private static GunSmithTableScreen screen;
    private static int frames;
    private static boolean done;
    private WorkbenchClientValidation() {}
    public static void install() { KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, WorkbenchClientValidation::tick); }
    private static void tick() {
        if (done || !WorkbenchServerValidation.clientRequested) return;
        var mc = Minecraft.getInstance();
        try {
            if (screen == null) {
                if (mc.player == null || mc.level == null || mc.screen != null) return;
                require(mc.getSingleplayerServer() != null, "Client fixture requires an integrated server");
                require(mc.getWindow().getWidth() == 1920 && mc.getWindow().getHeight() == 1080, "Launch the original pack at 1920x1080");
                TaczAmmoValidation.checkClient();
                screen = new GunSmithTableScreen(new GunSmithTableMenu(9182, mc.player.getInventory(), DefaultAssets.DEFAULT_BLOCK_ID), mc.player.getInventory(), Component.literal("Workbench material validation"));
                require(screen instanceof WorkbenchMaterialView, "Production screen mixin did not load");
                mc.setScreen(screen);
                // A real result-button click keeps TaCZ's tab, held-item filter and repeated init() consistent.
                var selected = GunSmithTableScreen.class.getDeclaredField("selectedRecipe"); selected.setAccessible(true);
                GunSmithTableRecipe recipe = null;
                for (int index = 0; index < 6; index++) {
                    var buttons = screen.children().stream().filter(child -> child.getClass().getSimpleName().equals("ResultButton"))
                            .map(AbstractWidget.class::cast).toList();
                    if (index >= buttons.size()) break;
                    var button = buttons.get(index);
                    double x = button.getX() + button.getWidth() / 2.0, y = button.getY() + button.getHeight() / 2.0;
                    require(screen.mouseClicked(x, y, 0), "Visible TaCZ recipe button did not handle click"); screen.mouseReleased(x, y, 0);
                    recipe = (GunSmithTableRecipe) selected.get(screen);
                    if (recipe != null && !recipe.getInputs().isEmpty()) break;
                }
                require(recipe != null && !recipe.getInputs().isEmpty(), "No visible selectable TaCZ recipe with inputs; selected=" + selected.get(screen));
                WorkbenchServerValidation.clientRecipe = recipe.getId();
                WorkbenchServerValidation.clientInputCount = recipe.getInputs().size();
                int[] diagnostic = new int[recipe.getInputs().size()]; Arrays.fill(diagnostic, 7);
                var view = (WorkbenchMaterialView) screen;
                view.workshop$materials(screen.getMenu().containerId + 1, recipe.getId(), diagnostic, false);
                require(!allCounts(7), "Production material endpoint accepted a different menu");
                view.workshop$materials(screen.getMenu().containerId, recipe.getId(), diagnostic, false);
                require(allCounts(7), "Production material endpoint rejected matching menu/recipe: " + state());
                WorkbenchServerValidation.clientState = "selected by real button=" + recipe.getId() + " inputs=" + recipe.getInputs().size() + " " + state();
                WorkbenchServerValidation.clientReady = true; return;
            }
            require(mc.screen == screen, "Validation screen was unexpectedly replaced");
            if (!WorkbenchServerValidation.clientSent) return;
            var field = GunSmithTableScreen.class.getDeclaredField("playerIngredientCount"); field.setAccessible(true);
            var counts = (Int2IntArrayMap) field.get(screen);
            var selected = GunSmithTableScreen.class.getDeclaredField("selectedRecipe"); selected.setAccessible(true);
            var recipe = (GunSmithTableRecipe) selected.get(screen);
            WorkbenchServerValidation.clientState = "expected=" + WorkbenchServerValidation.clientRecipe + " selected="
                    + (recipe == null ? "none" : recipe.getId()) + " counts=" + counts + " " + state()
                    + " server=" + WorkbenchServerValidation.serverClientState;
            require(recipe != null && recipe.getId().equals(WorkbenchServerValidation.clientRecipe), "Fixture selected recipe was replaced: " + WorkbenchServerValidation.clientState);
            if (counts == null || counts.size() != recipe.getInputs().size() || counts.values().intStream().anyMatch(value -> value != 123)) return;
            // Capture after one rendered frame; the normal 500ms refresh must not be treated as ten stable ticks.
            if (++frames < 2) return;
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(WorkbenchServerValidation.clientOutput.resolve("tacz-workbench-materials-1080.png"));
            }
            mc.setScreen(null); WorkbenchServerValidation.clientPassed = true; done = true;
        } catch (Throwable failure) {
            if (mc.screen == screen) mc.setScreen(null);
            WorkbenchServerValidation.clientFailure = failure.toString(); done = true;
        }
    }
    private static boolean allCounts(int expected) throws Exception {
        var field = GunSmithTableScreen.class.getDeclaredField("playerIngredientCount"); field.setAccessible(true);
        var counts = (Int2IntArrayMap) field.get(screen);
        return counts != null && counts.size() == WorkbenchServerValidation.clientInputCount
                && counts.values().intStream().allMatch(value -> value == expected);
    }
    private static String state() throws Exception {
        Field recipe = GunSmithTableScreen.class.getDeclaredField("workshop$recipe"); recipe.setAccessible(true);
        Field counts = GunSmithTableScreen.class.getDeclaredField("workshop$counts"); counts.setAccessible(true);
        return "screenMenu=" + screen.getMenu().containerId + " cachedRecipe=" + recipe.get(screen)
                + " cachedCounts=" + Arrays.toString((int[]) counts.get(screen));
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
