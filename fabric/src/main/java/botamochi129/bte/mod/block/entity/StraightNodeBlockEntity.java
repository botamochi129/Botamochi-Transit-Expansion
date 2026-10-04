package botamochi129.bte.mod.block.entity;

import botamochi129.bte.mapping.LoaderImpl;
import botamochi129.bte.mod.data.IRailMathExtra;
import botamochi129.bte.mod.data.NodeGeometry;
import botamochi129.bte.mod.data.RailAngleOverride;
import botamochi129.bte.mod.rail.CantProfile;
import botamochi129.bte.mod.rail.CantRegistry;
import botamochi129.bte.mod.registry.Blocks;
import org.mtr.core.data.Data;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.block.IBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.BlockEntity;

public class StraightNodeBlockEntity extends BlockEntityExtension {

    private static final String KEY_ANGLE = "angle_degrees";
    private static final String KEY_OFFSET_X = "offset_x";
    private static final String KEY_OFFSET_Y = "offset_y";
    private static final String KEY_OFFSET_Z = "offset_z";
    private static final String KEY_SPEED_ORIGINALS = "rail_speed_originals";
    private static final String KEY_CANT_PROFILES = "rail_cant_profiles";

    public static final double UNBOUND_SENTINEL = -129129.0;

    public static final Map<String, double[]> RAIL_MATH_DATA_MAP = new java.util.concurrent.ConcurrentHashMap<>();

/**
     * {@link #RAIL_MATH_DATA_MAP} のエントリが最後に生存確認された時刻（ミリ秒）。
     *
     * <p><b>なぜ時刻判定なのか（per-frame live set ではないのか）</b><br>
     * 当初は「そのフレームの描画 pass で見えたキー」だけを生存とする mark-and-sweep に
     * なっていた。しかしこれは <b>クライアントの描画距離に依存した</b>判定であり、
     * {@link #RAIL_MATH_DATA_MAP} の内容を描画距離で変えていた。
     *
     * <p>実害は次の経路で出る。MTR は {@code MinecraftClientData$RailWrapper} に
     * {@code Rail} をキャッシュしており（フィールド {@code private Rail rail}、
     * 一覧は hexId をキーとする {@code railWrapperList}）、描画距離外にあるレールは
     * {@code positionsToRail} に現れないため sweep がエントリを削除する。
     *  列車が範囲へ戻ると MTR が {@code RailWrapper} と {@code RailMath} を構築するが、
     * その瞬間には
     * {@link botamochi129.bte.mixin.mtr.RailMathMixin#bte$capturePositions} が読む
     * map が空であり、注入されない {@code RailMath} は MTR 純正（＝直線）のまま
     * {@code RailWrapper} にキャッシュされる。結果として形状が描画距離で変わってしまう。
     *
     * <p>そこで生存の根拠を描画 pass ではなく <b>サーバの publish</b>
     * （{@code updateBezierDataOnly}。4 tick 周期で、描画距離に依存しない）に置き、
     * 削除の判断だけを「最後に publish されてから {@link #ENTRY_TTL_MILLIS}
     * 以上経過した」へ変更した。{@code NODE_STATE} も同じ方針。
     */
    private static final java.util.Map<String, Long> RAIL_MATH_LAST_SEEN = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * エントリを捨てるまでの猶予時間（ミリ秒）。
     *
     * <p>描画距離の変更やチャンクの出し入れを十分に吸収できる長さを採る。
     * 恒久的なレールはサーバ publish が 4 tick 周期で記録し続けるので失効しない。
     * 実際に削除されたレールだけが失効する。
     */
    private static final long ENTRY_TTL_MILLIS = 120_000L;

    /**
     * レール配置プレビュー専用のジオメトリ記述子（最大 1 枠）。
     *
     * <p><b>なぜ {@link #RAIL_MATH_DATA_MAP} ではなく別枠なのか</b><br>
     * MTR はプレビュー用 {@code Rail} をそのまま描画せず、
     * {@code PacketUpdateLastRailStyles$Cache#getRailWithLastStyles} → {@code Rail.copy} で
     * <b>新しい {@code Rail} と新しい {@code RailMath}</b> を作ってそちらをレンダーリストへ積む
     * （{@code RenderRails#renderRailStandard} のバイトコード 551 で生成 → 568 で copy → 575/583 で add）。
     * {@code Rail.copy} は {@code position1/angle1/position2/angle2} から {@code RailMath} を
     * 再構築するため、生成した {@code Rail} へ注入したフィールドは<b>捨てられる</b>。
     * コピーの {@code RailMath} は {@link botamochi129.bte.mixin.mtr.RailMathMixin#bte$capturePositions}
     * しか救済手段を持たないが、そこは {@link #RAIL_MATH_DATA_MAP} を読む。
     * プレビューのノード対は「まだ配置されていない」ので同 Map には載らず、
     * コピーだけが MTR 純正のブロック角度ジオメトリで描画され（＝BTE のカスタム角度が無視され）
     * プレビューのモデルが崩れる。
     *
     * <p>そこで本枠を {@code Rail.copy} が生成する {@code RailMath} の {@code <init>} から
     * 回収させる。<b>{@link #RAIL_MATH_DATA_MAP} は触らない</b>ことで、
     * シングルプレイでサーバと共有される同 Map（および同 Map を読む {@code RailStartAngleMixin}）へ
     * プレビュー専用の一時値を混ぜずに済む。生存判定（sweep）とも独立になる。
     *
     * <p><b>寿命</b><br>
     * {@code RenderRailsMixin#bte$patchRailsBeforeRender} の冒頭でクリアし、
     * {@code ItemRailModifierMixin} の {@code createRail} RETURN でセットする。
     * 消費（コピーの {@code <init>}）は同じフレーム内の copy 生成時に起きるので 1 フレームで消える。
     * 参照の書き込みは原子的であり、完成済みの配列をまるごと公開するため
     * {@code volatile} で他スレッドへの可視性を確保する。
     */
    private static volatile double[] PREVIEW_RAIL_GEOMETRY = null;

    /**
     * {@code RailMath} / {@code PathData} / レンダラが共有するレール識別キーを作る。
     * <p>
     * 6 個の {@code long} をカンマ連結した {@link String} をそのまま {@link String#format} 相当で
     * 組み立てているが、{@link StringBuilder} を使い回すことで毎フレームの
     * 中間オブジェクト生成を削る。キーの <b>文字列表現は従来と完全に同一</b>。
     */
    private static final ThreadLocal<StringBuilder> KEY_BUILDER = ThreadLocal.withInitial(StringBuilder::new);

    // ── サーバティック駆動台帳 ──────────────────────────────────────────
    // MTR の Registry.registerBlockEntityType は ticker を受け付けないため
    // BlockEntityExtension#tick() はゲームから一度も呼ばれない。结果として
    // ワールド読み込み時 (readCompoundTag が needsInitialUpdate を立てるだけ) や
    // MTR のデータ再読込後に更新が走らず、bezier も経路探索用の角度上書きも
    // 保存値のまま残り、共有ノードが 180 度_minus ずれ PATH_NOT_FOUND になる。
    // 弱い参照の台帳を Fabric のサーバティックから駆動して自己修復させる。
    private static final Map<String, java.lang.ref.WeakReference<StraightNodeBlockEntity>> TRACKED =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> PENDING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static int serverTickCount = 0;

