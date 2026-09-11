package dev.xyat.taczworkshop.mixin;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import dev.xyat.taczworkshop.client.TaczPreviewIndexContext;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = TimelessAPI.class, remap = false)
public abstract class TimelessApiPreviewMixin {

    @Inject(method = "getClientGunIndex", at = @At("HEAD"), cancellable = true)
    private static void taczworkshop_tacz$previewClientGun(ResourceLocation id, CallbackInfoReturnable<Optional<ClientGunIndex>> cir) {
        TaczPreviewIndexContext.clientGun(id).ifPresent(value -> cir.setReturnValue(Optional.of(value)));
    }

    @Inject(method = "getClientAttachmentIndex", at = @At("HEAD"), cancellable = true)
    private static void taczworkshop_tacz$previewClientAttachment(ResourceLocation id, CallbackInfoReturnable<Optional<ClientAttachmentIndex>> cir) {
        TaczPreviewIndexContext.clientAttachment(id).ifPresent(value -> cir.setReturnValue(Optional.of(value)));
    }

    @Inject(method = "getClientAmmoIndex", at = @At("HEAD"), cancellable = true)
    private static void taczworkshop_tacz$previewClientAmmo(ResourceLocation id, CallbackInfoReturnable<Optional<ClientAmmoIndex>> cir) {
        TaczPreviewIndexContext.clientAmmo(id).ifPresent(value -> cir.setReturnValue(Optional.of(value)));
    }

    @Inject(method = "getCommonGunIndex", at = @At("HEAD"), cancellable = true)
    private static void taczworkshop_tacz$previewGun(ResourceLocation id, CallbackInfoReturnable<Optional<CommonGunIndex>> cir) {
        TaczPreviewIndexContext.gun(id).ifPresent(value -> cir.setReturnValue(Optional.of(value)));
    }

    @Inject(method = "getCommonAttachmentIndex", at = @At("HEAD"), cancellable = true)
    private static void taczworkshop_tacz$previewAttachment(ResourceLocation id, CallbackInfoReturnable<Optional<CommonAttachmentIndex>> cir) {
        TaczPreviewIndexContext.attachment(id).ifPresent(value -> cir.setReturnValue(Optional.of(value)));
    }

    @Inject(method = "getCommonAmmoIndex", at = @At("HEAD"), cancellable = true)
    private static void taczworkshop_tacz$previewAmmo(ResourceLocation id, CallbackInfoReturnable<Optional<CommonAmmoIndex>> cir) {
        TaczPreviewIndexContext.ammo(id).ifPresent(value -> cir.setReturnValue(Optional.of(value)));
    }
}
