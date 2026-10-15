/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.pnp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.ProfileUploadJob
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberSharingMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper

/**
 * All of the storage access behind [PhoneNumberPrivacySettingsViewModel].
 */
object PhoneNumberPrivacySettingsRepository {

  fun isPhoneNumberSharingEnabled(): Boolean = SignalStore.phoneNumberPrivacy.isPhoneNumberSharingEnabled

  fun isDiscoverableByPhoneNumber(): Boolean = SignalStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode != PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE

  /** Stores the new sharing mode, syncs it to linked devices, and uploads the profile. */
  suspend fun setPhoneNumberSharingEnabled(enabled: Boolean) {
    withContext(Dispatchers.Default) {
      SignalStore.phoneNumberPrivacy.phoneNumberSharingMode = if (enabled) PhoneNumberSharingMode.EVERYBODY else PhoneNumberSharingMode.NOBODY
      markSelfNeedsSync()
      AppDependencies.jobManager.add(ProfileUploadJob())
    }
  }

  /** Stores the new discoverability mode, syncs it to linked devices, and refreshes account attributes. */
  suspend fun setDiscoverableByPhoneNumber(discoverable: Boolean) {
    withContext(Dispatchers.Default) {
      SignalStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode = if (discoverable) PhoneNumberDiscoverabilityMode.DISCOVERABLE else PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE
      markSelfNeedsSync()
      AppDependencies.jobManager.startChain(RefreshAttributesJob()).then(RefreshOwnProfileJob()).enqueue()
    }
  }

  private fun markSelfNeedsSync() {
    SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }
}
