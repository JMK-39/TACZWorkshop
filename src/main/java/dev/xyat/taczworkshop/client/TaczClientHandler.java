package dev.xyat.taczworkshop.client;

import dev.xyat.kineticcore.api.text.KineticI18n;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.taczworkshop.client.gui.TaczDataDetailPage;
import dev.xyat.taczworkshop.client.gui.TaczDataManagerPage;
import dev.xyat.taczworkshop.network.TaczDataNetwork;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.taczworkshop.config.TaczConfigGui;
import net.minecraft.network.chat.Component;
import dev.xyat.kineticcore.api.client.gui.KineticGui;

public final class TaczClientHandler {
    private TaczClientHandler() {
    }

    public static void openDataManagerFromCore() {
        KineticGui.openChild(new TaczDataManagerPage());
        TaczDataNetwork.requestDataList();
    }

    public static void handleDataList(String json) {
        try {
            TaczDataClientState.replaceList(json);
            TaczDataManagerPage page = KineticGui.currentPage(TaczDataManagerPage.class);
            if (page != null) page.refreshFromState();
        } catch (Exception exception) {
            handleToast(KineticI18n.translatable("msg.taczworkshop.sync_failed"));
        }
    }

    public static void handleDataDetail(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json == null || json.isBlank() ? "{}" : json);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("Invalid TACZ detail");
            JsonObject detail = TaczDataClientState.applyPendingToDetail(parsed.getAsJsonObject());
            if (KineticGui.currentPage(TaczDataManagerPage.class) != null) {
                KineticGui.openChild(new TaczDataDetailPage(detail));
            }
        } catch (Exception exception) {
            handleToast(KineticI18n.translatable("msg.taczworkshop.data_detail_failed"));
        }
    }

    public static void handleDataBatchCommit(long batchId) {
        TaczDataClientState.acknowledgeThrough(batchId);
        TaczDataManagerPage page = KineticGui.currentPage(TaczDataManagerPage.class);
        if (page != null) page.handleDataBatchCommit(batchId);
    }

    public static void handleSaved() {
        KTConfigApi.notifySaved(TaczConfigGui.PAGE_ID);
    }

    public static void handleToast(Component message) {
        KineticClientRuntime.displayClientMessage(message, false);
    }
}
