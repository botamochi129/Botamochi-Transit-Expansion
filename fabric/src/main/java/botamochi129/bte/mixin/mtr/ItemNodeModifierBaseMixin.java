package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.AngleExtra;
import botamochi129.bte.mod.data.NodeGeometry;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.ItemStack;
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

    @Shadow protected abstract void onConnect(World world, ItemStack stack, TransportMode transportMode,
                                              BlockState state1, BlockState state2, BlockPos pos1, BlockPos pos2,
                                              Angle angle1, Angle angle2, ServerPlayerEntity player);

    @Shadow protected abstract void onRemove(World world, BlockPos pos1, BlockPos pos2, ServerPlayerEntity player);

    @Inject(method = "onEndClick", at = @At("HEAD"), cancellable = true)
    private void bte$onEndClick(ItemUsageContext context, BlockPos endBlockPos, CompoundTag tag, CallbackInfo ci) {
        World world = context.getWorld();
        BlockPos startBlockPos = context.getBlockPos();
        BlockState startState = world.getBlockState(startBlockPos);
        BlockState endState = world.getBlockState(endBlockPos);

        if (!(startState.getBlock().data instanceof BlockNode) || !(endState.getBlock().data instanceof BlockNode)) {
            return;
        }

        PlayerEntity player = context.getPlayer();
        if (!ServerPlayerEntity.isInstance(player)) return;

        BlockNode startNode = (BlockNode) startState.getBlock().data;
        if (!startNode.transportMode.toString().equals(tag.getString(TAG_TRANSPORT_MODE))) return;

        if (isConnector) {
            if (startBlockPos.equals(endBlockPos)) {
                tag.remove(TAG_TRANSPORT_MODE);
                ci.cancel();
                return;
            }

            StraightNodeBlockEntity beStart = getStraightNodeBE(world, startBlockPos);
            StraightNodeBlockEntity beEnd = getStraightNodeBE(world, endBlockPos);

            if (beStart == null && beEnd == null) {
                return;
            }

            Double startAxis = getFixedAngle(beStart, startState);
            Double endAxis = getFixedAngle(beEnd, endState);

            double geoAngleDeg = NodeGeometry.normalizeDegrees(Math.toDegrees(Math.atan2(
                    endBlockPos.getZ() - startBlockPos.getZ(),
                    endBlockPos.getX() - startBlockPos.getX()
            )));
            double reverseGeoAngle = NodeGeometry.normalizeDegrees(geoAngleDeg + 180.0);

            double startExitDeg;
            double endExitDeg;

            // ★ 修正: StraightNodeもMTR標準ノードも、すべて「軸」として扱い chooseBestExit を適用する
            // これにより、標準ノードの背面に接続先があっても、自動的に180°反転して最適な出口を選ぶ
            if (startAxis != null) {
                startExitDeg = NodeGeometry.chooseBestExit(startAxis, geoAngleDeg);
            } else if (endAxis != null) {
                double endExit = NodeGeometry.chooseBestExit(endAxis, reverseGeoAngle);
                startExitDeg = NodeGeometry.maxRadiusTangentAngle(endBlockPos, endExit, startBlockPos);
            } else {
                startExitDeg = geoAngleDeg;
            }

            if (endAxis != null) {
                endExitDeg = NodeGeometry.chooseBestExit(endAxis, reverseGeoAngle);
            } else if (startAxis != null) {
                double startExit = NodeGeometry.chooseBestExit(startAxis, geoAngleDeg);
                endExitDeg = NodeGeometry.maxRadiusTangentAngle(startBlockPos, startExit, endBlockPos);
            } else {
                endExitDeg = reverseGeoAngle;
            }

            if (beStart != null && !beStart.isBound() && startAxis == null) {
                beStart.bind(startExitDeg);
            }
            if (beEnd != null && !beEnd.isBound() && endAxis == null) {
                beEnd.bind(endExitDeg);
            }

            Angle finalStartAngle = AngleExtra.fromDegrees(startExitDeg);
            Angle finalEndAngle = AngleExtra.fromDegrees(endExitDeg);

            onConnect(
                    world, context.getStack(), startNode.transportMode,
                    startState, endState, startBlockPos, endBlockPos,
                    finalStartAngle, finalEndAngle, ServerPlayerEntity.cast(player)
            );

            if (beStart != null && beStart.isBound()) beStart.updateConnectedRails(true);
            if (beEnd != null && beEnd.isBound()) beEnd.updateConnectedRails(true);

            tag.remove(TAG_TRANSPORT_MODE);
            ci.cancel();
        } else {
            onRemove(world, startBlockPos, endBlockPos, ServerPlayerEntity.cast(player));
            tag.remove(TAG_TRANSPORT_MODE);
            ci.cancel();
        }
    }

    private Double getFixedAngle(StraightNodeBlockEntity be, BlockState state) {
        if (be == null) {
            return (double) BlockNode.getAngle(state);
        } else if (be.isBound()) {
            return be.getAngleDegrees();
        }
        return null;
    }

    private static StraightNodeBlockEntity getStraightNodeBE(World world, BlockPos pos) {
        org.mtr.mapping.holder.BlockEntity rawBe = world.getBlockEntity(pos);
        if (rawBe != null && rawBe.data instanceof StraightNodeBlockEntity be) {
            return be;
        }
        return null;
    }
}