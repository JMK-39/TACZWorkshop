package dev.xyat.taczworkshop.config;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.taczworkshop.client.TaczClientHandler;

public final class TaczConfigGui {
    public static final String PAGE_ID = "taczworkshop:management";

    private TaczConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        KineticI18n.translatable("cfg.taczworkshop.management.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .pageDescription(KineticI18n.translatable("cfg.taczworkshop.management.description"))
                .action(
                        "open_data_manager",
                        KineticI18n.translatable("cfg.taczworkshop.management.open_data"),
                        TaczClientHandler::openDataManagerFromCore,
                        KineticI18n.translatable("cfg.taczworkshop.management.open_data.tooltip")
                )
                .action(
                        "open_recipe_manager",
                        KineticI18n.translatable("cfg.taczworkshop.management.open_recipes"),
                        TaczClientHandler::openRecipeManagerFromCore,
                        KineticI18n.translatable("cfg.taczworkshop.management.open_recipes.tooltip")
                )
                .build());
    }
}
