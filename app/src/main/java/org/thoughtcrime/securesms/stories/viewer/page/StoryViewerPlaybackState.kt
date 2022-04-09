package org.thoughtcrime.securesms.stories.viewer.page

data class StoryViewerPlaybackState(
  val areSegmentsInitialized: Boolean = false,
  val isUserTouching: Boolean = false,
  val isDisplayingForwardDialog: Boolean = false,
  val isDisplayingDeleteDialog: Boolean = false,
  val isDisplayingContextMenu: Boolean = false,
  val isDisplayingViewsAndRepliesDialog: Boolean = false,
  val isDisplayingDirectReplyDialog: Boolean = false,
  val isDisplayingCaptionOverlay: Boolean = false,
  val isUserScrollingParent: Boolean = false,
  val isSelectedPage: Boolean = false,
  val isDisplayingSlate: Boolean = false,
  val isFragmentResumed: Boolean = false,
  val isDisplayingLinkPreviewTooltip: Boolean = false,
  val isDisplayingReactionAnimation: Boolean = false,
  val isRunningSharedElementAnimation: Boolean = false
) {
  val hideChromeImmediate: Boolean = isRunningSharedElementAnimation

  val hideChrome: Boolean = isRunningSharedElementAnimation || isUserTouching

  val isPaused: Boolean = !areSegmentsInitialized ||
    isUserTouching ||
    isDisplayingCaptionOverlay ||
    isDisplayingForwardDialog ||
    isDisplayingDeleteDialog ||
    isDisplayingContextMenu ||
    isDisplayingViewsAndRepliesDialog ||
    isDisplayingDirectReplyDialog ||
    isDisplayingCaptionOverlay ||
    isUserScrollingParent ||
    !isSelectedPage ||
    isDisplayingSlate ||
    !isFragmentResumed ||
    isDisplayingLinkPreviewTooltip ||
    isDisplayingReactionAnimation ||
    isRunningSharedElementAnimation
}
