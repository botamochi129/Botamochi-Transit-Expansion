package botamochi129.bte.mod.rail;

import botamochi129.bte.mod.data.RailAccessor;
import org.mtr.core.data.Rail;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * MTR の {@link Rail} を、指定した制限速度を持つ同等のレールとして再構築する補助。
 * <p>
 * MTR 4.x は既にレール毎の制限速度を保持している:
 * <ul>
 *     <li>{@code RailType#speedLimit} — レールコネクター種別ごとの初期値 (鉄レール = 80 km/h)</li>
 *     <li>{@code Rail.speedLimit1/2} — レール毎の上書き値 (km/h, {@code final})</li>
 *     <li>{@code RailSchema} が上記をワールドセーブへ永続化する</li>
 * </ul>
 * したがって BTE は MTR の保存フォーマットを一切変更せず、
 * {@code Rail#newRail} で {@code speedLimit1/2} のみ差し替えた {@link Rail} を作り、
 * MTR 標準の {@code UpdateDataRequest} 経由でサーバーへ送ればよい。
 * サーバー適用・クライアント同期・減速ロジック (
 * {@code Siding#getUpcomingSlowerSpeed} → {@code PathData#getSpeedLimitMetersPerMillisecond})
 * は MTR がそのまま面倒を見る。
 * <p>
 * 端点角度は {@link RailAccessor} 経由で<b>保存値</b>を読む。
 * 公開メソッド {@code getStartAngle()} は BTE のノード軸で上書きされているため、
 * こちらを使うと一時的な派生値が MTR の保存データに焼き込まれる。
 */
public final class RailBuilder {

    private RailBuilder() {
    }

    /**
     * 指定した制限速度 (km/h) を持つ {@link Rail} を生成する。
     *
     * @param rail           原本となるレール
     * @param speedLimit1Kmh 端点1の制限速度 (km/h)
     * @param speedLimit2Kmh 端点2の制限速度 (km/h)
     * @return 生成したレール。変更が不要または対象の形式外なら {@code null}
     */
    public static Rail withSpeedLimitKmh(Rail rail, long speedLimit1Kmh, long speedLimit2Kmh) {
        if (!(((Object) rail) instanceof RailAccessor accessor)) {
            return null;
        }

        if (accessor.bte$getSpeedLimit1Kmh() == speedLimit1Kmh
                && accessor.bte$getSpeedLimit2Kmh() == speedLimit2Kmh) {
            return null;
        }

        // MTR の newRail は canTurnBack を受け取らない (終端の折返し専用レール向けの別生成関数がある)。
        // 折返しレールを再構築すると canTurnBack が false へ落ちて終端挙動が壊れるため、対象外とする。
        if (rail.canTurnBack()) {
            return null;
        }

        final ObjectArrayList<String> styles = new ObjectArrayList<>(rail.getStyles());

        return Rail.newRail(
                accessor.bte$getPosition1(),
                accessor.bte$getSavedAngle1(),
                accessor.bte$getPosition2(),
                accessor.bte$getSavedAngle2(),
                rail.railMath.getShape(),
                rail.railMath.getVerticalRadius(),
                styles,
                speedLimit1Kmh,
                speedLimit2Kmh,
                rail.isPlatform(),
                rail.isSiding(),
                rail.canAccelerate(),
                rail.canConnectRemotely(),
                accessor.bte$canHaveSignal(),
                rail.getTransportMode()
        );
    }

    /**
     * 現在の保存されている制限速度 (端点1, 端点2) を km/h で返す。
     */
    public static long[] getSpeedLimitKmh(Rail rail) {
        if (!(((Object) rail) instanceof RailAccessor accessor)) {
            return new long[]{-1L, -1L};
        }
        return new long[]{accessor.bte$getSpeedLimit1Kmh(), accessor.bte$getSpeedLimit2Kmh()};
    }
}
