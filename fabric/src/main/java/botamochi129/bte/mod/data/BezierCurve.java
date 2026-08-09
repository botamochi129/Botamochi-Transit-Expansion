package botamochi129.bte.mod.data;

import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;

public class BezierCurve {
    private final Vector p0, p1, p2, p3;
    private final double length;
    private final double startY, endY;
    private final double verticalRadius;
    private final Rail.Shape shape;
    private static final int LENGTH_STEPS = 100;
    private static final double STRAIGHT_THRESHOLD = 0.05; // 約3度以内なら直線とみなす

    public BezierCurve(Vector posStart, double startAngleRad, Vector posEnd, double endAngleRad, double verticalRadius, Rail.Shape shape) {
        this.p0 = posStart;
        this.p3 = posEnd;
        this.startY = posStart.y();
        this.endY = posEnd.y();
        this.verticalRadius = verticalRadius;
        this.shape = shape != null ? shape : Rail.Shape.QUADRATIC;

        double dx = posEnd.x() - posStart.x();
        double dz = posEnd.z() - posStart.z();
        double dist = Math.hypot(dx, dz);

        // 幾何学的な直線方向（ラジアン）
        double geoAngle = Math.atan2(dz, dx);
        double reverseGeoAngle = geoAngle + Math.PI;

        // ★ 直線スナップ判定: 渡された角度が幾何学方向とほぼ一致しているか？
        boolean isStraightStart = Math.abs(normalizeRad(startAngleRad - geoAngle)) < STRAIGHT_THRESHOLD;
        boolean isStraightEnd = Math.abs(normalizeRad(endAngleRad - reverseGeoAngle)) < STRAIGHT_THRESHOLD;

        double handleLength = dist / 2.0;

        // ★ 両端とも直線方向を向いている場合、制御点を厳密に直線上へ配置（カーブ半径0を保証）
        if (isStraightStart && isStraightEnd) {
            this.p1 = new Vector(
                    posStart.x() + Math.cos(geoAngle) * handleLength,
                    posStart.y(),
                    posStart.z() + Math.sin(geoAngle) * handleLength
            );
            this.p2 = new Vector(
                    posEnd.x() + Math.cos(reverseGeoAngle) * handleLength,
                    posEnd.y(),
                    posEnd.z() + Math.sin(reverseGeoAngle) * handleLength
            );
        } else {
            // それ以外は、指定された角度通りに制御点を配置
            this.p1 = new Vector(
                    posStart.x() + Math.cos(startAngleRad) * handleLength,
                    posStart.y(),
                    posStart.z() + Math.sin(startAngleRad) * handleLength
            );
            this.p2 = new Vector(
                    posEnd.x() + Math.cos(endAngleRad) * handleLength,
                    posEnd.y(),
                    posEnd.z() + Math.sin(endAngleRad) * handleLength
            );
        }

        this.length = calculateHorizontalLength(LENGTH_STEPS);
    }

    private static double normalizeRad(double rad) {
        rad = rad % (2 * Math.PI);
        if (rad > Math.PI) rad -= 2 * Math.PI;
        if (rad < -Math.PI) rad += 2 * Math.PI;
        return rad;
    }

    public Vector getPoint(double t) {
        t = Math.max(0, Math.min(1, t));
        double u = 1 - t;
        double tt = t * t;
        double uu = u * u;
        double uuu = uu * u;
        double ttt = tt * t;

        double x = uuu * p0.x() + 3 * uu * t * p1.x() + 3 * u * tt * p2.x() + ttt * p3.x();
        double z = uuu * p0.z() + 3 * uu * t * p1.z() + 3 * u * tt * p2.z() + ttt * p3.z();

        double value = t * this.length;
        double y = calculateY(value);

        return new Vector(x, y, z);
    }

