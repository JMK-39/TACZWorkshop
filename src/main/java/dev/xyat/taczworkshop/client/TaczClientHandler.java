package dev.xyat.taczworkshop.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import dev.xyat.taczworkshop.client.gui.TaczDataDetailScreen;
import dev.xyat.taczworkshop.client.gui.TaczDataManagerScreen;
import dev.xyat.taczworkshop.client.gui.TaczRecipeListScreen;
import dev.xyat.taczworkshop.network.TaczRecipeNetwork;
import dev.xyat.kineticcore.config.client.KTConfigApi;
import dev.xyat.taczworkshop.config.TaczConfigGui;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;

public final class TaczClientHandler {
    private TaczClientHandler() {
    }

    public static void openEditor() {
        openRecipeManager(null);
    }

    public static void openRecipeManagerFromCore() {
        Minecraft minecraft = Minecraft.getInstance();
        openRecipeManager(minecraft.screen);
    }

    public static void openDataManagerFromCore() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen;
        minecraft.setScreen(new TaczDataManagerScreen(parent));
        TaczRecipeNetwork.requestDataList();
        TaczRecipeNetwork.requestSnapshot();
    }

    private static void openRecipeManager(Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new TaczRecipeListScreen(parent));
        TaczRecipeNetwork.requestSnapshot();
    }

    public static void handleSnapshot(String json) {
        try {
            TaczClientState.replaceFromJson(json);
            if (Minecraft.getInstance().screen instanceof TaczRecipeListScreen screen) {
                screen.refreshFromState();
            }
        } catch (Exception exception) {
            handleToast(Component.translatable("msg.taczworkshop.sync_failed"));
        }
    }


    public static void handleRoutes(String json) {
        TaczRouteClientState.replaceFromJson(json);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof GunSmithTableScreen screen) screen.init();
    }

    public static void handleDataList(String json) {
        try {
            TaczDataClientState.replaceList(json);
            if (Minecraft.getInstance().screen instanceof TaczDataManagerScreen screen) screen.refreshFromState();
        } catch (Exception exception) {
            handleToast(Component.translatable("msg.taczworkshop.sync_failed"));
        }
    }

    public static void handleDataDetail(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json == null || json.isBlank() ? "{}" : json);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("Invalid TACZ detail");
            JsonObject detail = TaczDataClientState.applyPendingToDetail(parsed.getAsJsonObject());
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof TaczDataManagerScreen parent) {
                minecraft.setScreen(new TaczDataDetailScreen(parent, detail));
            }
        } catch (Exception exception) {
            handleToast(Component.translatable("msg.taczworkshop.data_detail_failed"));
        }
    }

    public static void handleDataBatchCommit(long batchId) {
        TaczDataClientState.acknowledgeThrough(batchId);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TaczDataManagerScreen screen) screen.handleDataBatchCommit(batchId);
    }

    public static void handleSaved() {
        KTConfigApi.notifySaved(TaczConfigGui.PAGE_ID);
    }

    public static void handleToast(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.displayClientMessage(message, false);
    }
}
