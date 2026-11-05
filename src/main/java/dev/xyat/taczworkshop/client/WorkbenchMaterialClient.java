package dev.xyat.taczworkshop.client;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.taczworkshop.crafting.WorkbenchMaterialView;
import dev.xyat.taczworkshop.network.WorkbenchNetwork;
import net.minecraft.client.Minecraft;

public final class WorkbenchMaterialClient {
    private WorkbenchMaterialClient() {}
    public static void accept(WorkbenchNetwork.Materials packet) {
        var recipe = KineticResourceIds.tryParse(packet.recipe());
        if (recipe != null && Minecraft.getInstance().screen instanceof WorkbenchMaterialView view) {
            view.workshop$materials(packet.menu(), recipe, packet.counts(), packet.craftable());
        }
    }
}
