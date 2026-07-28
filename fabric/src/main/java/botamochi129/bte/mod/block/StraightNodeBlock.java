package botamochi129.bte.mod.block;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.screen.StraightNodeAngleScreen;
import org.mtr.mapping.holder.ActionResult;
import org.mtr.mapping.holder.BlockHitResult;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockRenderType;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.Hand;
import org.mtr.mapping.holder.IntegerProperty;
import org.mtr.mapping.holder.ItemPlacementContext;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.PlayerEntity;
import org.mtr.mapping.holder.Property;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockWithEntity;
import org.mtr.mapping.tool.HolderBase;
import org.mtr.core.data.TransportMode;
import org.mtr.mod.Items;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.block.IBlock;

import java.util.List;

public class StraightNodeBlock extends BlockNode implements BlockWithEntity {
    public static final IntegerProperty CUSTOM_FACING = IntegerProperty.of("custom_facing", 0, 179);

    public StraightNodeBlock() {
        super(TransportMode.TRAIN);
        setDefaultState2(getDefaultState2().with(new Property<>(CUSTOM_FACING.data), 0));
    }

    @Override
    public void addBlockProperties(List<HolderBase<?>> properties) {
        super.addBlockProperties(properties);
        properties.add(CUSTOM_FACING);
    }

    @Override
    public ActionResult onUse2(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient() && player != null && player.isHolding(Items.BRUSH.get())) {
            MinecraftClient.getInstance().openScreen(new Screen(new StraightNodeAngleScreen(pos, world)));
            return ActionResult.SUCCESS;
        }
        return super.onUse2(state, world, pos, player, hand, hit);
    }

    @Override
    public BlockState getPlacementState2(ItemPlacementContext ctx) {
        BlockState state = super.getPlacementState2(ctx);
        if (state != null) {
            state = state.with(new Property<>(CUSTOM_FACING.data), 0);
        }
        return state;
    }

    public static int getAngle2(BlockState state) {
        return IBlock.getStatePropertySafe(state, CUSTOM_FACING);
    }

    public static BlockState setAngle(BlockState state, int angle) {
        int normalized = angle % 180;
        if (normalized < 0) normalized += 180;
        int minecraftYaw = (normalized - 90 + 360) % 360;
        BlockState newState = BlockNode.getStateWithAngle(state, minecraftYaw);
        newState = newState.with(new Property<>(CUSTOM_FACING.data), normalized);
        boolean connected = IBlock.getStatePropertySafe(state, BlockNode.IS_CONNECTED);
        newState = newState.with(new Property<>(BlockNode.IS_CONNECTED.data), connected);
        return newState;
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
