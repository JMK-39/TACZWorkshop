package dev.xyat.taczworkshop.mixin.compat;

import dev.xyat.kineticcore.api.registry.KineticRegistries;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import dev.xyat.taczworkshop.compat.ClientAmmoTickEvent;
import dev.xyat.taczworkshop.compat.VirtualInventory;
import dev.xyat.taczworkshop.compat.sophisticated.SophisticatedBackpacksCompat;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

public final class TaczBackpackMixins {
    private TaczBackpackMixins() {
    }

    @Mixin(value = AbstractGunItem.class, remap = false)
    public static class AbstractGunItemMixin {
        @SuppressWarnings("unchecked")
        @Redirect(method = "canReload", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;"))
        private <T> LazyOptional<T> taczworkshop_tacz$checkBackpackAmmos(LivingEntity instance, Capability<T> capability, Direction facing) {
            if (!(instance instanceof Player player)) return instance.getCapability(capability, facing);
            ArrayList<ItemStack> items = SophisticatedBackpacksCompat.getItemsFromInventoryBackpack(player);
            items.addAll(player.getInventory().items);
            VirtualInventory inventory = new VirtualInventory(items.size(), player);
            for (int i = 0; i < items.size(); i++) inventory.setItem(i, items.get(i));
            return LazyOptional.of(() -> (T) inventory.getHandler());
        }
    }

    @Mixin(value = ModernKineticGunScriptAPI.class, remap = false)
    public static class ModernGunScriptApiMixin {
        @Shadow
        private LivingEntity shooter;

        @SuppressWarnings("unchecked")
        @Redirect(method = "hasAmmoToConsume", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;"))
        private <T> LazyOptional<T> taczworkshop_tacz$redirectGetCapability(LivingEntity instance, Capability<T> capability, Direction facing) {
            if (!(instance instanceof Player player) || capability != ForgeCapabilities.ITEM_HANDLER) return instance.getCapability(capability, facing);
            ArrayList<ItemStack> items = SophisticatedBackpacksCompat.getItemsFromInventoryBackpack(player);
            instance.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(handler -> {
                for (int i = 0; i < handler.getSlots(); i++) items.add(handler.getStackInSlot(i));
            });
            VirtualInventory inventory = new VirtualInventory(items.size(), player);
            for (int i = 0; i < items.size(); i++) inventory.setItem(i, items.get(i));
            return LazyOptional.of(() -> (T) inventory.getHandler());
        }

        @Redirect(method = "lambda$consumeAmmoFromPlayer$4", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/item/gun/AbstractGunItem;findAndExtractInventoryAmmo(Lnet/minecraftforge/items/IItemHandler;Lnet/minecraft/world/item/ItemStack;I)I"))
        private int taczworkshop_tacz$useBackpackAmmo(AbstractGunItem gun, IItemHandler inventoryHandler, ItemStack gunItem, int requested) {
            if (!(shooter instanceof ServerPlayer player)) return gun.findAndExtractInventoryAmmo(inventoryHandler, gunItem, requested);
            final int[] remaining = {requested};
            SophisticatedBackpacksCompat.getAllInventoryBackpack(player).forEach(backpack -> {
                if (backpack.isEmpty() || remaining[0] <= 0) return;
                ResourceLocation itemId = KineticRegistries.items().id(backpack.getItem());
                if (itemId == null || !"sophisticatedbackpacks".equals(itemId.getNamespace()) || !itemId.getPath().contains("backpack")) return;
                final int[] used = new int[1];
                SophisticatedBackpacksCompat.modifyInventoryBackpack(player, backpack, handler -> used[0] = gun.findAndExtractInventoryAmmo(handler, gunItem, remaining[0]));
                remaining[0] -= used[0];
            });
            if (remaining[0] > 0) remaining[0] -= gun.findAndExtractInventoryAmmo(inventoryHandler, gunItem, remaining[0]);
            return requested - remaining[0];
        }
    }

    @Mixin(value = GunAnimationStateContext.class, remap = false)
    public static class GunAnimationStateContextMixin {
        @SuppressWarnings("unchecked")
        @Redirect(method = "lambda$hasAmmoToConsume$8", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;"))
        private <T> LazyOptional<T> taczworkshop_tacz$redirectGetCapability(Entity instance, Capability<T> capability, Direction facing) {
            if (!(instance instanceof LocalPlayer player) || capability != ForgeCapabilities.ITEM_HANDLER) return instance.getCapability(capability, facing);
            ArrayList<ItemStack> items = SophisticatedBackpacksCompat.getItemsFromInventoryBackpack(player);
            player.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(handler -> {
                for (int i = 0; i < handler.getSlots(); i++) items.add(handler.getStackInSlot(i));
            });
            VirtualInventory inventory = new VirtualInventory(items.size(), player);
            for (int i = 0; i < items.size(); i++) inventory.setItem(i, items.get(i));
            return LazyOptional.of(() -> (T) inventory.getHandler());
        }
    }

    @Mixin(value = GunHudOverlay.class, remap = false)
    public static class GunHudOverlayMixin {
        @ModifyArg(method = "handleCacheCount", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/gui/overlay/GunHudOverlay;handleInventoryAmmo(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Inventory;)V"))
        private static Inventory taczworkshop_tacz$useBackpackAmmo(Inventory inventory) {
            return ClientAmmoTickEvent.virtualInventory == null ? inventory : ClientAmmoTickEvent.virtualInventory;
        }
    }
}
