/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.data

import org.signal.core.models.MasterKey
import org.whispersystems.signalservice.api.account.PreKeyCollection

data class AccountRegistrationResult(
  val uuid: String,
  val pni: String,
  val storageCapable: Boolean,
  val number: String,
  val masterKey: MasterKey?,
  val pin: String?,
  val aciPreKeyCollection: PreKeyCollection,
  val pniPreKeyCollection: PreKeyCollection,
  val reRegistration: Boolean
)
