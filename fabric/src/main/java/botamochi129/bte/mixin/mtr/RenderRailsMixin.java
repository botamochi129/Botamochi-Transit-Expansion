package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.StraightNodeBlock;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mod.render.RenderRails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(value = RenderRails.class, remap = false)
public abstract class RenderRailsMixin {

    @Inject(method = "renderNode", at = @At("HEAD"), cancellable = true)
    private static void bte$skipStraightNode(BlockState state, BlockPos pos, BooleanSupplier shouldRender, int light, CallbackInfo ci) {
        if (state.getBlock().data instanceof StraightNodeBlock) {
            ci.cancel();
        }
    }
}
