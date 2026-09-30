package botamochi129.bte.mod.rail;

import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * レール上の任意のワールド座標を「正規方向の進捗 [0,1]」へ射影し、
 * その点のカント角を返すヘルパー。
 *
 * <p>{@code RailMath#getPosition} は 1 回あたりが軽くないため、レールごとに
 * 折れ線サンプルをキャッシュする。レール形状が変わると署名が変わり自動で作り直す。
 */
public final class RailCantSampler {

    private static final int MIN_SAMPLES = 8;
    private static final int MAX_SAMPLES = 64;
    private static final double SAMPLE_SPACING = 1.25D;

    private static final Map<String, Samples> CACHE = new ConcurrentHashMap<>();

    private RailCantSampler() {
    }

    /**
     * 指定座標に最も近いレール上の点の進捗を返す。
     *
     * @return [0,1] の進捗。算出できなければ 0。
     */
    public static double progressOnRail(Rail rail, double x, double y, double z) {
        final double[] sample = sampleOnRail(rail, x, y, z);
        return sample == null ? 0.0D : sample[0];
    }

    /**
     * 指定座標に最も近いレール上の点について、進捗と接線方向を返す。
     *
     * @return {@code [進捗, 前進X, 前進Z, 距離二乗]}。算出できなければ {@code null}。
     *         前進ベクトルは {@link Rail#getHexId()} が定める正規方向（position1 → position2）。
     */
    public static double[] sampleOnRail(Rail rail, double x, double y, double z) {
        final Samples samples = samplesFor(rail);
        if (samples == null) {
            return null;
        }

        int bestSegment = 0;
        double bestT = 0.0D;
        double bestDistance = Double.MAX_VALUE;

        for (int i = 0; i < samples.segments; i++) {
            final double startX = samples.px[i];
            final double startY = samples.py[i];
            final double startZ = samples.pz[i];
            final double endX = samples.px[i + 1];
            final double endY = samples.py[i + 1];
            final double endZ = samples.pz[i + 1];

            final double segX = endX - startX;
            final double segY = endY - startY;
            final double segZ = endZ - startZ;
            final double lengthSq = segX * segX + segY * segY + segZ * segZ;

            final double t;
            if (lengthSq < 1.0E-9D) {
                t = 0.0D;
            } else {
                t = clamp01(((x - startX) * segX + (y - startY) * segY + (z - startZ) * segZ) / lengthSq);
            }

            final double dx = x - (startX + segX * t);
            final double dy = y - (startY + segY * t);
            final double dz = z - (startZ + segZ * t);
            final double distance = dx * dx + dy * dy + dz * dz;

            if (distance < bestDistance) {
                bestDistance = distance;
                bestSegment = i;
                bestT = t;
            }
        }

        final double progress = (bestSegment + bestT) / samples.segments;
        final double segX = samples.px[bestSegment + 1] - samples.px[bestSegment];
        final double segZ = samples.pz[bestSegment + 1] - samples.pz[bestSegment];
        final double flatLength = Math.sqrt(segX * segX + segZ * segZ);
        if (flatLength < 1.0E-9D) {
            return new double[]{progress, 0.0D, 0.0D, bestDistance};
        }
        return new double[]{progress, segX / flatLength, segZ / flatLength, bestDistance};
    }

    /**
     * 指定座標におけるカント角（度）。未設定または算出不能なら 0。
     */
    public static float cantDegreesOnRail(Rail rail, double x, double y, double z) {
        if (rail == null) {
            return 0.0F;
        }
        final CantProfile profile = CantRegistry.get(rail.getHexId());
        if (profile == null || profile.isNone()) {
            return 0.0F;
        }
        return profile.interpolateDegrees(progressOnRail(rail, x, y, z));
    }

    private static Samples samplesFor(Rail rail) {
        if (rail == null || rail.railMath == null) {
            return null;
        }
        final String railId = rail.getHexId();
        if (railId == null || railId.isEmpty()) {
            return null;
        }

        final long signature = signatureOf(rail);
        final Samples existing = CACHE.get(railId);
        if (existing != null && existing.signature == signature) {
            return existing;
        }

        final Samples rebuilt = build(rail, signature);
        if (rebuilt != null) {
            CACHE.put(railId, rebuilt);
            return rebuilt;
        }
        return existing;
    }

    private static Samples build(Rail rail, long signature) {
        final double length;
        try {
            length = rail.railMath.getLength();
        } catch (Throwable ignored) {
            return null;
        }
        if (!(length > 1.0E-4D)) {
            return null;
        }

        final int segments = Math.max(MIN_SAMPLES, Math.min(MAX_SAMPLES, (int) Math.ceil(length / SAMPLE_SPACING)));
        final double[] px = new double[segments + 1];
        final double[] py = new double[segments + 1];
        final double[] pz = new double[segments + 1];

        for (int i = 0; i <= segments; i++) {
            final Vector point;
            try {
                point = rail.railMath.getPosition(length * i / segments, false);
            } catch (Throwable ignored) {
                return null;
            }
            if (point == null) {
                return null;
            }
            px[i] = point.x();
            py[i] = point.y();
            pz[i] = point.z();
        }
        return new Samples(signature, segments, px, py, pz);
    }

    private static long signatureOf(Rail rail) {
        long hash = 1469598103934665603L;
        try {
            final Rail.Shape shape = rail.railMath.getShape();
            hash = (hash ^ Double.doubleToLongBits(rail.railMath.getLength())) * 1099511628211L;
            hash = (hash ^ Double.doubleToLongBits(rail.railMath.getVerticalRadius())) * 1099511628211L;
            hash = (hash ^ (shape == null ? 0 : shape.ordinal())) * 1099511628211L;
        } catch (Throwable ignored) {
            return 0L;
        }
        return hash;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static final class Samples {
        private final long signature;
        private final int segments;
        private final double[] px;
        private final double[] py;
        private final double[] pz;

        private Samples(long signature, int segments, double[] px, double[] py, double[] pz) {
            this.signature = signature;
            this.segments = segments;
            this.px = px;
            this.py = py;
            this.pz = pz;
        }
    }
}
