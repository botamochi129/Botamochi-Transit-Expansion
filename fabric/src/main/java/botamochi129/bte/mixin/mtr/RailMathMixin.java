package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.ArcCurve;
import botamochi129.bte.mod.data.IRailMathExtra;
import botamochi129.bte.mod.data.MtrRailGeometry;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.RailMath;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

/**
 * BTE の自由角度レールを、MTR 本体の {@link RailMath} を<b>乗っ取らずに</b>表現させる。
 *
 * <p>従来は {@code getPosition} / {@code getLength} / {@code render} を cancel して
 * {@link ArcCurve} の点列を返していた。そのため MTR 本体が同じ {@code RailMath} を
 * 「フィールドから」読む箇所（3D レール模型の配置・レール面の刻み）と、BTE が
 * 「メソッド上書きから」返す軌道が<b>二重のジオメトリ</b>になり、模型の向き不一致・
 * レール間隔のずれ・走行中の微振動を起こしていた。
 *
 * <p>本実装では {@link ArcCurve} を MTR 表現へ変換した
 * {@link MtrRailGeometry} を {@code <init>} 後にフィールドへ注入するだけに留め、
 * 描画（{@code render}/{@code renderSegment}）・走行位置（{@code getPosition}）・
 * 長さ（{@code getLength}）は<b>すべて MTR 純正コードに任せる</b>。これにより
 * 模型・レール面・列車軌道が同一のジオメトリを参照する。
 *
 * <p><b>インスタンス単位の注入が特别注意すべき点</b><br>
 * フィールド注入は「その {@code RailMath} インスタンス」へのパッチであり、
 * {@code Rail} が複製されると<b>複製側の {@code RailMath} だけが MTR 純正の初期値へ戻る</b>。
 * {@code Rail.copy} は {@code position1/angle1/position2/angle2} から {@code RailMath} を
 * {@code new} する（final フィールドはコピーされない）ためである。
 * メソッドを cancel していた旧実装なら複製にも一律で効いたが、フィールド注入では
 * 複製を逐一救済しないと MTR 純正ジオメトリで描画される。
 * 実際の複製は 2 経路だけで、いずれも {@code <init>} 時に拾える：
 * <ul>
 *   <li>配置プレビュー：{@code RenderRails#renderRailStandard} が
 *       {@code ItemRailModifier#createRail} の戻り値を必ず {@code Rail.copy} してから
 *       レンダーリストへ積む。プレビューのノード対は未配置なので
 *       {@link StraightNodeBlockEntity#RAIL_MATH_DATA_MAP} には無い
 *       → {@link StraightNodeBlockEntity#getPreviewRailGeometry()} の 1 枠で拾う。</li>
 *   <li>Shift 保持中の配置済みレール：ノード対が {@code RAIL_MATH_DATA_MAP} に在るので
 *       通常の Map 経路で拾える。</li>
 * </ul>
 *
 * <p>高低プロファイルだけは MTR が {@code yStart}/{@code yEnd} の {@code long}
 * しか持たずサブブロックの {@code offY} を表現できないため、{@code getPositionY} を
 * 注入して {@link ArcCurve#getPositionY(double)} へ委譲する。{@code getPositionY} は
 * 描画（{@code renderSegment}）と走行（{@code getPosition}）の両方から呼ばれるので、
 * {@code offY} が描画・走行の両方に同時に反映される。
 *
 * <p>注入しない条件（標準レール・退化レールへ原理的に干渉しない）:
 * <ul>
 *   <li>BTE ノードが関与していない → {@link StraightNodeBlockEntity#RAIL_MATH_DATA_MAP} に
 *       データが無く {@link #bte$capturePositions} も描画側も何もしない</li>
 *   <li>{@link botamochi129.bte.mod.data.RailCalculator} が有効な Group を返さない
 *       → {@link ArcCurve#toMtrGeometry()} が {@code null} を返し注入しない</li>
 * </ul>
 */
@Mixin(value = RailMath.class, remap = false)
public abstract class RailMathMixin implements IRailMathExtra {

    private static final Rail.Shape[] SHAPES = Rail.Shape.values();

