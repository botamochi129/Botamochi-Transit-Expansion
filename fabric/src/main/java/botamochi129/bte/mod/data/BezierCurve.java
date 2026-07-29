package botamochi129.bte.mod.data;

import org.mtr.core.tool.Vector;

public class BezierCurve {
    private final Vector p0, p1, p2, p3;
    private final double length;
    private static final int LENGTH_STEPS = 100;

    public BezierCurve(Vector posStart, double startAngleRad, Vector posEnd, double endAngleRad) {
        this.p0 = posStart;
        this.p3 = posEnd;

        // 始点と終点の 2D 平面距離
        double dist = Math.hypot(posEnd.x() - posStart.x(), posEnd.z() - posStart.z());
        // 制御点の引き出し長さ（距離の半分）
        double handleLength = dist / 2.0;

        // 始点制御点 P1
        this.p1 = new Vector(
                posStart.x() + Math.cos(startAngleRad) * handleLength,
                posStart.y(),
                posStart.z() + Math.sin(startAngleRad) * handleLength
        );

        // 終点制御点 P2 (向きを正しく合わせる)
        this.p2 = new Vector(
                posEnd.x() + Math.cos(endAngleRad) * handleLength,
                posEnd.y(),
                posEnd.z() + Math.sin(endAngleRad) * handleLength
        );

        this.length = calculateLength(LENGTH_STEPS);
    }

    /**
     * パラメータ t (0.0 〜 1.0) における 3D 座標を取得
     */
    public Vector getPoint(double t) {
        t = Math.max(0, Math.min(1, t));
        double u = 1 - t;
        double tt = t * t;
        double uu = u * u;
        double uuu = uu * u;
        double ttt = tt * t;

        double x = uuu * p0.x() + 3 * uu * t * p1.x() + 3 * u * tt * p2.x() + ttt * p3.x();
        double y = uuu * p0.y() + 3 * uu * t * p1.y() + 3 * u * tt * p2.y() + ttt * p3.y();
        double z = uuu * p0.z() + 3 * uu * t * p1.z() + 3 * u * tt * p2.z() + ttt * p3.z();

        return new Vector(x, y, z);
    }

    /**
     * パラメータ t (0.0 〜 1.0) における接線ベクトル (進行方向) を取得
     */
    public Vector getTangent(double t) {
        t = Math.max(0, Math.min(1, t));
        double u = 1 - t;

        // 3次ベジェの1階微分 (導関数)
        double dx = 3 * u * u * (p1.x() - p0.x()) + 6 * u * t * (p2.x() - p1.x()) + 3 * t * t * (p3.x() - p2.x());
        double dy = 3 * u * u * (p1.y() - p0.y()) + 6 * u * t * (p2.y() - p1.y()) + 3 * t * t * (p3.y() - p2.y());
        double dz = 3 * u * u * (p1.z() - p0.z()) + 6 * u * t * (p2.z() - p1.z()) + 3 * t * t * (p3.z() - p2.z());

        return new Vector(dx, dy, dz);
    }

    /**
     * 指定された距離 (distance) に対応するパラメータ t (0.0 〜 1.0) を二分探索で求める
     */
    public double getTForDistance(double targetDistance) {
        if (length <= 1e-5 || targetDistance <= 0) return 0;
        if (targetDistance >= length) return 1;

        double low = 0.0;
        double high = 1.0;
        double mid = 0.5;

        // 二分探索 (10回のループで十分な精度が得られます)
        for (int i = 0; i < 10; i++) {
            mid = (low + high) / 2.0;
            double currentDist = getLengthToT(mid, 20);

            if (currentDist < targetDistance) {
                low = mid;
            } else {
                high = mid;
            }
        }

        return mid;
    }

    /**
     * 距離 (distance) から直接 3D 座標を取得
     */
    public Vector getPosition(double distance) {
        double t = getTForDistance(distance);
        return getPoint(t);
    }

    public double getLength() {
        return length;
    }

    /**
     * 0 から t までの曲線長を計算
     */
    private double getLengthToT(double maxT, int steps) {
        double total = 0;
        Vector prev = p0;
        for (int i = 1; i <= steps; i++) {
            double t = maxT * ((double) i / steps);
            Vector curr = getPoint(t);

            double dx = curr.x() - prev.x();
            double dy = curr.y() - prev.y();
            double dz = curr.z() - prev.z();
            total += Math.sqrt(dx * dx + dy * dy + dz * dz);
            prev = curr;
        }
        return total;
    }

    private double calculateLength(int steps) {
        return getLengthToT(1.0, steps);
    }
}