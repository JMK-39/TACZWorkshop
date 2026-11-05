package dev.xyat.taczworkshop.network;

import com.tacz.guns.inventory.GunSmithTableMenu;
import dev.xyat.kineticcore.api.network.*;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.taczworkshop.crafting.WorkbenchMaterials;
import dev.xyat.taczworkshop.crafting.WorkbenchMenuContext;
import dev.xyat.taczworkshop.client.WorkbenchMaterialClient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;

public final class WorkbenchNetwork {
    private static final PacketChannel CHANNEL = PacketChannel.create(KineticResourceIds.of("taczworkshop", "workbench"), "1", NetworkVersionPolicy.EXACT);
    private static boolean registered;
    private WorkbenchNetwork() {}
    public record Query(int menu, String recipe) {}
    public record Materials(int menu, String recipe, int[] counts, boolean craftable) {}

    public static synchronized void register() {
        if (registered) return;
        CHANNEL.registerServerbound(0, Query.class, NetworkCodec.of((buffer, value) -> {
            buffer.writeInt(value.menu); buffer.writeUtf(value.recipe, 256);
        }, buffer -> new Query(buffer.readInt(), buffer.readUtf(256))), (packet, context) -> {
            ServerPlayer player = context.sender();
            if (!(player.containerMenu instanceof GunSmithTableMenu menu) || menu.containerId != packet.menu) return;
            WorkbenchMenuContext access = (WorkbenchMenuContext) menu;
            long tick = player.level().getGameTime();
            if (tick - access.workshop$lastQuery() < 2) return;
            access.workshop$lastQuery(tick);
            ResourceLocation recipe = KineticResourceIds.tryParse(packet.recipe);
            if (recipe != null) send(player, menu, recipe);
        });
        CHANNEL.registerClientboundLazy(1, Materials.class, NetworkCodec.of((buffer, value) -> {
            buffer.writeInt(value.menu); buffer.writeUtf(value.recipe, 256); buffer.writeBoolean(value.craftable);
            buffer.writeInt(value.counts.length); for (int count : value.counts) buffer.writeInt(count);
        }, buffer -> {
            int menu = buffer.readInt(); String recipe = buffer.readUtf(256); boolean craftable = buffer.readBoolean();
            int size = buffer.readInt(); if (size < 0 || size > 256) throw new IllegalArgumentException("Invalid workbench material count");
            int[] counts = new int[size]; for (int i = 0; i < size; i++) counts[i] = Math.max(0, buffer.readInt());
            return new Materials(menu, recipe, counts, craftable);
        }), () -> WorkbenchMaterialClient::accept);
        MinecraftForge.EVENT_BUS.addListener(WorkbenchNetwork::recover);
        registered = true;
    }

    public static void query(int menu, ResourceLocation recipe) { CHANNEL.sendToServer(new Query(menu, recipe.toString())); }
    public static void send(ServerPlayer player, GunSmithTableMenu menu, ResourceLocation id) {
        if (player instanceof FakePlayer) return;
        var recipe = ((WorkbenchMenuContext) menu).workshop$recipe(id, player.level().getRecipeManager());
        WorkbenchMaterials.Preview preview = WorkbenchMaterials.preview(player, menu, recipe);
        CHANNEL.sendToPlayer(player, new Materials(menu.containerId, id.toString(), preview.counts(), preview.craftable()));
    }
    private static void recover(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            WorkbenchMaterials.recoverRefunds(player);
        }
    }
}
