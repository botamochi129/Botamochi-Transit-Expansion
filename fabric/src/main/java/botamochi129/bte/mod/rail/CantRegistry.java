package botamochi129.bte.mod.rail;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * レール hex id → カント設定 のランタイム索引。
 *
 * <p>カントは見た目だけの情報なので、描画側（レール・車両）はこの索引だけを見ればよい。
 * 値の供給元は両ローダー共通で {@code StraightNodeBlockEntity#readCompoundTag} であり、
 * これがサーバーのワールド読込時とクライアントへの BE 同期時の双方で呼ばれるため、
 * 追加の同期機構なしにサーバー・クライアントの双方へ行き渡る。
 *
 * <p>キーは {@code Rail#getHexId()}（正規化済み = 端点順が x→y→z 昇順）を用いる。
 */
public final class CantRegistry {

    private static final Map<String, CantProfile> BY_RAIL = new ConcurrentHashMap<>();

    private CantRegistry() {
    }

    public static void put(String railHexId, CantProfile profile) {
        if (railHexId == null || railHexId.isEmpty() || profile == null) return;
        if (profile.isNone()) {
            BY_RAIL.remove(railHexId);
        } else {
            BY_RAIL.put(railHexId, profile);
        }
    }

    public static void remove(String railHexId) {
        if (railHexId == null || railHexId.isEmpty()) return;
        BY_RAIL.remove(railHexId);
    }

    /**
     * @return 設定されたカント。未設定なら {@code null}。
     */
    public static CantProfile get(String railHexId) {
        if (railHexId == null || railHexId.isEmpty()) return null;
        return BY_RAIL.get(railHexId);
    }

    /** カントが設定されているレールのみを返す（車両側の最寄りレール探索用）。 */
    public static Map<String, CantProfile> all() {
        return BY_RAIL;
    }

    /**
     * セッション境界（ワールド切替・切断）で索引を捨てる。
     * 静的索引は JVM より長生きするため、明示的に消さないと前セッションの値が残る。
     */
    public static void clear() {
        BY_RAIL.clear();
    }
}
