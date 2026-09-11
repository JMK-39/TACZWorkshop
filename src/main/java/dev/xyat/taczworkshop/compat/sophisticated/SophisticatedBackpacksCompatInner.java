package dev.xyat.taczworkshop.compat.sophisticated;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext;
import net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackContentsMessage;
import net.p3pp3rf1y.sophisticatedbackpacks.network.RequestBackpackInventoryContentsMessage;
import net.p3pp3rf1y.sophisticatedbackpacks.network.SBPPacketHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;

public class SophisticatedBackpacksCompatInner {

    public static ArrayList<ItemStack> getItemsFromBackpackItem(ItemStack itemStack) {
        ArrayList<ItemStack> items = new ArrayList<>();
        BackpackWrapper backpackWrapper = new BackpackWrapper(itemStack);
        InventoryHandler handler = backpackWrapper.getInventoryHandler();
        int size = itemStack.getTag() != null ? itemStack.getTag().getInt("inventorySlots") : 0;
        for (int i = 0; i < size; i++) {
            items.add(handler.getStackInSlot(i));
        }
        return items;
    }

    public static ArrayList<ItemStack> getItemsFromInventoryBackpack(Player player) {
        ArrayList<ItemStack> items = new ArrayList<>();
        getAllInventoryBackpack(player).forEach(itemStack -> items.addAll(getItemsFromBackpackItem(itemStack)));
        return items;
    }

    public static void syncAllBackpack(Player player) {
        getAllInventoryBackpack(player).forEach(itemStack -> {
            IBackpackWrapper backpackWrapper = itemStack.getCapability(CapabilityBackpackWrapper.getCapabilityInstance()).orElse(IBackpackWrapper.Noop.INSTANCE);
            if (backpackWrapper.getContentsUuid().isPresent()) {
                UUID uuid = backpackWrapper.getContentsUuid().get();
                SBPPacketHandler.INSTANCE.sendToServer(new RequestBackpackInventoryContentsMessage(uuid));
            }
        });
    }

    public static void modifyInventoryBackpack(ServerPlayer player, ItemStack backpackItem, Consumer<IItemHandler> action) {
        PlayerInventoryProvider.get().runOnBackpacks(player, (backpack, inventoryName, identifier, index) -> {
            if (!backpack.equals(backpackItem)) return false;
            BackpackContext.Item backpackContext = new BackpackContext.Item(inventoryName, identifier, index);
            BackpackContainer container = new BackpackContainer(player.containerMenu.containerId + 1, player, backpackContext);
            InventoryHandler inventoryHandler = container.getStorageWrapper().getInventoryHandler();

            action.accept(inventoryHandler);

            int size = container.slots.size() - player.getInventory().items.size();
            for (int i = 0; i < size; i++) {
                container.getSlot(i).set(inventoryHandler.getStackInSlot(i));
            }

            if (container.getStorageWrapper().getContentsUuid().isPresent()) {
                UUID uuid = container.getStorageWrapper().getContentsUuid().get();
                CompoundTag backpackContent = BackpackStorage.get().getOrCreateBackpackContents(uuid);
                SBPPacketHandler.INSTANCE.sendToClient(player, new BackpackContentsMessage(uuid, backpackContent));
            }
            return false;
        });
    }

    public static ArrayList<ItemStack> getAllInventoryBackpack(Player player) {
        ArrayList<ItemStack> items = new ArrayList<>();
        PlayerInventoryProvider.get().runOnBackpacks(player, (backpack, inventoryName, identifier, index) -> {
            items.add(backpack);
            return false;
        });
        return items;
    }
}
