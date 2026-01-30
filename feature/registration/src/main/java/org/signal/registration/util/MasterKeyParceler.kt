/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.util

import android.os.Parcel
import kotlinx.parcelize.Parceler
import org.signal.core.models.MasterKey

object MasterKeyParceler : Parceler<MasterKey?> {
  override fun create(parcel: Parcel): MasterKey? {
    val bytes = parcel.createByteArray()
    return bytes?.let { MasterKey(it) }
  }

  override fun MasterKey?.write(parcel: Parcel, flags: Int) {
    parcel.writeByteArray(this?.serialize())
  }
}

