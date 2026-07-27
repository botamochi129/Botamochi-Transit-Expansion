package botamochi129.bte.mod;

import botamochi129.bte.mod.registry.BTERegistryClient;
import botamochi129.bte.mod.registry.Blocks;
import botamochi129.bte.mod.render.StraightNodeBlockEntityRenderer;

public class BTEClient {
    public static void initialize() {
        BTERegistryClient.setupPackets(new org.mtr.mapping.holder.Identifier(Constants.MOD_ID, "packets"));
        BTERegistryClient.registerBlockEntityRenderer(Blocks.STRAIGHT_NODE_BE, StraightNodeBlockEntityRenderer::new);
        BTERegistryClient.init();
    }
}
