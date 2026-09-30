package botamochi129.bte.mod.block;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.screen.StraightNodeAngleScreen;
import org.mtr.core.data.Data;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TwoPositionsBase;
import org.mtr.core.data.TransportMode;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockWithEntity;
import org.mtr.mod.Items;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketDeleteData;

import java.util.ArrayList;
import java.util.Map;

public class StraightNodeBlock extends BlockNode implements BlockWithEntity {
    public static StraightNodeBlockClientInteraction clientInteractionHandler = null;

    public StraightNodeBlock() {
        super(TransportMode.TRAIN);
    }

    @Override
    public ActionResult onUse2(BlockState blockState, World world, BlockPos blockPos, PlayerEntity playerEntity, Hand hand, BlockHitResult hit) {
        // ★ クライアント側かつハンドラが登録されていれば、クライアント専用処理に委譲する
        if (world.isClient() && clientInteractionHandler != null) {
            if (clientInteractionHandler.onUseClient(blockPos, world, playerEntity)) {
                return ActionResult.SUCCESS;
            }
        }
        return super.onUse2(blockState, world, blockPos, playerEntity, hand, hit);
    }

    @Override
    public BlockEntityExtension createBlockEntity(BlockPos pos, BlockState state) {
        return new StraightNodeBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType2(BlockState state) {
        return BlockRenderType.getEntityblockAnimatedMapped();
    }

    @Override
    public void onStateReplaced2(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (world.isClient()) {
                // ★ こちらもハンドラ経由で呼び出す
                if (clientInteractionHandler != null) {
                    clientInteractionHandler.removeConnectedRailsClient(world, pos);
                }
            } else {
                PacketDeleteData.sendDirectlyToServerRailNodePosition(
                        ServerWorld.cast(world),
                        Init.blockPosToPosition(pos)
                );
            }
        }
        super.onStateReplaced2(state, world, pos, newState, moved);
    }

    /**
     * クライアント側での即時削除処理（描画のチラつき防止用）
     */
    private void removeConnectedRailsClient(World world, BlockPos pos) {
        Position nodePos = Init.blockPosToPosition(pos);
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
}