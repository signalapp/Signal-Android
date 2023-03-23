package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import org.thoughtcrime.securesms.R

/**
 * A "forced" EN US Numeric keyboard designed solely for SMS code entry. This
 * "upgrade" over KeyboardView will ensure that the keyboard is navigable via
 * TalkBack and will read out keys as they are selected by the user. This is
 * not a perfect solution, but save being able to force the system keyboard to
 * appear in EN US, this is the best we can do for the time being.
 */
class NumericKeyboardView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

  var listener: Listener? = null

  init {
    layoutDirection = LAYOUT_DIRECTION_LTR
    inflate(context, R.layout.numeric_keyboard_view, this)

    findViewById<TextView>(R.id.numeric_keyboard_1).setOnClickListener {
      listener?.onKeyPress(1)
    }

    findViewById<TextView>(R.id.numeric_keyboard_2).setOnClickListener {
      listener?.onKeyPress(2)
    }

    findViewById<TextView>(R.id.numeric_keyboard_3).setOnClickListener {
      listener?.onKeyPress(3)
    }

    findViewById<TextView>(R.id.numeric_keyboard_4).setOnClickListener {
      listener?.onKeyPress(4)
    }

    findViewById<TextView>(R.id.numeric_keyboard_5).setOnClickListener {
      listener?.onKeyPress(5)
    }

    findViewById<TextView>(R.id.numeric_keyboard_6).setOnClickListener {
      listener?.onKeyPress(6)
    }

    findViewById<TextView>(R.id.numeric_keyboard_7).setOnClickListener {
      listener?.onKeyPress(7)
    }

    findViewById<TextView>(R.id.numeric_keyboard_8).setOnClickListener {
      listener?.onKeyPress(8)
    }

    findViewById<TextView>(R.id.numeric_keyboard_9).setOnClickListener {
      listener?.onKeyPress(9)
    }

    findViewById<TextView>(R.id.numeric_keyboard_0).setOnClickListener {
      listener?.onKeyPress(0)
    }

    findViewById<ImageView>(R.id.numeric_keyboard_back).setOnClickListener {
      listener?.onKeyPress(-1)
    }
  }

  interface Listener {
    fun onKeyPress(keyCode: Int)
  }
}
