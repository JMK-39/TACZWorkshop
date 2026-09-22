package dev.xyat.taczworkshop.client;

import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import com.google.gson.JsonObject;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class TaczStackUtil {
    private TaczStackUtil() {
    }

    public static JsonObject ingredientFromStack(ItemStack stack) {
        JsonObject ingredient = new JsonObject();
        if (stack == null || stack.isEmpty()) return ingredient;
        ResourceLocation itemId = KineticRegistries.items().id(stack.getItem());
        if (itemId == null) return ingredient;

        CompoundTag identity = identityTag(stack);
        if (!identity.isEmpty()) {
            ingredient.addProperty("type", "forge:partial_nbt");
            ingredient.addProperty("item", itemId.toString());
            ingredient.addProperty("nbt", identity.toString());
        } else {
            ingredient.addProperty("item", itemId.toString());
        }
        return ingredient;
    }

    public static JsonObject ingredientFromTag(String tag) {
        JsonObject ingredient = new JsonObject();
        String value = tag == null ? "" : tag.trim();
        if (value.startsWith("#")) value = value.substring(1);
        ingredient.addProperty("tag", value);
        return ingredient;
    }

    public static String specialId(ItemStack stack, String type) {
        if (stack == null || stack.isEmpty()) return "";
        String nbtId = identityIdFromTag(stack, type);
        if (!nbtId.isBlank()) return nbtId;
        if ("gun".equals(type) && stack.getItem() instanceof IGun gun) {
            ResourceLocation id = gun.getGunId(stack);
            return id == null || DefaultAssets.EMPTY_GUN_ID.equals(id) ? "" : id.toString();
        }
        if ("attachment".equals(type) && stack.getItem() instanceof IAttachment attachment) {
            ResourceLocation id = attachment.getAttachmentId(stack);
            return id == null || DefaultAssets.EMPTY_ATTACHMENT_ID.equals(id) ? "" : id.toString();
        }
        if ("ammo".equals(type) && stack.getItem() instanceof IAmmo ammo) {
            ResourceLocation id = ammo.getAmmoId(stack);
            return id == null || DefaultAssets.EMPTY_AMMO_ID.equals(id) ? "" : id.toString();
        }
        return "";
    }

    private static String identityIdFromTag(ItemStack stack, String type) {
        if (!stack.hasTag() || stack.getTag() == null) return "";
        String key = switch (type) {
            case "gun" -> "GunId";
            case "attachment" -> "AttachmentId";
            case "ammo" -> "AmmoId";
            default -> "";
        };
        if (key.isBlank() || !stack.getTag().contains(key)) return "";
        String value = stack.getTag().getString(key).trim();
        ResourceLocation id = KineticResourceIds.tryParse(value);
        if (id == null) return "";
        if ("gun".equals(type) && DefaultAssets.EMPTY_GUN_ID.equals(id)) return "";
        if ("attachment".equals(type) && DefaultAssets.EMPTY_ATTACHMENT_ID.equals(id)) return "";
        if ("ammo".equals(type) && DefaultAssets.EMPTY_AMMO_ID.equals(id)) return "";
        return value;
    }

    public static String attachmentId(ItemStack stack) {
        return specialId(stack, "attachment");
    }

    public static JsonObject customResultItem(ItemStack stack, int count) {
        JsonObject item = new JsonObject();
        if (stack == null || stack.isEmpty()) return item;
        ResourceLocation id = KineticRegistries.items().id(stack.getItem());
        if (id == null) return item;
        item.addProperty("item", id.toString());
        item.addProperty("count", Math.max(1, count));
        if (stack.hasTag() && stack.getTag() != null && !stack.getTag().isEmpty()) {
            item.addProperty("nbt", stack.getTag().toString());
        }
        return item;
    }

    private static CompoundTag identityTag(ItemStack stack) {
        CompoundTag identity = new CompoundTag();
        if (stack.getItem() instanceof IGun gun) {
            ResourceLocation id = gun.getGunId(stack);
            if (id != null) identity.putString("GunId", id.toString());
            return identity;
        }
        if (stack.getItem() instanceof IAttachment attachment) {
            ResourceLocation id = attachment.getAttachmentId(stack);
            if (id != null) identity.putString("AttachmentId", id.toString());
            return identity;
        }
        if (stack.getItem() instanceof IAmmo ammo) {
            ResourceLocation id = ammo.getAmmoId(stack);
            if (id != null) identity.putString("AmmoId", id.toString());
            return identity;
        }
        if (stack.hasTag() && stack.getTag() != null) return stack.getTag().copy();
        return identity;
    }
}
