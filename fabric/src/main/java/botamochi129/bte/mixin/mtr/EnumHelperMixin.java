package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.data.AngleExtra;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.EnumHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EnumHelper.class, remap = false)
public interface EnumHelperMixin {

    /**
     * 自由角度 Angle (phantom) の復元。
     * <p>
     * MTR は Angle を enum 名で保存する: RailSchema.serializeData / PathDataSchema は
     * {@code angle.toString()} を書き、復号側の {@code EnumHelper.valueOf(Angle.values()[0], name)} は
     * {@code Enum.valueOf} に失敗すると先頭定数 (= Angle.E, 0 度) を返す。
     * phantom の名前は "D45.3" のような enum 定数名ではないので、保存/読み込みや
     * PacketUpdateData 経由の同期のたびに自由角度が 0 度へ化けていた。
     * <p>
     * "D" 始まりの名前を度数として解釈し、正規化 ({@link AngleExtra#canonicalize}) して
     * 同一キャッシュキーの Angle を返すため、シリアライズ前と同一のインスタンスに戻る。
     * <p>
     * {@code require = 0} なのは、interface の static メソッドへの注入が環境依存で失敗しうるため。
     * 失敗しても起動を落とさず、角度が 0 度に落ちる(旧挙動)だけ。
     */
    @Inject(method = "valueOf", at = @At("HEAD"), remap = false, cancellable = true, require = 0)
    private static void bte$recoverPhantomAngle(Enum defaultValue, String name, CallbackInfoReturnable<Enum> cir) {
        if (defaultValue instanceof Angle && name != null && name.length() > 1 && name.charAt(0) == 'D') {
            try {
                Angle recovered = AngleExtra.fromDegrees(Float.parseFloat(name.substring(1)));
                cir.setReturnValue(recovered);
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
