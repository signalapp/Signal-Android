/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.dataandstorage

import org.signal.core.models.calls.CallDataMode
import org.signal.core.models.media.SentMediaQuality

/**
 * Everything [DataAndStorageSettingsScreen] needs to render.
 */
data class DataAndStorageSettingsState(
  val totalStorageUseBytes: Long? = null,
  val autoDownload: Map<AutoDownloadNetwork, Set<AutoDownloadMediaType>> = emptyMap(),
  val alwaysAutoDownloadedBytes: Long = 0,
  val sentMediaQuality: SentMediaQuality = SentMediaQuality.STANDARD,
  val callDataMode: CallDataMode = CallDataMode.HIGH_ALWAYS,
  val forceWebsocketMode: Boolean = false,
  val playServicesAvailable: Boolean = true,
  val isProxyEnabled: Boolean = false,
  val dialog: Dialog = Dialog.None
) {

  /** Without Play Services there's no push, so the app always stays connected and the toggle is locked on. */
  val isStayConnectedInBackgroundChecked: Boolean
    get() = forceWebsocketMode || !playServicesAvailable

  fun autoDownloadFor(network: AutoDownloadNetwork): Set<AutoDownloadMediaType> = autoDownload[network].orEmpty()

  /** Whichever dialog the screen is showing, if any. */
  sealed interface Dialog {
    data object None : Dialog

    /** Warns that staying connected in the background uses more battery. */
    data object ConfirmStayConnectedInBackground : Dialog
  }
}

/** The kinds of network media can be automatically downloaded on. */
enum class AutoDownloadNetwork {
  MOBILE,
  WIFI,
  ROAMING
}

/** The kinds of media that can be automatically downloaded. */
enum class AutoDownloadMediaType {
  IMAGES,
  AUDIO,
  VIDEO,
  DOCUMENTS
}
