package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.AngleExtra;
import botamochi129.bte.mod.data.RailCalculator;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.ItemUsageContext;
import org.mtr.mapping.holder.PlayerEntity;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.holder.Text;
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

        if (!tag.contains("start_x") || !tag.contains("start_y") || !tag.contains("start_z")) {
            return;
        }
        BlockPos startBlockPos = new BlockPos(tag.getInt("start_x"), tag.getInt("start_y"), tag.getInt("start_z"));

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

            // 1. 角度の初期値を設定 (バインド済みなら double 精度の値、そうでなければ MTR 標準値)
            double startDeg = (beStart != null && beStart.isBound()) ? beStart.getAngleDegrees() : BlockNode.getAngle(startState);
            double endDeg = (beEnd != null && beEnd.isBound()) ? beEnd.getAngleDegrees() : BlockNode.getAngle(endState);

            boolean hasStart = (beStart != null && beStart.isBound());
            boolean hasEnd = (beEnd != null && beEnd.isBound());

            // 2. 両方とも未バインドの場合：直線角度で自動バインド（一旦同じ角度を入れ、後で補正で向きを合わせる）
            if (beStart != null && beEnd != null && !hasStart && !hasEnd) {
                double straightDeg = calculateStraightAngle(startBlockPos, endBlockPos);
                startDeg = straightDeg;
                endDeg = straightDeg;
                hasStart = true;
                hasEnd = true;
            }

            // 3. 片方だけがバインドされている場合：RailCalculator で最適な角度を計算
            if (hasStart && !hasEnd && beEnd != null) {
                Double calculatedEndDeg = RailCalculator.calculateMaxRadiusAngle(
                        startBlockPos.getX(), startBlockPos.getZ(),
                        endBlockPos.getX(), endBlockPos.getZ(),
                        Math.toRadians(startDeg)
                );
                if (calculatedEndDeg != null) {
                    endDeg = calculatedEndDeg;
                    hasEnd = true;
                } else {
                    if (player != null) player.sendMessage(Text.of("Invalid orientation"), true);
                    tag.remove(TAG_TRANSPORT_MODE);
                    ci.cancel();
                    return;
                }
            } else if (hasEnd && !hasStart && beStart != null) {
                Double calculatedStartDeg = RailCalculator.calculateMaxRadiusAngle(
                        endBlockPos.getX(), endBlockPos.getZ(),
                        startBlockPos.getX(), startBlockPos.getZ(),
                        Math.toRadians(endDeg)
                );
                if (calculatedStartDeg != null) {
                    startDeg = calculatedStartDeg;
                    hasStart = true;
                } else {
                    if (player != null) player.sendMessage(Text.of("Invalid orientation"), true);
                    tag.remove(TAG_TRANSPORT_MODE);
                    ci.cancel();
                    return;
                }
            }

            // 4. 接続確定とベクトルの向き補正
            if (hasStart && hasEnd) {
                // 始点から終点への方向
                float geoAngleDeg = (float) ((Math.toDegrees(Math.atan2(
                        endBlockPos.getZ() - startBlockPos.getZ(),
                        endBlockPos.getX() - startBlockPos.getX()
                )) % 360 + 360) % 360);

                // 【修正】終点から始点への方向を別途計算
                float reverseGeoAngle = (float) ((Math.toDegrees(Math.atan2(
                        startBlockPos.getZ() - endBlockPos.getZ(),
                        startBlockPos.getX() - endBlockPos.getX()
                )) % 360 + 360) % 360);

                // 始点の角度が進行方向と逆向きなら180度反転
                if (!Angle.similarFacing(geoAngleDeg, (float) startDeg)) {
                    startDeg = (startDeg + 180) % 360;
                }
                // 【修正】終点の角度が「終点から始点への方向」と逆向きなら180度反転
                if (!Angle.similarFacing(reverseGeoAngle, (float) endDeg)) {
                    endDeg = (endDeg + 180) % 360;
                }

                Angle finalStartAngle = AngleExtra.fromDegrees(startDeg);
                Angle finalEndAngle = AngleExtra.fromDegrees(endDeg);

                // 【重要】5. まず MTR 標準の接続処理を行い、Rail オブジェクトを生成・登録させる
                onConnect(
                        world,
                        context.getStack(),
                        startNode.transportMode,
                        startState,
                        endState,
                        startBlockPos,
                        endBlockPos,
                        finalStartAngle,
                        finalEndAngle,
                        ServerPlayerEntity.cast(player)
                );

                // 【重要】6. Rail が生成された AFTER に、BE の角度を更新し、updateRailwayData でベジェ上書きを実行させる
                if (beStart != null) {
                    beStart.bind(startDeg);
                }
                if (beEnd != null) {
                    beEnd.bind(endDeg);
                }
            } else {
                if (player != null) player.sendMessage(Text.of("Unbound straight node"), true);
            }
        } else {
            onRemove(world, startBlockPos, endBlockPos, ServerPlayerEntity.cast(player));
        }

        tag.remove(TAG_TRANSPORT_MODE);
        ci.cancel();
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
        return ((deg % 360) + 360) % 360;
    }
}