    // ── MTR RailMath の final フィールド（注入対象）────────────────
    @Shadow @Final @Mutable private double h1;
    @Shadow @Final @Mutable private double k1;
    @Shadow @Final @Mutable private double r1;
    @Shadow @Final @Mutable private double tStart1;
    @Shadow @Final @Mutable private double tEnd1;
    @Shadow @Final @Mutable private boolean reverseT1;
    @Shadow @Final @Mutable private boolean isStraight1;
    @Shadow @Final @Mutable private double h2;
    @Shadow @Final @Mutable private double k2;
    @Shadow @Final @Mutable private double r2;
    @Shadow @Final @Mutable private double tStart2;
    @Shadow @Final @Mutable private double tEnd2;
    @Shadow @Final @Mutable private boolean reverseT2;
    @Shadow @Final @Mutable private boolean isStraight2;
    @Shadow @Final @Mutable private long yStart;
    @Shadow @Final @Mutable private long yEnd;
    @Shadow @Final @Mutable private long minX;
    @Shadow @Final @Mutable private long minY;
    @Shadow @Final @Mutable private long minZ;
    @Shadow @Final @Mutable private long maxX;
    @Shadow @Final @Mutable private long maxY;
    @Shadow @Final @Mutable private long maxZ;

    /**
     * MTR 純正の {@code getPositionXZ}（{@code invokestatic}）。
     *
     * <p>診断用にshadow する。BTE が注入した final フィールドを MTR 本家の式で
     * 評価し直すことで、模型の {@code yaw} が実際にどう並ぶかを
     * ゲーム外（＝ログだけ）で再現するため。
     */
    @Shadow
    private static Vector getPositionXZ(double h, double k, double r, double t, double offset, boolean isStraight) {
        throw new AssertionError("shadow");
    }

    @Unique private ArcCurve bte$arcCurve = null;
    @Unique private boolean bte$isBezierEnabled = false;
    @Unique private boolean bte$hasSavedInput = false;
    @Unique private double bte$startRad = 0;
    @Unique private double bte$endRad = 0;
    @Unique private Vector bte$startPos = null;
    @Unique private Vector bte$endPos = null;
    @Unique private double bte$savedVerticalRadius = 0;
    @Unique private Rail.Shape bte$savedShape = Rail.Shape.QUADRATIC;

    @Inject(
            method = "<init>(Lorg/mtr/core/data/Position;Lorg/mtr/core/tool/Angle;Lorg/mtr/core/data/Position;Lorg/mtr/core/tool/Angle;Lorg/mtr/core/data/Rail$Shape;D)V",
            at = @At("RETURN")
    )
    private void bte$capturePositions(
            Position position1, Angle angle1, Position position2, Angle angle2, Rail.Shape shape, double verticalRadius, CallbackInfo ci
    ) {
        // 0) ★ MTR ネイティブ解決済みのレールには何も注入しない。
        //      RailGetAnglesMixin が Rail.getAngles の引数を差し替えた結果、MTR が
        //      RailMath の final（h1/k1/r1/.../minX..maxZ）を正しく焼き込んでいる。
        //      ここで注入するとその値を上書きし直し、MTR の不変条件
        //      「final は構築時のみ。変えたければ Rail を作り直す」を破って
        //      導出キャッシュ（RailWrapper の AABB、closeTo、経路探索）が中途半端に古くなる。
        //
        //      ネイティブ扱いは「記録した軸が今も 22.5 度グリッド上で offset が 0」のときだけ
        //      成立する。ノード軸をグリッド外へ回した瞬間に false へ落ち、この注入経路へ入る。
        if (StraightNodeBlockEntity.isNativeRail(position1, position2)) {
            bte$diagPath("native", position1, position2);
            return;
        }

        // 1) 配置済みレール。BTE ノードが関与しているものだけが publish されている。
        //    無ければ何もしない = MTR 純正の計算に委譲する（標準レールの軌道を守る）。
        final double[] existingData = StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.get(StraightNodeBlockEntity.railMathKey(position1, position2));
        if (existingData != null && existingData.length >= 14) {
            // キーの衝突で別レールの記述子を誤って注入しないよう、
            // プレビューパスと同様に端点照合を挟む。不一致なら MTR 純正に委譲する。
            if (!bte$matchesEndpointsXZ(existingData, position1, position2)) {
                return;
            }
            bte$diagPath("map", position1, position2);
            bte$injectDescriptor(existingData, position1, position2, shape, verticalRadius);
            return;
        }

        // 2) 配置プレビュー。MTR は createRail の戻り値を Rail.copy で複製してから描くため、
        //    複製側の RailMath だけが MTR 純正のジオメトリで初期化される。
        //    複製は描画パス内のプレビュー分岐（getRailWithLastStyles → Rail.copy）だけで起きるため、
        //    1 枠の一時値で拾える。端点対が一致しないRailMath には適用しない。
        final double[] preview = StraightNodeBlockEntity.getPreviewRailGeometry();
        if (preview == null) {
            bte$diagPath("miss:none", position1, position2);
            return;
        }
        if (!bte$matchesEndpoints(preview, position1, position2)) {
            bte$diagPath("miss:endpoint", position1, position2);
            return;
        }
        bte$diagPath("preview", position1, position2);
        bte$injectDescriptor(preview, position1, position2, shape, verticalRadius);
    }

