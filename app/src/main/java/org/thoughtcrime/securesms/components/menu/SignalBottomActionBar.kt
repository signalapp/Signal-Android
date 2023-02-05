package org.thoughtcrime.securesms.components.menu

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * A bar that displays a set of action buttons. Intended as a replacement for ActionModes, this gives you a simple interface to add a bunch of actions, and
 * the bar itself will handle putting things in the overflow and whatnot.
 *
 * Overflow items are rendered in a [SignalContextMenu].
 */
class SignalBottomActionBar(context: Context, attributeSet: AttributeSet) : LinearLayout(context, attributeSet) {

  val items: MutableList<ActionItem> = mutableListOf()

  val enterAnimation: Animation by lazy {
    AnimationUtils.loadAnimation(context, R.anim.slide_fade_from_bottom).apply {
      duration = 250
      interpolator = FastOutSlowInInterpolator()
    }
  }

  val exitAnimation: Animation by lazy {
    AnimationUtils.loadAnimation(context, R.anim.slide_fade_to_bottom).apply {
      duration = 250
      interpolator = FastOutSlowInInterpolator()
    }
  }

  init {
    orientation = HORIZONTAL
    setBackgroundResource(R.drawable.signal_bottom_action_bar_background)
    elevation = 20f
  }

  fun setItems(items: List<ActionItem>) {
    this.items.clear()
    this.items.addAll(items)
    present(this.items)
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)

    if (w != oldw) {
      present(items)
    }
  }

  private fun present(items: List<ActionItem>) {
    if (width == 0) {
      return
    }

    val wasLayoutRequested = isLayoutRequested

    val widthDp: Float = ViewUtil.pxToDp(width.toFloat())
    val minButtonWidthDp = 80
    val maxButtons: Int = (widthDp / minButtonWidthDp).toInt()
    val usableButtonCount = when {
      items.size <= maxButtons -> items.size
      else -> maxButtons - 1
    }

    val renderableItems: List<ActionItem> = items.subList(0, usableButtonCount)
    val overflowItems: List<ActionItem> = if (renderableItems.size < items.size) items.subList(usableButtonCount, items.size) else emptyList()

    removeAllViews()

    renderableItems.forEach { item ->
      val view: View = LayoutInflater.from(context).inflate(R.layout.signal_bottom_action_bar_item, this, false)
      addView(view)
      bindItem(view, item)
    }

    if (overflowItems.isNotEmpty()) {
      val view: View = LayoutInflater.from(context).inflate(R.layout.signal_bottom_action_bar_item, this, false)
      addView(view)
      bindItem(
        view,
        ActionItem(
          iconRes = R.drawable.ic_more_horiz_24,
          title = context.getString(R.string.SignalBottomActionBar_more),
          action = {
            SignalContextMenu.Builder(view, parent as ViewGroup)
              .preferredHorizontalPosition(SignalContextMenu.HorizontalPosition.END)
              .offsetY(ViewUtil.dpToPx(8))
              .show(overflowItems)
          }
        )
      )
    }

    if (wasLayoutRequested) {
      post {
        requestLayout()
      }
    }
  }

  private fun bindItem(view: View, item: ActionItem) {
    val icon: ImageView = view.findViewById(R.id.signal_bottom_action_bar_item_icon)
    val title: TextView = view.findViewById(R.id.signal_bottom_action_bar_item_title)

    icon.setImageResource(item.iconRes)
    title.text = item.title
    view.setOnClickListener { item.action.run() }
  }
}
