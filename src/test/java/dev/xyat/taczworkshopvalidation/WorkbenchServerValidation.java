package dev.xyat.taczworkshopvalidation;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.inventory.GunSmithTableMenu;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.network.PacketChannel;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import dev.xyat.taczworkshop.crafting.*;
import dev.xyat.taczworkshop.network.WorkbenchNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Opt-in real API validation. Uses a fake player and temporary air-only blocks, never a user's inventory/config. */
public final class WorkbenchServerValidation {
    private static final Logger LOG = LoggerFactory.getLogger(WorkbenchServerValidation.class);
    private static final int CLIENT_MENU = 9182;
    private static final List<String> checks = new ArrayList<>();
    private static final Set<BlockPos> placed = new LinkedHashSet<>();
    private static ServerLevel level;
    private static FakePlayer player;
    private static BlockPos root, controller;
    private static GunSmithTableBlockEntity table;
    private static GunSmithTableMenu menu;
    private static GunSmithTableRecipe original;
    private static ItemStack backpack;
    private static UUID backpackId;
    private static Object disk;
    private static Rs rs;
    private static Path output;
    private static int phase, ticks, wait, direction;
    private static long deadline;
    private static boolean finished;
    static volatile boolean clientRequested, clientReady, clientSent;
    static volatile String clientFailure;
    static volatile boolean clientPassed;
    static volatile String clientState = "not initialized";
    static volatile String serverClientState = "not sent";
    static volatile int clientInputCount;
    static volatile ResourceLocation clientRecipe;
    static volatile Path clientOutput;

