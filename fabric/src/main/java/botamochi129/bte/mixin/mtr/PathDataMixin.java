package botamochi129.bte.mixin.mtr;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.mtr.core.data.Data;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Rail;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PathData.class, remap = false)
public abstract class PathDataMixin {

    @Shadow public abstract org.mtr.core.data.Position getOrderedPosition1();
    @Shadow public abstract org.mtr.core.data.Position getOrderedPosition2();

    @Shadow(remap = false)
    private Rail rail;

    @Inject(method = "writePathCache(Lorg/mtr/core/data/Data;)V", at = @At("HEAD"), remap = false, cancellable = true)
    private void bte$writePathCache(Data data, CallbackInfo ci) {
        // ★ 必須: サーバー側ではクライアント専用クラスにアクセスしない
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;

        if (data.positionsToRail.isEmpty()) {
            try {
                final Data realData = org.mtr.mod.client.MinecraftClientData.getInstance();
                if (realData != null && realData != data) {
                    final Rail realRail = Data.tryGet(realData.positionsToRail, getOrderedPosition1(), getOrderedPosition2());
                    if (realRail != null) {
                        this.rail = realRail;
                        ci.cancel();
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    // getPosition のフックは削除済みのため、ここにはありません (正解です)
}