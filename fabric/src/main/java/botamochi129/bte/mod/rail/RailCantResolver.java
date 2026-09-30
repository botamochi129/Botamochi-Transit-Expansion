package botamochi129.bte.mod.rail;

import org.mtr.core.data.Rail;
import org.mtr.mod.client.MinecraftClientData;

import java.util.Map;

/**
 * Resolves the cant angle of the nearest cant-bearing rail for an arbitrary world position.
 *
 * <p>Only rails registered in {@link CantRegistry} are considered, so the cost stays
 * bounded regardless of how many rails exist in the world.
 *
 * <p>Sign convention: the cant angle is expressed in the rail's canonical direction
 * ({@code position1 -> position2}). Callers that move along a rail must flip the sign
 * themselves; see {@link #cantDegreesForHeading}.
 */
public final class RailCantResolver {

    /**
     * Maximum squared distance from the query point to a rail sample for the rail to be
     * considered a candidate. Vehicles legitimately overshoot a rail's end by a block or
     * two while transitioning between nodes, so this must be generous. 8 blocks keeps the
     * neighbourhood small enough to avoid cross-talk between parallel rails.
     */
    private static final double MAX_DISTANCE_SQ = 64.0D;

    /** Returned when no cant-bearing rail is near the query point. */
    private static final double[] NONE = {0.0D, 0.0D, 0.0D, Double.MAX_VALUE, 0.0D};

    private RailCantResolver() {
    }

    /**
     * Cant angle (degrees) at the given position, in the rail's canonical direction.
     * Returns 0 when no cant-bearing rail is nearby.
     */
    public static float cantDegreesAt(double x, double y, double z) {
        return (float) sampleAt(x, y, z)[0];
    }

    /**
     * Cant angle and canonical forward direction (XZ) of the nearest cant-bearing rail.
     *
     * <p>Selection is by nearest sample point, not by bounding box. A vehicle overshoots
     * the end of a rail by a block or two when it hands over to the next node, and a
     * strict bounding-box test rejects the rail exactly in that region, which made the
     * roll collapse to zero for most of the ride.
     *
     * @return {@code [cantDegrees, forwardX, forwardZ, distanceSq, progress]}, or
     *         {@code [0, 0, 0, MAX, 0]} when nothing is within {@link #MAX_DISTANCE_SQ}.
     */
    public static double[] sampleAt(double x, double y, double z) {
        final MinecraftClientData data = MinecraftClientData.getInstance();
        if (data == null || data.railIdMap == null) {
            return NONE;
        }

        final Map<String, CantProfile> profiles = CantRegistry.all();
        if (profiles.isEmpty()) {
            return NONE;
        }

        final Map<String, Rail> railIdMap = data.railIdMap;
        double[] best = NONE;
        double bestDistance = MAX_DISTANCE_SQ;

        for (Map.Entry<String, CantProfile> entry : profiles.entrySet()) {
            final CantProfile profile = entry.getValue();
            if (profile == null || profile.isNone()) {
                continue;
            }
            final Rail rail = railIdMap.get(entry.getKey());
            if (rail == null) {
                continue;
            }

            final double[] sample = RailCantSampler.sampleOnRail(rail, x, y, z);
            if (sample == null || sample[3] >= bestDistance) {
                continue;
            }

            bestDistance = sample[3];
            best = new double[]{profile.interpolateDegrees(sample[0]), sample[1], sample[2], sample[3], sample[0]};
        }

        return best;
    }

    /**
     * Cant angle oriented for a traveller moving along {@code (headingX, headingZ)}.
     *
     * <p>Prefer {@link RailCantDirectionTracker}: on a curve the rail tangent rotates
     * through a wide range, so this dot product can cross zero mid-rail and the roll
     * would swap sides partway along the ride. This method remains for cases with no
     * travel history, such as a stationary camera.
     */
    public static float cantDegreesForHeading(double x, double y, double z, double headingX, double headingZ) {
        final double[] sample = sampleAt(x, y, z);
        float cant = (float) sample[0];
        if (cant == 0.0F) {
            return 0.0F;
        }
        if ((sample[1] * headingX + sample[2] * headingZ) < 0.0D) {
            cant = -cant;
        }
        return cant;
    }
}
