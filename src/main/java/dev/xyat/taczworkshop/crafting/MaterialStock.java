package dev.xyat.taczworkshop.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** A concrete slot or exact RS item variant, never a shared mutable preview stack. */
public record MaterialStock(ItemStack template, long count, int priority, Access access) {
    public interface Access {
        ItemStack extract(int amount, boolean simulate);
        ItemStack restore(ItemStack stack);
    }
    public static MaterialStock slot(IItemHandler handler, int slot, int priority) {
        ItemStack stack = handler.getStackInSlot(slot).copy();
        return new MaterialStock(stack, stack.getCount(), priority, new Access() {
            public ItemStack extract(int amount, boolean simulate) { return handler.extractItem(slot, amount, simulate); }
            public ItemStack restore(ItemStack value) { return handler.insertItem(slot, value, false); }
        });
    }
}
