/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.storage.protos.StoryDistributionListRecord

val StoryDistributionListRecord.recipientServiceAddresses: List<SignalServiceAddress>
  get() {
    return this.recipientServiceIds
      .mapNotNull { ServiceId.parseOrNull(it) }
      .map { SignalServiceAddress(it) }
  }
