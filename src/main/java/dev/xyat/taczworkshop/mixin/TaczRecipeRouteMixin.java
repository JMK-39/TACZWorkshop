package dev.xyat.taczworkshop.mixin;

import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import dev.xyat.taczworkshop.server.TaczRecipeRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GunSmithTableMenu.class, remap = false)
public abstract class TaczRecipeRouteMixin {
    @Shadow
    public abstract ResourceLocation getBlockId();

    @Inject(method = "getRecipe", at = @At("HEAD"), cancellable = true)
    private void taczworkshop_tacz$routeRecipe(ResourceLocation recipeId, RecipeManager recipeManager, CallbackInfoReturnable<GunSmithTableRecipe> cir) {
        if (!TaczRecipeRuntime.hasManagedRoute(recipeId)) return;
        ResourceLocation blockId = getBlockId();
        if (!TaczRecipeRuntime.routeAllows(recipeId, blockId)) {
            cir.setReturnValue(null);
            return;
        }
        Recipe<?> recipe = recipeManager.byKey(recipeId).orElse(null);
        cir.setReturnValue(recipe instanceof GunSmithTableRecipe gunRecipe ? gunRecipe : null);
    }
}
