package dev.xyat.taczworkshop.mixin.compat;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import dev.xyat.taczworkshop.crafting.WorkbenchMaterialView;
import dev.xyat.taczworkshop.network.WorkbenchNetwork;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GunSmithTableScreen.class, remap = false)
public abstract class WorkbenchScreenMixin extends AbstractContainerScreen<GunSmithTableMenu> implements WorkbenchMaterialView {
    @Shadow private GunSmithTableRecipe selectedRecipe;
    @Shadow private Int2IntArrayMap playerIngredientCount;
    @Unique private ResourceLocation workshop$recipe;
    @Unique private int[] workshop$counts = new int[0];
    @Unique private boolean workshop$craftable;
    @Unique private long workshop$requestedAt;
    @Unique private ResourceLocation workshop$requestedRecipe;
    protected WorkbenchScreenMixin(GunSmithTableMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Inject(method = "getPlayerIngredientCount", at = @At("HEAD"), cancellable = true)
    private void workshop$counts(GunSmithTableRecipe recipe, CallbackInfo callback) {
        callback.cancel(); workshop$update(recipe);
    }
    @Inject(method = "renderIngredient", at = @At("HEAD"))
    private void workshop$refresh(GuiGraphics graphics, CallbackInfo callback) { workshop$update(selectedRecipe); }
    @Unique private void workshop$update(GunSmithTableRecipe recipe) {
        playerIngredientCount = new Int2IntArrayMap();
        if (recipe == null) return;
        boolean current = recipe.getId().equals(workshop$recipe);
        for (int i = 0; i < recipe.getInputs().size(); i++) playerIngredientCount.put(i, current && i < workshop$counts.length ? workshop$counts[i] : 0);
        long now = System.currentTimeMillis();
        if (!recipe.getId().equals(workshop$requestedRecipe) || now - workshop$requestedAt >= 500) {
            workshop$requestedAt = now; workshop$requestedRecipe = recipe.getId();
            WorkbenchNetwork.query(menu.containerId, recipe.getId());
        }
    }
    @Inject(method = "lambda$addCraftButton$10", at = @At("HEAD"), cancellable = true)
    private void workshop$guard(Button button, CallbackInfo callback) {
        if (selectedRecipe == null || !selectedRecipe.getId().equals(workshop$recipe) || !workshop$craftable) callback.cancel();
    }
    @Override
    public void workshop$materials(int menuId, ResourceLocation recipe, int[] counts, boolean craftable) {
        if (menu.containerId != menuId || selectedRecipe == null || !selectedRecipe.getId().equals(recipe)) return;
        workshop$recipe = recipe;
        // Mixin 0.8.5 cannot transform an array-owned clone() invocation in a detached-super mixin.
        workshop$counts = java.util.Arrays.copyOf(counts, counts.length);
        workshop$craftable = craftable;
        workshop$update(selectedRecipe);
    }
}