    /**
     * MTRの RailMath#getPositionY ロジックを完全移植
     */
    private double calculateY(double value) {
        if (Math.abs(startY - endY) < 1e-5) return startY;

        switch (this.shape) {
            case TWO_RADII:
                if (verticalRadius <= 0) {
                    return startY + (endY - startY) * (value / length);
                }

                double height = Math.abs(endY - startY);
                double innerSqrt = Math.max(0, height * height - 4.0 * verticalRadius * height + length * length);
                double vTheta = 2.0 * Math.atan2(Math.sqrt(innerSqrt) - length, height - 4.0 * verticalRadius);

                double curveLength = Math.sin(vTheta) * verticalRadius;
                double curveHeight = (1.0 - Math.cos(vTheta)) * verticalRadius;
                int sign = startY < endY ? 1 : -1;

                if (value < curveLength) {
                    return sign * (verticalRadius - Math.sqrt(Math.max(0, verticalRadius * verticalRadius - value * value))) + startY;
                } else if (value > length - curveLength) {
                    double r = length - value;
                    return -sign * (verticalRadius - Math.sqrt(Math.max(0, verticalRadius * verticalRadius - r * r))) + endY;
                } else {
                    double midY = startY + sign * curveHeight;
                    double midProgress = (value - curveLength) / Math.max(1e-5, length - 2.0 * curveLength);
                    return midY + sign * (Math.abs(endY - startY) - 2.0 * curveHeight) * midProgress;
                }

            case QUADRATIC:
            default:
                // MTRの2次曲線（放物線）ロジック
                double intercept = length / 2.0;
                double yChange;
                double yInitial;
                double offsetValue;
                if (value < intercept) {
                    yChange = (endY - startY) / 2.0;
                    yInitial = startY;
                    offsetValue = value;
                } else {
                    yChange = (startY - endY) / 2.0;
                    yInitial = endY;
                    offsetValue = length - value;
                }
                return yChange * offsetValue * offsetValue / (intercept * intercept) + yInitial;
        }
    }

    public Vector getTangent(double t) {
        t = Math.max(0, Math.min(1, t));
        double u = 1 - t;
        double dx = 3 * u * u * (p1.x() - p0.x()) + 6 * u * t * (p2.x() - p1.x()) + 3 * t * t * (p3.x() - p2.x());
        double dz = 3 * u * u * (p1.z() - p0.z()) + 6 * u * t * (p2.z() - p1.z()) + 3 * t * t * (p3.z() - p2.z());
        return new Vector(dx, 0, dz).normalize();
    }

    public double getTForDistance(double targetDistance) {
        if (length <= 1e-5 || targetDistance <= 0) return 0;
        if (targetDistance >= length) return 1;

        double low = 0.0;
        double high = 1.0;
        double mid = 0.5;

        for (int i = 0; i < 10; i++) {
            mid = (low + high) / 2.0;
            double currentDist = getLengthToT(mid, 20);
            if (currentDist < targetDistance) low = mid;
            else high = mid;
        }
        return mid;
    }

    public Vector getPosition(double distance) {
        double t = getTForDistance(distance);
        return getPoint(t);
    }

    public double getLength() {
        return length;
    }

    private double getLengthToT(double maxT, int steps) {
        double total = 0;
        double prevX = p0.x();
        double prevZ = p0.z();

        for (int i = 1; i <= steps; i++) {
            double t = maxT * ((double) i / steps);
            double u = 1 - t;
            double tt = t * t;
            double uu = u * u;
            double uuu = uu * u;
            double ttt = tt * t;

            double x = uuu * p0.x() + 3 * uu * t * p1.x() + 3 * u * tt * p2.x() + ttt * p3.x();
            double z = uuu * p0.z() + 3 * uu * t * p1.z() + 3 * u * tt * p2.z() + ttt * p3.z();

            double dx = x - prevX;
            double dz = z - prevZ;
            total += Math.sqrt(dx * dx + dz * dz);

            prevX = x;
            prevZ = z;
        }
        return total;
    }

    private double calculateHorizontalLength(int steps) {
        return getLengthToT(1.0, steps);
    }
}