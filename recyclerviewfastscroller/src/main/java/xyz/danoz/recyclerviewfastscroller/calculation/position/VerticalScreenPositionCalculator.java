package xyz.danoz.recyclerviewfastscroller.calculation.position;

import xyz.danoz.recyclerviewfastscroller.calculation.VerticalScrollBoundsProvider;
import xyz.danoz.recyclerviewfastscroller.vertical.VerticalRecyclerViewFastScroller;

/**
 * Calculates the correct vertical Y position for a view based on scroll progress and given bounds
 */
public class VerticalScreenPositionCalculator {

    private final VerticalScrollBoundsProvider mVerticalScrollBoundsProvider;

    public VerticalScreenPositionCalculator(VerticalScrollBoundsProvider scrollBoundsProvider) {
        mVerticalScrollBoundsProvider = scrollBoundsProvider;
    }

    public float getYPositionFromScrollProgress(float scrollProgress) {
//        return Math.max(
//                mVerticalScrollBoundsProvider.getMinimumScrollY(),
//                Math.min(
//                        scrollProgress * mVerticalScrollBoundsProvider.getMaximumScrollY(),
//                        mVerticalScrollBoundsProvider.getMaximumScrollY()
//                )
//        );

        float ret = 0;
        float min1 = Math.min(scrollProgress * mVerticalScrollBoundsProvider.getMaximumScrollY(), mVerticalScrollBoundsProvider.getMaximumScrollY());
        float max1 = Math.max(mVerticalScrollBoundsProvider.getMinimumScrollY(), min1);
        ret = max1;

        // System.out.println("VerticalScreenPositionCalculator:" + "scrollProgress="+scrollProgress);
        // System.out.println("VerticalScreenPositionCalculator:" + "mVerticalScrollBoundsProvider.getMaximumScrollY="+mVerticalScrollBoundsProvider.getMaximumScrollY());
        // System.out.println("VerticalScreenPositionCalculator:" + "mVerticalScrollBoundsProvider.getMinimumScrollY="+mVerticalScrollBoundsProvider.getMinimumScrollY());
        // System.out.println("VerticalScreenPositionCalculator:" + "min1="+min1);
        // System.out.println("VerticalScreenPositionCalculator:" + "max1="+max1);

        return ret;
    }

}
