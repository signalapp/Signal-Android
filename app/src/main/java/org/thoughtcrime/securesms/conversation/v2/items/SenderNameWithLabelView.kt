/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.isGone
import androidx.core.view.isVisible
import org.signal.core.ui.util.ThemeUtil
import org.signal.core.util.dp
import org.signal.core.util.isNotNullOrBlank
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabel
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible
import org.signal.core.ui.R as CoreUiR

/**
 * Displays a sender and their member label. The XML version of [MemberLabelPill] used in conversations
 */
class SenderNameWithLabelView(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {

  companion object {
    private val HORIZONTAL_SPACING = 4.dp
    private val VERTICAL_SPACING = 2.dp
  }

  private val nameView: EmojiTextView
  private val labelView: View
  private val labelEmojiView: EmojiTextView
  private val labelTextView: EmojiTextView
  private var isLabelWrapped = false

  init {
    inflate(context, R.layout.sender_name_with_label_view, this)

    nameView = findViewById(R.id.member_name)
    labelView = findViewById(R.id.member_label)
    labelEmojiView = findViewById(R.id.member_label_emoji)
    labelTextView = findViewById(R.id.member_label_text)
  }

  /**
   * Sets sender and label state. Intended for use in a RecyclerView.
   */
  fun bind(name: String, @ColorInt tintColor: Int, label: MemberLabel?) {
    val isDark = ThemeUtil.isDarkTheme(context)
    val labelBackgroundColor = Color(tintColor).copy(alpha = if (isDark) 0.32f else 0.10f).toArgb()
    val labelTextColor = if (isDark) {
      Color.White.copy(alpha = 0.25f).compositeOver(Color(tintColor))
    } else {
      Color.Black.copy(alpha = 0.30f).compositeOver(Color(tintColor))
    }.toArgb()

    bind(name, tintColor, label, labelTextColor, labelBackgroundColor)
  }

  /**
   * Variant of [bind] with explicit label text/background colors.
   */
  fun bind(name: String, @ColorInt tintColor: Int, label: MemberLabel?, @ColorInt labelTextColor: Int, @ColorInt labelBackgroundColor: Int) {
    nameView.text = name
    nameView.setTextColor(tintColor)

    if (label != null) {
      presentLabel(label)
      labelView.backgroundTintList = ColorStateList.valueOf(labelBackgroundColor)
      labelTextView.setTextColor(labelTextColor)
    }

    labelView.visible = label != null
  }

  /**
   * Used to update the colors in response to theme changes (e.g., wallpaper enabled/disabled).
   */
  fun updateColors(@ColorInt foregroundColor: Int, @ColorInt labelBackgroundColor: Int) {
    nameView.setTextColor(foregroundColor)
    labelView.backgroundTintList = ColorStateList.valueOf(labelBackgroundColor)
    labelTextView.setTextColor(foregroundColor)
  }

  fun applyQuoteAuthorStyle() {
    nameView.setTextAppearance(CoreUiR.style.Signal_Text_LabelLarge)
  }

  private fun presentLabel(label: MemberLabel) {
    labelEmojiView.visible = label.emoji.isNotNullOrBlank()
    labelEmojiView.text = label.emoji

    labelTextView.visible = label.text.isNotEmpty()
    labelTextView.text = label.displayText
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val horizontalPadding = paddingStart + paddingEnd
    val verticalPadding = paddingTop + paddingBottom
    val availableWidth = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
      Int.MAX_VALUE
    } else {
      MeasureSpec.getSize(widthMeasureSpec) - horizontalPadding
    }

    measureChildWithMargins(nameView, widthMeasureSpec, 0, heightMeasureSpec, 0)
    val nameWidth = nameView.outerWidth()
    val nameHeight = nameView.outerHeight()

    val contentWidth: Int
    val contentHeight: Int

    if (labelView.isGone) {
      contentWidth = nameWidth
      contentHeight = nameHeight
    } else {
      measureChildWithMargins(labelView, widthMeasureSpec, 0, heightMeasureSpec, 0)
      val labelWidth = labelView.outerWidth()
      val labelHeight = labelView.outerHeight()

      isLabelWrapped = nameWidth.toLong() + HORIZONTAL_SPACING + labelWidth > availableWidth
      if (isLabelWrapped) {
        contentWidth = maxOf(nameWidth, labelWidth)
        contentHeight = nameHeight + VERTICAL_SPACING + labelHeight
      } else {
        contentWidth = nameWidth + HORIZONTAL_SPACING + labelWidth
        contentHeight = maxOf(nameHeight, labelHeight)
      }
    }

    setMeasuredDimension(
      resolveSize(contentWidth + horizontalPadding, widthMeasureSpec),
      resolveSize(contentHeight + verticalPadding, heightMeasureSpec)
    )
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    if (!labelView.isVisible) {
      layoutChild(nameView, paddingStart, paddingTop)
    } else if (isLabelWrapped) {
      layoutChild(nameView, paddingStart, paddingTop)
      layoutChild(labelView, paddingStart, paddingTop + nameView.outerHeight() + VERTICAL_SPACING)
    } else {
      val rowHeight = maxOf(nameView.outerHeight(), labelView.outerHeight())
      layoutChild(nameView, paddingStart, paddingTop + (rowHeight - nameView.outerHeight()) / 2)
      layoutChild(labelView, paddingStart + nameView.outerWidth() + HORIZONTAL_SPACING, paddingTop + (rowHeight - labelView.outerHeight()) / 2)
    }
  }

  private fun layoutChild(child: View, start: Int, top: Int) {
    val params = child.layoutParams as MarginLayoutParams
    val childTop = top + params.topMargin
    val childStart = start + params.marginStart

    val left = if (ViewUtil.isRtl(child)) {
      width - childStart - child.measuredWidth
    } else {
      childStart
    }

    child.layout(left, childTop, left + child.measuredWidth, childTop + child.measuredHeight)
  }

  private fun View.outerWidth(): Int {
    val params = layoutParams as MarginLayoutParams
    return measuredWidth + params.marginStart + params.marginEnd
  }

  private fun View.outerHeight(): Int {
    val params = layoutParams as MarginLayoutParams
    return measuredHeight + params.topMargin + params.bottomMargin
  }

  override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = MarginLayoutParams(context, attrs)

  override fun generateDefaultLayoutParams(): LayoutParams = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

  override fun generateLayoutParams(p: LayoutParams): LayoutParams = MarginLayoutParams(p)

  override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams
}
