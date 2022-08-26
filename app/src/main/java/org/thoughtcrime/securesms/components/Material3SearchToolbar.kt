package org.thoughtcrime.securesms.components

import android.content.Context
import android.graphics.PointF
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewAnimationUtils
import android.widget.EditText
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.addListener
import androidx.core.widget.addTextChangedListener
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible

/**
 * Search Toolbar following the Signal Material3 design spec.
 */
class Material3SearchToolbar @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

  var listener: Listener? = null

  private val input: EditText

  private val circularRevealPoint = PointF()

  init {
    inflate(context, R.layout.material3_serarch_toolbar, this)

    input = findViewById(R.id.search_input)

    val close = findViewById<View>(R.id.search_close)
    val clear = findViewById<View>(R.id.search_clear)

    close.setOnClickListener { collapse() }
    clear.setOnClickListener { input.setText("") }

    input.addTextChangedListener(afterTextChanged = {
      clear.visible = !it.isNullOrBlank()
      listener?.onSearchTextChange(it?.toString() ?: "")
    })
  }

  fun display(x: Float, y: Float) {
    if (Build.VERSION.SDK_INT < 21) {
      visibility = VISIBLE
      ViewUtil.focusAndShowKeyboard(input)
    } else if (!visible) {
      circularRevealPoint.set(x, y)

      val animator = ViewAnimationUtils.createCircularReveal(this, x.toInt(), y.toInt(), 0f, width.toFloat())
      animator.duration = 400

      visibility = VISIBLE
      ViewUtil.focusAndShowKeyboard(input)
      animator.start()
    }
  }

  fun collapse() {
    if (visibility == VISIBLE) {
      listener?.onSearchClosed()
      ViewUtil.hideKeyboard(context, input)

      if (Build.VERSION.SDK_INT >= 21) {
        val animator = ViewAnimationUtils.createCircularReveal(this, circularRevealPoint.x.toInt(), circularRevealPoint.y.toInt(), width.toFloat(), 0f)
        animator.duration = 400

        animator.addListener(onEnd = {
          visibility = INVISIBLE
        })
        animator.start()
      } else {
        visibility = INVISIBLE
      }
    }
  }

  fun clearText() {
    input.setText("")
  }

  interface Listener {
    fun onSearchTextChange(text: String)
    fun onSearchClosed()
  }
}
