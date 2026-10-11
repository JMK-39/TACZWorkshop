package dev.xyat.taczworkshopvalidation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.network.KineticCompression;
import dev.xyat.kineticcore.api.network.ServerPacketContext;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import dev.xyat.taczworkshop.data.TaczDataKind;
import dev.xyat.taczworkshop.data.TaczDataOverride;
import dev.xyat.taczworkshop.network.TaczDataNetwork;
import dev.xyat.taczworkshop.server.TaczBaseDataCache;
import dev.xyat.taczworkshop.server.TaczDataRuntime;
import dev.xyat.taczworkshop.server.TaczDataStore;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.Map;

/** Real server batch/reload checks in an explicitly enabled, isolated client fixture. */
public final class TaczAddonValidation {
    private static final Logger LOG = LoggerFactory.getLogger(TaczAddonValidation.class);
    private static Map<TaczDataKind, Map<String, TaczDataOverride>> previous;
    private static int phase, ticks, waitTicks, oldAmmo, oldRpm;
    private static float oldDamage;
    private static boolean finished, grantedOperator, installed, busy;
    private static ServerPlayer testPlayer;

    public static void install() {
        if (installed) return;
        installed = true;
        KineticServerEvents.onTick(KineticEventPriority.NORMAL, KineticServerEvents.TickPhase.END, TaczAddonValidation::tick);
    }

    private static void tick(MinecraftServer server) {
        if (finished || busy || server.getPlayerList().getPlayers().isEmpty()) return;
        busy = true;
        try {
            if (++ticks > 6000) throw new IllegalStateException("Runtime validation timeout");
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            testPlayer = player;
            if (++waitTicks < 60 || !TaczDataRuntime.canStartBatchReload()) return;
            if (phase == 0) {
                if (!KineticPlatform.isModLoaded("sometaczaddon")) throw new IllegalStateException("SomeTaczAddon not installed");
                if (!player.hasPermissions(2)) {
                    server.getPlayerList().op(player.getGameProfile());
                    grantedOperator = true;
                }
                require(player.hasPermissions(2), "Isolated test player must have admin permissions");
                verifyMigratedRecipes(server);
                previous = TaczDataStore.snapshot();
                GunData gun = gunData();
                oldAmmo = gun.getAmmoAmount(); oldRpm = gun.getRoundsPerMinute(); oldDamage = gun.getBulletData().getDamageAmount();
                JsonObject detail = TaczBaseDataCache.detailSnapshot(TaczDataKind.GUN, "tacz:ak47");
                JsonObject data = detail.getAsJsonObject("data").deepCopy();
                data.addProperty("ammo_amount", 50);
                data.addProperty("rpm", 720);
                data.getAsJsonObject("bullet").addProperty("damage", 37);
                JsonObject edit = new JsonObject();
                edit.addProperty("kind", "gun"); edit.addProperty("id", "tacz:ak47");
                edit.addProperty("data_id", detail.get("data_id").getAsString());
                edit.addProperty("op", "override"); edit.add("data", data);
                JsonArray edits = new JsonArray(); edits.add(edit);
                JsonObject batch = new JsonObject(); batch.addProperty("batch_id", 101); batch.add("edits", edits);
                var handler = TaczDataNetwork.class.getDeclaredMethod("handleSaveDataBatch", TaczDataNetwork.SaveDataBatchPacket.class, ServerPacketContext.class);
                handler.setAccessible(true);
                handler.invoke(null, new TaczDataNetwork.SaveDataBatchPacket(KineticCompression.compressUtf8(batch.toString(), 4 * 1024 * 1024)), new ServerPacketContext(player));
                var saved = TaczDataStore.get(TaczDataKind.GUN, "tacz:ak47");
                require(saved != null && saved.data().get("ammo_amount").getAsInt() == 50, "Production batch save was rejected");
                phase = 1; waitTicks = 0;
            } else if (phase == 1) {
                GunData gun = gunData();
                require(gun.getAmmoAmount() == 50, "Magazine capacity was not reloaded");
                require(gun.getRoundsPerMinute() == 720, "RPM was not reloaded");
                require(Math.abs(gun.getBulletData().getDamageAmount() - 37) < 0.001, "Damage was not reloaded");
                ItemStack stack = GunItemBuilder.create().setId(KineticResourceIds.parse("tacz:ak47")).forceBuild();
                require(AttachmentDataUtils.getAmmoCountWithAttachment(stack, gun) == 50, "Base magazine changed unexpectedly");
                Object registryObject = Class.forName("dev.cticetofu.sometaczaddon.common.registry.ModEnchantments").getField("BIGGER_MAGAZINE").get(null);
                Enchantment enchantment = (Enchantment) registryObject.getClass().getMethod("get").invoke(registryObject);
                stack.enchant(enchantment, 3);
                int modifiedCapacity = AttachmentDataUtils.getAmmoCountWithAttachment(stack, gun);
                require(modifiedCapacity == 65, "SomeTaczAddon magazine enchantment expected 65, got " + modifiedCapacity);
                verifyMigratedRecipes(server);
                LOG.info("TACZ_DATA_CHECK saved/reloaded damage=37 rpm=720 magazine=50; SomeTaczAddon level-3 magazine=65");
                TaczDataStore.replaceAll(previous);
                TaczDataRuntime.requestDataBatchReload(player, 102, 0);
                phase = 2; waitTicks = 0;
            } else {
                GunData gun = gunData();
                require(gun.getAmmoAmount() == oldAmmo && gun.getRoundsPerMinute() == oldRpm
                        && Math.abs(gun.getBulletData().getDamageAmount() - oldDamage) < 0.001, "Original data was not restored");
                finished = true;
                if (grantedOperator) server.getPlayerList().deop(player.getGameProfile());
                LOG.info("TACZ_DATA_PASS automatic migration, production batch/reload, SomeTaczAddon modifiers and restoration");
                KineticClientRuntime.execute(GuiLongTextValidation::install);
            }
        } catch (Throwable error) {
            finished = true;
            try { if (previous != null) TaczDataStore.replaceAll(previous); }
            catch (Exception restoreError) { error.addSuppressed(restoreError); }
            if (grantedOperator && testPlayer != null) server.getPlayerList().deop(testPlayer.getGameProfile());
            LOG.error("TACZ_DATA_FAIL phase=" + phase, error);
            KineticClientRuntime.execute(() -> Minecraft.getInstance().stop());
        } finally {
            busy = false;
        }
    }

