package dev.xyat.taczworkshop.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import dev.xyat.kineticcore.api.network.KineticCompression;
import dev.xyat.kineticcore.api.network.NetworkBuffer;
import dev.xyat.kineticcore.api.network.NetworkCodec;
import dev.xyat.kineticcore.api.network.NetworkVersionPolicy;
import dev.xyat.kineticcore.api.network.PacketChannel;
import dev.xyat.kineticcore.api.network.PacketRegistrations;
import dev.xyat.kineticcore.api.network.ServerPacketContext;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.taczworkshop.TaczWorkshop;
import dev.xyat.taczworkshop.client.TaczClientHandler;
import dev.xyat.taczworkshop.data.TaczDataCodec;
import dev.xyat.taczworkshop.data.TaczDataKind;
import dev.xyat.taczworkshop.data.TaczDataOverride;
import dev.xyat.taczworkshop.data.TaczRecipeCodec;
import dev.xyat.taczworkshop.data.TaczRecipeRecord;
import dev.xyat.taczworkshop.server.TaczBaseDataCache;
import dev.xyat.taczworkshop.server.TaczDataRuntime;
import dev.xyat.taczworkshop.server.TaczDataStore;
import dev.xyat.taczworkshop.server.TaczRecipeRuntime;
import dev.xyat.taczworkshop.server.TaczRecipeStore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

