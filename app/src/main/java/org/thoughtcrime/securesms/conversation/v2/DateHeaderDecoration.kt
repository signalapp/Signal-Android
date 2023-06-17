/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.graphics.Canvas
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.v2.data.ConversationMessageElement
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.toLocalDate
import java.util.Locale

private typealias ConversationElement = MappingModel<*>

/**
 * Given the same list as used by the [ConversationAdapterV2], determines where date headers should be rendered
 * and manages adjusting the list accordingly.
 *
 * This is a converted and trimmed down version of [org.thoughtcrime.securesms.util.StickyHeaderDecoration].
 */
class DateHeaderDecoration(hasWallpaper: Boolean = false, private val scheduleMessageMode: Boolean = false) : RecyclerView.ItemDecoration() {

  private val headerCache: MutableMap<Long, DateHeaderViewHolder> = hashMapOf()

  var currentItems: MutableList<ConversationElement?> = mutableListOf()

  var hasWallpaper: Boolean = hasWallpaper
    set(value) {
      field = value
      headerCache.values.forEach { it.updateForWallpaper() }
    }

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
    val position = parent.getChildAdapterPosition(view)

    val headerHeight = if (position in currentItems.indices && hasHeader(position)) {
      getHeader(parent, currentItems[position] as ConversationMessageElement).height
    } else {
      0
    }

    outRect.set(0, headerHeight, 0, 0)
  }

  override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    val count = parent.childCount
    for (layoutPosition in 0 until count) {
      val child = parent.getChildAt(parent.childCount - 1 - layoutPosition)
      val position = parent.getChildAdapterPosition(child)

      if (hasHeader(position)) {
        val headerView = getHeader(parent, currentItems[position] as ConversationMessageElement).itemView
        c.save()
        val left = parent.left
        val top = child.y.toInt() - headerView.height
        c.translate(left.toFloat(), top.toFloat())
        headerView.draw(c)
        c.restore()
      }
    }
  }

  private fun hasHeader(position: Int): Boolean {
    val model = currentItems[position]

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

    val headerView = headerHolder.itemView
    val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
    val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)
    val childWidth = ViewGroup.getChildMeasureSpec(widthSpec, parent.paddingLeft + parent.paddingRight, headerView.layoutParams.width)
    val childHeight = ViewGroup.getChildMeasureSpec(heightSpec, parent.paddingTop + parent.paddingBottom, headerView.layoutParams.height)
    headerView.measure(childWidth, childHeight)
    headerView.layout(0, 0, headerView.measuredWidth, headerView.measuredHeight)
    return headerHolder
  }

  private fun ConversationMessageElement.timestamp(): Long {
    return if (scheduleMessageMode) {
      (conversationMessage.messageRecord as MediaMmsMessageRecord).scheduledDate
    } else {
      conversationMessage.messageRecord.dateSent
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
}
