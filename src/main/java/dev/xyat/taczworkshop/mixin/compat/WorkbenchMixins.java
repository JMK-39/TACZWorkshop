package dev.xyat.taczworkshop.mixin.compat;

import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import dev.xyat.taczworkshop.crafting.WorkbenchMaterials;
import dev.xyat.taczworkshop.crafting.WorkbenchMenuContext;
import dev.xyat.taczworkshop.network.WorkbenchNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class WorkbenchMixins {
    private WorkbenchMixins() {}
    @Mixin(value = GunSmithTableBlockEntity.class, remap = false)
    public static class TableMixin {
        // MenuProvider's override has no owner mapping in the external TaCZ jar. Both verified names cover dev and installed Forge.
        @Inject(method = {"createMenu", "m_7208_"}, at = @At("RETURN"), remap = false)
        private void workshop$bind(int id, Inventory inventory, Player player, CallbackInfoReturnable<AbstractContainerMenu> callback) {
            if (callback.getReturnValue() instanceof WorkbenchMenuContext context) context.workshop$bindTable((GunSmithTableBlockEntity) (Object) this);
        }
    }
    @Mixin(value = GunSmithTableMenu.class, remap = false)
    public static class MenuMixin implements WorkbenchMenuContext {
        @Unique private GunSmithTableBlockEntity workshop$table;
        @Unique private long workshop$lastQuery = -1000;
        @Shadow private GunSmithTableRecipe getRecipe(ResourceLocation id, RecipeManager recipes) { throw new AssertionError(); }
        public void workshop$bindTable(GunSmithTableBlockEntity table) { workshop$table = table; }
        public GunSmithTableBlockEntity workshop$table() { return workshop$table; }
        public GunSmithTableRecipe workshop$recipe(ResourceLocation id, RecipeManager recipes) { return getRecipe(id, recipes); }
        public long workshop$lastQuery() { return workshop$lastQuery; }
        public void workshop$lastQuery(long tick) { workshop$lastQuery = tick; }

        @Inject(method = "doCraft", at = @At("HEAD"), cancellable = true)
        private void workshop$craft(ResourceLocation id, Player player, CallbackInfo callback) {
            callback.cancel();
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            GunSmithTableMenu menu = (GunSmithTableMenu) (Object) this;
            try { WorkbenchMaterials.craft(serverPlayer, menu, getRecipe(id, player.level().getRecipeManager())); }
            finally { WorkbenchNetwork.send(serverPlayer, menu, id); }
        }
        @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true, remap = true)
        private void workshop$valid(Player player, CallbackInfoReturnable<Boolean> callback) {
            if (player instanceof ServerPlayer serverPlayer && workshop$table != null) {
                callback.setReturnValue(WorkbenchMaterials.valid(serverPlayer, (GunSmithTableMenu) (Object) this));
            }
        }
    }
}
