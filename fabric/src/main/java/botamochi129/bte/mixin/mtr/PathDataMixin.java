package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.BezierCurve;
import botamochi129.bte.mod.data.IRailMathExtra;
import botamochi129.bte.mod.data.NodeGeometry;
import org.mtr.core.data.Data;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;
import org.mtr.mapping.holder.BlockEntity;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockNode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PathData.class, remap = false)
public abstract class PathDataMixin {

    @Shadow public abstract Rail getRail();
    @Shadow public abstract Position getOrderedPosition1();
    @Shadow public abstract Position getOrderedPosition2();
    @Shadow @Final public boolean reversePositions;
    @Shadow public abstract double getStartDistance();
    @Shadow public abstract double getEndDistance();

    // ★ 追加: MTR内部の rail フィールドに直接アクセスするため
    @Shadow(remap = false)
    private Rail rail;

    // ★★★ FangSu由来の修復 ★★★
    // クライアント側でパケット受信時に空データで初期化され、22.5°スナップショットになるのを防ぎ、
    // クライアント実データ (MinecraftClientData) から正確な Rail を再検索してセットする
    @Inject(method = "writePathCache(Lorg/mtr/core/data/Data;)V", at = @At("HEAD"), remap = false, cancellable = true)
    private void bte$writePathCache(Data data, CallbackInfo ci) {
        if (data.positionsToRail.isEmpty()) {
            final Data realData = bte$getClientData();
            if (realData != null && realData != data) {
                final Rail realRail = Data.tryGet(realData.positionsToRail, getOrderedPosition1(), getOrderedPosition2());
                if (realRail != null) {
                    this.rail = realRail;
                    ci.cancel();
                }
            }
        }
    }

    private static Data bte$getClientData() {
        try {
            return org.mtr.mod.client.MinecraftClientData.getInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Inject(method = "getPosition(D)Lorg/mtr/core/tool/Vector;", at = @At("HEAD"), cancellable = true)
    private void bte$overrideGetPosition(double rawValue, CallbackInfoReturnable<Vector> cir) {
        Rail currentRail = this.rail;
        if (currentRail == null) {
            try { currentRail = this.getRail(); } catch (Exception ignored) {}
        }
        if (currentRail == null || currentRail.railMath == null) return;

        IRailMathExtra mathExtra = (currentRail.railMath instanceof IRailMathExtra) ? (IRailMathExtra) currentRail.railMath : null;
        BezierCurve curve = (mathExtra != null) ? mathExtra.bte$getCurve() : null;

        // ★★★ フォールバック機能 ★★★
        // Fabric環境でMapがヒットした場合や、RenderRailsMixinで適用済みの場合はここをスキップ
        // Forge環境 (Mapが空) や適用漏れの場合は、その場でクライアント側のBlockEntityから計算して適用する
        if (curve == null && mathExtra != null) {
            ClientWorld world = MinecraftClient.getInstance().getWorldMapped();
            if (world != null) {
                Position p1 = this.reversePositions ? this.getOrderedPosition2() : this.getOrderedPosition1();
                Position p2 = this.reversePositions ? this.getOrderedPosition1() : this.getOrderedPosition2();

                if (p1 != null && p2 != null) {
                    BlockPos bp1 = Init.positionToBlockPos(p1);
                    BlockPos bp2 = Init.positionToBlockPos(p2);

                    double startRad = 0, endRad = 0;
                    double offX1 = 0, offY1 = 0, offZ1 = 0, offX2 = 0, offY2 = 0, offZ2 = 0;
                    boolean hasStraightNode = false;

                    BlockEntity be1 = world.getBlockEntity(bp1);
                    if (be1 != null && be1.data instanceof StraightNodeBlockEntity sn1 && sn1.isBound()) {
                        double geo = Math.toDegrees(Math.atan2(bp2.getZ() - bp1.getZ(), bp2.getX() - bp1.getX()));
                        startRad = Math.toRadians(NodeGeometry.chooseBestExit(sn1.getAngleDegrees(), geo));
                        offX1 = sn1.getOffsetX(); offY1 = sn1.getOffsetY(); offZ1 = sn1.getOffsetZ();
                        hasStraightNode = true;
                    } else {
                        startRad = Math.toRadians(BlockNode.getAngle(world.getBlockState(bp1)));
                    }

                    BlockEntity be2 = world.getBlockEntity(bp2);
                    if (be2 != null && be2.data instanceof StraightNodeBlockEntity sn2 && sn2.isBound()) {
                        double reverseGeo = Math.toDegrees(Math.atan2(bp1.getZ() - bp2.getZ(), bp1.getX() - bp2.getX()));
                        endRad = Math.toRadians(NodeGeometry.chooseBestExit(sn2.getAngleDegrees(), reverseGeo));
                        offX2 = sn2.getOffsetX(); offY2 = sn2.getOffsetY(); offZ2 = sn2.getOffsetZ();
                        hasStraightNode = true;
                    } else {
                        endRad = Math.toRadians(BlockNode.getAngle(world.getBlockState(bp2)));
                    }

                    if (hasStraightNode) {
                        Vector startVec = new Vector(bp1.getX() + 0.5 + offX1, bp1.getY() + offY1, bp1.getZ() + 0.5 + offZ1);
                        Vector endVec = new Vector(bp2.getX() + 0.5 + offX2, bp2.getY() + offY2, bp2.getZ() + 0.5 + offZ2);
                        mathExtra.bte$enableBezier(startVec, startRad, endVec, endRad, currentRail.railMath.getVerticalRadius(), currentRail.railMath.getShape());
                        curve = mathExtra.bte$getCurve();
                    }
                }
            }
        }

        // ベジェ適用済みなら座標を返す
        if (curve != null) {
            double bezierLength = curve.getLength();
            double mtrLength = this.getEndDistance() - this.getStartDistance();
            if (mtrLength <= 0) mtrLength = bezierLength;

            double clampedValue = Math.max(0, Math.min(rawValue, mtrLength));
            double ratio = clampedValue / mtrLength;

            // PathData の進行方向に合わせて距離を計算
            double targetDistance = ratio * bezierLength;
            cir.setReturnValue(curve.getPosition(targetDistance));
        }
    }
}