package dev.xyat.taczworkshop.crafting;

import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import dev.xyat.taczworkshop.compat.sophisticated.SophisticatedBackpacksCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.*;

public final class WorkbenchMaterials {
    private static final String REFUNDS = "TaczWorkshopMaterialRefunds";
    public record Preview(int[] counts, boolean craftable) {}
    private record Snapshot(List<MaterialStock> stocks, long[] needs, long[] allocation, int[] counts) {}
    private WorkbenchMaterials() {}

    public static boolean valid(ServerPlayer player, GunSmithTableMenu menu) {
        if (player.containerMenu != menu || !player.isAlive()) return false;
        GunSmithTableBlockEntity table = ((WorkbenchMenuContext) menu).workshop$table();
        // Virtual TaCZ menus retain inventory/backpack crafting, but can never access RS.
        return table == null || table.getLevel() == player.level() && !table.isRemoved()
                && player.level().hasChunkAt(table.getBlockPos()) && player.level().getBlockEntity(table.getBlockPos()) == table
                && player.distanceToSqr(table.getBlockPos().getX() + .5, table.getBlockPos().getY() + .5, table.getBlockPos().getZ() + .5) <= 64;
    }

    public static Preview preview(ServerPlayer player, GunSmithTableMenu menu, GunSmithTableRecipe recipe) {
        if (!valid(player, menu) || recipe == null || recipe.getInputs().size() > 256) return new Preview(new int[0], false);
        Snapshot snapshot = snapshot(player, menu, recipe);
        return new Preview(snapshot.counts, player.isCreative() || snapshot.allocation != null);
    }

    public static boolean craft(ServerPlayer player, GunSmithTableMenu menu, GunSmithTableRecipe recipe) {
        if (!valid(player, menu) || recipe == null || recipe.getInputs().size() > 256 || !player.server.isSameThread()) return false;
        ItemStack output = recipe.getResultItem(player.level().registryAccess()).copy();
        if (output.isEmpty()) return false;
        if (player.isCreative()) return output(player, output);
        Snapshot snapshot = snapshot(player, menu, recipe);
        if (snapshot.allocation == null) return false;
        try (MaterialDebit debit = MaterialDebit.withdraw(snapshot.stocks, snapshot.allocation, stack -> refund(player, stack))) {
            if (debit == null || !output(player, output)) return false;
            debit.commit();
            return true;
        } finally {
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            SophisticatedBackpacksCompat.syncWorkbench(player);
        }
    }

    private static boolean output(ServerPlayer player, ItemStack output) {
        var entity = new ItemEntity(player.level(), player.getX(), player.getY() + .5, player.getZ(), output);
        entity.setPickUpDelay(0);
        boolean added = false;
        try { added = player.level().addFreshEntity(entity); return added; }
        finally { if (!added) entity.discard(); }
    }

    private static Snapshot snapshot(ServerPlayer player, GunSmithTableMenu menu, GunSmithTableRecipe recipe) {
        List<MaterialStock> stocks = new ArrayList<>(RefinedStorageMaterials.adjacent(player, ((WorkbenchMenuContext) menu).workshop$table()));
        var inventory = new InvWrapper(player.getInventory());
        for (int i = 0; i < inventory.getSlots(); i++) stocks.add(MaterialStock.slot(inventory, i, 1));
        for (var handler : SophisticatedBackpacksCompat.workbenchInventories(player)) {
            for (int i = 0; i < handler.getSlots(); i++) stocks.add(MaterialStock.slot(handler, i, 2));
        }
        int size = recipe.getInputs().size();
        long[] needs = new long[size];
        int[] counts = new int[size];
        for (int j = 0; j < size; j++) needs[j] = recipe.getInputs().get(j).getCount();
        List<MaterialStock> matching = new ArrayList<>();
        List<MaterialPlanner.Stock> candidates = new ArrayList<>();
        for (MaterialStock stock : stocks) {
            if (stock.template().isEmpty() || stock.count() <= 0) continue;
            boolean[] matches = new boolean[size]; boolean any = false;
            for (int j = 0; j < size; j++) {
                matches[j] = recipe.getInputs().get(j).getIngredient().test(stock.template());
                if (matches[j]) { any = true; counts[j] = (int) Math.min(Integer.MAX_VALUE, counts[j] + stock.count()); }
            }
            if (any) { matching.add(stock); candidates.add(new MaterialPlanner.Stock(stock.count(), stock.priority(), matches)); }
        }
        return new Snapshot(matching, needs, MaterialPlanner.allocate(needs, candidates), counts);
    }

    private static void refund(ServerPlayer player, ItemStack stack) {
        ItemStack remainder = stack.copy(); player.getInventory().add(remainder);
        if (remainder.isEmpty()) return;
        CompoundTag data = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        ListTag pending = data.getList(REFUNDS, 10);
        while (!remainder.isEmpty()) pending.add(remainder.split(Math.min(remainder.getCount(), remainder.getMaxStackSize())).save(new CompoundTag()));
        data.put(REFUNDS, pending); player.getPersistentData().put(Player.PERSISTED_NBT_TAG, data);
    }

    /** Refund overflow is saved with the player and retried when space becomes available, rather than dropped/lost. */
    public static void recoverRefunds(ServerPlayer player) {
        CompoundTag data = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        ListTag pending = data.getList(REFUNDS, 10);
        if (pending.isEmpty()) return;
        ListTag remaining = new ListTag();
        for (int i = 0; i < pending.size(); i++) {
            ItemStack stack = ItemStack.of(pending.getCompound(i)); player.getInventory().add(stack);
            if (!stack.isEmpty()) remaining.add(stack.save(new CompoundTag()));
        }
        if (remaining.isEmpty()) data.remove(REFUNDS); else data.put(REFUNDS, remaining);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, data); player.getInventory().setChanged();
    }
}
