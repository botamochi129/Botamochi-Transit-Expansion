package botamochi129.bte.mod.data;

import org.mtr.core.tool.Angle;

public class AngleExtra {

    /**
     * MTR の Angle.fromAngle() は 22.5度スナップのため、
     * 正確な角度は Bézier カーブの startRad/endRad で保持される
     */
    public static Angle fromDegrees(double degrees) {
        return Angle.fromAngle((float) degrees);
    }
}