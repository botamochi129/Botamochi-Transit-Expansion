package botamochi129.bte.mod.block;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.client.ClientHelper;
import botamochi129.bte.mod.screen.StraightNodeAngleScreen;
import org.mtr.core.data.TransportMode;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockWithEntity;
import org.mtr.mod.Items;
import org.mtr.mod.block.BlockNode;

public class StraightNodeBlock extends BlockNode implements BlockWithEntity {

    public StraightNodeBlock() {
        super(TransportMode.TRAIN);
    }

    @Override
    public ActionResult onUse2(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player != null && player.isHolding(Items.BRUSH.get())) {
            if (world.isClient()) {
                // クライアント側でのみ GUI を開く（直参照を避ける）
                openAngleScreen(pos, world);
            }
            return ActionResult.SUCCESS; // サーバー側も HANDLED/SUCCESS を返して腕の振りを同期
        }
        return super.onUse2(state, world, pos, player, hand, hit);
    }

    // クライアント専用ヘルパー（Separate Client-Only Call）
    private static void openAngleScreen(BlockPos pos, World world) {
        ClientHelper.openAngleScreen(pos, world);
    }

    @Override
    public BlockEntityExtension createBlockEntity(BlockPos pos, BlockState state) {
        return new StraightNodeBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType2(BlockState state) {
        return BlockRenderType.getEntityblockAnimatedMapped();
    }
}