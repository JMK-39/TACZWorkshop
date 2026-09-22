package dev.xyat.taczworkshop.client;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import com.google.gson.JsonObject;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.AmmoIndexPOJO;
import com.tacz.guns.resource.pojo.AttachmentIndexPOJO;
import com.tacz.guns.resource.pojo.GunIndexPOJO;
import dev.xyat.taczworkshop.data.TaczDataKind;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.function.Supplier;

public final class TaczPreviewIndexContext {
    private record Preview(TaczDataKind kind, ResourceLocation id, JsonObject index) {
    }

    private static final ThreadLocal<Preview> CURRENT = new ThreadLocal<>();

    private TaczPreviewIndexContext() {
    }

    public static void with(TaczDataListEntry entry, Runnable action) {
        if (entry == null) {
            action.run();
            return;
        }
        with(entry.kind(), entry.id(), entry.previewIndex(), action);
    }

    public static void with(TaczDataKind kind, String id, JsonObject index, Runnable action) {
        withResult(kind, id, index, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T withResult(TaczDataKind kind, String id, JsonObject index, Supplier<T> action) {
        ResourceLocation resourceId = KineticResourceIds.tryParse(id);
        if (kind == null || resourceId == null || index == null || index.entrySet().isEmpty()) return action.get();
        Preview previous = CURRENT.get();
        CURRENT.set(new Preview(kind, resourceId, index));
        try {
            return action.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static Optional<CommonGunIndex> gun(ResourceLocation id) {
        Preview preview = CURRENT.get();
        if (doesNotMatch(preview, TaczDataKind.GUN, id)) return Optional.empty();
        try {
            GunIndexPOJO pojo = CommonAssetsManager.GSON.fromJson(preview.index(), GunIndexPOJO.class);
            return Optional.of(CommonGunIndex.getInstance(pojo));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<CommonAttachmentIndex> attachment(ResourceLocation id) {
        Preview preview = CURRENT.get();
        if (doesNotMatch(preview, TaczDataKind.ATTACHMENT, id)) return Optional.empty();
        try {
            AttachmentIndexPOJO pojo = CommonAssetsManager.GSON.fromJson(preview.index(), AttachmentIndexPOJO.class);
            return Optional.of(CommonAttachmentIndex.getInstance(pojo));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<CommonAmmoIndex> ammo(ResourceLocation id) {
        Preview preview = CURRENT.get();
        if (doesNotMatch(preview, TaczDataKind.AMMO, id)) return Optional.empty();
        try {
            AmmoIndexPOJO pojo = CommonAssetsManager.GSON.fromJson(preview.index(), AmmoIndexPOJO.class);
            return Optional.of(CommonAmmoIndex.getInstance(pojo));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }


    public static Optional<ClientGunIndex> clientGun(ResourceLocation id) {
        return gun(id).flatMap(index -> {
            try {
                return Optional.of(ClientGunIndex.getInstance(index.getPojo()));
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        });
    }

    public static Optional<ClientAttachmentIndex> clientAttachment(ResourceLocation id) {
        return attachment(id).flatMap(index -> {
            try {
                return Optional.of(ClientAttachmentIndex.getInstance(id, index.getPojo()));
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        });
    }

    public static Optional<ClientAmmoIndex> clientAmmo(ResourceLocation id) {
        return ammo(id).flatMap(index -> {
            try {
                return Optional.of(ClientAmmoIndex.getInstance(index.getPojo()));
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        });
    }

    private static boolean doesNotMatch(Preview preview, TaczDataKind kind, ResourceLocation id) {
        return preview == null || preview.kind() != kind || !preview.id().equals(id);
    }
}
