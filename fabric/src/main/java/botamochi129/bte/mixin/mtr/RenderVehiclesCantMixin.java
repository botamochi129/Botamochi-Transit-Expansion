package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.rail.RailCantDirectionTracker;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mod.render.PositionAndRotation;
import org.mtr.mod.render.RenderVehicles;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vehicle / riding player roll injection driven by the active rail's cant.
 *
 * <p>{@link PositionAndRotation} has no roll slot, so the rotation is appended as a
 * final transform step. {@code StoredMatrixTransformations#add} appends to a list that
 * {@code transform} replays in order, so the cant composes with MTR's own built-in
 * banking ({@code rotateZDegrees(oscillationAmount)}) rather than being overwritten by it.
 *
 * <h2>Why this redirects call sites instead of the factory method</h2>
 * {@code getStoredMatrixTransformations} is a shared static factory called from three
 * unrelated places with three different positions:
 * <ul>
 *   <li>{@code lambda$render$14} — the vehicle body, once per car</li>
 *   <li>{@code renderPlayer} — the riding player's interior</li>
 *   <li>{@code lambda$render$5} — bogies and other parts</li>
 * </ul>
 * Hooking the factory itself gave all three the same direction-tracking state, so the
 * stored progress jumped between objects within a single frame and the measured direction
 * was meaningless. Riding adds the {@code renderPlayer} calls on top of the body calls,
 * which is why the roll collapsed only while riding. Each call site now has its own
 * stream in {@link RailCantDirectionTracker}.
 *
 * <p>This file depends on the method and lambda names of MTR 4.0.5+1.20.1. If a target
 * moves, {@code require = 1} makes startup fail loudly rather than silently rendering
 * the vehicle upright.
 */
@Mixin(value = RenderVehicles.class, remap = false)
public abstract class RenderVehiclesCantMixin {

    @Unique
    private static final String TRANSFORM_FACTORY = "Lorg/mtr/mod/render/RenderVehicles;getStoredMatrixTransformations(ZLorg/mtr/mod/render/PositionAndRotation;D)Lorg/mtr/mod/render/StoredMatrixTransformations;";

    @Unique
    private static final String RENDER_POSITION_FACTORY = "Lorg/mtr/mod/render/RenderVehicles;getRenderPositionAndRotation(Lorg/mtr/mapping/holder/Vector3d;Ljava/lang/Double;Lorg/mtr/mod/render/PositionAndRotation;Lorg/mtr/mod/render/PositionAndRotation;Lorg/mtr/mapping/holder/Vector3d;)Lorg/mtr/mod/render/PositionAndRotation;";

    /**
     * Frame boundary. {@code RenderVehicles#render} runs once per rendered frame, before
     * any vehicle transform is built, so it is the point where per-frame tracking state
     * has to be rolled over.
     */
    @Inject(method = "render(JLorg/mtr/mapping/holder/Vector3d;)V", at = @At("HEAD"), require = 1)
    private static void bte$beginRenderFrame(long tickDelta, Vector3d camera, CallbackInfo ci) {
        RailCantDirectionTracker.beginFrame();
    }

    @Redirect(
            method = "lambda$render$14",
            at = @At(value = "INVOKE", target = TRANSFORM_FACTORY),
            require = 1
    )
    private static StoredMatrixTransformations bte$applyBodyRoll(
            boolean useOffset,
            PositionAndRotation positionAndRotation,
            double oscillationAmount
    ) {
        return bte$applyCant(RailCantDirectionTracker.STREAM_BODY, useOffset, positionAndRotation, oscillationAmount);
    }

    @Redirect(
            method = "renderPlayer",
            at = @At(value = "INVOKE", target = TRANSFORM_FACTORY),
            require = 1
    )
    private static StoredMatrixTransformations bte$applyPlayerRoll(
            boolean useOffset,
            PositionAndRotation positionAndRotation,
            double oscillationAmount
    ) {
        return bte$applyCant(RailCantDirectionTracker.STREAM_PLAYER, useOffset, positionAndRotation, oscillationAmount);
    }

    @Redirect(
            method = "lambda$render$5",
            at = @At(value = "INVOKE", target = TRANSFORM_FACTORY),
            require = 1
    )
    private static StoredMatrixTransformations bte$applyBogieRoll(
            boolean useOffset,
            PositionAndRotation positionAndRotation,
            double oscillationAmount
    ) {
        return bte$applyCant(RailCantDirectionTracker.STREAM_BOGIE, useOffset, positionAndRotation, oscillationAmount);
    }

    /**
     * Captures the absolute position of the car being rendered. While the player rides,
     * MTR feeds the {@code getStoredMatrixTransformations} a render-relative position that
     * a world-space rail lookup can never match, but this {@code getRenderPositionAndRotation}
     * invocation still receives the original absolute car {@code PositionAndRotation} as its
     * 4th argument. Stashing it lets {@code cantFor} fall back to it.
     */
    @Redirect(
            method = "lambda$render$14",
            at = @At(value = "INVOKE", target = RENDER_POSITION_FACTORY),
            require = 1
    )
    private static PositionAndRotation bte$captureBodyPosition(
            org.mtr.mapping.holder.Vector3d viewPosition,
            Double tickDelta,
            PositionAndRotation ridePosition,
            PositionAndRotation carPosition,
            org.mtr.mapping.holder.Vector3d offset
    ) {
        return bte$capturePosition(RailCantDirectionTracker.STREAM_BODY, viewPosition, tickDelta, ridePosition, carPosition, offset);
    }

    @Redirect(
            method = "renderPlayer",
            at = @At(value = "INVOKE", target = RENDER_POSITION_FACTORY),
            require = 1
    )
    private static PositionAndRotation bte$capturePlayerPosition(
            org.mtr.mapping.holder.Vector3d viewPosition,
            Double tickDelta,
            PositionAndRotation ridePosition,
            PositionAndRotation playerPosition,
            org.mtr.mapping.holder.Vector3d offset
    ) {
        return bte$capturePosition(RailCantDirectionTracker.STREAM_PLAYER, viewPosition, tickDelta, ridePosition, playerPosition, offset);
    }

    @Redirect(
            method = "lambda$render$5",
            at = @At(value = "INVOKE", target = RENDER_POSITION_FACTORY),
            require = 1
    )
    private static PositionAndRotation bte$captureBogiePosition(
            org.mtr.mapping.holder.Vector3d viewPosition,
            Double tickDelta,
            PositionAndRotation ridePosition,
            PositionAndRotation bogiePosition,
            org.mtr.mapping.holder.Vector3d offset
    ) {
        return bte$capturePosition(RailCantDirectionTracker.STREAM_BOGIE, viewPosition, tickDelta, ridePosition, bogiePosition, offset);
    }

    @Unique
    private static PositionAndRotation bte$capturePosition(
            final String stream,
            final org.mtr.mapping.holder.Vector3d viewPosition,
            final Double tickDelta,
            final PositionAndRotation ridePosition,
            final PositionAndRotation absolutePosition,
            final org.mtr.mapping.holder.Vector3d offset
    ) {
        if (absolutePosition != null && absolutePosition.position != null) {
            RailCantDirectionTracker.stashAbsolutePosition(
                    stream,
                    absolutePosition.position.x(),
                    absolutePosition.position.y(),
                    absolutePosition.position.z()
            );
        }
        return RenderVehicles.getRenderPositionAndRotation(viewPosition, tickDelta, ridePosition, absolutePosition, offset);
    }

    /**
     * Builds the original transform, then appends the cant as a final Z rotation.
     *
     * <p>Calling the factory from here invokes the unredirected method, so the original
     * MTR transforms are preserved and the cant is added on top of them.
     */
    @Unique
    private static StoredMatrixTransformations bte$applyCant(
            final String stream,
            final boolean useOffset,
            final PositionAndRotation positionAndRotation,
            final double oscillationAmount
    ) {
        final StoredMatrixTransformations original =
                RenderVehicles.getStoredMatrixTransformations(useOffset, positionAndRotation, oscillationAmount);
        if (original == null || positionAndRotation == null || positionAndRotation.position == null) {
            return original;
        }

        final RailCantDirectionTracker.Result result = RailCantDirectionTracker.cantFor(
                stream,
                positionAndRotation.position.x(),
                positionAndRotation.position.y(),
                positionAndRotation.position.z()
        );
        if (result.cant == 0.0F) {
            return original;
        }

        final StoredMatrixTransformations adjusted = original.copy();
        final float roll = result.cant;
        adjusted.add(graphicsHolder -> graphicsHolder.rotateZDegrees(roll));
        return adjusted;
    }
}
