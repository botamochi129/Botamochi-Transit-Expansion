package botamochi129.bte.mod.data;

import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;

/**
 * MTR 本家 {@code org.mtr.core.data.RailMath} の幾何（2 区間 = 円弧/直線 の接続）を
 * BTE 向けに複製したもの。
 *
 * <p>本家との対応:
 * <ul>
 *   <li>{@code getPositionXZ(h, k, r, t, offset, isStraight)} = {@code (h + (r + offset) * cos(t / r), k + (r + offset) * sin(t / r))}</li>
 *   <li>{@code getPositionY(value)} = TWO_RADII / QUADRATIC の高低プロファイル</li>
 * </ul>
 *
 * <p><b>h / k の意味は区間種別で異なる</b>这一点が本実装の要点:
 * <ul>
 *   <li>円弧: {@code h, k} は円の中心座標</li>
 *   <li>直線: {@code h, k} は進行方向の単位ベクトル</li>
 * </ul>
 * {@link #arc1} / {@link #arc2} で区別して使い分ける。
 *
 * <p>また本家 {@code RailMath} の直線分支は {@code t} のパラメータ化が
 * {@code tStart = (h*x - k*z) / (2*cos^2)} になっており、
 * {@code |tEnd - tStart|} が<b>実長と一致しない</b>（X 軸平行だと実長の 1/2）。
 * 一方 {@code getPositionY} は「rails の実長」で高低プロファイルを評価するため、
 * ここでは直線区間の長さを必ず<b>物理座標距離</b>から求める。
 *
 * <p>最適化の要点:
 * <ul>
 *   <li>角度はコンストラクタで {@code thetaStart} と {@code 1/r} まで畳み込む</li>
 *   <li>浮動小数点ドリフト補正もコンストラクタで 1 回だけ計算する</li>
 *   <li>{@code double[2]} の中間配列を全廃した</li>
 *   <li>高低プロファイルは本家 {@code getPositionY} の実装（以前は {@code startY} を返すスタブだった）</li>
 * </ul>
 */
public class ArcCurve {

    private static final double EPSILON = 1e-5;

    // ── 第 1 区間 ───────────────────────────────────────────────
    /** 円弧なら中心座標、直線なら進行方向の単位ベクトル。 */
    private final double h1, k1;
    private final double r1, invR1, thetaStart1, corrX1, corrZ1;
    private final double baseX1, baseZ1;
    /** 物理的な弧長（円弧）/ 実長（直線）。 */
    private final double len1;
    private final boolean reverse1, arc1;

    // ── 第 2 区間 ───────────────────────────────────────────────
    private final double h2, k2;
    private final double r2, invR2, thetaStart2, corrX2, corrZ2;
    private final double baseX2, baseZ2;
    private final double len2;
    private final boolean reverse2, arc2;

    private final double totalLength;
    private final double yStart, yEnd;
    private final double verticalRadius;
    private final double curveLength, curveHeight, ySign, height;
    private final Rail.Shape shape;
    private final boolean flat;

    // MTR RailMath への注入用に、RailCalculator の生 Section を保持する。
    private final RailCalculator.Section section1, section2;
    private final boolean validGeometry;

