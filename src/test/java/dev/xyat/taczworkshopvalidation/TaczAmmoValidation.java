package dev.xyat.taczworkshopvalidation;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.gameplay.ClientAmmoCount;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.taczworkshop.compat.ClientAmmoTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage;
import net.minecraftforge.registries.ForgeRegistries;

/** Client-only inventory substitutions are restored in the same tick, never sent to the server. */
final class TaczAmmoValidation {
    static void checkClient() throws Exception {
        var player = Minecraft.getInstance().player;
        var inventory = player.getInventory();
        var saved = new java.util.ArrayList<ItemStack>();
        for (int i = 0; i < inventory.getContainerSize(); i++) saved.add(inventory.getItem(i));
        boolean creative = player.getAbilities().instabuild;
        var info = Minecraft.getInstance().getConnection().getPlayerInfo(player.getUUID());
        if (info == null) throw new AssertionError("Local player info is not loaded");
        var originalMode = info.getGameMode();
        var mode = java.util.Arrays.stream(info.getClass().getDeclaredFields())
                .filter(field -> field.getType() == net.minecraft.world.level.GameType.class).findFirst().orElseThrow();
        mode.setAccessible(true);
        java.util.UUID backpackId = null;
        var refresh = ClientAmmoTickEvent.class.getDeclaredMethod("storageBackpack");
        refresh.setAccessible(true);
        try {
            inventory.clearContent();
            player.getAbilities().instabuild = false;
            mode.set(info, net.minecraft.world.level.GameType.SURVIVAL);
            ItemStack gun = GunItemBuilder.create().setId(KineticResourceIds.parse("tacz:ak47")).forceBuild();
            inventory.offhand.set(0, ammo(7));
            if (!((IAmmo) inventory.offhand.get(0).getItem()).isAmmoOfGun(gun, inventory.offhand.get(0)))
                throw new AssertionError("Fixture ammo must match actual gun");
            refresh.invoke(null);
            int count = ClientAmmoCount.get(player, gun, (IGun) gun.getItem());
            if (count != 7) throw new AssertionError("Native offhand-only ammo count: expected=7 actual=" + count);
            ItemStack box = new ItemStack(ModItems.AMMO_BOX.get());
            IAmmoBox boxItem = (IAmmoBox) box.getItem();
            boxItem.setAmmoId(box, KineticResourceIds.parse("tacz:762x39"));
            boxItem.setAmmoCount(box, 11);
            inventory.offhand.set(0, box);
            refresh.invoke(null);
            expect(ClientAmmoCount.get(player, gun, (IGun) gun.getItem()) == 11, "offhand ammo box count");
            ItemStack bag = new ItemStack(java.util.Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(KineticResourceIds.parse("sophisticatedbackpacks:backpack"))));
            var wrapper = new BackpackWrapper(bag);
            wrapper.getInventoryHandler().setStackInSlot(0, ammo(5));
            backpackId = wrapper.getContentsUuid().orElseThrow();
            inventory.setItem(0, ammo(3));
            inventory.setItem(8, bag);
            inventory.armor.set(0, ammo(2));
            refresh.invoke(null);
            expect(ClientAmmoCount.get(player, gun, (IGun) gun.getItem()) == 21, "main/armor/offhand/backpack each counted once");
            ((IGun) gun.getItem()).setDummyAmmoAmount(gun, 4);
            expect(ClientAmmoCount.get(player, gun, (IGun) gun.getItem()) == 4, "native dummy count is preserved");
            player.getAbilities().instabuild = true;
            mode.set(info, net.minecraft.world.level.GameType.CREATIVE);
            expect(ClientAmmoCount.get(player, gun, (IGun) gun.getItem()) == ClientAmmoCount.MAX_DISPLAY_COUNT, "native creative count is preserved");
            org.slf4j.LoggerFactory.getLogger(TaczAmmoValidation.class).info("TACZ_AMMO_CLIENT_PASS offhand=7 box=11 combined=21 dummy=4 creative=9999");
        } finally {
            for (int i = 0; i < saved.size(); i++) inventory.setItem(i, saved.get(i));
            player.getAbilities().instabuild = creative;
            mode.set(info, originalMode);
            refresh.invoke(null);
            if (backpackId != null) BackpackStorage.get().removeBackpackContents(backpackId);
        }
    }

    static void checkServer(FakePlayer player, ItemStack bag) {
        ItemStack gun = GunItemBuilder.create().setId(KineticResourceIds.parse("tacz:ak47")).forceBuild();
        AbstractGunItem item = (AbstractGunItem) gun.getItem();
        item.setCurrentAmmoCount(gun, 0);
        ModernKineticGunScriptAPI script = new ModernKineticGunScriptAPI();
        script.setShooter(player); script.setItemStack(gun);
        player.getInventory().clearContent();
        player.getInventory().offhand.set(0, ammo(7));
        expect(item.canReload(player, gun) && script.hasAmmoToConsume(), "server offhand-only reload availability");
        expect(script.consumeAmmoFromPlayer(3) == 3 && player.getInventory().offhand.get(0).getCount() == 4, "server offhand exact debit");
        player.getInventory().clearContent();
        var contents = new BackpackWrapper(bag).getInventoryHandler();
        contents.setStackInSlot(0, ammo(7));
        player.getInventory().setItem(8, bag);
        expect(item.canReload(player, gun) && script.hasAmmoToConsume(), "server backpack-only reload availability");
        int used = script.consumeAmmoFromPlayer(3);
        int remaining = new BackpackWrapper(bag).getInventoryHandler().getStackInSlot(0).getCount();
        expect(used == 3 && remaining == 4, "server backpack exact debit: used=" + used + " remaining=" + remaining);
        int rest = script.consumeAmmoFromPlayer(99);
        expect(rest == 4 && new BackpackWrapper(bag).getInventoryHandler().getStackInSlot(0).isEmpty(), "server backpack clamps at remaining ammo: used=" + rest);
        expect(!script.hasAmmoToConsume(), "server cannot reuse consumed ammo");
    }

    private static ItemStack ammo(int count) {
        return AmmoItemBuilder.create().setId(KineticResourceIds.parse("tacz:762x39")).setCount(count).build();
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
