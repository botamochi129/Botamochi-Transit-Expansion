package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.data.IRailWrapperExtra;
import org.mtr.core.data.Rail;
import org.mtr.core.data.RailMath;
import org.mtr.libraries.com.logisticscraft.occlusionculling.util.Vec3d;
import org.mtr.mod.client.MinecraftClientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code MinecraftClientData$RailWrapper} の遮蔽カリング用 AABB を BTE 形状に追従させる。
 *
 * <p><b>なぜ必要か</b><br>
 * MTR の {@code RailWrapper} コンストラクタは {@code RailMath} の
 * {@code minX/minY/minZ/maxX/maxY/maxZ} を {@code Vec3d} 2 個にコピーして
 * {@code startVector} / {@code endVector} に保持する。この 2 つは {@code final} で、
 * コンストラクタの後は<b>一度も更新されない</b>。
 *
 * <p>そして遮蔽カリングは
 * {@code RenderRails.lambda$render$1} が {@code getRail()} を経由せず
 * <b>フィールドを直接 {@code getfield} して</b>
 * {@code OcclusionCullingInstance.isAABBVisible(startVector, endVector, camera)} を呼ぶ。
 * しかもこの判定はカリング用ワーカースレッドで非同期に走る。
 *
 * <p>{@code minX..maxZ} は MTR 本家が {@code RailMath.<init>} 内で
 * <b>MTR 純正（ブロック軸の直線）ジオメトリ</b>から算出する。BTE は曲線パラメータを
 * {@code RailMathMixin} で上書きし {@code RailMathMixin#bte$recomputeBoundingBox} で
 * {@code minX..maxZ} も BTE 形状に合わせて書き直すが、
 * <b>その値が {@code RailWrapper} に伝わる経路が存在しなかった</b>。
 * 結果としてカリング箱は弦（直線）のまま残り、BTE の曲線は弦から大きく膨らむので
 * 箱の外側部分が切られる。近距離では錐台が細く切れやすく「直線に見える」、
 * 遠距離では箱全体が錐台に入って正しく描画される、という症状になる。
 * {@code RailWrapper} は描画距離変更時などに作り直され、その瞬間に
 * {@code RailMath} の箱が BTE 反映済みかどうかで結果が変わるため、距離設定と
 * 結び付いて毎回変化した。
 *
 * <p><b>方針</b><br>
 * {@code RailMath} 側の箱が正しいことは {@code bte$recomputeBoundingBox} が保証する。
 * 本 mixin はそれを {@code RailWrapper} へ伝えるだけで、形状計算には触れない。
 * 毎フレーム {@link #bte$refreshBounds()} を呼ぶと、前回値を保持して 6 個の
 * {@code long} を比較し、<b>変化があったときだけ</b> {@code Vec3d} を 2 個割り当てる。
 * 定常状態では割り当てゼロ。
 *
 * <p>final フィールドへの参照代入は原子的であり、非同期カリングスレッドは
 * 旧値か新値のどちらかを通しで読む（参照の分割は起きない）。
 * {@code Vec3d} は不変の値オブジェクトとして扱われ、中身为書き換わることはない。
 */
@Mixin(value = MinecraftClientData.RailWrapper.class, remap = false)
public abstract class RailWrapperMixin implements IRailWrapperExtra {

    @Shadow private Rail rail;

    @Shadow @Mutable private Vec3d startVector;

    @Shadow @Mutable private Vec3d endVector;

    /** {@link #bte$refreshBounds()} が {@link #startVector} に詰めたときの {@code minX..maxZ}。 */
    @Unique private long bte$lastMinX;
    @Unique private long bte$lastMinY;
    @Unique private long bte$lastMinZ;
    @Unique private long bte$lastMaxX;
    @Unique private long bte$lastMaxY;
    @Unique private long bte$lastMaxZ;
    @Unique private boolean bte$boundsInitialized;

    /**
     * コンストラクタ直後に MTR 本家の手順で箱を作り直す。
     *
     * <p>本家の箱は {@code RailMath.<init>} 内の集計をそのまま写すだけなので、
     * {@code RailMathMixin#bte$capturePositions} が {@code RailMath.<init>} の RETURN で
     * 注入を済ませている場合は、ここで最初から BTE 形状の箱になる。
     */
    @Inject(method = "<init>(Lorg/mtr/core/data/Rail;Ljava/lang/String;)V", at = @At("RETURN"))
    private void bte$refreshBoundsOnConstruct(Rail rail, String hexId, CallbackInfo ci) {
        bte$refreshBounds();
    }

    /**
     * {@link RailMath} の現在の {@code minX..maxZ} を {@link #startVector} / {@link #endVector} へ反映する。
     *
     * <p>{@code RailMathMixin#bte$recomputeBoundingBox} は曲線が実際に再構築されたときだけ動くので、
     * 箱が動くのもその時だけになる。前回値との比較で短絡するので、
     * 箱が動かないフレームでは {@code Vec3d} を作らない。
     */
    @Unique
    @Override
    public void bte$refreshBounds() {
        final Rail currentRail = this.rail;
        if (currentRail == null) return;

        final RailMath railMath = currentRail.railMath;
        if (railMath == null) return;

        final long minX = railMath.minX;
        final long minY = railMath.minY;
        final long minZ = railMath.minZ;
        final long maxX = railMath.maxX;
        final long maxY = railMath.maxY;
        final long maxZ = railMath.maxZ;

        if (bte$boundsInitialized
                && minX == bte$lastMinX && minY == bte$lastMinY && minZ == bte$lastMinZ
                && maxX == bte$lastMaxX && maxY == bte$lastMaxY && maxZ == bte$lastMaxZ) {
            return;
        }

        this.startVector = new Vec3d(minX, minY, minZ);
        this.endVector = new Vec3d(maxX, maxY, maxZ);

        bte$lastMinX = minX;
        bte$lastMinY = minY;
        bte$lastMinZ = minZ;
        bte$lastMaxX = maxX;
        bte$lastMaxY = maxY;
        bte$lastMaxZ = maxZ;
        bte$boundsInitialized = true;
    }
}