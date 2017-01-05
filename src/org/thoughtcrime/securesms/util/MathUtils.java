package org.thoughtcrime.securesms.util;

import android.graphics.PointF;
import android.support.annotation.NonNull;

public class MathUtils {

    /**
     * For more info:
     * <a href="http://math.stackexchange.com/questions/190111/how-to-check-if-a-point-is-inside-a-rectangle">StackOverflow: How to check point is in rectangle</a>
     *
     * @param pt point to check
     * @param v1 vertex 1 of the triangle
     * @param v2 vertex 2 of the triangle
     * @param v3 vertex 3 of the triangle
     * @return true if point (x, y) is inside the triangle
     */
    public static boolean pointInTriangle(@NonNull PointF pt, @NonNull PointF v1,
                                          @NonNull PointF v2, @NonNull PointF v3) {

        boolean b1 = crossProduct(pt, v1, v2) < 0.0f;
        boolean b2 = crossProduct(pt, v2, v3) < 0.0f;
        boolean b3 = crossProduct(pt, v3, v1) < 0.0f;

        return (b1 == b2) && (b2 == b3);
    }

    /**
     * calculates cross product of vectors AB and AC
     *
     * @param a beginning of 2 vectors
     * @param b end of vector 1
     * @param c enf of vector 2
     * @return cross product AB * AC
     */
    private static float crossProduct(@NonNull PointF a, @NonNull PointF b, @NonNull PointF c) {
        return crossProduct(a.x, a.y, b.x, b.y, c.x, c.y);
    }

    /**
     * calculates cross product of vectors AB and AC
     *
     * @param ax X coordinate of point A
     * @param ay Y coordinate of point A
     * @param bx X coordinate of point B
     * @param by Y coordinate of point B
     * @param cx X coordinate of point C
     * @param cy Y coordinate of point C
     * @return cross product AB * AC
     */
    private static float crossProduct(float ax, float ay, float bx, float by, float cx, float cy) {
        return (ax - cx) * (by - cy) - (bx - cx) * (ay - cy);
    }
}