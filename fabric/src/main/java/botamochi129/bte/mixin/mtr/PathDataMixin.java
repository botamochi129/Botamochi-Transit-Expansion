package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.BezierCurve;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PathData.class, remap = false)
public abstract class PathDataMixin {

    @Shadow public abstract Position getOrderedPosition1();
    @Shadow public abstract Position getOrderedPosition2();

    // ★ 修正: フィールドではなく Getterメソッドを Shadow する
    @Shadow public abstract double getStartDistance();
    @Shadow public abstract double getEndDistance();

    @Inject(method = "getPosition(D)Lorg/mtr/core/tool/Vector;", at = @At("HEAD"), cancellable = true)
    private void bte$overrideGetPosition(double rawValue, CallbackInfoReturnable<Vector> cir) {
        Position p1 = this.getOrderedPosition1();
        Position p2 = this.getOrderedPosition2();
        if (p1 == null || p2 == null) return;

        long x1 = Math.min(p1.getX(), p2.getX());
        long y1 = Math.min(p1.getY(), p2.getY());
        long z1 = Math.min(p1.getZ(), p2.getZ());
        long x2 = Math.max(p1.getX(), p2.getX());
        long y2 = Math.max(p1.getY(), p2.getY());
        long z2 = Math.max(p1.getZ(), p2.getZ());

        String key = x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2;
        double[] bezierData = StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.get(key);

        if (bezierData != null && bezierData.length >= 10) {
            Vector savedStart = new Vector(bezierData[0], bezierData[1], bezierData[2]);
            Vector savedEnd = new Vector(bezierData[3], bezierData[4], bezierData[5]);

            Vector p1Vec = new Vector(p1.getX() + 0.5, p1.getY(), p1.getZ() + 0.5);

            double dxStart = p1Vec.x() - savedStart.x();
            double dyStart = p1Vec.y() - savedStart.y();
            double dzStart = p1Vec.z() - savedStart.z();
            double distToStartSq = dxStart * dxStart + dyStart * dyStart + dzStart * dzStart;

            double dxEnd = p1Vec.x() - savedEnd.x();
            double dyEnd = p1Vec.y() - savedEnd.y();
            double dzEnd = p1Vec.z() - savedEnd.z();
            double distToEndSq = dxEnd * dxEnd + dyEnd * dyEnd + dzEnd * dzEnd;

            Vector startPos;
            Vector endPos;
            double startRad;
            double endRad;

            if (distToStartSq < 4.0 && distToStartSq <= distToEndSq) {
                startPos = savedStart;
                endPos = savedEnd;
                startRad = bezierData[6];
                endRad = bezierData[7];
            } else if (distToEndSq < 4.0) {
                startPos = savedEnd;
                endPos = savedStart;
                startRad = bezierData[7];
                endRad = bezierData[6];
            } else {
                return;
            }

            double verticalRadius = bezierData[8];
            Rail.Shape shape = Rail.Shape.QUADRATIC;
            int shapeOrdinal = (int) bezierData[9];
            for (Rail.Shape s : Rail.Shape.values()) {
                if (s.ordinal() == shapeOrdinal) {
                    shape = s;
                    break;
                }
            }

            BezierCurve curve = new BezierCurve(startPos, startRad, endPos, endRad, verticalRadius, shape);
            double bezierLength = curve.getLength();

            // ★ 修正: Shadowしたメソッドを呼び出して MTR側のレール長を取得
            double mtrLength = this.getEndDistance() - this.getStartDistance();
            if (mtrLength <= 0) mtrLength = bezierLength;

            double ratio = Math.max(0, Math.min(rawValue, mtrLength)) / mtrLength;
            double bezierDistance = ratio * bezierLength;

            Vector pos = curve.getPosition(bezierDistance);
            cir.setReturnValue(pos);
        }
    }
}