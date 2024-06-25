/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stories.viewer.reply.reaction

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.children
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiUtil
import org.thoughtcrime.securesms.events.GroupCallReactionEvent
import kotlin.time.Duration.Companion.seconds

class MultiReactionBurstLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
  private val cooldownTimes = mutableMapOf<String, Long>()

  private var nextViewIndex = 0

  init {
    repeat(MAX_SIMULTANEOUS_REACTIONS) {
      val view = OnReactionSentView(context, layoutRes = R.layout.reaction_burst_view)
      addView(view)
    }
  }

  fun displayReactions(reactions: List<GroupCallReactionEvent>) {
    if (children.count() == 0) {
      throw IllegalStateException("You must add views before displaying reactions!")
    }

    reactions.filter {
      if (it.getExpirationTimestamp() < System.currentTimeMillis()) {
        return@filter false
      }

      val cutoffTimestamp = cooldownTimes[EmojiUtil.getCanonicalRepresentation(it.reaction)] ?: return@filter true

      return@filter cutoffTimestamp < it.timestamp
    }
      .groupBy { EmojiUtil.getCanonicalRepresentation(it.reaction) }
      .filter { it.value.groupBy { event -> event.sender }.size >= REACTION_COUNT_THRESHOLD }
      .values
      .map { it.sortedBy { event -> event.timestamp } }
      .sortedBy { it.last().timestamp }
      .take(MAX_SIMULTANEOUS_REACTIONS - cooldownTimes.filter { it.value > System.currentTimeMillis() }.size)
      .forEach {
        val reactionView = getNextReactionView()
        reactionView.playForEmoji(it.map { event -> event.reaction })
        val lastEvent = it.last()
        cooldownTimes[EmojiUtil.getCanonicalRepresentation(lastEvent.reaction)] = lastEvent.timestamp + cooldownDuration.inWholeMilliseconds
      }
  }

  private fun getNextReactionView(): OnReactionSentView {
    val v = getChildAt(nextViewIndex) as OnReactionSentView

    nextViewIndex = (nextViewIndex + 1) % MAX_SIMULTANEOUS_REACTIONS

    return v
  }

  companion object {
    private const val REACTION_COUNT_THRESHOLD = 3
    private const val MAX_SIMULTANEOUS_REACTIONS = 3
    private val cooldownDuration = 2.seconds
  }
}
