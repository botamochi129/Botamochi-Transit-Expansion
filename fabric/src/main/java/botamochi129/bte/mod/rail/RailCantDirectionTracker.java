package botamochi129.bte.mod.rail;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the cant angle for a traveller (vehicle, riding player interior, bogie, camera)
 * from its world position.
 *
 * <p>The superelevation of a canted rail is a physical property of the rail itself: the
 * world leans the same way regardless of which direction a vehicle travels over it.
 * Earlier designs derived the sign from the direction of travel (progress delta) and
 * flipped it when the traveller ran the rail backwards, which made an observed vehicle
 * tilt the wrong way on the return trip. The angle is therefore taken directly from the
 * rail profile at the traveller's position with no sign flip for travel direction.
 *
 * <h2>Streams</h2>
 * State is keyed by a <em>stream</em> that must correspond to exactly one object which
 * moves continuously. MTR calls
 * {@code RenderVehicles#getStoredMatrixTransformations} from three unrelated places
 * (vehicle body, riding player interior, bogie), so each call site uses its own stream.
 *
 * <h2>Absolute positions</h2>
 * While the player rides, MTR feeds {@code getStoredMatrixTransformations} coordinates
 * that are relative to the render view (the {@code PositionAndRotation} built by
 * {@code getRenderPositionAndRotation} when a view is present), which a world-space rail
 * lookup can never match. The render call sites still hold the original absolute position,
 * so they stash it here right before the transform is built. {@link #cantFor} prefers that
 * stashed position over the (relative) position it is passed.
 */
public final class RailCantDirectionTracker {

    /**
     * Stream for the vehicle body. MTR builds one transform per car, and the gangway
     * transform is derived from the same car, so both share this stream.
     */
    public static final String STREAM_BODY = "body";

    /** Stream for the riding player's interior transform. */
    public static final String STREAM_PLAYER = "player";

    /** Stream for bogies and other vehicle parts. */
    public static final String STREAM_BOGIE = "bogie";

    /** Stream for the camera. */
    public static final String STREAM_CAMERA = "camera";

    /**
     * Absolute positions stashed by the render call sites, keyed by stream. Kept across
     * frames: it is overwritten right before each transform is built, and only read by the
     * {@code cantFor} call made from the very same transform build.
     */
    private static final Map<String, double[]> ABSOLUTE_POSITIONS = new HashMap<>();

    private static int currentFrame;

    private RailCantDirectionTracker() {
    }

    /**
     * Result of a cant query.
     *
     * @param cant     cant angle, in degrees, oriented for the rail's own lean (world-space,
     *                 independent of travel direction)
     * @param reliable always true; kept for call sites that branched on reliability
     * @param progress progress along the canonical direction that was used
     */
    public static final class Result {

        public static final Result ZERO = new Result(0.0F, false, 0.0D);

        public final float cant;
        public final boolean reliable;
        public final double progress;

        Result(final float cant, final boolean reliable, final double progress) {
            this.cant = cant;
            this.reliable = reliable;
            this.progress = progress;
        }
    }

    /**
     * Records the absolute world position of the object a stream is about to transform.
     * Called from a handler on MTR's {@code getRenderPositionAndRotation} invocation, whose
     * 4th argument is the original absolute {@code PositionAndRotation}.
     */
    public static void stashAbsolutePosition(final String stream, final double x, final double y, final double z) {
        ABSOLUTE_POSITIONS.put(stream, new double[]{x, y, z});
    }

    /** Advances the frame counter. Called once per rendered frame. */
    public static void beginFrame() {
        currentFrame++;
    }

    public static int currentFrame() {
        return currentFrame;
    }

    public static Result cantFor(final String stream, final double x, final double y, final double z) {
        final double[] absolute = ABSOLUTE_POSITIONS.get(stream);
        final double qx = absolute != null ? absolute[0] : x;
        final double qy = absolute != null ? absolute[1] : y;
        final double qz = absolute != null ? absolute[2] : z;

        final double[] sample = RailCantResolver.sampleAt(qx, qy, qz);
        if (sample == null || sample[0] == 0.0D) {
            return Result.ZERO;
        }

        return new Result((float) sample[0], true, sample[4]);
    }

    /** Drops all stored state. Call on world change or disconnect. */
    public static void clear() {
        ABSOLUTE_POSITIONS.clear();
    }
}