    private WorkbenchServerValidation() {}
    public static void serverStarted(ServerStartedEvent event) {
        if (Boolean.getBoolean("taczworkshop.workbenchValidation.client")) {
            KineticClientRuntime.execute(WorkbenchClientValidation::install);
        }
        KineticServerEvents.onTick(KineticEventPriority.NORMAL, KineticServerEvents.TickPhase.END,
                WorkbenchServerValidation::tick);
    }
    private static void tick(MinecraftServer server) {
        if (finished) return;
        try {
            if (++ticks > 3600) throw new AssertionError("Workbench validation timed out, phase=" + phase);
            if (++wait < 30) return;
            if (phase == 0) {
                initialize(server);
                resetMaterials(2, 4);
                require(RefinedStorageMaterials.adjacent(player, table).isEmpty(), "RS without a controller");
                craftAndCheck(5, 1, 0, 1);
                check("no RS: inventory 2 -> backpack 3, exactly one output");
                transactionChecks();
                placeController(root.east(2));
                phase = 1;
            } else if (phase == 1) {
                if (!networkReady()) return;
                attachDisk(3);
                require(RefinedStorageMaterials.adjacent(player, table).isEmpty(), "Controller across an air gap was accepted");
                resetMaterials(2, 4);
                craftAndCheck(5, 1, 0, 1);
                require(rs.stored(disk) == 3, "Remote RS was debited");
                check("air-gap controller excluded from display and consumption");
                placeController(root.east().north());
                phase = 2;
            } else if (phase == 2) {
                if (!networkReady()) return;
                attachDisk(3);
                require(RefinedStorageMaterials.adjacent(player, table).isEmpty(), "Diagonal controller was accepted");
                check("diagonal controller excluded");
                direction = 0;
                placeController(root.relative(Direction.values()[direction]));
                phase = 3;
            } else if (phase == 3) {
                if (!networkReady()) return;
                attachDisk(3);
                long available = RefinedStorageMaterials.adjacent(player, table).stream().mapToLong(MaterialStock::count).sum();
                require(available == 3, "Adjacent " + Direction.values()[direction] + " count=" + available);
                check("direct controller " + Direction.values()[direction]);
                if (direction == 0) {
                    resetMaterials(2, 4);
                    canceledOutputCheck();
                    craftAndCheck(5, 1, 0, 4);
                    require(rs.stored(disk) == 0, "RS must supply first three ingredients");
                    craftAndCheck(5, 0, 0, 4);
                    check("RS -> inventory -> backpack, repeat request cannot duplicate output");
                }
                if (++direction < Direction.values().length) placeController(root.relative(Direction.values()[direction]));
                else {
                    removeController();
                    resetMaterials(1, 0);
                    craftWithInputs(List.of(ingredient(1), ingredient(1)), 0);
                    require(inventoryIron() == 1, "Overlapping ingredients changed inventory");
                    check("overlapping ingredients cannot reuse one item");
                    player.setPos(root.getX() + 32, root.getY(), root.getZ());
                    require(!WorkbenchMaterials.valid(player, menu), "Distant workbench stayed valid");
                    craftWithInputs(List.of(ingredient(1)), 0);
                    require(inventoryIron() == 1, "Distant craft changed inventory");
                    player.setPos(root.getX() + .5, root.getY(), root.getZ() + .5);
                    check("server rejects out-of-range workbench");
                    cleanup();
                    if (Boolean.getBoolean("taczworkshop.workbenchValidation.client")) {
                        clientOutput = output; clientRequested = true;
                        deadline = System.currentTimeMillis() + 30_000;
                        phase = 4;
                    } else finish(server, null);
                }
            } else {
                if (clientFailure != null) throw new AssertionError(clientFailure);
                if (System.currentTimeMillis() > deadline) throw new AssertionError("Client material view timed out, ready=" + clientReady + " sent=" + clientSent + " " + clientState);
                if (clientReady && !clientSent) {
                    require(server.getPlayerList().getPlayerCount() == 1, "Client fixture requires one integrated-server player");
                    var recipient = server.getPlayerList().getPlayers().get(0);
                    serverClientState = "heldMenu=" + recipient.containerMenu.getClass().getSimpleName() + ":" + recipient.containerMenu.containerId;
                    var field = WorkbenchNetwork.class.getDeclaredField("CHANNEL"); field.setAccessible(true);
                    var selectedRecipe = level.getRecipeManager().byKey(clientRecipe).orElseThrow();
                    require(selectedRecipe instanceof GunSmithTableRecipe actual && actual.getInputs().size() == clientInputCount
                            && clientInputCount > 0 && clientInputCount <= 256, "Client/server recipe inputs differ: " + clientState);
                    int[] counts = new int[clientInputCount]; Arrays.fill(counts, 123);
                    ((PacketChannel) field.get(null)).sendToPlayer(recipient,
                            new WorkbenchNetwork.Materials(CLIENT_MENU, clientRecipe.toString(), counts, true));
                    clientSent = true;
                }
                if (clientPassed) { check("real client packet -> original TaCZ material counters, 1080p screenshot"); finish(server, null); }
            }
        } catch (Throwable failure) { finish(server, failure); }
    }

