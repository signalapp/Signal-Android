/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.storage

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.storage.SignalAccountRecord
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord

class StorageRecordTest {

  @Test
  fun `describeDiff - general test`() {
    val a = SignalAccountRecord(
      StorageId.forAccount(Util.getSecretBytes(16)),
      AccountRecord(
        profileKey = ByteString.EMPTY,
        givenName = "First",
        familyName = "Last"
      )
    )

    val b = SignalAccountRecord(
      StorageId.forAccount(Util.getSecretBytes(16)),
      AccountRecord(
        profileKey = Util.getSecretBytes(16).toByteString(),
        givenName = "First",
        familyName = "LastB"
      )
    )

    assertEquals("Some fields differ: familyName, id, profileKey", a.describeDiff(b))
  }

  @Test
  fun `describeDiff - different class`() {
    val a = SignalAccountRecord(
      StorageId.forAccount(Util.getSecretBytes(16)),
      AccountRecord()
    )

    val b = SignalContactRecord(
      StorageId.forAccount(Util.getSecretBytes(16)),
      ContactRecord()
    )

    assertEquals("Classes are different!", a.describeDiff(b))
  }
}
