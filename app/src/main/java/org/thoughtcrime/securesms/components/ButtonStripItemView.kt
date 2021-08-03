package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import org.thoughtcrime.securesms.R

class ButtonStripItemView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

  private val iconView: ImageView
  private val labelView: TextView

  init {
    inflate(context, R.layout.button_strip_item_view, this)

    iconView = findViewById(R.id.icon)
    labelView = findViewById(R.id.label)

    val array = context.obtainStyledAttributes(attrs, R.styleable.ButtonStripItemView)

    val icon = array.getDrawable(R.styleable.ButtonStripItemView_bsiv_icon)
    val contentDescription = array.getString(R.styleable.ButtonStripItemView_bsiv_icon_contentDescription)
    val label = array.getString(R.styleable.ButtonStripItemView_bsiv_label)

    iconView.setImageDrawable(icon)
    iconView.contentDescription = contentDescription
    labelView.text = label

    array.recycle()
  }

  fun setOnIconClickedListener(onIconClickedListener: (() -> Unit)?) {
    iconView.setOnClickListener { onIconClickedListener?.invoke() }
  }
}
