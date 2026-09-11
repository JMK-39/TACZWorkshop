package dev.xyat.taczworkshop;

import com.mojang.logging.LogUtils;
import dev.xyat.kineticcore.config.server.KTServerConfigApi;
import dev.xyat.taczworkshop.config.TaczConfigGui;
import dev.xyat.taczworkshop.network.TaczRecipeNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TaczWorkshop.MODID)
public final class TaczWorkshop {
    public static final String MODID = "taczworkshop";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TaczWorkshop(FMLJavaModLoadingContext context) {
        KTServerConfigApi.registerActionPage(TaczConfigGui.PAGE_ID);
        TaczRecipeNetwork.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> TaczConfigGui::load);
    }
}
