package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R

class AlertView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

  init {
    setImageResource(R.drawable.symbol_error_circle_compact_16)
    scaleType = ScaleType.FIT_CENTER
  }

  fun setNone() {
    visibility = View.GONE
  }

  fun setFailed() {
    visibility = View.VISIBLE
    setColorFilter(ContextCompat.getColor(context, org.signal.core.ui.R.color.signal_colorError))
    contentDescription = context.getString(R.string.conversation_item_sent__send_failed_indicator_description)
  }

  fun setRateLimited() {
    visibility = View.VISIBLE
    setColorFilter(ContextCompat.getColor(context, org.signal.core.ui.R.color.signal_colorOnSurfaceVariant))
    contentDescription = context.getString(R.string.conversation_item_sent__pending_approval_description)
  }
}