    // ── 診断（TEMPORARY: 原因確定後に削除する）─────────────────────
    private static final String DIAG_TAG = "[BTE-DIAG]";
    private static int bte$diagCount = 0;
    private static final int DIAG_MAX = 6;

    /**
     * {@code RailMath.<init>} がどの経路で BTE データへ到達したかを記録する。
     * {@code Rail.copy} の複製を 1 枠で拾う実装のため、
     * プレビュー／配置済みのどちらが実際に拾われているかを実機ログで切り分ける。
     */
    @Unique
    private static void bte$diagPath(String path, Position p1, Position p2) {
        if (bte$diagCount >= DIAG_MAX) return;
        bte$diagCount++;
        System.out.println(DIAG_TAG + " init " + path
                + " (" + p1.getX() + "," + p1.getY() + "," + p1.getZ() + ")"
                + "->(" + p2.getX() + "," + p2.getY() + "," + p2.getZ() + ")");
    }

    /**
     * ジオメトリ記述子をこの {@code RailMath} へ注入する。
     *
     * <p>{@link StraightNodeBlockEntity#RAIL_MATH_DATA_MAP} の値と、プレビュー枠の値が
     * 同じレイアウト（先頭 14 要素）を持つことを前提とする。
     */
    @Unique
    private void bte$injectDescriptor(double[] data, Position position1, Position position2, Rail.Shape shape, double verticalRadius) {
        final double vRad = data[8];
        final int shapeOrdinal = (int) data[9];
        final Rail.Shape s = (shapeOrdinal >= 0 && shapeOrdinal < SHAPES.length) ? SHAPES[shapeOrdinal] : Rail.Shape.QUADRATIC;

        // data[0..5] / data[6..7] は「publish した側」の端点順に書かれており、
        // このコンストラクタ引数 position1/position2 は MTR が正規化済み。
        // data[10..13] には端点の X と Z しか無いので、Y は推測せず XZ の一致で実体を選ぶ。
        final long nodeAx = (long) data[10];
        final long nodeAz = (long) data[11];
        final boolean nodeAIsPosition1 = (position1.getX() == nodeAx && position1.getZ() == nodeAz);
        final Position nodeA = nodeAIsPosition1 ? position1 : position2;
        final Position nodeB = nodeAIsPosition1 ? position2 : position1;

        // 順序の正規化は bte$enableBezier 側が担保するのでここではそのまま渡してよい。
        this.bte$enableBezier(
                nodeA,
                new Vector(data[0], data[1], data[2]),
                data[6],
                nodeB,
                new Vector(data[3], data[4], data[5]),
                data[7],
                vRad,
                s
        );

        // ★ data を書き戻してはいけない。
        //   data は RAIL_MATH_DATA_MAP から複製せず取り出した共有配列で、
        //   他の読み手（ここの vRad/shapeOrdinal を読む後続の構築、
        //   RenderRailsMixin#bte$matchesCurveData の dataChanged 判定）も同じ要素を見る。
        //   1 つの RailMath が自分の verticalRadius / shape を書き込むと、
        //   次の RailMath がその汚染値で計算し、
        //   toMtrGeometry() が null（＝MTR 純正の直線）に落ちる。
        //   作りたければ data.clone() に対して行うこと。
    }

