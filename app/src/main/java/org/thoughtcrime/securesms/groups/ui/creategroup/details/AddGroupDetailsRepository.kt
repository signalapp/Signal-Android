/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup.details

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.signal.core.models.media.Media
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.bitmaps.BitmapUtil
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.groups.GroupChangeBusyException
import org.signal.core.util.groups.GroupChangeException
import org.signal.core.util.logging.Log
import org.signal.glide.decryptableuri.DecryptableUri
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.recipients.Recipient
import java.io.IOException
import java.util.concurrent.ExecutionException

class AddGroupDetailsRepository(
  private val context: Context = AppDependencies.application
) {

  companion object {
    private val TAG = Log.tag(AddGroupDetailsRepository::class)
  }

  suspend fun resolveMembers(recipientIds: List<RecipientId>): List<Recipient> = withContext(SignalDispatchers.IO) {
    recipientIds.map { Recipient.resolved(it) }
  }

  suspend fun getGroupsWithSameMembers(memberIds: Set<RecipientId>): List<Recipient> = withContext(SignalDispatchers.IO) {
    SignalDatabase.groups.getGroupsWithExactMembers(memberIds).map { Recipient.resolved(it.recipientId) }
  }

  suspend fun getOrCreateThreadId(recipient: Recipient): Long = withContext(SignalDispatchers.IO) {
    SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
  }

  suspend fun loadAvatar(media: Media): ByteArray? = withContext(SignalDispatchers.IO) {
    val loadBitmap = Glide.with(context)
      .asBitmap()
      .load(DecryptableUri(media.uri))
      .skipMemoryCache(true)
      .diskCacheStrategy(DiskCacheStrategy.NONE)
      .centerCrop()
      .submit(AvatarHelper.AVATAR_DIMENSIONS, AvatarHelper.AVATAR_DIMENSIONS)

    try {
      val bitmap = runInterruptible { loadBitmap.get() }
      BitmapUtil.toByteArray(bitmap)
    } catch (e: ExecutionException) {
      Log.w(TAG, "Unable to load the selected avatar.", e)
      null
    } finally {
      Glide.with(context).clear(loadBitmap)
    }
  }

  suspend fun createGroup(
    members: Set<RecipientId>,
    avatar: ByteArray?,
    name: String?,
    disappearingMessagesTimer: Int
  ): GroupCreateResult = withContext(SignalDispatchers.IO) {
    try {
      val result = GroupManager.createGroup(context, members, avatar, name, disappearingMessagesTimer)

      GroupCreateResult.Success(
        groupId = result.groupRecipient.id,
        threadId = result.threadId,
        invitedMembers = Recipient.resolvedList(result.invitedMembers)
      )
    } catch (e: GroupChangeBusyException) {
      Log.w(TAG, "Unable to create group, group busy", e)
      GroupCreateResult.RetryableFailure
    } catch (e: GroupChangeException) {
      Log.w(TAG, "Unable to create group, group change failed", e)
      GroupCreateResult.Failure
    } catch (e: IOException) {
      Log.w(TAG, "Unable to create group, unknown IO", e)
      GroupCreateResult.RetryableFailure
    }
  }
}
