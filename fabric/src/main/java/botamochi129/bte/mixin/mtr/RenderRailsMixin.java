package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.IRailMathExtra;
import botamochi129.bte.mod.data.IRailWrapperExtra;
import botamochi129.bte.mod.data.NodeGeometry;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;
import org.mtr.mapping.holder.BlockEntity;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mod.Init;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.render.RenderRails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderRails.class, remap = false)
public abstract class RenderRailsMixin {

    /** 生存判定は何フレームに 1 回行うか。描画 pass は毎フレーム走るので過剰な走査は避ける。 */
    private static final int SWEEP_EVERY = 60;
    private static int bte$frameCounter = 0;

    /**
     * 描画の直前に BTE ノードの.Exit 角度を {@link StraightNodeBlockEntity#RAIL_MATH_DATA_MAP} と
     * {@link IRailMathExtra} へ反映する。
     * <p>
     * ★ 軽量化: 旧実装は毎フレーム「全レール」に対して {@code atan2} 2 本と
     * {@code rail.getStartAngle} 2 本を計算してから BTE ノードの有無を判定していた。
     * MTR の线路の大半は標準ノードだけなので、その計算はほぼ無駄になる。
     * 判定を「両端が束縛済み BTE ノードか」の {@link BlockEntity} 取得だけに先行させ、
     * 該当しないレールは即 return する。
     */
    @Inject(method = "render", at = @At("HEAD"))
    private static void bte$patchRailsBeforeRender(CallbackInfo ci) {
        // ★ プレビュー用ジオメトリは「生成された同じフレーム内」にRail.copy の
        //   RailMath <init> から回収されるだけなので、pass の先頭で必ず捨てる。
        //   （world が null の早期 return より前に置かないと枠が残り続ける）
        StraightNodeBlockEntity.clearPreviewRailGeometry();

        ClientWorld world = MinecraftClient.getInstance().getWorldMapped();
        if (world == null) return;

        // 生存判定は定期実行。削除の根拠は「描画距離外」ではなく
        // 「最後に publish/観測されてから StraightNodeBlockEntity.ENTRY_TTL_MILLIS 経過」なので、
        // ここでは時刻の記録と失効の走査だけを行う（フレーム単位の live set は持たない）。
        final boolean sweeping = (++bte$frameCounter % SWEEP_EVERY) == 0;

        MinecraftClientData.getInstance().positionsToRail.forEach((pos1, map) -> {
            map.forEach((pos2, rail) -> {
                if (rail == null || rail.railMath == null) return;

                BlockPos p1 = Init.positionToBlockPos(pos1);
                BlockPos p2 = Init.positionToBlockPos(pos2);

// ★ MTR ネイティブ解決済みのレールは in-place 注入しない。判定は下段で行う。
                //   RailGetAnglesMixin が Rail.getAngles の引数を差し替えた結果、
                //   MTR が RailMath の final（h1/k1/r1/.../minX..maxZ）を正しく焼き込んでいる。
                //   ここで注入するとその正しい値を上書きし直し、
                //   RailWrapper の遮蔽カリング AABB や closeTo の導出元が乖離して
                //   位置依存の描画の揺れが復活する。
                //
                //   ただしこれは恒久フラグではない。ノード軸が 22.5 度グリッドの外にある、
                //   あるいはサブブロック offset が 0 でなくなった瞬間に false へ落ち、
                //   毎フレームの判定がそのまま BTE の注入経路へ入る。
                //   （昔はこの判定が「一度 true れると二度と false に戻らない」ため、
                //   ノード軸を回しても形が更新されず再入場まで直らなかった。）
                final String railKey = StraightNodeBlockEntity.railMathKey(
                        p1.getX(), p1.getY(), p1.getZ(), p2.getX(), p2.getY(), p2.getZ()
                );

                // 既に publish 済みの記述子。bte$enableBezier の条件判定と
                // MTR 標準ノード端の退出角の供給に共用する。
                double[] existing = StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.get(railKey);

                // ★ 入口の短絡。BTE が一度も触れていないレール（標準ノードだけの線路）は
                //   世界照会も注入判定も要らないので、ここで弾く。旧実装は native 判定を
                //   ここに置いていたが、native 判定は NODE_STATE を参照するため
                //   NODE_STATE が未投入の直後（参加直後・チャンク退出時）には必ず false となり、
                //   「MTR 純正のまま使う」安全側の既定が崩れて BTE を注入していた。
                //   キー参照 2 回だけで済み、world.getBlockEntity も呼ばない。
                if (existing == null && !StraightNodeBlockEntity.wasMarkedNativeRail(railKey)) return;

                // ★ 端ごとの解決: ライブな BE を優先し、無ければノード状態キャッシュを使う ──
                //   チャンクのロード範囲はプレイヤー中心なので、長いレールの端が範囲外に出ると
                //   ClientWorld#getBlockEntity が null を返す。旧実装はここで即 MTR 標準軸へ
                //   フォールバックしていたため、同じレールが「プレイヤーの位置によって別の曲線」
                //   で描画されていた（ノード付近では正しく、真ん中に歩くと軌道が変わる）。
                //   解決順を「live → cached」にし、どちらも無い端だけを MTR 標準軸へ倒す。
                //
                //   ★ ただし positionsToRail は描画距離で絞っていない（MinecraftClientData.sync() が
                //     全レールを積む）ため、範囲外のレールでもこの反復は走る。
                //     MTR の ClientWorld#getBlockEntity は ClientChunkManager#getChunk が
                //     null を返すと NPE になるので、未ロード_chunk を見分ける必要がある。
                //     ここでは「例外 = live ではない」とみなして保存値へ落とすだけでよい。
                BlockEntity be1 = null;
                BlockEntity be2 = null;
                try {
                    be1 = world.getBlockEntity(p1);
                    be2 = world.getBlockEntity(p2);
                } catch (RuntimeException ignored) {
                    // チャンクマップ半径外。live 解決は諦め、キャッシュ経路へフォールバックする
                }

                StraightNodeBlockEntity sn1 = (be1 != null && be1.data instanceof StraightNodeBlockEntity s) ? s : null;
                StraightNodeBlockEntity sn2 = (be2 != null && be2.data instanceof StraightNodeBlockEntity s) ? s : null;

                // [0]=angleDeg, [1]=offX, [2]=offY, [3]=offZ, [4]=bound
                final double[] st1 = new double[5];
                final double[] st2 = new double[5];

                final boolean live1 = (sn1 != null && sn1.isBound());
                final boolean live2 = (sn2 != null && sn2.isBound());

                if (live1) {
                    st1[0] = sn1.getAngleDegrees();
                    st1[1] = sn1.getOffsetX(); st1[2] = sn1.getOffsetY(); st1[3] = sn1.getOffsetZ();
                    st1[4] = 1;
                    StraightNodeBlockEntity.cacheNodeState(p1, st1[0], st1[1], st1[2], st1[3]);
                } else {
                    StraightNodeBlockEntity.getCachedNodeState(p1, st1);
                }

                if (live2) {
                    st2[0] = sn2.getAngleDegrees();
                    st2[1] = sn2.getOffsetX(); st2[2] = sn2.getOffsetY(); st2[3] = sn2.getOffsetZ();
                    st2[4] = 1;
                    StraightNodeBlockEntity.cacheNodeState(p2, st2[0], st2[1], st2[2], st2[3]);
                } else {
                    StraightNodeBlockEntity.getCachedNodeState(p2, st2);
                }

                final boolean bound1 = st1[4] != 0;
                final boolean bound2 = st2[4] != 0;
                if (!bound1 && !bound2) return;

                // ★ ネイティブ判定は「ここ」で行う。
                //   isNativeRail は現在のノード軸（NODE_STATE）を参照するので、
                //   live/cache の解決（上の cacheNodeState）を済ませてから問う必要がある。
                //   先に判定すると NODE_STATE が未投入の直後に必ず false となり、
                //   MTR 純正で正しいレールまで BTE の曲線を上書きしてしまう。
                if (StraightNodeBlockEntity.isNativeRail(pos1, pos2)) return;

                double geo = Math.toDegrees(Math.atan2(p2.getZ() - p1.getZ(), p2.getX() - p1.getX()));
                double reverseGeo = Math.toDegrees(Math.atan2(p1.getZ() - p2.getZ(), p1.getX() - p2.getX()));

                // ★ MTR 標準ノード端の退出角は world から取らない。
                //   ClientWorld#getBlockState はチャンクマップ半径外で air を返すため
                //   BlockNode.getAngle が 90 度（＝MTR 純正の直線）になり、
                //   プレイヤー位置で同じレールの曲線が変わっていた。
                //   正しい値は既にサーバの updateBezierDataOnly() が chooseBestExit まで適用して
                //   RAIL_MATH_DATA_MAP へ publish 済みなので、その保存値を端点組の向きに合わせて読み戻す。
                final double[] cachedExit = new double[2];
                final boolean hasCachedExit = StraightNodeBlockEntity.getCachedExitAngles(railKey, p1, p2, cachedExit);
                // 保存値が無い（参加直後 / sweep 後の再 publish 待ち）なら、90 度で書かず
                // MTR 純正のまま待つ。publish は最大 REFRESH_INTERVAL(4 tick) で届く。
                if ((!bound1 || !bound2) && !hasCachedExit) return;

                final double startRad = bound1
                        ? Math.toRadians(NodeGeometry.chooseBestExit(st1[0], geo))
                        : cachedExit[0];
                final double endRad = bound2
                        ? Math.toRadians(NodeGeometry.chooseBestExit(st2[0], reverseGeo))
                        : cachedExit[1];

                final double offX1 = bound1 ? st1[1] : 0;
                final double offY1 = bound1 ? st1[2] : 0;
                final double offZ1 = bound1 ? st1[3] : 0;
                final double offX2 = bound2 ? st2[1] : 0;
                final double offY2 = bound2 ? st2[2] : 0;
                final double offZ2 = bound2 ? st2[3] : 0;

                double verticalRadius = rail.railMath.getVerticalRadius();
                Rail.Shape shape = rail.railMath.getShape();

                bte$diagNode(p1, live1, st1, p2, live2, st2, bound1, bound2);

                // ★ +0.5 は付けない。MTR の getPositionXZ が x,z に +0.5（ブロック中心）を
                //   加算するため、ここで足すと注入ジオメトリが 0.5 ブロックずれる。
                //   ブロック整数 + サブブロック offset のまま渡す。
                Vector startVec = new Vector(p1.getX() + offX1, p1.getY() + offY1, p1.getZ() + offZ1);
                Vector endVec = new Vector(p2.getX() + offX2, p2.getY() + offY2, p2.getZ() + offZ2);

                if (sweeping) StraightNodeBlockEntity.markLiveRailMath(railKey);

                // ★ 差分書き込み: 既に同じ値が入っていれば double[14] と Map を触らない。
                //   毎フレーム new していた分を、値が動いたときだけにする。
                //   existing は上で退出角の供給にも使った同じ参照。
                final boolean dataChanged = (existing == null || existing.length < 14
                        || existing[0] != startVec.x() || existing[1] != startVec.y() || existing[2] != startVec.z()
                        || existing[3] != endVec.x() || existing[4] != endVec.y() || existing[5] != endVec.z()
                        || existing[6] != startRad || existing[7] != endRad
                        || existing[8] != verticalRadius || (int) existing[9] != shape.ordinal()
                        || existing[10] != p1.getX() || existing[11] != p1.getZ()
                        || existing[12] != p2.getX() || existing[13] != p2.getZ());
                if (dataChanged) {
                    StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.put(railKey, new double[]{
                            startVec.x(), startVec.y(), startVec.z(),
                            endVec.x(), endVec.y(), endVec.z(),
                            startRad, endRad,
                            verticalRadius, shape.ordinal(),
                            (double) p1.getX(), (double) p1.getZ(),
                            (double) p2.getX(), (double) p2.getZ()
                    });
                }

                if (rail.railMath instanceof IRailMathExtra mathExtra) {
                    // ★ 曲線の再構築も同じ判定でゲートする。
                    //   以前はここが無条件だったため、map は更新されないまま
                    //   RailMath インスタンスの bte$arcCurve だけが毎フレーム差し替わり、
                    //   描画がフレームごとに別の一本になっていた（間隔ずれ・列車の常時ガタつき）。
                    //   bte$isBezierEnabled() が false のままなら新規 Rail 実体 or チャンク再読込なので
                    //   map が同値でも必ず 1 回は構築する。
                    if (dataChanged || !mathExtra.bte$isBezierEnabled()) {
                        // 順序は bte$enableBezier が MTR の正規順に揃える
                        mathExtra.bte$enableBezier(pos1, startVec, startRad, pos2, endVec, endRad, verticalRadius, shape);
                    }
                }
            });
        });

        // ★ RailWrapper の遮蔽カリング箱を BTE 形状へ同期する。
        //   上の反復で RailMath の minX..maxZ が書き換わった直後に伝播させる。
        //   これをしないと MTR の final Vec3d（startVector/endVector）が弦のまま残り、
        //   曲線の膨らみがカリングで切れて「近づくと直線に見える」になる。
        //   sweep 周期ではなく毎フレーム呼ぶ。箱は 6 個の long 比較で短絡し、
        //   動かないフレームは Vec3d を作らないので、走査コストは定数倍にとどまる。
        MinecraftClientData.getInstance().railWrapperList.values().forEach(wrapper -> {
            if (wrapper instanceof IRailWrapperExtra extra) extra.bte$refreshBounds();
        });

        if (sweeping) {
            // 診断を sweep 周期（≒1 秒）ごとに再武装させる。永久に 6 本で止まると
            // 「歩いてノード端がチャンク外に出た後」の cached 参照を観測できないため。
            bte$diagCount = 0;
            StraightNodeBlockEntity.sweepRailMath();
            StraightNodeBlockEntity.sweepNodeState();
        }
    }

