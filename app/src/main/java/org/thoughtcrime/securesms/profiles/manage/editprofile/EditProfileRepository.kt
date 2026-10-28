/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.editprofile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext
import org.signal.appsettings.editprofile.EditProfileState
import org.signal.core.models.badges.Badge
import org.signal.core.models.media.Media
import org.signal.core.util.StreamUtil
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileContentUpdateJob
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameDeleteResult
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ProfileUtil
import org.whispersystems.signalservice.api.util.StreamDetails
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * All of the storage, network and platform access behind [EditProfileViewModel].
 */
object EditProfileRepository {

  private val TAG = Log.tag(EditProfileRepository::class)

  fun observeSelf(): Flow<Recipient> {
    return Recipient.self().live().observable().asFlow()
  }

  /** The user's username, written `nickname.discriminator`, or null if they don't have one. */
  fun getUsername(): String? = SignalStore.account.username

  fun getUsernameSyncState(): EditProfileState.UsernameSyncState {
    return when (SignalStore.account.usernameSyncState) {
      AccountValues.UsernameSyncState.IN_SYNC -> EditProfileState.UsernameSyncState.IN_SYNC
      AccountValues.UsernameSyncState.USERNAME_AND_LINK_CORRUPTED -> EditProfileState.UsernameSyncState.USERNAME_AND_LINK_CORRUPTED
      AccountValues.UsernameSyncState.LINK_CORRUPTED -> EditProfileState.UsernameSyncState.LINK_CORRUPTED
    }
  }

  /** False while the account is unregistered or this version of the app has expired. */
  fun canEdit(): Boolean {
    return !SignalStore.account.isUnauthorizedReceived && SignalStore.account.isRegistered && !SignalStore.misc.isClientDeprecated
  }

  fun isClientDeprecated(): Boolean = SignalStore.misc.isClientDeprecated

  fun shouldShowUsernameLinkTooltip(): Boolean = SignalStore.tooltips.showProfileSettingsQrCodeTooltop()

  fun markUsernameLinkTooltipSeen() {
    SignalStore.tooltips.markProfileSettingsQrCodeTooltipSeen()
  }

  fun hasBadges(): Boolean = Recipient.self().badges.isNotEmpty()

  /** The badge a monthly donor gets, or null if the donation configuration couldn't be loaded. */
  suspend fun getSustainerBadge(): Badge? {
    return try {
      RecurringInAppPaymentRepository.getSubscriptions().await().firstOrNull()?.badge
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Could not load subscriptions.", e)
      null
    }
  }

  /** Fetches the user's own profile, in case it changed on another device. */
  fun refreshOwnProfile() {
    AppDependencies.jobManager.add(RefreshOwnProfileJob())
  }

  suspend fun deleteUsername(): UsernameDeleteResult = UsernameRepository.deleteUsernameAndLink()

  /** Reads the photo the user picked for their avatar, or returns null if it couldn't be read. */
  suspend fun readAvatar(media: Media): ByteArray? {
    return withContext(Dispatchers.Default) {
      try {
        AppDependencies.blobs.getStream(AppDependencies.application, media.uri).use { StreamUtil.readFully(it) }
      } catch (e: IOException) {
        Log.w(TAG, "Failed to read avatar!", e)
        null
      }
    }
  }

  /** Uploads [data] as the user's avatar and, once the service has it, saves it locally and syncs it to linked devices. */
  suspend fun setAvatar(data: ByteArray, contentType: String?): SaveResult {
    return withContext(SignalDispatchers.IO) {
      try {
        ProfileUtil.uploadProfileWithAvatar(StreamDetails(ByteArrayInputStream(data), contentType, data.size.toLong()))
        AvatarHelper.setAvatar(AppDependencies.application, Recipient.self().id, ByteArrayInputStream(data))
        SignalStore.misc.hasEverHadAnAvatar = true
        AppDependencies.jobManager.add(MultiDeviceProfileContentUpdateJob())
        Recipient.self().live().refresh()
        SaveResult.SUCCESS
      } catch (e: IOException) {
        Log.w(TAG, "Failed to upload profile during avatar change.", e)
        SaveResult.NETWORK_FAILURE
      }
    }
  }

  /** Removes the user's avatar from the service, then locally, and syncs that to linked devices. */
  suspend fun clearAvatar(): SaveResult {
    return withContext(SignalDispatchers.IO) {
      try {
        ProfileUtil.uploadProfileWithAvatar(null)
        AvatarHelper.delete(AppDependencies.application, Recipient.self().id)
        AppDependencies.jobManager.add(MultiDeviceProfileContentUpdateJob())
        SaveResult.SUCCESS
      } catch (e: IOException) {
        Log.w(TAG, "Failed to upload profile during avatar removal.", e)
        SaveResult.NETWORK_FAILURE
      }
    }
  }

  enum class SaveResult {
    SUCCESS,
    NETWORK_FAILURE
  }
}
