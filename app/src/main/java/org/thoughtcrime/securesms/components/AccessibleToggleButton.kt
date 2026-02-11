package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatToggleButton

class AccessibleToggleButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AppCompatToggleButton(context, attrs, defStyleAttr) {

  private var listener: OnCheckedChangeListener? = null

  override fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
    super.setOnCheckedChangeListener(listener)
    this.listener = listener
  }

  fun setChecked(checked: Boolean, notifyListener: Boolean) {
    if (!notifyListener) {
      super.setOnCheckedChangeListener(null)
    }

    super.setChecked(checked)

    if (!notifyListener) {
      super.setOnCheckedChangeListener(listener)
    }
  }

  fun getOnCheckedChangeListener(): OnCheckedChangeListener? {
    return this.listener
  }
}
