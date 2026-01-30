/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.util

import android.os.Parcel
import kotlinx.parcelize.Parceler
import org.signal.libsignal.protocol.state.KyberPreKeyRecord

class KyberPreKeyRecordParceler : Parceler<KyberPreKeyRecord> {
  override fun KyberPreKeyRecord.write(parcel: Parcel, flags: Int) {
    parcel.writeByteArray(this.serialize())
  }

  override fun create(parcel: Parcel): KyberPreKeyRecord {
    return KyberPreKeyRecord(parcel.createByteArray())
  }
}