package botamochi129.bte.mod.block;

import botamochi129.bte.mapping.LoaderImpl;
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

    public StraightNodeBlock() {
        super(TransportMode.TRAIN);
    }

    @Override
    public ActionResult onUse2(BlockState blockState, World world, BlockPos blockPos, PlayerEntity playerEntity, Hand hand, BlockHitResult hit) {
        if (world.isClient()) {
            BlockPos targetBlockPos = null;

            // ★ MTR標準と同じく、ブラシを持っている場合は厳密なRaycastでレールを特定
            if (playerEntity.isHolding(Items.BRUSH.get())) {
                final ObjectObjectImmutablePair<Rail, BlockPos> railAndBlockPos = MinecraftClientData.getInstance().getFacingRailAndBlockPos(false);
                if (railAndBlockPos != null) {
                    targetBlockPos = railAndBlockPos.right(); // 接続先のBlockPosを取得
                    MinecraftClient.getInstance().openScreen(new Screen(new StraightNodeAngleScreen(blockPos, world, targetBlockPos)));
                    return ActionResult.SUCCESS;
                }
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
        // ブロックが別のブロックに置き換わる（＝破壊される）場合のみ処理を実行
        if (!state.isOf(newState.getBlock())) {
            removeConnectedRails(world, pos);
        }
        super.onStateReplaced2(state, world, pos, newState, moved);
    }

    /**
     * 【修正版】指定された座標のノードに接続されているすべてのレールを削除する
     * クライアント側での即時削除により、チラつきを防止する
     */
    private void removeConnectedRails(World world, BlockPos pos) {
        Position nodePos = Init.blockPosToPosition(pos);

        if (world.isClient()) {
            // クライアント側: 即座にローカルデータから削除し、描画を更新する
            MinecraftClientData clientData = MinecraftClientData.getInstance();
            Map<Position, Rail> railsAtPos = clientData.positionsToRail.get(nodePos);

            if (railsAtPos != null && !railsAtPos.isEmpty()) {
                // ConcurrentModificationException を防ぐため、キーをコピーしてループする
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

                // 描画キャッシュをクリアし、再描画を促す
                world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            }
        } else {
            // サーバー側: 削除パケットを送信して、他のプレイヤーや永続化データにも反映させる
            Data data = LoaderImpl.getDataForWorld(world);
            if (data == null) return;

            Map<Position, Rail> railsAtPos = data.positionsToRail.get(nodePos);
            if (railsAtPos != null && !railsAtPos.isEmpty()) {
                ServerWorld serverWorld = LoaderImpl.toServerWorld(world);
                if (serverWorld == null) return;

                for (Position otherPos : new ArrayList<>(railsAtPos.keySet())) {
                    String railId = TwoPositionsBase.getHexId(nodePos, otherPos);
                    PacketDeleteData.sendDirectlyToServerRailId(serverWorld, railId);
                }
            }
        }
    }
}