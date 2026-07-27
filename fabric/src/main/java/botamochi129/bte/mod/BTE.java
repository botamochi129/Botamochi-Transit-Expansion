package botamochi129.bte.mod;

import botamochi129.bte.mod.packet.PacketUpdateStraightNodeAngle;
import botamochi129.bte.mod.registry.BTERegistry;
import botamochi129.bte.mod.registry.Blocks;
import botamochi129.bte.mod.registry.ItemGroups;

public class BTE {
    public static void initialize() {
        Blocks.register();
        ItemGroups.register();
        registerPackets();
        BTERegistry.REGISTRY.init();
    }

    private static void registerPackets() {
        BTERegistry.setupPackets(new org.mtr.mapping.holder.Identifier(Constants.MOD_ID, "packets"));
        BTERegistry.registerPacket(PacketUpdateStraightNodeAngle.class, PacketUpdateStraightNodeAngle::new);
    }
}
