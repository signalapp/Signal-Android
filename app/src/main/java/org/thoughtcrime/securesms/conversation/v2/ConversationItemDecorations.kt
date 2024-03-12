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
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
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

  private var unreadState: UnreadState = UnreadState.None
    set(value) {
      field = value
      unreadViewHolder?.bind()
    }

  var currentItems: List<ConversationElement?> = emptyList()
    set(value) {
      field = value
      updateUnreadState(value)
    }

  var hasWallpaper: Boolean = hasWallpaper
    set(value) {
      field = value
      headerCache.values.forEach { it.updateForWallpaper() }
      unreadViewHolder?.updateForWallpaper()
    }

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    val viewHolder = parent.getChildViewHolder(view)

    if (viewHolder is ConversationTypingIndicatorAdapter.ViewHolder) {
      outRect.set(0, 0, 0, 0)
      return
    }

    val position = viewHolder.bindingAdapterPosition

    val unreadHeight = if (isFirstUnread(position)) {
      getUnreadViewHolder(parent).height
    } else {
      0
    }

    val dateHeaderHeight = if (hasHeader(position)) {
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
      val viewHolder = parent.getChildViewHolder(child)

      if (viewHolder is ConversationTypingIndicatorAdapter.ViewHolder) {
        continue
      }

      val bindingAdapterPosition = viewHolder.bindingAdapterPosition

      val unreadOffset = if (isFirstUnread(bindingAdapterPosition)) {
        val unread = getUnreadViewHolder(parent)
        unread.itemView.drawAsTopItemDecoration(c, parent, child)
        unread.height
      } else {
        0
      }

      if (hasHeader(bindingAdapterPosition)) {
        val headerView = getHeader(parent, currentItems[bindingAdapterPosition] as ConversationMessageElement).itemView
        headerView.drawAsTopItemDecoration(c, parent, child, unreadOffset)
      }
    }
  }

  /** Must be called before first setting of [currentItems] */
  fun setFirstUnreadCount(unreadCount: Int) {
    if (unreadState == UnreadState.None && unreadCount > 0) {
      unreadState = UnreadState.InitialUnreadState(unreadCount)
    }
  }

  /**
   * If [unreadState] is [UnreadState.InitialUnreadState] we need to determine the first unread timestamp based on
   * initial unread count.
   *
   * Once in [UnreadState.CompleteUnreadState], need to update the unread count based on new incoming messages since
   * the first unread timestamp. If an outgoing message is found in this range the unread state is cleared completely,
   * which causes the unread divider to be removed.
   */
  private fun updateUnreadState(items: List<ConversationElement?>) {
    val state: UnreadState = unreadState

    if (state is UnreadState.InitialUnreadState) {
      val firstUnread: ConversationMessageElement? = findFirstUnreadStartingAt(items, (state.unreadCount - 1).coerceIn(items.indices))
      val timestamp = firstUnread?.timestamp()
      if (timestamp != null) {
        unreadState = UnreadState.CompleteUnreadState(unreadCount = state.unreadCount, firstUnreadTimestamp = timestamp)
      }
    } else if (state is UnreadState.CompleteUnreadState) {
      var newUnreadCount = 0
      for (element in items) {
        if (element is ConversationMessageElement) {
          if (element.conversationMessage.messageRecord.isOutgoing) {
            unreadState = UnreadState.None
            break
          } else {
            newUnreadCount++
            if (element.timestamp() == state.firstUnreadTimestamp) {
              unreadState = state.copy(unreadCount = newUnreadCount)
              break
            }
          }
        }
      }
    }
  }

  private fun findFirstUnreadStartingAt(items: List<ConversationElement?>, startingIndex: Int): ConversationMessageElement? {
    val endingIndex = (startingIndex + 20).coerceAtMost(items.lastIndex)
    for (index in startingIndex..endingIndex) {
      val item = items[index] as? ConversationMessageElement
      if ((item?.conversationMessage?.messageRecord as? MmsMessageRecord)?.isRead == false) {
        return item
      }
    }
    return items[startingIndex] as? ConversationMessageElement
  }

  private fun isFirstUnread(bindingAdapterPosition: Int): Boolean {
    val state = unreadState

    return state is UnreadState.CompleteUnreadState &&
      state.firstUnreadTimestamp != null &&
      bindingAdapterPosition in currentItems.indices &&
      (currentItems[bindingAdapterPosition] as? ConversationMessageElement)?.timestamp() == state.firstUnreadTimestamp
  }

  private fun hasHeader(bindingAdapterPosition: Int): Boolean {
    val model = if (bindingAdapterPosition in currentItems.indices) {
      currentItems[bindingAdapterPosition]
    } else {
      null
    }

    if (model == null || model !is ConversationMessageElement) {
      return false
    }

    val previousPosition = bindingAdapterPosition + 1
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
      unreadViewHolder!!.itemView.layoutIn(parent)
      return unreadViewHolder!!
    }

    unreadViewHolder = UnreadViewHolder(parent)
    return unreadViewHolder!!
  }

  private fun ConversationMessageElement.timestamp(): Long {
    return if (scheduleMessageMode) {
      (conversationMessage.messageRecord as MmsMessageRecord).scheduledDate
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
      val unreadCount = (unreadState as? UnreadState.CompleteUnreadState)?.unreadCount ?: 0
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

  sealed class UnreadState {
    /** Unread state hasn't been initialized or there are 0 unreads upon entering the conversation */
    object None : UnreadState()

    /** On first load of data, there is at least 1 unread message but we don't know the 'position' in the list yet */
    data class InitialUnreadState(val unreadCount: Int) : UnreadState()

    /** We have at least one unread and know the timestamp of the first unread message and thus 'position' for the header */
    data class CompleteUnreadState(val unreadCount: Int, val firstUnreadTimestamp: Long? = null) : UnreadState()
  }
}
