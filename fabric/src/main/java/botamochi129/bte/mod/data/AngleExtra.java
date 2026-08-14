package botamochi129.bte.mod.data;

import org.mtr.core.tool.Angle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface AngleExtra {
    Map<Float, Angle> BTE$PHANTOM_CACHE = new ConcurrentHashMap<>();

    static Angle fromDegrees(double degrees) {
        return ((AngleExtra) (Object) Angle.values()[0]).bte$fromDegrees(degrees);
    }

    Angle bte$fromDegrees(double degrees);
    void bte$setRadians(double radians);
}