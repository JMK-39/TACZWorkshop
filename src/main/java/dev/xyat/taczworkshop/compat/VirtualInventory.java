package dev.xyat.taczworkshop.compat;

import com.google.common.collect.ImmutableList;
import dev.xyat.taczworkshop.mixin.compat.InventoryAccessor;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class VirtualInventory extends Inventory {
    private final int size;

    public VirtualInventory(int size, Player player) {
        super(Objects.requireNonNull(player));
        this.size = Math.max(0, size);
        ((InventoryAccessor) this).taczworkshop_tacz$setItems(NonNullList.withSize(this.size, ItemStack.EMPTY));
        ((InventoryAccessor) this).taczworkshop_tacz$setCompartments(ImmutableList.of(this.items, this.armor, this.offhand));
    }

    public IItemHandler getHandler() {
        return new ReadMostlyItemHandler(this);
    }

    private static final class ReadMostlyItemHandler implements IItemHandler {
        private final VirtualInventory inventory;

        private ReadMostlyItemHandler(VirtualInventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public int getSlots() {
            return inventory.size;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return inventory.getItem(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack current = inventory.getItem(slot);
            if (current.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            int extracted = Math.min(amount, current.getCount());
            ItemStack result = current.copy();
            result.setCount(extracted);
            if (!simulate) current.shrink(extracted);
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return false;
        }
    }
}
