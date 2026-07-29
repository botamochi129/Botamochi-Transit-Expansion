package botamochi129.bte.mod.data;

import org.mtr.core.tool.Vector;

public interface IRailMathExtra {
    void bte$enableBezier(Vector startPos, double startRad, Vector endPos, double endRad);
    boolean bte$isBezierEnabled();
    double bte$getStartRad();
    double bte$getEndRad();
}