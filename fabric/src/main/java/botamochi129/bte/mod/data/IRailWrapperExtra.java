package botamochi129.bte.mod.data;

/**
 * {@code MinecraftClientData$RailWrapper} へ BTE が差し込むための操作。
 *
 * <p>値は {@code RailWrapperMixin} が実装し、呼び出し側は {@code instanceof} で絞り込む。
 * {@code RailMath} に対する {@link IRailMathExtra} と同じ方針。
 *
 * <p>offset が非ゼロのレール（従来 in-place 注入に任せるもの）専用。
 * offset ゼロのレールは {@code RailCopyAnglesMixin} が MTR にネイティブ構築させるため、
 * AABB も MTR が正しく焼き込む。本 mixin はその場合でも並行動作で無害である。
 */
public interface IRailWrapperExtra {

    /**
     * 遮蔽カリング用 AABB（{@code startVector} / {@code endVector}）を、
     * 現在の {@link org.mtr.core.data.RailMath} の {@code minX..maxZ} へ同期する。
     *
     * <p>MTR は {@code RenderRails.lambda$render$1} から
     * {@code OcclusionCullingInstance.isAABBVisible(startVector, endVector, camera)} を呼ぶが、
     * その 2 つは {@code final} で {@code RailWrapper} 構築時に一度だけ決まる。
     * BTE の曲線は弦から膨らむので、箱を BTE 形状に合わせないと
     * 曲線の外側がカリングで切れる。
     */
    void bte$refreshBounds();
}