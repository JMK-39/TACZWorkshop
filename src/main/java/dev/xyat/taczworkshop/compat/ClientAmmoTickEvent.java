package dev.xyat.taczworkshop.compat;

import dev.xyat.taczworkshop.TaczWorkshop;
import dev.xyat.taczworkshop.compat.sophisticated.SophisticatedBackpacksCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;

@Mod.EventBusSubscriber(modid = TaczWorkshop.MODID, value = Dist.CLIENT)
public final class ClientAmmoTickEvent {
    public static VirtualInventory virtualInventory;
    private static boolean backpackSynced;

    private ClientAmmoTickEvent() {
    }

    @SubscribeEvent
    public static void storageBackpack(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) return;
        Player player = Minecraft.getInstance().player;
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
        items.addAll(player.getInventory().items);
        VirtualInventory inventory = new VirtualInventory(items.size(), player);
        for (int i = 0; i < items.size(); i++) inventory.setItem(i, items.get(i));
        virtualInventory = inventory;
    }
}
