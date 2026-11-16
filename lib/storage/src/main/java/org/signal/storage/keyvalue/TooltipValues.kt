package org.signal.storage.keyvalue

import android.content.Context
import org.signal.core.util.logging.Log

class TooltipValues(store: KeyValueStore, context: Context) : SignalStoreValues(store) {

  companion object {
    private val TAG = Log.tag(TooltipValues::class)

    private const val GROUP_CALLING_MAX_TOOLTIP_DISPLAY_COUNT = 3

    private const val BLUR_HUD_ICON = "tooltip.blur_hud_icon"
    private const val GROUP_CALL_SPEAKER_VIEW = "tooltip.group_call_speaker_view"
    private const val GROUP_CALL_TOOLTIP_DISPLAY_COUNT = "tooltip.group_call_tooltip_display_count"
    private const val MULTI_FORWARD_DIALOG = "tooltip.multi.forward.dialog"
    private const val BUBBLE_OPT_OUT = "tooltip.bubble.opt.out"
    private const val PROFILE_SETTINGS_QR_CODE = "tooltip.profile_settings_qr_code"
    private const val CALLING_SWITCH_CAMERA = "tooltip.calling.switch_camera"
    private const val STICKER_INTRO = "tooltip.sticker_intro"
    private const val SWIPE_TO_REPLY = "tooltip.swipe_to_reply"
  }

  init {
    if (!store.containsKey(STICKER_INTRO)) {
      migrateFromSharedPrefsV1(context)
    }
  }

  /** Do not alter. If you need to migrate more stuff, create a new method. */
  private fun migrateFromSharedPrefsV1(context: Context) {
    Log.i(TAG, "[V1] Migrating tooltip values from shared prefs.")

    store.beginWrite()
      .putBoolean(SWIPE_TO_REPLY, LegacySharedPrefs.getBoolean(context, "pref_has_seen_swipe_to_reply", false))
      .putBoolean(STICKER_INTRO, LegacySharedPrefs.getBoolean(context, "pref_seen_sticker_intro_tooltip", false))
      .commit()
  }

  fun hasSeenStickerIntroTooltip(): Boolean {
    return getBoolean(STICKER_INTRO, false)
  }

  fun markStickerIntroTooltipSeen() {
    putBoolean(STICKER_INTRO, true)
  }

  fun hasSeenSwipeToReplyTooltip(): Boolean {
    return getBoolean(SWIPE_TO_REPLY, false)
  }

  fun markSwipeToReplyTooltipSeen() {
    putBoolean(SWIPE_TO_REPLY, true)
  }

  public override fun onFirstEverAppLaunch() {
    markMultiForwardDialogSeen()
  }

  override val keysToIncludeInBackup: List<String>
    get() = emptyList()

  fun hasSeenBlurHudIconTooltip(): Boolean {
    return getBoolean(BLUR_HUD_ICON, false)
  }

  fun markBlurHudIconTooltipSeen() {
    putBoolean(BLUR_HUD_ICON, true)
  }

  fun hasSeenGroupCallSpeakerView(): Boolean {
    return getBoolean(GROUP_CALL_SPEAKER_VIEW, false)
  }

  fun markGroupCallSpeakerViewSeen() {
    putBoolean(GROUP_CALL_SPEAKER_VIEW, true)
  }

  fun shouldShowGroupCallingTooltip(): Boolean {
    return getInteger(GROUP_CALL_TOOLTIP_DISPLAY_COUNT, 0) < GROUP_CALLING_MAX_TOOLTIP_DISPLAY_COUNT
  }

  fun markGroupCallingTooltipSeen() {
    putInteger(GROUP_CALL_TOOLTIP_DISPLAY_COUNT, getInteger(GROUP_CALL_TOOLTIP_DISPLAY_COUNT, 0) + 1)
  }

  fun markGroupCallingLobbyEntered() {
    putInteger(GROUP_CALL_TOOLTIP_DISPLAY_COUNT, Int.MAX_VALUE)
  }

  fun showMultiForwardDialog(): Boolean {
    return getBoolean(MULTI_FORWARD_DIALOG, true)
  }

  fun markMultiForwardDialogSeen() {
    putBoolean(MULTI_FORWARD_DIALOG, false)
  }

  fun hasSeenBubbleOptOutTooltip(): Boolean {
    return getBoolean(BUBBLE_OPT_OUT, false)
  }

  fun markBubbleOptOutTooltipSeen() {
    putBoolean(BUBBLE_OPT_OUT, true)
  }

  fun showProfileSettingsQrCodeTooltop(): Boolean {
    return getBoolean(PROFILE_SETTINGS_QR_CODE, true)
  }

  fun markProfileSettingsQrCodeTooltipSeen() {
    putBoolean(PROFILE_SETTINGS_QR_CODE, false)
  }

  fun showCallingSwitchCameraTooltip(): Boolean {
    return getBoolean(CALLING_SWITCH_CAMERA, true)
  }

  fun markCallingSwitchCameraTooltipSeen() {
    putBoolean(CALLING_SWITCH_CAMERA, false)
  }
}
