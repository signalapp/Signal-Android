/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.colors

import org.junit.Assert.assertEquals
import org.junit.Test
import org.signal.core.util.Base64
import org.thoughtcrime.securesms.groups.GroupId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI

class AvatarColorHashTest {

  @Test
  fun `hash test vector - ACI`() {
    assertEquals(AvatarColor.A140, AvatarColorHash.forAddress(ACI.parseOrThrow("a025bf78-653e-44e0-beb9-deb14ba32487"), null))
  }

  @Test
  fun `hash test vector - PNI`() {
    assertEquals(AvatarColor.A200, AvatarColorHash.forAddress(PNI.parseOrThrow("11a175e3-fe31-4eda-87da-e0bf2a2e250b"), null))
  }

  @Test
  fun `hash test vector - E164`() {
    assertEquals(AvatarColor.A150, AvatarColorHash.forAddress(null, "+12135550124"))
  }

  @Test
  fun `hash test vector - GroupId`() {
    assertEquals(AvatarColor.A130, AvatarColorHash.forGroupId(GroupId.V2.push(Base64.decode("BwJRIdomqOSOckHjnJsknNCibCZKJFt+RxLIpa9CWJ4="))))
  }
}
