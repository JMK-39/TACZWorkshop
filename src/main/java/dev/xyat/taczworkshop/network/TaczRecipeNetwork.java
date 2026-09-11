package dev.xyat.taczworkshop.network;

import dev.xyat.kineticcore.api.KTNetworkProtocol;
import dev.xyat.kineticcore.api.NetworkCompressUtil;
import dev.xyat.taczworkshop.TaczWorkshop;
import dev.xyat.taczworkshop.client.TaczClientHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class TaczRecipeNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final int MAX_COMPRESSED = 4 * 1024 * 1024;
    private static final int MAX_DECOMPRESSED = 32 * 1024 * 1024;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Objects.requireNonNull(ResourceLocation.tryParse(TaczWorkshop.MODID + ":main")),
            () -> PROTOCOL_VERSION,
            KTNetworkProtocol::acceptsAnyVersion,
            KTNetworkProtocol::acceptsAnyVersion
    );

    private TaczRecipeNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenEditorPacket.class, OpenEditorPacket::encode, OpenEditorPacket::new, OpenEditorPacket::handle);
        CHANNEL.registerMessage(id++, RequestSnapshotPacket.class, RequestSnapshotPacket::encode, RequestSnapshotPacket::new, RequestSnapshotPacket::handle);
        CHANNEL.registerMessage(id++, SnapshotPacket.class, SnapshotPacket::encode, SnapshotPacket::new, SnapshotPacket::handle);
        CHANNEL.registerMessage(id++, RouteSnapshotPacket.class, RouteSnapshotPacket::encode, RouteSnapshotPacket::new, RouteSnapshotPacket::handle);
        CHANNEL.registerMessage(id++, SaveRecordPacket.class, SaveRecordPacket::encode, SaveRecordPacket::new, SaveRecordPacket::handle);
        CHANNEL.registerMessage(id++, DeleteRecordPacket.class, DeleteRecordPacket::encode, DeleteRecordPacket::new, DeleteRecordPacket::handle);
        CHANNEL.registerMessage(id++, RequestDataListPacket.class, RequestDataListPacket::encode, RequestDataListPacket::new, RequestDataListPacket::handle);
        CHANNEL.registerMessage(id++, DataListPacket.class, DataListPacket::encode, DataListPacket::new, DataListPacket::handle);
        CHANNEL.registerMessage(id++, RequestDataDetailPacket.class, RequestDataDetailPacket::encode, RequestDataDetailPacket::new, RequestDataDetailPacket::handle);
        CHANNEL.registerMessage(id++, DataDetailPacket.class, DataDetailPacket::encode, DataDetailPacket::new, DataDetailPacket::handle);
        CHANNEL.registerMessage(id++, SaveDataOverridePacket.class, SaveDataOverridePacket::encode, SaveDataOverridePacket::new, SaveDataOverridePacket::handle);
        CHANNEL.registerMessage(id++, ResetDataOverridePacket.class, ResetDataOverridePacket::encode, ResetDataOverridePacket::new, ResetDataOverridePacket::handle);
        CHANNEL.registerMessage(id++, SetDataRemovedPacket.class, SetDataRemovedPacket::encode, SetDataRemovedPacket::new, SetDataRemovedPacket::handle);
        CHANNEL.registerMessage(id++, SetOriginalRecipeDisabledPacket.class, SetOriginalRecipeDisabledPacket::encode, SetOriginalRecipeDisabledPacket::new, SetOriginalRecipeDisabledPacket::handle);
        CHANNEL.registerMessage(id++, RestoreOriginalRecipePacket.class, RestoreOriginalRecipePacket::encode, RestoreOriginalRecipePacket::new, RestoreOriginalRecipePacket::handle);
        CHANNEL.registerMessage(id++, ToastPacket.class, ToastPacket::encode, ToastPacket::new, ToastPacket::handle);
        CHANNEL.registerMessage(id++, SaveDataBatchPacket.class, SaveDataBatchPacket::encode, SaveDataBatchPacket::new, SaveDataBatchPacket::handle);
        CHANNEL.registerMessage(id++, DataBatchCommitPacket.class, DataBatchCommitPacket::encode, DataBatchCommitPacket::new, DataBatchCommitPacket::handle);
        CHANNEL.registerMessage(id, SavedPacket.class, SavedPacket::encode, SavedPacket::new, SavedPacket::handle);
    }

    public static void requestSnapshot() {
        CHANNEL.sendToServer(new RequestSnapshotPacket());
    }

    public static void saveRecord(TaczRecipeRecord record) {
        byte[] payload = NetworkCompressUtil.compress(TaczRecipeCodec.encodeRecord(record));
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
        byte[] payload = NetworkCompressUtil.compress(json);
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
        byte[] payload = NetworkCompressUtil.compress(json);
        if (payload.length > MAX_COMPRESSED) {
            TaczClientHandler.handleToast(Component.translatable("msg.taczworkshop.data_too_large"));
            return;
        }
        CHANNEL.sendToServer(new SaveDataBatchPacket(payload));
    }

    public static void openFor(ServerPlayer player) {
        if (player != null && player.hasPermissions(2)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenEditorPacket());
        }
    }

    public static void sendToast(ServerPlayer player, Component message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ToastPacket(message));
    }

    public static void sendSaved(ServerPlayer player) {
        if (player == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SavedPacket());
    }

    public static void completeDataBatchReload(ServerPlayer player, long batchId, int editCount) {
        if (player == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DataBatchCommitPacket(batchId));
        sendSaved(player);
    }

    public static void failDataBatchReload(ServerPlayer player) {
        if (player == null) return;
        sendToast(player, Component.translatable("msg.taczworkshop.data_save_failed"));
    }

    private static void sendSnapshot(ServerPlayer player) {
        byte[] payload = NetworkCompressUtil.compress(TaczRecipeRuntime.combinedSnapshotJson());
        if (payload.length > MAX_COMPRESSED) {
            sendToast(player, Component.translatable("msg.taczworkshop.sync_too_large"));
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SnapshotPacket(payload));
    }

    private static void broadcastSnapshot(MinecraftServer server) {
        if (server == null) return;
        server.getPlayerList().getPlayers().stream()
                .filter(player -> player.hasPermissions(2))
                .forEach(TaczRecipeNetwork::sendSnapshot);
    }

    public static void sendRoutes(ServerPlayer player) {
        if (player == null) return;
        byte[] payload = NetworkCompressUtil.compress(TaczRecipeRuntime.routeSnapshotJson());
        if (payload.length > MAX_COMPRESSED) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new RouteSnapshotPacket(payload));
    }

    public static void broadcastRoutes(MinecraftServer server) {
        if (server == null) return;
        server.getPlayerList().getPlayers().forEach(TaczRecipeNetwork::sendRoutes);
    }

    public static void sendDataList(ServerPlayer player) {
        if (player == null || !player.hasPermissions(2)) return;
        byte[] payload = NetworkCompressUtil.compress(TaczRecipeCodec.GSON.toJson(TaczBaseDataCache.listSnapshot()));
        if (payload.length > MAX_COMPRESSED) {
            sendToast(player, Component.translatable("msg.taczworkshop.sync_too_large"));
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DataListPacket(payload));
    }

    private static void sendDataDetail(ServerPlayer player, TaczDataKind kind, String id) {
        JsonObject detail = TaczBaseDataCache.detailSnapshot(kind, id);
        byte[] payload = NetworkCompressUtil.compress(TaczRecipeCodec.GSON.toJson(detail));
        if (payload.length > MAX_COMPRESSED) {
            sendToast(player, Component.translatable("msg.taczworkshop.sync_too_large"));
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DataDetailPacket(payload));
    }

    public record OpenEditorPacket() {
        public OpenEditorPacket(FriendlyByteBuf buf) {
            this();
        }

        public void encode(FriendlyByteBuf buf) {
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> TaczClientHandler::openEditor));
            context.get().setPacketHandled(true);
        }
    }

    public record RequestSnapshotPacket() {
        public RequestSnapshotPacket(FriendlyByteBuf buf) {
            this();
        }

        public void encode(FriendlyByteBuf buf) {
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                ServerPlayer player = context.get().getSender();
                if (player != null && player.hasPermissions(2)) sendSnapshot(player);
            });
            context.get().setPacketHandled(true);
        }
    }

    public record SnapshotPacket(byte[] payload) {
        public SnapshotPacket(FriendlyByteBuf buf) {
            this(buf.readByteArray(MAX_COMPRESSED));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByteArray(payload);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                String json = NetworkCompressUtil.decompress(payload, MAX_DECOMPRESSED);
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TaczClientHandler.handleSnapshot(json));
            });
            context.get().setPacketHandled(true);
        }
    }

    public record RouteSnapshotPacket(byte[] payload) {
        public RouteSnapshotPacket(FriendlyByteBuf buf) {
            this(buf.readByteArray(MAX_COMPRESSED));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByteArray(payload);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                String json = NetworkCompressUtil.decompress(payload, MAX_DECOMPRESSED);
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TaczClientHandler.handleRoutes(json));
            });
            context.get().setPacketHandled(true);
        }
    }

    public record SaveRecordPacket(byte[] payload) {
        public SaveRecordPacket(FriendlyByteBuf buf) {
            this(buf.readByteArray(MAX_COMPRESSED));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByteArray(payload);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                ServerPlayer player = context.get().getSender();
                if (player == null || !player.hasPermissions(2)) return;
                try {
                    String json = NetworkCompressUtil.decompress(payload, MAX_DECOMPRESSED);
                    TaczRecipeRecord record = TaczRecipeCodec.decodeRecord(json);
                    TaczRecipeStore.saveEdited(record);
                    TaczRecipeRuntime.applyAndSync(player.getServer());
                    broadcastSnapshot(player.getServer());
                    sendSaved(player);
                } catch (Exception exception) {
                    TaczWorkshop.LOGGER.warn("Rejected recipe save from {}: {}", player.getGameProfile().getName(), safeMessage(exception));
                    sendToast(player, Component.translatable("msg.taczworkshop.save_failed"));
                }
            });
            context.get().setPacketHandled(true);
        }
    }

    public record DeleteRecordPacket(String uuid) {
        public DeleteRecordPacket(FriendlyByteBuf buf) {
            this(buf.readUtf(64));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(uuid, 64);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                ServerPlayer player = context.get().getSender();
                if (player == null || !player.hasPermissions(2)) return;
                try {
                    UUID.fromString(uuid);
                    if (TaczRecipeStore.delete(uuid)) {
                        TaczRecipeRuntime.applyAndSync(player.getServer());
                        broadcastSnapshot(player.getServer());
                        sendSaved(player);
                    }
                } catch (Exception exception) {
                    sendToast(player, Component.translatable("msg.taczworkshop.delete_failed"));
                }
            });
            context.get().setPacketHandled(true);
        }
    }

    public record SetOriginalRecipeDisabledPacket(String originalId, boolean disabled) {
        public SetOriginalRecipeDisabledPacket(FriendlyByteBuf buf) {
            this(buf.readUtf(512), buf.readBoolean());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(originalId, 512);
            buf.writeBoolean(disabled);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                ServerPlayer player = context.get().getSender();
                if (player == null || !player.hasPermissions(2)) return;
                try {
                    TaczRecipeStore.setOriginalDisabled(originalId, disabled);
                    TaczRecipeRuntime.applyAndSync(player.getServer());
                    broadcastSnapshot(player.getServer());
                    sendSaved(player);
                } catch (Exception exception) {
                    TaczWorkshop.LOGGER.warn("Rejected original recipe state change from {}: {}", player.getGameProfile().getName(), safeMessage(exception));
                    sendToast(player, Component.translatable("msg.taczworkshop.save_failed"));
                }
            });
            context.get().setPacketHandled(true);
        }
    }

    public record RestoreOriginalRecipePacket(String originalId, String replacementUuid) {
        public RestoreOriginalRecipePacket(FriendlyByteBuf buf) {
            this(buf.readUtf(512), buf.readUtf(64));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(originalId, 512);
            buf.writeUtf(replacementUuid, 64);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                ServerPlayer player = context.get().getSender();
                if (player == null || !player.hasPermissions(2)) return;
                try {
                    if (TaczRecipeStore.restoreOriginal(originalId, replacementUuid)) {
                        TaczRecipeRuntime.applyAndSync(player.getServer());
                        broadcastSnapshot(player.getServer());
                        sendSaved(player);
                    }
                } catch (Exception exception) {
                    TaczWorkshop.LOGGER.warn("Rejected original recipe restore from {}: {}", player.getGameProfile().getName(), safeMessage(exception));
                    sendToast(player, Component.translatable("msg.taczworkshop.save_failed"));
                }
            });
            context.get().setPacketHandled(true);
        }
    }

    public record RequestDataListPacket() {
        public RequestDataListPacket(FriendlyByteBuf buf) {
            this();
        }

        public void encode(FriendlyByteBuf buf) {
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                ServerPlayer player = context.get().getSender();
                if (player != null && player.hasPermissions(2)) sendDataList(player);
            });
            context.get().setPacketHandled(true);
        }
    }

    public record DataListPacket(byte[] payload) {
        public DataListPacket(FriendlyByteBuf buf) {
            this(buf.readByteArray(MAX_COMPRESSED));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByteArray(payload);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                String json = NetworkCompressUtil.decompress(payload, MAX_DECOMPRESSED);
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TaczClientHandler.handleDataList(json));
            });
            context.get().setPacketHandled(true);
        }
    }

    public record RequestDataDetailPacket(String kind, String id) {
        public RequestDataDetailPacket(FriendlyByteBuf buf) {
            this(buf.readUtf(16), buf.readUtf(256));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(kind, 16);
            buf.writeUtf(id, 256);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                ServerPlayer player = context.get().getSender();
                if (player == null || !player.hasPermissions(2)) return;
                try {
                    sendDataDetail(player, TaczDataKind.fromWire(kind), id);
                } catch (Exception exception) {
                    sendToast(player, Component.translatable("msg.taczworkshop.data_detail_failed"));
                }
            });
            context.get().setPacketHandled(true);
        }
    }

    public record DataDetailPacket(byte[] payload) {
        public DataDetailPacket(FriendlyByteBuf buf) {
            this(buf.readByteArray(MAX_COMPRESSED));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByteArray(payload);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                String json = NetworkCompressUtil.decompress(payload, MAX_DECOMPRESSED);
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TaczClientHandler.handleDataDetail(json));
            });
            context.get().setPacketHandled(true);
        }
    }

    public record SaveDataOverridePacket(String kind, String id, String dataId, boolean removed, byte[] payload) {
        public SaveDataOverridePacket(FriendlyByteBuf buf) {
            this(buf.readUtf(16), buf.readUtf(256), buf.readUtf(256), buf.readBoolean(), buf.readByteArray(MAX_COMPRESSED));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(kind, 16);
            buf.writeUtf(id, 256);
            buf.writeUtf(dataId, 256);
            buf.writeBoolean(removed);
            buf.writeByteArray(payload);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                ServerPlayer player = context.get().getSender();
                if (player == null || !player.hasPermissions(2)) return;
                try {
                    TaczDataKind parsedKind = TaczDataKind.fromWire(kind);
                    String json = NetworkCompressUtil.decompress(payload, MAX_DECOMPRESSED);
                    JsonElement parsed = JsonParser.parseString(json);
                    if (!parsed.isJsonObject()) throw new IllegalArgumentException("Override data is not an object");
                    JsonObject data = parsed.getAsJsonObject();
                    validateTaczData(parsedKind, data);
                    TaczDataStore.upsert(new TaczDataOverride(parsedKind, id, dataId, removed, data));
                    sendSaved(player);
                    sendDataList(player);
                    TaczDataRuntime.reloadTaczData();
                } catch (Exception exception) {
                    TaczWorkshop.LOGGER.warn("Rejected TACZ data save from {}: {}", player.getGameProfile().getName(), safeMessage(exception));
                    sendToast(player, Component.translatable("msg.taczworkshop.data_save_failed"));
                }
            });
            context.get().setPacketHandled(true);
        }
    }

    public record ResetDataOverridePacket(String kind, String id) {
        public ResetDataOverridePacket(FriendlyByteBuf buf) {
            this(buf.readUtf(16), buf.readUtf(256));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(kind, 16);
            buf.writeUtf(id, 256);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                ServerPlayer player = context.get().getSender();
                if (player == null || !player.hasPermissions(2)) return;
                try {
                    TaczDataKind parsedKind = TaczDataKind.fromWire(kind);
                    TaczDataStore.reset(parsedKind, id);
                    sendSaved(player);
                    sendDataList(player);
                    TaczDataRuntime.reloadTaczData();
                } catch (Exception exception) {
                    sendToast(player, Component.translatable("msg.taczworkshop.data_reset_failed"));
                }
            });
            context.get().setPacketHandled(true);
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

    public record SetDataRemovedPacket(String kind, String id, boolean removed) {
        public SetDataRemovedPacket(FriendlyByteBuf buf) {
            this(buf.readUtf(16), buf.readUtf(256), buf.readBoolean());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(kind, 16);
            buf.writeUtf(id, 256);
            buf.writeBoolean(removed);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                ServerPlayer player = context.get().getSender();
                if (player == null || !player.hasPermissions(2)) return;
                try {
                    TaczDataKind parsedKind = TaczDataKind.fromWire(kind);
                    TaczDataOverride existing = TaczDataStore.get(parsedKind, id);
                    if (removed) {
                        if (existing == null) {
                            TaczDataStore.upsert(new TaczDataOverride(parsedKind, id, "", true, null));
                        } else {
                            TaczDataStore.upsert(new TaczDataOverride(parsedKind, id, existing.dataId(), true, existing.data()));
                        }
                    } else if (existing != null) {
                        if (existing.data() == null) {
                            TaczDataStore.reset(parsedKind, id);
                        } else {
                            TaczDataStore.upsert(new TaczDataOverride(parsedKind, id, existing.dataId(), false, existing.data()));
                        }
                    }
                    sendSaved(player);
                    sendDataList(player);
                    TaczDataRuntime.reloadTaczData();
                } catch (Exception exception) {
                    TaczWorkshop.LOGGER.warn("Rejected TACZ disabled-state change from {}: {}", player.getGameProfile().getName(), safeMessage(exception));
                    sendToast(player, Component.translatable("msg.taczworkshop.data_save_failed"));
                }
            });
            context.get().setPacketHandled(true);
        }
    }

    public record SaveDataBatchPacket(byte[] payload) {
        public SaveDataBatchPacket(FriendlyByteBuf buf) {
            this(buf.readByteArray(MAX_COMPRESSED));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByteArray(payload);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                ServerPlayer player = context.get().getSender();
                if (player == null || !player.hasPermissions(2)) return;
                try {
                    String json = NetworkCompressUtil.decompress(payload, MAX_DECOMPRESSED);
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
            });
            context.get().setPacketHandled(true);
        }
    }

    public record DataBatchCommitPacket(long batchId) {
        public DataBatchCommitPacket(FriendlyByteBuf buf) {
            this(buf.readLong());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeLong(batchId);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TaczClientHandler.handleDataBatchCommit(batchId)));
            context.get().setPacketHandled(true);
        }
    }

    public record ToastPacket(Component message) {
        public ToastPacket(FriendlyByteBuf buf) {
            this(buf.readComponent());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeComponent(message);
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TaczClientHandler.handleToast(message)));
            context.get().setPacketHandled(true);
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

    public record SavedPacket() {
        public SavedPacket(FriendlyByteBuf buf) {
            this();
        }

        public void encode(FriendlyByteBuf buf) {
        }

        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> TaczClientHandler::handleSaved
            ));
            context.get().setPacketHandled(true);
        }
    }

}
