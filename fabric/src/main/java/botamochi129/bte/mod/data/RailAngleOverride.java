package botamochi129.bte.mod.data;

import org.mtr.core.data.Position;

/**
 * Rail の端点角度を、保存値ではなく BTE ノード軸から導出した値で上書きするための口。
 * <p>
 * {@link botamochi129.bte.mod.block.entity.StraightNodeBlockEntity} が
 * {@code updateBezierDataOnly()} で既に全接続レールについて正しい角度を計算しているため、
 * その値を同じループで {@link botamochi129.bte.mixin.mtr.RailStartAngleMixin} に通知する。
 * <p>
 * なぜ必要か:
 * MTR は {@code Rail.angle1/angle2} を {@code final} で保持し、経路探索
 * ({@code SidingPathFinder}) はその保存値だけを厳密一致 ({@code ==}) で比較する:
 * <pre>
 *   node.angle == rail.getStartAngle(node.position)
 * </pre>
 * 一方 BTE のノード軸は画面からいつでも変更できるが、変更時は-bezier 曲線 (描画・走行位置)
 * しか更新されず、保存角度は書き戻されなかった。そのため
 * 「走行は新角度・経路探索は旧角度」という乖離が生じ、共有ノードで 180 度ちょうどが
 * 崩れて経路が切れていた (BTE 端が 171.761 度、既存 MTR レール端が 0 度 のケース等)。
 * <p>
 * 保存データに触れないため永続化・マイグレーションは不要であり、軸・座標・bind/unbind の
 * いずれ的操作に対しても常に同期した値が経路探索に見える。
 */
public interface RailAngleOverride {

    /**
     * 指定した端点位置の角度を、BTE ノード軸から導出した値で上書きする。
     *
     * @param position    上書き対象の端点位置 (Rail の position1 / position2 と一致させる)
     * @param angleDegrees 0〜360 度の進行方向
     */
    void bte$setAngleOverride(Position position, double angleDegrees);

    /**
     * 角度の上書きを解除する (ノードが unbind されたときなど)。
     *
     * @param position 解除対象の端点位置
     */
    void bte$clearAngleOverride(Position position);
}