    public ArcCurve(Vector posStart, double startAngleRad, Vector posEnd, double endAngleRad, double verticalRadius, Rail.Shape shape) {
        this.yStart = posStart.y();
        this.yEnd = posEnd.y();
        this.verticalRadius = verticalRadius;
        this.shape = shape != null ? shape : Rail.Shape.QUADRATIC;
        this.flat = Math.abs(yStart - yEnd) < EPSILON;
        this.height = Math.abs(yEnd - yStart);

        final double sx = posStart.x();
        final double sz = posStart.z();
        final double ex = posEnd.x();
        final double ez = posEnd.z();

        RailCalculator.Group group = RailCalculator.calculate(sx, sz, ex, ez, startAngleRad, endAngleRad);

        if (group == null) {
            this.arc1 = false;
            this.arc2 = false;
            this.h1 = 1; this.k1 = 0;
            this.r1 = 0; this.invR1 = 0; this.thetaStart1 = 0; this.corrX1 = 0; this.corrZ1 = 0;
            this.baseX1 = sx; this.baseZ1 = sz;
            this.len1 = 0;
            this.reverse1 = false;
            this.h2 = 1; this.k2 = 0;
            this.r2 = 0; this.invR2 = 0; this.thetaStart2 = 0; this.corrX2 = 0; this.corrZ2 = 0;
            this.baseX2 = ex; this.baseZ2 = ez;
            this.len2 = 0;
            this.reverse2 = false;
            this.totalLength = 0;
            this.validGeometry = false;
            this.section1 = null;
            this.section2 = null;
        } else {
            RailCalculator.Section s1 = group.first;
            RailCalculator.Section s2 = group.second;

            this.arc1 = s1.isValid() && !s1.isStraight && s1.r > EPSILON;
            this.arc2 = s2.isValid() && !s2.isStraight && s2.r > EPSILON;
            this.reverse1 = s1.reverseT;
            this.reverse2 = s2.reverseT;
            this.baseX1 = sx;
            this.baseZ1 = sz;

            // ================================================================
            // 接合点 F（第 1 区間の終端 = 第 2 区間の始端）を先に 1 回だけ確定させる。
            //
            // RailCalculator.calculate が返す Group は 4 通りあり、F の求め方が各异になる:
            //   直線のみ        : F = posEnd
            //   直線 + 円弧     : F = 円弧の始端（第 2 区間のパラメータから厳密に復元）
            //   円弧 + 直線     : F = 円弧 1 の終端
            //   円弧 + 円弧     : F = 円弧 1 の終端（= 円弧 2 の始端）
            //
            // 「第 1 区間が直線ならレール全体を覆う」と仮定すると、円弧の終端を F と
            // 取り違えて，接合点が posEnd に潰れ，続く len2 が 0 になって
            // 円弧区間ごと消える（= カーブにならず、角度を変えても直線のまま）。
            // よって必ず円弧のパラメータから復元する。
            // ================================================================
            final double fx;
            final double fz;
            if (arc1) {
                // 円弧のパラメータは t = r * theta なので t / r がそのまま偏角。
                final double thetaEnd1 = s1.tEnd * (1.0 / s1.r);
                fx = s1.h + s1.r * Math.cos(thetaEnd1);
                fz = s1.k + s1.r * Math.sin(thetaEnd1);
            } else if (arc2) {
                final double thetaStart2 = s2.tStart * (1.0 / s2.r);
                fx = s2.h + s2.r * Math.cos(thetaStart2);
                fz = s2.k + s2.r * Math.sin(thetaStart2);
            } else {
                fx = ex;
                fz = ez;
            }
            this.baseX2 = fx;
            this.baseZ2 = fz;

            // ── 第 1 区間 ──────────────────────────────────────────────
            if (arc1) {
                this.h1 = s1.h;
                this.k1 = s1.k;
                this.r1 = s1.r;
                this.invR1 = 1.0 / s1.r;
                this.thetaStart1 = s1.tStart * this.invR1;
                // 円弧は |tEnd - tStart| がそのまま弧長になる（t = r * theta のため）
                this.len1 = Math.abs(s1.tEnd - s1.tStart);
                // 開始点の実座標に厳密に合わせるための補正（コンストラクタで 1 回だけ）
                this.corrX1 = sx - (s1.h + s1.r * Math.cos(thetaStart1));
                this.corrZ1 = sz - (s1.k + s1.r * Math.sin(thetaStart1));
            } else {
                // 直線: h,k は進行方向。RailCalculator の既定 Section は (0,0) なので
                // 長さ 0 の「空区間」と区別する。
                double dirX = s1.h;
                double dirZ = s1.k;
                double dirLen = Math.sqrt(dirX * dirX + dirZ * dirZ);
                if (dirLen < EPSILON) {
                    // 方向が求まらない場合は接合点へ向かうベクトルへ退避
                    dirX = fx - sx;
                    dirZ = fz - sz;
                    dirLen = Math.sqrt(dirX * dirX + dirZ * dirZ);
                    if (dirLen < EPSILON) {
                        dirX = 1;
                        dirZ = 0;
                        dirLen = 1;
                    }
                }
                this.h1 = dirX / dirLen;
                this.k1 = dirZ / dirLen;
                this.r1 = 0;
                this.invR1 = 0;
                this.thetaStart1 = 0;
                this.corrX1 = 0;
                this.corrZ1 = 0;
                // ★ 直線長は必ず物理座標距離を取る。
                //   Section#getLength() = |tEnd - tStart| は直線では実長と一致しない
                //   （MTR 本家と同じパラメータ化で、軸並行だと実長の 1/2 になる）。
                this.len1 = Math.sqrt((fx - sx) * (fx - sx) + (fz - sz) * (fz - sz));
            }

            // ── 第 2 区間 ──────────────────────────────────────────────
            if (arc2) {
                this.h2 = s2.h;
                this.k2 = s2.k;
                this.r2 = s2.r;
                this.invR2 = 1.0 / s2.r;
                this.thetaStart2 = s2.tStart * this.invR2;
                this.corrX2 = fx - (s2.h + s2.r * Math.cos(thetaStart2));
                this.corrZ2 = fz - (s2.k + s2.r * Math.sin(thetaStart2));
                this.len2 = Math.abs(s2.tEnd - s2.tStart);
            } else {
                double dirX = s2.h;
                double dirZ = s2.k;
                double dirLen = Math.sqrt(dirX * dirX + dirZ * dirZ);
                if (dirLen < EPSILON) {
                    // 長さ 0 の空区間（RailCalculator.Group の既定値）
                    this.h2 = 1;
                    this.k2 = 0;
                    this.len2 = 0;
                } else {
                    this.h2 = dirX / dirLen;
                    this.k2 = dirZ / dirLen;
                    // ★ ここも物理距離。getLength() は使わない。
                    this.len2 = Math.sqrt((ex - fx) * (ex - fx) + (ez - fz) * (ez - fz));
                }
                this.r2 = 0;
                this.invR2 = 0;
                this.thetaStart2 = 0;
                this.corrX2 = 0;
                this.corrZ2 = 0;
            }

            this.totalLength = this.len1 + this.len2;
            this.validGeometry = true;
            this.section1 = s1;
            this.section2 = s2;
        }

        // ── 高低プロファイル（本家 getPositionY が getVTheta で先出し計算する量）──
        if (this.flat || this.totalLength <= EPSILON || this.verticalRadius <= 0) {
            this.curveLength = 0;
            this.curveHeight = 0;
        } else {
            double innerSqrt = Math.max(0, height * height - 4.0 * verticalRadius * height + totalLength * totalLength);
            double vTheta = 2.0 * Math.atan2(Math.sqrt(innerSqrt) - totalLength, height - 4.0 * verticalRadius);
            this.curveLength = Math.sin(vTheta) * verticalRadius;
            this.curveHeight = (1.0 - Math.cos(vTheta)) * verticalRadius;
        }
        this.ySign = yStart < yEnd ? 1.0 : -1.0;
    }

