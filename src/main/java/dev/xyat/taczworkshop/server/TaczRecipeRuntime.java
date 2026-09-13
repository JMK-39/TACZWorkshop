package dev.xyat.taczworkshop.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonBlockIndex;
import dev.xyat.taczworkshop.TaczWorkshop;
import dev.xyat.taczworkshop.data.TaczRecipeCodec;
import dev.xyat.taczworkshop.data.TaczRecipeRecord;
import dev.xyat.taczworkshop.network.TaczRecipeNetwork;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = TaczWorkshop.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TaczRecipeRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, GunSmithTableRecipe> BASE_RECIPES = new LinkedHashMap<>();
    private static final Set<ResourceLocation> ACTIVE_IDS = new HashSet<>();
    private static final Set<ResourceLocation> DISABLED_BASE_IDS = new HashSet<>();
    private static final Map<ResourceLocation, Recipe<?>> REPLACED_BASE = new HashMap<>();
    private static final Map<ResourceLocation, Set<ResourceLocation>> ROUTES = new HashMap<>();

    private TaczRecipeRuntime() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        resetRuntimeState();
        captureBaseRecipes(event.getServer());
        apply(event.getServer(), false);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        resetRuntimeState();
        BASE_RECIPES.clear();
        TaczRecipeIssueRegistry.clear();
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) return;
        resetRuntimeState();
        MinecraftServer server = event.getPlayerList().getServer();
        captureBaseRecipes(server);
        apply(server, true);
        TaczRecipeNetwork.broadcastRoutes(server);
    }

    public static synchronized void applyAndSync(MinecraftServer server) {
        apply(server, true);
        TaczRecipeNetwork.broadcastRoutes(server);
    }

    public static synchronized String combinedSnapshotJson() {
        return TaczRecipeCodec.encodeRoot(combinedSnapshot());
    }

    public static synchronized List<TaczRecipeRecord> combinedSnapshot() {
        TaczRecipeStore.State state = TaczRecipeStore.loadState();
        List<TaczRecipeRecord> result = new ArrayList<>();
        Set<String> suppressedIssueIds = new HashSet<>(state.disabledOriginals());
        for (TaczRecipeRecord stored : state.recipes()) {
            if (stored != null && !stored.id().isBlank()) suppressedIssueIds.add(stored.id());
        }
        result.addAll(TaczRecipeIssueRegistry.snapshotRecords(suppressedIssueIds));

        for (Map.Entry<ResourceLocation, GunSmithTableRecipe> entry : BASE_RECIPES.entrySet()) {
            String originalId = entry.getKey().toString();
            boolean enabled = !state.disabledOriginals().contains(originalId);
            result.add(TaczRecipeCodec.fromNativeRecipe(entry.getValue(), detectWorkbenches(entry.getValue()), enabled));
        }

        for (TaczRecipeRecord stored : state.recipes()) {
            TaczRecipeRecord created = stored.copy();
            if (created.isReplacement()) created.setOrigin(TaczRecipeRecord.ORIGIN_CUSTOM);
            if (created.workbenches().isEmpty() && !created.originalId().isBlank()) {
                ResourceLocation originalId = ResourceLocation.tryParse(created.originalId());
                GunSmithTableRecipe original = originalId == null ? null : BASE_RECIPES.get(originalId);
                if (original != null) created.setWorkbenches(detectWorkbenches(original));
            }
            result.add(created);
        }
        return result;
    }

    public static synchronized String routeSnapshotJson() {
        JsonObject root = new JsonObject();
        ROUTES.forEach((recipeId, workbenches) -> {
            JsonArray array = new JsonArray();
            workbenches.forEach(id -> array.add(id.toString()));
            root.add(recipeId.toString(), array);
        });
        return TaczRecipeCodec.GSON.toJson(root);
    }

    public static synchronized boolean routeAllows(ResourceLocation recipeId, ResourceLocation blockId) {
        if (recipeId == null || blockId == null) return false;
        Set<ResourceLocation> allowed = ROUTES.get(recipeId);
        return allowed != null && allowed.contains(blockId);
    }

    public static synchronized boolean hasManagedRoute(ResourceLocation recipeId) {
        return recipeId != null && ROUTES.containsKey(recipeId);
    }

    private static synchronized void captureBaseRecipes(MinecraftServer server) {
        BASE_RECIPES.clear();
        if (server == null) return;
        for (GunSmithTableRecipe recipe : server.getRecipeManager().getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get())) {
            if (recipe != null && recipe.getId() != null) BASE_RECIPES.put(recipe.getId(), recipe);
        }
        LOGGER.info("Captured TACZ base recipes: {}", BASE_RECIPES.size());
    }

    private static synchronized void apply(MinecraftServer server, boolean syncPlayers) {
        if (server == null) return;

        Map<ResourceLocation, Recipe<?>> merged = new LinkedHashMap<>();
        for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
            if (recipe != null && recipe.getId() != null) merged.put(recipe.getId(), recipe);
        }

        Set<ResourceLocation> previousActive = new HashSet<>(ACTIVE_IDS);
        for (ResourceLocation id : previousActive) {
            merged.remove(id);
            Recipe<?> base = REPLACED_BASE.get(id);
            if (base != null) merged.put(id, base);
        }
        for (ResourceLocation id : new HashSet<>(DISABLED_BASE_IDS)) {
            GunSmithTableRecipe base = BASE_RECIPES.get(id);
            if (base != null) merged.put(id, base);
        }

        TaczRecipeStore.State state = TaczRecipeStore.loadState();
        Set<ResourceLocation> effectiveDisabled = new LinkedHashSet<>();
        for (String raw : state.disabledOriginals()) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id != null) effectiveDisabled.add(id);
        }
        effectiveDisabled.forEach(merged::remove);

        Set<ResourceLocation> nextActive = new HashSet<>();
        Map<ResourceLocation, Set<ResourceLocation>> nextRoutes = new HashMap<>();
        int loaded = 0;
        int skipped = 0;

        for (TaczRecipeRecord record : state.recipes()) {
            if (!record.enabled()) continue;
            try {
                ResourceLocation id = ResourceLocation.tryParse(record.id());
                if (id == null) throw new IllegalArgumentException("invalid recipe id");
                if (!previousActive.contains(id) && merged.containsKey(id)) REPLACED_BASE.putIfAbsent(id, merged.get(id));
                Recipe<?> recipe = TaczRecipeCodec.validateAndBuild(record);
                merged.put(id, recipe);
                nextActive.add(id);
                Set<ResourceLocation> workbenches = parseWorkbenches(record.workbenches());
                if (workbenches.isEmpty() && !record.originalId().isBlank()) {
                    ResourceLocation originalId = ResourceLocation.tryParse(record.originalId());
                    GunSmithTableRecipe original = originalId == null ? null : BASE_RECIPES.get(originalId);
                    if (original != null) workbenches = parseWorkbenches(detectWorkbenches(original));
                }
                if (!workbenches.isEmpty()) nextRoutes.put(id, workbenches);
                loaded++;
            } catch (Exception exception) {
                skipped++;
                LOGGER.warn("Skipping TaczWorkshop recipe {}: {}", record.id(), safeMessage(exception));
            }
        }

        for (ResourceLocation oldId : previousActive) {
            if (!nextActive.contains(oldId)) REPLACED_BASE.remove(oldId);
        }

        ACTIVE_IDS.clear();
        ACTIVE_IDS.addAll(nextActive);
        DISABLED_BASE_IDS.clear();
        DISABLED_BASE_IDS.addAll(effectiveDisabled);
        ROUTES.clear();
        ROUTES.putAll(nextRoutes);

        List<Recipe<?>> finalRecipes = new ArrayList<>(merged.values());
        server.getRecipeManager().replaceRecipes(finalRecipes);

        if (syncPlayers) {
            ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(finalRecipes);
            server.getPlayerList().getPlayers().forEach(player -> player.connection.send(packet));
        }

        LOGGER.info("Applied TaczWorkshop recipes: loaded={}, disabled_originals={}, skipped={}, total={}", loaded, effectiveDisabled.size(), skipped, finalRecipes.size());
    }

    public static List<String> detectWorkbenches(GunSmithTableRecipe recipe) {
        List<String> result = new ArrayList<>();
        if (recipe == null || recipe.getId() == null) return result;
        for (Map.Entry<ResourceLocation, CommonBlockIndex> entry : CommonAssetsManager.get().getAllBlocks()) {
            ResourceLocation blockId = entry.getKey();
            CommonBlockIndex index = entry.getValue();
            if (blockId == null || index == null) continue;
            boolean bypass = DefaultAssets.DEFAULT_BLOCK_ID.equals(blockId) && !SyncConfig.ENABLE_TABLE_FILTER.get();
            boolean filterAllowed = bypass || index.getFilter() == null || index.getFilter().contains(recipe.getId());
            boolean tabAllowed = bypass || index.getData().getTabs().stream().anyMatch(tab -> tab.id().equals(recipe.getTab()));
            if (filterAllowed && tabAllowed) result.add(blockId.toString());
        }
        return result;
    }

    private static Set<ResourceLocation> parseWorkbenches(List<String> raw) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        if (raw == null) return result;
        for (String value : raw) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id != null) result.add(id);
        }
        return result;
    }

    private static void resetRuntimeState() {
        ACTIVE_IDS.clear();
        DISABLED_BASE_IDS.clear();
        REPLACED_BASE.clear();
        ROUTES.clear();
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) return "unknown";
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
