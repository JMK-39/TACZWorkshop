package dev.xyat.taczworkshopvalidation;

import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/** Checks the actual transformed client classes after joining the owned validation world. */
final class TaczAddonClientValidation {
    static void check() throws Exception {
        require(KineticPlatform.isModLoaded("taczaddon"), "Correct TACZ addon is not installed");
        require(!KineticPlatform.isModLoaded("sometaczaddon"), "Wrong-target addon must be absent");
        for (String name : new String[]{
                "com.tacz.guns.api.item.gun.AbstractGunItem",
                "com.tacz.guns.item.ModernKineticGunScriptAPI",
                "com.tacz.guns.client.animation.statemachine.GunAnimationStateContext",
                "com.tacz.guns.client.gui.overlay.GunHudOverlay",
                "com.tacz.guns.client.tooltip.ClientAttachmentItemTooltip"}) {
            var methods = java.util.Arrays.asList(Class.forName(name).getDeclaredMethods());
            require(methods.stream().noneMatch(method -> method.getName().contains("taczworkshop_tacz$")),
                    "Workshop duplicate feature was injected into " + name);
            require(methods.stream().map(method -> method.getAnnotation(MixinMerged.class))
                            .anyMatch(origin -> origin != null && origin.mixin().startsWith("com.mafuyu404.taczaddon.")),
                    "Addon replacement feature was not injected into " + name);
        }
        var preview = java.util.Arrays.stream(Class.forName("com.tacz.guns.api.TimelessAPI").getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("taczworkshop_tacz$preview"));
        require(preview, "Workshop data preview must remain active with TACZ addon");
        var registered = Class.forName("dev.xyat.taczworkshop.compat.ClientAmmoTickEvent")
                .getDeclaredField("registered");
        registered.setAccessible(true);
        require(!registered.getBoolean(null), "Duplicate Workshop client ammo scanner must not be registered");
        LoggerFactory.getLogger(TaczAddonClientValidation.class).info(
                "TACZ_ADDON_CLIENT_PASS correct addon, actual client hook ownership, retained Workshop preview hook and disabled duplicate ammo scan");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
