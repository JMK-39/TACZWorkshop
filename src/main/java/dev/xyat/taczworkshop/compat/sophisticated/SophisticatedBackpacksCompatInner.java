package dev.xyat.taczworkshop.compat.sophisticated;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackContentsMessage;
import net.p3pp3rf1y.sophisticatedbackpacks.network.RequestBackpackInventoryContentsMessage;
import net.p3pp3rf1y.sophisticatedbackpacks.network.SBPPacketHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageStackData;
import net.p3pp3rf1y.sophisticatedcore.network.ItemStackReference;

import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class SophisticatedBackpacksCompatInner {
    private static Player clientOwner;
    private static Map<UUID, ItemStackReference> requestedSlots = Map.of();

    public static List<IItemHandler> workbenchInventories(Player player) {
        List<IItemHandler> result = new ArrayList<>();
        for (ItemStack backpack : getAllInventoryBackpack(player)) {
            result.add(new BackpackWrapper(backpack).getInventoryHandler());
        }
        return result;
    }

    public static void syncWorkbench(ServerPlayer player) {
        if (player instanceof net.minecraftforge.common.util.FakePlayer) return;
        for (ItemStack backpack : getAllInventoryBackpack(player)) {
            new BackpackWrapper(backpack).getContentsUuid().ifPresent(uuid -> sendInventory(player, uuid));
        }
    }

    private static void appendInventory(BackpackWrapper backpack, List<ItemStack> items) {
        InventoryHandler handler = backpack.getInventoryHandler();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) items.add(stack);
        }
    }

    public static ArrayList<ItemStack> getItemsFromInventoryBackpack(Player player) {
        ArrayList<ItemStack> items = new ArrayList<>();
        for (ItemStack backpack : getAllInventoryBackpack(player)) appendInventory(new BackpackWrapper(backpack), items);
        return items;
    }

    public static ArrayList<ItemStack> getClientItems(Player player) {
        if (clientOwner != player) {
            requestedSlots = Map.of();
            clientOwner = player;
        }
        Map<UUID, ItemStackReference> carried = new HashMap<>();
        ArrayList<ItemStack> items = new ArrayList<>();
        PlayerInventoryProvider.get().runOnBackpacks(player, (stack, inventoryName, identifier, index) -> {
            if (LinkedStorageStackData.getEndpoint(stack) != null) return false;
            var wrapper = new BackpackWrapper(stack);
            UUID uuid = wrapper.getContentsUuid().orElse(null);
            if (uuid == null || carried.containsKey(uuid)) return false;
            var slot = new ItemStackReference(ItemStackReference.EQUIPMENT_HANDLER, index, inventoryName, identifier);
            carried.put(uuid, slot);
            if (!slot.equals(requestedSlots.get(uuid)))
                SBPPacketHandler.INSTANCE.sendToServer(new RequestBackpackInventoryContentsMessage(uuid, slot));
            appendInventory(wrapper, items);
            return false;
        });
        requestedSlots = carried;
        return items;
    }

    public static void clearClientRequests() {
        clientOwner = null;
        requestedSlots = Map.of();
    }

    private static void sendInventory(ServerPlayer player, UUID uuid) {
        if (player instanceof net.minecraftforge.common.util.FakePlayer) return;
        BackpackStorage.get().getBackpackContents(uuid).ifPresent(contents -> {
            CompoundTag snapshot = new CompoundTag();
            for (String key : List.of("inventory", "upgradeInventory")) {
                var value = contents.get(key);
                if (value != null) snapshot.put(key, value.copy());
            }
            SBPPacketHandler.INSTANCE.sendToClient(player, new BackpackContentsMessage(uuid, snapshot));
        });
    }

    public static void modifyInventoryBackpack(ServerPlayer player, ItemStack backpackItem, Consumer<IItemHandler> action) {
        PlayerInventoryProvider.get().runOnBackpacks(player, (backpack, inventoryName, identifier, index) -> {
            if (backpack != backpackItem || LinkedStorageStackData.getEndpoint(backpack) != null) return false;
            var wrapper = new BackpackWrapper(backpack);
            var inventory = wrapper.getInventoryHandler();
            action.accept(inventory);
            // TaCZ changes ammo-box NBT in place. Give the native handler a chance to persist those slots too.
            for (int i = 0; i < inventory.getSlots(); i++) inventory.onContentsChanged(i);
            wrapper.getContentsUuid().ifPresent(uuid -> sendInventory(player, uuid));
            return true;
        });
    }

    public static ArrayList<ItemStack> getAllInventoryBackpack(Player player) {
        ArrayList<ItemStack> items = new ArrayList<>();
        var seen = new HashSet<UUID>();
        PlayerInventoryProvider.get().runOnBackpacks(player, (backpack, inventoryName, identifier, index) -> {
            if (LinkedStorageStackData.getEndpoint(backpack) == null
                    && new BackpackWrapper(backpack).getContentsUuid().filter(seen::add).isPresent()) items.add(backpack);
            return false;
        });
        return items;
    }
}
