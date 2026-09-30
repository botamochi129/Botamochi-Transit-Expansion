package botamochi129.bte.mod.data;

import org.mtr.core.data.Position;
import org.mtr.core.tool.Angle;

/**
 * {@link org.mtr.core.data.Rail} が {@code final} で保持している保存値を読むための口。
 * <p>
 * MTR の {@code Rail} は {@code RailSchema} の {@code protected final} フィールド
 * (端点座標・端点角度・速度制限など) を公開アクセサ 없이保持しており、
 * {@code getStartAngle()} などの公開メソッドは角度について一部を派生値で返す。
 * <p>
 * 特に角度は BTE が {@link botamochi129.bte.mixin.mtr.RailStartAngleMixin} で
 * 一時的に上書きしているため、公開メソッド経由でレールの再構築を行うと
 * 「一時的な BTE 軸」が MTR の保存データに焼き込まれてしまう。
 * そのため再構築時は必ず本インタフェース経由で
 * <b>保存値そのまま</b>を読み書きし、BTE の派生値は永続化に混ぜない。
 */
public interface RailAccessor {

    /**
     * レールの端点1 (MTR 内部での position1)。
     */
    Position bte$getPosition1();

    /**
     * レールの端点2 (MTR 内部での position2)。
     */
    Position bte$getPosition2();

    /**
     * 保存されている端点1の角度。BTE の上書き値ではなく、MTR の保存値。
     */
    Angle bte$getSavedAngle1();

    /**
     * 保存されている端点2の角度。BTE の上書き値ではなく、MTR の保存値。
     */
    Angle bte$getSavedAngle2();

    /**
     * 保存されている端点1の制限速度 (km/h)。
     * <p>
     * {@code RailSchema} は km/h で永続化し、コンストラクタで
     * {@code *MetersPerMillisecond} に変換する。
     */
    long bte$getSpeedLimit1Kmh();

    /**
     * 保存されている端点2の制限速度 (km/h)。
     */
    long bte$getSpeedLimit2Kmh();

    /**
     * このレールが信号を設置できるかどうか。
     * <p>
     * MTR 側に公開アクセサが無いため、レール再構築時に
     * {@code RailType#hasSignal} 由来の値を復元するためだけに読み出す。
     */
    boolean bte$canHaveSignal();
}
