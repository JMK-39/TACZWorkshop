package dev.xyat.taczworkshop.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.xyat.taczworkshop.server.TaczRecipePreflight;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerPreflightMixin {
    @ModifyVariable(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Map<ResourceLocation, JsonElement> taczworkshop_tacz$preflightRecipes(Map<ResourceLocation, JsonElement> recipes) {
        return TaczRecipePreflight.filter(recipes);
    }

    @Inject(method = "fromJson(Lnet/minecraft/resources/ResourceLocation;Lcom/google/gson/JsonObject;)Lnet/minecraft/world/item/crafting/Recipe;", at = @At("HEAD"), cancellable = true)
    private static void taczworkshop_tacz$guardRecipe(ResourceLocation id, JsonObject jsonObject, CallbackInfoReturnable<Recipe<?>> cir) {
        if (!TaczRecipePreflight.inspectRecipeObject(id, jsonObject)) cir.setReturnValue(null);
    }
}
