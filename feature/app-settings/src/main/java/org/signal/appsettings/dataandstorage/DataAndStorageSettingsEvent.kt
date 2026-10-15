/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.dataandstorage

import org.signal.core.models.calls.CallDataMode
import org.signal.core.models.media.SentMediaQuality

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface DataAndStorageSettingsEvent {

  /** The screen came back to the foreground, so storage usage and the proxy setting may be stale. */
  data object ScreenResumed : DataAndStorageSettingsEvent

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : DataAndStorageSettingsEvent

  /** The user tapped the manage storage row. */
  data object ManageStorageClicked : DataAndStorageSettingsEvent

  /** The user changed which media types are automatically downloaded on [network]. */
  data class AutoDownloadChanged(val network: AutoDownloadNetwork, val mediaTypes: Set<AutoDownloadMediaType>) : DataAndStorageSettingsEvent

  /** The user picked a quality to send media at. */
  data class SentMediaQualitySelected(val quality: SentMediaQuality) : DataAndStorageSettingsEvent

  /** The user picked when calls should use less data. */
  data class CallDataModeSelected(val mode: CallDataMode) : DataAndStorageSettingsEvent

  /** The user flipped the stay connected in background toggle. Turning it on asks them to confirm first. */
  data class StayConnectedInBackgroundToggled(val enabled: Boolean) : DataAndStorageSettingsEvent

  /** The user confirmed they want to stay connected in the background. */
  data object StayConnectedInBackgroundConfirmed : DataAndStorageSettingsEvent

  /** The user tapped the use proxy row. */
  data object UseProxyClicked : DataAndStorageSettingsEvent

  /** Dismisses whatever is in [DataAndStorageSettingsState.dialog]. */
  data object DialogDismissed : DataAndStorageSettingsEvent
}
