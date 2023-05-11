/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.audio

/**
 * A listener for when audio devices are added or removed, for example if a wired headset is plugged/unplugged or Bluetooth connected/disconnected.
 */
interface AudioDeviceUpdatedListener {
  fun onAudioDeviceUpdated()
}
