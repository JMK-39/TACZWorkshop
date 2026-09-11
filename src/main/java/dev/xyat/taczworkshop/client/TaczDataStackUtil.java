package dev.xyat.taczworkshop.client;

import com.google.gson.JsonObject;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import dev.xyat.taczworkshop.data.TaczDataKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public final class TaczDataStackUtil {
    private TaczDataStackUtil() {
    }

    public static ItemStack build(TaczDataKind kind, String id) {
        return build(kind, id, new JsonObject());
    }

    public static ItemStack build(TaczDataKind kind, String id, JsonObject index) {
        ResourceLocation resourceId = ResourceLocation.tryParse(id);
        if (resourceId == null || kind == null) return ItemStack.EMPTY;
        return switch (kind) {
            case GUN -> GunItemBuilder.create().setId(resourceId).forceBuild();
            case ATTACHMENT -> AttachmentItemBuilder.create().setId(resourceId).build();
            case AMMO -> AmmoItemBuilder.create().setId(resourceId).build();
            case MELEE, THROWABLE, CONSUMABLE -> buildLrTactical(kind, resourceId, index);
        };
    }

    private static ItemStack buildLrTactical(TaczDataKind kind, ResourceLocation id, JsonObject index) {
        String baseItem = read(index, "base_item");
        if (baseItem.isBlank()) {
            baseItem = switch (kind) {
                case MELEE -> "lrtactical:melee";
                case THROWABLE -> "lrtactical:throwable";
                case CONSUMABLE -> "lrtactical:consumable";
                default -> "";
            };
        }
        ResourceLocation baseId = ResourceLocation.tryParse(baseItem);
        if (baseId == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(baseId);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        String key = switch (kind) {
            case MELEE -> "MeleeWeaponId";
            case THROWABLE -> "ThrowableId";
            case CONSUMABLE -> "ConsumableId";
            default -> "";
        };
        if (!key.isBlank()) stack.getOrCreateTag().putString(key, id.toString());
        return stack;
    }


    public static String externalId(ItemStack stack, TaczDataKind kind) {
        if (stack == null || stack.isEmpty() || kind == null || stack.getTag() == null) return "";
        String key = switch (kind) {
            case MELEE -> "MeleeWeaponId";
            case THROWABLE -> "ThrowableId";
            case CONSUMABLE -> "ConsumableId";
            default -> "";
        };
        return key.isBlank() ? "" : stack.getTag().getString(key);
    }

    private static String read(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return "";
        return object.get(key).getAsString();
    }
}
