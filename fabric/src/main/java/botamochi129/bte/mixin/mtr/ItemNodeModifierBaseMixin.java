package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.ItemUsageContext;
import org.mtr.mapping.holder.PlayerEntity;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.holder.World;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.item.ItemNodeModifierBase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemNodeModifierBase.class, remap = false)
public abstract class ItemNodeModifierBaseMixin {

    private static final String TAG_TRANSPORT_MODE = "transport_mode";

    @Shadow @Final protected boolean isConnector;

    @Inject(method = "onEndClick", at = @At("RETURN"))
    private void bte$onEndClick(ItemUsageContext context, BlockPos endBlockPos, CompoundTag tag, CallbackInfo ci) {
        if (!isConnector || !tag.contains(TAG_TRANSPORT_MODE)) return;

        World world = context.getWorld();
        BlockPos startBlockPos = context.getBlockPos();
        BlockState startState = world.getBlockState(startBlockPos);
        BlockState endState = world.getBlockState(endBlockPos);

        if (!(startState.getBlock().data instanceof BlockNode) || !(endState.getBlock().data instanceof BlockNode)) {
            return;
        }

        StraightNodeBlockEntity beStart = getStraightNodeBE(world, startBlockPos);
        StraightNodeBlockEntity beEnd = getStraightNodeBE(world, endBlockPos);

        // 1. 「接続方向の絶対角度」を決定する
        // 相手ノードの状態（MTR標準/Bound/Unbound）を最優先で尊重し、それに合わせる
        double targetStartDeg = getTargetAngle(world, startBlockPos, endBlockPos, endState, beEnd, true);
        double targetEndDeg = getTargetAngle(world, endBlockPos, startBlockPos, startState, beStart, false);

        // 2. StraightNodeであれば、その絶対角度に強制スナップ（バインド）する
        // これにより、接続部は必ず直線（カーブ半径0）になる
        if (beStart != null) {
            beStart.bind(targetStartDeg);
        }
        if (beEnd != null) {
            beEnd.bind(targetEndDeg);
        }

        // 3. ベジェデータの更新
        if (beStart != null) beStart.updateConnectedRails(true);
        if (beEnd != null) beEnd.updateConnectedRails(true);
    }

    /**
     * 接続相手との「あるべき接続角度」を決定する
     * 優先順: 1. MTR標準ノードの固定角度, 2. バインド済みStraightNodeの角度, 3. 幾何学方向（直線）
     */
    private double getTargetAngle(World world, BlockPos myPos, BlockPos otherPos,
                                  BlockState otherState, StraightNodeBlockEntity otherBe,
                                  boolean isStartNode) {

        double baseAngle;
        if (otherBe == null) {
            // 相手がMTR標準ノードの場合、その固定角度を絶対基準とする
            baseAngle = BlockNode.getAngle(otherState);
        } else if (otherBe.isBound()) {
            // 相手がバインド済みの場合、その角度を絶対基準とする
            baseAngle = otherBe.getAngleDegrees();
        } else {
            // 相手がUnboundの場合、幾何学方向（直線）を基準とする
            baseAngle = calculateStraightAngle(myPos, otherPos);
        }

        // 自分から見た相手の方向（幾何学方向）
        double geoAngle = calculateStraightAngle(myPos, otherPos);

        // 基準角度が、幾何学方向と逆向き（180度ズレ）の場合は補正
        if (!Angle.similarFacing((float) baseAngle, (float) geoAngle)) {
            baseAngle = normalize360(baseAngle + 180.0);
        }

        return baseAngle;
    }

    private static StraightNodeBlockEntity getStraightNodeBE(World world, BlockPos pos) {
        org.mtr.mapping.holder.BlockEntity rawBe = world.getBlockEntity(pos);
        if (rawBe != null && rawBe.data instanceof StraightNodeBlockEntity be) {
            return be;
        }
        return null;
    }

    private static double calculateStraightAngle(BlockPos pos1, BlockPos pos2) {
        double dx = pos2.getX() - pos1.getX();
        double dz = pos2.getZ() - pos1.getZ();
        double deg = Math.toDegrees(Math.atan2(dz, dx));
        return normalize360(deg);
    }

    private static double normalize360(double angle) {
        angle = angle % 360.0;
        if (angle < 0.0) angle += 360.0;
        return angle;
    }
}