package dev.xyat.taczworkshopvalidation;
import com.google.gson.*;
import dev.xyat.taczworkshop.client.*;
import dev.xyat.taczworkshop.client.gui.*;
import dev.xyat.taczworkshop.data.*;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import org.slf4j.*;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
/** Opt-in screenshots of unsaved samples; never clicks Save or changes inventory. */
public final class GuiLongTextValidation {
 private static final Logger LOG=LoggerFactory.getLogger(GuiLongTextValidation.class);
 private static final String ROOT=System.getProperty("taczworkshop.guiValidation.output","D:/IDEAWork/TACZWorkshop/.worktrees/gui-long-text-20261004/.gradle/gui-long-text-20261004/screenshots");
 private static final String LONG_NAME="A deliberately long representative name for bounded text ".repeat(4);
 private static final String[] NAMES={"recipes-empty","recipes","recipe-invalid","editor-custom-empty","editor-custom","editor-gun","editor-attachment","editor-ammo","editor-melee","editor-throwable","editor-consumable","workbench-empty","workbenches","data-empty","data","detail-active","detail-modified","detail-removed","editor-invalid"};
 private static boolean installed,started,screenshot,finished,originalFullscreen,prepared;
 private static String originalLanguage;
 private static int originalScale,originalWidth,originalHeight,phase=-1,page=-1,captures,failures;
 private static long due;
 private static CompletableFuture<Void> reload;
 private static Language stressOriginal;
 private static KineticPage fixturePage;
 private static Field mouseXField,mouseYField;
 private static double originalMouseX,originalMouseY;
    public static void install() {
        if (installed) return;
        installed = true;
        KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, GuiLongTextValidation::tick);
    }

    private static void tick() {
        if (finished) return;
        try {
            var mc = Minecraft.getInstance();
            if (fixturePage != null && KineticGui.currentPage() == fixturePage) suppressCachedMouse();
            if (!started) {
                if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
                started = true;
                originalLanguage = mc.getLanguageManager().getSelected();
                originalScale = mc.options.guiScale().get();
                originalWidth = mc.getWindow().getWidth();
                originalHeight = mc.getWindow().getHeight();
                originalFullscreen = mc.getWindow().isFullscreen();
                mc.options.guiScale().set(0);
                if (originalFullscreen) mc.getWindow().toggleFullScreen();
                nextPhase();
                return;
            }
            if (reload != null) {
                if (!reload.isDone() || mc.getOverlay() != null) return;
                reload.join();
                reload = null;
                enterLoadedPhase();
                return;
            }
            long now = System.currentTimeMillis();
            if (!screenshot && !prepared && now >= due - 300) {
                // Initial read-only server replies can replace these tabs' lists. Restore only the local sample.
                preparePage(page);
                prepared = true;
                due = now + 300;
                return;
            }
            if (!screenshot && now >= due) {
                capture("start");
                screenshot = true;
                due = now + (phase == 4 ? 3400 : 550);
                return;
            }
            if (screenshot && now >= due) {
                if (phase == 4) capture("scroll");
                nextPage();
            }
        } catch (Throwable error) {
            failures++;
            LOG.error("TACZ_GUI_FAIL phase=" + phase + " page=" + page, error);
            finish();
        }
    }

    private static void nextPhase() throws Exception {
        if (stressOriginal != null) {
            Language.inject(stressOriginal);
            stressOriginal = null;
        }
        phase++;
        page = -1;
        String selectedPhases = System.getProperty("taczworkshop.guiValidation.phases", "");
        while (phase < 5 && !selectedPhases.isBlank()
                && !List.of(selectedPhases.split(",")).contains(String.valueOf(phase))) phase++;
        if (phase >= 5) { finish(); return; }
        var mc = Minecraft.getInstance();
        fixturePage = null;
        mc.setScreen(null);
        String lang = phase == 2 || phase == 3 ? "zh_cn" : "en_us";
        boolean languageChanged = !lang.equals(mc.getLanguageManager().getSelected());
        mc.getLanguageManager().setSelected(lang);
        mc.options.languageCode = lang;
        int width = phase == 1 || phase == 3 ? 1920 : 854;
        int height = phase == 1 || phase == 3 ? 1080 : 480;
        mc.getWindow().setWindowed(width, height);
        mc.resizeDisplay();
        LOG.info("TACZ_GUI_PHASE phase={} language={} requested={}x{} autoScale=true resourceReload={}",
                phase, lang, width, height, languageChanged);
        if (languageChanged) reload = mc.reloadResourcePacks();
        else enterLoadedPhase();
    }

    private static void enterLoadedPhase() throws Exception {
        if (phase == 4) {
            stressOriginal = Language.getInstance();
            Language.inject(new StressLanguage(stressOriginal));
        }
        nextPage();
    }

    private static void nextPage() throws Exception {
        page++;
        String selectedPages = System.getProperty("taczworkshop.guiValidation.pages." + phase,
                System.getProperty("taczworkshop.guiValidation.pages", ""));
        while (page < NAMES.length && !selectedPages.isBlank()
                && !List.of(selectedPages.split(",")).contains(String.valueOf(page))) page++;
        if (page >= NAMES.length) { nextPhase(); return; }
        openPage(page);
        fixturePage = KineticGui.currentPage();
        suppressCachedMouse();
        screenshot = false;
        prepared = false;
        due = System.currentTimeMillis() + 1100;
        LOG.info("TACZ_GUI_OPEN phase={} index={} case={} page={}", phase, page, NAMES[page],
                KineticGui.currentPage().getClass().getName());
    }

 private static TaczRecipeRecord sample(String type, boolean populated) {
  TaczRecipeRecord r=TaczRecipeRecord.blank(type);r.setId("validation:a_deliberately_long_recipe_identifier_for_clipping");r.setComment(LONG_NAME);
  if(populated){JsonObject ingredient=new JsonObject();ingredient.addProperty("item","minecraft:diamond_sword");ingredient.addProperty("nbt","{display:{Name:'{\"text\":\"A very long colored material name\",\"color\":\"aqua\"}'}}");r.materials().add(new TaczMaterial(ingredient,32));}
  if(type.equals("custom")){JsonObject item=r.result().getAsJsonObject("item");item.addProperty("item","minecraft:diamond_sword");item.addProperty("nbt","{display:{Name:'{\"text\":\"A very long colored result name\",\"color\":\"aqua\"}'}}");}
  else r.result().addProperty("id","tacz:ak47");r.result().addProperty("group",LONG_NAME);return r;
 }
 private static void openPage(int i) throws Exception {
  KineticPage p;
  if(i<3){p=new TaczRecipeListPage();List<TaczRecipeRecord> source=(List<TaczRecipeRecord>)field(p,"source");source.clear();if(i>0)source.add(sample("custom",true));if(i==2)source.get(0).setIssue("tacz_deserialize_failed",LONG_NAME,new JsonObject());}
  else if(i==18){TaczRecipeRecord record=sample("custom",true);record.setIssue("tacz_deserialize_failed",LONG_NAME,new JsonObject());p=new TaczRecipeEditorPage(null,record);setField(p,"selectedMaterial",0);}
  else if(i<=10){String type=List.of("custom","custom","gun","attachment","ammo","melee","throwable","consumable").get(i-3);p=new TaczRecipeEditorPage(null,sample(type,i!=3));if(i!=3)setField(p,"selectedMaterial",0);}
  else if(i<=12){p=new TaczWorkbenchSelectorPage(List.of(),value->{});if(i==11)((List<?>)field(p,"source")).clear();}
  else if(i<=14){p=new TaczDataManagerPage();}
  else {JsonObject d=new JsonObject();d.addProperty("kind","gun");d.addProperty("id","tacz:ak47");d.addProperty("data_id","validation:a_deliberately_long_data_identifier_for_clipping");d.addProperty("modified",i==16);d.addProperty("removed",i==17);JsonObject index=new JsonObject();index.addProperty("name","item.minecraft.diamond_sword");d.add("index",index);JsonObject data=new JsonObject();data.addProperty("damage",12);data.addProperty("a_very_long_field_identifier_for_layout",LONG_NAME);data.addProperty("a_very_long_boolean_field_identifier_for_layout",true);d.add("data",data);p=new TaczDataDetailPage(d);}
  KineticGui.open(p);fixturePage=p;
 }
 private static void preparePage(int i)throws Exception{
  if(i<3){List<TaczRecipeRecord> source=(List<TaczRecipeRecord>)field(fixturePage,"source");source.clear();if(i>0)source.add(sample("custom",true));if(i==2)source.get(0).setIssue("tacz_deserialize_failed",LONG_NAME,new JsonObject());invoke(fixturePage,"rebuildFiltered");}
  if(i==13||i==14){List<TaczDataListEntry> source=(List<TaczDataListEntry>)field(fixturePage,"source");source.clear();if(i==14)source.add(new TaczDataListEntry(TaczDataKind.GUN,"tacz:ak47","validation:long_data_identifier", "item.minecraft.diamond_sword","gun",true,false,new JsonObject()));invoke(fixturePage,"rebuildFiltered");}
 }
    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) try {
            var f = type.getDeclaredField(name); f.setAccessible(true); f.set(target, value); return;
        } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }

    private static void suppressCachedMouse() throws Exception {
        var mouse = Minecraft.getInstance().mouseHandler;
        if (mouseXField == null) {
            // Exact official/SRG names verified in this worktree's build/createMcpToSrg/output.tsrg.
            mouseXField = mouseCoordinateField(mouse, "xpos", "f_91507_");
            mouseYField = mouseCoordinateField(mouse, "ypos", "f_91508_");
            originalMouseX = mouseXField.getDouble(mouse);
            originalMouseY = mouseYField.getDouble(mouse);
            LOG.info("TACZ_GUI_MOUSE cachedCoordinatesOnly=true fields={},{}", mouseXField.getName(), mouseYField.getName());
        }
        if (Boolean.getBoolean("taczworkshop.guiValidation.hover") && fixturePage != null && List.of(1,2,12,14).contains(page)) {
            var mc = Minecraft.getInstance();
            double sx=((Number)invoke(mc.screen,"canvasX")).doubleValue();
            double sy=((Number)invoke(mc.screen,"canvasY")).doubleValue();
            double scale=((Number)invoke(mc.screen,"canvasScale")).doubleValue();
            double px=((Number)field(fixturePage,"GRID_X")).doubleValue()+8;
            double py=((Number)field(fixturePage,"GRID_Y")).doubleValue()+8;
            mouseXField.setDouble(mouse,(sx+px*scale)*mc.getWindow().getWidth()/mc.getWindow().getGuiScaledWidth());
            mouseYField.setDouble(mouse,(sy+py*scale)*mc.getWindow().getHeight()/mc.getWindow().getGuiScaledHeight());
        } else {
            mouseXField.setDouble(mouse, -1000.0D);
            mouseYField.setDouble(mouse, -1000.0D);
        }
    }

    private static Field mouseCoordinateField(Object mouse, String official, String srg) throws Exception {
        for (String name : List.of(official, srg)) try {
            Field coordinate = mouse.getClass().getDeclaredField(name);
            if (coordinate.getType() != double.class) throw new IllegalStateException("Unexpected mouse coordinate field type: " + name);
            coordinate.setAccessible(true);
            return coordinate;
        } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(official + " / " + srg);
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) try {
            var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(target);
        } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) for (var m : type.getDeclaredMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            m.setAccessible(true); return m.invoke(target, args);
        }
        throw new NoSuchMethodException(name);
    }

    private static void capture(String frame) throws Exception {
        var mc = Minecraft.getInstance();
        suppressCachedMouse();
        var current = KineticGui.currentPage();
        if (current != fixturePage) throw new AssertionError("Expected fixture page, got " + current);
        Path path = Path.of(ROOT, String.format("%d-%02d-%s-%s.png", phase, page, NAMES[page], frame));
        Files.createDirectories(path.getParent());
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(path); }
        captures++;
        LOG.info("TACZ_GUI_CAPTURE phase={} index={} case={} frame={} image={}x{}", phase, page, NAMES[page], frame,
                mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    private static void finish() {
        finished = true;
        var mc = Minecraft.getInstance();
        fixturePage = null;
        if (mouseXField != null && mouseYField != null) try {
            mouseXField.setDouble(mc.mouseHandler, originalMouseX);
            mouseYField.setDouble(mc.mouseHandler, originalMouseY);
        } catch (IllegalAccessException error) {
            failures++;
            LOG.error("TACZ_GUI_MOUSE_RESTORE_FAIL", error);
        }
        if (stressOriginal != null) { Language.inject(stressOriginal); stressOriginal = null; }
        if (started) {
            mc.options.guiScale().set(originalScale);
            mc.getLanguageManager().setSelected(originalLanguage);
            mc.options.languageCode = originalLanguage;
            mc.setScreen(null);
            mc.getWindow().setWindowed(originalWidth, originalHeight);
            if (originalFullscreen && !mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
        }
        LOG.info("TACZ_GUI_{} pages={} captures={} failures={} userSettingsRestored=true", failures == 0 ? "PASS" : "FAIL",
                NAMES.length, captures, failures);
        KineticClientRuntime.stopClient();
    }

    private static final class StressLanguage extends Language {
        private final Language delegate;
        StressLanguage(Language delegate) { this.delegate = delegate; }
        @Override public String getOrDefault(String key, String fallback) {
            String text = delegate.getOrDefault(key, fallback);
            return key.startsWith("gui.taczworkshop.") || key.equals("item.minecraft.diamond_sword") || key.equals("block.minecraft.chest")
                    ? text + " - deliberately extended translation to verify text stays inside its own region" : text;
        }
        @Override public boolean has(String key) { return delegate.has(key); }
        @Override public boolean isDefaultRightToLeft() { return delegate.isDefaultRightToLeft(); }
        @Override public net.minecraft.util.FormattedCharSequence getVisualOrder(net.minecraft.network.chat.FormattedText text) {
            return delegate.getVisualOrder(text);
        }
    }
}
