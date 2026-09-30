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
     * {@link #RAIL_MATH_DATA_MAP} のキー（{@code minX,minY,minZ,maxX,maxY,maxZ}）を組み立てる。
     * <p>端点の順序に依存しないよう、必ず小さい方を先に並べる。
     */
    public static String railMathKey(long x1, long y1, long z1, long x2, long y2, long z2) {
        StringBuilder sb = KEY_BUILDER.get();
        sb.setLength(0);
        sb.append(Math.min(x1, x2)).append(',')
                .append(Math.min(y1, y2)).append(',')
                .append(Math.min(z1, z2)).append(',')
                .append(Math.max(x1, x2)).append(',')
                .append(Math.max(y1, y2)).append(',')
                .append(Math.max(z1, z2));
        return sb.toString();
    }

    /** {@link Position} 2 点から {@link #railMathKey} を組み立てる。 */
    public static String railMathKey(Position p1, Position p2) {
        return railMathKey(p1.getX(), p1.getY(), p1.getZ(), p2.getX(), p2.getY(), p2.getZ());
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

        for (Map.Entry<Position, Rail> entry : railsAtPos.entrySet()) {
            Position otherPos = entry.getKey();
            Rail rail = entry.getValue();
            if (rail == null || rail.railMath == null) continue;

            String railMathKey = railMathKey(
                    nodePos.getX(), nodePos.getY(), nodePos.getZ(),
                    otherPos.getX(), otherPos.getY(), otherPos.getZ()
            );

            BlockPos otherBlockPos = Init.positionToBlockPos(otherPos);
            double otherAxis = BlockNode.getAngle(world.getBlockState(otherBlockPos)); // MTR標準ノードも軸として扱う
            double otherOffX = 0, otherOffY = 0, otherOffZ = 0;

            BlockEntity rawBe = world.getBlockEntity(otherBlockPos);
            if (rawBe != null && rawBe.data instanceof StraightNodeBlockEntity snbe && snbe.isBound()) {
                otherAxis = snbe.getAngleDegrees(); // 0~180 の軸
                otherOffX = snbe.getOffsetX();
                otherOffY = snbe.getOffsetY();
                otherOffZ = snbe.getOffsetZ();
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

            Vector startVec = new Vector(
                    nodePos.getX() + 0.5 + this.offsetX, nodePos.getY() + this.offsetY, nodePos.getZ() + 0.5 + this.offsetZ
            );
            Vector endVec = new Vector(
                    otherPos.getX() + 0.5 + otherOffX, otherPos.getY() + otherOffY, otherPos.getZ() + 0.5 + otherOffZ
            );

            if (rail.railMath instanceof IRailMathExtra mathExtra) {
                mathExtra.bte$enableBezier(startVec, startRad, endVec, endRad, verticalRadius, shape);
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