    // ── 公開 API ─────────────────────────────────────────────────

    public double getLength() {
        return totalLength;
    }

    /**
     * RailCalculator の幾何を MTR RailMath のフィールド表現へ変換する（変換レイヤー）。
     *
     * @return 変換結果。RailCalculator が有効な Group を返さなかった場合（平行・計算不能）は
     *         {@code null}。呼び出し側はそのとき MTR 純正のままにして注入しない。
     */
    public MtrRailGeometry toMtrGeometry() {
        return validGeometry ? MtrRailGeometry.from(section1, section2, yStart, yEnd) : null;
    }

    /** 高低を無視した平面位置。 */
    public Vector getPosition(double distance) {
        return getPosition(distance, 0.0);
    }

    /**
     * 本家 {@code RailMath.getPositionXZ} と同じ規約。
     * <p>{@code offset} は円弧では外向き放射方向（半径を {@code r + offset} にする）、
     * 直線では進行方向を -90 度回した法線方向へ加算される。
     * 描画は {@code offsetRadius1} / {@code offsetRadius2} をそのまま渡せば本家と同じ並びになる。
     */
    public Vector getPosition(double distance, double offset) {
        if (totalLength <= EPSILON) return new Vector(baseX1, yStart, baseZ1);

        double[] out = new double[2];
        double y = getPositionXZ(distance, offset, out);
        return new Vector(out[0], y, out[1]);
    }