    /**
     * プレビュー枠の記述子が、この {@code RailMath} の端点に対応するか。
     *
     * <p>記述子は {@code [10..13]} に端点 A/B の x,z を、{@code [14..15]} に y を持つ。
     * 順不同を許しつつ<b>3D で厳密に</b>照合するので、預け方（1 枠）を誤って
     * 他の端点対の {@code RailMath} へ適用しない。
     */
    @Unique
    private static boolean bte$matchesEndpoints(double[] data, Position position1, Position position2) {
        if (data.length < 16) return false;

        final long ax = (long) data[10], ay = (long) data[14], az = (long) data[11];
        final long bx = (long) data[12], by = (long) data[15], bz = (long) data[13];

        if (position1.getX() == ax && position1.getY() == ay && position1.getZ() == az) {
            return position2.getX() == bx && position2.getY() == by && position2.getZ() == bz;
        }
        if (position1.getX() == bx && position1.getY() == by && position1.getZ() == bz) {
            return position2.getX() == ax && position2.getY() == ay && position2.getZ() == az;
        }
        return false;
    }

    /**
     * 配置済みの {@link StraightNodeBlockEntity#RAIL_MATH_DATA_MAP} 記述子が、
     * この {@code RailMath} の端点に対応するか。
     *
     * <p>プレビューの {@link #bte$matchesEndpoints} と違い、配置済みのペイロードは
     * {@code double[14]} で、端点の Y を格納する {@code [14..15]} を持たない。よって XZ のみで照合する。
     * （Y を payload に載せる改修は別件。）
     *
     * <p>順序に依存しない照合だが、端点組そのものが一致することを要求する。
     * {@link StraightNodeBlockEntity#railMathKey} のキーがバウンディングボックスだったときは
     * 別レールと衝突し、誤った端点組の記述子が注入されていたため、
     * プレビューパスと同じく多層防御としてこの照合を入れる。
     * 不一致なら何もしない（= MTR 純正）ので、誤った曲線より直線に落ちる。
     */
    @Unique
    private static boolean bte$matchesEndpointsXZ(double[] data, Position position1, Position position2) {
        if (data.length < 14) return false;

        final long ax = (long) data[10], az = (long) data[11];
        final long bx = (long) data[12], bz = (long) data[13];

        if (position1.getX() == ax && position1.getZ() == az) {
            return position2.getX() == bx && position2.getZ() == bz;
        }
        if (position1.getX() == bx && position1.getZ() == bz) {
            return position2.getX() == ax && position2.getZ() == az;
        }
        return false;
    }

    @Unique
    private ArcCurve bte$getActiveCurve() {
        if (bte$isBezierEnabled && bte$arcCurve != null) {
            return bte$arcCurve;
        }
        return null;
    }

    @Override
    public void bte$enableBezier(Position posA, Vector vecA, double radA, Position posB, Vector vecB, double radB, double verticalRadius, Rail.Shape shape) {
        // MTR の Rail は position1.compareTo(position2) > 0 のとき RailMath 端点を入れ替える。
        // したがって RailMath.position1 は常に辞書順で小さい方。
        // 距離 0 を正規順で決めるため、ここで揃える。
        final boolean swap = posA.compareTo(posB) > 0;
        final Vector startPos = swap ? vecB : vecA;
        final double startRad = swap ? radB : radA;
        final Vector endPos   = swap ? vecA : vecB;
        final double endRad   = swap ? radA : radB;

        // 早期 return: 正規化後の 8 入力が前回と完全一致なら再構築・再注入しない。
        // 描画パスは毎フレーム全レールへ本メソッドを呼ぶため、ガードが無いと
        // 同一入力に対して毎フレーム new ArcCurve + フィールド注入が走る。
        if (bte$hasSavedInput
                && startRad == bte$startRad && endRad == bte$endRad
                && bte$sameVector(startPos, bte$startPos)
                && bte$sameVector(endPos, bte$endPos)
                && verticalRadius == bte$savedVerticalRadius
                && shape == bte$savedShape) {
            return;
        }

        this.bte$startPos = startPos;
        this.bte$startRad = startRad;
        this.bte$endPos   = endPos;
        this.bte$endRad   = endRad;
        this.bte$savedVerticalRadius = verticalRadius;
        this.bte$savedShape = shape;
        this.bte$hasSavedInput = true;

        final ArcCurve curve = new ArcCurve(startPos, startRad, endPos, endRad, verticalRadius, shape);
        final MtrRailGeometry geometry = curve.toMtrGeometry();

        if (geometry == null) {
            // RailCalculator が有効な Group を返さない（平行・計算不能）。MTR 純正のままにする。
            this.bte$arcCurve = null;
            this.bte$isBezierEnabled = false;
            bte$diag(-1);
            return;
        }

        this.bte$arcCurve = curve;
        this.bte$isBezierEnabled = true;
        bte$writeFields(geometry);
        bte$recomputeBoundingBox();
        bte$diag(0);
    }

