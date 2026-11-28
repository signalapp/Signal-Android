/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.about

import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileContentUpdateJob
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ProfileUtil
import java.io.IOException

object EditAboutRepository {

  private val TAG = Log.tag(EditAboutRepository::class)

  fun getAbout(): String = Recipient.self().about.orEmpty()

  fun getAboutEmoji(): String = Recipient.self().aboutEmoji.orEmpty()

  /** Uploads [about] and [emoji] and, once the service has them, saves them locally and syncs them to linked devices. */
  suspend fun setAbout(about: String, emoji: String): SaveResult {
    return withContext(SignalDispatchers.IO) {
      try {
        ProfileUtil.uploadProfileWithAbout(AppDependencies.application, about, emoji)
        SignalDatabase.recipients.setAbout(Recipient.self().id, about, emoji)
        AppDependencies.jobManager.add(MultiDeviceProfileContentUpdateJob())
        SaveResult.SUCCESS
      } catch (e: IOException) {
        Log.w(TAG, "Failed to upload profile during about change.", e)
        SaveResult.NETWORK_FAILURE
      }
    }
  }

  enum class SaveResult {
    SUCCESS,
    NETWORK_FAILURE
  }
}
