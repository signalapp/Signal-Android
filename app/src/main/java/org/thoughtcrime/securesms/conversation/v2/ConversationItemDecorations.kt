/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.graphics.Canvas
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.v2.data.ConversationMessageElement
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.drawAsTopItemDecoration
import org.thoughtcrime.securesms.util.layoutIn
import org.thoughtcrime.securesms.util.toLocalDate
import java.util.Locale

private typealias ConversationElement = MappingModel<*>

/**
 * Given the same list as used by the [ConversationAdapterV2], determines where date headers should be rendered
 * and manages adjusting the list accordingly.
 *
 * This is a converted and trimmed down version of [org.thoughtcrime.securesms.util.StickyHeaderDecoration].
 */
class ConversationItemDecorations(hasWallpaper: Boolean = false, private val scheduleMessageMode: Boolean = false) : RecyclerView.ItemDecoration() {

  private val headerCache: MutableMap<Long, DateHeaderViewHolder> = hashMapOf()
  private var unreadViewHolder: UnreadViewHolder? = null

  var unreadCount: Int = 0
    set(value) {
      field = value
      unreadViewHolder?.bind()
    }

  private val firstUnreadPosition: Int
    get() = unreadCount - 1

  var currentItems: MutableList<ConversationElement?> = mutableListOf()

  var hasWallpaper: Boolean = hasWallpaper
    set(value) {
      field = value
      headerCache.values.forEach { it.updateForWallpaper() }
      unreadViewHolder?.updateForWallpaper()
    }

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    val position = parent.getChildAdapterPosition(view)

    val unreadHeight = if (unreadCount > 0 && position == firstUnreadPosition) {
      getUnreadViewHolder(parent).height
    } else {
      0
    }

    val dateHeaderHeight = if (position in currentItems.indices && hasHeader(position)) {
      getHeader(parent, currentItems[position] as ConversationMessageElement).height
    } else {
      0
    }

    outRect.set(0, unreadHeight + dateHeaderHeight, 0, 0)
  }

  override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    val count = parent.childCount
    for (layoutPosition in 0 until count) {
      val child = parent.getChildAt(count - 1 - layoutPosition)
      val position = parent.getChildAdapterPosition(child)

      val unreadOffset = if (position == firstUnreadPosition) {
        val unread = getUnreadViewHolder(parent)
        unread.itemView.drawAsTopItemDecoration(c, parent, child)
        unread.height
      } else {
        0
      }

      if (hasHeader(position)) {
        val headerView = getHeader(parent, currentItems[position] as ConversationMessageElement).itemView
        headerView.drawAsTopItemDecoration(c, parent, child, unreadOffset)
      }
    }
  }

  private fun hasHeader(position: Int): Boolean {
    val model = if (position in currentItems.indices) {
      currentItems[position]
    } else {
      null
    }

    if (model == null || model !is ConversationMessageElement) {
      return false
    }

    val previousPosition = position + 1
    val previousDay: Long
    if (previousPosition in currentItems.indices) {
      val previousModel = currentItems[previousPosition]
      if (previousModel == null || previousModel !is ConversationMessageElement) {
        return true
      } else {
        previousDay = previousModel.toEpochDay()
      }
    } else {
      return false
    }

    return model.toEpochDay() != previousDay
  }

  private fun getHeader(parent: RecyclerView, model: ConversationMessageElement): DateHeaderViewHolder {
    val headerHolder: DateHeaderViewHolder = headerCache.getOrPut(model.toEpochDay()) {
      val view = LayoutInflater.from(parent.context).inflate(R.layout.conversation_item_header, parent, false)
      val holder = DateHeaderViewHolder(view)
      holder.bind(model)
      holder
    }

    headerHolder.itemView.layoutIn(parent)

    return headerHolder
  }

  private fun getUnreadViewHolder(parent: RecyclerView): UnreadViewHolder {
    if (unreadViewHolder != null) {
      return unreadViewHolder!!
    }

    unreadViewHolder = UnreadViewHolder(parent)
    return unreadViewHolder!!
  }

  private fun ConversationMessageElement.timestamp(): Long {
    return if (scheduleMessageMode) {
      (conversationMessage.messageRecord as MediaMmsMessageRecord).scheduledDate
    } else {
      conversationMessage.conversationTimestamp
    }
  }

  private fun ConversationMessageElement.toEpochDay(): Long {
    return timestamp().toLocalDate().toEpochDay()
  }

  private inner class DateHeaderViewHolder(val itemView: View) {
    private val date = itemView.findViewById<TextView>(R.id.text)

    val height: Int
      get() = itemView.height

    fun bind(model: ConversationMessageElement) {
      val dateText = DateUtils.getConversationDateHeaderString(itemView.context, Locale.getDefault(), model.timestamp())
      date.text = dateText
      updateForWallpaper()
    }

    fun updateForWallpaper() {
      if (hasWallpaper) {
        date.setBackgroundResource(R.drawable.wallpaper_bubble_background_18)
        date.setTextColor(ContextCompat.getColor(itemView.context, R.color.signal_colorNeutralInverse))
      } else {
        date.background = null
        date.setTextColor(ContextCompat.getColor(itemView.context, R.color.signal_colorOnSurfaceVariant))
      }
    }
  }

  private inner class UnreadViewHolder(parent: RecyclerView) {
    val itemView: View

    private val unreadText: TextView
    private val unreadDivider: View

    val height: Int
      get() = itemView.height

    init {
      itemView = LayoutInflater.from(parent.context).inflate(R.layout.conversation_item_last_seen, parent, false)
      unreadText = itemView.findViewById(R.id.text)
      unreadDivider = itemView.findViewById(R.id.last_seen_divider)

      bind()
      itemView.layoutIn(parent)
    }

    fun bind() {
      unreadText.text = itemView.context.resources.getQuantityString(R.plurals.ConversationAdapter_n_unread_messages, unreadCount, unreadCount)
      updateForWallpaper()
    }

    fun updateForWallpaper() {
      if (hasWallpaper) {
        unreadText.setBackgroundResource(R.drawable.wallpaper_bubble_background_18)
        unreadDivider.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.transparent_black_80))
      } else {
        unreadText.background = null
        unreadDivider.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.core_grey_45))
      }
    }
  }
}
