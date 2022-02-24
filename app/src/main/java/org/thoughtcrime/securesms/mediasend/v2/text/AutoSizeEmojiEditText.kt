package org.thoughtcrime.securesms.mediasend.v2.text

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import androidx.core.view.doOnNextLayout
import org.signal.core.util.DimensionUnit
import org.signal.core.util.EditTextUtil
import org.thoughtcrime.securesms.components.emoji.EmojiEditText
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class AutoSizeEmojiEditText @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : EmojiEditText(context, attrs) {

  private val maxTextSize = DimensionUnit.DP.toPixels(32f)
  private val minTextSize = DimensionUnit.DP.toPixels(6f)
  private var lowerBounds = minTextSize
  private var upperBounds = maxTextSize

  private val sizeSet: MutableSet<Float> = mutableSetOf()

  private var beforeText: String? = null
  private var beforeCursorPosition = 0

  private val watcher: TextWatcher = object : TextWatcher {

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
      beforeText = s.toString()
      beforeCursorPosition = start
    }

    override fun afterTextChanged(s: Editable) {
      if (lineCount == 0) {
        doOnNextLayout {
          checkCountAndAddListener()
        }
      } else {
        checkCountAndAddListener()
      }
    }
  }

  init {
    EditTextUtil.addGraphemeClusterLimitFilter(this, 700)
    addTextChangedListener(watcher)
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    if (isInEditMode) return

    if (checkCountAndAddListener()) {
      // TODO [stories] infinite measure loop when font change pushes us over the line count limit
      measure(widthMeasureSpec, heightMeasureSpec)
      return
    }

    try {
      val operation = getNextAutoSizeOperation()
      val newSize = when (operation) {
        AutoSizeOperation.INCREASE -> {
          lowerBounds = textSize
          val midpoint = abs(lowerBounds - upperBounds) / 2f + lowerBounds
          min(maxTextSize, midpoint)
        }
        AutoSizeOperation.DECREASE -> {
          upperBounds = textSize
          val midpoint = abs(lowerBounds - upperBounds) / 2f + lowerBounds
          max(minTextSize, midpoint)
        }
        AutoSizeOperation.NONE -> return
      }

      if (abs(upperBounds - lowerBounds) < 1f) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, lowerBounds)
        return
      } else if (sizeSet.add(newSize) || operation == AutoSizeOperation.INCREASE) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize)
        measure(widthMeasureSpec, heightMeasureSpec)
      } else {
        return
      }
    } finally {
      upperBounds = maxTextSize
      lowerBounds = minTextSize
      sizeSet.clear()
    }
  }

  private fun getNextAutoSizeOperation(): AutoSizeOperation {
    if (lineCount == 0) {
      return AutoSizeOperation.NONE
    }

    val availableHeight = measuredHeight - paddingTop - paddingBottom
    if (availableHeight <= 0) {
      return AutoSizeOperation.NONE
    }

    val pixelsRequired = lineHeight * lineCount

    return if (pixelsRequired > availableHeight) {
      if (textSize > minTextSize) {
        AutoSizeOperation.DECREASE
      } else {
        AutoSizeOperation.NONE
      }
    } else if (pixelsRequired < availableHeight) {
      if (textSize < maxTextSize) {
        AutoSizeOperation.INCREASE
      } else {
        AutoSizeOperation.NONE
      }
    } else {
      AutoSizeOperation.NONE
    }
  }

  private fun checkCountAndAddListener(): Boolean {
    removeTextChangedListener(watcher)

    if (lineCount > 12) {
      setText(beforeText)
      setSelection(beforeCursorPosition)
      addTextChangedListener(watcher)
      return true
    }

    if (getNextAutoSizeOperation() != AutoSizeOperation.NONE) {
      requestLayout()
    }

    addTextChangedListener(watcher)
    return false
  }

  private enum class AutoSizeOperation {
    INCREASE,
    DECREASE,
    NONE
  }
}
