package dev.xyat.taczworkshop.crafting;

import net.minecraft.resources.ResourceLocation;

/** Client screen endpoint; packets are accepted only by the currently open matching menu and recipe. */
public interface WorkbenchMaterialView {
    void workshop$materials(int menuId, ResourceLocation recipe, int[] counts, boolean craftable);
}
