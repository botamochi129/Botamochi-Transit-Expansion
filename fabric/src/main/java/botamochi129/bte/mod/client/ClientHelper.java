package botamochi129.bte.mod.client;

import botamochi129.bte.mod.screen.StraightNodeAngleScreen;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.holder.World;

public class ClientHelper {
    public static void openAngleScreen(BlockPos pos, World world) {
        MinecraftClient.getInstance().openScreen(new Screen(new StraightNodeAngleScreen(pos, world)));
    }
}