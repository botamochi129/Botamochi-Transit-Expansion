package botamochi129.bte.mod.data;

import org.mtr.mapping.holder.BlockPos;

public final class NodeGeometry {

    public static double straightAngle(BlockPos a, BlockPos b) {
        return normalizeDegrees(Math.toDegrees(Math.atan2(b.getZ() - a.getZ(), b.getX() - a.getX())));
    }

    /**
     * MTR の Rail 角度規約における「free 端の退出角度」を返す。
     * <p>
     * 曲線の場合は max-radius 円に接する接線を free で求め、fixed 側を向く向きに揃えている
     * ({@code fixTang} と fixedAngle の内積で反転を判定)。
     * <p>
     * ★ 修正: 同一直線 (nDotD == 0) の早期 return が {@code straightAngle(fixed, free)} を
     * そのまま返していたため、fixed -> free 方向 (進行方向) になり、上の曲線分岐と
     * 180 度ずれた規約を返していた。MTR は
     * {@code Rail.getAngles} / {@code BezierCurve(posStart, startAngleRad, posEnd, endAngleRad, ...)}
     * と同じく「angle1 は position1 から positionEnd へ」「angle2 は positionEnd から position1 へ」を
     * 期待しているため、同一直線でも fixed 側を向く角度を返す必要がある。
     * この不一致により、BTE ノード (未束縛) と MTR 標準ノードを同一直線で繋いだときだけ
     * 保存角度が 180 度ずれ、SidingPathFinder の
     * {@code node.angle == rail.getStartAngle(node.position)} が偽になって経路が切れていた。
     */
    public static double maxRadiusTangentAngle(BlockPos fixed, double fixedAngle, BlockPos free) {
        double fx = fixed.getX(), fz = fixed.getZ();
        double px = free.getX(), pz = free.getZ();
        double rad = Math.toRadians(fixedAngle);
        double dirX = Math.cos(rad), dirZ = Math.sin(rad);
        double nX = -dirZ, nZ = dirX;
        double dX = px - fx, dZ = pz - fz;
        double nDotD = nX * dX + nZ * dZ;
        if (Math.abs(nDotD) < 1e-6) return normalizeDegrees(straightAngle(fixed, free) + 180.0);
        double t = (dX * dX + dZ * dZ) / (2 * nDotD);
        double cx = fx + t * nX, cz = fz + t * nZ;
        double rX = px - cx, rZ = pz - cz;
        double tangX = -rZ, tangZ = rX;
        double fixTangX = -(fz - cz), fixTangZ = (fx - cx);
        if (fixTangX * dirX + fixTangZ * dirZ >= 0) { tangX = -tangX; tangZ = -tangZ; }
        return normalizeDegrees(Math.toDegrees(Math.atan2(tangZ, tangX)));
    }

    public static double normalizeDegrees(double deg) {
        deg %= 360.0;
        if (deg < 0) deg += 360.0;
        return deg;
    }

    // ★ 追加: 2つの角度が同じ向き（±90°以内）を向いているか判定
    public static boolean isFacingDirection(double nodeAngle, double connectionAngle) {
        double diff = Math.abs(nodeAngle - connectionAngle) % 360.0;
        if (diff > 180.0) diff = 360.0 - diff;
        return diff < 90.0;
    }

    // ★ 追加: 軸 (0~180°) から、ターゲット方向に最も近い出口 (0~360°) を選択する
    public static double chooseBestExit(double axisDeg, double targetGeoDeg) {
        double c1 = normalizeDegrees(axisDeg);
        double c2 = normalizeDegrees(axisDeg + 180.0);
        return isFacingDirection(c1, targetGeoDeg) ? c1 : c2;
    }
}