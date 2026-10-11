package dev.xyat.taczworkshop.mixin.compat;

import dev.xyat.kineticcore.api.text.KineticI18n;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.client.tooltip.ClientAttachmentItemTooltip;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TaczUiMixins {
    private TaczUiMixins() {
    }

    @Mixin(value = ClientAttachmentItemTooltip.class, remap = false)
    public static class ClientAttachmentItemTooltipMixin {
        @Shadow
        @Final
        private ResourceLocation attachmentId;

        @Redirect(method = "lambda$addText$5", at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/modifier/JsonProperty;getComponents()Ljava/util/List;"))
        private List<Component> taczworkshop_tacz$modifyAttachmentDetail(JsonProperty<?> value) {
            LocalPlayer player = KineticClientRuntime.localPlayer();
            if (player == null) return value.getComponents();
            ItemStack gunItem = player.getMainHandItem().copy();
            IGun gun = IGun.getIGunOrNull(gunItem);
            if (gun == null || !AllowAttachmentTagMatcher.match(gun.getGunId(gunItem), attachmentId)) return value.getComponents();
            ItemStack attachmentItem = AttachmentItemBuilder.create().setId(attachmentId).build();
            IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentItem);
            if (attachment == null) return value.getComponents();

            AttachmentType attachmentType = attachment.getType(attachmentItem);
            List<Component> result = new ArrayList<>();
            ResourceLocation gunId = gun.getGunId(gunItem);
            HashMap<String, String> changes = new HashMap<>();
            HashMap<String, Double> origin = new HashMap<>();
            HashMap<String, Double> modified = new HashMap<>();
            HashMap<String, Double> defaults = new HashMap<>();

            TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
                GunData gunData = index.getGunData();
                ItemStack installed = gun.getAttachment(gunItem, attachmentType);
                if (!installed.isEmpty()) gun.unloadAttachment(gunItem, attachmentType);
                AttachmentCacheProperty cache = new AttachmentCacheProperty();
                cache.eval(gunItem, gunData);
                AttachmentPropertyManager.getModifiers().forEach((key, modifier) -> modifier.getPropertyDiagramsData(gunItem, gunData, cache).forEach(data -> origin.putAll(taczworkshop_tacz$handleData(data))));
                gun.installAttachment(gunItem, attachmentItem);
                cache.eval(gunItem, gunData);
                AttachmentPropertyManager.getModifiers().forEach((key, modifier) -> modifier.getPropertyDiagramsData(gunItem, gunData, cache).forEach(data -> {
                    String[] split = data.titleKey().split("\\.");
                    if (split.length >= 5) defaults.put(split[4], taczworkshop_tacz$extractValue(data.defaultString()));
                    modified.putAll(taczworkshop_tacz$handleData(data));
                }));
            });

            modified.forEach((titleKey, newValue) -> {
                if (!origin.containsKey(titleKey)) return;
                double offset = newValue - origin.get(titleKey);
                double defaultValue = defaults.getOrDefault(titleKey, 0.0);
                double rounded = Math.round(offset * 100d) / 100d;
                String signed = (offset > 0 ? "+" : "") + rounded + taczworkshop_tacz$unit(titleKey);
                String percent = defaultValue == 0 ? "0" : Long.toString((long) Math.ceil(offset / defaultValue * 100));
                changes.put(titleKey, KineticI18n.translatable("tip.taczworkshop.attachment.change.value", signed, (offset > 0 ? "+" : "") + percent).getString());
            });

            value.getComponents().forEach(component -> {
                String translationKey = taczworkshop_tacz$getTranslationKey(component);
                if (translationKey == null) return;
                String[] split = translationKey.split("\\.");
                if (split.length < 4) return;
                String titleKey = split[3];
                if ("inaccuracy".equals(titleKey)) titleKey = "hipfire_inaccuracy";
                String title = "hipfire_inaccuracy".equals(titleKey)
                        ? KineticI18n.translatable("gui.tacz.gun_refit.property_diagrams.hipfire_inaccuracy").getString()
                        : component.getString().replace("+ ", "").replace("- ", "");
                String remark = changes.getOrDefault(titleKey, component.getString().split(" ")[0]);
                boolean negative = component.getStyle().getColor() != null && "red".equals(component.getStyle().getColor().toString());
                result.add(KineticI18n.translatable(negative ? "tip.taczworkshop.attachment.change.negative" : "tip.taczworkshop.attachment.change.positive", title, remark));
            });
            return result;
        }

        @Unique
        private HashMap<String, Double> taczworkshop_tacz$handleData(IAttachmentModifier.DiagramsData data) {
            String positive = data.positivelyString();
            String negative = data.negativeString();
            String[] splitPositive = positive.split(" ");
            String text = splitPositive.length > 1 && !splitPositive[1].contains("+-") ? positive : negative;
            HashMap<String, Double> result = new HashMap<>();
            String[] titleSplit = data.titleKey().split("\\.");
            if (titleSplit.length >= 5) result.put(titleSplit[4], taczworkshop_tacz$extractValue(text));
            return result;
        }

        @Unique
        private double taczworkshop_tacz$extractValue(String text) {
            Matcher matcher = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?").matcher(text);
            double value = 0;
            while (matcher.find()) value = Double.parseDouble(matcher.group());
            return value;
        }

        @Unique
        private static String taczworkshop_tacz$getTranslationKey(Component component) {
            ComponentContents contents = component.getContents();
            return contents instanceof TranslatableContents translatable ? translatable.getKey() : null;
        }

        @Unique
        private static String taczworkshop_tacz$unit(String titleKey) {
            String key = null;
            if ("weight".equals(titleKey)) key = "unit.taczworkshop.kg";
            else if ("ads".equals(titleKey) || titleKey.contains("time")) key = "unit.taczworkshop.second";
            else if ("aim_inaccuracy".equals(titleKey) || "armor_ignore".equals(titleKey)) key = "unit.taczworkshop.percent";
            else if ("rpm".equals(titleKey)) key = "unit.taczworkshop.rpm";
            else if ("effective_range".equals(titleKey)) key = "unit.taczworkshop.meter";
            else if (titleKey.contains("ammo_speed")) key = "unit.taczworkshop.meter_per_second";
            return key == null ? "" : KineticI18n.translatable(key).getString();
        }
    }
}
