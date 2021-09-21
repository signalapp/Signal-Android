package org.thoughtcrime.securesms.components.settings.app.data

import org.thoughtcrime.securesms.webrtc.CallBandwidthMode

data class DataAndStorageSettingsState(
  val totalStorageUse: Long,
  val mobileAutoDownloadValues: Set<String>,
  val wifiAutoDownloadValues: Set<String>,
  val roamingAutoDownloadValues: Set<String>,
  val callBandwidthMode: CallBandwidthMode,
  val isProxyEnabled: Boolean
)
