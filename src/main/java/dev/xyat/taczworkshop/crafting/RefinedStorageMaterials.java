package dev.xyat.taczworkshop.crafting;

import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import dev.xyat.taczworkshop.TaczWorkshop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.*;

/** RS 1.12.4 API verified against the installed JAR. Kept optional without linking RS into non-RS installations. */
public final class RefinedStorageMaterials {
    private RefinedStorageMaterials() {}
    public static List<MaterialStock> adjacent(ServerPlayer player, GunSmithTableBlockEntity table) {
        if (table == null || !KineticPlatform.isModLoaded("refinedstorage")) return List.of();
        List<MaterialStock> result = new ArrayList<>();
        try {
            Api api = Holder.API;
            if (api == null) return result;
            Set<BlockPos> parts = new LinkedHashSet<>();
            BlockPos root = table.getBlockPos(); parts.add(root);
            // TaCZ B/C tables have a second horizontal/vertical part. A controller must touch a real table part.
            for (Direction direction : Direction.values()) {
                BlockPos part = root.relative(direction);
                var state = player.level().getBlockState(part);
                if (state.getBlock() instanceof AbstractGunSmithTableBlock block && block.getRootPos(part, state).equals(root)) parts.add(part);
            }
            Set<Object> networks = Collections.newSetFromMap(new IdentityHashMap<>());
            for (BlockPos part : parts) for (Direction direction : Direction.values()) {
                BlockPos pos = part.relative(direction);
                if (!player.level().hasChunkAt(pos)) continue;
                Object controller = player.level().getBlockEntity(pos);
                if (!api.controller.isInstance(controller)) continue;
                Object network = api.network.invoke(controller);
                if (network == null || !networks.add(network) || !(Boolean) api.canRun.invoke(network) || !api.allowed(network, player, api.extractPermission)) continue;
                Object cache = api.itemCache.invoke(network);
                Object list = api.list.invoke(cache);
                for (Object entry : (Collection<?>) api.stacks.invoke(list)) {
                    ItemStack stack = ((ItemStack) api.stack.invoke(entry)).copy();
                    if (stack.isEmpty()) continue;
                    Object owner = network;
                    result.add(new MaterialStock(stack, stack.getCount(), 0, new MaterialStock.Access() {
                        public ItemStack extract(int amount, boolean simulate) {
                            try {
                                if (!(Boolean) api.canRun.invoke(owner) || !api.allowed(owner, player, api.extractPermission)) return ItemStack.EMPTY;
                                ItemStack result = (ItemStack) api.extract.invoke(owner, stack, amount, api.compareNbt, simulate ? api.simulate : api.perform);
                                return result == null ? ItemStack.EMPTY : result;
                            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("RS extraction failed", failure); }
                        }
                        public ItemStack restore(ItemStack value) {
                            try {
                                if (!(Boolean) api.canRun.invoke(owner) || !api.allowed(owner, player, api.insertPermission)) return value;
                                ItemStack result = (ItemStack) api.insert.invoke(owner, value, value.getCount(), api.perform);
                                return result == null ? ItemStack.EMPTY : result;
                            } catch (ReflectiveOperationException failure) { return value; }
                        }
                    }));
                }
            }
        } catch (ReflectiveOperationException | LinkageError failure) {
            TaczWorkshop.LOGGER.error("Unable to inspect adjacent RS controller materials", failure);
        }
        return result;
    }

    private static final class Holder {
        static final Api API = load();
        static Api load() {
            try { return new Api(); }
            catch (ReflectiveOperationException | LinkageError failure) { TaczWorkshop.LOGGER.error("Unsupported Refined Storage crafting API", failure); return null; }
        }
    }
    private static final class Api {
        final Class<?> controller = Class.forName("com.refinedmods.refinedstorage.blockentity.ControllerBlockEntity");
        final Class<?> networkType = Class.forName("com.refinedmods.refinedstorage.api.network.INetwork");
        final Class<?> action = Class.forName("com.refinedmods.refinedstorage.api.util.Action");
        final Class<?> permission = Class.forName("com.refinedmods.refinedstorage.api.network.security.Permission");
        final Object perform = action.getField("PERFORM").get(null), simulate = action.getField("SIMULATE").get(null);
        final Object extractPermission = permission.getField("EXTRACT").get(null), insertPermission = permission.getField("INSERT").get(null);
        final int compareNbt = Class.forName("com.refinedmods.refinedstorage.api.util.IComparer").getField("COMPARE_NBT").getInt(null);
        final Method network = controller.getMethod("getNetwork"), canRun = networkType.getMethod("canRun");
        final Method security = networkType.getMethod("getSecurityManager");
        final Method hasPermission = Class.forName("com.refinedmods.refinedstorage.api.network.security.ISecurityManager").getMethod("hasPermission", permission, Player.class);
        final Method itemCache = networkType.getMethod("getItemStorageCache");
        final Method list = Class.forName("com.refinedmods.refinedstorage.api.storage.cache.IStorageCache").getMethod("getList");
        final Method stacks = Class.forName("com.refinedmods.refinedstorage.api.util.IStackList").getMethod("getStacks");
        final Method stack = Class.forName("com.refinedmods.refinedstorage.api.util.StackListEntry").getMethod("getStack");
        final Method extract = networkType.getMethod("extractItem", ItemStack.class, int.class, int.class, action);
        final Method insert = networkType.getMethod("insertItem", ItemStack.class, int.class, action);
        Api() throws ReflectiveOperationException {}
        boolean allowed(Object network, Player player, Object permission) throws ReflectiveOperationException {
            return (Boolean) hasPermission.invoke(security.invoke(network), permission, player);
        }
    }
}
