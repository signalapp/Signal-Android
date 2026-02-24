/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.util

import android.os.Parcel
import kotlinx.parcelize.Parceler
import org.signal.core.models.AccountEntropyPool

object AccountEntropyPoolParceler : Parceler<AccountEntropyPool?> {
  override fun create(parcel: Parcel): AccountEntropyPool? {
    val aep = parcel.readString()
    return aep?.let { AccountEntropyPool(it) }
  }

  override fun AccountEntropyPool?.write(parcel: Parcel, flags: Int) {
    parcel.writeString(this?.value)
  }
}
