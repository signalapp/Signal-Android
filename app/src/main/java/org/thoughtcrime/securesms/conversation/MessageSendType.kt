package org.thoughtcrime.securesms.conversation

import android.content.Context
import android.os.Parcelable
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlinx.parcelize.Parcelize
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.CharacterCalculator
import org.thoughtcrime.securesms.util.PushCharacterCalculator
import java.lang.IllegalArgumentException

/**
 * The kinds of messages you can send, e.g. a plain Signal message, an SMS message, etc.
 */
@Parcelize
sealed class MessageSendType(
  @StringRes
  val titleRes: Int,
  @StringRes
  val composeHintRes: Int,
  @DrawableRes
  val buttonDrawableRes: Int,
  @DrawableRes
  val menuDrawableRes: Int,
  @ColorRes
  val backgroundColorRes: Int,
  val transportType: TransportType,
  val characterCalculator: CharacterCalculator
) : Parcelable {

  @get:JvmName("usesSignalTransport")
  val usesSignalTransport
    get() = transportType == TransportType.SIGNAL

  fun calculateCharacters(body: String): CharacterCalculator.CharacterState {
    return characterCalculator.calculateCharacters(body)
  }

  open fun getTitle(context: Context): String {
    return context.getString(titleRes)
  }

  /**
   * A type representing a basic Signal message.
   */
  @Parcelize
  object SignalMessageSendType : MessageSendType(
    titleRes = R.string.ConversationActivity_send_message_content_description,
    composeHintRes = R.string.conversation_activity__type_message_push,
    buttonDrawableRes = R.drawable.ic_send_lock_24,
    menuDrawableRes = R.drawable.ic_secure_24,
    backgroundColorRes = R.color.core_ultramarine,
    transportType = TransportType.SIGNAL,
    characterCalculator = PushCharacterCalculator()
  )

  enum class TransportType {
    SIGNAL,
    SMS
  }

  companion object {
    @JvmStatic
    fun getAllAvailable(): List<MessageSendType> {
      return listOf(SignalMessageSendType)
    }

    @JvmStatic
    fun getFirstForTransport(transportType: TransportType): MessageSendType {
      return getAllAvailable().firstOrNull { it.transportType == transportType } ?: throw IllegalArgumentException("No options available for desired type $transportType!")
    }
  }
}