    // ── 診断（TEMPORARY: 原因確定後に削除する）─────────────────────
    private static int bte$diagFieldCount = 0;
    private static final int DIAG_FIELD_MAX = 4;

    @Unique
    private void bte$diag(int stage) {
        if (bte$diagFieldCount >= DIAG_FIELD_MAX) return;
        bte$diagFieldCount++;
        final StringBuilder sb = new StringBuilder();
        sb.append(DIAG_TAG).append(" fields#").append(bte$diagFieldCount).append(" stage=").append(stage);
        sb.append(" n1=").append(fmt(tStart1)).append(' ').append(fmt(tEnd1));
        sb.append(" rev1=").append(reverseT1).append(" str1=").append(isStraight1);
        sb.append(" h1=").append(fmt(h1)).append(" k1=").append(fmt(k1)).append(" r1=").append(fmt(r1));
        // ★ 修正: 旧実装は isStraight2 の TRUE 側だけoke しいセクションを出力し、
        //   FALSE（＝第2セクションは円弧）側に "n2=OFF" と出ていた。実機ログの
        //   "str2=true かつ n2=OFF" という矛盾はこの反転が原因。
        if (isStraight2) {
            sb.append(" n2=").append(fmt(tStart2)).append(' ').append(fmt(tEnd2));
            sb.append(" rev2=").append(reverseT2).append(" str2=").append(isStraight2);
            sb.append(" h2=").append(fmt(h2)).append(" k2=").append(fmt(k2)).append(" r2=").append(fmt(r2));
        } else {
            sb.append(" n2=none");
        }
        if (bte$arcCurve == null) {
            sb.append(" arc=null");
            System.out.println(sb);
            return;
        }
        sb.append(" arc.len=").append(fmt(bte$arcCurve.getLength()));

        // ★ 模型パスと同一の「renderSegment の不等間隔サンプリング」を MTR 純関数で再現し、
        //   実際の yaw 列を出す。描画側と 1 サンプルもズレないことを意図している。
        //   step = (len < 0.5 || interval <= 0) ? 0.5 : interval * Math.round(len) / len
        bte$diagSeries(sb, 1);
        if (!isStraight2) bte$diagSeries(sb, 2);
        System.out.println(sb);
    }

    @Unique
    private void bte$diagSeries(StringBuilder sb, int sec) {
        final double h = sec == 1 ? h1 : h2;
        final double k = sec == 1 ? k1 : k2;
        final double r = sec == 1 ? r1 : r2;
        final double tStart = sec == 1 ? tStart1 : tStart2;
        final double tEnd = sec == 1 ? tEnd1 : tEnd2;
        final boolean reverse = sec == 1 ? reverseT1 : reverseT2;
        final boolean straight = sec == 1 ? isStraight1 : isStraight2;

        final double len = Math.abs(tEnd - tStart);
        final double step = (len < 0.5) ? 0.5 : 0.5 * Math.round(len) / len;
        final double dir = reverse ? -1 : 1;
        final int maxSamples = 16;

        sb.append(String.format(Locale.ROOT, " | s%d len=%.3f step=%.3f rev=%b straight=%b",
                sec, len, step, reverse, straight));

        Vector prev = null;
        int emitted = 0;
        for (int i = 0; i < maxSamples; i++) {
            final double t = i * step;
            if (t >= len + step - 0.1) break;
            final Vector p = getPositionXZ(h, k, r, tStart + dir * t, 0, straight);
            if (prev == null) {
                sb.append(String.format(Locale.ROOT, " p0=(%.3f,%.3f)", p.x(), p.z()));
            } else {
                sb.append(String.format(Locale.ROOT, " y%d=%.1f", emitted,
                        Math.toDegrees(Math.atan2(p.z() - prev.z(), p.x() - prev.x()))));
                emitted++;
            }
            prev = p;
        }
        if (prev != null) sb.append(String.format(Locale.ROOT, " pN=(%.3f,%.3f)", prev.x(), prev.z()));
    }

