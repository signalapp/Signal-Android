package org.thoughtcrime.securesms.sharing

import androidx.recyclerview.widget.RecyclerView

internal object ShareFlowConstants {

  private const val ADD_DURATION = 60L
  private const val REMOVE_DURATION = 60L
  private const val MOVE_DURATION = 125L
  private const val CHANGE_DURATION = 125L

  @JvmStatic
  fun applySelectedContactsRecyclerAnimationSpeeds(
    itemAnimator: RecyclerView.ItemAnimator
  ) {
    itemAnimator.addDuration = ADD_DURATION
    itemAnimator.removeDuration = REMOVE_DURATION
    itemAnimator.moveDuration = MOVE_DURATION
    itemAnimator.changeDuration = CHANGE_DURATION
  }
}
