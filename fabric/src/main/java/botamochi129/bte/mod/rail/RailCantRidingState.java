package botamochi129.bte.mod.rail;

import org.mtr.mod.render.RenderVehicles;

import java.util.UUID;

/**
 * Detects whether an entity is riding an MTR vehicle.
 *
 * <p>MTR does not use vanilla riding: it keeps its own state in
 * {@link RenderVehicles#RIDING_PLAYER_INTERPOLATIONS} and moves the client player with
 * {@code VehicleRidingMovement#movePlayer}. {@code Entity#getVehicle()} therefore stays
 * null the whole time, so any gate built on it is permanently closed.
 *
 * <p>{@code RenderVehicles#renderPlayer} looks riders up in that same list by UUID, which
 * is what this mirrors.
 */
public final class RailCantRidingState {

    private RailCantRidingState() {
    }

    /**
     * @param uuid UUID of the entity to test, typically
     *             {@code MinecraftClient#getCameraEntity()}'s
     * @return true when MTR currently lists that entity as a rider
     */
    public static boolean isRiding(final UUID uuid) {
        if (uuid == null) {
            return false;
        }

        try {
            for (final RenderVehicles.RidingPlayerInterpolation interpolation : RenderVehicles.RIDING_PLAYER_INTERPOLATIONS) {
                if (interpolation != null && uuid.equals(interpolation.uuid)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // MTR internals differ between versions; treat a failure as "not riding"
            // rather than breaking the camera.
        }
        return false;
    }
}