    /** 角度の再反映周期 (tick)。短すぎると無駄、長すぎると MTR の Rail 再生成と経路探索が乖離する。 */
    private static final int REFRESH_INTERVAL = 4;

    /** 発見スウィープで 1 tick あたりに走査する位置数。 */
    private static final int DISCOVERY_CHUNK = 2000;

    /** 発見スウィープの開始間隔 (tick)。チャンクは時間差で読み込まれるため反復走査する。 */
    private static final int DISCOVERY_INTERVAL = 100;

    /** 発見スウィープの残り位置。null は「走査中でない」。 */
    private static java.util.Iterator<Position> discoveryCursor = null;
    private static World discoveryWorld = null;
    private static int discoveryRuns = 0;
    private static int lastDiscoveryTick = 0;

    /** 一時診断: どの経路で登録されたか。0 = 未登録。 */
    private static final int SRC_NONE = 0;
    private static final int SRC_READ = 1;
    private static final int SRC_EDIT = 2;
    private static final int SRC_DISCOVERY = 3;

    private double angleDegrees = UNBOUND_SENTINEL;
    private boolean needsInitialUpdate = true;
    private int tickCount = 0;

    /** 一時診断: このノードがどの経路で台帳に登録されたか。 */
    private transient int bte$regSource = SRC_NONE;

    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private double offsetZ = 0.0;

    /**
     * BTE が速度制限を変更したレールについて、<b>変更前の MTR 既定値</b> を rail hex id ごとに保持する。
     * <p>
     * 実際の制限速度は MTR 側が {@code RailSchema} としてワールドセーブへ永続化するため、
     * BTE 側で持つのは「既定値へ戻す」ための退避用データだけである。
     * 値の実体は {@code long[2]} = {端点1, 端点2} の km/h。
     */
    private final Map<String, long[]> speedLimitOriginals = new java.util.HashMap<>();

    /**
     * このノードに接続するレールのカント設定。キーは {@code Rail#getHexId()}（正規方向）。
     * <p>
     * カントは見た目だけなので物理側へは何も送らない。値の供給は
     * {@link #readCompoundTag(CompoundTag)} 一本化で、これがサーバーのワールド読込時と
     * クライアントへの BE 同期時の双方で走るため、{@link CantRegistry} へ自然に行き渡る。
     */
    private final Map<String, CantProfile> cantProfiles = new java.util.HashMap<>();

    public StraightNodeBlockEntity(BlockPos pos, BlockState state) {
        super(Blocks.STRAIGHT_NODE_BE.get(), pos, state);
    }

    public StraightNodeBlockEntity(BlockPos pos, BlockState state, double angle) {
        super(Blocks.STRAIGHT_NODE_BE.get(), pos, state);
        this.angleDegrees = angle;
    }

    public void tick() {
        World world = getWorld2();
        if (world == null || world.isClient() || !isBound()) return;

        tickCount++;
        if (needsInitialUpdate || tickCount >= 10) {
            tickCount = 0;
            needsInitialUpdate = false;
            updateConnectedRails(true);
        }
    }

    public double getAngleDegrees() {
        return angleDegrees;
    }

