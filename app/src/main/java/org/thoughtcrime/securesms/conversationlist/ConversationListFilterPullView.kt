package org.thoughtcrime.securesms.conversationlist

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.ConversationListFilterPullViewBinding

/**
 * Encapsulates the push / pull latch for enabling and disabling
 * filters into a convenient view.
 */
class ConversationListFilterPullView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

  private val colorPull = ContextCompat.getColor(context, R.color.signal_colorSurface1)
  private val colorRelease = ContextCompat.getColor(context, R.color.signal_colorSecondaryContainer)
  private var state: State = State.PULL

  init {
    inflate(context, R.layout.conversation_list_filter_pull_view, this)
    setBackgroundColor(colorPull)
  }

  private val binding = ConversationListFilterPullViewBinding.bind(this)

  fun setToPull() {
    if (state == State.PULL) {
      return
    }

    state = State.PULL
    setBackgroundColor(colorPull)
    binding.arrow.setImageResource(R.drawable.ic_arrow_down)
    binding.text.setText(R.string.ConversationListFilterPullView__pull_down_to_filter)
  }

  fun setToRelease() {
    if (state == State.RELEASE) {
      return
    }

    if (Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0) {
      performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP)
    }

    state = State.RELEASE
    setBackgroundColor(colorRelease)
    binding.arrow.setImageResource(R.drawable.ic_arrow_up_16)
    binding.text.setText(R.string.ConversationListFilterPullView__release_to_filter)
  }

  enum class State {
    RELEASE,
    PULL
  }
}
