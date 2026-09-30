package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.data.EnumHelperRecovery;
import org.mtr.core.generated.data.PathDataSchema;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * PathDataSchema のデシリアライズで呼ばれる {@code EnumHelper.valueOf} を差し替え、
 * 自由角度 Angle (phantom) を復元する。詳細は {@link EnumHelperRecovery} を参照。
 */
@Mixin(value = PathDataSchema.class, remap = false)
public abstract class PathDataSchemaAngleMixin {

    @Redirect(
            method = "<init>(Lorg/mtr/core/serializer/ReaderBase;)V",
            at = @At(value = "INVOKE", target = "Lorg/mtr/core/tool/EnumHelper;valueOf(Ljava/lang/Enum;Ljava/lang/String;)Ljava/lang/Enum;")
    )
    private Enum bte$recoverPhantomAngleOnPath(Enum defaultValue, String name) {
        return EnumHelperRecovery.valueOf(defaultValue, name);
    }
}