    /** 接線（単位ベクトル、y 成分を含む）。 */
    public Vector getTangent(double distance) {
        if (totalLength <= EPSILON) return new Vector(1, 0, 0);

        double[] out = new double[2];
        double d = Math.max(0.0, Math.min(distance, totalLength));
        getTangentXZ(d, out);
        double dx = out[0];
        double dz = out[1];

        double y1 = getPositionY(Math.max(0.0, d - EPSILON));
        double y2 = getPositionY(Math.min(totalLength, d + EPSILON));
        return new Vector(dx, (y2 - y1) / (2.0 * EPSILON), dz).normalize();
    }

    /**
     * 描画用の軽量版。{@link Vector} を生成せず x/z を {@code out[0]}, {@code out[1]} に書く。
     *
     * @return y
     */
    public double getPositionXZ(double distance, double offset, double[] out) {
        if (totalLength <= EPSILON) {
            out[0] = baseX1;
            out[1] = baseZ1;
            return yStart;
        }
        double d = Math.max(0.0, Math.min(distance, totalLength));
        // pair = false: out は長さ 2 しか無므로 2 つ目を書かない
        if (d <= len1) {
            sectionPosition(1, d, offset, offset, out, 0, false);
        } else {
            sectionPosition(2, d - len1, offset, offset, out, 0, false);
        }
        return getPositionY(d);
    }

    /**
     * 描画ループ用のペア版。左右のオフセットを持つ 2 頂点を <b>1 回の</b> {@code cos/sin} で求める。
     * <p>
     * {@code theta} が両者で共通であることを利用し、三角関数を頂点数 x2 から x1 に畳む。
     *
     * @param out {@code out[0..1]} = corner1, {@code out[2..3]} = corner2
     * @return y
     */
    public double getPositionXZPair(double distance, double offset1, double offset2, double[] out) {
        if (totalLength <= EPSILON) {
            out[0] = baseX1; out[1] = baseZ1;
            out[2] = baseX1; out[3] = baseZ1;
            return yStart;
        }
        double d = Math.max(0.0, Math.min(distance, totalLength));
        if (d <= len1) {
            sectionPosition(1, d, offset1, offset2, out, 0, true);
        } else {
            sectionPosition(2, d - len1, offset1, offset2, out, 0, true);
        }
        return getPositionY(d);
    }

    /** 水平接線のみを {@code out[0]}, {@code out[1]} に書く（単位ベクトル）。 */
    public void getTangentXZ(double distance, double[] out) {
        if (totalLength <= EPSILON) {
            out[0] = 1;
            out[1] = 0;
            return;
        }
        double d = Math.max(0.0, Math.min(distance, totalLength));
        if (d <= len1) {
            sectionTangent(1, d, out);
        } else {
            sectionTangent(2, d - len1, out);
        }
    }

    // ── 内部ヘルパー ─────────────────────────────────────────────

