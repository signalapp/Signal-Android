/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.name

import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileContentUpdateJob
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ProfileUtil
import java.io.IOException

object EditProfileNameRepository {

  private val TAG = Log.tag(EditProfileNameRepository::class)

  fun getProfileName(): ProfileName = Recipient.self().profileName

  /** Uploads [profileName] and, once the service has it, saves it locally and syncs it to linked devices. */
  suspend fun setProfileName(profileName: ProfileName): SaveResult {
    return withContext(SignalDispatchers.IO) {
      try {
        ProfileUtil.uploadProfileWithName(AppDependencies.application, profileName)
        SignalDatabase.recipients.setProfileName(Recipient.self().id, profileName)
        AppDependencies.jobManager.add(MultiDeviceProfileContentUpdateJob())
        SaveResult.SUCCESS
      } catch (e: IOException) {
        Log.w(TAG, "Failed to upload profile during name change.", e)
        SaveResult.NETWORK_FAILURE
      }
    }
  }

  enum class SaveResult {
    SUCCESS,
    NETWORK_FAILURE
  }
}
