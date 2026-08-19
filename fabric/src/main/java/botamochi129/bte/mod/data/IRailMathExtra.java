package botamochi129.bte.mod.data;

import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;

public interface IRailMathExtra {
    // ★ 引数に Rail.Shape を追加
    void bte$enableBezier(Vector startPos, double startRad, Vector endPos, double endRad, double verticalRadius, Rail.Shape shape);
    boolean bte$isBezierEnabled();
    double bte$getStartRoll();
    double bte$getEndRoll();

    BezierCurve bte$getCurve();

    void bte$setRoll(double startRoll, double endRoll);
}