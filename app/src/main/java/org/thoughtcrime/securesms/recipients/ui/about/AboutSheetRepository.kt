/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.about

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.rx3.rxSingle
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupsInCommonRepository
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabel
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabelRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.Optional

class AboutSheetRepository {

  fun getGroupsInCommonCount(recipientId: RecipientId): Single<Int> {
    return rxSingle { GroupsInCommonRepository.getGroupsInCommonCount(recipientId) }
  }

  fun getVerified(recipientId: RecipientId): Single<Boolean> {
    return Single.fromCallable {
      val identityRecord = AppDependencies.protocolStore.aci().identities().getIdentityRecord(recipientId)
      identityRecord.isPresent && identityRecord.get().verifiedStatus == IdentityTable.VerifiedStatus.VERIFIED
    }.subscribeOn(Schedulers.io())
  }

  fun getMemberLabel(groupId: GroupId.V2): Single<Optional<MemberLabel>> = rxSingle {
    Optional.ofNullable(MemberLabelRepository.instance.getLabel(groupId, Recipient.self()))
  }

  fun canEditMemberLabel(groupId: GroupId.V2): Single<Boolean> = rxSingle {
    MemberLabelRepository.instance.canSetLabel(groupId, Recipient.self())
  }
}
