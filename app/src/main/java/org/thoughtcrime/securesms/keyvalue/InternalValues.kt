package org.thoughtcrime.securesms.keyvalue

import org.signal.ringrtc.CallManager.AudioProcessingMethod
import org.signal.ringrtc.CallManager.DataMode
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.util.Environment.Calling.defaultSfuUrl
import org.thoughtcrime.securesms.util.RemoteConfig

class InternalValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {
  companion object {
    const val GV2_FORCE_INVITES: String = "internal.gv2.force_invites"
    const val GV2_IGNORE_P2P_CHANGES: String = "internal.gv2.ignore_p2p_changes"
    const val RECIPIENT_DETAILS: String = "internal.recipient_details"
    const val ALLOW_CENSORSHIP_SETTING: String = "internal.force_censorship"
    const val FORCE_BUILT_IN_EMOJI: String = "internal.force_built_in_emoji"
    const val REMOVE_SENDER_KEY_MINIMUM: String = "internal.remove_sender_key_minimum"
    const val DELAY_RESENDS: String = "internal.delay_resends"
    const val CALLING_SERVER: String = "internal.calling_server"
    const val CALLING_AUDIO_PROCESSING_METHOD: String = "internal.calling_audio_processing_method"
    const val CALLING_DATA_MODE: String = "internal.calling_bandwidth_mode"
    const val CALLING_DISABLE_TELECOM: String = "internal.calling_disable_telecom"
    const val CALLING_ENABLE_OBOE_ADM: String = "internal.calling_enable_oboe_adm"
    const val SHAKE_TO_REPORT: String = "internal.shake_to_report"
    const val DISABLE_STORAGE_SERVICE: String = "internal.disable_storage_service"
    const val FORCE_WEBSOCKET_MODE: String = "internal.force_websocket_mode"
    const val LAST_SCROLL_POSITION: String = "internal.last_scroll_position"
    const val CONVERSATION_ITEM_V2_MEDIA: String = "internal.conversation_item_v2_media"
    const val WEB_SOCKET_SHADOWING_STATS: String = "internal.web_socket_shadowing_stats"
    const val ENCODE_HEVC: String = "internal.hevc_encoding"
    const val NEW_CALL_UI: String = "internal.new.call.ui"
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> = emptyList()

  /**
   * Members will not be added directly to a GV2 even if they could be.
   */
  var gv2ForceInvites by booleanValue(GV2_FORCE_INVITES, false).defaultForExternalUsers()

  /**
   * Signed group changes are sent P2P, if the client ignores them, it will then ask the server
   * directly which allows testing of certain testing scenarios.
   */
  var gv2IgnoreP2PChanges by booleanValue(GV2_IGNORE_P2P_CHANGES, false).defaultForExternalUsers()

  /**
   * Show detailed recipient info in the [org.thoughtcrime.securesms.components.settings.conversation.InternalConversationSettingsFragment].
   */
  var recipientDetails by booleanValue(RECIPIENT_DETAILS, true).falseForExternalUsers()

  /**
   * Allow changing the censorship circumvention setting regardless of network status.
   */
  var allowChangingCensorshipSetting by booleanValue(ALLOW_CENSORSHIP_SETTING, false).defaultForExternalUsers()

  /**
   * Force the app to use the emoji that ship with the app, as opposed to the ones that were downloaded.
   */
  var forceBuiltInEmoji by booleanValue(FORCE_BUILT_IN_EMOJI, false).defaultForExternalUsers()

  /**
   * Remove the requirement that there must be two sender-key-capable recipients to use sender key
   */
  var removeSenderKeyMinimum by booleanValue(REMOVE_SENDER_KEY_MINIMUM, false).defaultForExternalUsers()

  /**
   * Delay resending messages in response to retry receipts by 10 seconds.
   */
  var delayResends by booleanValue(DELAY_RESENDS, false).defaultForExternalUsers()

  /**
   * Whether or not "shake to report" is enabled.
   */
  var shakeToReport: Boolean by booleanValue(SHAKE_TO_REPORT, true).falseForExternalUsers()

  /**
   * Whether or not storage service is manually disabled.
   */
  var storageServiceDisabled by booleanValue(DISABLE_STORAGE_SERVICE, false).defaultForExternalUsers()

  /**
   * The selected group calling server to use.
   *
   *
   * The user must be an internal user and the setting must be one of the current set of internal servers otherwise
   * the default SFU will be returned. This ensures that if the [BuildConfig.SIGNAL_SFU_INTERNAL_URLS] list changes,
   * internal users cannot be left on old servers.
   */
  var groupCallingServer: String
    get() {
      var internalServer = if (RemoteConfig.internalUser) getString(CALLING_SERVER, defaultSfuUrl()) else null
      if (internalServer != null && !listOf(*BuildConfig.SIGNAL_SFU_INTERNAL_URLS).contains(internalServer)) {
        internalServer = null
      }
      return internalServer ?: BuildConfig.SIGNAL_SFU_URL
    }
    set(value) = putString(CALLING_SERVER, value)

  /**
   * Setting to override the default handling of hardware/software AEC.
   */
  val callingAudioProcessingMethod: AudioProcessingMethod
    get() {
      return if (RemoteConfig.internalUser) {
        val entryIndex = getInteger(CALLING_AUDIO_PROCESSING_METHOD, AudioProcessingMethod.Default.ordinal)
        AudioProcessingMethod.entries[entryIndex]
      } else {
        AudioProcessingMethod.Default
      }
    }

  /**
   * Setting to override the default calling bandwidth mode.
   */
  val callingDataMode: DataMode
    get() {
      return if (RemoteConfig.internalUser) {
        val index = getInteger(CALLING_DATA_MODE, DataMode.NORMAL.ordinal)
        val modes: Array<DataMode> = DataMode.entries.toTypedArray()

        if (index < modes.size) modes[index] else DataMode.NORMAL
      } else {
        DataMode.NORMAL
      }
    }

  /**
   * Whether or not Telecom integration is manually disabled.
   */
  var callingDisableTelecom by booleanValue(CALLING_DISABLE_TELECOM, true).falseForExternalUsers()

  /**
   * Whether or not the Oboe ADM is used.
   */
  var callingEnableOboeAdm by booleanValue(CALLING_ENABLE_OBOE_ADM, true).falseForExternalUsers()

  /**
   * Whether or not the system is forced to be in 'websocket mode', where FCM is ignored and we use a foreground service to keep the app alive.
   */
  var isWebsocketModeForced: Boolean by booleanValue(FORCE_WEBSOCKET_MODE, false).defaultForExternalUsers()

  var hevcEncoding by booleanValue(ENCODE_HEVC, false).defaultForExternalUsers()

  var newCallingUi: Boolean by booleanValue(NEW_CALL_UI, false).defaultForExternalUsers()

  var lastScrollPosition: Int by integerValue(LAST_SCROLL_POSITION, 0).defaultForExternalUsers()

  var useConversationItemV2Media by booleanValue(CONVERSATION_ITEM_V2_MEDIA, false).defaultForExternalUsers()

  var webSocketShadowingStats by nullableBlobValue(WEB_SOCKET_SHADOWING_STATS, null).defaultForExternalUsers()

  var forceSsre2Capability by booleanValue("internal.force_ssre2_capability", false).defaultForExternalUsers()

  private fun <T> SignalStoreValueDelegate<T>.defaultForExternalUsers(): SignalStoreValueDelegate<T> {
    return this.withPrecondition { RemoteConfig.internalUser }
  }

  private fun SignalStoreValueDelegate<Boolean>.falseForExternalUsers(): SignalStoreValueDelegate<Boolean> {
    return this.map { actualValue -> RemoteConfig.internalUser && actualValue }
  }
}
