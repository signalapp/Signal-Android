package org.thoughtcrime.securesms.components

import android.view.View.OnClickListener
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.util.views.Stub

/**
 * State object for transfer controls.
 */
data class ThumbnailViewTransferControlsState(
  val isFocusable: Boolean = true,
  val isClickable: Boolean = true,
  val slide: Slide? = null,
  val downloadClickedListener: OnClickListener? = null,
  val showDownloadText: Boolean = true
) {

  fun withFocusable(isFocusable: Boolean): ThumbnailViewTransferControlsState = copy(isFocusable = isFocusable)
  fun withClickable(isClickable: Boolean): ThumbnailViewTransferControlsState = copy(isClickable = isClickable)
  fun withSlide(slide: Slide?): ThumbnailViewTransferControlsState = copy(slide = slide)
  fun withDownloadClickListener(downloadClickedListener: OnClickListener): ThumbnailViewTransferControlsState = copy(downloadClickedListener = downloadClickedListener)
  fun withDownloadText(showDownloadText: Boolean): ThumbnailViewTransferControlsState = copy(showDownloadText = showDownloadText)

  fun applyState(transferControlView: Stub<TransferControlView>) {
    if (transferControlView.resolved()) {
      transferControlView.get().isFocusable = isFocusable
      transferControlView.get().isClickable = isClickable
      if (slide != null) {
        transferControlView.get().setSlide(slide)
      }
      transferControlView.get().setDownloadClickListener(downloadClickedListener)
      transferControlView.get().setShowDownloadText(showDownloadText)
    }
  }
}
