package dev.xyat.taczworkshop.mixin.compat;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(Inventory.class)
public interface InventoryAccessor {
    @Accessor("items")
    @Mutable
    void taczworkshop_tacz$setItems(NonNullList<ItemStack> items);

    @Accessor("compartments")
    @Mutable
    void taczworkshop_tacz$setCompartments(List<NonNullList<ItemStack>> compartments);
}
