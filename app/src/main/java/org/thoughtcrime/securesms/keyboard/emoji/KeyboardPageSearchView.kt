package org.thoughtcrime.securesms.keyboard.emoji

import android.animation.Animator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.doAfterTextChanged
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.animation.AnimationCompleteListener
import org.thoughtcrime.securesms.animation.ResizeAnimation
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible

private const val REVEAL_DURATION = 250L

/**
 * Search bar to be used in the various keyboard views (emoji, sticker, gif)
 */
class KeyboardPageSearchView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

  var callbacks: Callbacks? = null

  private var state: State = State.HIDE_REQUESTED
  private var targetInputWidth: Int = -1

  private val navButton: AppCompatImageView
  private val clearButton: AppCompatImageView
  private val input: EditText

  init {
    inflate(context, R.layout.keyboard_pager_search_bar, this)

    navButton = findViewById(R.id.emoji_search_nav_icon)
    clearButton = findViewById(R.id.emoji_search_clear_icon)
    input = findViewById(R.id.emoji_search_entry)

    input.doAfterTextChanged {
      if (it.isNullOrEmpty()) {
        clearButton.setImageDrawable(null)
        clearButton.isClickable = false
      } else {
        clearButton.setImageResource(R.drawable.ic_x)
        clearButton.isClickable = true
      }

      if (it.isNullOrEmpty()) {
        callbacks?.onQueryChanged("")
      } else {
        callbacks?.onQueryChanged(it.toString())
      }
    }

    input.setOnFocusChangeListener { _, hasFocus ->
      if (hasFocus) {
        callbacks?.onFocusGained()
      } else {
        callbacks?.onFocusLost()
      }
    }

    clearButton.setOnClickListener { clearQuery() }

    context.obtainStyledAttributes(attrs, R.styleable.KeyboardPageSearchView, 0, 0).use { typedArray ->
      val showAlways: Boolean = typedArray.getBoolean(R.styleable.KeyboardPageSearchView_show_always, false)
      if (showAlways) {
        alpha = 1f
        state = State.SHOW_REQUESTED
      } else {
        alpha = 0f
        input.layoutParams = input.layoutParams.apply { width = 1 }
        state = State.HIDE_REQUESTED
      }

      input.hint = typedArray.getString(R.styleable.KeyboardPageSearchView_search_hint) ?: ""

      val backgroundTint = typedArray.getColor(R.styleable.KeyboardPageSearchView_search_bar_tint, ContextCompat.getColor(context, R.color.signal_background_primary))
      val backgroundTintList = ColorStateList.valueOf(backgroundTint)
      input.background = ColorDrawable(backgroundTint)
      ViewCompat.setBackgroundTintList(findViewById(R.id.emoji_search_nav), backgroundTintList)
      ViewCompat.setBackgroundTintList(findViewById(R.id.emoji_search_clear), backgroundTintList)

      val iconTint = typedArray.getColorStateList(R.styleable.KeyboardPageSearchView_search_icon_tint) ?: ContextCompat.getColorStateList(context, R.color.signal_icon_tint_primary)
      ImageViewCompat.setImageTintList(navButton, iconTint)
      ImageViewCompat.setImageTintList(clearButton, iconTint)
      input.setHintTextColor(iconTint)

      val clickOnly: Boolean = typedArray.getBoolean(R.styleable.KeyboardPageSearchView_click_only, false)
      if (clickOnly) {
        val clickIntercept: View = findViewById(R.id.keyboard_search_click_only)
        clickIntercept.visible = true
        clickIntercept.setOnClickListener { callbacks?.onClicked() }
      }
    }
  }

  fun showRequested(): Boolean = state == State.SHOW_REQUESTED

  fun enableBackNavigation(enable: Boolean = true) {
    navButton.setImageResource(if (enable) R.drawable.ic_arrow_left_24 else R.drawable.ic_search_24)
    if (enable) {
      navButton.setImageResource(R.drawable.ic_arrow_left_24)
      navButton.setOnClickListener { callbacks?.onNavigationClicked() }
    } else {
      navButton.setImageResource(R.drawable.ic_search_24)
      navButton.setOnClickListener(null)
    }
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    targetInputWidth = w - ViewUtil.dpToPx(32) - ViewUtil.dpToPx(90)
  }

  fun show() {
    if (state == State.SHOW_REQUESTED) {
      return
    }

    visibility = VISIBLE
    state = State.SHOW_REQUESTED

    post {
      animate()
        .setDuration(REVEAL_DURATION)
        .alpha(1f)
        .setListener(null)

      val resizeAnimation = ResizeAnimation(input, targetInputWidth, input.measuredHeight)
      resizeAnimation.duration = REVEAL_DURATION
      input.startAnimation(resizeAnimation)
    }
  }

  fun hide() {
    if (state == State.HIDE_REQUESTED) {
      return
    }

    state = State.HIDE_REQUESTED

    post {
      animate()
        .setDuration(REVEAL_DURATION)
        .alpha(0f)
        .setListener(object : AnimationCompleteListener() {
          override fun onAnimationEnd(animation: Animator) {
            visibility = INVISIBLE
          }
        })

      val resizeAnimation = ResizeAnimation(input, 1, input.measuredHeight)
      resizeAnimation.duration = REVEAL_DURATION
      input.startAnimation(resizeAnimation)
    }
  }

  fun presentForEmojiSearch() {
    ViewUtil.focusAndShowKeyboard(input)
    enableBackNavigation()
  }

  override fun clearFocus() {
    super.clearFocus()
    clearChildFocus(input)
  }

  fun clearQuery() {
    input.text.clear()
  }

  interface Callbacks {
    fun onFocusLost() = Unit
    fun onFocusGained() = Unit
    fun onNavigationClicked() = Unit
    fun onQueryChanged(query: String) = Unit
    fun onClicked() = Unit
  }

  enum class State {
    SHOW_REQUESTED,
    HIDE_REQUESTED
  }
}
