package botamochi129.bte.mod.block;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.PlayerEntity;
import org.mtr.mapping.holder.World;

public interface StraightNodeBlockClientInteraction {
    boolean onUseClient(BlockPos blockPos, World world, PlayerEntity playerEntity);
    void removeConnectedRailsClient(World world, BlockPos pos);
}