package botamochi129.bte.mod.data;

public class CantContext {
    private static final ThreadLocal<CantData> CURRENT = new ThreadLocal<>();

    public static void set(CantData data) { CURRENT.set(data); }
    public static CantData get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }

    public static class CantData {
        public final double rollRad, centerX, centerY, centerZ;
        public final double dirX, dirZ; // ★ レールの進行方向（接線ベクトル）

        public CantData(double rollRad, double centerX, double centerY, double centerZ, double dirX, double dirZ) {
            this.rollRad = rollRad;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.dirX = dirX;
            this.dirZ = dirZ;
        }
    }
}