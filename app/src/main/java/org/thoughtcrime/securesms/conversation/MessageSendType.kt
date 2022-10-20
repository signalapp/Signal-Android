package org.thoughtcrime.securesms.conversation

import android.Manifest
import android.content.Context
import android.os.Parcelable
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlinx.parcelize.Parcelize
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.CharacterCalculator
import org.thoughtcrime.securesms.util.MmsCharacterCalculator
import org.thoughtcrime.securesms.util.PushCharacterCalculator
import org.thoughtcrime.securesms.util.SmsCharacterCalculator
import org.thoughtcrime.securesms.util.dualsim.SubscriptionInfoCompat
import org.thoughtcrime.securesms.util.dualsim.SubscriptionManagerCompat
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
  val characterCalculator: CharacterCalculator,
  open val simName: CharSequence? = null,
  open val simSubscriptionId: Int? = null
) : Parcelable {

  @get:JvmName("usesSmsTransport")
  val usesSmsTransport
    get() = transportType == TransportType.SMS

  @get:JvmName("usesSignalTransport")
  val usesSignalTransport
    get() = transportType == TransportType.SIGNAL

  fun calculateCharacters(body: String): CharacterCalculator.CharacterState {
    return characterCalculator.calculateCharacters(body)
  }

  fun getSimSubscriptionIdOr(fallback: Int): Int {
    return simSubscriptionId ?: fallback
  }

  open fun getTitle(context: Context): String {
    return context.getString(titleRes)
  }

  /**
   * A type representing an SMS message, with optional SIM fields for multi-SIM devices.
   */
  @Parcelize
  data class SmsMessageSendType(override val simName: CharSequence? = null, override val simSubscriptionId: Int? = null) : MessageSendType(
    titleRes = R.string.ConversationActivity_transport_insecure_sms,
    composeHintRes = R.string.conversation_activity__type_message_sms_insecure,
    buttonDrawableRes = R.drawable.ic_send_unlock_24,
    menuDrawableRes = R.drawable.ic_insecure_24,
    backgroundColorRes = R.color.core_grey_50,
    transportType = TransportType.SMS,
    characterCalculator = SmsCharacterCalculator(),
    simName = simName,
    simSubscriptionId = simSubscriptionId
  ) {
    override fun getTitle(context: Context): String {
      return if (simName == null) {
        super.getTitle(context)
      } else {
        context.getString(R.string.ConversationActivity_transport_insecure_sms_with_sim, simName)
      }
    }
  }

  /**
   * A type representing an MMS message, with optional SIM fields for multi-SIM devices.
   */
  @Parcelize
  data class MmsMessageSendType(override val simName: CharSequence? = null, override val simSubscriptionId: Int? = null) : MessageSendType(
    titleRes = R.string.ConversationActivity_transport_insecure_mms,
    composeHintRes = R.string.conversation_activity__type_message_mms_insecure,
    buttonDrawableRes = R.drawable.ic_send_unlock_24,
    menuDrawableRes = R.drawable.ic_insecure_24,
    backgroundColorRes = R.color.core_grey_50,
    transportType = TransportType.SMS,
    characterCalculator = MmsCharacterCalculator(),
    simName = simName,
    simSubscriptionId = simSubscriptionId
  ) {
    override fun getTitle(context: Context): String {
      return if (simName == null) {
        super.getTitle(context)
      } else {
        context.getString(R.string.ConversationActivity_transport_insecure_sms_with_sim, simName)
      }
    }
  }

  /**
   * A type representing a basic Signal message.
   */
  @Parcelize
  object SignalMessageSendType : MessageSendType(
    titleRes = R.string.ConversationActivity_transport_signal,
    composeHintRes = R.string.conversation_activity__type_message_push,
    buttonDrawableRes = R.drawable.ic_send_lock_24,
    menuDrawableRes = R.drawable.ic_secure_24,
    backgroundColorRes = R.color.core_ultramarine,
    transportType = TransportType.SIGNAL,
    characterCalculator = PushCharacterCalculator()
  )

  enum class TransportType {
    SIGNAL, SMS
  }

  companion object {

    private val TAG = Log.tag(MessageSendType::class.java)

    /**
     * Returns a list of all available [MessageSendType]s. Requires [Manifest.permission.READ_PHONE_STATE] in order to get available
     * SMS options.
     */
    @JvmStatic
    fun getAllAvailable(context: Context, isMedia: Boolean = false): List<MessageSendType> {
      val options: MutableList<MessageSendType> = mutableListOf()

      options += SignalMessageSendType

      if (SignalStore.misc().smsExportPhase.allowSmsFeatures()) {
        try {
          val subscriptions: Collection<SubscriptionInfoCompat> = SubscriptionManagerCompat(context).activeAndReadySubscriptionInfos

          if (subscriptions.size < 2) {
            options += if (isMedia) MmsMessageSendType() else SmsMessageSendType()
          } else {
            options += subscriptions.map {
              if (isMedia) {
                MmsMessageSendType(simName = it.displayName, simSubscriptionId = it.subscriptionId)
              } else {
                SmsMessageSendType(simName = it.displayName, simSubscriptionId = it.subscriptionId)
              }
            }
          }
        } catch (e: SecurityException) {
          Log.w(TAG, "Did not have permission to get SMS subscription details!")
        }
      }

      return options
    }

    @JvmStatic
    fun getFirstForTransport(context: Context, isMedia: Boolean, transportType: TransportType): MessageSendType {
      return getAllAvailable(context, isMedia).firstOrNull { it.transportType == transportType } ?: throw IllegalArgumentException("No options available for desired type $transportType!")
    }
  }
}
