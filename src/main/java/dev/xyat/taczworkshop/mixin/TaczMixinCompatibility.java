package dev.xyat.taczworkshop.mixin;

import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Leave overlapping gameplay features to TACZ addon while keeping Workshop data hooks. */
public final class TaczMixinCompatibility implements IMixinConfigPlugin {
    private static final Set<String> ADDON_OWNED = Set.of(
            "dev.xyat.taczworkshop.mixin.compat.TaczBackpackMixins$AbstractGunItemMixin",
            "dev.xyat.taczworkshop.mixin.compat.TaczBackpackMixins$ModernGunScriptApiMixin",
            "dev.xyat.taczworkshop.mixin.compat.TaczBackpackMixins$GunAnimationStateContextMixin",
            "dev.xyat.taczworkshop.mixin.compat.TaczBackpackMixins$GunHudOverlayMixin",
            "dev.xyat.taczworkshop.mixin.compat.TaczUiMixins$ClientAttachmentItemTooltipMixin"
    );
    private final BooleanSupplier addonPresent;

    public TaczMixinCompatibility() {
        this(() -> FMLLoader.getLoadingModList() != null
                && FMLLoader.getLoadingModList().getModFileById("taczaddon") != null);
    }

    TaczMixinCompatibility(BooleanSupplier addonPresent) {
        this.addonPresent = addonPresent;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !ADDON_OWNED.contains(mixinClassName) || !addonPresent.getAsBoolean();
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