    // ── 診断（TEMPORARY: 実測確認後に削除する）──────────────────────
    // 検証したいのは「チャンク未ロードの端が NODE_STATE から正しく引けているか」だけなので、
    // (nodePos, live|cached, angleDeg, offX/Z) の4項目に絞る。
    private static final String DIAG_TAG = "[BTE-DIAG]";
    private static int bte$diagCount = 0;
    private static final int DIAG_MAX = 6;

    @Unique
    private static void bte$diagNode(BlockPos p1, boolean live1, double[] st1,
                                     BlockPos p2, boolean live2, double[] st2,
                                     boolean bound1, boolean bound2) {
        if (bte$diagCount >= DIAG_MAX) return;
        bte$diagCount++;
        System.out.println(DIAG_TAG + " node"
                + " A=(" + p1.getX() + "," + p1.getY() + "," + p1.getZ() + ")"
                + " " + bte$diagSrc(live1, st1, bound1)
                + " B=(" + p2.getX() + "," + p2.getY() + "," + p2.getZ() + ")"
                + " " + bte$diagSrc(live2, st2, bound2));
    }

    @Unique
    private static String bte$diagSrc(boolean live, double[] st, boolean bound) {
        if (!bound) return "MTR";
        return (live ? "live" : "cached")
                + String.format(java.util.Locale.ROOT, " ang=%.2f off=%.3f/%.3f", st[0], st[1], st[3]);
    }
}
