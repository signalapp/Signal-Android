/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.voice

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.annotation.WorkerThread
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.LocalConfiguration
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SimpleTask
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.NoSuchMessageException
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.messages
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.util.hasAudio
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * See [VoiceNotePlaybackService].
 */
@OptIn(UnstableApi::class)
class VoiceNotePlayerCallback(val context: Context, val player: VoiceNotePlayer) : MediaSession.Callback {
  companion object {
    private val SUPPORTED_ACTIONS = Player.Commands.Builder()
      .addAll(
        Player.COMMAND_PLAY_PAUSE,
        Player.COMMAND_PREPARE,
        Player.COMMAND_STOP,
        Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_MEDIA_ITEM,
        Player.COMMAND_SEEK_BACK,
        Player.COMMAND_SEEK_FORWARD,
        Player.COMMAND_SET_SPEED_AND_PITCH,
        Player.COMMAND_SET_REPEAT_MODE,
        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
        Player.COMMAND_GET_TIMELINE,
        Player.COMMAND_GET_METADATA,
        Player.COMMAND_SET_PLAYLIST_METADATA,
        Player.COMMAND_SET_MEDIA_ITEM,
        Player.COMMAND_CHANGE_MEDIA_ITEMS,
        Player.COMMAND_GET_AUDIO_ATTRIBUTES,
        Player.COMMAND_GET_TEXT,
        Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS,
        Player.COMMAND_RELEASE
      )
      .build()

    private val CUSTOM_COMMANDS = SessionCommands.Builder()
      .add(SessionCommand(VoiceNotePlaybackService.ACTION_NEXT_PLAYBACK_SPEED, Bundle.EMPTY))
      .add(SessionCommand(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, Bundle.EMPTY))
      .build()
    private const val DEFAULT_PLAYBACK_SPEED = 1f
    private const val LIMIT: Long = 5
  }

  private val TAG = Log.tag(VoiceNotePlayerCallback::class.java)
  private val EXECUTOR: Executor = Executors.newSingleThreadExecutor()
  private val customLayout: List<CommandButton> = mutableListOf<CommandButton>().apply {
    add(CommandButton.Builder().setPlayerCommand(Player.COMMAND_PLAY_PAUSE).build())
    add(CommandButton.Builder().setPlayerCommand(Player.COMMAND_STOP).build())
  }
  private var canLoadMore = false
  private var latestUri = Uri.EMPTY

