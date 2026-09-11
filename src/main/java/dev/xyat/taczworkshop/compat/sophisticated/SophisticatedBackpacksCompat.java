package dev.xyat.taczworkshop.compat.sophisticated;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.function.Consumer;

public final class SophisticatedBackpacksCompat {
    private static final boolean INSTALLED = ModList.get().isLoaded("sophisticatedbackpacks");

    private SophisticatedBackpacksCompat() {
    }

    public static ArrayList<ItemStack> getItemsFromInventoryBackpack(Player player) {
        return INSTALLED ? SophisticatedBackpacksCompatInner.getItemsFromInventoryBackpack(player) : new ArrayList<>();
    }

    public static void syncAllBackpack(Player player) {
        if (INSTALLED) SophisticatedBackpacksCompatInner.syncAllBackpack(player);
    }

    public static void modifyInventoryBackpack(ServerPlayer player, ItemStack backpackItem, Consumer<IItemHandler> action) {
        if (INSTALLED) SophisticatedBackpacksCompatInner.modifyInventoryBackpack(player, backpackItem, action);
    }

    public static ArrayList<ItemStack> getAllInventoryBackpack(Player player) {
        return INSTALLED ? SophisticatedBackpacksCompatInner.getAllInventoryBackpack(player) : new ArrayList<>();
    }
}
