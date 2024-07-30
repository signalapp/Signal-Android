/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.data

import okio.ByteString.Companion.toByteString
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.LocalRegistrationMetadata
import org.thoughtcrime.securesms.registration.RegistrationData
import org.whispersystems.signalservice.api.account.PreKeyCollection

/**
 * Takes the two sources of registration data ([RegistrationData], [RegistrationRepository.AccountRegistrationResult])
 * and combines them into a proto-backed class [LocalRegistrationMetadata] so they can be serialized & stored.
 */
object LocalRegistrationMetadataUtil {
  fun createLocalRegistrationMetadata(registrationData: RegistrationData, remoteResult: RegistrationRepository.AccountRegistrationResult, reglockEnabled: Boolean): LocalRegistrationMetadata {
    return LocalRegistrationMetadata.Builder().apply {
      aciIdentityKey = remoteResult.aciPreKeyCollection.identityKey.serialize().toByteString()
      aciSignedPreKey = remoteResult.aciPreKeyCollection.signedPreKey.serialize().toByteString()
      aciLastRestoreKyberPreKey = remoteResult.aciPreKeyCollection.signedPreKey.serialize().toByteString()
      pniIdentityKey = remoteResult.pniPreKeyCollection.identityKey.serialize().toByteString()
      pniSignedPreKey = remoteResult.pniPreKeyCollection.signedPreKey.serialize().toByteString()
      pniLastRestoreKyberPreKey = remoteResult.pniPreKeyCollection.signedPreKey.serialize().toByteString()
      aci = remoteResult.uuid
      pni = remoteResult.pni
      hasPin = remoteResult.storageCapable
      remoteResult.pin?.let {
        pin = it
      }
      remoteResult.masterKey?.serialize()?.toByteString()?.let {
        masterKey = it
      }
      e164 = registrationData.e164
      fcmEnabled = registrationData.isFcm
      profileKey = registrationData.profileKey.serialize().toByteString()
      servicePassword = registrationData.password
      this.reglockEnabled = reglockEnabled
    }.build()
  }

  fun LocalRegistrationMetadata.getAciPreKeyCollection(): PreKeyCollection {
    return PreKeyCollection(
      IdentityKey(aciIdentityKey.toByteArray()),
      SignedPreKeyRecord(aciSignedPreKey.toByteArray()),
      KyberPreKeyRecord(aciLastRestoreKyberPreKey.toByteArray())
    )
  }

  fun LocalRegistrationMetadata.getPniPreKeyCollection(): PreKeyCollection {
    return PreKeyCollection(
      IdentityKey(pniIdentityKey.toByteArray()),
      SignedPreKeyRecord(pniSignedPreKey.toByteArray()),
      KyberPreKeyRecord(pniLastRestoreKyberPreKey.toByteArray())
    )
  }
}
