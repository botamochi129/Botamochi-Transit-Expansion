package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.BezierCurve;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PathData.class, remap = false)
public abstract class PathDataMixin {

    // ★ 修正: protected フィールドへのアクセスを避け、public なものだけ Shadow する
    @Shadow public abstract Position getOrderedPosition1();
    @Shadow public abstract Position getOrderedPosition2();

    // PathData の public final boolean reversePositions を Shadow
    @Shadow @Final public boolean reversePositions;

    @Shadow public abstract double getStartDistance();
    @Shadow public abstract double getEndDistance();

    @Inject(method = "getPosition(D)Lorg/mtr/core/tool/Vector;", at = @At("HEAD"), cancellable = true)
    private void bte$overrideGetPosition(double rawValue, CallbackInfoReturnable<Vector> cir) {
        // ★ 修正: reversePositions を使って、常に「進行方向の始点 (startPosition)」と「終点 (endPosition)」を復元する
        Position p1 = this.reversePositions ? this.getOrderedPosition2() : this.getOrderedPosition1();
        Position p2 = this.reversePositions ? this.getOrderedPosition1() : this.getOrderedPosition2();

        if (p1 == null || p2 == null) return;

        long x1 = Math.min(p1.getX(), p2.getX());
        long y1 = Math.min(p1.getY(), p2.getY());
        long z1 = Math.min(p1.getZ(), p2.getZ());
        long x2 = Math.max(p1.getX(), p2.getX());
        long y2 = Math.max(p1.getY(), p2.getY());
        long z2 = Math.max(p1.getZ(), p2.getZ());

        String key = x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2;
        double[] bezierData = StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.get(key);

        if (bezierData != null && bezierData.length >= 14) {
            Vector savedStart = new Vector(bezierData[0], bezierData[1], bezierData[2]);
            Vector savedEnd = new Vector(bezierData[3], bezierData[4], bezierData[5]);

            long startBlockX = (long) bezierData[10];
            long startBlockZ = (long) bezierData[11];
            long endBlockX = (long) bezierData[12];
            long endBlockZ = (long) bezierData[13];

            Vector startPos;
            Vector endPos;
            double startRad;
            double endRad;

            // p1 (進行方向の始点) が Map の始点ブロックと一致するかで向きを決定
            if (p1.getX() == startBlockX && p1.getZ() == startBlockZ) {
                startPos = savedStart;
                endPos = savedEnd;
                startRad = bezierData[6];
                endRad = bezierData[7];
            } else if (p1.getX() == endBlockX && p1.getZ() == endBlockZ) {
                // p1 が Map の終点ブロックと一致する場合 (復路など)
                // ベジェの始点・終点を入れ替えて、進行方向に合わせる
                startPos = savedEnd;
                endPos = savedStart;
                startRad = bezierData[7];
                endRad = bezierData[6];
            } else {
                return; // ブロック座標が一致しない場合はMTR標準に任せる
            }

            double verticalRadius = bezierData[8];
            Rail.Shape shape = Rail.Shape.QUADRATIC;
            int shapeOrdinal = (int) bezierData[9];
            for (Rail.Shape s : Rail.Shape.values()) {
                if (s.ordinal() == shapeOrdinal) { shape = s; break; }
            }

            BezierCurve curve = new BezierCurve(startPos, startRad, endPos, endRad, verticalRadius, shape);
            double bezierLength = curve.getLength();

            double mtrLength = this.getEndDistance() - this.getStartDistance();
            if (mtrLength <= 0) mtrLength = bezierLength;

            double ratio = Math.max(0, Math.min(rawValue, mtrLength)) / mtrLength;
            double bezierDistance = ratio * bezierLength;

            Vector pos = curve.getPosition(bezierDistance);
            cir.setReturnValue(pos);
        }
    }
}