  override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
    return MediaSession.ConnectionResult.accept(CUSTOM_COMMANDS, SUPPORTED_ACTIONS)
  }

  override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
    if (customLayout.isNotEmpty() && controller.controllerVersion != 0) {
      session.setCustomLayout(controller, customLayout)
    }
  }

  override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
    mediaItems.forEach {
      val uri = it.localConfiguration?.uri
      if (uri != null) {
        val extras = it.requestMetadata.extras
        onPrepareFromUri(uri, extras)
      } else {
        throw UnsupportedOperationException("VoiceNotePlayerCallback does not support onPrepareFromMediaId/onPrepareFromSearch")
      }
    }
    return super.onAddMediaItems(mediaSession, controller, mediaItems)
  }

  private fun onPrepareFromUri(uri: Uri, extras: Bundle?) {
    Log.d(TAG, "onPrepareFromUri: $uri")
    if (extras == null) {
      return
    }
    val messageId = extras.getLong(VoiceNoteMediaController.EXTRA_MESSAGE_ID)
    val threadId = extras.getLong(VoiceNoteMediaController.EXTRA_THREAD_ID)
    val progress = extras.getDouble(VoiceNoteMediaController.EXTRA_PROGRESS, 0.0)
    val singlePlayback = extras.getBoolean(VoiceNoteMediaController.EXTRA_PLAY_SINGLE, false)
    canLoadMore = false
    latestUri = uri
    SimpleTask.run(
      EXECUTOR,
      {
        if (singlePlayback) {
          if (messageId != -1L) {
            return@run loadMediaItemsForSinglePlayback(messageId)
          } else {
            return@run loadMediaItemsForDraftPlayback(threadId, uri)
          }
        } else {
          return@run loadMediaItemsForConsecutivePlayback(messageId)
        }
      }
    ) { mediaItems: List<MediaItem> ->
      player.clearMediaItems()
      if (mediaItems.isNotEmpty() && latestUri == uri) {
        addItemsToPlaylist(mediaItems)
        val window = max(0, indexOfPlayerMediaItemByUri(uri))
        player.addListener(object : Player.Listener {
          override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (timeline.windowCount >= window) {
              player.playWhenReady = false
              player.playbackParameters = PlaybackParameters(DEFAULT_PLAYBACK_SPEED)
              player.seekTo(window, (player.duration * progress).toLong())
              player.playWhenReady = true
              player.removeListener(this)
            }
          }
        })
        player.prepare()
        canLoadMore = !singlePlayback
      } else if (latestUri == uri) {
        Log.w(TAG, "Requested playback but no voice notes could be found.")
        ThreadUtil.postToMain {
          Toast.makeText(context, R.string.VoiceNotePlaybackPreparer__failed_to_play_voice_message, Toast.LENGTH_SHORT)
            .show()
        }
      }
    }
  }

  override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
    return when (customCommand.customAction) {
      VoiceNotePlaybackService.ACTION_NEXT_PLAYBACK_SPEED -> incrementPlaybackSpeed(args)
      VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM -> setAudioStream(args)
      else -> super.onCustomCommand(session, controller, customCommand, args)
    }
  }

  private fun incrementPlaybackSpeed(extras: Bundle): ListenableFuture<SessionResult> {
    val speed = extras.getFloat(VoiceNotePlaybackService.ACTION_NEXT_PLAYBACK_SPEED, 1f)
    player.playbackParameters = PlaybackParameters(speed)
    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
  }

  private fun setAudioStream(extras: Bundle): ListenableFuture<SessionResult> {
    val newStreamType: Int = extras.getInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_MUSIC)

    val currentStreamType = androidx.media3.common.util.Util.getStreamTypeForAudioUsage(player.audioAttributes.usage)
    if (newStreamType != currentStreamType) {
      val attributes = when (newStreamType) {
        AudioManager.STREAM_MUSIC -> AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build()
        AudioManager.STREAM_VOICE_CALL -> AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_VOICE_COMMUNICATION).build()
        else -> throw AssertionError()
      }

      player.playWhenReady = false
      player.setAudioAttributes(attributes, newStreamType == AudioManager.STREAM_MUSIC)
      if (newStreamType == AudioManager.STREAM_VOICE_CALL) {
        player.playWhenReady = true
      }
    }
    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
  }

  @MainThread
  private fun addItemsToPlaylist(mediaItems: List<MediaItem>) {
    var mediaItemsWithNextTone = mediaItems.flatMap { listOf(it, VoiceNoteMediaItemFactory.buildNextVoiceNoteMediaItem(it)) }.toMutableList()
    mediaItemsWithNextTone = mediaItemsWithNextTone.subList(0, mediaItemsWithNextTone.lastIndex).toMutableList()
    if (player.mediaItemCount == 0) {
      if (mediaItems.size > 1) {
        mediaItemsWithNextTone += VoiceNoteMediaItemFactory.buildEndVoiceNoteMediaItem(mediaItemsWithNextTone.last())
      }
      player.addMediaItems(mediaItemsWithNextTone)
    } else {
      player.addMediaItems(player.mediaItemCount, mediaItemsWithNextTone)
    }
  }

  private fun indexOfPlayerMediaItemByUri(uri: Uri): Int {
    for (i in 0 until player.mediaItemCount) {
      val playbackProperties: LocalConfiguration? = player.getMediaItemAt(i).playbackProperties
      if (playbackProperties?.uri == uri) {
        return i
      }
    }
    return -1
  }

  fun loadMoreVoiceNotes() {
    if (!canLoadMore) {
      return
    }
    val currentMediaItem: MediaItem = player.currentMediaItem ?: return
    val messageId = currentMediaItem.mediaMetadata.extras!!.getLong(VoiceNoteMediaItemFactory.EXTRA_MESSAGE_ID)
    val currentPlaylist = List(player.mediaItemCount) { index -> player.getMediaItemAt(index) }.mapNotNull { it.requestMetadata.mediaUri }
    SimpleTask.run(
      EXECUTOR,
      { loadMediaItemsForConsecutivePlayback(messageId).filterNot { it.requestMetadata.mediaUri in currentPlaylist } }
    ) { mediaItems: List<MediaItem> ->
      if (mediaItems.isNotEmpty() && canLoadMore) {
        addItemsToPlaylist(mediaItems)
      }
    }
  }

  private fun loadMediaItemsForDraftPlayback(threadId: Long, draftUri: Uri): List<MediaItem> {
    return listOf<MediaItem>(VoiceNoteMediaItemFactory.buildMediaItem(context, threadId, draftUri))
  }

  private fun loadMediaItemsForSinglePlayback(messageId: Long): List<MediaItem> {
    return try {
      listOf(messages.getMessageRecord(messageId)).messageRecordsToVoiceNoteMediaItems()
    } catch (e: NoSuchMessageException) {
      Log.w(TAG, "Could not find message.", e)
      emptyList()
    }
  }

  @WorkerThread
  private fun loadMediaItemsForConsecutivePlayback(messageId: Long): List<MediaItem> {
    return try {
      messages.getMessagesAfterVoiceNoteInclusive(messageId, LIMIT).messageRecordsToVoiceNoteMediaItems()
    } catch (e: NoSuchMessageException) {
      Log.w(TAG, "Could not find message.", e)
      emptyList()
    }
  }

  private fun List<MessageRecord>.messageRecordsToVoiceNoteMediaItems(): List<MediaItem> {
    return this.takeWhile { it.hasAudio() }.mapNotNull { VoiceNoteMediaItemFactory.buildMediaItem(context, it) }
  }
}
