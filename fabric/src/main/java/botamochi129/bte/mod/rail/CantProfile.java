package botamochi129.bte.mod.rail;

/**
 * 1 レール区間のカント（外側レールの高さ）を 3 制御点で表す不変データ。
 *
 * <p>角度は「レールの正規方向」に対する見かけの傾き（度）で、右肩上がりを正とする。
 * 正規方向とは MTR の {@code Rail#getHexId()} が決める端点順（x → y → z の昇順）である。
 * 端点の選び方で意味が変わらないよう、保存は常にこの正規方向で行う。
 *
 * <p>区間内の補間は {@code start → middle → end} の区間線形。
 * 区間中央を最大にしたい場合は {@code start = end = 0, middle = 8} とすればよく、
 * レールを分割する必要はない。
 */
public final class CantProfile {

    /** 見た目として許容する最大カント角。車両が浮き上がりすぎるのを防ぐ。 */
    public static final float MAX_DEGREES = 45.0F;

    /** カントなしを表す共有インスタンス。 */
    public static final CantProfile NONE = new CantProfile(0.0F, 0.0F, 0.0F);

    public final float startDeg;
    public final float middleDeg;
    public final float endDeg;

    public CantProfile(float startDeg, float middleDeg, float endDeg) {
        this.startDeg = clamp(startDeg);
        this.middleDeg = clamp(middleDeg);
        this.endDeg = clamp(endDeg);
    }

    public static float clamp(float degrees) {
        if (!Float.isFinite(degrees)) return 0.0F;
        return Math.max(-MAX_DEGREES, Math.min(MAX_DEGREES, degrees));
    }

    public boolean isNone() {
        return startDeg == 0.0F && middleDeg == 0.0F && endDeg == 0.0F;
    }

    /**
     * 正規方向の進捗 {@code progress ∈ [0,1]} におけるカント角（度）。
     */
    public float interpolateDegrees(double progress) {
        final double t = Math.max(0.0D, Math.min(1.0D, progress));
        if (t <= 0.5D) {
            return lerp(startDeg, middleDeg, t * 2.0D);
        }
        return lerp(middleDeg, endDeg, (t - 0.5D) * 2.0D);
    }

    /**
     * 正規方向と逆向きから見た等価プロファイル（符号反転 + 両端の入れ替え）。
     */
    public CantProfile reversed() {
        return new CantProfile(-endDeg, -middleDeg, -startDeg);
    }

    private static float lerp(float a, float b, double t) {
        return (float) (a + (b - a) * t);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CantProfile profile)) return false;
        return Float.compare(startDeg, profile.startDeg) == 0
                && Float.compare(middleDeg, profile.middleDeg) == 0
                && Float.compare(endDeg, profile.endDeg) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(startDeg);
        result = 31 * result + Float.hashCode(middleDeg);
        result = 31 * result + Float.hashCode(endDeg);
        return result;
    }

    @Override
    public String toString() {
        return "Cant(" + startDeg + ", " + middleDeg + ", " + endDeg + ")";
    }
}
