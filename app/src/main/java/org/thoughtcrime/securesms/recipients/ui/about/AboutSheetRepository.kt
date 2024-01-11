/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.about

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.RecipientId

class AboutSheetRepository {
  fun getGroupsInCommonCount(recipientId: RecipientId): Single<Int> {
    return Single.fromCallable {
      SignalDatabase.groups.getPushGroupsContainingMember(recipientId).size
    }.subscribeOn(Schedulers.io())
  }
}
