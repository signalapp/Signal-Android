package org.thoughtcrime.securesms.avatar.picker

import android.net.Uri
import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.signal.core.models.media.Media
import org.signal.core.util.StreamUtil
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.AvatarPickerStorage
import org.thoughtcrime.securesms.avatar.AvatarRenderer
import org.thoughtcrime.securesms.avatar.Avatars
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.NameUtil
import org.whispersystems.signalservice.api.util.StreamDetails
import java.io.IOException
import kotlin.coroutines.resume

private val TAG = Log.tag(AvatarPickerRepository::class.java)

object AvatarPickerRepository {

  suspend fun getAvatarForSelf(): Avatar = withContext(SignalDispatchers.IO) {
    val details: StreamDetails? = AvatarHelper.getSelfProfileAvatarStream(AppDependencies.application)
    if (details != null) {
      try {
        val bytes = StreamUtil.readFully(details.stream)
        Avatar.Photo(
          AppDependencies.blobs.forData(bytes).createForSingleSessionInMemory(),
          details.length,
          Avatar.DatabaseId.DoNotPersist
        )
      } catch (e: IOException) {
        Log.w(TAG, "Failed to read avatar!")
        getDefaultAvatarForSelf()
      }
    } else {
      getDefaultAvatarForSelf()
    }
  }

  suspend fun getAvatarForGroup(groupId: GroupId): Avatar = withContext(SignalDispatchers.IO) {
    val recipient = Recipient.externalGroupExact(groupId)

    if (AvatarHelper.hasAvatar(AppDependencies.application, recipient.id)) {
      try {
        val bytes = AvatarHelper.getAvatarBytes(AppDependencies.application, recipient.id)
        Avatar.Photo(
          AppDependencies.blobs.forData(bytes).createForSingleSessionInMemory(),
          AvatarHelper.getAvatarLength(AppDependencies.application, recipient.id),
          Avatar.DatabaseId.DoNotPersist
        )
      } catch (e: IOException) {
        Log.w(TAG, "Failed to read group avatar!")
        getDefaultAvatarForGroup(recipient.avatarColor)
      }
    } else {
      getDefaultAvatarForGroup(recipient.avatarColor)
    }
  }

  suspend fun getPersistedAvatarsForSelf(): List<Avatar> = withContext(SignalDispatchers.Default) {
    SignalDatabase.avatarPicker.getAvatarsForSelf()
  }

  suspend fun getPersistedAvatarsForGroup(groupId: GroupId): List<Avatar> = withContext(SignalDispatchers.Default) {
    SignalDatabase.avatarPicker.getAvatarsForGroup(groupId)
  }

  fun getDefaultAvatarsForSelf(): List<Avatar> {
    return Avatars.defaultAvatarsForSelf.entries.mapIndexed { index, entry ->
      Avatar.Vector(entry.key, color = Avatars.colors[index % Avatars.colors.size], Avatar.DatabaseId.NotSet)
    }
  }

  fun getDefaultAvatarsForGroup(): List<Avatar> {
    return Avatars.defaultAvatarsForGroup.entries.mapIndexed { index, entry ->
      Avatar.Vector(entry.key, color = Avatars.colors[index % Avatars.colors.size], Avatar.DatabaseId.NotSet)
    }
  }

  suspend fun writeMediaToMultiSessionStorage(media: Media): Either<Throwable, Uri> = withContext(SignalDispatchers.IO) {
    try {
      AvatarPickerStorage.save(AppDependencies.application, media).right()
    } catch (e: IOException) {
      e.left()
    }
  }

  suspend fun persistAvatarForSelf(avatar: Avatar): Either<Throwable, Avatar> = withContext(SignalDispatchers.Default) {
    try {
      val avatarDatabase = SignalDatabase.avatarPicker
      val savedAvatar = avatarDatabase.saveAvatarForSelf(avatar)
      avatarDatabase.markUsage(savedAvatar)
      savedAvatar.right()
    } catch (e: Exception) {
      e.left()
    }
  }

  suspend fun persistAvatarForGroup(avatar: Avatar, groupId: GroupId): Either<Throwable, Avatar> = withContext(SignalDispatchers.Default) {
    try {
      val avatarDatabase = SignalDatabase.avatarPicker
      val savedAvatar = avatarDatabase.saveAvatarForGroup(avatar, groupId)
      avatarDatabase.markUsage(savedAvatar)
      savedAvatar.right()
    } catch (e: Exception) {
      e.left()
    }
  }

  suspend fun persistAndCreateMediaForSelf(avatar: Avatar): Either<Throwable, Media> = either {
    if (avatar.databaseId !is Avatar.DatabaseId.DoNotPersist) {
      persistAvatarForSelf(avatar).bind()
    }

    renderAvatar(avatar).bind()
  }

  suspend fun persistAndCreateMediaForGroup(avatar: Avatar, groupId: GroupId): Either<Throwable, Media> = either {
    if (avatar.databaseId !is Avatar.DatabaseId.DoNotPersist) {
      persistAvatarForGroup(avatar, groupId).bind()
    }

    renderAvatar(avatar).bind()
  }

  private suspend fun renderAvatar(avatar: Avatar): Either<Throwable, Media> = suspendCancellableCoroutine { continuation ->
    AvatarRenderer.renderAvatar(
      context = AppDependencies.application,
      avatar = avatar,
      onAvatarRendered = { continuation.resume(it.right()) },
      onRenderFailed = { continuation.resume((it ?: IOException("Failed to render avatar.")).left()) }
    )
  }

  suspend fun createMediaForNewGroup(avatar: Avatar): Either<Throwable, Media> = renderAvatar(avatar)

  suspend fun getDefaultAvatarForSelf(): Avatar = withContext(SignalDispatchers.Default) {
    val initials = NameUtil.getAbbreviation(Recipient.self().getDisplayName(AppDependencies.application))

    if (initials.isNullOrBlank()) {
      Avatar.getDefaultForSelf()
    } else {
      Avatar.Text(initials, requireNotNull(Avatars.colorMap[Recipient.self().avatarColor.serialize()]), Avatar.DatabaseId.DoNotPersist)
    }
  }

  suspend fun getDefaultAvatarForGroup(groupId: GroupId): Avatar = withContext(SignalDispatchers.Default) {
    val recipient = Recipient.externalGroupExact(groupId)

    getDefaultAvatarForGroup(recipient.avatarColor)
  }

  fun getDefaultAvatarForGroup(color: AvatarColor?): Avatar {
    val colorPair = Avatars.colorMap[color?.serialize()]
    val defaultColor = Avatar.getDefaultForGroup()

    return if (colorPair != null) {
      defaultColor.copy(color = colorPair)
    } else {
      defaultColor
    }
  }

  suspend fun delete(avatar: Avatar) {
    withContext(SignalDispatchers.Default) {
      if (avatar.databaseId is Avatar.DatabaseId.Saved) {
        val avatarDatabase = SignalDatabase.avatarPicker
        avatarDatabase.deleteAvatar(avatar)
      }
    }
  }
}
