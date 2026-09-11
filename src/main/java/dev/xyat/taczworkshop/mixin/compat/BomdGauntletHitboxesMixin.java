package dev.xyat.taczworkshop.mixin.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.cerbon.bosses_of_mass_destruction.entity.custom.gauntlet.GauntletHitboxes", remap = false)
public abstract class BomdGauntletHitboxesMixin {
    @Unique
    private boolean taczworkshop_tacz$openHand = true;

    @Inject(method = "setOpenHandHitbox", at = @At("TAIL"), remap = false)
    private void taczworkshop_tacz$markOpenHand(CallbackInfo ci) {
        taczworkshop_tacz$openHand = true;
    }

    @Inject(method = "setClosedFistHitbox", at = @At("TAIL"), remap = false)
    private void taczworkshop_tacz$markClosedFist(CallbackInfo ci) {
        taczworkshop_tacz$openHand = false;
    }

    @Inject(method = "shouldDamage", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczworkshop_tacz$allowTaczDamage(LivingEntity actor, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source.getMsgId().startsWith("tacz.") && taczworkshop_tacz$openHand) cir.setReturnValue(true);
    }
}