public final class TaczRecipeNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final int MAX_COMPRESSED = 4 * 1024 * 1024;
    private static final int MAX_DECOMPRESSED = 32 * 1024 * 1024;
    private static final PacketChannel CHANNEL = PacketChannel.create(
            KineticResourceIds.of(TaczWorkshop.MODID, "main"),
            PROTOCOL_VERSION,
            NetworkVersionPolicy.ANY
    );
    private static final boolean[] REGISTERED = new boolean[19];

    private TaczRecipeNetwork() {
    }

    public static synchronized void register() {
        PacketRegistrations.runIndependent(
                () -> registerClientbound(0, OpenEditorPacket.class, OpenEditorPacket::new, OpenEditorPacket::encode, packet -> TaczClientHandler.openEditor()),
                () -> registerServerbound(1, RequestSnapshotPacket.class, RequestSnapshotPacket::new, RequestSnapshotPacket::encode, TaczRecipeNetwork::handleRequestSnapshot),
                () -> registerClientbound(2, SnapshotPacket.class, SnapshotPacket::new, SnapshotPacket::encode, packet -> TaczClientHandler.handleSnapshot(decompress(packet.payload()))),
                () -> registerClientbound(3, RouteSnapshotPacket.class, RouteSnapshotPacket::new, RouteSnapshotPacket::encode, packet -> TaczClientHandler.handleRoutes(decompress(packet.payload()))),
                () -> registerServerbound(4, SaveRecordPacket.class, SaveRecordPacket::new, SaveRecordPacket::encode, TaczRecipeNetwork::handleSaveRecord),
                () -> registerServerbound(5, DeleteRecordPacket.class, DeleteRecordPacket::new, DeleteRecordPacket::encode, TaczRecipeNetwork::handleDeleteRecord),
                () -> registerServerbound(6, RequestDataListPacket.class, RequestDataListPacket::new, RequestDataListPacket::encode, TaczRecipeNetwork::handleRequestDataList),
                () -> registerClientbound(7, DataListPacket.class, DataListPacket::new, DataListPacket::encode, packet -> TaczClientHandler.handleDataList(decompress(packet.payload()))),
                () -> registerServerbound(8, RequestDataDetailPacket.class, RequestDataDetailPacket::new, RequestDataDetailPacket::encode, TaczRecipeNetwork::handleRequestDataDetail),
                () -> registerClientbound(9, DataDetailPacket.class, DataDetailPacket::new, DataDetailPacket::encode, packet -> TaczClientHandler.handleDataDetail(decompress(packet.payload()))),
                () -> registerServerbound(10, SaveDataOverridePacket.class, SaveDataOverridePacket::new, SaveDataOverridePacket::encode, TaczRecipeNetwork::handleSaveDataOverride),
                () -> registerServerbound(11, ResetDataOverridePacket.class, ResetDataOverridePacket::new, ResetDataOverridePacket::encode, TaczRecipeNetwork::handleResetDataOverride),
                () -> registerServerbound(12, SetDataRemovedPacket.class, SetDataRemovedPacket::new, SetDataRemovedPacket::encode, TaczRecipeNetwork::handleSetDataRemoved),
                () -> registerServerbound(13, SetOriginalRecipeDisabledPacket.class, SetOriginalRecipeDisabledPacket::new, SetOriginalRecipeDisabledPacket::encode, TaczRecipeNetwork::handleSetOriginalRecipeDisabled),
                () -> registerServerbound(14, RestoreOriginalRecipePacket.class, RestoreOriginalRecipePacket::new, RestoreOriginalRecipePacket::encode, TaczRecipeNetwork::handleRestoreOriginalRecipe),
                () -> registerClientbound(15, ToastPacket.class, ToastPacket::new, ToastPacket::encode, packet -> TaczClientHandler.handleToast(packet.message())),
                () -> registerServerbound(16, SaveDataBatchPacket.class, SaveDataBatchPacket::new, SaveDataBatchPacket::encode, TaczRecipeNetwork::handleSaveDataBatch),
                () -> registerClientbound(17, DataBatchCommitPacket.class, DataBatchCommitPacket::new, DataBatchCommitPacket::encode, packet -> TaczClientHandler.handleDataBatchCommit(packet.batchId())),
                () -> registerClientbound(18, SavedPacket.class, SavedPacket::new, SavedPacket::encode, packet -> TaczClientHandler.handleSaved())
        );
    }

    private static <T> void registerServerbound(
            int id,
            Class<T> type,
            java.util.function.Function<NetworkBuffer, T> decoder,
            java.util.function.BiConsumer<T, NetworkBuffer> encoder,
            dev.xyat.kineticcore.api.network.ServerboundPacketHandler<T> handler
    ) {
        if (REGISTERED[id]) return;
        CHANNEL.registerServerbound(
                id,
                type,
                NetworkCodec.of((buffer, message) -> encoder.accept(message, buffer), decoder),
                handler
        );
        REGISTERED[id] = true;
    }

    private static <T> void registerClientbound(
            int id,
            Class<T> type,
            java.util.function.Function<NetworkBuffer, T> decoder,
            java.util.function.BiConsumer<T, NetworkBuffer> encoder,
            java.util.function.Consumer<T> handler
    ) {
        if (REGISTERED[id]) return;
        CHANNEL.registerClientbound(
                id,
                type,
                NetworkCodec.of((buffer, message) -> encoder.accept(message, buffer), decoder),
                handler
        );
        REGISTERED[id] = true;
    }

    public static void requestSnapshot() {
        CHANNEL.sendToServer(new RequestSnapshotPacket());
    }

    public static void saveRecord(TaczRecipeRecord record) {
        byte[] payload = compress(TaczRecipeCodec.encodeRecord(record));
        if (payload.length > MAX_COMPRESSED) {
            TaczClientHandler.handleToast(Component.translatable("msg.taczworkshop.save_too_large"));
            return;
        }
        CHANNEL.sendToServer(new SaveRecordPacket(payload));
    }

    public static void deleteRecord(String uuid) {
        CHANNEL.sendToServer(new DeleteRecordPacket(uuid == null ? "" : uuid));
    }

    public static void setOriginalRecipeDisabled(String originalId, boolean disabled) {
        CHANNEL.sendToServer(new SetOriginalRecipeDisabledPacket(originalId == null ? "" : originalId, disabled));
    }

    public static void restoreOriginalRecipe(String originalId, String replacementUuid) {
        CHANNEL.sendToServer(new RestoreOriginalRecipePacket(originalId == null ? "" : originalId, replacementUuid == null ? "" : replacementUuid));
    }

    public static void requestDataList() {
        CHANNEL.sendToServer(new RequestDataListPacket());
    }

    public static void requestDataDetail(TaczDataKind kind, String id) {
        CHANNEL.sendToServer(new RequestDataDetailPacket(kind.wireName(), id == null ? "" : id));
    }

    public static void saveDataOverride(TaczDataKind kind, String id, String dataId, boolean removed, JsonObject data) {
        String json = TaczRecipeCodec.GSON.toJson(data == null ? new JsonObject() : data);
        byte[] payload = compress(json);
        if (payload.length > MAX_COMPRESSED) {
            TaczClientHandler.handleToast(Component.translatable("msg.taczworkshop.data_too_large"));
            return;
        }
        CHANNEL.sendToServer(new SaveDataOverridePacket(kind.wireName(), id == null ? "" : id, dataId == null ? "" : dataId, removed, payload));
    }

    public static void resetDataOverride(TaczDataKind kind, String id) {
        CHANNEL.sendToServer(new ResetDataOverridePacket(kind.wireName(), id == null ? "" : id));
    }

    public static void setDataRemoved(TaczDataKind kind, String id, boolean removed) {
        CHANNEL.sendToServer(new SetDataRemovedPacket(kind.wireName(), id == null ? "" : id, removed));
    }

    public static void saveDataBatch(JsonObject root) {
        String json = TaczRecipeCodec.GSON.toJson(root == null ? new JsonObject() : root);
        byte[] payload = compress(json);
        if (payload.length > MAX_COMPRESSED) {
            TaczClientHandler.handleToast(Component.translatable("msg.taczworkshop.data_too_large"));
            return;
        }
        CHANNEL.sendToServer(new SaveDataBatchPacket(payload));
    }

    public static void openFor(ServerPlayer player) {
        if (player != null && player.hasPermissions(2)) {
            CHANNEL.sendToPlayer(player, new OpenEditorPacket());
        }
    }

    public static void sendToast(ServerPlayer player, Component message) {
        if (player == null) return;
        CHANNEL.sendToPlayer(player, new ToastPacket(message));
    }

    public static void sendSaved(ServerPlayer player) {
        if (player == null) return;
        CHANNEL.sendToPlayer(player, new SavedPacket());
    }

    public static void completeDataBatchReload(ServerPlayer player, long batchId, int editCount) {
        if (player == null) return;
        CHANNEL.sendToPlayer(player, new DataBatchCommitPacket(batchId));
        sendSaved(player);
    }

    public static void failDataBatchReload(ServerPlayer player) {
        if (player == null) return;
        sendToast(player, Component.translatable("msg.taczworkshop.data_save_failed"));
    }

    private static void sendSnapshot(ServerPlayer player) {
        byte[] payload = compress(TaczRecipeRuntime.combinedSnapshotJson());
        if (payload.length > MAX_COMPRESSED) {
            sendToast(player, Component.translatable("msg.taczworkshop.sync_too_large"));
            return;
        }
        CHANNEL.sendToPlayer(player, new SnapshotPacket(payload));
    }

    private static void broadcastSnapshot(MinecraftServer server) {
        if (server == null) return;
        server.getPlayerList().getPlayers().stream()
                .filter(player -> player.hasPermissions(2))
                .forEach(TaczRecipeNetwork::sendSnapshot);
    }

    public static void sendRoutes(ServerPlayer player) {
        if (player == null) return;
        byte[] payload = compress(TaczRecipeRuntime.routeSnapshotJson());
        if (payload.length > MAX_COMPRESSED) return;
        CHANNEL.sendToPlayer(player, new RouteSnapshotPacket(payload));
    }

    public static void broadcastRoutes(MinecraftServer server) {
        if (server == null) return;
        server.getPlayerList().getPlayers().forEach(TaczRecipeNetwork::sendRoutes);
    }

    public static void sendDataList(ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) return;
        byte[] payload = compress(TaczRecipeCodec.GSON.toJson(TaczBaseDataCache.listSnapshot()));
        if (payload.length > MAX_COMPRESSED) {
            sendToast(player, Component.translatable("msg.taczworkshop.sync_too_large"));
            return;
        }
        CHANNEL.sendToPlayer(player, new DataListPacket(payload));
    }

    private static void sendDataDetail(ServerPlayer player, TaczDataKind kind, String id) {
        JsonObject detail = TaczBaseDataCache.detailSnapshot(kind, id);
        byte[] payload = compress(TaczRecipeCodec.GSON.toJson(detail));
        if (payload.length > MAX_COMPRESSED) {
            sendToast(player, Component.translatable("msg.taczworkshop.sync_too_large"));
            return;
        }
        CHANNEL.sendToPlayer(player, new DataDetailPacket(payload));
    }

    private static byte[] compress(String value) {
        return KineticCompression.compressUtf8(value, Integer.MAX_VALUE);
    }

    private static String decompress(byte[] payload) {
        return KineticCompression.decompressUtf8(payload, MAX_DECOMPRESSED);
    }

    private static void handleRequestSnapshot(RequestSnapshotPacket packet, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (player.hasPermissions(2)) sendSnapshot(player);
    }

    private static void handleSaveRecord(SaveRecordPacket packet, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) return;
        try {
            String json = decompress(packet.payload());
            TaczRecipeRecord record = TaczRecipeCodec.decodeRecord(json);
            TaczRecipeStore.saveEdited(record);
            TaczRecipeRuntime.applyAndSync(player.getServer());
            broadcastSnapshot(player.getServer());
            sendSaved(player);
        } catch (Exception exception) {
            TaczWorkshop.LOGGER.warn("Rejected recipe save from {}: {}", player.getGameProfile().getName(), safeMessage(exception));
            sendToast(player, Component.translatable("msg.taczworkshop.save_failed"));
        }
    }

    private static void handleDeleteRecord(DeleteRecordPacket packet, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) return;
        try {
            UUID.fromString(packet.uuid());
            if (TaczRecipeStore.delete(packet.uuid())) {
                TaczRecipeRuntime.applyAndSync(player.getServer());
                broadcastSnapshot(player.getServer());
                sendSaved(player);
            }
        } catch (Exception exception) {
            sendToast(player, Component.translatable("msg.taczworkshop.delete_failed"));
        }
    }

    private static void handleSetOriginalRecipeDisabled(SetOriginalRecipeDisabledPacket packet, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) return;
        try {
            TaczRecipeStore.setOriginalDisabled(packet.originalId(), packet.disabled());
            TaczRecipeRuntime.applyAndSync(player.getServer());
            broadcastSnapshot(player.getServer());
            sendSaved(player);
        } catch (Exception exception) {
            TaczWorkshop.LOGGER.warn("Rejected original recipe state change from {}: {}", player.getGameProfile().getName(), safeMessage(exception));
            sendToast(player, Component.translatable("msg.taczworkshop.save_failed"));
        }
    }

    private static void handleRestoreOriginalRecipe(RestoreOriginalRecipePacket packet, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) return;
        try {
            if (TaczRecipeStore.restoreOriginal(packet.originalId(), packet.replacementUuid())) {
                TaczRecipeRuntime.applyAndSync(player.getServer());
                broadcastSnapshot(player.getServer());
                sendSaved(player);
            }
        } catch (Exception exception) {
            TaczWorkshop.LOGGER.warn("Rejected original recipe restore from {}: {}", player.getGameProfile().getName(), safeMessage(exception));
            sendToast(player, Component.translatable("msg.taczworkshop.save_failed"));
        }
    }

    private static void handleRequestDataList(RequestDataListPacket packet, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (player.hasPermissions(2)) sendDataList(player);
    }

    private static void handleRequestDataDetail(RequestDataDetailPacket packet, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) return;
        try {
            sendDataDetail(player, TaczDataKind.fromWire(packet.kind()), packet.id());
        } catch (Exception exception) {
            sendToast(player, Component.translatable("msg.taczworkshop.data_detail_failed"));
        }
    }

    private static void handleSaveDataOverride(SaveDataOverridePacket packet, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) return;
        try {
            TaczDataKind parsedKind = TaczDataKind.fromWire(packet.kind());
            String json = decompress(packet.payload());
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("Override data is not an object");
            JsonObject data = parsed.getAsJsonObject();
            validateTaczData(parsedKind, data);
            TaczDataStore.upsert(new TaczDataOverride(parsedKind, packet.id(), packet.dataId(), packet.removed(), data));
            sendSaved(player);
            sendDataList(player);
            TaczDataRuntime.reloadTaczData();
        } catch (Exception exception) {
            TaczWorkshop.LOGGER.warn("Rejected TACZ data save from {}: {}", player.getGameProfile().getName(), safeMessage(exception));
            sendToast(player, Component.translatable("msg.taczworkshop.data_save_failed"));
        }
    }

    private static void handleResetDataOverride(ResetDataOverridePacket packet, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) return;
        try {
            TaczDataKind parsedKind = TaczDataKind.fromWire(packet.kind());
            TaczDataStore.reset(parsedKind, packet.id());
            sendSaved(player);
            sendDataList(player);
            TaczDataRuntime.reloadTaczData();
        } catch (Exception exception) {
            sendToast(player, Component.translatable("msg.taczworkshop.data_reset_failed"));
        }
    }

    private static void handleSetDataRemoved(SetDataRemovedPacket packet, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) return;
        try {
            TaczDataKind parsedKind = TaczDataKind.fromWire(packet.kind());
            TaczDataOverride existing = TaczDataStore.get(parsedKind, packet.id());
            if (packet.removed()) {
                if (existing == null) {
                    TaczDataStore.upsert(new TaczDataOverride(parsedKind, packet.id(), "", true, null));
                } else {
                    TaczDataStore.upsert(new TaczDataOverride(parsedKind, packet.id(), existing.dataId(), true, existing.data()));
                }
            } else if (existing != null) {
                if (existing.data() == null) {
                    TaczDataStore.reset(parsedKind, packet.id());
                } else {
                    TaczDataStore.upsert(new TaczDataOverride(parsedKind, packet.id(), existing.dataId(), false, existing.data()));
                }
            }
            sendSaved(player);
            sendDataList(player);
            TaczDataRuntime.reloadTaczData();
        } catch (Exception exception) {
            TaczWorkshop.LOGGER.warn("Rejected TACZ disabled-state change from {}: {}", player.getGameProfile().getName(), safeMessage(exception));
            sendToast(player, Component.translatable("msg.taczworkshop.data_save_failed"));
        }
    }

    private static void handleSaveDataBatch(SaveDataBatchPacket packet, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) return;
        try {
            String json = decompress(packet.payload());
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("Batch payload is not an object");
            JsonObject root = parsed.getAsJsonObject();
            long batchId = root.has("batch_id") && root.get("batch_id").isJsonPrimitive() ? root.get("batch_id").getAsLong() : 0L;
            if (batchId <= 0L) throw new IllegalArgumentException("Invalid data batch id");
            JsonArray edits = root.has("edits") && root.get("edits").isJsonArray() ? root.getAsJsonArray("edits") : new JsonArray();
            if (edits.size() > 4096) throw new IllegalArgumentException("Too many data edits");
            if (!TaczDataRuntime.canStartBatchReload()) throw new IllegalStateException("A server reload is already pending");

            Map<TaczDataKind, Map<String, TaczDataOverride>> previous = TaczDataStore.snapshot();
            Map<TaczDataKind, Map<String, TaczDataOverride>> next = TaczDataStore.snapshot();
            for (JsonElement element : edits) {
                if (!element.isJsonObject()) throw new IllegalArgumentException("Invalid data edit");
                JsonObject edit = element.getAsJsonObject();
                TaczDataKind kind = TaczDataKind.fromWire(readString(edit, "kind"));
                String id = TaczDataCodec.normalizeId(readString(edit, "id"));
                String op = readString(edit, "op");
                if ("reset".equals(op)) {
                    next.get(kind).remove(id);
                    continue;
                }
                if ("removed".equals(op)) {
                    boolean removed = edit.has("removed") && edit.get("removed").isJsonPrimitive() && edit.get("removed").getAsBoolean();
                    TaczDataOverride existing = next.get(kind).get(id);
                    if (removed) {
                        if (existing == null) next.get(kind).put(id, new TaczDataOverride(kind, id, "", true, null));
                        else next.get(kind).put(id, new TaczDataOverride(kind, id, existing.dataId(), true, existing.data()));
                    } else if (existing != null) {
                        if (existing.data() == null) next.get(kind).remove(id);
                        else next.get(kind).put(id, new TaczDataOverride(kind, id, existing.dataId(), false, existing.data()));
                    }
                    continue;
                }
                if (!"override".equals(op)) throw new IllegalArgumentException("Unknown data edit operation");
                if (!edit.has("data") || !edit.get("data").isJsonObject()) throw new IllegalArgumentException("Missing override data");
                JsonObject data = edit.getAsJsonObject("data");
                validateTaczData(kind, data);
                String dataId = readString(edit, "data_id");
                if (kind.usesInlineData() && dataId.isBlank()) dataId = id;
                boolean removed = edit.has("removed") && edit.get("removed").isJsonPrimitive() && edit.get("removed").getAsBoolean();
                next.get(kind).put(id, new TaczDataOverride(kind, id, dataId, removed, data));
            }

            TaczDataStore.replaceAll(next);
            try {
                TaczDataRuntime.requestDataBatchReload(player, batchId, edits.size());
            } catch (Exception reloadException) {
                try {
                    TaczDataStore.replaceAll(previous);
                } catch (Exception rollbackException) {
                    reloadException.addSuppressed(rollbackException);
                }
                throw reloadException;
            }
        } catch (Exception exception) {
            TaczWorkshop.LOGGER.warn("Rejected TACZ data batch save from {}: {}", player.getGameProfile().getName(), safeMessage(exception));
            sendToast(player, Component.translatable("msg.taczworkshop.data_save_failed"));
        }
    }

    private static void validateTaczData(TaczDataKind kind, JsonObject data) {
        switch (kind) {
            case GUN -> CommonAssetsManager.GSON.fromJson(data, GunData.class);
            case ATTACHMENT -> CommonAssetsManager.GSON.fromJson(data, AttachmentData.class);
            case AMMO -> CommonAssetsManager.GSON.fromJson(data, CommonAmmoIndex.class);
            case MELEE, THROWABLE, CONSUMABLE -> {
                if (data == null) throw new IllegalArgumentException("Missing external data object");
            }
        }
    }

    private static String readString(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return "";
        return object.get(key).getAsString().trim();
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) return "unknown";
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    public record OpenEditorPacket() {
        private OpenEditorPacket(NetworkBuffer buffer) { this(); }
        private void encode(NetworkBuffer buffer) { }
    }

    public record RequestSnapshotPacket() {
        private RequestSnapshotPacket(NetworkBuffer buffer) { this(); }
        private void encode(NetworkBuffer buffer) { }
    }

    public record SnapshotPacket(byte[] payload) {
        private SnapshotPacket(NetworkBuffer buffer) { this(buffer.readByteArray(MAX_COMPRESSED)); }
        private void encode(NetworkBuffer buffer) { buffer.writeByteArray(payload, MAX_COMPRESSED); }
    }

    public record RouteSnapshotPacket(byte[] payload) {
        private RouteSnapshotPacket(NetworkBuffer buffer) { this(buffer.readByteArray(MAX_COMPRESSED)); }
        private void encode(NetworkBuffer buffer) { buffer.writeByteArray(payload, MAX_COMPRESSED); }
    }

    public record SaveRecordPacket(byte[] payload) {
        private SaveRecordPacket(NetworkBuffer buffer) { this(buffer.readByteArray(MAX_COMPRESSED)); }
        private void encode(NetworkBuffer buffer) { buffer.writeByteArray(payload, MAX_COMPRESSED); }
    }

    public record DeleteRecordPacket(String uuid) {
        private DeleteRecordPacket(NetworkBuffer buffer) { this(buffer.readUtf(64)); }
        private void encode(NetworkBuffer buffer) { buffer.writeUtf(uuid, 64); }
    }

    public record SetOriginalRecipeDisabledPacket(String originalId, boolean disabled) {
        private SetOriginalRecipeDisabledPacket(NetworkBuffer buffer) { this(buffer.readUtf(512), buffer.readBoolean()); }
        private void encode(NetworkBuffer buffer) { buffer.writeUtf(originalId, 512); buffer.writeBoolean(disabled); }
    }

    public record RestoreOriginalRecipePacket(String originalId, String replacementUuid) {
        private RestoreOriginalRecipePacket(NetworkBuffer buffer) { this(buffer.readUtf(512), buffer.readUtf(64)); }
        private void encode(NetworkBuffer buffer) { buffer.writeUtf(originalId, 512); buffer.writeUtf(replacementUuid, 64); }
    }

    public record RequestDataListPacket() {
        private RequestDataListPacket(NetworkBuffer buffer) { this(); }
        private void encode(NetworkBuffer buffer) { }
    }

    public record DataListPacket(byte[] payload) {
        private DataListPacket(NetworkBuffer buffer) { this(buffer.readByteArray(MAX_COMPRESSED)); }
        private void encode(NetworkBuffer buffer) { buffer.writeByteArray(payload, MAX_COMPRESSED); }
    }

    public record RequestDataDetailPacket(String kind, String id) {
        private RequestDataDetailPacket(NetworkBuffer buffer) { this(buffer.readUtf(16), buffer.readUtf(256)); }
        private void encode(NetworkBuffer buffer) { buffer.writeUtf(kind, 16); buffer.writeUtf(id, 256); }
    }

    public record DataDetailPacket(byte[] payload) {
        private DataDetailPacket(NetworkBuffer buffer) { this(buffer.readByteArray(MAX_COMPRESSED)); }
        private void encode(NetworkBuffer buffer) { buffer.writeByteArray(payload, MAX_COMPRESSED); }
    }

    public record SaveDataOverridePacket(String kind, String id, String dataId, boolean removed, byte[] payload) {
        private SaveDataOverridePacket(NetworkBuffer buffer) {
            this(buffer.readUtf(16), buffer.readUtf(256), buffer.readUtf(256), buffer.readBoolean(), buffer.readByteArray(MAX_COMPRESSED));
        }
        private void encode(NetworkBuffer buffer) {
            buffer.writeUtf(kind, 16);
            buffer.writeUtf(id, 256);
            buffer.writeUtf(dataId, 256);
            buffer.writeBoolean(removed);
            buffer.writeByteArray(payload, MAX_COMPRESSED);
        }
    }

    public record ResetDataOverridePacket(String kind, String id) {
        private ResetDataOverridePacket(NetworkBuffer buffer) { this(buffer.readUtf(16), buffer.readUtf(256)); }
        private void encode(NetworkBuffer buffer) { buffer.writeUtf(kind, 16); buffer.writeUtf(id, 256); }
    }

    public record SetDataRemovedPacket(String kind, String id, boolean removed) {
        private SetDataRemovedPacket(NetworkBuffer buffer) { this(buffer.readUtf(16), buffer.readUtf(256), buffer.readBoolean()); }
        private void encode(NetworkBuffer buffer) { buffer.writeUtf(kind, 16); buffer.writeUtf(id, 256); buffer.writeBoolean(removed); }
    }

    public record ToastPacket(Component message) {
        private ToastPacket(NetworkBuffer buffer) { this(buffer.readComponent()); }
        private void encode(NetworkBuffer buffer) { buffer.writeComponent(message); }
    }

    public record SaveDataBatchPacket(byte[] payload) {
        private SaveDataBatchPacket(NetworkBuffer buffer) { this(buffer.readByteArray(MAX_COMPRESSED)); }
        private void encode(NetworkBuffer buffer) { buffer.writeByteArray(payload, MAX_COMPRESSED); }
    }

    public record DataBatchCommitPacket(long batchId) {
        private DataBatchCommitPacket(NetworkBuffer buffer) { this(buffer.readLong()); }
        private void encode(NetworkBuffer buffer) { buffer.writeLong(batchId); }
    }

    public record SavedPacket() {
        private SavedPacket(NetworkBuffer buffer) { this(); }
        private void encode(NetworkBuffer buffer) { }
    }
}
