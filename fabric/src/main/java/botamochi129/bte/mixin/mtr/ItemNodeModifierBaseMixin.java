package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.AngleExtra;
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

    @Shadow protected abstract void onConnect(World world, org.mtr.mapping.holder.ItemStack stack, TransportMode transportMode,
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

            // 【重要修正】1. 基準角度の取得
            double startDeg;
            if (beStart == null) {
                // MTR標準ノード: 角度は絶対に変更しない (22.5°刻みを維持)
                startDeg = BlockNode.getAngle(startState);
            } else if (beStart.isBound()) {
                // StraightNode (バインド済み): 設定された角度を使用
                startDeg = beStart.getAngleDegrees();
            } else {
                // StraightNode (未バインド): 幾何学的な直線角度を基準にする
                startDeg = calculateStraightAngle(startBlockPos, endBlockPos);
            }

            double endDeg;
            if (beEnd == null) {
                // MTR標準ノード: 角度は絶対に変更しない (22.5°刻みを維持)
                endDeg = BlockNode.getAngle(endState);
            } else if (beEnd.isBound()) {
                // StraightNode (バインド済み): 設定された角度を使用
                endDeg = beEnd.getAngleDegrees();
            } else {
                // StraightNode (未バインド): 幾何学的な直線角度を基準にする
                endDeg = calculateStraightAngle(endBlockPos, startBlockPos);
            }

            // 【重要修正】2. MTR標準の similarFacing 補正を適用
            // バインド済みであっても、接続方向に対して逆向きなら補正し、内部データもそれに合わせる
            float geoAngleDeg = (float) normalize360(Math.toDegrees(Math.atan2(
                    endBlockPos.getZ() - startBlockPos.getZ(),
                    endBlockPos.getX() - startBlockPos.getX()
            )));
            float reverseGeoAngle = (float) normalize360(geoAngleDeg + 180.0);

            if (!Angle.similarFacing(geoAngleDeg, (float) startDeg)) {
                startDeg = normalize360(startDeg + 180);
            }
            if (!Angle.similarFacing(reverseGeoAngle, (float) endDeg)) {
                endDeg = normalize360(endDeg + 180);
            }

            // 3. 角度を更新 (補正後の角度でバインドし、内部データと描画を完全に同期させる)
            if (beStart != null) {
                beStart.bind(startDeg);
            }
            if (beEnd != null) {
                beEnd.bind(endDeg);
            }

            Angle finalStartAngle = AngleExtra.fromDegrees(startDeg);
            Angle finalEndAngle = AngleExtra.fromDegrees(endDeg);

            // 4. MTR標準の接続処理を実行
            onConnect(
                    world, context.getStack(), startNode.transportMode,
                    startState, endState, startBlockPos, endBlockPos,
                    finalStartAngle, finalEndAngle, ServerPlayerEntity.cast(player)
            );

            // 5. バインド済みノードのベジェ曲線を更新
            if (beStart != null && beStart.isBound()) beStart.updateConnectedRails();
            if (beEnd != null && beEnd.isBound()) beEnd.updateConnectedRails();

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