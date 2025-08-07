package org.thoughtcrime.securesms.keyvalue

import org.signal.ringrtc.CallManager.DataMode
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.backup.v2.proto.BackupDebugInfo
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
    const val CALLING_DATA_MODE: String = "internal.calling_bandwidth_mode"
    const val CALLING_DISABLE_TELECOM: String = "internal.calling_disable_telecom"
    const val CALLING_SET_AUDIO_CONFIG: String = "internal.calling_set_audio_config"
    const val CALLING_USE_OBOE_ADM: String = "internal.calling_use_oboe_adm"
    const val CALLING_USE_SOFTWARE_AEC: String = "internal.calling_use_software_aec"
    const val CALLING_USE_SOFTWARE_NS: String = "internal.calling_use_software_ns"
    const val CALLING_USE_INPUT_LOW_LATENCY: String = "internal.calling_use_input_low_latency"
    const val CALLING_USE_INPUT_VOICE_COMM: String = "internal.calling_use_input_voice_comm"
    const val SHAKE_TO_REPORT: String = "internal.shake_to_report"
    const val DISABLE_STORAGE_SERVICE: String = "internal.disable_storage_service"
    const val FORCE_WEBSOCKET_MODE: String = "internal.force_websocket_mode"
    const val LAST_SCROLL_POSITION: String = "internal.last_scroll_position"
    const val CONVERSATION_ITEM_V2_MEDIA: String = "internal.conversation_item_v2_media"
    const val WEB_SOCKET_SHADOWING_STATS: String = "internal.web_socket_shadowing_stats"
    const val ENCODE_HEVC: String = "internal.hevc_encoding"
    const val NEW_CALL_UI: String = "internal.new.call.ui"
    const val LARGE_SCREEN_UI: String = "internal.large.screen.ui"
    const val FORCE_SPLIT_PANE_ON_COMPACT_LANDSCAPE: String = "internal.force.split.pane.on.compact.landscape.ui"
    const val SHOW_ARCHIVE_STATE_HINT: String = "internal.show_archive_state_hint"
    const val INCLUDE_DEBUGLOG_IN_BACKUP: String = "internal.include_debuglog_in_backup"
    const val IMPORTED_BACKUP_DEBUG_INFO: String = "internal.imported_backup_debug_info"
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> = emptyList()

  /**
   * Enable or disable the large screen UI.
   */
  var largeScreenUi by booleanValue(LARGE_SCREEN_UI, false).defaultForExternalUsers()

  /**
   * Force split-pane mode on compact landscape
   */
  var forceSplitPaneOnCompactLandscape by booleanValue(FORCE_SPLIT_PANE_ON_COMPACT_LANDSCAPE, false).defaultForExternalUsers()

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
   * Whether or not to override the audio settings from the remote configuration.
   */
  var callingSetAudioConfig by booleanValue(CALLING_SET_AUDIO_CONFIG, true).falseForExternalUsers()

  /**
   * If overriding the audio settings, use the Oboe ADM or not.
   */
  var callingUseOboeAdm by booleanValue(CALLING_USE_OBOE_ADM, true).defaultForExternalUsers()

  /**
   * If overriding the audio settings, use the Software AEC or not.
   */
  var callingUseSoftwareAec by booleanValue(CALLING_USE_SOFTWARE_AEC, false).defaultForExternalUsers()

  /**
   * If overriding the audio settings, use the Software NS or not.
   */
  var callingUseSoftwareNs by booleanValue(CALLING_USE_SOFTWARE_NS, false).defaultForExternalUsers()

  /**
   * If overriding the audio settings, use Low Latency for the input or not.
   */
  var callingUseInputLowLatency by booleanValue(CALLING_USE_INPUT_LOW_LATENCY, true).defaultForExternalUsers()

  /**
   * If overriding the audio settings, use Voice Comm for the input or not.
   */
  var callingUseInputVoiceComm by booleanValue(CALLING_USE_INPUT_VOICE_COMM, true).defaultForExternalUsers()

  /**
   * Whether or not the system is forced to be in 'websocket mode', where FCM is ignored and we use a foreground service to keep the app alive.
   */
  var isWebsocketModeForced: Boolean by booleanValue(FORCE_WEBSOCKET_MODE, false).defaultForExternalUsers()

  var hevcEncoding by booleanValue(ENCODE_HEVC, false).defaultForExternalUsers()

  var newCallingUi: Boolean by booleanValue(NEW_CALL_UI, false).defaultForExternalUsers()

  var lastScrollPosition: Int by integerValue(LAST_SCROLL_POSITION, 0).defaultForExternalUsers()

  var useConversationItemV2Media by booleanValue(CONVERSATION_ITEM_V2_MEDIA, false).defaultForExternalUsers()

  var forceSsre2Capability by booleanValue("internal.force_ssre2_capability", false).defaultForExternalUsers()

  var showArchiveStateHint by booleanValue(SHOW_ARCHIVE_STATE_HINT, false).defaultForExternalUsers()

  /** Whether or not we should include a debuglog in the backup debug info when generating a backup. */
  var includeDebuglogInBackup by booleanValue(INCLUDE_DEBUGLOG_IN_BACKUP, true).falseForExternalUsers()

  /** Any [BackupDebugInfo] that was imported during the last backup restore, if any. */
  var importedBackupDebugInfo: BackupDebugInfo? by protoValue(IMPORTED_BACKUP_DEBUG_INFO, BackupDebugInfo.ADAPTER).defaultForExternalUsers()

  private fun <T> SignalStoreValueDelegate<T>.defaultForExternalUsers(): SignalStoreValueDelegate<T> {
    return this.withPrecondition { RemoteConfig.internalUser }
  }

  private fun SignalStoreValueDelegate<Boolean>.falseForExternalUsers(): SignalStoreValueDelegate<Boolean> {
    return this.map { actualValue -> RemoteConfig.internalUser && actualValue }
  }
}
