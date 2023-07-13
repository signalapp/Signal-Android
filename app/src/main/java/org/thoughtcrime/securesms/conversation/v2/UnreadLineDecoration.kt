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
import org.thoughtcrime.securesms.util.drawAsItemDecoration
import org.thoughtcrime.securesms.util.layoutIn

/**
 * Renders the unread divider in a conversation list based on the unread count.
 */
class UnreadLineDecoration(hasWallpaper: Boolean) : RecyclerView.ItemDecoration() {

  private var unreadViewHolder: UnreadViewHolder? = null

  var unreadCount: Int = 0
    set(value) {
      field = value
      unreadViewHolder?.bind()
    }

  private val firstUnreadPosition: Int
    get() = unreadCount - 1

  var hasWallpaper: Boolean = hasWallpaper
    set(value) {
      field = value
      unreadViewHolder?.updateForWallpaper()
    }

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    if (unreadCount == 0) {
      super.getItemOffsets(outRect, view, parent, state)
      return
    }

    val position = parent.getChildAdapterPosition(view)

    val height = if (position == firstUnreadPosition) {
      getUnreadViewHolder(parent).height
    } else {
      0
    }

    outRect.set(0, height, 0, 0)
  }

  override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    for (layoutPosition in 0 until parent.childCount) {
      val child = parent.getChildAt(layoutPosition)
      val position = parent.getChildAdapterPosition(child)

      if (position == firstUnreadPosition) {
        getUnreadViewHolder(parent).itemView.drawAsItemDecoration(c, parent, child)
        break
      }
    }
  }

  private fun getUnreadViewHolder(parent: RecyclerView): UnreadViewHolder {
    if (unreadViewHolder != null) {
      return unreadViewHolder!!
    }

    unreadViewHolder = UnreadViewHolder(parent)
    return unreadViewHolder!!
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
