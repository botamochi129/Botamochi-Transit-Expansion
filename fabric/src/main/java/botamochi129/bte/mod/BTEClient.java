package botamochi129.bte.mod;

import botamochi129.bte.mod.block.StraightNodeBlock;
import botamochi129.bte.mod.block.StraightNodeBlockClientInteraction;
import botamochi129.bte.mod.registry.BTERegistryClient;
import botamochi129.bte.mod.registry.Blocks;
import botamochi129.bte.mod.render.StraightNodeBlockEntityRenderer;
import botamochi129.bte.mod.screen.StraightNodeAngleScreen;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.PlayerEntity;
import org.mtr.mapping.holder.World;
import org.mtr.mod.Items;
import org.mtr.mod.client.MinecraftClientData;

import java.util.ArrayList;
import java.util.Map;

public class BTEClient {
    public static void initialize() {
        BTERegistryClient.setupPackets(new org.mtr.mapping.holder.Identifier(Constants.MOD_ID, "packets"));
        BTERegistryClient.registerBlockEntityRenderer(Blocks.STRAIGHT_NODE_BE, StraightNodeBlockEntityRenderer::new);
        BTERegistryClient.init();

        StraightNodeBlock.clientInteractionHandler = new StraightNodeBlockClientInteraction() {
            @Override
            public boolean onUseClient(BlockPos blockPos, World world, PlayerEntity playerEntity) {
                if (playerEntity.isHolding(Items.BRUSH.get())) {
                    BlockPos targetBlockPos = null;
                    final ObjectObjectImmutablePair<Rail, BlockPos> railAndBlockPos = MinecraftClientData.getInstance().getFacingRailAndBlockPos(false);
                    if (railAndBlockPos != null) {
                        targetBlockPos = railAndBlockPos.right();
                    }
                    org.mtr.mapping.holder.MinecraftClient.getInstance().openScreen(
                            new org.mtr.mapping.holder.Screen(new StraightNodeAngleScreen(blockPos, world, targetBlockPos))
                    );
                    return true;
                }
                return false;
            }

            @Override
            public void removeConnectedRailsClient(World world, BlockPos pos) {
                Position nodePos = org.mtr.mod.Init.blockPosToPosition(pos);
                MinecraftClientData clientData = MinecraftClientData.getInstance();
                Map<Position, Rail> railsAtPos = clientData.positionsToRail.get(nodePos);

                if (railsAtPos != null && !railsAtPos.isEmpty()) {
                    for (Position otherPos : new ArrayList<>(railsAtPos.keySet())) {
                        Rail rail = railsAtPos.get(otherPos);
                        if (rail != null) {
                            clientData.railIdMap.remove(rail.getHexId());
                            Map<Position, Rail> map1 = clientData.positionsToRail.get(nodePos);
                            if (map1 != null) map1.remove(otherPos);
                            Map<Position, Rail> map2 = clientData.positionsToRail.get(otherPos);
                            if (map2 != null) map2.remove(nodePos);
                        }
                    }
                    clientData.positionsToRail.remove(nodePos);
                    world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
                }
            }
        };
    }
}
