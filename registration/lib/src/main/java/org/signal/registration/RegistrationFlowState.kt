/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import org.signal.core.models.MasterKey

@Parcelize
@TypeParceler<MasterKey?, MasterKeyParceler>
data class RegistrationFlowState(
  val backStack: List<RegistrationRoute> = listOf(RegistrationRoute.Welcome),
  val sessionMetadata: NetworkController.SessionMetadata? = null,
  val sessionE164: String? = null,
  val masterKey: MasterKey? = null,
  val registrationLockProof: String? = null
) : Parcelable

object MasterKeyParceler : Parceler<MasterKey?> {
  override fun create(parcel: Parcel): MasterKey? {
    val bytes = parcel.createByteArray()
    return bytes?.let { MasterKey(it) }
  }

  override fun MasterKey?.write(parcel: Parcel, flags: Int) {
    parcel.writeByteArray(this?.serialize())
  }
}
