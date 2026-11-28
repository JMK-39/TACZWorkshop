package dev.xyat.taczworkshop.compat.sophisticated;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class SophisticatedBackpacksCompat {
    private static final boolean INSTALLED = KineticPlatform.isModLoaded("sophisticatedbackpacks");

    private SophisticatedBackpacksCompat() {
    }

    public static ArrayList<ItemStack> getItemsFromInventoryBackpack(Player player) {
        return INSTALLED ? SophisticatedBackpacksCompatInner.getItemsFromInventoryBackpack(player) : new ArrayList<>();
    }

    public static ArrayList<ItemStack> getClientItems(Player player) {
        return INSTALLED ? SophisticatedBackpacksCompatInner.getClientItems(player) : new ArrayList<>();
    }

    public static void clearClientRequests() {
        if (INSTALLED) SophisticatedBackpacksCompatInner.clearClientRequests();
    }

    public static void modifyInventoryBackpack(ServerPlayer player, ItemStack backpackItem, Consumer<IItemHandler> action) {
        if (INSTALLED) SophisticatedBackpacksCompatInner.modifyInventoryBackpack(player, backpackItem, action);
    }

    public static ArrayList<ItemStack> getAllInventoryBackpack(Player player) {
        return INSTALLED ? SophisticatedBackpacksCompatInner.getAllInventoryBackpack(player) : new ArrayList<>();
    }

    public static List<IItemHandler> workbenchInventories(Player player) {
        return INSTALLED ? SophisticatedBackpacksCompatInner.workbenchInventories(player) : List.of();
    }

    public static void syncWorkbench(ServerPlayer player) {
        if (INSTALLED) SophisticatedBackpacksCompatInner.syncWorkbench(player);
    }
}
