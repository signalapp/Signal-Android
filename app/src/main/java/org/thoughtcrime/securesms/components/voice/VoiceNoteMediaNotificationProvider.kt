/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.voice

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import org.signal.core.util.PendingIntentFlags.cancelCurrent
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.AvatarUtil
import java.util.Arrays

/**
 * This handles all of the notification and playback APIs for playing back a voice note.
 * It integrates, using [androidx.media.app.NotificationCompat.MediaStyle], with the system's media controls.
 */
@OptIn(markerClass = [UnstableApi::class])
class VoiceNoteMediaNotificationProvider(val context: Context) : MediaNotification.Provider {
  private val notificationChannel: String = NotificationChannels.getInstance().VOICE_NOTES
  private var cachedRecipientId: RecipientId? = null
  private var cachedBitmap: Bitmap? = null

  override fun createNotification(mediaSession: MediaSession, customLayout: ImmutableList<CommandButton>, actionFactory: MediaNotification.ActionFactory, onNotificationChangedCallback: MediaNotification.Provider.Callback): MediaNotification {
    val player = mediaSession.player
    val builder = NotificationCompat.Builder(context, notificationChannel)
      .setSmallIcon(R.drawable.ic_notification)
      .setColorized(true)
    if (player.isCommandAvailable(Player.COMMAND_GET_METADATA)) {
      val metadata: MediaMetadata = player.mediaMetadata
      builder
        .setContentTitle(metadata.title)
        .setContentText(metadata.subtitle)
    }
    val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
    val compactViewIndices: IntArray = addNotificationActions(
      mediaSession,
      getMediaButtons(
        player.availableCommands,
        customLayout,
        player.playWhenReady &&
          player.playbackState != Player.STATE_ENDED
      ),
      builder,
      actionFactory
    )
    mediaStyle.setShowActionsInCompactView(*compactViewIndices)

    if (player.isCommandAvailable(Player.COMMAND_STOP)) {
      mediaStyle.setCancelButtonIntent(
        actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_STOP.toLong())
      )
    }
    val extras = mediaSession.player.mediaMetadata.extras

    if (extras != null) {
      var color = extras.getLong(VoiceNoteMediaItemFactory.EXTRA_COLOR).toInt()
      if (color == 0) {
        color = ChatColorsPalette.UNKNOWN_CONTACT.asSingleColor()
      }
      builder.color = color

      val pendingIntent = createCurrentContentIntent(extras)
      builder.setContentIntent(pendingIntent)
    } else {
      Log.w(TAG, "Could not populate notification: request metadata extras were null.")
    }
    builder.setDeleteIntent(
      actionFactory.createMediaActionPendingIntent(mediaSession, Player.COMMAND_STOP.toLong())
    )
      .setOnlyAlertOnce(true)
      .setStyle(mediaStyle)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setOngoing(false)
    addLargeIcon(builder, extras, onNotificationChangedCallback)

