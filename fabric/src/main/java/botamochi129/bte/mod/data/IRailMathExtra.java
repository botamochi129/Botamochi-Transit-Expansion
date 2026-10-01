package botamochi129.bte.mod.data;

import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;

public interface IRailMathExtra {
    /**
     * BTE の軌道（円弧）を有効にする。
     * <p>
     * ★ {@code posA}/{@code posB} はそれぞれ {@code vecA}/{@code vecB} に対応する
     * {@link Position} で、<b>呼び出し側はどちらを先に書いてもよい</b>。
     * 正規化（入れ替え）は実装側で行う。
     * <p>
     * なぜ {@link Position} が要るか:
     * MTR の {@code Rail} コンストラクタは
     * {@code reversePositions = position1.compareTo(position2) > 0} のとき
     * {@code RailMath} へ端点を<b>入れ替えて</b>渡す。すなわち {@code RailMath} の
     * {@code position1} は常に「辞書順で小さい方」であり、これは
     * {@code RailMath#getPosition(value, false)} の距離 0 の端点を決める。
     * <p>
     * ここを「ノード順」「{@code positionsToRail} の反復順」「{@code Rail} の保存順」
     * のいずれかで渡すと、およそ半数のレールで曲線が反転し、距離 0 が反対の端に
     * なって列車が逆走・瞬間移動・反対ノード停車を起こす。実装側で
     * {@code posA.compareTo(posB) > 0} を見て入れ替えることで、この不変条件を
     * 呼び出し側に漏れ込ませない。
     */
    void bte$enableBezier(Position posA, Vector vecA, double radA, Position posB, Vector vecB, double radB, double verticalRadius, Rail.Shape shape);
    boolean bte$isBezierEnabled();
    double bte$getStartRad();
    double bte$getEndRad();
}