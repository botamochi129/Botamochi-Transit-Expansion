package botamochi129.bte.mod.data;

import org.mtr.core.tool.Angle;

public class AngleHelper {

    public static Angle createDynamicAngle(String name, int ordinal, float degrees) {
        float normalized = ((degrees % 360) + 360) % 360;
        Angle closest = Angle.values()[0];
        float minDiff = Float.MAX_VALUE;
        for (Angle a : Angle.values()) {
            float diff = Math.abs(a.angleDegrees - normalized);
            if (diff < minDiff) {
                minDiff = diff;
                closest = a;
            }
        }
        return closest;
    }

    public static Angle fromDegrees(double degrees) {
        float f = (float) (((degrees % 360) + 360) % 360);
        for (Angle angle : Angle.values()) {
            if (Math.abs(angle.angleDegrees - f) < 0.001f) {
                return angle;
            }
        }
        Angle closest = Angle.values()[0];
        float minDiff = Float.MAX_VALUE;
        for (Angle a : Angle.values()) {
            float diff = Math.abs(a.angleDegrees - f);
            if (diff < minDiff) {
                minDiff = diff;
                closest = a;
            }
        }
        return closest;
    }
}
