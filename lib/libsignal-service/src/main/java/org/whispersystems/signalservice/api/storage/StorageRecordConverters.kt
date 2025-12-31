/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.internal.storage.protos.AccountRecord
import org.whispersystems.signalservice.internal.storage.protos.CallLinkRecord
import org.whispersystems.signalservice.internal.storage.protos.ChatFolderRecord
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord
import org.whispersystems.signalservice.internal.storage.protos.GroupV1Record
import org.whispersystems.signalservice.internal.storage.protos.GroupV2Record
import org.whispersystems.signalservice.internal.storage.protos.NotificationProfile
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord
import org.whispersystems.signalservice.internal.storage.protos.StoryDistributionListRecord

fun ContactRecord.toSignalContactRecord(storageId: StorageId): SignalContactRecord {
  return SignalContactRecord(storageId, this)
}

fun AccountRecord.toSignalAccountRecord(storageId: StorageId): SignalAccountRecord {
  return SignalAccountRecord(storageId, this)
}

fun AccountRecord.Builder.toSignalAccountRecord(storageId: StorageId): SignalAccountRecord {
  return SignalAccountRecord(storageId, this.build())
}

fun GroupV1Record.toSignalGroupV1Record(storageId: StorageId): SignalGroupV1Record {
  return SignalGroupV1Record(storageId, this)
}

fun GroupV2Record.toSignalGroupV2Record(storageId: StorageId): SignalGroupV2Record {
  return SignalGroupV2Record(storageId, this)
}

fun StoryDistributionListRecord.toSignalStoryDistributionListRecord(storageId: StorageId): SignalStoryDistributionListRecord {
  return SignalStoryDistributionListRecord(storageId, this)
}

fun CallLinkRecord.toSignalCallLinkRecord(storageId: StorageId): SignalCallLinkRecord {
  return SignalCallLinkRecord(storageId, this)
}

fun ChatFolderRecord.toSignalChatFolderRecord(storageId: StorageId): SignalChatFolderRecord {
  return SignalChatFolderRecord(storageId, this)
}

fun NotificationProfile.toSignalNotificationProfileRecord(storageId: StorageId): SignalNotificationProfileRecord {
  return SignalNotificationProfileRecord(storageId, this)
}

fun SignalContactRecord.toSignalStorageRecord(): SignalStorageRecord {
  return SignalStorageRecord(id, StorageRecord(contact = this.proto))
}

fun SignalGroupV1Record.toSignalStorageRecord(): SignalStorageRecord {
  return SignalStorageRecord(id, StorageRecord(groupV1 = this.proto))
}

fun SignalGroupV2Record.toSignalStorageRecord(): SignalStorageRecord {
  return SignalStorageRecord(id, StorageRecord(groupV2 = this.proto))
}

fun SignalAccountRecord.toSignalStorageRecord(): SignalStorageRecord {
  return SignalStorageRecord(id, StorageRecord(account = this.proto))
}

fun SignalStoryDistributionListRecord.toSignalStorageRecord(): SignalStorageRecord {
  return SignalStorageRecord(id, StorageRecord(storyDistributionList = this.proto))
}

fun SignalCallLinkRecord.toSignalStorageRecord(): SignalStorageRecord {
  return SignalStorageRecord(id, StorageRecord(callLink = this.proto))
}

fun SignalChatFolderRecord.toSignalStorageRecord(): SignalStorageRecord {
  return SignalStorageRecord(id, StorageRecord(chatFolder = this.proto))
}

fun SignalNotificationProfileRecord.toSignalStorageRecord(): SignalStorageRecord {
  return SignalStorageRecord(id, StorageRecord(notificationProfile = this.proto))
}