    private static GunData gunData() {
        return TimelessAPI.getCommonGunIndex(KineticResourceIds.parse("tacz:ak47")).orElseThrow().getGunData();
    }

    private static void verifyMigratedRecipes(MinecraftServer server) throws Exception {
        var directory = KineticPlatform.configDirectory().resolve("kineticcore");
        var expectedSource = java.nio.file.Path.of(System.getProperty("taczworkshop.dataValidation.source",
                directory.resolve(".validation/taczrecipes.json").toString()));
        JsonObject original = JsonParser.parseString(Files.readString(expectedSource)).getAsJsonObject();
        JsonObject migrated = JsonParser.parseString(Files.readString(directory.resolve("datapack/data/contentstudio/recipe/recipe_overrides.json"))).getAsJsonObject();
        require(!Files.exists(directory.resolve("taczrecipes.json")), "Legacy migration source was not removed");
        require(!Files.exists(directory.resolve("datapack/data/contentstudio/recipe_overrides.json")), "Overrides remain outside recipe directory");
        for (String obsolete : new String[]{"taczrecipes.json.pre-contentstudio.bak",
                "datapack/data/contentstudio/recipe_overrides.json.pre-taczworkshop.bak",
                "datapack/data/contentstudio/recipe/recipe_bundle.json.pre-taczworkshop.bak"}) {
            require(!Files.exists(directory.resolve(obsolete)), "Unnecessary migration backup remains: " + obsolete);
        }
        JsonObject marker = JsonParser.parseString(Files.readString(directory.resolve("taczrecipes-migrated.json"))).getAsJsonObject();
        require(marker.getAsJsonArray("unusable_records").isEmpty(), "Migration contains unusable records");
        int count = 0, emptyLegacyResults = 0;
        for (JsonElement element : original.getAsJsonArray("recipes")) {
            JsonObject record = element.getAsJsonObject();
            if (record.has("enabled") && !record.get("enabled").getAsBoolean()) continue;
            String id = record.get("id").getAsString();
            JsonObject nativeRecipe = migrated.getAsJsonObject(id);
            require(nativeRecipe != null, "Missing imported recipe " + id);
            require(nativeRecipe.get("materials").equals(record.get("materials")), "Changed materials for " + id);
            require(nativeRecipe.get("result").equals(record.get("result")), "Changed result for " + id);
            var recipe = server.getRecipeManager().byKey(KineticResourceIds.parse(id)).orElseThrow(() -> new IllegalStateException("Recipe was not loaded: " + id));
            // Reconstruct the old Workshop's native conversion to distinguish migration
            // regressions from already-unresolvable IDs in the original gun packs.
            var legacyRecipe = new GunSmithTableRecipe(KineticResourceIds.parse(id),
                    CommonAssetsManager.GSON.fromJson(nativeRecipe, TableRecipe.class));
            legacyRecipe.init();
            ItemStack expected = legacyRecipe.getOutput();
            ItemStack actual = recipe.getResultItem(server.registryAccess());
            require(ItemStack.matches(expected, actual), "Runtime output differs from legacy conversion: " + id
                    + " legacy=" + expected + " migrated=" + actual);
            if (expected.isEmpty()) {
                emptyLegacyResults++;
                if (emptyLegacyResults <= 4) LOG.warn("TACZ_MIGRATION_BASELINE_EMPTY {} result={}", id, record.get("result"));
            }
            count++;
        }
        require(count == 519, "Expected 519 legacy recipes, got " + count);
        LOG.info("TACZ_MIGRATION_CHECK {} recipes loaded with unchanged inputs/results and runtime output matching legacy conversion; {} original outputs already unresolved", count, emptyLegacyResults);
    }

    private static void require(boolean passed, String message) {
        if (!passed) throw new IllegalStateException(message);
    }
}
