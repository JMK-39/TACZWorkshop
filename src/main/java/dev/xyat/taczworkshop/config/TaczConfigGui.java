package dev.xyat.taczworkshop.config;

import dev.xyat.kineticcore.config.client.KTConfigApi;
import dev.xyat.kineticcore.config.client.KTConfigPage;
import dev.xyat.kineticcore.config.client.KTConfigScope;
import dev.xyat.taczworkshop.client.TaczClientHandler;
import net.minecraft.network.chat.Component;

public final class TaczConfigGui {
    public static final String PAGE_ID = "taczworkshop:management";

    private TaczConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        Component.translatable("cfg.taczworkshop.management.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .pageDescription(Component.translatable("cfg.taczworkshop.management.description"))
                .action(
                        "open_data_manager",
                        Component.translatable("cfg.taczworkshop.management.open_data"),
                        TaczClientHandler::openDataManagerFromCore,
                        Component.translatable("cfg.taczworkshop.management.open_data.tooltip")
                )
                .action(
                        "open_recipe_manager",
                        Component.translatable("cfg.taczworkshop.management.open_recipes"),
                        TaczClientHandler::openRecipeManagerFromCore,
                        Component.translatable("cfg.taczworkshop.management.open_recipes.tooltip")
                )
                .build());
    }
}
