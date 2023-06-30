/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.signal.core.util.dp
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.Projection
import org.thoughtcrime.securesms.util.isScheduled
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

/**
 * Determines the shape for a conversation item based off of the appearance context
 * and message data.
 */
class V2ConversationItemShape(
  private val conversationContext: V2ConversationContext
) {

  companion object {
    private var bigRadius: Float = 18f.dp
    private var smallRadius: Float = 4f.dp

    private var collapsedSpacing: Float = 1f.dp
    private var defaultSpacing: Float = 6f.dp

    private val clusterTimeout = 3.minutes
  }

  var corners: Projection.Corners = Projection.Corners(bigRadius)
    private set

  var bodyBubble: MaterialShapeDrawable = MaterialShapeDrawable(
    ShapeAppearanceModel.Builder().setAllCornerSizes(bigRadius).build()
  )
    private set

  /**
   * Sets the message spacing and corners based off the given information. This
   * updates the class state.
   */
  fun setMessageShape(
    isLtr: Boolean,
    currentMessage: MessageRecord,
    isGroupThread: Boolean,
    adapterPosition: Int
  ): MessageShape {
    val nextMessage: MessageRecord? = conversationContext.getNextMessage(adapterPosition)
    val previousMessage: MessageRecord? = conversationContext.getPreviousMessage(adapterPosition)

    if (isSingularMessage(currentMessage, previousMessage, nextMessage, isGroupThread)) {
      setBodyBubbleCorners(isLtr, bigRadius, bigRadius, bigRadius, bigRadius)
      return MessageShape.SINGLE
    } else if (isStartOfMessageCluster(currentMessage, previousMessage, isGroupThread)) {
      val bottomEnd = if (currentMessage.isOutgoing) smallRadius else bigRadius
      val bottomStart = if (currentMessage.isOutgoing) bigRadius else smallRadius
      setBodyBubbleCorners(isLtr, bigRadius, bigRadius, bottomEnd, bottomStart)
      return MessageShape.START
    } else if (isEndOfMessageCluster(currentMessage, nextMessage)) {
      val topStart = if (currentMessage.isOutgoing) bigRadius else smallRadius
      val topEnd = if (currentMessage.isOutgoing) smallRadius else bigRadius
      setBodyBubbleCorners(isLtr, topStart, topEnd, bigRadius, bigRadius)
      return MessageShape.END
    } else {
      val start = if (currentMessage.isOutgoing) bigRadius else smallRadius
      val end = if (currentMessage.isOutgoing) smallRadius else bigRadius
      setBodyBubbleCorners(isLtr, start, end, end, start)
      return MessageShape.MIDDLE
    }
  }

  private fun setBodyBubbleCorners(
    isLtr: Boolean,
    topStart: Float,
    topEnd: Float,
    bottomEnd: Float,
    bottomStart: Float
  ) {
    val newCorners = Projection.Corners(
      if (isLtr) topStart else topEnd,
      if (isLtr) topEnd else topStart,
      if (isLtr) bottomEnd else bottomStart,
      if (isLtr) bottomStart else bottomEnd
    )
    if (corners == newCorners) {
      return
    }

    corners = newCorners
    bodyBubble.shapeAppearanceModel = ShapeAppearanceModel.builder()
      .setTopLeftCornerSize(corners.topLeft)
      .setTopRightCornerSize(corners.topRight)
      .setBottomLeftCornerSize(corners.bottomLeft)
      .setBottomRightCornerSize(corners.bottomRight)
      .build()
  }

  private fun isSingularMessage(
    currentMessage: MessageRecord,
    previousMessage: MessageRecord?,
    nextMessage: MessageRecord?,
    isGroupThread: Boolean
  ): Boolean {
    return isStartOfMessageCluster(currentMessage, previousMessage, isGroupThread) && isEndOfMessageCluster(currentMessage, nextMessage)
  }

  private fun isStartOfMessageCluster(
    currentMessage: MessageRecord,
    previousMessage: MessageRecord?,
    isGroupThread: Boolean
  ): Boolean {
    if (previousMessage == null ||
      previousMessage.isUpdate ||
      !DateUtils.isSameDay(currentMessage.timestamp, previousMessage.timestamp) ||
      !isWithinClusteringTime(currentMessage, previousMessage) ||
      currentMessage.isScheduled() ||
      currentMessage.fromRecipient != previousMessage.fromRecipient
    ) {
      return true
    }

    return isGroupThread || currentMessage.isSecure != previousMessage.isSecure
  }

  private fun isEndOfMessageCluster(
    currentMessage: MessageRecord,
    nextMessage: MessageRecord?
  ): Boolean {
    if (nextMessage == null ||
      nextMessage.isUpdate ||
      !DateUtils.isSameDay(currentMessage.timestamp, nextMessage.timestamp) ||
      !isWithinClusteringTime(currentMessage, nextMessage) ||
      currentMessage.isScheduled() ||
      currentMessage.reactions.isNotEmpty()
    ) {
      return true
    }

    return currentMessage.fromRecipient != nextMessage.fromRecipient
  }

  private fun isWithinClusteringTime(currentMessage: MessageRecord, previousMessage: MessageRecord): Boolean {
    return abs(currentMessage.dateSent - previousMessage.dateSent) <= clusterTimeout.inWholeMilliseconds
  }

  enum class MessageShape(
    val topPadding: Float,
    val bottomPadding: Float
  ) {
    /**
     * This message stands alone.
     */
    SINGLE(defaultSpacing, defaultSpacing),

    /**
     * This message is the start of a cluster
     */
    START(defaultSpacing, collapsedSpacing),

    /**
     * This message is the end of a cluster
     */
    END(collapsedSpacing, defaultSpacing),

    /**
     * This message is in the middle of a cluster
     */
    MIDDLE(collapsedSpacing, collapsedSpacing)
  }
}
