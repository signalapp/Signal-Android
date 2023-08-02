package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageButton
import com.google.android.material.snackbar.Snackbar
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * The send button you see in a conversation.
 * Also encapsulates the long-press menu that allows users to switch [MessageSendType]s.
 */
class SendButton(context: Context, attributeSet: AttributeSet?) : AppCompatImageButton(context, attributeSet), OnLongClickListener {

  companion object {
    private val TAG = Log.tag(SendButton::class.java)
  }

  private var scheduledSendListener: ScheduledSendListener? = null

  private var availableSendTypes: List<MessageSendType> = MessageSendType.getAllAvailable(context, false)
  private var activeMessageSendType: MessageSendType? = null
  private var defaultTransportType: MessageSendType.TransportType = MessageSendType.TransportType.SIGNAL
  private var defaultSubscriptionId: Int? = null

  var snackbarContainer: View? = null
  private var popupContainer: ViewGroup? = null

  init {
    setOnLongClickListener(this)
    ViewUtil.mirrorIfRtl(this, getContext())
  }

  /**
   * The actively-selected send type.
   */
  private val selectedSendType: MessageSendType
    get() {
      activeMessageSendType?.let {
        return it
      }

      if (defaultTransportType === MessageSendType.TransportType.SMS) {
        for (type in availableSendTypes) {
          if (type.usesSmsTransport && (defaultSubscriptionId == null || type.simSubscriptionId == defaultSubscriptionId)) {
            return type
          }
        }
      }

      for (type in availableSendTypes) {
        if (type.transportType === defaultTransportType) {
          return type
        }
      }

      Log.w(TAG, "No options of default type! Resetting. DefaultTransportType: $defaultTransportType, AllAvailable: ${availableSendTypes.map { it.transportType }}")

      val signalType: MessageSendType? = availableSendTypes.firstOrNull { it.usesSignalTransport }
      if (signalType != null) {
        Log.w(TAG, "No options of default type, but Signal type is available. Switching. DefaultTransportType: $defaultTransportType, AllAvailable: ${availableSendTypes.map { it.transportType }}")
        defaultTransportType = MessageSendType.TransportType.SIGNAL
        onSelectionChanged(signalType)
        return signalType
      } else if (availableSendTypes.isEmpty()) {
        Log.w(TAG, "No send types available at all! Enabling the Signal transport.")
        defaultTransportType = MessageSendType.TransportType.SIGNAL
        availableSendTypes = listOf(MessageSendType.SignalMessageSendType)
        onSelectionChanged(MessageSendType.SignalMessageSendType)
        return MessageSendType.SignalMessageSendType
      } else {
        throw AssertionError("No options of default type! DefaultTransportType: $defaultTransportType, AllAvailable: ${availableSendTypes.map { it.transportType }}")
      }
    }

  fun triggerSelectedChangedEvent() {
    onSelectionChanged(newType = selectedSendType)
  }

  fun setScheduledSendListener(listener: ScheduledSendListener?) {
    this.scheduledSendListener = listener
  }

  private fun setSendType(sendType: MessageSendType?) {
    if (activeMessageSendType == sendType) {
      return
    }
    activeMessageSendType = sendType
    onSelectionChanged(newType = selectedSendType)
  }

  /**
   * Must be called with a view that is acceptable for determining the bounds of the popup selector.
   */
  fun setPopupContainer(container: ViewGroup) {
    popupContainer = container
  }

  private fun onSelectionChanged(newType: MessageSendType) {
    setImageResource(newType.buttonDrawableRes)
    contentDescription = context.getString(newType.titleRes)
  }

  override fun onLongClick(v: View): Boolean {
    if (!isEnabled) {
      return false
    }

    val scheduleListener = scheduledSendListener
    if (availableSendTypes.size == 1) {
      return if (scheduleListener?.canSchedule() == true && selectedSendType.transportType != MessageSendType.TransportType.SMS) {
        scheduleListener.onSendScheduled()
        true
      } else if (snackbarContainer != null && !SignalStore.misc().smsExportPhase.allowSmsFeatures()) {
        Snackbar.make(snackbarContainer!!, R.string.InputPanel__sms_messaging_is_no_longer_supported_in_signal, Snackbar.LENGTH_SHORT).show()
        true
      } else {
        false
      }
    }

    showSendTypeContextMenu(selectedSendType.transportType != MessageSendType.TransportType.SMS)

    return true
  }

  private fun showSendTypeContextMenu(allowScheduling: Boolean) {
    val currentlySelected: MessageSendType = selectedSendType
    val listener = scheduledSendListener
    val items = availableSendTypes
      .filterNot { it == currentlySelected }
      .map { option ->
        ActionItem(
          iconRes = option.menuDrawableRes,
          title = option.getTitle(context),
          action = { setSendType(option) }
        )
      }.toMutableList()
    if (allowScheduling && listener?.canSchedule() == true) {
      items += ActionItem(
        iconRes = R.drawable.symbol_calendar_24,
        title = context.getString(R.string.conversation_activity__option_schedule_message),
        action = { listener.onSendScheduled() }
      )
    }

    SignalContextMenu.Builder((parent as View), popupContainer!!)
      .preferredVerticalPosition(SignalContextMenu.VerticalPosition.ABOVE)
      .offsetY(ViewUtil.dpToPx(8))
      .show(items)
  }

  interface ScheduledSendListener {
    fun onSendScheduled()
    fun canSchedule(): Boolean
  }
}
