package dev.xyat.taczworkshop.crafting;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Holds actual extraction receipts until the crafted output exists. Closing an uncommitted debit refunds it. */
public final class MaterialDebit implements AutoCloseable {
    private record Receipt(MaterialStock stock, ItemStack stack) {}
    private final List<Receipt> receipts = new ArrayList<>();
    private final Consumer<ItemStack> recovery;
    private boolean committed;
    private MaterialDebit(Consumer<ItemStack> recovery) { this.recovery = recovery; }

    public static MaterialDebit withdraw(List<MaterialStock> stocks, long[] amounts, Consumer<ItemStack> recovery) {
        var debit = new MaterialDebit(recovery);
        try {
            // Every planned source is simulated before any source is changed.
            for (int i = 0; i < stocks.size(); i++) {
                MaterialStock stock = stocks.get(i);
                for (long left = amounts[i]; left > 0;) {
                    int count = (int) Math.min(left, Math.max(1, stock.template().getMaxStackSize()));
                    if (!exact(stock.template(), stock.access().extract(count, true), count)) return null;
                    left -= count;
                }
            }
            // Inputs arrive in RS -> player -> backpack order; allocation already accounted for all overlaps.
            for (int i = 0; i < stocks.size(); i++) {
                MaterialStock stock = stocks.get(i);
                for (long left = amounts[i]; left > 0;) {
                    int count = (int) Math.min(left, Math.max(1, stock.template().getMaxStackSize()));
                    ItemStack actual = stock.access().extract(count, false);
                    if (!actual.isEmpty()) debit.receipts.add(new Receipt(stock, actual.copy()));
                    if (!exact(stock.template(), actual, count)) { debit.close(); return null; }
                    left -= count;
                }
            }
            return debit;
        } catch (RuntimeException failure) {
            debit.close();
            dev.xyat.taczworkshop.TaczWorkshop.LOGGER.warn("Workbench extraction aborted and refunded", failure);
            return null;
        }
    }

    private static boolean exact(ItemStack expected, ItemStack actual, int count) {
        return !actual.isEmpty() && actual.getCount() == count && ItemStack.isSameItemSameTags(expected, actual);
    }
    public void commit() { committed = true; }
    @Override public void close() {
        if (committed) return;
        committed = true;
        for (int i = receipts.size() - 1; i >= 0; i--) {
            Receipt receipt = receipts.get(i);
            ItemStack remainder;
            try { remainder = receipt.stock.access().restore(receipt.stack.copy()); }
            catch (RuntimeException failure) { remainder = receipt.stack.copy(); }
            if (remainder != null && !remainder.isEmpty()) recovery.accept(remainder);
        }
    }
}
