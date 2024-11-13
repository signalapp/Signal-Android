/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord

val ContactRecord.signalAci: ServiceId.ACI?
  get() = ServiceId.ACI.parseOrNull(this.aci)

val ContactRecord.signalPni: ServiceId.PNI?
  get() = ServiceId.PNI.parseOrNull(this.pni)
