/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import org.thoughtcrime.securesms.database.model.IdentityRecord

/**
 * Emitted when safety numbers changed recently before a send attempt.
 */
class RecentSafetyNumberChangeException(val changedRecords: List<IdentityRecord>) : Exception()