    /**
     * {@code offset1} / {@code offset2} の 2 頂点をまとめて求める。
     *
     * @param pair {@code false} なら {@code out[0..1]} のみ、{@code true} なら {@code out[0..3]} を書く
     */
    private void sectionPosition(int section, double localDist, double offset1, double offset2, double[] out, int base, boolean pair) {
        if (section == 1) {
            if (arc1) {
                // 本家 getPositionXZ の円弧分岐: h,k が中心、r + offset が半径
                double theta = thetaStart1 + (reverse1 ? -localDist : localDist) * invR1;
                double cs = Math.cos(theta);
                double sn = Math.sin(theta);
                out[base] = h1 + (r1 + offset1) * cs + corrX1;
                out[base + 1] = k1 + (r1 + offset1) * sn + corrZ1;
                if (pair) {
                    out[2] = h1 + (r1 + offset2) * cs + corrX1;
                    out[3] = k1 + (r1 + offset2) * sn + corrZ1;
                }
            } else {
                // 直線: 法線は進行方向 (h,k) を -90 度回したもの = (k, -h)
                out[base] = baseX1 + h1 * localDist + k1 * offset1;
                out[base + 1] = baseZ1 + k1 * localDist - h1 * offset1;
                if (pair) {
                    out[2] = baseX1 + h1 * localDist + k1 * offset2;
                    out[3] = baseZ1 + k1 * localDist - h1 * offset2;
                }
            }
        } else {
            if (arc2) {
                double theta = thetaStart2 + (reverse2 ? -localDist : localDist) * invR2;
                double cs = Math.cos(theta);
                double sn = Math.sin(theta);
                out[base] = h2 + (r2 + offset1) * cs + corrX2;
                out[base + 1] = k2 + (r2 + offset1) * sn + corrZ2;
                if (pair) {
                    out[2] = h2 + (r2 + offset2) * cs + corrX2;
                    out[3] = k2 + (r2 + offset2) * sn + corrZ2;
                }
            } else {
                out[base] = baseX2 + h2 * localDist + k2 * offset1;
                out[base + 1] = baseZ2 + k2 * localDist - h2 * offset1;
                if (pair) {
                    out[2] = baseX2 + h2 * localDist + k2 * offset2;
                    out[3] = baseZ2 + k2 * localDist - h2 * offset2;
                }
            }
        }
    }

    private void sectionTangent(int section, double localDist, double[] out) {
        if (section == 1) {
            if (arc1) {
                double theta = thetaStart1 + (reverse1 ? -localDist : localDist) * invR1;
                double sign = reverse1 ? -1.0 : 1.0;
                out[0] = -Math.sin(theta) * sign;
                out[1] = Math.cos(theta) * sign;
            } else {
                out[0] = h1;
                out[1] = k1;
            }
        } else {
            if (arc2) {
                double theta = thetaStart2 + (reverse2 ? -localDist : localDist) * invR2;
                double sign = reverse2 ? -1.0 : 1.0;
                out[0] = -Math.sin(theta) * sign;
                out[1] = Math.cos(theta) * sign;
            } else {
                out[0] = h2;
                out[1] = k2;
            }
        }
    }

    // ── 本家 RailMath.getPositionY の移植 ────────────────────────

    public double getPositionY(double value) {
        if (flat) return yStart;

        final double length = totalLength;
        // 長さ 0 のとき QUADRATIC 分岐の intercept が 0 になり 0 除算になる
        if (length <= EPSILON) return yStart;

        switch (shape) {
            case TWO_RADII: {
                if (verticalRadius <= 0) {
                    return value / length * (yEnd - yStart) + yStart;
                }
                if (value < curveLength) {
                    return ySign * (verticalRadius - Math.sqrt(Math.max(0, verticalRadius * verticalRadius - value * value))) + yStart;
                }
                if (value > length - curveLength) {
                    double r = length - value;
                    return -ySign * (verticalRadius - Math.sqrt(Math.max(0, verticalRadius * verticalRadius - r * r))) + yEnd;
                }
                double midY = yStart + ySign * curveHeight;
                double midProgress = (value - curveLength) / Math.max(EPSILON, length - 2.0 * curveLength);
                return midY + ySign * (height - 2.0 * curveHeight) * midProgress;
            }
            case QUADRATIC:
            default: {
                double intercept = length / 2.0;
                double yChange;
                double yInitial;
                double offsetValue;
                if (value < intercept) {
                    yChange = (yEnd - yStart) / 2.0;
                    yInitial = yStart;
                    offsetValue = value;
                } else {
                    yChange = (yStart - yEnd) / 2.0;
                    yInitial = yEnd;
                    offsetValue = length - value;
                }
                return yChange * offsetValue * offsetValue / (intercept * intercept) + yInitial;
            }
        }
    }
}
