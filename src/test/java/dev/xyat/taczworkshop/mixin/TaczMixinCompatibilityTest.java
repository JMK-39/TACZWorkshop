package dev.xyat.taczworkshop.mixin;

import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class TaczMixinCompatibilityTest {
    private static final String PREFIX = "dev.xyat.taczworkshop.mixin.";
    private static final String[] DUPLICATE_FEATURES = {
            "compat.TaczBackpackMixins$AbstractGunItemMixin",
            "compat.TaczBackpackMixins$ModernGunScriptApiMixin",
            "compat.TaczBackpackMixins$GunAnimationStateContextMixin",
            "compat.TaczBackpackMixins$GunHudOverlayMixin",
            "compat.TaczUiMixins$ClientAttachmentItemTooltipMixin"
    };

    @Test
    void installedTaczAddonOwnsAmmoAndTooltipHooks() throws Exception {
        Object plugin = plugin(true);
        for (String name : DUPLICATE_FEATURES) {
            assertFalse(applies(plugin, name), "TACZ addon must own duplicate hook " + name);
        }
    }

    @Test
    void standaloneWorkshopKeepsAmmoAndTooltipFeatures() throws Exception {
        Object plugin = plugin(false);
        for (String name : DUPLICATE_FEATURES) assertTrue(applies(plugin, name));
    }

    @Test
    void addonDoesNotDisableDataEditingAndExternalDataRepair() throws Exception {
        Object plugin = plugin(true);
        for (String name : new String[]{"TaczDataManagerMixin", "TimelessApiPreviewMixin",
                "compat.LrTacticalDataManagerMixins$MeleeIndexManagerMixin",
                "compat.BomdGauntletHitboxesMixin", "compat.InventoryAccessor"}) {
            assertTrue(applies(plugin, name), "Data/repair hook must remain active " + name);
        }
    }

    private static Object plugin(boolean addonPresent) throws Exception {
        Class<?> type;
        try {
            type = Class.forName(PREFIX + "TaczMixinCompatibility");
        } catch (ClassNotFoundException absent) {
            throw new AssertionError("Workshop has no TACZ addon hook ownership gate", absent);
        }
        var constructor = type.getDeclaredConstructor(BooleanSupplier.class);
        constructor.setAccessible(true);
        return constructor.newInstance((BooleanSupplier) () -> addonPresent);
    }

    private static boolean applies(Object plugin, String shortName) throws Exception {
        return (boolean) plugin.getClass().getMethod("shouldApplyMixin", String.class, String.class)
                .invoke(plugin, "com.tacz.guns.TestTarget", PREFIX + shortName);
    }
}