    return MediaNotification(NOW_PLAYING_NOTIFICATION_ID, builder.build())
  }

  /**
   * Borrowed from [DefaultMediaNotificationProvider]
   */
  private fun addNotificationActions(
    mediaSession: MediaSession?,
    mediaButtons: ImmutableList<CommandButton>,
    builder: NotificationCompat.Builder,
    actionFactory: MediaNotification.ActionFactory
  ): IntArray {
    var compactViewIndices = IntArray(3)
    val defaultCompactViewIndices = IntArray(3)
    Arrays.fill(compactViewIndices, C.INDEX_UNSET)
    Arrays.fill(defaultCompactViewIndices, C.INDEX_UNSET)
    var compactViewCommandCount = 0
    for (i in mediaButtons.indices) {
      val commandButton = mediaButtons[i]
      if (commandButton.sessionCommand != null) {
        builder.addAction(
          actionFactory.createCustomActionFromCustomCommandButton(mediaSession!!, commandButton)
        )
      } else {
        Assertions.checkState(commandButton.playerCommand != Player.COMMAND_INVALID)
        builder.addAction(
          actionFactory.createMediaAction(
            mediaSession!!,
            IconCompat.createWithResource(context, commandButton.iconResId),
            commandButton.displayName,
            commandButton.playerCommand
          )
        )
      }
      if (compactViewCommandCount == 3) {
        continue
      }
      val compactViewIndex = commandButton.extras.getInt(
        DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX,
        C.INDEX_UNSET
      )
      if (compactViewIndex >= 0 && compactViewIndex < compactViewIndices.size) {
        compactViewCommandCount++
        compactViewIndices[compactViewIndex] = i
      } else if (commandButton.playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS ||
        commandButton.playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
      ) {
        defaultCompactViewIndices[0] = i
      } else if (commandButton.playerCommand == Player.COMMAND_PLAY_PAUSE) {
        defaultCompactViewIndices[1] = i
      } else if (commandButton.playerCommand == Player.COMMAND_SEEK_TO_NEXT ||
        commandButton.playerCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
      ) {
        defaultCompactViewIndices[2] = i
      }
    }
    if (compactViewCommandCount == 0) {
      // If there is no custom configuration we use the seekPrev (if any), play/pause (if any),
      // seekNext (if any) action in compact view.
      var indexInCompactViewIndices = 0
      for (i in defaultCompactViewIndices.indices) {
        if (defaultCompactViewIndices[i] == C.INDEX_UNSET) {
          continue
        }
        compactViewIndices[indexInCompactViewIndices] = defaultCompactViewIndices[i]
        indexInCompactViewIndices++
      }
    }
    for (i in compactViewIndices.indices) {
      if (compactViewIndices[i] == C.INDEX_UNSET) {
        compactViewIndices = compactViewIndices.copyOf(i)
        break
      }
    }
    return compactViewIndices
  }

  /**
   * Borrowed from [DefaultMediaNotificationProvider]
   */
  private fun getMediaButtons(
    playerCommands: Player.Commands,
    customLayout: ImmutableList<CommandButton>,
    showPauseButton: Boolean
  ): ImmutableList<CommandButton> {
    val commandButtons = ImmutableList.Builder<CommandButton>()
    if (playerCommands.containsAny(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
      val commandButtonExtras = Bundle()
      commandButtonExtras.putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, C.INDEX_UNSET)
      commandButtons.add(
        CommandButton.Builder()
          .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
          .setIconResId(R.drawable.exo_icon_rewind)
          .setDisplayName(
            context.getString(R.string.media3_controls_seek_to_previous_description)
          )
          .setExtras(commandButtonExtras)
          .build()
      )
    }
    if (playerCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
      val commandButtonExtras = Bundle()
      commandButtonExtras.putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, C.INDEX_UNSET)
      commandButtons.add(
        CommandButton.Builder()
          .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
          .setIconResId(
            if (showPauseButton) R.drawable.exo_notification_pause else R.drawable.exo_notification_play
          )
          .setExtras(commandButtonExtras)
          .setDisplayName(
            if (showPauseButton) context.getString(R.string.media3_controls_pause_description) else context.getString(R.string.media3_controls_play_description)
          )
          .build()
      )
    }
    if (playerCommands.containsAny(Player.COMMAND_STOP)) {
      val commandButtonExtras = Bundle()
      commandButtons.add(
        CommandButton.Builder()
          .setPlayerCommand(Player.COMMAND_STOP)
          .setIconResId(R.drawable.exo_notification_stop)
          .setExtras(commandButtonExtras)
          .setDisplayName(context.getString(R.string.media3_controls_seek_to_next_description))
          .build()
      )
    }
    for (i in customLayout.indices) {
      val button = customLayout[i]
      if (button.sessionCommand != null &&
        button.sessionCommand!!.commandCode == SessionCommand.COMMAND_CODE_CUSTOM
      ) {
        commandButtons.add(button)
      }
    }
    return commandButtons.build()
  }

  private fun createCurrentContentIntent(extras: Bundle): PendingIntent? {
    val serializedRecipientId = extras.getString(VoiceNoteMediaItemFactory.EXTRA_THREAD_RECIPIENT_ID) ?: return null
    val recipientId = RecipientId.from(serializedRecipientId)
    val startingPosition = extras.getLong(VoiceNoteMediaItemFactory.EXTRA_MESSAGE_POSITION)
    val threadId = extras.getLong(VoiceNoteMediaItemFactory.EXTRA_THREAD_ID)

    val conversationActivity = ConversationIntents.createBuilderSync(context, recipientId, threadId)
      .withStartingPosition(startingPosition.toInt())
      .build()

    conversationActivity.flags = Intent.FLAG_ACTIVITY_NEW_TASK

    return PendingIntent.getActivity(
      context,
      0,
      conversationActivity,
      cancelCurrent()
    )
  }

  /**
   * This will either fetch a cached bitmap and add it to the builder immediately,
   * OR it will set a callback to update the notification once the bitmap is fetched by [AvatarUtil]
   */
  private fun addLargeIcon(builder: NotificationCompat.Builder, extras: Bundle?, callback: MediaNotification.Provider.Callback) {
    if (extras == null || !SignalStore.settings.messageNotificationsPrivacy.isDisplayContact) {
      cachedBitmap = null
      cachedRecipientId = null
      return
    }

    val serializedRecipientId: String = extras.getString(VoiceNoteMediaItemFactory.EXTRA_AVATAR_RECIPIENT_ID) ?: return

    val currentRecipientId = RecipientId.from(serializedRecipientId)

    if (currentRecipientId == cachedRecipientId && cachedBitmap != null) {
      builder.setLargeIcon(cachedBitmap)
    } else {
      cachedRecipientId = currentRecipientId
      SignalExecutors.BOUNDED.execute {
        try {
          cachedBitmap = AvatarUtil.getBitmapForNotification(context, Recipient.resolved(cachedRecipientId!!))
          builder.setLargeIcon(cachedBitmap)
          callback.onNotificationChanged(MediaNotification(NOW_PLAYING_NOTIFICATION_ID, builder.build()))
        } catch (e: Exception) {
          cachedBitmap = null
        }
      }
    }
  }

  /**
   * We do not currently support any custom commands in the notification area.
   */
  override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean {
    throw UnsupportedOperationException("Custom command handler for Notification is unused.")
  }

  companion object {
    private const val NOW_PLAYING_NOTIFICATION_ID = 32221
    private const val TAG = "VoiceNoteMediaNotificationProvider"
  }
}
