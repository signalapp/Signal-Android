/**
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links.details

import org.thoughtcrime.securesms.database.CallLinkTable

data class CallLinkDetailsState(
  val callLink: CallLinkTable.CallLink? = null
)
