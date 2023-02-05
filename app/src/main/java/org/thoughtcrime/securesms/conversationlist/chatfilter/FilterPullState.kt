package org.thoughtcrime.securesms.conversationlist.chatfilter

/**
 * Represents the state of the filter pull.
 */
enum class FilterPullState {
  /**
   * The filter is not active. Releasing the filter will cause it to slide shut.
   * Pulling the filter to 100% will move to apex.
   */
  CLOSED,

  /**
   * The filter has been dragged all the way to the end of it's space. This is considered
   * the "apex" point. The only action here is that the user can release to move to the open state.
   */
  OPEN_APEX,

  /**
   * The filter has been dragged all the way to the apex, but the user started to drag back instead of
   * releasing the filter.
   */
  CANCELING,

  /**
   * The filter is being activated and the animation is running.
   */
  OPENING,

  /**
   * The filter is active and the animation has settled.
   */
  OPEN,

  /**
   * From the open position, the user has dragged to the apex again.
   */
  CLOSE_APEX,

  /**
   * The filter is being removed and the animation is running
   */
  CLOSING;
}
