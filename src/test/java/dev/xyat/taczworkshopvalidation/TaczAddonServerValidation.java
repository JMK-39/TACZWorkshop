package dev.xyat.taczworkshopvalidation;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import dev.xyat.taczworkshop.data.TaczDataKind;
import dev.xyat.taczworkshop.data.TaczDataOverride;
import dev.xyat.taczworkshop.server.TaczBaseDataCache;
import dev.xyat.taczworkshop.server.TaczDataRuntime;
import dev.xyat.taczworkshop.server.TaczDataStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/** Dedicated-server fixture: real TACZ addon JAR, Workshop persistence, reload and ammo hooks. */
public final class TaczAddonServerValidation {
    private static final Logger LOG = LoggerFactory.getLogger(TaczAddonServerValidation.class);
    private static Map<TaczDataKind, Map<String, TaczDataOverride>> previous;
    private static int phase, ticks, wait;
    private static int oldAmmo, oldRpm;
    private static float oldDamage;
    private static boolean finished;

    public static void serverStarted(ServerStartedEvent event) {
        KineticServerEvents.onTick(KineticEventPriority.NORMAL, KineticServerEvents.TickPhase.END,
                TaczAddonServerValidation::tick);
    }

    private static void tick(MinecraftServer server) {
        if (finished) return;
        try {
            if (++ticks > 1200) throw new AssertionError("TACZ addon server validation timed out");
            if (++wait < 40 || !TaczDataRuntime.canStartBatchReload()) return;
            require(KineticPlatform.isModLoaded("taczaddon"), "Correct TACZ addon is missing");
            require(!KineticPlatform.isModLoaded("sometaczaddon"), "Wrong-target addon must not be used in this test");
            if (phase == 0) {
                previous = TaczDataStore.snapshot();
                GunData gun = gunData();
                oldAmmo = gun.getAmmoAmount(); oldRpm = gun.getRoundsPerMinute();
                oldDamage = gun.getBulletData().getDamageAmount();
                JsonObject detail = TaczBaseDataCache.detailSnapshot(TaczDataKind.GUN, "tacz:ak47");
                JsonObject data = detail.getAsJsonObject("data").deepCopy();
                data.addProperty("ammo_amount", 50); data.addProperty("rpm", 720);
                data.getAsJsonObject("bullet").addProperty("damage", 37);
                TaczDataStore.upsert(new TaczDataOverride(TaczDataKind.GUN, "tacz:ak47",
                        detail.get("data_id").getAsString(), false, data));
                TaczDataRuntime.reloadManagedData(server, false);
                phase = 1; wait = 0;
            } else if (phase == 1) {
                GunData gun = gunData();
                require(gun.getAmmoAmount() == 50 && gun.getRoundsPerMinute() == 720
                        && Math.abs(gun.getBulletData().getDamageAmount() - 37) < 0.001,
                        "Workshop data override did not survive actual /reload with TACZ addon");
                require(TaczDataStore.get(TaczDataKind.GUN, "tacz:ak47").data().get("ammo_amount").getAsInt() == 50,
                        "Override was not persisted");
                var player = FakePlayerFactory.get(server.overworld(), new GameProfile(
                        UUID.fromString("9f766b32-fc33-4fa7-8a35-339faef2a020"), "TaczCompatFixture"));
                player.getInventory().clearContent();
                ItemStack stack = GunItemBuilder.create().setId(KineticResourceIds.parse("tacz:ak47")).forceBuild();
                AbstractGunItem gunItem = (AbstractGunItem) stack.getItem();
                gunItem.setCurrentAmmoCount(stack, 0);
                ModernKineticGunScriptAPI script = new ModernKineticGunScriptAPI();
                script.setShooter(player); script.setItemStack(stack);
                require(script.isReloadingNeedConsumeAmmo(), "Fixture gun must consume actual reload ammo");
                require(!gunItem.canReload(player, stack), "Reload without ammo must fail");
                require(!script.hasAmmoToConsume(), "Script ammo check without ammo must fail");
                player.getInventory().setItem(0, AmmoItemBuilder.create().setId(gun.getAmmoId()).setCount(7).build());
                require(gunItem.canReload(player, stack), "TACZ addon reload hook lost inventory ammo");
                require(script.hasAmmoToConsume(), "TACZ addon script hook lost inventory ammo");
                require(script.consumeAmmoFromPlayer(3) == 3 && player.getInventory().getItem(0).getCount() == 4,
                        "Actual ammo consumption must debit exactly three items");
                require(script.consumeAmmoFromPlayer(99) == 4 && player.getInventory().getItem(0).isEmpty(),
                        "Ammo consumption must clamp to remaining ammo without duplication");
                require(!script.hasAmmoToConsume(), "Script check must observe exhausted ammo");
                if (Boolean.getBoolean("taczworkshop.taczAddonServerValidation.backpack")) {
                    require(KineticPlatform.isModLoaded("sophisticatedbackpacks"), "Backpack fixture dependency is missing");
                    ItemStack backpack = new ItemStack(java.util.Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(
                            KineticResourceIds.parse("sophisticatedbackpacks:backpack"))));
                    new BackpackWrapper(backpack).getInventoryHandler().setStackInSlot(0,
                            AmmoItemBuilder.create().setId(gun.getAmmoId()).setCount(11).build());
                    player.getInventory().setItem(1, backpack);
                    require(gunItem.canReload(player, stack) && script.hasAmmoToConsume(),
                            "Backpack-only ammo must permit reload and script checks");
                    require(script.consumeAmmoFromPlayer(4) == 4
                                    && new BackpackWrapper(backpack).getInventoryHandler().getStackInSlot(0).getCount() == 7,
                            "Backpack-only consumption must persist exact debit");
                    require(script.consumeAmmoFromPlayer(99) == 7
                                    && new BackpackWrapper(backpack).getInventoryHandler().getStackInSlot(0).isEmpty(),
                            "Backpack-only consumption must clamp without duplication");
                    require(!gunItem.canReload(player, stack) && !script.hasAmmoToConsume(),
                            "Exhausted backpack must not report ammo");
                    LOG.info("TACZ_ADDON_BACKPACK_PASS backpack-only checks, exact/clamped consumption and persisted contents");
                }
                require(server.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING).stream()
                                .anyMatch(recipe -> recipe.getClass().getName().startsWith("com.mafuyu404.taczaddon.")),
                        "TACZ addon attachment-pack smithing recipes were not loaded");
                LOG.info("TACZ_ADDON_SERVER_CHECK taczaddon=1.1.8.3 data damage=37 rpm=720 magazine=50; real reload/script ammo checks and exact/clamped consumption; addon smithing recipes");
                player.getInventory().clearContent();
                TaczDataStore.replaceAll(previous);
                TaczDataRuntime.reloadManagedData(server, false);
                phase = 2; wait = 0;
            } else {
                GunData gun = gunData();
                require(gun.getAmmoAmount() == oldAmmo && gun.getRoundsPerMinute() == oldRpm
                        && Math.abs(gun.getBulletData().getDamageAmount() - oldDamage) < 0.001,
                        "Original TACZ data was not restored");
                finished = true;
                LOG.info("TACZ_ADDON_SERVER_PASS correct target, production data persistence/reload, addon ammo hooks, smithing recipes and restoration");
                server.halt(false);
            }
        } catch (Throwable error) {
            finished = true;
            try { if (previous != null) TaczDataStore.replaceAll(previous); }
            catch (Throwable restore) { error.addSuppressed(restore); }
            LOG.error("TACZ_ADDON_SERVER_FAIL phase=" + phase, error);
            server.halt(false);
        }
    }

    private static GunData gunData() {
        return TimelessAPI.getCommonGunIndex(KineticResourceIds.parse("tacz:ak47")).orElseThrow().getGunData();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
