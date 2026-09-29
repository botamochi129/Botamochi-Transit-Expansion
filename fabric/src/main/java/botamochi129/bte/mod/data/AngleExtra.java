package botamochi129.bte.mod.data;

import org.mtr.core.tool.Angle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface AngleExtra {
    Map<Float, Angle> BTE$PHANTOM_CACHE = new ConcurrentHashMap<>();

    /**
     * MTR の Angle.angleDegrees と同じ規約（[-180, 180)）で正規化し、浮動小数点ノイズを
     * 潰した正規値を返す。ここを通した値は「同じ向きなら必ず同じ値」になり、
     * BTE$PHANTOM_CACHE と Angle.values() の双方で同一インスタンスの一意性が保証される。
     * MTR の経路探索 (SidingPathFinder) は Angle を == で比較するため、この正規化が無いと
     * 同一向きのAnglesが別インスタンスになり接続が切れる。
     */
    static float canonicalize(float degrees) {
        float deg = degrees % 360.0f;
        if (deg >= 180.0f) deg -= 360.0f;
        else if (deg < -180.0f) deg += 360.0f;
        return Math.round(deg * 1000.0f) / 1000.0f;
    }

    static Angle fromDegrees(double degrees) {
        return ((AngleExtra) (Object) Angle.values()[0]).bte$fromDegrees(degrees);
    }

    Angle bte$fromDegrees(double degrees);
    void bte$setRadians(double radians);
}