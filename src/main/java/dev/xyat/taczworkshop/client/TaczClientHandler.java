package dev.xyat.taczworkshop.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import dev.xyat.taczworkshop.client.gui.TaczDataDetailScreen;
import dev.xyat.taczworkshop.client.gui.TaczDataManagerScreen;
import dev.xyat.taczworkshop.client.gui.TaczRecipeListScreen;
import dev.xyat.taczworkshop.network.TaczRecipeNetwork;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.taczworkshop.config.TaczConfigGui;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;

public final class TaczClientHandler {
    private TaczClientHandler() {
    }

    public static void openEditor() {
        openRecipeManager(null);
    }

    public static void openRecipeManagerFromCore() {
        openRecipeManager(KineticClientRuntime.currentScreen());
    }

    public static void openDataManagerFromCore() {
        Screen parent = KineticClientRuntime.currentScreen();
        KineticClientRuntime.openScreen(new TaczDataManagerScreen(parent));
        TaczRecipeNetwork.requestDataList();
        TaczRecipeNetwork.requestSnapshot();
    }

    private static void openRecipeManager(Screen parent) {
        KineticClientRuntime.openScreen(new TaczRecipeListScreen(parent));
        TaczRecipeNetwork.requestSnapshot();
    }

    public static void handleSnapshot(String json) {
        try {
            TaczClientState.replaceFromJson(json);
            if (KineticClientRuntime.currentScreen() instanceof TaczRecipeListScreen screen) {
                screen.refreshFromState();
            }
        } catch (Exception exception) {
            handleToast(Component.translatable("msg.taczworkshop.sync_failed"));
        }
    }


    public static void handleRoutes(String json) {
        TaczRouteClientState.replaceFromJson(json);
        if (KineticClientRuntime.currentScreen() instanceof GunSmithTableScreen screen) KineticClientRuntime.refreshScreen(screen);
    }

    public static void handleDataList(String json) {
        try {
            TaczDataClientState.replaceList(json);
            if (KineticClientRuntime.currentScreen() instanceof TaczDataManagerScreen screen) screen.refreshFromState();
        } catch (Exception exception) {
            handleToast(Component.translatable("msg.taczworkshop.sync_failed"));
        }
    }

    public static void handleDataDetail(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json == null || json.isBlank() ? "{}" : json);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("Invalid TACZ detail");
            JsonObject detail = TaczDataClientState.applyPendingToDetail(parsed.getAsJsonObject());
            if (KineticClientRuntime.currentScreen() instanceof TaczDataManagerScreen parent) {
                KineticClientRuntime.openScreen(new TaczDataDetailScreen(parent, detail));
            }
        } catch (Exception exception) {
            handleToast(Component.translatable("msg.taczworkshop.data_detail_failed"));
        }
    }

    public static void handleDataBatchCommit(long batchId) {
        TaczDataClientState.acknowledgeThrough(batchId);
        if (KineticClientRuntime.currentScreen() instanceof TaczDataManagerScreen screen) screen.handleDataBatchCommit(batchId);
    }

    public static void handleSaved() {
        KTConfigApi.notifySaved(TaczConfigGui.PAGE_ID);
    }

    public static void handleToast(Component message) {
        KineticClientRuntime.displayClientMessage(message, false);
    }
}
