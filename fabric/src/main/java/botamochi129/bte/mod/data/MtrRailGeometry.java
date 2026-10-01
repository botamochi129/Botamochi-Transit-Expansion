package botamochi129.bte.mod.data;

/**
 * {@link RailCalculator} の幾何（2 区間）を MTR 本体 {@code RailMath} の
 * フィールド表現へ写す「変換レイヤー」。
 *
 * <p><b>責務分離</b>
 * <ul>
 *   <li>{@link RailCalculator} は幾何学的に正しい {@link RailCalculator.Section} を出す。
 *       このクラスは {@code RailCalculator} を一切変更しない。</li>
 *   <li>このクラスは Section を MTR の
 *       {@code (h, k, r, tStart, tEnd, reverseT, isStraight)} 表現へ写すだけ。
 *       将来 {@code RailCalculator} の絶対 tolerance（{@link RailCalculator#PRECISION}）を
 *       直しても、表現形式のロジックと干渉しない。</li>
 * </ul>
 *
 * <p><b>直線規約（逆アセンブルで確認した結論）</b><br>
 * MTR {@code RailMath.getPositionXZ} の直線分岐は
 * {@code x = h*t + k*((|h|>=0.5 && |k|>=0.5 ? 0 : r) + offset) + 0.5},
 * {@code z = k*t + h*(r - offset) + 0.5} で、ここで {@code (h, k)} は<b>単位方向ベクトル</b>。
 * よって線上の移動量は {@code |Δ(x,z)| = |Δt|}（offset は定数シフトのみ）となり、
 * 直線の {@code |tEnd - tStart|} は<b>物理長と一致する</b>。
 * {@link RailCalculator.Segment#toSection()} は {@code h,k = delta/len} で
 * {@code div = 2h^2-1} とすると {@code |Δt| = len*(h^2-k^2)/(2h^2-1) = len}（{@code h^2+k^2=1}）
 * となるため、直線もそのまま注入できる。<b>直線専用の再正規化は不要</b>。
 * （{@link ArcCurve} の javadoc に「直線は実長の 1/2 になる」とあるが、これは
 * 本家 {@code getPositionXZ} の消費規約と矛盾する誤記であり、ここでは採用しない。）
 *
 * <p><b>空区間の扱い</b><br>
 * {@link RailCalculator.Group} は片側に既定 {@link RailCalculator.Section}（全 0）を返し、
 * 退化した場合は {@link Double#NaN} の Section を返す。有効側の終端点を指す
 * <b>ゼロ長区間</b>へ置き換えるのは、その {@code NaN} を MTR 側へ漏らさないためである。
 * {@code RailMath#getLength} は {@code |tEnd1-tStart1| + |tEnd2-tStart2|} なので、
 * 片側でも {@code NaN} があれば合計が {@code NaN} になり、{@code getHorizontalRadii} や
 * {@code renderSegment} の反復境界まで汚染される。ゼロ長区間なら寄与は 0 になる。
 *
 * <p><b>（撤回した誤記）</b>「絶対長 0 の区間があると {@code renderSegment} が
 * {@code getPositionXZ(0,0,0,0,...)} を原点付近で評価し、レール終端から原点へ迷走線を引く」
 * という理由だった記載は本家のバイトコードで<b>否定した</b>。{@code renderSegment} は
 * 1 回目の反復で previous 点が {@code null} のため callback を呼ばない。よってゼロ長区間は
 * 何も描かない。置換の実効的な効果は上記の {@code NaN} 除去である。
 * なお {@code getHorizontalRadii} の参照先は {@code RailMath} と {@code RenderRails}
 * （統計テキスト表示）だけで、3D 模型の頂点列は {@code render}/{@code renderSegment} の
 * callback 座標から生成される。つまり第 2 区間の置換は模型形状に影響しない。
 */
public final class MtrRailGeometry {

    // 第 1 区間
    public final double h1, k1, r1, tStart1, tEnd1;
    public final boolean reverseT1, isStraight1;
    // 第 2 区間
    public final double h2, k2, r2, tStart2, tEnd2;
    public final boolean reverseT2, isStraight2;
    // 高低プロファイル用のサブブロック対応端点 Y（MTR 側は long なので別途 getPositionY で処理）
    public final double yStart, yEnd;

    private MtrRailGeometry(
            double h1, double k1, double r1, double tStart1, double tEnd1, boolean reverseT1, boolean isStraight1,
            double h2, double k2, double r2, double tStart2, double tEnd2, boolean reverseT2, boolean isStraight2,
            double yStart, double yEnd
    ) {
        this.h1 = h1; this.k1 = k1; this.r1 = r1; this.tStart1 = tStart1; this.tEnd1 = tEnd1;
        this.reverseT1 = reverseT1; this.isStraight1 = isStraight1;
        this.h2 = h2; this.k2 = k2; this.r2 = r2; this.tStart2 = tStart2; this.tEnd2 = tEnd2;
        this.reverseT2 = reverseT2; this.isStraight2 = isStraight2;
        this.yStart = yStart; this.yEnd = yEnd;
    }

    /**
     * {@link RailCalculator} の生 Section を MTR 表現へ写す。
     *
     * @return 変換結果。第 1 区間が使用不能（{@code null} / 非有限 / 退化直線）なら {@code null}。
     *         その場合呼び出し側は注入せず MTR 純正のままにする。
     */
    public static MtrRailGeometry from(RailCalculator.Section s1, RailCalculator.Section s2, double yStart, double yEnd) {
        if (!bte$isUsable(s1)) {
            // 第 1 区間が NaN（Segment 退化時の Double.NaN セクション）や空。
            // 注入すると NaN が全計算へ伝播するので、注入せず MTR 純正へ委ねる。
            return null;
        }

        final RailCalculator.Section second = bte$isUsable(s2)
                ? s2
                // 有効側の終端点を指すゼロ長区間（renderSegment が t=tStart2=tEnd1 を 1 回だけ評価する）
                : new RailCalculator.Section(s1.h, s1.k, s1.r, s1.tEnd, s1.tEnd, s1.reverseT, s1.isStraight);

        return new MtrRailGeometry(
                s1.h, s1.k, s1.r, s1.tStart, s1.tEnd, s1.reverseT, s1.isStraight,
                second.h, second.k, second.r, second.tStart, second.tEnd, second.reverseT, second.isStraight,
                yStart, yEnd
        );
    }

    /**
     * MTR へ注入してよい Section か。
     *
     * <p>{@link RailCalculator.Section#isValid()} は非有限値を弾く。加えて
     * {@code h==k==0} の直線は {@link RailCalculator.Section} の既定値（空区間）であり
     * 実在の直線ではない（実長 &lt; {@link RailCalculator#PRECISION} の退化直線は NaN を返す）。
     */
    private static boolean bte$isUsable(RailCalculator.Section s) {
        return s != null && s.isValid() && !(s.isStraight && s.h == 0 && s.k == 0);
    }
}
