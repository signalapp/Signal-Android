/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.util.ProjectionList

/**
 * A conversation element that a user can either swipe or snapshot
 */
interface InteractiveConversationElement {
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
}
