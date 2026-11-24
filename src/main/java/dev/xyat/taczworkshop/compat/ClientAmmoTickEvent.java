package dev.xyat.taczworkshop.compat;

import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.taczworkshop.compat.sophisticated.SophisticatedBackpacksCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;

public final class ClientAmmoTickEvent {
    public static VirtualInventory virtualInventory;
    private static boolean backpackSynced;
    private static boolean registered;

    private ClientAmmoTickEvent() {
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        KineticClientEvents.onTick(KineticClientEvents.TickPhase.START, ClientAmmoTickEvent::storageBackpack);
    }

    private static void storageBackpack() {
        Player player = KineticClientRuntime.localPlayer();
        if (player == null) {
            virtualInventory = null;
            backpackSynced = false;
            return;
        }
        if (!backpackSynced) {
            SophisticatedBackpacksCompat.syncAllBackpack(player);
            backpackSynced = true;
        }
        ArrayList<ItemStack> items = SophisticatedBackpacksCompat.getItemsFromInventoryBackpack(player);
        // Native 1.2.0 gameplay counts every inventory compartment, including offhand and armor.
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            items.add(player.getInventory().getItem(slot));
        VirtualInventory inventory = new VirtualInventory(items.size(), player);
        for (int i = 0; i < items.size(); i++) inventory.setItem(i, items.get(i));
        virtualInventory = inventory;
    }
}
