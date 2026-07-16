/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.megaphone

import android.net.Uri
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.RemoteMegaphoneRecord
import org.thoughtcrime.securesms.recipients.Recipient
import java.util.UUID
import java.util.concurrent.TimeUnit

fun donateMegaphoneRecord(conditionalId: String?): RemoteMegaphoneRecord {
  val now = System.currentTimeMillis()
  return RemoteMegaphoneRecord(
    priority = 100,
    uuid = UUID.randomUUID().toString(),
    countries = null,
    minimumVersion = 1,
    doNotShowBefore = now - TimeUnit.DAYS.toMillis(2),
    doNotShowAfter = now + TimeUnit.DAYS.toMillis(28),
    showForNumberOfDays = 30,
    conditionalId = conditionalId,
    primaryActionId = RemoteMegaphoneRecord.ActionId.DONATE,
    secondaryActionId = RemoteMegaphoneRecord.ActionId.SNOOZE,
    imageUrl = null,
    title = "Donate Test",
    body = "Donate body test.",
    primaryActionText = "Donate",
    secondaryActionText = "Snooze"
  )
}

fun donorBadge(): Badge {
  return Badge(
    id = "test-donor-badge",
    category = Badge.Category.Donor,
    name = "Signal Sustainer",
    description = "",
    imageUrl = Uri.EMPTY,
    imageDensity = "xxhdpi",
    expirationTimestamp = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30),
    visible = true,
    duration = TimeUnit.DAYS.toMillis(30)
  )
}

fun setSelfBadges(badges: List<Badge>) {
  SignalDatabase.recipients.setBadges(Recipient.self().id, badges)
  Recipient.self().fresh()
}
