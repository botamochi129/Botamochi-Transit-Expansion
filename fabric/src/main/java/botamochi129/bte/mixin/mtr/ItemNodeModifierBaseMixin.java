package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
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

            // 両方がStraightNodeでなければ、MTR標準のロジックに完全に委ねる
            if (beStart == null && beEnd == null) {
                return;
            }

            boolean isStartBound = (beStart != null && beStart.isBound());
            boolean isEndBound = (beEnd != null && beEnd.isBound());
            boolean bothUnboundStraightNodes = (beStart != null && beEnd != null && !isStartBound && !isEndBound);

            // 1. 角度の決定
            //    - バインド済み → その確定角度を使用（上書きしない）
            //    - 片側だけバインド → 確定角度に接する最大半径円弧の接線（滑らかな曲線）
            //    - 両方未バインド → 2点を結ぶ直線の角度（直線優先）
            double startDeg;
            double endDeg;
            boolean startIsTangent = false;
            boolean endIsTangent = false;

            if (isStartBound) {
                startDeg = beStart.getAngleDegrees();
            } else if (isEndBound) {
                startDeg = NodeGeometry.maxRadiusTangentAngle(endBlockPos, beEnd.getAngleDegrees(), startBlockPos);
                startIsTangent = true;
            } else {
                startDeg = NodeGeometry.straightAngle(startBlockPos, endBlockPos);
            }

            if (isEndBound) {
                endDeg = beEnd.getAngleDegrees();
            } else if (isStartBound) {
                endDeg = NodeGeometry.maxRadiusTangentAngle(startBlockPos, beStart.getAngleDegrees(), endBlockPos);
                endIsTangent = true;
            } else {
                endDeg = NodeGeometry.straightAngle(endBlockPos, startBlockPos);
            }

            // 2. MTR標準の similarFacing 補正
            //    ※ 最大半径円弧の接線（既に進行方向に沿った角度）には適用しない
            float geoAngleDeg = (float) normalize360(Math.toDegrees(Math.atan2(
                    endBlockPos.getZ() - startBlockPos.getZ(),
                    endBlockPos.getX() - startBlockPos.getX()
            )));
            float reverseGeoAngle = (float) normalize360(geoAngleDeg + 180.0);

            if (!startIsTangent) {
                if (!Angle.similarFacing(geoAngleDeg, (float) startDeg)) {
                    startDeg = normalize360(startDeg + 180);
                }
            }
            if (!endIsTangent) {
                if (!Angle.similarFacing(reverseGeoAngle, (float) endDeg)) {
                    endDeg = normalize360(endDeg + 180);
                }
            }

            // 3. 両方が未バインドのStraightNodeの場合、0度と180度の自動揃え（直線化）を適用
            if (bothUnboundStraightNodes) {
                double angleDiff = getAngleDifference(startDeg, endDeg);
                if (angleDiff > 175.0 && angleDiff < 185.0) {
                    if (getAngleDifference(startDeg, geoAngleDeg) < getAngleDifference(endDeg, geoAngleDeg)) {
                        endDeg = startDeg;
                    } else {
                        startDeg = endDeg;
                    }
                }
            }

            // 4. バインド済みノードの角度を更新（ベジェ曲線の適用トリガー）
            if (beStart != null && !isStartBound) {
                beStart.bind(startDeg);
            }
            if (beEnd != null && !isEndBound) {
                beEnd.bind(endDeg);
            }

            Angle finalStartAngle = Angle.fromAngle((float) startDeg);
            Angle finalEndAngle = Angle.fromAngle((float) endDeg);

            // 5. MTR標準の接続処理を実行（ブロックステートの22.5°角度ではなく
            //    上で決定した角度でレールを作成するため「方向が無効です」を防止）
            onConnect(
                    world, context.getStack(), startNode.transportMode,
                    startState, endState, startBlockPos, endBlockPos,
                    finalStartAngle, finalEndAngle, ServerPlayerEntity.cast(player)
            );

            // 6. バインド済みノードのベジェ曲線を更新
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

    private static StraightNodeBlockEntity getStraightNodeBE(World world, BlockPos pos) {
        org.mtr.mapping.holder.BlockEntity rawBe = world.getBlockEntity(pos);
        if (rawBe != null && rawBe.data instanceof StraightNodeBlockEntity be) {
            return be;
        }
        return null;
    }

    private static double normalize360(double angle) {
        angle = angle % 360.0;
        if (angle < 0.0) angle += 360.0;
        return angle;
    }

    private static double getAngleDifference(double a1, double a2) {
        double diff = Math.abs(a1 - a2) % 360.0;
        return diff > 180.0 ? 360.0 - diff : diff;
    }
}