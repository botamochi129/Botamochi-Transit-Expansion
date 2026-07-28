package botamochi129.bte.mod.data;

import botamochi129.bte.mapping.LoaderImpl;
import org.mtr.core.tool.Angle;

public class AngleExtra {

    public static Angle fromDegrees(double degrees) {
        float f = (float) ((degrees % 360 + 360) % 360);
        for (Angle angle : Angle.values()) {
            if (Math.abs(angle.angleDegrees - f) < 0.001f) {
                return angle;
            }
        }
        return LoaderImpl.createDynamicAngle("DYN_" + String.format("%.2f", f), -1, f);
    }

    public static Angle fromRadians(double radians) {
        double degrees = Math.toDegrees(radians);
        float f = (float) ((degrees % 360 + 360) % 360);
        for (Angle angle : Angle.values()) {
            if (Math.abs(angle.angleDegrees - f) < 0.001f) {
                return angle;
            }
        }
        return LoaderImpl.createDynamicAngle("DYN_" + String.format("%.2f", f), -1, f);
    }
}
