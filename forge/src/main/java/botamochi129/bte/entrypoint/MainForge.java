package botamochi129.bte.entrypoint;

import botamochi129.bte.mod.Constants;
import botamochi129.bte.mod.BTE;
import botamochi129.bte.mod.BTEClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class MainForge {
    public MainForge() {
        BTE.initialize();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ForgeConfig.registerConfig();
            BTEClient.initialize();
        });
        MinecraftForge.EVENT_BUS.register(new MigrateMapping());
    }
}