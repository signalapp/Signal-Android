package xyz.danoz.recyclerviewfastscroller;

import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;

/**
 * To be implemented by any object that scrolls a {@link RecyclerView}
 */
public interface RecyclerViewScroller {

    /**
     * What good is a RecyclerViewScroller without a {@link RecyclerView}?!
     * @param recyclerView to scroll using the scroller
     */
    public void setRecyclerView(RecyclerView recyclerView);

    /**
     * Since {@link OnScrollListener} is not implemented as an interface, RecyclerViewScrollers cannot implement this
     * interface, and in most cases, it makes no sense for them to extend a scroll listener. For this reason, we must
     * provide a listener that is intended to be fetched and set as a listener on a {@link RecyclerView}.
     *
     * This assumes that a scroller should to know when RecyclerViews are scrolled independently of scroller actions.
     *
     * @return this scroller's listener for a RecyclerView's scrolling.
     */
    public OnScrollListener getOnScrollListener();

    /**
     * Indicate to the scroller that it should scroll to a certain amount of scroll progress
     * @param scrollProgress the progress of the scroll expressed as a fraction from [0, 1]
     * @param fromTouch true if this scroll request was triggered by a touch
     */
    public void scrollTo(float scrollProgress, boolean fromTouch);

}