    public boolean isBound() {
        return angleDegrees != UNBOUND_SENTINEL;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public double getOffsetZ() {
        return offsetZ;
    }

    public void setOffset(double x, double y, double z) {
        this.offsetX = Math.max(-1.0, Math.min(1.0, x));
        this.offsetY = Math.max(-1.0, Math.min(1.0, y));
        this.offsetZ = Math.max(-1.0, Math.min(1.0, z));
        markDirty2();
        syncBlockEntity();
        updateBezierDataOnly();
        markRefreshPending(SRC_EDIT);
    }

    public void bind(StraightNodeBlockEntity other) {
        if (isBound()) return;
        BlockPos thi = getPos2();
        BlockPos oth = other.getPos2();
        bind(Math.toDegrees(Math.atan2(oth.getZ() - thi.getZ(), oth.getX() - thi.getX())));
        other.bind(this);
    }

    public void bind(double angle) {
        this.angleDegrees = normalize(angle);
        markDirty2();
        syncBlockEntity();
        updateBezierDataOnly();
        markRefreshPending(SRC_EDIT);
    }

    private static String trackKey(World world, BlockPos pos) {
        return Init.getWorldId(world) + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /**
     * {@link #RAIL_MATH_DATA_MAP} のキー（{@code x1,y1,z1,x2,y2,z2}）を組み立てる。
     * <p>端点の順序に依存しないよう、2 端点を (x, y, z) の辞書順で大小を決め、
     * 小さい方の端点を先に並べて連結する。
     *
     * <p><b>（重要）軸ごとに独立した min/max で正規化してはいけない。</b>
     * その方式だとキーは「2 端点を包むバウンディングボックス」になり、
     * 大小が混在する端点対（例: {@code (0,70,0)} と {@code (10,64,5)}）では
     * どちらの端点もボックスの角と一致しない。その結果、
     * {@code (0,70,0)-(10,64,5)} と {@code (0,64,5)-(10,70,0)} はどちらも
     * {@code (0,64,0)-(10,70,5)} になり<b>別レールのキーが衝突する</b>。
     *
     * <p>衝突すると {@code RailMathMixin#bte$capturePositions} が
     * {@code Rail.copy} 複製へ別レールの記述子を注入し、
     * 端点組が合わない {@link RailCalculator} の計算が退化して
     * {@code toMtrGeometry()} が null を返す → MTR 純正の直線に落ちる。
     * どちらの記述子が最後に書かれるかは {@code positionsToRail} の反復順、
     * 即ちロード済みチャンク集合（= プレイヤー位置）で変わるため、
     * 「近づくと直線になり離れると正しく曲がる」という位置依存の振動症状になる。
     */
    public static String railMathKey(long x1, long y1, long z1, long x2, long y2, long z2) {
        final boolean swap = (x1 > x2)
                || (x1 == x2 && y1 > y2)
                || (x1 == x2 && y1 == y2 && z1 > z2);
        StringBuilder sb = KEY_BUILDER.get();
        sb.setLength(0);
        if (swap) {
            sb.append(x2).append(',').append(y2).append(',').append(z2).append(',')
              .append(x1).append(',').append(y1).append(',').append(z1);
        } else {
            sb.append(x1).append(',').append(y1).append(',').append(z1).append(',')
              .append(x2).append(',').append(y2).append(',').append(z2);
        }
        return sb.toString();
    }

    /** {@link Position} 2 点から {@link #railMathKey} を組み立てる。 */
    public static String railMathKey(Position p1, Position p2) {
        return railMathKey(p1.getX(), p1.getY(), p1.getZ(), p2.getX(), p2.getY(), p2.getZ());
    }

    /**
     * {@link #RAIL_MATH_DATA_MAP} に publish 済みの<b>退出角</b>を端点組の向きiguouslyに読み出す。
     *
     * <p>{@code double[14]} の {@code d[6]}/{@code d[7]} は publish した側の
     * {@code p1/p2} 順で入っており、MTR が {@code Rail} の端点を入れ替えると
     * どちらの端か入れ替わる（{@code RailMathMixin#bte$injectDescriptor} が
     * {@code d[10],d[11]} で向きを判定している理由がこれ）。
     * よって要求された {@code p1}/{@code p2} の順へ読み替える必要がある。
     *
     * <p>これは<b>チャンクロード状態に依存しない</b>唯一の MTR 標準ノード端の角度供給源。
     * クライアントが {@code ClientWorld#getBlockState} から {@code BlockNode.getAngle} を
     * 求めると、チャンクマップ半径外では air が返り 90 度（＝直線）になり、
     * プレイヤー位置で形状が変わる。
     *
     * @param outRad 長さ 2。{@code outRad[0]} に p1 端、{@code outRad[1]} に p2 端の退出角（ラジアン）
     * @return publish 済みの記述子があり向きも特定できたときだけ true
     */
    public static boolean getCachedExitAngles(String railKey, BlockPos p1, BlockPos p2, double[] outRad) {
        if (railKey == null || p1 == null || p2 == null || outRad == null || outRad.length < 2) return false;
        final double[] d = RAIL_MATH_DATA_MAP.get(railKey);
        if (d == null || d.length < 14) return false;

        final long ax = (long) d[10];
        final long az = (long) d[11];
        final long bx = (long) d[12];
        final long bz = (long) d[13];

        if (p1.getX() == ax && p1.getZ() == az && p2.getX() == bx && p2.getZ() == bz) {
            outRad[0] = d[6];
            outRad[1] = d[7];
            return true;
        }
        if (p1.getX() == bx && p1.getZ() == bz && p2.getX() == ax && p2.getZ() == az) {
            outRad[0] = d[7];
            outRad[1] = d[6];
            return true;
        }
        return false;
    }

    /**
     * レールが現在も存在することを記録する（生存確認）。
     *
     * <p>呼び出し元は 2 箇所あり、どちらも描画距離に依存しない：
     * <ul>
     *   <li>サーバの publish（{@code updateBezierDataOnly}、4 tick 周期）</li>
     *   <li>クライアントの描画 pass（{@code RenderRailsMixin}、sweep 周期のみ）</li>
     * </ul>
     */
    public static void markLiveRailMath(String key) {
        if (key != null) RAIL_MATH_LAST_SEEN.put(key, System.currentTimeMillis());
    }

    /**
     * 最後に生存確認されてから {@link #ENTRY_TTL_MILLIS} 以上経過したキーを
     * {@link #RAIL_MATH_DATA_MAP} から削除する。
     *
     * <p>削除の根拠は「今は描画距離内にない」ではなく「存在しなくなった」である。
     * 前者で判定すると、レールが範囲外に出た途端に {@code RailMath} の救済手段が
     * 失われ、列車が戻ったときの形状が MTR 純正の直線になってしまう。
     * 記録が一度も無いキー（正常には起こらない）も安全側に倒して削除する。
     */
    public static void sweepRailMath() {
        final long now = System.currentTimeMillis();
        for (java.util.Iterator<String> it = RAIL_MATH_DATA_MAP.keySet().iterator(); it.hasNext(); ) {
            final String key = it.next();
            final Long lastSeen = RAIL_MATH_LAST_SEEN.get(key);
            if (lastSeen == null || (now - lastSeen) > ENTRY_TTL_MILLIS) {
                it.remove();
                RAIL_MATH_LAST_SEEN.remove(key);
            }
        }
    }

    /** ワールド切替時に生存判定を捨てる。 */
    public static void clearLiveRailMath() {
        RAIL_MATH_LAST_SEEN.clear();
    }

    // ── ノード状態のクライアント側キャッシュ ────────────────────────
    //
    // ★ なぜノード単位で持つのか（設計判断）
    //   RAIL_MATH_DATA_MAP をフォールバックに使う案は却下した。同 Map のキーは
    //   railMathKey が端点組の辞書順で対称化しているが、ペイロード data[0..2]/[6] は
    //   「publish した側の p1/p2 順」で書かれており、MTR が Rail 端点を入れ替えると
    //   どちらの端か入れ替わる（bte$injectDescriptor が data[10],data[11] で向きを
    //   判定している理由がこれ）。ノード単位でキャッシュすれば向きの問題が構造的に起きない。
    //
    // ★ なぜ必要か
    //   チャンクのロード範囲はプレイヤー中心なので、長いレールの端が範囲外に出ると
    //   ClientWorld#getBlockEntity が null を返す。そのままでは
    //   RenderRailsMixin が MTR 標準のブロック軸へフォールバックし、
    //   同じレールがプレイヤーの位置によって別の曲線で描画されていた。
    //   なお angleDegrees / offset はブロック更新でしか変わらないので、
    //   チャンク未ロード中にキャッシュが古くなることはない。
    private static final java.util.Map<Long, double[]> NODE_STATE = new java.util.concurrent.ConcurrentHashMap<>();

/** {@link #NODE_STATE} の各ノードが最後に生存確認された時刻（ミリ秒）。 */
private static final java.util.Map<Long, Long> NODE_STATE_LAST_SEEN = new java.util.concurrent.ConcurrentHashMap<>();

/**
 * キャッシュから node state を引く（チャンク未ロード時に使う）。bound なら true。
 *
 * <p>生存記録をここ（read path）でも行うのは、mark-and-sweep の標準実装である。
 * ただし削除の根拠はフレームの可視性ではなく {@link #ENTRY_TTL_MILLIS} 経過である。
 * per-frame live set のままだと「チャンク未ロード = そのフレームで読まれない = 即削除」となり、
 * このキャッシュがアンロードを生き延びない。実測ログで {@code live → MTR} へ振動し
 * {@code cached} が一度も観測されなかったのはその欠落が原因。
 */
public static boolean getCachedNodeState(BlockPos pos, double[] out) {
    if (pos == null || out == null) return false;
    final long k = packNodeKey(pos.getX(), pos.getY(), pos.getZ());
    final double[] v = NODE_STATE.get(k);
    if (v == null || v.length < 5 || v[4] == 0) return false;
    out[0] = v[0]; out[1] = v[1]; out[2] = v[2]; out[3] = v[3]; out[4] = 1;
    NODE_STATE_LAST_SEEN.put(k, System.currentTimeMillis());
    return true;
}

/** ライブな BE から角度/offset を記録する。bound でなければキャッシュしない。 */
public static void cacheNodeState(BlockPos pos, double angleDegrees, double offX, double offY, double offZ) {
    if (pos == null) return;
    final long k = packNodeKey(pos.getX(), pos.getY(), pos.getZ());
    NODE_STATE.put(k, new double[]{angleDegrees, offX, offY, offZ, 1.0});
    NODE_STATE_LAST_SEEN.put(k, System.currentTimeMillis());
}

    private static long packNodeKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

/**
 * 最後に生存確認されてから {@link #ENTRY_TTL_MILLIS} 以上経過したノード状態を
 * {@link #NODE_STATE} から削除する。
 *
 * <p>削除の根拠は「今は描画距離内にない」ではなく「存在しなくなった」である。
 * アンロード中のノードこそこのキャッシュの存在理由なので、可視性で消すと
 * 形状がチャンクの出し入れで変わる。ノードが実際に消されたときだけ失効する。
 */
public static void sweepNodeState() {
    final long now = System.currentTimeMillis();
    for (java.util.Iterator<Long> it = NODE_STATE.keySet().iterator(); it.hasNext(); ) {
        final Long k = it.next();
        final Long lastSeen = NODE_STATE_LAST_SEEN.get(k);
        if (lastSeen == null || (now - lastSeen) > ENTRY_TTL_MILLIS) {
            it.remove();
            NODE_STATE_LAST_SEEN.remove(k);
        }
    }
}

/** ワールド切替時にノード状態キャッシュを捨てる。 */
    public static void clearNodeState() {
        NODE_STATE.clear();
        NODE_STATE_LAST_SEEN.clear();
        NATIVE_RAILS.clear();
    }

// ── MTR ネイティブ解決の台帳 ──────────────────────────────────
    //
    // ★ MTR の不変条件
    //   MTR の RailMath は final フィールド（h1/k1/r1/tStart.../minX..maxZ/yStart/yEnd）を
    //   <init> で一度だけ焼き込み、以後は一切書き換えない。形状を変えたい場合は Rail を作り直す。
    //    RailMathMixin が minX..maxZ を上書きすると、そこから導出される
    //   RailWrapper の遮蔽カリング AABB、Rail#closeTo、接続キャッシュ、経路探索が
    //   すべて構築時の値のまま中途半端に古くなる。
    //
    //   RailGetAnglesMixin は Rail.getAngles の引数（= 両端ノードの軸角）を BTE の値に
    //   差し替えることで、MTR に RailMath を natively 構築させる。
    //   こうすると minX..maxZ も RailWrapper の AABB も構造的に正しくなり、
    //   in-place 注入が要らなくなる。
    //
    // ★ なぜ「記録した軸の値」と照合して判定するのか（恒久フラグにしない理由）
//   Rail.getAngles は Rail の構築時にしか呼ばれないので、一度 true になった端点対は
//   Rail が再構築されるまで「構築されたまま」でしかありえない。
//   しかし BTE ノードの軸は GUI で実行時に変更できる。軸を回転すると、
// MTR が構築した RailMath は旧軸のままなのに台帳だけが true のままだと、
//     RailMathMixin#bte$capturePositions と RenderRailsMixin が in-place 注入を
//   両方スキップし、形状が更新されなくなる（再入場まで直らない症状の直接原因）。
//   したがって「構築した瞬間」ではなく「現在のノード軸が記録と一致するか」で判定する。
//   これなら軸の回転・offset の増減・bind/unbind のすべてで判定が自動的に追随する。
//   TTL は設けない。時間窓ではなく「記録した入力が今も有効か」という理屈の判定だから。

/**
 * ネイティブ解決済みの端点対。
 * キーは {@link #railMathKey} と同じ、値は
 * 「{@code Rail.getAngles} に差し込んだ BTE 軸」を端点座標で引けるようにしたもの。
 * BTE ノードでなかった端は値を持たない（＝MTR 本家の軸をそのまま通した端）。
 */
private static final java.util.Map<String, java.util.Map<Long, Double>> NATIVE_RAILS =
        new java.util.concurrent.ConcurrentHashMap<>();

/** {@code Position} を {@link #packNodeKey} と同じ規則で long に潰す。 */
private static long packPosition(Position position) {
    // Position#getX/getY/getZ は long を返すが、pack する添字は int 幅で十分な
    // ブロック座標なので詰め替わる（連結は long 演算のままで桁あふれしない）。
    return packNodeKey((int) position.getX(), (int) position.getY(), (int) position.getZ());
}

/**
 * この端点対が {@code Rail.getAngles} で BTE の軸角に差し替えられたことを記録する。
 *
 * @param key    {@link #railMathKey} と同じ端点対キー
 * @param axis1  {@code position1} 端の BTE 軸。MTR 標準ノードの端なら null
 * @param axis2  {@code position2} 端の BTE 軸。MTR 標準ノードの端なら null
 */
public static void markNativeRail(String key, Position position1, Position position2,
                                 Float axis1, Float axis2) {
    if (key == null || position1 == null || position2 == null) return;
    final java.util.Map<Long, Double> axes = new java.util.HashMap<>(2);
    if (axis1 != null) axes.put(packPosition(position1), (double) axis1);
    if (axis2 != null) axes.put(packPosition(position2), (double) axis2);
    NATIVE_RAILS.put(key, axes);
}

/**
 * 一度でも {@link #markNativeRail} された端点対か（＝ BTE が構築時に軸を差し替えたことがあるか）。
 *
 * <p>これは {@link #isNativeRail} とは別の、<b>{@code NODE_STATE} を参照しない</b>問い。
 * 「BTE がこの端点対を一度でも扱ったか」だけをキー参照 1 回で判定するので、
 * 描画 pass の入口で「BTE がまったく関与していないレール」を世界照会なしで弾く用途に使う。
 */
public static boolean wasMarkedNativeRail(String key) {
    return key != null && NATIVE_RAILS.containsKey(key);
}

/**
 * この端点対がネイティブ解決済みか（＝ in-place 注入を無効化して MTR 純正に委ねるべきか）。
 *
 * <p>「記録した BTE 軸が今もそのまま有効」であれば true。すなわち全端について
 * <ul>
 *   <li>BTE 端だったなら {@link #getNativeNodeAxis} が今も同じ軸を返す（offset が 0）</li>
 *   <li>MTR 標準ノード端だったなら今も BTE ノードになっていない</li>
 * </ul>
 * を満たすときだけ true を返す。ノード軸の回転・offset 変更・bind/unbind に自動追随し、
 * 恒久フラグのような「入力が変わっても true のまま残る」状態をつくらない。
 */
public static boolean isNativeRail(Position position1, Position position2) {
    if (position1 == null || position2 == null) return false;
    final java.util.Map<Long, Double> axes = NATIVE_RAILS.get(railMathKey(position1, position2));
    if (axes == null) return false;

    if (!bte$nativeEndStillValid(position1, axes)) return false;
    return bte$nativeEndStillValid(position2, axes);
}

/** 片端が「記録した条件を今も満たす」か。 */
private static boolean bte$nativeEndStillValid(Position position, java.util.Map<Long, Double> axes) {
    final double[] out = new double[4];
    final boolean nativeNow = getNativeNodeAxis(Init.positionToBlockPos(position), out);
    final Double recorded = axes.get(packPosition(position));

    if (recorded == null) {
        // MTR 標準ノード端だったのが BTE ノードに置き換わった → 条件は崩れた
        return !nativeNow;
    }
    // BTE 端だったのに現在ネイティブ扱いでなくなった（offset が 0 でなくなった）
    if (!nativeNow) return false;
    return Math.abs(out[0] - recorded) <= 1e-6;
}

    /**
     * {@code Rail.getAngles} で BTE ノードの生軸をそのまま MTR に渡してよいか
     *（＝offset が全てゼロで、bound な BTE ノードか）。
     *
     * <p><b>角度の丸めを考えなくてよい理由</b><br>
     * MTR の {@code Angle} は本来 16 要素の enum で、{@code Angle.fromAngle(float)} は
     * 22.5 度刻みに必ず丸める。しかし BTE の {@code AngleMixin} は
     * {@link botamochi129.bte.mod.data.AngleExtra#fromDegrees(double)} を通じて
     * 16 要素に一致しない任意角度の phantom {@code Angle}（{@code ordinal = -1}、
     * {@code angleDegrees} を厳密に保持、{@code angleRadians/sin/cos/tan/halfTan} も再計算）
     * を生成できる。したがって<b>MTR のソルバには正確な自由角を直接渡せる</b>。
     * 「MTR は丸めてしまう = 別のカーブになる」という理由でのグリッド判定は行わない。
     *
     * <p>残る制約は offset のみ。{@code RailMath} の端点座標は {@code Position}（整数）であり
     * {@code double} を受け取る公開入口が無いので、小数座標だけは MTR ネイティブに載せられない。
     * そのため offset が非ゼロの端は MTR 本家の値に任せ、BTE の in-place 注入に一本化する。
     */
    public static boolean getNativeNodeAxis(BlockPos pos, double[] out) {
        if (pos == null || out == null || out.length < 4) return false;
        final long k = packNodeKey(pos.getX(), pos.getY(), pos.getZ());
        final double[] v = NODE_STATE.get(k);
        if (v == null || v.length < 5 || v[4] == 0) return false;
        if (v[1] != 0.0 || v[2] != 0.0 || v[3] != 0.0) return false;
        out[0] = v[0];
        out[1] = v[1];
        out[2] = v[2];
        out[3] = v[3];
        NODE_STATE_LAST_SEEN.put(k, System.currentTimeMillis());
        return true;
    }

    /**
     * このブロック位置に bound な BTE ノードの状態が記録されているか。
     *
     * <p>{@code NODE_STATE} だけを参照するので世界照会も BE 取得もせず、
     * 描画 pass の入口で「この端点が BTE ノードか」をキー参照 1 回で判定できる。
     * クライアント側のブートストラップ判定に使う。
     */
public static boolean hasNodeState(BlockPos pos) {
        if (pos == null) return false;
        final double[] v = NODE_STATE.get(packNodeKey(pos.getX(), pos.getY(), pos.getZ()));
        return v != null && v.length >= 5 && v[4] != 0;
    }

    /**
     * この BE の現在の角度/offset を {@link #NODE_STATE} へ公開する。
     * bound でなければ記録を破棄する（unbind 済みノードの形状を残さないため）。
     *
     * <p>{@link #readCompoundTag} から呼ぶ。クライアントは {@code Rail} を
     * 逆シリアライズして得るだけで {@code Rail.getAngles} を通らないため、
     * {@code RailGetAnglesMixin} による公開では {@code NODE_STATE} が埋まらない。
     * NBT 同期が両側で確実に届く経路なので、読み込み直後にここで公開し直す。
     */
    public void publishNodeState() {
        if (!isBound()) {
            invalidateNodeState(getPos2());
            return;
        }
        cacheNodeState(getPos2(), angleDegrees, offsetX, offsetY, offsetZ);
    }

    /** 指定位置の {@link #NODE_STATE} を破棄する（unbind・ブロック除去に追随させる）。 */
    public static void invalidateNodeState(BlockPos pos) {
        if (pos == null) return;
        final long k = packNodeKey(pos.getX(), pos.getY(), pos.getZ());
        NODE_STATE.remove(k);
        NODE_STATE_LAST_SEEN.remove(k);
    }

    /**
     * プレビュー用 {@code Rail} のジオメトリ記述子を登録する（1 枠・上書き）。
     *
     * <p>レイアウトは {@link #RAIL_MATH_DATA_MAP} の値と同じ 14 要素で、
     * {@code botamochi129.bte.mixin.mtr.RailMathMixin#bte$capturePositions} が
     * 同じコードでそのまま読める：
     * <pre>
     * [0..2]   始点 x, y, z（ブロック整数 + BTE サブブロック offset。+0.5 は付けない）
     * [3..5]   終点 x, y, z
     * [6]      始点角度 (rad)
     * [7]      終点角度 (rad)
     * [8]      verticalRadius
     * [9]      Rail.Shape の ordinal
     * [10..11] 端点 A の x, z
     * [12..13] 端点 B の x, z
     * [14..15] 端点 A, B の y（プレビュー枠のみ。XZ の一致だけで他レールへ
     *           誤適用しないよう、3D で厳密に照合するために使う）
     * </pre>
     *
     * @param descriptor 完成済みの 16 要素配列（呼び出し側が所有してよい）
     */
    public static void setPreviewRailGeometry(double[] descriptor) {
        PREVIEW_RAIL_GEOMETRY = (descriptor != null && descriptor.length >= 16) ? descriptor : null;
    }

    /** プレビュー用ジオメトリを捨てる。描画 pass の先頭で呼ぶ。 */
    public static void clearPreviewRailGeometry() {
        PREVIEW_RAIL_GEOMETRY = null;
    }

    /**
     * 登録済みのプレビュー用ジオメトリを返す。未登録なら {@code null}。
     * <p>他のノード対の {@code RailMath} には適用しないよう、呼び出し側が
     * 記述子末尾の端点 XZ とコンストラクタ引数を照合すること。
     */
    public static double[] getPreviewRailGeometry() {
        return PREVIEW_RAIL_GEOMETRY;
    }

    /**
     * このノードの状態を MTR の経路探索へ反映し直す予約を立てる。
     * クライアント側では不要 (描画は保存データと RAIL_MATH_DATA_MAP を使う) なので何もしない。
     */
    public void markRefreshPending() {
        World world = getWorld2();
        if (world == null || world.isClient()) return;
        String key = trackKey(world, getPos2());
        TRACKED.put(key, new java.lang.ref.WeakReference<>(this));
        PENDING.add(key);
    }

    /** どの経路で登録されたかを診断用に記録する (一時)。 */
    private void markRefreshPending(int source) {
        String key = null;
        World world = getWorld2();
        if (world != null && !world.isClient()) key = trackKey(world, getPos2());
        if (key == null) {
            return;
        }
        bte$regSource = source;
        markRefreshPending();
    }

    /**
     * サーバ生成/破棄のたびに台帳を捨てる。
     * <p>
     * シングルプレイでは「タイトルへ戻って再参加」しても JVM は同じでサーバだけが作り直される。
     * 静的台帳に残った前セッションの弱参照は、新セッションの同一座標キーと衝突するため、
     * 新セッション側のノードが永久に publish されないままになる (角度を触るまで直らない)。
     */
    public static void resetTracking() {
        TRACKED.clear();
        PENDING.clear();
        clearLiveRailMath();
        clearNodeState();
        serverTickCount = 0;
        discoveryCursor = null;
        discoveryWorld = null;
        discoveryRuns = 0;
        lastDiscoveryTick = 0;
        CantRegistry.clear();
    }

    /**
     * セッション開始時に、MTR のレール端位置を走査して BTE ノードを漏れなく登録する。
     * <p>
     * {@code readCompoundTag} はチャンクのデシリアライズ時に呼ばれるが、その時点では
     * World が未接続の場合があり {@link #markRefreshPending()} が no-op になり得る。
     * 再参加のたびに「角度を触らないと直らない」症状の主因がこれだったため、
     * World が確実に取れる経路を別に用意し、その場で判定し直す。
     */
    public static void beginDiscovery() {
        discoveryCursor = null;
        discoveryWorld = null;
    }

    /** 発見スウィープを 1 tick 分だけ進める。 */
    private static void discoveryTick(World world) {
        if (world == null || world.isClient()) return;

        // 走査中にワールドが変わった (別ワールドへ移動した) 場合は破棄してやり直す
        if (discoveryCursor != null && discoveryWorld != null && discoveryWorld != world) {
            discoveryCursor = null;
            discoveryWorld = null;
        }

        if (discoveryCursor == null) {
            // 走査中は回さない。完了後に一定間隔で再開する。
            if (discoveryRuns > 0 && serverTickCount - lastDiscoveryTick < DISCOVERY_INTERVAL) return;
            Data data = LoaderImpl.getDataForWorld(world);
            if (data == null) return; // MTR の Data は未準備。次の tick で再試行する
            if (data.positionsToRail == null || data.positionsToRail.isEmpty()) return;
            // ★ keySet() のコピーを作らずビューをそのまま反復する（総レール数分の配列確保を避ける）
            discoveryCursor = data.positionsToRail.keySet().iterator();
            discoveryWorld = world;
            lastDiscoveryTick = serverTickCount;
            discoveryRuns++;
        }

        for (int i = 0; i < DISCOVERY_CHUNK && discoveryCursor.hasNext(); i++) {
            Position p = discoveryCursor.next();
            BlockPos bp = Init.positionToBlockPos(p);
            BlockEntity rawBe = world.getBlockEntity(bp);
            if (rawBe == null) continue;
            if (!(rawBe.data instanceof StraightNodeBlockEntity snbe)) continue;
            if (!snbe.isBound()) continue;
            String key = trackKey(world, bp);
            if (TRACKED.containsKey(key)) continue;
            snbe.bte$regSource = SRC_DISCOVERY;
            snbe.markRefreshPending();
        }

        if (!discoveryCursor.hasNext()) {
            discoveryCursor = null;
            discoveryWorld = null;
        }
    }

    /** {@code ServerTickEvents.END_SERVER_TICK} から呼ばれる。 */
    public static void serverTick(World world) {
        serverTickCount++;
        discoveryTick(world);
        if (TRACKED.isEmpty()) return;

        // MTR は自前のデータ保存で Rail 実体を作り直すため、そのたびに上書きが消える。
        // 再生成から経路探索までの窓を小さく保つため短周期で自己修復する。
        //
        // ★ PENDING ⊆ TRACKED なので、PENDING を先に走らせる pass は
        //   同じノードを同じ tick で 2 回更新するだけだった。TRACKED を 1 回走れば足りる。
        if (serverTickCount % REFRESH_INTERVAL == 0) {
            drain();
        }
    }

    private static void drain() {
        // ★ 反復中に TRACKED を触るので ConcurrentModificationException を避けるため
        //   keySet のコピーではなく ConcurrentHashMap のイテレータを直接使う。
        for (java.util.Iterator<Map.Entry<String, java.lang.ref.WeakReference<StraightNodeBlockEntity>>> it = TRACKED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, java.lang.ref.WeakReference<StraightNodeBlockEntity>> entry = it.next();
            java.lang.ref.WeakReference<StraightNodeBlockEntity> ref = entry.getValue();
            StraightNodeBlockEntity be = ref == null ? null : ref.get();
            if (be == null) {
                it.remove();
                PENDING.remove(entry.getKey());
                continue;
            }
            World world = be.getWorld2();
            if (world == null || world.isClient()) {
                it.remove();
                PENDING.remove(entry.getKey());
                continue;
            }
            // MTR の Data が未準備なら PENDING に残して次回へ繰り越す (取りこぼさない)
            if (be.updateBezierDataOnly()) {
                PENDING.remove(entry.getKey());
            }
        }
    }

    /**
     * bezier 曲線と経路探索用角度を MTR へ反映する。
     *
     * @return MTR の Data が利用できて反映が完了したなら true。
     *         false は「まだ反映できていない」= 呼び出し側で再試行すべき状態。
     */
    private boolean updateBezierDataOnly() {
        World world = getWorld2();
        if (world == null || world.isClient()) return false;

        Data data = LoaderImpl.getDataForWorld(world);
        if (data == null) return false;

        Position nodePos = Init.blockPosToPosition(getPos2());

        Map<Position, Rail> railsAtPos = data.positionsToRail.get(nodePos);
        if (railsAtPos == null) return true;

        // unbind済みノードは BTE 由来の上書きを解除し、MTR の保存値にフォールバックさせる。
        // 対向端も消す: 標準ノード端の上書きは対向ノードに再publishする者が居ないため。
        if (!isBound()) {
            for (Map.Entry<Position, Rail> entry : railsAtPos.entrySet()) {
                if (((Object) entry.getValue()) instanceof RailAngleOverride angleOverride) {
                    angleOverride.bte$clearAngleOverride(nodePos);
                    angleOverride.bte$clearAngleOverride(entry.getKey());
                }
            }
            return true;
        }

        double selfAxis = this.angleDegrees; // 0~180 の軸

        // ★ ノードの生軸を NODE_STATE へ載せる。
        //   RailGetAnglesMixin は MTR の RailMath を本家どおりに構築させるため、
        //   「両端ノードの軸角」しか必要としない。ここが唯一の距離非依存な供給源になる
        //   （クライアントのチャンクロード範囲に依存しない）。
        cacheNodeState(getPos2(), selfAxis, this.getOffsetX(), this.getOffsetY(), this.getOffsetZ());

        for (Map.Entry<Position, Rail> entry : railsAtPos.entrySet()) {
            Position otherPos = entry.getKey();
            Rail rail = entry.getValue();
            if (rail == null || rail.railMath == null) continue;

            String railMathKey = railMathKey(
                    nodePos.getX(), nodePos.getY(), nodePos.getZ(),
                    otherPos.getX(), otherPos.getY(), otherPos.getZ()
            );

            // ★ 生存を「クライアントの描画距離」ではなく serveurの publish で判定する。
            //   mark を描画 pass 側にしか置かないと、MTR の描画距離外にあるレールは
            //   「サーバが 4 tick 周期に publish した直後にクライアント sweep で削除」され、
            //   車両が範囲へ戻ったとき MTR が RailWrapper / RailMath を構築する瞬間には
            //   map が空になっている。その RailMath は MTR 純正（＝直線）のまま
            //   RailWrapper にキャッシュされ、形状が「描画距離」で変わってしまう。
            //   ここで mark することで、サーバが知っているレールは描画距離に関係なく
            //   sweep を生き延び、sweep は本当に削除されたレールだけを落とす。
            markLiveRailMath(railMathKey);

            BlockPos otherBlockPos = Init.positionToBlockPos(otherPos);
            double otherAxis = BlockNode.getAngle(world.getBlockState(otherBlockPos)); // MTR標準ノードも軸として扱う
            double otherOffX = 0, otherOffY = 0, otherOffZ = 0;

            BlockEntity rawBe = world.getBlockEntity(otherBlockPos);
            if (rawBe != null && rawBe.data instanceof StraightNodeBlockEntity snbe && snbe.isBound()) {
                otherAxis = snbe.getAngleDegrees(); // 0~180 の軸
                otherOffX = snbe.getOffsetX();
                otherOffY = snbe.getOffsetY();
                otherOffZ = snbe.getOffsetZ();
                // 対向ノードも生軸を載せ、MTR が両端をネイティブ解決できるようにする。
                cacheNodeState(otherBlockPos, otherAxis, otherOffX, otherOffY, otherOffZ);
            }

            // ★ 動的に最適な出口を選択 (軸から +0° か +180° かを決める)
            double selfExit = NodeGeometry.chooseBestExit(selfAxis,
                    Math.toDegrees(Math.atan2(otherPos.getZ() - nodePos.getZ(), otherPos.getX() - nodePos.getX())));
            double otherExit = NodeGeometry.chooseBestExit(otherAxis,
                    Math.toDegrees(Math.atan2(nodePos.getZ() - otherPos.getZ(), nodePos.getX() - otherPos.getX())));

            double startRad = Math.toRadians(selfExit);
            double endRad = Math.toRadians(otherExit);
            double verticalRadius = rail.railMath.getVerticalRadius();
            Rail.Shape shape = rail.railMath.getShape();

            // ★ +0.5 は付けない（MTR の getPositionXZ が x,z に +0.5 を加算するため）。
            Vector startVec = new Vector(
                    nodePos.getX() + this.offsetX, nodePos.getY() + this.offsetY, nodePos.getZ() + this.offsetZ
            );
            Vector endVec = new Vector(
                    otherPos.getX() + otherOffX, otherPos.getY() + otherOffY, otherPos.getZ() + otherOffZ
            );

            if (rail.railMath instanceof IRailMathExtra mathExtra) {
                // ★ 描画側（RenderRailsMixin / RailMathMixin#bte$capturePositions）と
                //   同じ述語でソルバを選ぶ。レール 1 本につき曲線のソースは必ず 1 個にする。
                //   ネイティブ可（軸が 22.5 度グリッド上かつ offset が 0）なら MTR 純正に委ね、
                //   そうでない場合だけ BTE の精确解で上書きする。
                //   両方を無条件に書くと getPositionY の委譲先だけが変わり、
                //   水平形状は MTR / 高低形状は BTE という混線が起きうる。
                if (!isNativeRail(nodePos, otherPos)) {
                    // ★ 順序は指定しない。bte$enableBezier が MTR の正規順 (辞書順で小さい端が
                    //   position1) へ揃える。ここを nodePos 起点で固定すると、nodePos が大きい側の
                    //   レールだけ曲線が反転し、列車が逆走 / 反対ノードへ停車する。
                    mathExtra.bte$enableBezier(nodePos, startVec, startRad, otherPos, endVec, endRad, verticalRadius, shape);
                }
            }

            // ★ 経路探索へ同じ角度を publish する (bezier と MTR の保存角度の乖離を解消)
            // 両端を必ず設定する。BTE-BTE では両ノードが同じ値を書き込むので競合しない。
            if (((Object) rail) instanceof RailAngleOverride angleOverride) {
                angleOverride.bte$setAngleOverride(nodePos, selfExit);
                angleOverride.bte$setAngleOverride(otherPos, otherExit);
            }

            // ★ 差分書き込み: 内容が同じなら double[14] を作り直さず Map にも触らない。
            //   4 tick 周期 x 全ノードで毎回 new していたのが描画/経路探索を重くした主因だった。
            double[] existing = RAIL_MATH_DATA_MAP.get(railMathKey);
            if (!bte$matchesCurveData(existing, startVec, endVec, startRad, endRad, verticalRadius, shape, nodePos, otherPos)) {
                RAIL_MATH_DATA_MAP.put(railMathKey, new double[]{
                        startVec.x(), startVec.y(), startVec.z(),
                        endVec.x(), endVec.y(), endVec.z(),
                        startRad, endRad,
                        verticalRadius, shape.ordinal(),
                        nodePos.getX(), nodePos.getZ(),
                        otherPos.getX(), otherPos.getZ()
                });
            }
        }

        return true;
    }

    /** 保存済みの {@code double[14]} が今回計算した値と一致するか（完全一致でよい）。 */
    private static boolean bte$matchesCurveData(
            double[] d, Vector startVec, Vector endVec, double startRad, double endRad,
            double verticalRadius, Rail.Shape shape, Position nodePos, Position otherPos
    ) {
        if (d == null || d.length < 14) return false;
        return d[0] == startVec.x() && d[1] == startVec.y() && d[2] == startVec.z()
                && d[3] == endVec.x() && d[4] == endVec.y() && d[5] == endVec.z()
                && d[6] == startRad && d[7] == endRad
                && d[8] == verticalRadius && (int) d[9] == shape.ordinal()
                && d[10] == nodePos.getX() && d[11] == nodePos.getZ()
                && d[12] == otherPos.getX() && d[13] == otherPos.getZ();
    }

    public void unbind() {
        if (!isBound()) return;
        angleDegrees = UNBOUND_SENTINEL;
        markDirty2();
        syncBlockEntity();
        updateBezierDataOnly();
        markRefreshPending(SRC_EDIT);
    }

    public boolean isConnected() {
        World world = getWorld2();
        if (world == null) return false;
        BlockState state = world.getBlockState(getPos2());
        return IBlock.getStatePropertySafe(state, BlockNode.IS_CONNECTED);
    }

    private void syncBlockEntity() {
        World world = getWorld2();
        if (world != null && !world.isClient()) {
            ServerWorld sw = LoaderImpl.toServerWorld(world);
            if (sw != null) sw.getChunkManager().markForUpdate(getPos2());
        }
    }

    /** 変更後の NBT をクライアントへ配る。パケットハンドラから呼ぶ。 */
    public void syncToClients() {
        syncBlockEntity();
    }

    public void updateConnectedRails(boolean updateSimulation) {
        updateBezierDataOnly();
    }

    public static double normalize(double angle) {
        angle = angle % 360.0D;
        if (angle < 0) angle += 360.0D;
        return angle;
    }

    @Override
    public void readCompoundTag(CompoundTag tag) {
        if (tag.contains(KEY_ANGLE)) {
            angleDegrees = tag.getDouble(KEY_ANGLE);
        } else {
            angleDegrees = UNBOUND_SENTINEL;
        }

        if (tag.contains(KEY_OFFSET_X)) {
            offsetX = tag.getDouble(KEY_OFFSET_X);
            offsetY = tag.getDouble(KEY_OFFSET_Y);
            offsetZ = tag.getDouble(KEY_OFFSET_Z);
        } else {
            offsetX = offsetY = offsetZ = 0.0;
        }

// ★ 角度/offset を NODE_STATE へ公開する（サーバ・クライアント共通）。
        //
        // これがマルチプレイでライブ更新を成立させる鍵：
        //   角度変更 → bind() → syncBlockEntity() → NBT パケット → クライアントの
        //   readCompoundTag → ここで publishNodeState() → NODE_STATE 更新 →
        //   RenderRailsMixin が次フレームで注入する。
        //
        // クライアントは Rail を逆シリアライズして得られるだけで Rail.getAngles を
        // 呼ばないため、RailGetAnglesMixin / markNativeRail がクライアントでは一度も
        // 走らない。結果として NODE_STATE も NATIVE_RAILS も空のままで、
        // 「BTE が関与しているか」を描画 pass の入口で判定できず注入が起動しない。
        // NBT は同期経路として確実に届くので（カントがクライアントで反映されるのが証拠）、
        // ここで自前に公開する。
        publishNodeState();

        speedLimitOriginals.clear();
        if (tag.contains(KEY_SPEED_ORIGINALS)) {
            decodeSpeedOriginals(tag.getString(KEY_SPEED_ORIGINALS));
        }

        // カント: NBT が唯一の供給点。消えたレールは索引からも消して食い違いを防ぐ。
        final java.util.Set<String> previousCantRails = new java.util.HashSet<>(cantProfiles.keySet());
        cantProfiles.clear();
        if (tag.contains(KEY_CANT_PROFILES)) {
            decodeCantProfiles(tag.getString(KEY_CANT_PROFILES));
        }
        for (String railHexId : previousCantRails) {
            if (!cantProfiles.containsKey(railHexId)) {
                CantRegistry.remove(railHexId);
            }
        }
        for (Map.Entry<String, CantProfile> entry : cantProfiles.entrySet()) {
            CantRegistry.put(entry.getKey(), entry.getValue());
        }

        this.needsInitialUpdate = true;
        // ★ tick() は呼ばれないため、ここでサーバティックへの反映予約を登録する
        markRefreshPending(SRC_READ);
    }

    @Override
    public void writeCompoundTag(CompoundTag tag) {
        super.writeCompoundTag(tag);
        if (isBound()) {
            // ★ 修正: % 180.0 に戻し、「軸」として保存する
            double normalizedAngle = angleDegrees % 180.0;
            if (normalizedAngle < 0.0) normalizedAngle += 180.0;
            tag.putDouble(KEY_ANGLE, normalizedAngle);
        }

        if (offsetX != 0.0 || offsetY != 0.0 || offsetZ != 0.0) {
            tag.putDouble(KEY_OFFSET_X, offsetX);
            tag.putDouble(KEY_OFFSET_Y, offsetY);
            tag.putDouble(KEY_OFFSET_Z, offsetZ);
        }

        if (!speedLimitOriginals.isEmpty()) {
            tag.putString(KEY_SPEED_ORIGINALS, encodeSpeedOriginals());
        }

        if (!cantProfiles.isEmpty()) {
            tag.putString(KEY_CANT_PROFILES, encodeCantProfiles());
        }
    }

    /**
     * 退避データを {@code hexId:原値1:原値2;...} 形式の単一文字列へ符号化する。
     * <p>
     * MTR の {@code CompoundTag} はネストしたタグを直接格納する API を持たないため、
     * フラットに符号化する。rail hex id は 16進数のハイフン区切りのみを含み
     * 区切り文字と衝突しない。
     */
    private String encodeSpeedOriginals() {
        final StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, long[]> entry : speedLimitOriginals.entrySet()) {
            final long[] values = entry.getValue();
            if (values == null || values.length < 2) continue;
            if (builder.length() > 0) builder.append(';');
            builder.append(entry.getKey())
                    .append(':').append(values[0])
                    .append(':').append(values[1]);
        }
        return builder.toString();
    }

    private void decodeSpeedOriginals(String encoded) {
        if (encoded == null || encoded.isEmpty()) return;
        for (String record : encoded.split(";")) {
            final String[] parts = record.split(":");
            if (parts.length < 3) continue;
            try {
                speedLimitOriginals.put(parts[0], new long[]{
                        Long.parseLong(parts[1]),
                        Long.parseLong(parts[2])
                });
            } catch (NumberFormatException ignored) {
                // 壊れたレコードは読み飛ばす
            }
        }
    }

    /**
     * 速度制限の変更前の MTR 既定値を記録する。
     *
     * @param railHexId レールの hex id
     * @param speed1Kmh 変更前の端点1の制限速度 (km/h)
     * @param speed2Kmh 変更前の端点2の制限速度 (km/h)
     */
    public void rememberOriginalSpeedLimit(String railHexId, long speed1Kmh, long speed2Kmh) {
        if (railHexId == null || railHexId.isEmpty()) return;
        if (speedLimitOriginals.containsKey(railHexId)) return;
        speedLimitOriginals.put(railHexId, new long[]{speed1Kmh, speed2Kmh});
    }

    /**
     * 記録済みの既定値を返す。未記録なら {@code null}。
     */
    public long[] getOriginalSpeedLimit(String hexId) {
        return speedLimitOriginals.get(hexId);
    }

    /**
     * 既定値の記録を破棄する (「既定値に戻す」を完了した時点)。
     */
    public void forgetOriginalSpeedLimit(String hexId) {
        speedLimitOriginals.remove(hexId);
    }

    // ── カント ────────────────────────────────────────────────────────

    /**
     * レール 1 本のカントを設定する。{@link CantProfile#isNone()} の場合は削除と同義。
     * 変更はランタイム索引 {@link CantRegistry} にも即時反映する。
     */
    public void setCant(String railHexId, CantProfile profile) {
        if (railHexId == null || railHexId.isEmpty()) return;
        if (profile == null || profile.isNone()) {
            cantProfiles.remove(railHexId);
            CantRegistry.remove(railHexId);
        } else {
            cantProfiles.put(railHexId, profile);
            CantRegistry.put(railHexId, profile);
        }
    }

    /** @return 設定されたカント。未設定なら {@code null}。 */
    public CantProfile getCant(String railHexId) {
        if (railHexId == null || railHexId.isEmpty()) return null;
        return cantProfiles.get(railHexId);
    }

    private String encodeCantProfiles() {
        final StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, CantProfile> entry : cantProfiles.entrySet()) {
            final CantProfile profile = entry.getValue();
            if (profile == null) continue;
            if (builder.length() > 0) builder.append(';');
            builder.append(entry.getKey())
                    .append(':').append(profile.startDeg)
                    .append(':').append(profile.middleDeg)
                    .append(':').append(profile.endDeg);
        }
        return builder.toString();
    }

    private void decodeCantProfiles(String encoded) {
        if (encoded == null || encoded.isEmpty()) return;
        for (String record : encoded.split(";")) {
            final String[] parts = record.split(":");
            if (parts.length < 4) continue;
            try {
                final CantProfile profile = new CantProfile(
                        Float.parseFloat(parts[1]),
                        Float.parseFloat(parts[2]),
                        Float.parseFloat(parts[3])
                );
                if (!profile.isNone()) {
                    cantProfiles.put(parts[0], profile);
                }
            } catch (NumberFormatException ignored) {
                // 壊れたレコードは読み飛ばす
            }
        }
    }
}
