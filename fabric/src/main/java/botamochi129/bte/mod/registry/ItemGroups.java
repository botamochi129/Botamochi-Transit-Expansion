package botamochi129.bte.mod.registry;

import botamochi129.bte.mod.Constants;
import org.mtr.mapping.holder.ItemConvertible;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mapping.registry.CreativeModeTabHolder;

public class ItemGroups {
    public static final CreativeModeTabHolder MAIN = BTERegistry.REGISTRY.createCreativeModeTabHolder(
            Constants.id("main"), () -> new ItemStack(new ItemConvertible(Blocks.STRAIGHT_NODE.get().data)));

    public static void register() {
    }
}
