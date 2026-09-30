package botamochi129.bte.mod.data;

import org.mtr.core.tool.Angle;
import org.mtr.core.tool.EnumHelper;

/**
 * MTR は Angle を enum 定数名で保存するため、自由角度 (phantom) の名前は
 * "D45.3" のような定数名ではない。復号側の
 * {@code EnumHelper.valueOf(Angle.values()[0], name)} は定数が見つからないため
 * 先頭定数 (Angle.E, 0 度) を返し、保存/読み込みや PacketUpdateData 経由の同期の
 * たびに自由角度が 0 度へ化けていた。
 * <p>
 * {@code EnumHelper} は interface かつ {@code valueOf} は static なので、Mixin では
 * そこへ直接注入できない (対象が interface なら mixin も interface である必要があり、
 * interface mixin は static handler を拒否する)。そのため生成的スキーマの
 * デシリアライザ (RailSchema / PathDataSchema) が呼ぶ
 * {@link EnumHelper#valueOf} をリダイレクトする形で復元する。
 * <p>
 * "D" 始まりでない名前や Angle 以外の enum は元の {@code EnumHelper.valueOf} に
 * 委ねるので、既存挙動は一切変わらない。
 */
public final class EnumHelperRecovery {

    private EnumHelperRecovery() {
    }

    public static Enum valueOf(Enum defaultValue, String name) {
        if (defaultValue instanceof Angle && name != null && name.length() > 1 && name.charAt(0) == 'D') {
            try {
                return AngleExtra.fromDegrees(Float.parseFloat(name.substring(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return EnumHelper.valueOf(defaultValue, name);
    }
}
