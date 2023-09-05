/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Px
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.util.ProjectionList

/**
 * A conversation element that a user can either swipe or snapshot
 */
interface InteractiveConversationElement : ChatColorsDrawable.ChatColorsDrawableInvalidator {
  val conversationMessage: ConversationMessage

  val root: ViewGroup
  val bubbleView: View
  val bubbleViews: List<View>
  val reactionsView: View
  val quotedIndicatorView: View?
  val replyView: View
  val contactPhotoHolderView: View?
  val badgeImageView: View?

  /**
   * Whether or not the given element is swipeable
   */
  fun disallowSwipe(latestDownX: Float, latestDownY: Float): Boolean

  /**
   * Gets the adapter position for this element. Since this element can either be a ConversationItem or a
   * ViewHolder, we require a delegate method.
   */
  fun getAdapterPosition(recyclerView: RecyclerView): Int

  /**
   * Note: Since we always clip out the view we want to display, we can ignore corners when providing this
   * projection list. This will prevent artifacts when we draw the bitmap.
   */
  fun getSnapshotProjections(coordinateRoot: ViewGroup, clipOutMedia: Boolean): ProjectionList

  fun getSnapshotProjections(coordinateRoot: ViewGroup, clipOutMedia: Boolean, outgoingOnly: Boolean): ProjectionList

  fun getSnapshotStrategy(): SnapshotStrategy?

  interface SnapshotStrategy {
    fun snapshot(canvas: Canvas)

    val snapshotMetrics: SnapshotMetrics
  }

  /**
   * Metrics describing how the snapshot is oriented.
   *
   * @param snapshotOffset      Describes the horizontal offset of the top-level that will be captured.
   *                            This is used to ensure the content is translated appropriately.
   * @param contextMenuPadding  Describes the distance between the edge of the view's container to the
   *                            edge of the content (for example, the bubble). This is used to position
   *                            the context menu.
   */
  data class SnapshotMetrics(
    @Px val snapshotOffset: Float,
    @Px val contextMenuPadding: Float
  )
}