    @Unique
    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    /** 変換レイヤーが作った MTR 表現を final フィールドへ書き込む。 */
    @Unique
    private void bte$writeFields(MtrRailGeometry g) {
        this.h1 = g.h1; this.k1 = g.k1; this.r1 = g.r1; this.tStart1 = g.tStart1; this.tEnd1 = g.tEnd1;
        this.reverseT1 = g.reverseT1; this.isStraight1 = g.isStraight1;
        this.h2 = g.h2; this.k2 = g.k2; this.r2 = g.r2; this.tStart2 = g.tStart2; this.tEnd2 = g.tEnd2;
        this.reverseT2 = g.reverseT2; this.isStraight2 = g.isStraight2;
        // yStart/yEnd は long なので offY は表現できない。getPositionY を上書きして
        // ArcCurve の double プロファイルへ委譲するため、ここでは概算（ブロック丸め）だけ入れる。
        this.yStart = Math.round(g.yStart);
        this.yEnd = Math.round(g.yEnd);
    }

    /**
     * MTR 本体 {@code <init>} と同じ手順でバウンディングボックスを再計算する。
     *
     * <p>本家は構築時に {@code render(callback, 0.1, 0, 0)} を呼び、得た 4 隅 XZ と 2 つの Y を
     * min/max 集計したあと floor/ceil（空なら 0）で {@code minX..maxZ} を確定する。
     * 注入後は MTR のその集計が BTE ジオメトリを反映しないため、同じ手順をここで踏襲する。
     * この bbox は {@code Rail#closeTo(Position, double)} の近接判定に使われる。
     */
    @Unique
    private void bte$recomputeBoundingBox() {
        final double[] acc = new double[]{
                Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE
        };
        ((RailMath) (Object) this).render((x1, z1, x2, z2, x3, z3, x4, z4, y1, y2) -> {
            acc[0] = Math.min(acc[0], Math.min(Math.min(x1, x2), Math.min(x3, x4)));
            acc[1] = Math.min(acc[1], Math.min(y1, y2));
            acc[2] = Math.min(acc[2], Math.min(Math.min(z1, z2), Math.min(z3, z4)));
            acc[3] = Math.max(acc[3], Math.max(Math.max(x1, x2), Math.max(x3, x4)));
            acc[4] = Math.max(acc[4], Math.max(y1, y2));
            acc[5] = Math.max(acc[5], Math.max(Math.max(z1, z2), Math.max(z3, z4)));
        }, 0.1, 0.0F, 0.0F);

        this.minX = acc[0] <= acc[3] ? (long) Math.floor(acc[0]) : 0L;
        this.minY = acc[1] <= acc[4] ? (long) Math.floor(acc[1]) : 0L;
        this.minZ = acc[2] <= acc[5] ? (long) Math.floor(acc[2]) : 0L;
        this.maxX = acc[3] >= acc[0] ? (long) Math.ceil(acc[3]) : 0L;
        this.maxY = acc[4] >= acc[1] ? (long) Math.ceil(acc[4]) : 0L;
        this.maxZ = acc[5] >= acc[2] ? (long) Math.ceil(acc[5]) : 0L;
    }

    /**
     * 高低プロファイルだけは MTR 純正へ委譲できない（{@code yStart}/{@code yEnd} が long で
     * offY を表現できないため）ので、{@link ArcCurve} へ委譲する。
     * {@code getPositionY} は {@code renderSegment}（描画）と {@code getPosition}（走行）の
     * 両方から呼ばれるので、offY が両方へ同時に反映される。
     */
    @Inject(method = "getPositionY(D)D", at = @At("HEAD"), cancellable = true)
    private void bte$overridePositionY(double value, CallbackInfoReturnable<Double> cir) {
        final ArcCurve curve = bte$getActiveCurve();
        if (curve != null) {
            cir.setReturnValue(curve.getPositionY(value));
        }
    }

    /** 位置ベクトルの全成分が完全一致するか（描画経路は毎フレーム同じ値を送るため厳密比較で足りる）。 */
    @Unique
    private boolean bte$sameVector(Vector a, Vector b) {
        return a != null && b != null
                && a.x() == b.x() && a.y() == b.y() && a.z() == b.z();
    }

    @Override
    public boolean bte$isBezierEnabled() { return bte$isBezierEnabled; }
    @Override
    public double bte$getStartRad() { return bte$startRad; }
    @Override
    public double bte$getEndRad() { return bte$endRad; }
}
