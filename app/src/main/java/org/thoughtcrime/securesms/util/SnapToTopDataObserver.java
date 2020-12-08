package org.thoughtcrime.securesms.util;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.signal.core.util.logging.Log;

import java.util.Objects;

/**
 * Helper class to scroll to the top of a RecyclerView when new data is inserted.
 * This works for both newly inserted data and moved data. It applies the following rules:
 *
 * <ul>
 *   <li>If the user is currently scrolled to some position, then we will not snap.</li>
 *   <li>If the user is currently dragging, then we will not snap.</li>
 *   <li>If the user has requested a scroll position, then we will only snap to that position.</li>
 * </ul>
 */
public class SnapToTopDataObserver extends RecyclerView.AdapterDataObserver {

  private static final String TAG = Log.tag(SnapToTopDataObserver.class);

  private final RecyclerView           recyclerView;
  private final LinearLayoutManager    layoutManager;
  private final Deferred               deferred;
  private final ScrollRequestValidator scrollRequestValidator;
  private final ScrollToTop            scrollToTop;

  public SnapToTopDataObserver(@NonNull RecyclerView recyclerView) {
    this(recyclerView, null, null);
  }

  public SnapToTopDataObserver(@NonNull RecyclerView recyclerView,
                               @Nullable ScrollRequestValidator scrollRequestValidator,
                               @Nullable ScrollToTop scrollToTop)
  {
    this.recyclerView           = recyclerView;
    this.layoutManager          = (LinearLayoutManager) recyclerView.getLayoutManager();
    this.deferred               = new Deferred();
    this.scrollRequestValidator = scrollRequestValidator;
    this.scrollToTop            = scrollToTop == null ? () -> layoutManager.scrollToPosition(0)
                                                      : scrollToTop;
  }

  /**
   * Requests a scroll to a specific position. This call will defer until the position is loaded or
   * becomes invalid.
   *
   * @param position The position to scroll to.
   */
  public void requestScrollPosition(int position) {
    buildScrollPosition(position).submit();
  }

  /**
   * Creates a ScrollRequestBuilder which can be used to customize a particular scroll request with
   * different callbacks. Don't forget to call `submit()`!
   *
   * @param position The position to scroll to.
   * @return         A ScrollRequestBuilder that must be submitted once you are satisfied with it.
   */
  @CheckResult(suggest = "#requestScrollPosition(int)")
  public ScrollRequestBuilder buildScrollPosition(int position) {
    return new ScrollRequestBuilder(position);
  }

  /**
   * Requests that instead of snapping to top, we should scroll to a specific position in the adapter.
   * It is up to the caller to ensure that the adapter will load the appropriate data, either by
   * invalidating and restarting the page load at the appropriate position or by utilizing
   * PagedList#loadAround(int).
   *
   * @param position                The position to scroll to.
   * @param onPerformScroll         Callback allowing the caller to perform the scroll themselves.
   * @param onScrollRequestComplete Notification that the scroll has completed successfully.
   * @param onInvalidPosition       Notification that the requested position has become invalid.
   */
  private void requestScrollPositionInternal(int position,
                                             @NonNull OnPerformScroll onPerformScroll,
                                             @NonNull Runnable onScrollRequestComplete,
                                             @NonNull Runnable onInvalidPosition)
  {
    Objects.requireNonNull(scrollRequestValidator, "Cannot request positions when SnapToTopObserver was initialized without a validator.");

    if (!scrollRequestValidator.isPositionStillValid(position)) {
      Log.d(TAG, "requestScrollPositionInternal(" + position + ") Invalid");
      onInvalidPosition.run();
    } else if (scrollRequestValidator.isItemAtPositionLoaded(position)) {
      Log.d(TAG, "requestScrollPositionInternal(" + position + ") Scrolling");
      onPerformScroll.onPerformScroll(layoutManager, position);
      onScrollRequestComplete.run();
    } else {
      Log.d(TAG, "requestScrollPositionInternal(" + position + ") Deferring");
      deferred.setDeferred(true);
      deferred.defer(() -> {
        Log.d(TAG, "requestScrollPositionInternal(" + position + ") Executing deferred");
        requestScrollPositionInternal(position, onPerformScroll, onScrollRequestComplete, onInvalidPosition);
      });
    }
  }

  @Override
  public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
    snapToTopIfNecessary(toPosition);
  }

  @Override
  public void onItemRangeInserted(int positionStart, int itemCount) {
    snapToTopIfNecessary(positionStart);
  }

  private void snapToTopIfNecessary(int newItemPosition) {
    if (deferred.isDeferred()) {
      deferred.setDeferred(false);
      return;
    }

    if (newItemPosition != 0                                            ||
        recyclerView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE ||
        recyclerView.canScrollVertically(layoutManager.getReverseLayout() ? 1 : -1))
    {
      return;
    }

    if (layoutManager.findFirstVisibleItemPosition() == 0) {
      Log.d(TAG, "Scrolling to top.");
      scrollToTop.scrollToTop();
    }
  }

  public interface ScrollRequestValidator {
    /**
     * This method is responsible for determining whether a given position is still a valid jump target.
     * @param position  The position to validate
     * @return          Whether the position is valid
     */
    boolean isPositionStillValid(int position);

    /**
     * This method is responsible for checking whether the desired position is available to be jumped to.
     * In the case of a PagedListAdapter, it is whether getItem returns a non-null value.
     * @param position  The position to check for.
     * @return          Whether or not the data for the given position is loaded.
     */
    boolean isItemAtPositionLoaded(int position);
  }

  public interface OnPerformScroll {
    /**
     * This method is responsible for actually performing the requested scroll. It is always called
     * immediately before the onScrollRequestComplete callback, and is always run via recyclerView.post(...)
     * so you don't have to do this yourself.
     *
     * By default, SnapToTopDataObserver will utilize layoutManager.scrollToPosition. This lets you modify that
     * behavior, and also gives you a chance to perform actions just before scrolling occurs.
     *
     * @param layoutManager The layoutManager containing your items.
     * @param position      The position to scroll to.
     */
    void onPerformScroll(@NonNull LinearLayoutManager layoutManager, int position);
  }

  /**
   * Method Object for scrolling to the top of a view, in case special handling is desired.
   */
  public interface ScrollToTop {
    void scrollToTop();
  }

  public final class ScrollRequestBuilder {
    private final int position;

    private OnPerformScroll onPerformScroll         = LinearLayoutManager::scrollToPosition;
    private Runnable        onScrollRequestComplete = () -> {};
    private Runnable        onInvalidPosition       = () -> {};

    public ScrollRequestBuilder(int position) {
      this.position = position;
    }

    @CheckResult
    public ScrollRequestBuilder withOnPerformScroll(@NonNull OnPerformScroll onPerformScroll) {
      this.onPerformScroll = onPerformScroll;
      return this;
    }

    @CheckResult
    public ScrollRequestBuilder withOnScrollRequestComplete(@NonNull Runnable onScrollRequestComplete) {
      this.onScrollRequestComplete = onScrollRequestComplete;
      return this;
    }

    @CheckResult
    public ScrollRequestBuilder withOnInvalidPosition(@NonNull Runnable onInvalidPosition) {
      this.onInvalidPosition = onInvalidPosition;
      return this;
    }

    public void submit() {
      requestScrollPositionInternal(position, onPerformScroll, onScrollRequestComplete, onInvalidPosition);
    }
  }
}
