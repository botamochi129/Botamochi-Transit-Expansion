package botamochi129.bte.mod;

import botamochi129.bte.mod.packet.PacketUpdateStraightNodeAngle;
import botamochi129.bte.mod.registry.BTERegistry;
import botamochi129.bte.mod.registry.Blocks;
import botamochi129.bte.mod.registry.ItemGroups;
import botamochi129.bte.mod.registry.Items;

public class BTE {
    public static void initialize() {
        Blocks.register();
        ItemGroups.register();
        Items.register();
        registerPackets();
        BTERegistry.REGISTRY.init();
    }

    private static void registerPackets() {
        BTERegistry.setupPackets(new org.mtr.mapping.holder.Identifier(Constants.MOD_ID, "packets"));
        BTERegistry.registerPacket(PacketUpdateStraightNodeAngle.class, PacketUpdateStraightNodeAngle::new);
        BTERegistry.registerPacket(botamochi129.bte.mod.packet.PacketSplitRail.class, botamochi129.bte.mod.packet.PacketSplitRail::new);
    }
}