    private static void initialize(MinecraftServer server) throws Exception {
        output = Path.of(System.getProperty("taczworkshop.workbenchValidation.output",
                "D:/IDEAWork/TACZWorkshop/build/ui-fixes-20261016/workbench-validation"));
        require(output.isAbsolute(), "workbenchValidation.output must be absolute");
        Files.createDirectories(output);
        require(KineticPlatform.isModLoaded("refinedstorage") && KineticPlatform.isModLoaded("sophisticatedbackpacks"), "Install actual RS and Sophisticated Backpacks");
        level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        root = new BlockPos(spawn.getX(), level.getMaxBuildHeight() - 8, spawn.getZ());
        level.getChunkAt(root);
        for (BlockPos pos : BlockPos.betweenClosed(root.offset(-3, -3, -3), root.offset(3, 3, 3)))
            require(level.getBlockState(pos).isAir() && level.getBlockEntity(pos) == null, "Temporary fixture volume is occupied: " + pos);
        player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("c5ecec51-6fab-401e-984e-dddc16b60010"), "WorkbenchFixture"));
        player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
        player.setPos(root.getX() + .5, root.getY(), root.getZ() + .5);
        Block block = ForgeRegistries.BLOCKS.getValues().stream().filter(value -> value.getClass().getSimpleName().equals("GunSmithTableBlockA")).findFirst().orElseThrow();
        put(root, block);
        require(level.getBlockEntity(root) instanceof GunSmithTableBlockEntity, "Real TaCZ workbench entity missing");
        table = (GunSmithTableBlockEntity) level.getBlockEntity(root); table.setId(DefaultAssets.DEFAULT_BLOCK_ID);
        menu = (GunSmithTableMenu) table.createMenu(9181, player.getInventory(), player); player.containerMenu = menu;
        require(((WorkbenchMenuContext) menu).workshop$table() == table, "Production createMenu mixin did not bind table");
        original = level.getRecipeManager().getRecipes().stream().filter(GunSmithTableRecipe.class::isInstance)
                .map(GunSmithTableRecipe.class::cast).filter(recipe -> !recipe.getInputs().isEmpty()
                        && ((WorkbenchMenuContext) menu).workshop$recipe(recipe.getId(), level.getRecipeManager()) != null)
                .findFirst().orElseThrow(() -> new AssertionError("No allowed loaded TaCZ recipe for real menu"));
        backpack = new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(KineticResourceIds.parse("sophisticatedbackpacks:backpack"))));
        var wrapper = new BackpackWrapper(backpack); wrapper.getInventoryHandler(); backpackId = wrapper.getContentsUuid().orElseThrow();
        rs = new Rs();
    }
    private static void resetMaterials(int inventory, int bag) {
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, inventory)); player.getInventory().setItem(8, backpack);
        new BackpackWrapper(backpack).getInventoryHandler().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, bag));
    }
    private static GunSmithTableIngredient ingredient(int count) { return new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT), count); }
    private static void craftAndCheck(int need, int outputs, int inventory, int bag) {
        craftWithInputs(List.of(ingredient(need)), outputs);
        require(inventoryIron() == inventory && new BackpackWrapper(backpack).getInventoryHandler().getStackInSlot(0).getCount() == bag,
                "Unexpected inventory/backpack debit: inventory=" + inventoryIron() + " backpack=" + new BackpackWrapper(backpack).getInventoryHandler().getStackInSlot(0).getCount());
    }
    private static void craftWithInputs(List<GunSmithTableIngredient> inputs, int outputs) {
        craftWithInputs(inputs, outputs, outputs > 0);
    }
    private static void craftWithInputs(List<GunSmithTableIngredient> inputs, int outputs, boolean available) {
        var recipe = new GunSmithTableRecipe(original.getId(), new GunSmithTableResult(new ItemStack(Items.PAPER), original.getTab()), inputs);
        var recipes = new ArrayList<Recipe<?>>(level.getRecipeManager().getRecipes());
        var replacement = new ArrayList<Recipe<?>>(recipes); replacement.removeIf(value -> value.getId().equals(original.getId())); replacement.add(recipe);
        Set<UUID> before = new HashSet<>(); items().forEach(value -> before.add(value.getUUID()));
        try {
            level.getRecipeManager().replaceRecipes(replacement);
            WorkbenchMaterials.Preview preview = WorkbenchMaterials.preview(player, menu, recipe);
            require(preview.craftable() == available, "Server preview differs from craft expectation");
            menu.doCraft(recipe.getId(), player);
            var spawned = items().stream().filter(value -> !before.contains(value.getUUID())).toList();
            try {
                require(spawned.stream().mapToInt(value -> value.getItem().getCount()).sum() == outputs
                        && spawned.stream().allMatch(value -> value.getItem().is(Items.PAPER)), "Actual doCraft output count mismatch");
            } finally { spawned.forEach(ItemEntity::discard); }
        } finally { level.getRecipeManager().replaceRecipes(recipes); }
    }
    private static List<ItemEntity> items() { return level.getEntitiesOfClass(ItemEntity.class, new AABB(root).inflate(4)); }
    private static int inventoryIron() { return player.getInventory().items.stream().filter(stack -> stack.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum(); }
    private static void put(BlockPos pos, Block block) { placed.add(pos.immutable()); require(level.setBlockAndUpdate(pos, block.defaultBlockState()), "Cannot place " + pos); }
    private static void placeController(BlockPos pos) {
        removeController(); controller = pos.immutable();
        put(controller, Objects.requireNonNull(ForgeRegistries.BLOCKS.getValue(KineticResourceIds.parse("refinedstorage:creative_controller"))));
        deadline = System.currentTimeMillis() + 30_000; wait = 0;
    }
    private static boolean networkReady() throws Exception {
        Object network = rs.network(level.getBlockEntity(controller));
        if ((Boolean) rs.networkType.getMethod("canRun").invoke(network)) return true;
        require(System.currentTimeMillis() < deadline, "Actual controller network did not become runnable at " + controller); return false;
    }
    private static void attachDisk(int count) throws Exception { disk = rs.attach(rs.network(level.getBlockEntity(controller)), count); }
    private static void removeController() {
        if (controller != null) { level.setBlockAndUpdate(controller, Blocks.AIR.defaultBlockState()); controller = null; disk = null; }
    }
    private static void transactionChecks() throws Exception {
        ItemStackHandler first = new ItemStackHandler(1);
        ItemStackHandler latePartial = new ItemStackHandler(1) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return super.extractItem(slot, simulate ? amount : Math.min(1, amount), simulate); }
        };
        first.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3)); latePartial.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        var recovered = new ArrayList<ItemStack>();
        require(MaterialDebit.withdraw(List.of(MaterialStock.slot(first, 0, 1), MaterialStock.slot(latePartial, 0, 2)), new long[]{2, 2}, recovered::add) == null, "Late partial extraction unexpectedly succeeded");
        require(first.getStackInSlot(0).getCount() == 3 && latePartial.getStackInSlot(0).getCount() == 3 && recovered.isEmpty(), "Partial failure did not restore all actual receipts");
        ItemStackHandler refusing = new ItemStackHandler(1) {
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        };
        refusing.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        try (var debit = MaterialDebit.withdraw(List.of(MaterialStock.slot(refusing, 0, 1)), new long[]{2}, recovered::add)) {
            require(debit != null, "Refund fallback fixture could not extract");
        }
        require(refusing.getStackInSlot(0).getCount() == 1 && recovered.stream().mapToInt(ItemStack::getCount).sum() == 2, "Failed source restore lost actual extracted items");
        check("partial extraction rolls back every receipt; rejected reinsertion goes to recovery");
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        var refund = WorkbenchMaterials.class.getDeclaredMethod("refund", net.minecraft.server.level.ServerPlayer.class, ItemStack.class); refund.setAccessible(true);
        refund.invoke(null, player, new ItemStack(Items.IRON_INGOT, 2));
        CompoundTag data = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        require(data.getList("TaczWorkshopMaterialRefunds", 10).size() == 1, "Full inventory refund was not persisted");
        player.getInventory().setItem(0, ItemStack.EMPTY); WorkbenchMaterials.recoverRefunds(player);
        require(inventoryIron() == 2 && player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getList("TaczWorkshopMaterialRefunds", 10).isEmpty(), "Persisted refund did not recover exactly once");
        check("full-inventory refund survives in player NBT and recovers exactly once");
        resetMaterials(0, 0);
    }
    private static void canceledOutputCheck() throws Exception {
        Consumer<EntityJoinLevelEvent> blocker = event -> {
            if (event.getLevel() == level && event.getEntity() instanceof ItemEntity item && item.getItem().is(Items.PAPER)
                    && item.distanceToSqr(player) < 4) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityJoinLevelEvent.class, blocker);
        try { craftWithInputs(List.of(ingredient(6)), 0, true); }
        finally { MinecraftForge.EVENT_BUS.unregister(blocker); }
        require(rs.stored(disk) == 3 && inventoryIron() == 2
                && new BackpackWrapper(backpack).getInventoryHandler().getStackInSlot(0).getCount() == 4,
                "Rejected output did not refund RS, inventory and backpack");
        check("rejected output restores RS, player inventory and backpack materials");
    }
    private static void cleanup() {
        removeController();
        if (level != null) for (BlockPos pos : placed) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        placed.clear();
        if (player != null) { player.containerMenu = player.inventoryMenu; player.getInventory().clearContent(); }
        if (backpackId != null) { BackpackStorage.get().removeBackpackContents(backpackId); backpackId = null; }
    }
    private static void finish(MinecraftServer server, Throwable failure) {
        if (finished) return; finished = true;
        try { cleanup(); } catch (Throwable restore) { if (failure == null) failure = restore; else failure.addSuppressed(restore); }
        String result = (failure == null ? "PASS" : "FAIL " + failure) + System.lineSeparator() + String.join(System.lineSeparator(), checks) + System.lineSeparator();
        try { if (output != null) Files.writeString(output.resolve("result.txt"), result, StandardCharsets.UTF_8); }
        catch (Exception write) { LOG.error("Unable to write workbench result", write); }
        if (failure == null) LOG.info("TACZ_WORKBENCH_PASS {}", checks.size()); else LOG.error("TACZ_WORKBENCH_FAIL phase=" + phase, failure);
        if (Boolean.getBoolean("taczworkshop.workbenchValidation.stopAfter")) {
            if (server.isDedicatedServer()) server.halt(false);
            else dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> () ->
                    dev.xyat.kineticcore.api.runtime.KineticClientRuntime.execute(
                            dev.xyat.kineticcore.api.runtime.KineticClientRuntime::stopClient));
        }
    }
    private static void check(String message) { checks.add(message); LOG.info("TACZ_WORKBENCH_CHECK {}", message); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    /** Actual installed RS storage/network APIs; only the disk's settings context is a trivial proxy. */
    private static final class Rs {
        final Class<?> networkType = Class.forName("com.refinedmods.refinedstorage.api.network.INetwork");
        final Class<?> cacheType = Class.forName("com.refinedmods.refinedstorage.api.storage.cache.IStorageCache");
        final Class<?> actionType = Class.forName("com.refinedmods.refinedstorage.api.util.Action");
        final Class<?> diskType = Class.forName("com.refinedmods.refinedstorage.apiimpl.storage.disk.ItemStorageDisk");
        final Object perform = actionType.getField("PERFORM").get(null);
        Rs() throws Exception {}
        Object network(Object blockEntity) throws Exception { return blockEntity.getClass().getMethod("getNetwork").invoke(blockEntity); }
        Object attach(Object network, int count) throws Exception {
            Object disk = diskType.getConstructor(ServerLevel.class, int.class, UUID.class).newInstance(null, 1000, UUID.randomUUID());
            Class<?> context = Class.forName("com.refinedmods.refinedstorage.api.storage.disk.IStorageDiskContainerContext");
            Object access = Class.forName("com.refinedmods.refinedstorage.api.storage.AccessType").getField("INSERT_EXTRACT").get(null);
            Object proxy = Proxy.newProxyInstance(context.getClassLoader(), new Class<?>[]{context}, (object, method, args) -> access);
            diskType.getMethod("setSettings", Class.forName("com.refinedmods.refinedstorage.api.storage.disk.IStorageDiskListener"), context).invoke(disk, null, proxy);
            diskType.getMethod("insert", ItemStack.class, int.class, actionType).invoke(disk, new ItemStack(Items.IRON_INGOT), count, perform);
            Object cache = networkType.getMethod("getItemStorageCache").invoke(network);
            @SuppressWarnings("unchecked") List<Object> storages = (List<Object>) cacheType.getMethod("getStorages").invoke(cache); storages.add(disk);
            Object list = cacheType.getMethod("getList").invoke(cache);
            Class.forName("com.refinedmods.refinedstorage.api.util.IStackList").getMethod("add", Object.class, int.class).invoke(list, new ItemStack(Items.IRON_INGOT), count);
            require(stored(disk) == count, "Actual in-memory RS disk initialization failed"); return disk;
        }
        int stored(Object disk) throws Exception { return (Integer) diskType.getMethod("getStored").invoke(disk); }
    }
}
