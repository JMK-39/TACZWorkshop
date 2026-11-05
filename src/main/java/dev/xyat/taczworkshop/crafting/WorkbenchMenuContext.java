package dev.xyat.taczworkshop.crafting;

import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;

public interface WorkbenchMenuContext {
    void workshop$bindTable(GunSmithTableBlockEntity table);
    GunSmithTableBlockEntity workshop$table();
    GunSmithTableRecipe workshop$recipe(ResourceLocation id, RecipeManager recipes);
    long workshop$lastQuery();
    void workshop$lastQuery(long tick);
}
